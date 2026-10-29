package com.freshorangeslices.legionwarex.band;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Making a band: the leader first (no band without one), then its followers a few blocks apart, each on the ground
 * like a patrol member (an empty block to stand in, a block below a mob can spawn on, little torchlight). Spawned as a
 * patrol (MobSpawnType.PATROL): illagers then patrol, the leader with the ominous banner heading off and its
 * followers keeping up, as vanilla's patrols do. Pirates aren't patrollers: they keep near their captain
 * ({@link FollowLeaderGoal}). Every member carries the scoreboard tag {@link #TAG} and, in its persistent data, the
 * band's kind and its leader.
 */
public final class Bands {
	/** On every band member (scoreboard tags are cheap to check each tick, unlike persistent data). */
	public static final String TAG = "legionwarex_band";
	/** In its persistent data: {kind, leader}. */
	public static final String DATA = "legionwarex_band";

	private Bands() {
	}

	/** How many: vanilla's patrol size (the local difficulty's effective difficulty, rounded up, plus one). */
	public static int vanillaSize(ServerLevel level, BlockPos pos) {
		return (int) Math.ceil(level.getCurrentDifficultyAt(pos).getEffectiveDifficulty()) + 1;
	}

	/** A spot 'near' to 'far' blocks away (each way, at random) from a position, on the same level. */
	public static BlockPos offset(BlockPos from, RandomSource random, int near, int far) {
		int dx = (near + random.nextInt(far - near + 1)) * (random.nextBoolean() ? -1 : 1);
		int dz = (near + random.nextInt(far - near + 1)) * (random.nextBoolean() ? -1 : 1);
		return from.offset(dx, 0, dz);
	}

	/**
	 * A band of 'size' at the top of the column at 'at' (members spread up to 4 blocks apart, on the ground there).
	 * Empty when the leader can't stand there or its mod isn't installed; followers that can't stand are left out.
	 */
	public static List<Mob> spawn(ServerLevel level, BandKind kind, BlockPos at, RandomSource random, int size) {
		List<Mob> band = new ArrayList<>();
		EntityType<?> leaderType = kind.pickLeader(random);

		if (leaderType == null) {
			return band;
		}

		BlockPos.MutableBlockPos pos = at.mutable();
		UUID leader = null;

		for (int i = 0; i < Math.max(1, size); i++) {
			pos.setY(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY());
			Mob mob = member(level, kind, i == 0 ? leaderType : kind.pickMember(random), pos.immutable(), random, leader);

			if (mob == null && i == 0) {
				return band;
			}

			if (mob != null) {
				if (i == 0) {
					leader = mob.getUUID();
				}

				band.add(mob);
			}

			pos.setX(pos.getX() + random.nextInt(5) - random.nextInt(5));
			pos.setZ(pos.getZ() + random.nextInt(5) - random.nextInt(5));
		}

		return band;
	}

	/** Whether a band member can stand here: room, ground to spawn on, no more torchlight than a patrol allows. */
	public static boolean canStand(ServerLevel level, BlockPos pos, EntityType<?> type) {
		BlockState state = level.getBlockState(pos);
		BlockPos below = pos.below();

		return NaturalSpawner.isValidEmptySpawnBlock(level, pos, state, state.getFluidState(), type)
				&& level.getBlockState(below).isValidSpawn(level, below, type)
				&& level.getBrightness(LightLayer.BLOCK, pos) <= 8;
	}

	private static Mob member(ServerLevel level, BandKind kind, EntityType<?> type, BlockPos pos, RandomSource random, UUID leader) {
		if (type == null || !canStand(level, pos, type)) {
			return null;
		}

		Entity entity = type.create(level);

		if (!(entity instanceof Mob mob)) {
			if (entity != null) {
				entity.discard();
			}

			return null;
		}

		// A patrol leader is chosen before the spawn is finalized: that's when it takes up the banner.
		if (leader == null && mob instanceof PatrollingMonster patroller) {
			patroller.setPatrolLeader(true);
			patroller.findPatrolTarget();
		}

		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.PATROL, null, null);
		mob.addTag(TAG);
		CompoundTag data = new CompoundTag();
		data.putString("kind", kind.id);

		if (leader != null) {
			data.putUUID("leader", leader);
		}

		mob.getPersistentData().put(DATA, data);

		// Joining the level is when a pirate follower is given its captain to keep near (BandEvents.onJoin).
		return level.addFreshEntity(mob) ? mob : null;
	}

	/** The band's leader, or null for the leader itself (or something not in a band). */
	public static UUID leaderOf(Entity entity) {
		CompoundTag data = entity.getPersistentData().getCompound(DATA);
		return data.hasUUID("leader") ? data.getUUID("leader") : null;
	}

	public static boolean inBand(Entity entity) {
		return entity.getTags().contains(TAG);
	}

	/** "war band of 4 at x, y, z: minecraft:pillager (leader, banner), ..." for logs and the command. */
	public static String describe(BandKind kind, List<Mob> band) {
		if (band.isEmpty()) {
			return "no " + kind.id + " band";
		}

		BlockPos at = band.get(0).blockPosition();
		return kind.id + " band of " + band.size() + " at " + at.getX() + ", " + at.getY() + ", " + at.getZ() + ": "
				+ band.stream().map(Bands::member).collect(Collectors.joining(", "));
	}

	private static String member(Mob mob) {
		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
		List<String> notes = new ArrayList<>();

		if (leaderOf(mob) == null) {
			notes.add("leader");
		}

		if (mob.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof BannerItem) {
			notes.add("banner");
		}

		return id + (notes.isEmpty() ? "" : " (" + String.join(", ", notes) + ")");
	}

	static void log(String message) {
		LegionWareX.LOGGER.info("LegionWareX band: {}", message);
	}
}
