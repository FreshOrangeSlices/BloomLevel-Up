package com.freshorangeslices.bloomlevelup.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.utility.PlacedBlocks;
import com.freshorangeslices.bloomlevelup.skill.utility.UtilityIds;
import com.freshorangeslices.bloomlevelup.skill.utility.UtilitySkill;

/**
 * Utility mechanics on a real server, using Fabric fake players (survival mode, so blocks really break,
 * tools really wear out and XP is really awarded).
 */
public class UtilityGameTests {
	private static final double EPS = 1e-3;

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer player(GameTestHelper helper, ItemStack mainHand) {
		ServerPlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bloom-test"));
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
		return player;
	}

	/** Max out the given categories (10 Major Levels each) and buy Majors. */
	private static void grant(ServerPlayer player, List<String> categories, String... majors) {
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);

		for (String category : categories) {
			service.addXp(profile, category, 1.0e9);
		}

		for (String major : majors) {
			ActionResult result = service.purchaseMajor(profile, major);

			if (!result.ok()) {
				throw new IllegalStateException("Couldn't buy " + major + ": " + result.outcome());
			}
		}
	}

	private static void points(ServerPlayer player, String stat, int amount) {
		ActionResult result = BloomServices.progression().allocate(BloomAttachments.profile(player), stat, ProgressionService.Pool.NATURAL, amount);

		if (!result.ok()) {
			throw new IllegalStateException("Couldn't allocate " + stat + ": " + result.outcome());
		}
	}

	private static double utilityXp(ServerPlayer player) {
		return BloomAttachments.profile(player).category(SkillXp.UTILITY).xp();
	}

	private static int count(List<ItemStack> drops, net.minecraft.world.item.Item item) {
		int total = 0;

		for (ItemStack stack : drops) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	private static List<ItemStack> drops(GameTestHelper helper, BlockPos relative, BlockState state, ServerPlayer player) {
		BlockPos pos = helper.absolutePos(relative);
		return Block.getDrops(state, helper.getLevel(), pos, null, player, player.getMainHandItem());
	}

	// ------------------------------------------------------------------ XP + anti-exploit

	@GameTest
	public void naturalBlocksGiveXpPlacedOnesDont(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_PICKAXE));
		ServerLevel level = helper.getLevel();

		helper.setBlock(new BlockPos(1, 1, 1), Blocks.DIAMOND_ORE);
		player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(1, 1, 1)));
		helper.assertTrue(Math.abs(utilityXp(player) - 12.0) < EPS, "Diamond ore = 12 Utility XP, had " + utilityXp(player));

		BlockPos placed = helper.absolutePos(new BlockPos(2, 1, 1));
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.DIAMOND_ORE);
		PlacedBlocks.mark(level, placed);
		player.gameMode.destroyBlock(placed);
		helper.assertTrue(Math.abs(utilityXp(player) - 12.0) < EPS, "A placed ore gives no XP, had " + utilityXp(player));
		helper.assertFalse(PlacedBlocks.isPlaced(level, placed), "Breaking a placed block forgets it");

		ServerPlayer barehanded = player(helper, ItemStack.EMPTY);
		helper.setBlock(new BlockPos(3, 1, 1), Blocks.STONE);
		barehanded.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 1)));
		helper.assertTrue(utilityXp(barehanded) < EPS, "Stone by hand (no drop) gives no XP");
		helper.succeed();
	}

	@GameTest
	public void placingWithAnItemIsRemembered(GameTestHelper helper) {
		ItemStack ore = new ItemStack(Items.DIAMOND_ORE, 4);
		ServerPlayer player = player(helper, ore);
		BlockPos base = new BlockPos(1, 1, 1);
		helper.setBlock(base, Blocks.STONE);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(base)).add(0, 0.5, 0), Direction.UP,
				helper.absolutePos(base), false);
		ore.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

		helper.assertBlockPresent(Blocks.DIAMOND_ORE, base.above());
		helper.assertTrue(PlacedBlocks.isPlaced(helper.getLevel(), helper.absolutePos(base.above())), "Placed ore is remembered");
		helper.assertFalse(PlacedBlocks.isPlaced(helper.getLevel(), helper.absolutePos(base)), "Untouched blocks are not");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Yield

	@GameTest
	public void yieldCopiesTheFinalDrop(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_PICKAXE));
		grant(player, List.of(SkillXp.UTILITY));
		points(player, UtilityIds.YIELD, 100);
		BlockState ore = Blocks.DIAMOND_ORE.defaultBlockState();

		helper.assertValueEqual(2, count(drops(helper, new BlockPos(1, 1, 1), ore, player), Items.DIAMOND), "Yield 100 = 1 extra copy");

		PlacedBlocks.mark(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 1)));
		helper.assertValueEqual(1, count(drops(helper, new BlockPos(2, 1, 1), ore, player), Items.DIAMOND), "No Yield on placed blocks");

		BloomServices.progression().toggleStat(BloomAttachments.profile(player), UtilityIds.YIELD);
		helper.assertValueEqual(1, count(drops(helper, new BlockPos(1, 1, 1), ore, player), Items.DIAMOND), "Yield switched off");
		helper.succeed();
	}

	@GameTest
	public void cropsOnlyCountWhenGrown(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		grant(player, List.of(SkillXp.UTILITY));
		points(player, UtilityIds.YIELD, 100);
		BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7);
		BlockState young = Blocks.WHEAT.defaultBlockState();

		helper.assertValueEqual(2, count(drops(helper, new BlockPos(1, 1, 1), ripe, player), Items.WHEAT), "Ripe wheat is doubled");
		helper.assertValueEqual(1, count(drops(helper, new BlockPos(1, 1, 1), young, player), Items.WHEAT_SEEDS), "Unripe wheat is untouched");
		helper.assertFalse(UtilitySkill.tracksPlacement(young), "Planted crops are never treated as placed");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Efficiency / Preservation / Shovel Mastery

	@GameTest
	public void efficiencyIsBreakSpeedAndShovelMasteryBoostsIt(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_PICKAXE));
		grant(player, List.of(SkillXp.UTILITY), UtilityIds.SHOVEL_MASTERY);
		points(player, UtilityIds.EFFICIENCY, 100);
		StatEffects.refresh(player);
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED) - 2.0) < EPS, "100 Efficiency = 2x break speed");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
		StatEffects.refresh(player);
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED) - 2.2) < EPS,
				"Shovel Mastery: 120 Efficiency with a shovel, was " + player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED));
		helper.succeed();
	}

	@GameTest
	public void shovelMasteryDigsUpTreasure(GameTestHelper helper) {
		for (String pool : UtilitySkill.TREASURE_POOLS) {
			helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(UtilitySkill.treasureTable(pool)) != LootTable.EMPTY,
					"The " + pool + " treasure table loaded");
		}

		helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(UtilitySkill.EXCAVATION_DEFAULT) != LootTable.EMPTY,
				"The default treasure table loaded");

		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SHOVEL));
		grant(player, List.of(SkillXp.UTILITY), UtilityIds.SHOVEL_MASTERY);
		points(player, UtilityIds.YIELD, 100);
		int dirt = 0;
		int other = 0;

		for (int i = 0; i < 1000; i++) {
			for (ItemStack stack : drops(helper, new BlockPos(1, 1, 1), Blocks.DIRT.defaultBlockState(), player)) {
				if (stack.is(Items.DIRT)) {
					dirt += stack.getCount();
				} else {
					other++;
				}
			}
		}

		helper.assertValueEqual(1000, dirt, "Dirt itself is never multiplied");
		// 10% + 120 Yield x 2% = 12.4% per block -> ~124 finds in 1000
		helper.assertTrue(other > 70 && other < 190, "About 124 treasure finds expected, found " + other);
		helper.succeed();
	}

	// ------------------------------------------------------------------ Vein Mining / Timber

	@GameTest
	public void veinMiningBreaksTheWholeVein(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_PICKAXE));
		grant(player, List.of(SkillXp.COMBAT), UtilityIds.VEIN_MINING);

		for (int x = 0; x < 5; x++) {
			helper.setBlock(new BlockPos(x, 1, 1), Blocks.IRON_ORE);
		}

		helper.setBlock(new BlockPos(5, 1, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 2, 2), Blocks.IRON_ORE); // diagonal neighbour joins the vein

		player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(0, 1, 1)));
		UtilitySkill.runChains();

		for (int x = 0; x < 5; x++) {
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, 1, 1));
		}

		helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 2, 2));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(5, 1, 1));
		helper.assertValueEqual(6, player.getMainHandItem().getDamageValue(), "Each block costs its own durability");
		helper.assertTrue(Math.abs(utilityXp(player) - 24.0) < EPS, "Every ore in the vein gives XP (6 x 4), had " + utilityXp(player));
		helper.succeed();
	}

	@GameTest
	public void sneakingSkipsVeinMining(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_PICKAXE));
		grant(player, List.of(SkillXp.COMBAT), UtilityIds.VEIN_MINING);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.IRON_ORE);
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.IRON_ORE);
		player.setShiftKeyDown(true);
		player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(1, 1, 1)));
		UtilitySkill.runChains();
		helper.assertBlockPresent(Blocks.IRON_ORE, new BlockPos(2, 1, 1));
		helper.succeed();
	}

	@GameTest
	public void timberFellsNaturalTrees(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.DIAMOND_AXE));
		grant(player, List.of(SkillXp.COMBAT), UtilityIds.TIMBER);

		for (int y = 1; y <= 5; y++) {
			helper.setBlock(new BlockPos(1, y, 1), Blocks.OAK_LOG);
		}

		helper.setBlock(new BlockPos(2, 5, 1), Blocks.OAK_LOG); // a branch
		helper.setBlock(new BlockPos(1, 6, 1), Blocks.OAK_LEAVES);

		player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(1, 1, 1)));
		UtilitySkill.runChains();

		for (int y = 1; y <= 5; y++) {
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, y, 1));
		}

		helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 5, 1));
		helper.assertValueEqual(6, player.getMainHandItem().getDamageValue(), "One durability per log");
		helper.succeed();
	}

	@GameTest
	public void timberIgnoresLogBuildings(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.DIAMOND_AXE));
		grant(player, List.of(SkillXp.COMBAT), UtilityIds.TIMBER);

		// A pillar with no leaves is not a tree.
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(new BlockPos(1, y, 1), Blocks.OAK_LOG);
		}

		player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(1, 1, 1)));
		UtilitySkill.runChains();
		helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(1, 2, 1));

		// Placed logs are skipped even when leaves are next to them.
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(new BlockPos(3, y, 3), Blocks.OAK_LOG);
			PlacedBlocks.mark(helper.getLevel(), helper.absolutePos(new BlockPos(3, y, 3)));
		}

		helper.setBlock(new BlockPos(3, 4, 3), Blocks.OAK_LEAVES);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.OAK_LOG); // the cut log itself is natural
		PlacedBlocks.clear(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)));
		player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)));
		UtilitySkill.runChains();
		helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(3, 2, 3));
		helper.succeed();
	}

	// ------------------------------------------------------------------ Growth Aura / Master Shearing / Triple Catch

	@GameTest
	public void growthAuraGrowsNearbyPlants(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_HOE));
		grant(player, List.of(SkillXp.COMBAT), UtilityIds.GROWTH_AURA);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.SAND);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.CACTUS);
		BlockState before = helper.getBlockState(new BlockPos(3, 2, 3));
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.FARMLAND);
		helper.setBlock(new BlockPos(2, 2, 3), Blocks.WHEAT);

		BloomConfig config = BloomServices.config();
		double chance = config.growthAuraChance;
		config.growthAuraChance = 1.0;

		try {
			int grown = UtilitySkill.growthAura(player);
			helper.assertTrue(grown >= 1, "The cactus got a growth tick");
			helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 3)) != before, "Cactus state advanced");
			helper.assertValueEqual(1, ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getAge(helper.getBlockState(new BlockPos(2, 2, 3))),
					"Wheat jumped a whole growth stage");

			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			helper.assertValueEqual(0, UtilitySkill.growthAura(player), "No hoe, no aura");
		} finally {
			config.growthAuraChance = chance;
		}

		helper.succeed();
	}

	@GameTest
	public void masterShearingFlipsACoin(GameTestHelper helper) {
		ItemStack shears = new ItemStack(Items.SHEARS);
		ServerPlayer player = player(helper, shears);
		grant(player, List.of(SkillXp.COMBAT), UtilityIds.MASTER_SHEARING);
		Sheep sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(2, 1, 2));
		int stayedShearable = 0;

		for (int i = 0; i < 100; i++) {
			sheep.setSheared(false);
			UtilitySkill.expectShear(player, InteractionHand.MAIN_HAND, sheep);
			sheep.shear(helper.getLevel(), SoundSource.PLAYERS, shears);
			UtilitySkill.settleShears();

			if (!sheep.isSheared()) {
				stayedShearable++;
			}
		}

		helper.assertTrue(stayedShearable > 30 && stayedShearable < 70, "About half the shears keep the wool, kept " + stayedShearable);
		helper.assertValueEqual(100, shears.getDamageValue(), "Master Shearing's extra durability per shear");
		// 100 shears x 2.5 XP = 250 XP: past Utility level 3 (50 + 60 + 70 = 180).
		helper.assertValueEqual(3, BloomAttachments.profile(player).category(SkillXp.UTILITY).level(), "Shearing gives Utility XP");
		helper.succeed();
	}

	@GameTest
	public void tripleCatchRollsThreeTimes(GameTestHelper helper) {
		ItemStack rod = new ItemStack(Items.FISHING_ROD);
		ServerPlayer player = player(helper, rod);
		grant(player, List.of(SkillXp.COMBAT), UtilityIds.TRIPLE_CATCH);
		FishingHook hook = new FishingHook(player, helper.getLevel(), 0, 0);
		LootParams params = new LootParams.Builder(helper.getLevel())
				.withParameter(LootContextParams.ORIGIN, hook.position())
				.withParameter(LootContextParams.TOOL, rod)
				.withParameter(LootContextParams.THIS_ENTITY, hook)
				.create(LootContextParamSets.FISHING);
		List<ItemStack> loot = helper.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params);

		helper.assertValueEqual(3, loot.size(), "Three independent catches");
		helper.assertValueEqual(2, rod.getDamageValue(), "Two extra durability (vanilla adds its own 1 after)");
		helper.assertTrue(Math.abs(utilityXp(player) - 8.0) < EPS, "One catch's worth of Utility XP");
		helper.succeed();
	}

	@GameTest
	public void majorsCarryTheirHubPositions(GameTestHelper helper) {
		var definitions = com.freshorangeslices.bloomlevelup.data.BloomServices.definitions();
		var triple = definitions.major("bloomlevelup:utility/triple_catch").orElseThrow().hubPosition();
		helper.assertTrue(triple.isPresent() && triple.get().x() == 80 && triple.get().y() == 232, "Triple Catch sits by the pond: " + triple);
		helper.assertTrue(definitions.major("bloomlevelup:combat/catch").orElseThrow().hubPosition().isEmpty(), "Combat is a tree: no set spots");
		// The client gets the same spots in the definitions snapshot.
		var json = com.freshorangeslices.bloomlevelup.network.SnapshotCodecs.encodeDefinitions(definitions,
				com.freshorangeslices.bloomlevelup.data.BloomServices.config());
		helper.assertTrue(json.contains("hub_position"), "Synced to clients");
		helper.succeed();
	}
}
