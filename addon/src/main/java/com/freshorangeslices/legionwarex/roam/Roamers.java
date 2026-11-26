package com.freshorangeslices.legionwarex.roam;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.companion.Companions;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Roaming bosses: the bosses the pack lets spawn out in the world (the Queen of Orchid in flowery biomes, the Old
 * Champion's Remains in snowy ones, Sir Pumpkinhead at night), told from the same bosses in their lairs by how they came
 * (a natural spawn). FreshOrangeSlices, 2026-11-24: five Queens in one hour.
 * <ul>
 * <li>After one spawns, no other of it spawns naturally for {@code roamingBossWaitDays} (game time: 3 days is an hour of
 * play), kept with the world ({@link RoamerClock}). All of {@code roamingBosses}.</li>
 * <li>The quiet ones ({@code quietRoamers}: the Queen and the Old Champion, and the Frenzied Shade that rises where a
 * roaming Champion falls, {@code roamerHeirs}) drop nothing and pay {@code roamerXpMultiplier} times the XP, vanilla's
 * orbs and Bloom's Combat XP (Bloom reads {@link #BLOOM_XP} from the mob). Sir Pumpkinhead isn't one: he has no lair,
 * keeps his drops and stays a threat.</li>
 * <li>A quiet one leaves everyone alone until something hits it (a player, their pet or companion, anything), and
 * then goes only for that one's side; or until a player comes within {@code roamerNoticeRange} blocks. Its pick of a
 * target is refused otherwise (the last word on {@link LivingChangeTargetEvent}), and a target got some other way is
 * dropped within a second. It calms down after {@value #CALM_SECONDS} seconds without a target.</li>
 * </ul>
 * Logged as "LegionWareX roamers:". {@code /legionwarex roamers [reset]}.
 */
public final class Roamers {
	/** A quiet roamer (no drops, more XP, neutral). */
	public static final String TAG = "legionwarex_roamer";
	/** Any roaming boss that came naturally (its clock starts as it joins). */
	public static final String ROAMING = "legionwarex_roaming";
	private static final String DATA = "legionwarex.roamer";
	/** A mob's own Combat XP multiplier, in its persistent data: Bloom multiplies its Combat XP by it. */
	public static final String BLOOM_XP = "bloomlevelup.combatXpMultiplier";
	public static final int CALM_SECONDS = 15;
	public static final long DAY = 24000L;
	/** What rises where a quiet roamer falls counts as one: within this many blocks and ticks. */
	static final double HEIR_RANGE = 24.0;
	static final long HEIR_TICKS = 600L;

	/** Game tests: lists in place of the config's (null: the config). */
	public static List<String> testRoaming;
	public static List<String> testQuiet;
	public static List<String> testHeirs;

	private static final List<Fallen> FALLEN = new ArrayList<>();
	private static final Set<String> HELD_LOGGED = new HashSet<>();

	private record Fallen(String heir, Vec3 at, long time) {
	}

	private Roamers() {
	}

	static List<? extends String> roaming() {
		return testRoaming != null ? testRoaming : AddonConfig.roamingBosses();
	}

	static List<? extends String> quiet() {
		return testQuiet != null ? testQuiet : AddonConfig.quietRoamers();
	}

	static List<? extends String> heirs() {
		return testHeirs != null ? testHeirs : AddonConfig.roamerHeirs();
	}

	static String id(Entity entity) {
		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		return id == null ? "" : id.toString();
	}

	public static boolean isQuiet(Entity entity) {
		return entity.getTags().contains(TAG);
	}

	/** Whether another of a boss may come: none came in the last {@code waitTicks} (or never). */
	public static boolean mayCome(long lastSpawn, long now, long waitTicks) {
		return lastSpawn < 0L || now - lastSpawn >= waitTicks;
	}

	static long waitTicks() {
		return Math.round(AddonConfig.roamingBossWaitDays() * DAY);
	}

	/** Makes a mob a quiet roamer: tagged, and Bloom's Combat XP multiplier set on it. */
	public static void makeQuiet(Mob mob) {
		mob.addTag(TAG);
		mob.getPersistentData().putDouble(BLOOM_XP, AddonConfig.roamerXpMultiplier());
	}

	/** The XP a quiet roamer drops: its own times the multiplier. */
	public static int xp(int original) {
		return (int) Math.round(original * AddonConfig.roamerXpMultiplier());
	}

	/** Whose side a hit or a target is on: a pet's or companion's owner, else itself. */
	public static UUID side(Entity entity) {
		UUID owner = Companions.ownerOf(entity);
		return owner != null ? owner : entity.getUUID();
	}

	private static CompoundTag data(Mob mob) {
		CompoundTag persistent = mob.getPersistentData();

		if (!persistent.contains(DATA)) {
			persistent.put(DATA, new CompoundTag());
		}

		return persistent.getCompound(DATA);
	}

	/** Something hit it: it may go for that one's side from now on. */
	public static void provoke(Mob roamer, Entity by) {
		CompoundTag angry = data(roamer).getCompound("angry");
		angry.putLong(side(by).toString(), roamer.level().getGameTime());
		data(roamer).put("angry", angry);
		data(roamer).putInt("calm", 0);
	}

	public static boolean provokedBy(Mob roamer, Entity target) {
		return data(roamer).getCompound("angry").contains(side(target).toString());
	}

	/** Lets go of every grudge. */
	public static void calm(Mob roamer) {
		data(roamer).remove("angry");
		data(roamer).putInt("calm", 0);
	}

	/** Whether a quiet roamer may go for this target: its side hit it, or it's a player within the notice range. */
	public static boolean mayTarget(Mob roamer, LivingEntity target) {
		return provokedBy(roamer, target) || noticed(roamer, target);
	}

	static boolean noticed(Mob roamer, LivingEntity target) {
		double range = AddonConfig.roamerNoticeRange();
		return target instanceof Player player && !player.isCreative() && !player.isSpectator() && player.distanceToSqr(roamer) <= range * range;
	}

	/** Whether what rises (heir, at, now) is the heir of a fall (its heir id, where, when). */
	public static boolean inherits(String fallenHeir, Vec3 fallenAt, long fallenTime, String id, Vec3 at, long now) {
		return fallenHeir.equals(id) && now - fallenTime <= HEIR_TICKS && fallenAt.distanceToSqr(at) <= HEIR_RANGE * HEIR_RANGE;
	}

	static String heirOf(String id) {
		for (String entry : heirs()) {
			int eq = entry.indexOf('=');

			if (eq > 0 && entry.substring(0, eq).trim().equals(id)) {
				return entry.substring(eq + 1).trim();
			}
		}

		return null;
	}

	static void log(String text) {
		LegionWareX.LOGGER.info("LegionWareX roamers: {}", text);
	}

	// ---------------------------------------------------------------- spawning

	/** A natural spawn of a roaming boss waits its days; one that may come is marked (quiet ones made quiet). */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
		Mob mob = event.getEntity();

		if (event.isSpawnCancelled() || event.getSpawnType() != MobSpawnType.NATURAL) {
			return;
		}

		ServerLevel level = event.getLevel().getLevel();

		String id = id(mob);

		if (!roaming().contains(id)) {
			return;
		}

		long last = RoamerClock.of(level.getServer()).last(id);

		if (!mayCome(last, level.getGameTime(), waitTicks())) {
			event.setSpawnCancelled(true);

			if (HELD_LOGGED.add(id + "@" + last)) {
				log(id + " held back: the last came " + days(level.getGameTime() - last) + " day(s) ago, the next may come in "
						+ days(last + waitTicks() - level.getGameTime()) + " day(s)");
			}

			return;
		}

		mob.addTag(ROAMING);

		if (quiet().contains(id)) {
			makeQuiet(mob);
		}
	}

	/** A roaming boss that came starts its kind's clock; what rises where a quiet one fell is quiet too. */
	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob)
				|| !(event.getLevel() instanceof ServerLevel level)) {
			return;
		}

		String id = id(mob);

		if (mob.getTags().contains(ROAMING) && !data(mob).contains("came")) {
			data(mob).putLong("came", level.getGameTime());
			RoamerClock.of(level.getServer()).record(id, level.getGameTime());
			log(id + " came at " + mob.blockPosition().toShortString() + (isQuiet(mob) ? " (quiet: no drops, more XP, leaves you alone until hit)" : "")
					+ "; the next may come in " + days(waitTicks()) + " day(s)");
			return;
		}

		for (Iterator<Fallen> it = FALLEN.iterator(); it.hasNext(); ) {
			Fallen fallen = it.next();

			if (level.getGameTime() - fallen.time() > HEIR_TICKS) {
				it.remove();
			} else if (!isQuiet(mob) && inherits(fallen.heir(), fallen.at(), fallen.time(), id, mob.position(), level.getGameTime())) {
				makeQuiet(mob);
				it.remove();
				log(id + " rose where a quiet roamer fell, and is quiet too");
				return;
			}
		}
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide() && isQuiet(mob)) {
			String heir = heirOf(id(mob));

			if (heir != null) {
				FALLEN.add(new Fallen(heir, mob.position(), mob.level().getGameTime()));
			}
		}
	}

	/** A quiet roamer drops nothing (its lair's twin keeps its loot). */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDrops(LivingDropsEvent event) {
		if (!event.getEntity().level().isClientSide() && isQuiet(event.getEntity()) && !event.getDrops().isEmpty()) {
			log(id(event.getEntity()) + " fell as a roamer: its " + event.getDrops().size() + " drop(s) held back");
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onXp(LivingExperienceDropEvent event) {
		if (!event.getEntity().level().isClientSide() && isQuiet(event.getEntity())) {
			event.setDroppedExperience(xp(event.getDroppedExperience()));
		}
	}

	// ---------------------------------------------------------------- leaving you alone

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onHurt(LivingHurtEvent event) {
		Entity by = event.getSource().getEntity();

		if (!event.isCanceled() && by != null && event.getEntity() instanceof Mob mob && !mob.level().isClientSide() && isQuiet(mob) && by != mob) {
			provoke(mob, by);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onChangeTarget(LivingChangeTargetEvent event) {
		LivingEntity target = event.getNewTarget();

		if (target == null || event.isCanceled() || !(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || !isQuiet(mob)) {
			return;
		}

		if (provokedBy(mob, target)) {
			return;
		}

		if (noticed(mob, target)) {
			provoke(mob, target);
		} else {
			event.setCanceled(true);
		}
	}

	/** Once a second: a target got past the event is dropped; without a target for a while, it calms down. */
	@SubscribeEvent
	public static void onTick(LivingEvent.LivingTickEvent event) {
		if (!(event.getEntity() instanceof Mob mob) || mob.tickCount % 20 != 13 || mob.level().isClientSide() || !isQuiet(mob)) {
			return;
		}

		LivingEntity target = mob.getTarget();

		if (target != null && !mayTarget(mob, target)) {
			mob.setTarget(null);
			target = null;
		}

		CompoundTag data = data(mob);

		if (target == null && data.contains("angry")) {
			int calm = data.getInt("calm") + 1;

			if (calm >= CALM_SECONDS) {
				calm(mob);
			} else {
				data.putInt("calm", calm);
			}
		} else if (target != null) {
			data.putInt("calm", 0);
		}
	}

	/** What the server runs with, for the boot test; an id no mod registers is named (a typo or a mod gone). */
	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		List<String> unknown = new ArrayList<>();

		for (String id : roaming()) {
			ResourceLocation key = ResourceLocation.tryParse(id);

			if (key == null || !ForgeRegistries.ENTITY_TYPES.containsKey(key)) {
				unknown.add(id);
			}
		}

		log(roaming().size() + " roaming boss(es) " + roaming() + ", one each " + days(waitTicks()) + " days; quiet (no drops, x"
				+ AddonConfig.roamerXpMultiplier() + " XP, leave you alone until hit or within " + AddonConfig.roamerNoticeRange() + " blocks): "
				+ quiet() + "; heirs " + heirs() + (unknown.isEmpty() ? "" : "; not in the game: " + unknown));
	}

	// ---------------------------------------------------------------- command

	static String days(long ticks) {
		return String.format(java.util.Locale.ROOT, "%.1f", Math.max(0L, ticks) / (double) DAY);
	}

	/** {@code /legionwarex roamers [reset]}: when each roaming boss last came and when the next may. */
	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("roamers").executes(context -> show(context.getSource()))
				.then(Commands.literal("reset").executes(context -> {
					RoamerClock.of(context.getSource().getServer()).forgetAll();
					context.getSource().sendSuccess(() -> Component.literal("Roaming bosses may all come again"), true);
					log("clock reset");
					return 1;
				}));
	}

	private static int show(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		RoamerClock clock = RoamerClock.of(server);
		long now = server.overworld().getGameTime();
		StringBuilder text = new StringBuilder("Roaming bosses (one each " + days(waitTicks()) + " days):");

		for (String id : roaming()) {
			long last = clock.last(id);
			text.append("\n ").append(id).append(quiet().contains(id) ? " (quiet)" : "").append(": ");
			text.append(last < 0L ? "never came" : "came " + days(now - last) + " day(s) ago, "
					+ (mayCome(last, now, waitTicks()) ? "may come again" : "next in " + days(last + waitTicks() - now) + " day(s)"));
		}

		source.sendSuccess(() -> Component.literal(text.toString()), false);
		log(text.toString().replace("\n", ";"));
		return 1;
	}

	/** For the command's and the tests' sake: the clock's view. */
	public static Map<String, Long> clock(MinecraftServer server) {
		return RoamerClock.of(server).all();
	}
}
