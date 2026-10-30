package com.freshorangeslices.legionwarex.sentinel;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A sentinel: one harder mob, alone, out by day. It holds its spot and watches ({@link HoldPostGoal}), takes on a player
 * who comes within its sight (16 blocks, whatever its own reach) and goes back when they're gone, and despawns like a
 * monster once players are far away. Undead ones get a plain helmet that never wears out and never drops, which keeps
 * the sun off them the way Minecraft's own helmet rule does. A pillager scout (a roamer) wanders instead.
 * <p>
 * Each one carries the scoreboard tag {@link #TAG} and, in its persistent data, its post (where it was placed).
 */
public final class Sentinels {
	public static final String TAG = "legionwarex_sentinel";
	public static final String DATA = "legionwarex_sentinel";
	/** How far a sentinel notices players, in blocks. */
	public static final double SIGHT = 16.0;
	private static final UUID SIGHT_ID = UUID.fromString("0f3c7a91-2b6e-4d8a-9c1f-5e7a3b2d9c44");

	private Sentinels() {
	}

	public static boolean isSentinel(Entity entity) {
		return entity.getTags().contains(TAG);
	}

	/** Its post, or null for a roamer (or something that isn't a sentinel). */
	public static BlockPos postOf(Entity entity) {
		CompoundTag data = entity.getPersistentData().getCompound(DATA);
		return data.contains("post") ? NbtUtils.readBlockPos(data.getCompound("post")) : null;
	}

	/** Whether a sentinel can be placed here: room to stand, ground under it, open sky (they stand outdoors). */
	public static boolean canStand(ServerLevel level, BlockPos pos, Mob mob) {
		BlockState state = level.getBlockState(pos);
		BlockPos below = pos.below();
		return NaturalSpawner.isValidEmptySpawnBlock(level, pos, state, state.getFluidState(), mob.getType())
				&& level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
				&& level.getFluidState(below).isEmpty()
				&& level.noCollision(mob, mob.getType().getAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
	}

	/**
	 * A sentinel of this kind standing at pos (the block it stands in), or null when it can't stand there or its mod
	 * isn't installed. Joins the level here; its post goal is given as it joins ({@link SentinelEvents}).
	 */
	public static Mob spawn(ServerLevel level, SentinelTable.Entry entry, BlockPos pos, boolean checkRoom) {
		if (!entry.present() || !(entry.type().create(level) instanceof Mob mob)) {
			return null;
		}

		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);

		if (checkRoom && !canStand(level, pos, mob)) {
			mob.discard();
			return null;
		}

		// The mob's own finalizeSpawn, not Forge's spawn event (a night-only rule for natural spawns would turn it away).
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
		dress(mob);
		mob.addTag(TAG);
		CompoundTag data = new CompoundTag();

		if (!entry.roams()) {
			data.put("post", NbtUtils.writeBlockPos(pos));
			shortenSight(mob);
		}

		mob.getPersistentData().put(DATA, data);
		return level.addFreshEntity(mob) ? mob : null;
	}

	/** An undead sentinel's helmet: kept on, never worn out by the sun, never dropped. */
	static void dress(Mob mob) {
		if (mob.getMobType() != MobType.UNDEAD) {
			return;
		}

		ItemStack head = mob.getItemBySlot(EquipmentSlot.HEAD);

		if (head.isEmpty()) {
			head = new ItemStack(Items.IRON_HELMET);
			mob.setItemSlot(EquipmentSlot.HEAD, head);
		}

		// Its own helmet (a few mobs come with one) is made sun-proof the same way; neither ever drops, so no player gets
		// an unbreakable helmet out of it.
		if (head.isDamageableItem()) {
			head.getOrCreateTag().putBoolean("Unbreakable", true);
		}

		mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
	}

	/** A post-holding sentinel notices players within 16 blocks at most (its own follow range may reach much further). */
	static void shortenSight(Mob mob) {
		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);

		if (range != null && range.getModifier(SIGHT_ID) == null && range.getBaseValue() > SIGHT) {
			range.addPermanentModifier(new AttributeModifier(SIGHT_ID, "LegionWareX sentinel sight", SIGHT - range.getBaseValue(),
					AttributeModifier.Operation.ADDITION));
		}
	}

	/** Whether a biome is vanilla's (sentinels come less often there; the pack's own biomes are where the new things are). */
	public static boolean vanillaBiome(Holder<Biome> biome) {
		return biome.unwrapKey().map(key -> key.location().getNamespace().equals("minecraft")).orElse(true);
	}

	static void log(String message) {
		LegionWareX.LOGGER.info("LegionWareX sentinel: {}", message);
	}
}
