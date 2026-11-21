package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bosses'Rise's Undying Tentacle, in the add-on's test run (FreshOrangeSlices, 2026-11-21: on the server, sneaking and
 * holding it plays the charge sound and nothing appears). A stand-in player finishes the 2-second charge on open ground,
 * and four Ghost Tentacles, owned by them, have to stand round them a second later. If this passes and the server still
 * shows nothing, the cause is in the pack or the place, not in the mod.
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TentacleGameTests {
	private static final ResourceLocation ITEM = new ResourceLocation("block_factorys_bosses", "undying_tentacle");
	private static final ResourceLocation GHOST = new ResourceLocation("block_factorys_bosses", "ghost_tentacle");

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void undyingTentacleSummonsFour(GameTestHelper helper) {
		Item item = ForgeRegistries.ITEMS.getValue(ITEM);
		EntityType<?> ghost = ForgeRegistries.ENTITY_TYPES.getValue(GHOST);

		// Both registries hand back a default (air, the pig) for an id they don't have.
		if (item == null || ghost == null || !ITEM.equals(ForgeRegistries.ITEMS.getKey(item)) || !GHOST.equals(ForgeRegistries.ENTITY_TYPES.getKey(ghost))) {
			helper.fail("Bosses'Rise's Undying Tentacle isn't in the test run");
			return;
		}

		// Open ground: a floor under the stand-in and nothing round it for 4 blocks (the "empty" template is all air).
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
			}
		}

		FakePlayer stand = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("legionwarex:tentacle".getBytes()), "[Tentacle test]"));
		Vec3 at = helper.absoluteVec(new Vec3(4.0, 1.0, 4.0));
		stand.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
		ItemStack stack = new ItemStack(item);
		stand.setItemInHand(InteractionHand.MAIN_HAND, stack);
		AABB around = new AABB(at, at).inflate(6.0);
		helper.getLevel().getEntities(ghost, around, entity -> true).forEach(Entity::discard);

		// The charge done: what the game calls after the 40 ticks of holding.
		stack.finishUsingItem(helper.getLevel(), stand);
		List<? extends Entity> summoned = helper.getLevel().getEntities(ghost, around, entity -> true);
		helper.assertTrue(summoned.size() == 4, "Four Ghost Tentacles appear when the charge finishes (found " + summoned.size() + ")");
		helper.assertTrue(summoned.stream().allMatch(entity -> entity instanceof OwnableEntity owned && stand.getUUID().equals(owned.getOwnerUUID())),
				"Each Ghost Tentacle belongs to the player who summoned it");

		helper.runAfterDelay(20, () -> {
			List<? extends Entity> left = helper.getLevel().getEntities(ghost, around, Entity::isAlive);
			helper.assertTrue(left.size() == 4, "All four are still there a second later (found " + left.size() + ")");
			left.forEach(Entity::discard);
			stand.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.succeed();
		});
	}
}
