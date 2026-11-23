package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bosses'Rise's Skor Gauntlet (the Yeti's arm) makes its wall of ice spikes, with Bosses'Rise in the add-on's test run
 * (FreshOrangeSlices, 2026-11-22: a friend found it made no wall). By its code the wave comes as right-click is let go,
 * only when the player is still sneaking, the gauntlet isn't cooling down, and a block is within reach of where they
 * look; anything else does nothing, without a sound. A stand-in lets go three ways: standing, sneaking while looking at
 * the sky, then sneaking while looking at the floor two blocks ahead, the only one that makes spikes.
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GauntletGameTests {
	private static final ResourceLocation GAUNTLET = new ResourceLocation("block_factorys_bosses", "ice_gauntlet");

	private static boolean spike(Entity entity) {
		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		return id != null && "block_factorys_bosses".equals(id.getNamespace()) && id.getPath().startsWith("ice_spike");
	}

	@GameTest(template = "empty", timeoutTicks = 120)
	public static void theSkorGauntletMakesItsWallOnlySneakingAndLookingDown(GameTestHelper helper) {
		Item item = ForgeRegistries.ITEMS.getValue(GAUNTLET);

		// The registry hands back air for an id it doesn't have.
		if (item == null || !GAUNTLET.equals(ForgeRegistries.ITEMS.getKey(item))) {
			throw new IllegalStateException("Bosses'Rise's Skor Gauntlet isn't in the test run");
		}

		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}

		ServerLevel level = helper.getLevel();
		FakePlayer stand = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("legionwarex:gauntlet".getBytes()), "[Gauntlet test]"));
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 4.5));
		AABB around = new AABB(at, at).inflate(16.0);
		Set<UUID> seen = new HashSet<>();
		ItemStack gauntlet = new ItemStack(item);
		stand.setItemInHand(InteractionHand.MAIN_HAND, gauntlet);
		int charged = gauntlet.getUseDuration() - 40;

		// Standing, looking at the floor: nothing.
		stand.moveTo(at.x, at.y, at.z, -90.0F, 40.0F);
		stand.setShiftKeyDown(false);
		gauntlet.releaseUsing(level, stand, charged);
		// Sneaking, looking at the sky: nothing.
		stand.moveTo(at.x, at.y, at.z, -90.0F, -60.0F);
		stand.setShiftKeyDown(true);
		gauntlet.releaseUsing(level, stand, charged);
		helper.runAfterDelay(10, () -> {
			level.getEntities((Entity) null, around, GauntletGameTests::spike).forEach(entity -> seen.add(entity.getUUID()));
			helper.assertTrue(seen.isEmpty(), "No wall standing up, nor sneaking at the sky: " + seen.size());
			helper.assertFalse(stand.getCooldowns().isOnCooldown(item), "Neither started the gauntlet's cooldown");
			// Sneaking, looking east at the floor two blocks ahead: the wave.
			stand.moveTo(at.x, at.y, at.z, -90.0F, 40.0F);
			gauntlet.releaseUsing(level, stand, charged);
			helper.assertTrue(stand.getCooldowns().isOnCooldown(item), "The wave started the gauntlet's 4 second cooldown");
		});
		helper.onEachTick(() -> level.getEntities((Entity) null, around, GauntletGameTests::spike).forEach(entity -> seen.add(entity.getUUID())));
		helper.runAfterDelay(60, () -> {
			try {
				helper.assertTrue(!seen.isEmpty(), "Sneaking, looking at the floor, the gauntlet raised its ice spikes");
			} finally {
				level.getEntities((Entity) null, around, GauntletGameTests::spike).forEach(Entity::discard);
			}

			helper.succeed();
		});
	}
}
