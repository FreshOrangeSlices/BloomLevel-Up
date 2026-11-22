package com.freshorangeslices.legionwarex.summon;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.companion.Companions;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Summons, and a player's side never fighting itself (FreshOrangeSlices, 2026-11-22: Bosses'Rise's Pirate Saber summons
 * a Crossbow Pirate and a Pirate Rook that stayed for good, couldn't be hurt by their owner or his companions, the
 * add-on's own {@link com.freshorangeslices.legionwarex.companion.OwnerHits} seeing them as his, and came at him with
 * sword and bolt, alone and beside a friend).
 *
 * <p>A <b>summon</b> is a monster a player owns: Bosses'Rise's owned pirates and Ghost Tentacles, other mods' summoned
 * monsters. <b>Targets</b>: nothing a player owns picks its owner, or anything else that owner has (companions, pets,
 * summons); a summon never picks any player (FreshOrangeSlices: never, for simplicity). Refused as the target is set
 * (the last word on {@link LivingChangeTargetEvent}), and a target got some other way is dropped within a second.
 * Bosses'Rise's own goals already skip the owner, so whatever pointed them at him is somewhere else in the pack: the
 * first refusal from each place in the code is logged as "LegionWareX summons:" with its calls. Hits are refused in
 * {@code OwnerHits}.
 *
 * <p>The <b>Pirate Saber</b>'s pirates leave after {@code pirateSaberSummonTicks} (60 seconds), at most
 * {@code pirateSaberSummonsAtOnce} (2) for each player (a new charge sends the oldest away, a tick later), and their
 * Rook's saber (and anything else they hold) never drops. Ones already in a world get the time when they load.
 */
public final class Summons {
	/** In a Pirate Saber summon's persistent data: {until: game time it leaves, order: when it came, this session}. */
	public static final String DATA = "legionwarex_summon";
	private static final Set<ResourceLocation> SABER_SUMMONS = Set.of(new ResourceLocation("block_factorys_bosses", "pirate_rook"),
			new ResourceLocation("block_factorys_bosses", "crossbow_pirate"));
	private static final AtomicLong ORDER = new AtomicLong();
	/** Owners who just got a summon, per level: their oldest are sent away at the end of the level's tick. */
	private static final Map<ResourceKey<Level>, Set<UUID>> TRIM = new ConcurrentHashMap<>();
	private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
	private static final int LOG_MAX = 40;
	private static final StackWalker WALKER = StackWalker.getInstance();

	private Summons() {
	}

	/** The player who owns this monster, or null for anything that isn't a summon. */
	public static UUID summoner(Entity entity) {
		return entity instanceof Enemy && entity instanceof OwnableEntity owned ? owned.getOwnerUUID() : null;
	}

	public static boolean isSummon(Entity entity) {
		return summoner(entity) != null;
	}

	/** One of the Pirate Saber's two: an owned Pirate Rook or Crossbow Pirate (the Kraken's crew and bands have no owner). */
	public static boolean isSaberSummon(Entity entity) {
		return SABER_SUMMONS.contains(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())) && summoner(entity) != null;
	}

	/** Why 'mob' may not have 'target' as its target ("its owner", "a player", ...), or null when it may. */
	public static String refusal(LivingEntity mob, LivingEntity target) {
		if (target == null || target == mob) {
			return null;
		}

		UUID side = Companions.ownerOf(mob);

		if (side == null) {
			return null;
		}

		if (side.equals(target.getUUID())) {
			return "its owner";
		}

		if (side.equals(Companions.ownerOf(target))) {
			return isSummon(target) ? "its owner's summon" : "its owner's companion or pet";
		}

		if (target instanceof Player && isSummon(mob)) {
			return "a player";
		}

		return null;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onChangeTarget(LivingChangeTargetEvent event) {
		LivingEntity mob = event.getEntity();
		LivingEntity target = event.getNewTarget();

		if (mob.level().isClientSide() || target == null) {
			return;
		}

		String why = refusal(mob, target);

		if (why != null) {
			event.setCanceled(true);
			note(mob, "was stopped from targeting " + why, target);
		}
	}

	/** Once a second: a target got past the event is dropped, and a Pirate Saber summon whose time is up leaves. */
	@SubscribeEvent
	public static void onTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();

		if (entity.tickCount % 20 != 11 || entity.level().isClientSide() || !(entity instanceof Mob mob)) {
			return;
		}

		LivingEntity target = mob.getTarget();

		if (target != null) {
			String why = refusal(mob, target);

			if (why != null) {
				mob.setTarget(null);
				note(mob, "had " + why + " as its target and dropped it", target);
			}
		}

		CompoundTag data = mob.getPersistentData().getCompound(DATA);

		if (data.contains("until") && mob.level().getGameTime() >= data.getLong("until")) {
			leave(mob);
		}
	}

	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !isSaberSummon(mob)) {
			return;
		}

		CompoundTag data = mob.getPersistentData().getCompound(DATA);

		if (!data.contains("until")) {
			data.putLong("until", event.getLevel().getGameTime() + AddonConfig.summonLifetime());
		}

		// Newest last, this session: ones loaded from disk come before any summoned after.
		data.putLong("order", ORDER.incrementAndGet());
		mob.getPersistentData().put(DATA, data);

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0F);
		}

		if (!event.loadedFromDisk()) {
			TRIM.computeIfAbsent(event.getLevel().dimension(), key -> ConcurrentHashMap.newKeySet()).add(summoner(mob));
		}
	}

	@SubscribeEvent
	public static void onLevelTick(TickEvent.LevelTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
			return;
		}

		Set<UUID> owners = TRIM.remove(level.dimension());

		if (owners != null) {
			owners.forEach(owner -> trim(level, owner));
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		TRIM.clear();
		LOGGED.clear();
	}

	/** The owner's Pirate Saber summons in this level, newest first. */
	public static List<Mob> saberSummons(ServerLevel level, UUID owner) {
		List<Mob> mine = new ArrayList<>();

		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof Mob mob && mob.isAlive() && isSaberSummon(mob) && (owner == null || owner.equals(summoner(mob)))) {
				mine.add(mob);
			}
		}

		mine.sort(Comparator.comparingLong((Mob mob) -> mob.getPersistentData().getCompound(DATA).getLong("order")).reversed());
		return mine;
	}

	/** Keeps the owner's newest pirateSaberSummonsAtOnce; the rest leave. */
	public static void trim(ServerLevel level, UUID owner) {
		List<Mob> mine = saberSummons(level, owner);

		for (int i = AddonConfig.summonsAtOnce(); i < mine.size(); i++) {
			leave(mine.get(i));
		}
	}

	/** Gone in a puff of smoke, dropping nothing. */
	public static void leave(Mob mob) {
		if (mob.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + mob.getBbHeight() / 2.0, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		}

		mob.discard();
	}

	/** Ticks until it leaves, or -1 for one with no time set. */
	public static long ticksLeft(Mob mob) {
		CompoundTag data = mob.getPersistentData().getCompound(DATA);
		return data.contains("until") ? Math.max(0L, data.getLong("until") - mob.level().getGameTime()) : -1L;
	}

	/** {@code /legionwarex summons}: the Pirate Saber's pirates in the level, whose, and how long each has left. */
	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("summons").executes(context -> {
			CommandSourceStack source = context.getSource();
			List<Mob> all = saberSummons(source.getLevel(), null);
			StringBuilder text = new StringBuilder(all.size() + " Pirate Saber summon" + (all.size() == 1 ? "" : "s"));

			for (Mob mob : all) {
				Player owner = source.getServer().getPlayerList().getPlayer(summoner(mob));
				text.append(String.format(Locale.ROOT, "; %s #%s of %s at %d %d %d, %d s left", ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()),
						mob.getUUID().toString().substring(0, 8), owner != null ? owner.getScoreboardName() : summoner(mob).toString(),
						mob.getBlockX(), mob.getBlockY(), mob.getBlockZ(), (ticksLeft(mob) + 19) / 20));
			}

			LegionWareX.LOGGER.info("LegionWareX summons: {}", text);
			source.sendSuccess(() -> Component.literal(text.toString()), false);
			return all.size();
		});
	}

	/** A hit on a player that OwnerHits stopped, logged as a refused target is. */
	public static void noteHit(LivingEntity attacker, String what, LivingEntity target) {
		note(attacker, what, target);
	}

	/** Logged once for each kind of summon, refusal and place in the code (the first few frames past the game's own). */
	static void note(LivingEntity mob, String what, LivingEntity other) {
		if (LOGGED.size() >= LOG_MAX) {
			return;
		}

		String calls = calls();
		String key = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()) + "|" + what + "|" + calls;

		if (LOGGED.add(key)) {
			LegionWareX.LOGGER.info("LegionWareX summons: {} ({}, owned by {}) {} ({}); calls: {}", mob.getName().getString(),
					ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()), Companions.ownerOf(mob), what, other.getName().getString(), calls);
		}
	}

	private static String calls() {
		List<String> calls = WALKER.walk(frames -> frames.skip(2)
				.map(frame -> frame.getClassName() + "." + frame.getMethodName())
				.filter(call -> !call.startsWith("net.minecraftforge.eventbus.") && !call.startsWith("java.") && !call.startsWith("jdk.")
						&& !call.startsWith(Summons.class.getName()) && !call.startsWith("net.minecraftforge.common.ForgeHooks")
						&& !call.startsWith("net.minecraftforge.event.ForgeEventFactory"))
				.limit(8).toList());
		return calls.isEmpty() ? "?" : String.join(" < ", calls);
	}
}
