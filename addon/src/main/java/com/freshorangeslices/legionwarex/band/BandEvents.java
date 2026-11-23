package com.freshorangeslices.legionwarex.band;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.night.NightEvents;
import com.freshorangeslices.legionwarex.Despawning;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.world.IllagerCrowding;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Bands in the Overworld: every so often (the config's cadence) a war band may come by day and a pirate band may land
 * on a warm beach near a player. Pirates in a band keep near the rook leading it and despawn like monsters (Bosses'Rise
 * keeps every pirate for good); illagers are patrollers and despawn as vanilla's patrols do.
 *
 * <p>Pirate bands since 0.4.0 (FreshOrangeSlices, 2026-11-22: more and more groups of them, after the illagers were
 * thinned): only on beaches by a warm or lukewarm sea, never within {@value #PIRATE_SPACING} blocks of another pirate
 * band, about one every 20 minutes, and they sail off {@code pirateBandStayTicks} (5 minutes) after landing once no
 * player is within {@value #SAIL_OFF_CLEAR} blocks, also as their chunk loads again (before, a band left behind waited
 * there, since a monster only despawns while a player is near enough for it to tick). The Kraken's crew isn't a band
 * and is left to its mod.
 */
public final class BandEvents {
	/** Bosses'Rise's Kraken ship: pirate bands keep away from one, so their deaths never sound its warning. */
	private static final ResourceLocation KRAKEN_SPAWNER = new ResourceLocation("block_factorys_bosses", "kraken_spawner");
	private static final int KRAKEN_DISTANCE = 80;
	/** Beaches pirate bands land on, and the seas that make one warm. */
	public static final TagKey<Biome> PIRATE_BEACHES = TagKey.create(Registries.BIOME, new ResourceLocation(LegionWareX.MOD_ID, "pirate_beaches"));
	public static final TagKey<Biome> WARM_OCEANS = TagKey.create(Registries.BIOME, new ResourceLocation(LegionWareX.MOD_ID, "warm_oceans"));
	/** How near a warm sea a beach has to be. */
	static final int WARM_SEA_WITHIN = 48;
	/** No pirate band lands this near another. */
	public static final int PIRATE_SPACING = 128;
	/** A pirate band sails off once no player is this near. */
	public static final int SAIL_OFF_CLEAR = 32;

	private static int warTimer = -1;
	private static int pirateTimer = -1;

	private BandEvents() {
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		warTimer = -1;
		pirateTimer = -1;
	}

	@SubscribeEvent
	public static void onLevelTick(TickEvent.LevelTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			return;
		}

		RandomSource random = level.getRandom();

		if (warTimer < 0) {
			warTimer = AddonConfig.warBandEvery() + random.nextInt(1200);
		}

		if (pirateTimer < 0) {
			pirateTimer = AddonConfig.pirateBandEvery() + random.nextInt(1200);
		}

		if (--warTimer <= 0) {
			warTimer = AddonConfig.warBandEvery() + random.nextInt(1200);
			tryWarBand(level, random);
		}

		if (--pirateTimer <= 0) {
			pirateTimer = AddonConfig.pirateBandEvery() + random.nextInt(1200);
			tryPirateBand(level, random);
		}
	}

	private static boolean mayComeAt(ServerLevel level, int fromDay) {
		return level.getDifficulty() != Difficulty.PEACEFUL && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
				&& level.getDayTime() / AddonConfig.DAY >= fromDay;
	}

	private static ServerPlayer somePlayer(ServerLevel level, RandomSource random) {
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator() && p.isAlive()).toList();
		return players.isEmpty() ? null : players.get(random.nextInt(players.size()));
	}

	/** Vanilla's patrol spawner, by day only and at the config's cadence, with a mixed war band. */
	static List<Mob> tryWarBand(ServerLevel level, RandomSource random) {
		if (!AddonConfig.warBands() || !mayComeAt(level, AddonConfig.warBandFromDay()) || !level.isDay()
				|| !level.getGameRules().getBoolean(GameRules.RULE_DO_PATROL_SPAWNING) || random.nextDouble() >= AddonConfig.warBandChance()) {
			return List.of();
		}

		ServerPlayer player = somePlayer(level, random);

		if (player == null || level.isCloseToVillage(player.blockPosition(), 2)) {
			return List.of();
		}

		BlockPos spot = Bands.offset(player.blockPosition(), random, 24, 48);

		if (!loadedAround(level, spot) || level.getBiome(spot).is(BiomeTags.WITHOUT_PATROL_SPAWNS)) {
			return List.of();
		}

		// Rarer where illagers keep coming (world/IllagerCrowding).
		if (!IllagerCrowding.roll(level, spot, random, "a war band")) {
			return List.of();
		}

		List<Mob> band = Bands.spawn(level, BandKind.WAR, spot, random, Bands.vanillaSize(level, spot));

		if (!band.isEmpty()) {
			Bands.log(Bands.describe(BandKind.WAR, band) + " (near " + player.getScoreboardName() + ")");
			IllagerCrowding.arrived(level, spot, "a war band");
		}

		return band;
	}

	/** Whether this band pirate's time is up and no player is near: it sails off. Not a kept one (name tag, lead, riding). */
	public static boolean sailsOff(Mob mob) {
		if (Bands.kindOf(mob) != BandKind.PIRATE || Despawning.keptByPlayer(mob)) {
			return false;
		}

		long landed = Bands.landed(mob);

		if (landed >= 0 && mob.level().getGameTime() - landed < AddonConfig.pirateBandStay()) {
			return false;
		}

		return mob.level().getNearestPlayer(mob, SAIL_OFF_CLEAR) == null;
	}

	/** A pirate band landing on a warm beach, 24 to 56 blocks from a player, none within 128 blocks of another. */
	static List<Mob> tryPirateBand(ServerLevel level, RandomSource random) {
		// A night that holds patrols off (The Old Night: vanilla's night mobs only) holds the pirates off too.
		if (!AddonConfig.pirateBands() || !BandKind.PIRATE.available() || !mayComeAt(level, AddonConfig.pirateBandFromDay())
				|| NightEvents.holdsBandsOff(level) || random.nextDouble() >= AddonConfig.pirateBandChance()) {
			return List.of();
		}

		ServerPlayer player = somePlayer(level, random);

		if (player == null) {
			return List.of();
		}

		for (int attempt = 0; attempt < 16; attempt++) {
			BlockPos column = Bands.offset(player.blockPosition(), random, 24, 56);

			if (!loadedAround(level, column)) {
				continue;
			}

			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);

			if (!level.getFluidState(top.below()).isEmpty() || !warmBeach(level, top)) {
				continue;
			}

			if (nearKrakenShip(level, top) || nearPirateBand(level, top)) {
				return List.of();
			}

			List<Mob> band = Bands.spawn(level, BandKind.PIRATE, top, random, Bands.vanillaSize(level, top));

			if (!band.isEmpty()) {
				Bands.log(Bands.describe(BandKind.PIRATE, band) + " (near " + player.getScoreboardName() + ")");
				return band;
			}
		}

		return List.of();
	}

	private static boolean loadedAround(ServerLevel level, BlockPos pos) {
		return level.hasChunksAt(pos.getX() - 10, pos.getZ() - 10, pos.getX() + 10, pos.getZ() + 10);
	}

	/** A beach in {@link #PIRATE_BEACHES} with a sea in {@link #WARM_OCEANS} within {@value #WARM_SEA_WITHIN} blocks. */
	public static boolean warmBeach(ServerLevel level, BlockPos pos) {
		if (!level.getBiome(pos).is(PIRATE_BEACHES)) {
			return false;
		}

		for (int distance = 16; distance <= WARM_SEA_WITHIN; distance += 16) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx != 0 || dz != 0) && level.getBiome(pos.offset(dx * distance, 0, dz * distance)).is(WARM_OCEANS)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/** Another pirate band within {@value #PIRATE_SPACING} blocks (as far as the world has it loaded). */
	static boolean nearPirateBand(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(PIRATE_SPACING), mob -> Bands.kindOf(mob) == BandKind.PIRATE).isEmpty();
	}

	/** A beach, or land with the sea within 10 blocks. */
	static boolean coast(ServerLevel level, BlockPos pos) {
		Holder<Biome> here = level.getBiome(pos);

		if (here.is(BiomeTags.IS_BEACH)) {
			return true;
		}

		if (here.is(BiomeTags.IS_OCEAN) || here.is(BiomeTags.IS_RIVER)) {
			return false;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (level.getBiome(pos.relative(direction, 10)).is(BiomeTags.IS_OCEAN)) {
				return true;
			}
		}

		return false;
	}

	private static boolean nearKrakenShip(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().findClosest(type -> type.is(KRAKEN_SPAWNER), pos, KRAKEN_DISTANCE, PoiManager.Occupancy.ANY).isPresent();
	}

	/**
	 * A pirate (or any follower that isn't a patroller) keeps near its band's leader; a pirate whose band's time is up
	 * sails off as its chunk loads, if no player is near.
	 */
	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !Bands.inBand(mob) || mob instanceof PatrollingMonster) {
			return;
		}

		if (event.loadedFromDisk() && sailsOff(mob)) {
			event.setCanceled(true);
			return;
		}

		UUID leader = Bands.leaderOf(mob);

		if (leader != null) {
			mob.goalSelector.addGoal(4, new FollowLeaderGoal(mob, leader, 1.0));
		}
	}

	/**
	 * Band members the game would keep for good (pirates) despawn like monsters, unless a player keeps them; a band's
	 * pirate rooks swing sooner ({@link PirateTuning}).
	 */
	@SubscribeEvent
	public static void onTick(LivingEvent.LivingTickEvent event) {
		if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || !Bands.inBand(mob)) {
			return;
		}

		PirateTuning.tick(mob);

		if (mob.tickCount % 20 == 7 && sailsOff(mob)) {
			mob.discard();
		} else if (mob.tickCount % 20 == 7 && mob.isPersistenceRequired() && !Despawning.keptByPlayer(mob)) {
			Despawning.tick(mob);
		}
	}
}
