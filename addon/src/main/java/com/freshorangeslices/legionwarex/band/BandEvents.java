package com.freshorangeslices.legionwarex.band;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.Despawning;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Bands in the Overworld: every so often (the config's cadence) a war band may come by day and a pirate band may land
 * on a coast near a player. Pirates in a band keep near their captain and despawn like monsters (Bosses'Rise keeps
 * every pirate for good); illagers are patrollers and despawn as vanilla's patrols do.
 */
public final class BandEvents {
	/** Bosses'Rise's Kraken ship: pirate bands keep away from one, so their deaths never sound its warning. */
	private static final ResourceLocation KRAKEN_SPAWNER = new ResourceLocation("block_factorys_bosses", "kraken_spawner");
	private static final int KRAKEN_DISTANCE = 80;

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

		List<Mob> band = Bands.spawn(level, BandKind.WAR, spot, random, Bands.vanillaSize(level, spot));

		if (!band.isEmpty()) {
			Bands.log(Bands.describe(BandKind.WAR, band) + " (near " + player.getScoreboardName() + ")");
		}

		return band;
	}

	/** A pirate band landing on a beach or a shore by the sea, 24 to 56 blocks from a player near a coast. */
	static List<Mob> tryPirateBand(ServerLevel level, RandomSource random) {
		if (!AddonConfig.pirateBands() || !BandKind.PIRATE.available() || !mayComeAt(level, AddonConfig.pirateBandFromDay())
				|| random.nextDouble() >= AddonConfig.pirateBandChance()) {
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

			if (!level.getFluidState(top.below()).isEmpty() || !coast(level, top)) {
				continue;
			}

			if (nearKrakenShip(level, top)) {
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

	/** A pirate (or any follower that isn't a patroller) keeps near its band's leader. */
	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !Bands.inBand(mob) || mob instanceof PatrollingMonster) {
			return;
		}

		UUID leader = Bands.leaderOf(mob);

		if (leader != null) {
			mob.goalSelector.addGoal(4, new FollowLeaderGoal(mob, leader, 1.0));
		}
	}

	/** Band members the game would keep for good (pirates) despawn like monsters, unless a player keeps them. */
	@SubscribeEvent
	public static void onTick(LivingEvent.LivingTickEvent event) {
		if (event.getEntity() instanceof Mob mob && mob.tickCount % 20 == 7 && !mob.level().isClientSide && Bands.inBand(mob)
				&& mob.isPersistenceRequired() && !Despawning.keptByPlayer(mob)) {
			Despawning.tick(mob);
		}
	}
}
