package com.freshorangeslices.legionwarex.sentinel;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.Despawning;
import com.freshorangeslices.legionwarex.band.Bands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Sentinels by day: every so often (the config's cadence) each player in the Overworld may get one, 34 to 62 blocks
 * off, on open ground in daylight, picked for the biome it lands in ({@link SentinelTable}). With the defaults that's
 * about one a day near each player, a little fewer in vanilla's own biomes, and never a second while one is still
 * near them. Sometimes it's a pillager scout instead, wandering on its own.
 */
public final class SentinelEvents {
	private static int timer = -1;

	private SentinelEvents() {
	}

	@SubscribeEvent
	public static void onReload(AddReloadListenerEvent event) {
		event.addListener(new SentinelTable());
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		timer = -1;
	}

	@SubscribeEvent
	public static void onLevelTick(TickEvent.LevelTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			return;
		}

		RandomSource random = level.getRandom();

		if (timer < 0) {
			timer = AddonConfig.sentinelEvery() + random.nextInt(1200);
		}

		if (--timer > 0) {
			return;
		}

		timer = AddonConfig.sentinelEvery() + random.nextInt(1200);

		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive()) {
				trySentinel(level, player, random);
			}
		}
	}

	/** One try near a player: the cadence's chance, by day, from the config's first day, with no sentinel near them yet. */
	static Mob trySentinel(ServerLevel level, ServerPlayer player, RandomSource random) {
		if (!AddonConfig.sentinels() || level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
				|| !level.isDay() || level.getDayTime() / AddonConfig.DAY < AddonConfig.sentinelFromDay()
				|| random.nextDouble() >= AddonConfig.sentinelChance() || sentinelNear(level, player.blockPosition(), 96)) {
			return null;
		}

		boolean scout = random.nextDouble() < AddonConfig.scoutShare();

		for (int attempt = 0; attempt < 12; attempt++) {
			BlockPos column = Bands.offset(player.blockPosition(), random, 24, 44);

			if (!level.hasChunksAt(column.getX() - 8, column.getZ() - 8, column.getX() + 8, column.getZ() + 8)) {
				continue;
			}

			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			Holder<Biome> biome = level.getBiome(top);

			if (!level.getFluidState(top.below()).isEmpty() || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || !level.canSeeSky(top)
					|| level.isCloseToVillage(top, 2) || level.getNearestPlayer(top.getX(), top.getY(), top.getZ(), 24.0, false) != null) {
				continue;
			}

			// Vanilla's own biomes get the smallest share: this try lands there only some of the time.
			if (Sentinels.vanillaBiome(biome) && random.nextDouble() >= AddonConfig.sentinelVanillaShare()) {
				return null;
			}

			SentinelTable.Entry entry = SentinelTable.pick(biome, random, scout);

			if (entry == null && scout) {
				entry = SentinelTable.pick(biome, random, false);
			}

			if (entry == null) {
				return null;
			}

			Mob mob = Sentinels.spawn(level, entry, top, true);

			if (mob != null) {
				Sentinels.log(entry.entity() + (entry.roams() ? " (scout)" : "") + " at " + top.toShortString() + " in "
						+ biome.unwrapKey().map(key -> key.location().toString()).orElse("?") + " (near " + player.getScoreboardName() + ")");
				return mob;
			}
		}

		return null;
	}

	static boolean sentinelNear(ServerLevel level, BlockPos pos, double distance) {
		return !level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(distance), Sentinels::isSentinel).isEmpty();
	}

	/** A post-holding sentinel gets its goal whenever it joins a level (goals aren't saved). */
	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !Sentinels.isSentinel(mob)) {
			return;
		}

		BlockPos post = Sentinels.postOf(mob);

		if (post != null) {
			mob.goalSelector.addGoal(1, new HoldPostGoal(mob, post));
		}
	}

	/** Sentinels despawn like monsters (some of these mobs never would), unless a player keeps one. */
	@SubscribeEvent
	public static void onTick(LivingEvent.LivingTickEvent event) {
		if (event.getEntity() instanceof Mob mob && mob.tickCount % 20 == 13 && !mob.level().isClientSide && Sentinels.isSentinel(mob)
				&& !Despawning.keptByPlayer(mob)) {
			Despawning.tick(mob);
		}
	}
}
