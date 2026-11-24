package com.freshorangeslices.legionwarex.night;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.world.IllagerCrowding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The themed nights at work. On a themed night (Overworld only) a monster spawning naturally that isn't one of the night's
 * comes, at the night's strength there ({@code themedNightShare} times its climate's share), as one of the night's mobs
 * instead, on the same spot (one that fits: a solid floor, no water, room for it, dark enough for a monster: {@link
 * #darkEnough}), a group of the same kind staying one kind; the mob's other spawn rules don't apply (the night brings the
 * deep's mobs up to the surface). The night's own mobs
 * spawning naturally are marked too. Beside each night's mob that comes, one more of its kind may ({@code
 * themedNightMoreMobs}), a few blocks away in the dark. Monsters in structures that spawn their own (fortresses, outposts,
 * monuments) and spawns of any other kind (spawners, eggs, commands, raids, other mods' events) are left alone, except
 * that a night with {@code patrols: false} (The Old Night) turns vanilla's illager patrols away, and the add-on's pirate
 * bands wait ({@link #holdsBandsOff}).
 */
public final class NightEvents {
	/** For the game tests: this night as if it were on (number -1), and this share in place of the config's; null for the world's. */
	public static NightTable.Theme testTheme;
	public static Double testShare;
	public static Double testMore;
	/** For the game tests: every spot dark enough (true) or none (false) in place of reading the light; null to read it. */
	public static Boolean testDark;
	public static final int TEST_NIGHT = -1;
	// The last replacement, so a group spawning together stays one kind (not saved).
	private static long groupTick = Long.MIN_VALUE;
	private static BlockPos groupAt = BlockPos.ZERO;
	private static NightTable.Mob groupMob;
	/** The last mob that came in another's place, and the last extra, for /legionwarex night check. */
	static Mob lastReplacement;
	static Mob lastExtra;

	private NightEvents() {
	}

	@SubscribeEvent
	public static void onReload(AddReloadListenerEvent event) {
		event.addListener(new NightTable());
	}

	/** Forgets the last group, so the next replacement is picked afresh (/legionwarex night check's tries, each its own). */
	public static void forgetGroup() {
		groupMob = null;
	}

	/** Tonight's night in this level (Overworld only), or null. */
	public static NightTable.Theme active(ServerLevel level) {
		if (testTheme != null) {
			return testTheme;
		}

		return level.dimension() == Level.OVERWORLD ? NightCycle.of(level).active() : null;
	}

	static int night(ServerLevel level) {
		return testTheme != null ? TEST_NIGHT : NightCycle.of(level).count();
	}

	/** The share of other monsters that come as this night's: its own if it has one, else the config's. */
	static double share(NightTable.Theme theme) {
		if (theme.share() >= 0.0) {
			return theme.share();
		}

		return testShare != null ? testShare : AddonConfig.themedNightShare();
	}

	static double more() {
		return testMore != null ? testMore : AddonConfig.themedNightMoreMobs();
	}

	/** Whether tonight holds the add-on's pirate bands off (a night with patrols: false). */
	public static boolean holdsBandsOff(ServerLevel level) {
		NightTable.Theme theme = active(level);
		return theme != null && !theme.patrols();
	}

	/** A night with patrols: false turns vanilla's illager patrols away (first, so the illager crowding doesn't count them). */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onPatrol(MobSpawnEvent.FinalizeSpawn event) {
		if (event.getSpawnType() == MobSpawnType.PATROL && !event.isSpawnCancelled() && holdsBandsOff(event.getLevel().getLevel())) {
			event.setSpawnCancelled(true);
		}
	}

	@SubscribeEvent
	public static void onLevelTick(TickEvent.LevelTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || testTheme != null) {
			return;
		}

		if (level.getGameTime() % 20L == 7L) {
			NightCycle.of(level).tick(level);
		}

		if (level.getGameTime() % 100L == 53L) {
			NightCycle cycle = NightCycle.of(level);
			NightMobs.sweep(level, cycle);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
		if (event.isSpawnCancelled() || event.getSpawnType() != MobSpawnType.NATURAL) {
			return;
		}

		ServerLevel level = event.getLevel().getLevel();
		NightTable.Theme theme = active(level);
		Mob mob = event.getEntity();

		if (theme == null || mob.getType().getCategory() != MobCategory.MONSTER || NightMobs.isNight(mob)) {
			return;
		}

		BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());

		if (theme.has(mob.getType())) {
			NightMobs.mark(mob, theme, night(level));
			extra(level, theme, theme.entry(mob.getType()), pos, level.getRandom());
			return;
		}

		if (!level.getFluidState(pos).isEmpty() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
				|| IllagerCrowding.inSpawnStructure(level, pos) || !darkEnough(level, pos)) {
			return;
		}

		NightTable.Climate here = NightTable.Climate.at(level, pos);

		if (level.getRandom().nextDouble() >= share(theme) * theme.strength(here)) {
			return;
		}

		if (replace(level, theme, pos, here, level.getRandom()) != null) {
			event.setSpawnCancelled(true);
			extra(level, theme, groupMob, pos, level.getRandom());
		}
	}

	/**
	 * Whether a monster could spawn here by the light, read as vanilla's own test reads it, without its random part: no
	 * torchlight over the dimension's limit (none in the Overworld), and the spot's light no brighter than the most a
	 * monster ever spawns in (7 in the Overworld; a thunderstorm darkens it, as in vanilla). A themed night's mob only comes
	 * where this holds, so a mod's monster that may spawn in torchlight isn't turned into one of the night's inside a lit
	 * house (FreshOrangeSlices, 2026-11-23: zombies in a base lit almost to the top).
	 */
	public static boolean darkEnough(ServerLevel level, BlockPos pos) {
		if (testDark != null) {
			return testDark;
		}

		DimensionType dimension = level.dimensionType();
		int raw = level.isThundering() ? level.getMaxLocalRawBrightness(pos, 10) : level.getMaxLocalRawBrightness(pos);
		return darkEnough(level.getBrightness(LightLayer.BLOCK, pos), dimension.monsterSpawnBlockLightLimit(), raw,
				dimension.monsterSpawnLightTest().getMaxValue());
	}

	/** The light test on its numbers: torchlight within the limit (15: no limit) and the spot's light within its own. */
	public static boolean darkEnough(int blockLight, int blockLimit, int light, int lightLimit) {
		return (blockLimit >= 15 || blockLight <= blockLimit) && light <= lightLimit;
	}

	/** Whether this mob of the night may come at this spot: its boss beaten (if it waits for one), not too many of it near. */
	public static boolean allowed(ServerLevel level, NightCycle cycle, NightTable.Mob mob, BlockPos pos) {
		if (mob.after() != null && !cycle.beaten(level, mob.after())) {
			return false;
		}

		if (mob.max() > 0) {
			EntityType<?> type = mob.type();
			return level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(128.0), other -> other.getType() == type).size() < mob.max();
		}

		return true;
	}

	/**
	 * One of the night's mobs on this spot, spawned as a natural spawn (other mods' spawn handlers see it as one), or null
	 * when none fits there.
	 */
	public static Mob replace(ServerLevel level, NightTable.Theme theme, BlockPos pos, NightTable.Climate here, RandomSource random) {
		NightTable.Mob pick = null;
		NightCycle cycle = NightCycle.of(level);

		// A group spawning together (the same tick, a few blocks apart) stays one kind.
		if (groupMob != null && groupTick == level.getGameTime() && groupAt.closerThan(pos, 10.0) && theme.mobs().contains(groupMob)
				&& allowed(level, cycle, groupMob, pos)) {
			pick = groupMob;
		}

		for (int attempt = 0; attempt < 3; attempt++) {
			if (pick == null) {
				pick = theme.pick(here, random, mob -> allowed(level, cycle, mob, pos));
			}

			if (pick == null) {
				return null;
			}

			Mob mob = spawn(level, theme, pick, pos, random, MobSpawnType.NATURAL, false);

			if (mob != null) {
				groupTick = level.getGameTime();
				groupAt = pos;
				groupMob = pick;
				lastReplacement = mob;
				return mob;
			}

			pick = null;
		}

		return null;
	}

	/**
	 * One more of this night's mob a few blocks from pos (a solid floor, no water, dark enough for a monster, room for it), at the
	 * {@code themedNightMoreMobs} chance; an extra, so the monster cap doesn't count it. Null when it doesn't come.
	 */
	static Mob extra(ServerLevel level, NightTable.Theme theme, NightTable.Mob pick, BlockPos near, RandomSource random) {
		if (pick == null || random.nextDouble() >= more() || !allowed(level, NightCycle.of(level), pick, near)) {
			return null;
		}

		for (int attempt = 0; attempt < 10; attempt++) {
			BlockPos column = near.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);

			// The same height first, then a step up or down.
			for (int dy : new int[] {0, 1, -1}) {
				BlockPos at = column.above(dy);

				if (at.equals(near) || !level.getFluidState(at).isEmpty() || !darkEnough(level, at)
						|| !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
					continue;
				}

				Mob mob = spawn(level, theme, pick, at, random, MobSpawnType.NATURAL, true);

				if (mob != null) {
					lastExtra = mob;
					return mob;
				}
			}
		}

		return null;
	}

	/** One of the night's mobs at pos, marked as the night's (and as an extra) and finalized as this kind of spawn; null when it doesn't fit there. */
	static Mob spawn(ServerLevel level, NightTable.Theme theme, NightTable.Mob pick, BlockPos pos, RandomSource random, MobSpawnType type, boolean extra) {
		if (!pick.present() || !(pick.type().create(level) instanceof Mob mob)) {
			return null;
		}

		BlockState state = level.getBlockState(pos);
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);

		if (!NaturalSpawner.isValidEmptySpawnBlock(level, pos, state, state.getFluidState(), mob.getType()) || !level.noCollision(mob)) {
			mob.discard();
			return null;
		}

		NightMobs.mark(mob, theme, night(level));

		if (extra) {
			NightMobs.markExtra(mob);
		}

		ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(pos), type, null, null);

		if (mob.isSpawnCancelled() || !level.addFreshEntity(mob)) {
			mob.discard();
			return null;
		}

		return mob;
	}

	/** A night's mob gets its health as it joins; a leftover of an ended night doesn't come back with its chunk. */
	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !NightMobs.isNight(mob)) {
			return;
		}

		if (event.loadedFromDisk()) {
			if (testTheme == null && event.getLevel() instanceof ServerLevel level && NightMobs.leaves(mob, NightCycle.of(level), false)) {
				event.setCanceled(true);
			}

			return;
		}

		NightMobs.boost(mob);
	}

	/** A boss a night's mob waits for, killed by a player (or their pet), is kept. */
	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity().level().isClientSide || !(event.getEntity().level() instanceof ServerLevel level)) {
			return;
		}

		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
		Entity killer = event.getSource().getEntity();

		if (id == null || !(killer instanceof Player || killer instanceof OwnableEntity pet && pet.getOwner() instanceof Player)
				|| NightTable.all().values().stream().noneMatch(theme -> theme.mobs().stream().anyMatch(mob -> id.equals(mob.after())))) {
			return;
		}

		NightCycle.of(level).beat(id);
		NightCycle.log(id + " beaten: the night mobs that wait for it can come");
	}

	/** A player coming into the Overworld during a themed night is told which. */
	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		tellArrival(event.getEntity());
	}

	@SubscribeEvent
	public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getTo() == Level.OVERWORLD) {
			tellArrival(event.getEntity());
		}
	}

	private static void tellArrival(Player entity) {
		if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
			NightTable.Theme theme = active(level);

			if (theme != null && testTheme == null) {
				player.sendSystemMessage(Component.literal("Tonight: ").withStyle(ChatFormatting.GRAY).append(theme.title())
						.append(Component.literal(" " + theme.subtitle()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
			}
		}
	}
}
