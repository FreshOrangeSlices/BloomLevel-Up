package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.BloomBridge;
import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.Bonding;
import com.freshorangeslices.bloompets.CompanionInventory;
import com.freshorangeslices.bloompets.PetEvents;
import com.freshorangeslices.bloompets.PetMood;
import com.freshorangeslices.bloompets.PetRegistry;
import com.freshorangeslices.bloompets.PetTrees;
import com.freshorangeslices.bloompets.Pets;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bloom Pets, Phase 5a: companions' mood and hunger, on a real Migamigos companion (Migamigos is in this test run only).
 * Out with its owner it tires and gets hungry as a party pet; anywhere else it rests, as a pet at home; it eats cooked
 * vanilla food; a sulking companion's tree rests.
 */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionCareGameTests {
	private static final ResourceLocation CLAYMORE = new ResourceLocation("migamigos", "claymore");

	private static ServerPlayer player(GameTestHelper helper, boolean oneAndTheSame) {
		ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pets-test"));
		BlockPos abs = helper.absolutePos(new BlockPos(4, 1, 4));
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		BloomBridge.setForTests(player.getUUID(), oneAndTheSame);
		PetTrees.remember(player);
		return player;
	}

	@SuppressWarnings("unchecked")
	private static Mob companion(GameTestHelper helper, ServerPlayer owner) {
		helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(CLAYMORE), "Migamigos is in the test run");
		Mob mob = helper.spawn((EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(CLAYMORE), new BlockPos(2, 2, 2));
		mob.setNoAi(true);
		Pets.claim(mob, owner);
		PetEvents.observe(mob);
		helper.assertTrue(Pets.kind(mob) == Pets.Kind.MODDED, "A Migamigos companion is a modded companion");
		return mob;
	}

	@GameTest(template = "empty")
	public static void aCompanionGetsHungryOnlyOutWithYou(GameTestHelper helper) {
		Mob mob = companion(helper, player(helper, false));
		helper.assertTrue(PetMood.mood(mob) == PetMood.START_MOOD && PetMood.hunger(mob) == PetMood.MAX_HUNGER, "It starts content and fed");

		PetMood.update(mob, 240, true);
		helper.assertTrue(Math.abs(PetMood.hunger(mob) - 19.0) < 1.0E-6, "Out with you: a point of food in 4 minutes (" + PetMood.hunger(mob) + ")");
		helper.assertTrue(PetMood.mood(mob) < PetMood.START_MOOD, "and its mood drops");

		double hunger = PetMood.hunger(mob);
		PetMood.update(mob, 600, false);
		helper.assertTrue(PetMood.hunger(mob) == hunger, "Away from you: no hungrier");
		helper.assertTrue(PetMood.mood(mob) == PetMood.MAX_MOOD, "and it cheers up resting (" + PetMood.mood(mob) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aCompanionEatsCookedFood(GameTestHelper helper) {
		ServerPlayer owner = player(helper, false);
		Mob mob = companion(helper, owner);
		PetMood.setHunger(mob, 10.0);

		ItemStack raw = new ItemStack(Items.BEEF, 2);
		helper.assertTrue(Bonding.feedCompanion(owner, mob, raw) == Bonding.Result.NOT_FOOD && raw.getCount() == 2, "Raw meat isn't its food");

		ItemStack cooked = new ItemStack(Items.COOKED_BEEF, 2);
		helper.assertTrue(Bonding.feedCompanion(owner, mob, cooked) == Bonding.Result.FED_OWN, "Cooked meat is");
		helper.assertTrue(PetMood.hunger(mob) == 16.0 && cooked.getCount() == 1, "A meal: +6 food, one eaten (" + PetMood.hunger(mob) + ")");

		ServerPlayer stranger = player(helper, false);
		helper.assertTrue(Bonding.feedCompanion(stranger, mob, cooked) == Bonding.Result.OWNED_BY_SOMEONE, "Not someone else's companion");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aHungryCompanionEatsFromItsBackpack(GameTestHelper helper) {
		Mob mob = companion(helper, player(helper, false));
		Container backpack = CompanionInventory.container(mob);
		helper.assertTrue(backpack != null, "Migamigos' backpack is found (getInventory)");
		backpack.setItem(0, new ItemStack(Items.BEEF, 3));
		backpack.setItem(1, new ItemStack(Items.COOKED_COD, 2));

		PetMood.setHunger(mob, 16.0);
		PetMood.update(mob, 1, true);
		helper.assertTrue(backpack.getItem(1).getCount() == 2, "Not hungry enough yet (over 14): nothing eaten");

		PetMood.setHunger(mob, 14.0);
		PetMood.update(mob, 1, true);
		helper.assertTrue(backpack.getItem(1).getCount() == 1 && backpack.getItem(0).getCount() == 3,
				"Down to 14 and out with you: it eats the cooked fish, not the raw beef");
		helper.assertTrue(PetMood.hunger(mob) > 19.9, "A meal: +6 (" + PetMood.hunger(mob) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aReleasedCompanionIsWildAgain(GameTestHelper helper) {
		ServerPlayer owner = player(helper, false);
		Mob mob = companion(helper, owner);

		try {
			java.lang.reflect.Field field = null;

			for (Class<?> c = mob.getClass(); c != null && field == null; c = c.getSuperclass()) {
				try {
					field = c.getDeclaredField("followingPlayerUUID");
				} catch (NoSuchFieldException e) {
					// up the hierarchy
				}
			}

			helper.assertTrue(field != null, "Migamigos keeps its owner in followingPlayerUUID");
			field.setAccessible(true);
			field.set(mob, owner.getUUID());
			com.freshorangeslices.bloompets.Release.release(owner, PetRegistry.get(helper.getLevel().getServer()).find(mob.getUUID()));
			helper.assertTrue(field.get(mob) == null, "Released: Migamigos' owner is gone");
		} catch (IllegalAccessException e) {
			helper.fail("followingPlayerUUID: " + e);
		}

		helper.assertTrue(Pets.owner(mob) == null && !Pets.listed(mob), "nobody's, off the list");
		mob.saveWithoutId(new net.minecraft.nbt.CompoundTag());
		helper.succeed();
	}

	/** Released by Bloom Pets 0.5.0 or 0.5.1 (off the list, still its owner's in Migamigos): wild once it loads. */
	@GameTest(template = "empty")
	public static void anEarlierReleaseIsFinished(GameTestHelper helper) {
		ServerPlayer owner = player(helper, false);
		Mob mob = companion(helper, owner);

		try {
			java.lang.reflect.Field field = null;

			for (Class<?> c = mob.getClass(); c != null && field == null; c = c.getSuperclass()) {
				try {
					field = c.getDeclaredField("followingPlayerUUID");
				} catch (NoSuchFieldException e) {
					// up the hierarchy
				}
			}

			field.setAccessible(true);
			field.set(mob, owner.getUUID());
			// As the old release left it: Bloom Pets' data gone but the mark.
			mob.getPersistentData().put("bloompets", new net.minecraft.nbt.CompoundTag());
			mob.getPersistentData().getCompound("bloompets").putUUID("releasedBy", owner.getUUID());
			helper.assertTrue(!Pets.listed(mob), "Off the list, though Migamigos still has its owner");

			com.freshorangeslices.bloompets.Release.finishOldRelease(mob);
			helper.assertTrue(field.get(mob) == null, "As it loads: wild in Migamigos too");
			helper.assertFalse(mob.getPersistentData().getCompound("bloompets").contains("releasedBy"), "and the mark is gone");
		} catch (IllegalAccessException e) {
			helper.fail("followingPlayerUUID: " + e);
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aSulkingCompanionsTreeRests(GameTestHelper helper) {
		ServerPlayer owner = player(helper, true);
		Mob mob = companion(helper, owner);
		PetRegistry.Record record = PetRegistry.get(helper.getLevel().getServer()).find(mob.getUUID());
		helper.assertTrue(record != null, "It's on its owner's list");
		PetTrees.pick(owner, record, "axe");
		helper.assertTrue(PetTrees.tree(mob) == PetTrees.Tree.AXE && PetTrees.treeLevel(mob) >= 1, "Its tree works (" + PetTrees.treeLevel(mob) + ")");

		PetMood.setMood(mob, 10.0);
		helper.assertTrue(PetMood.sad(mob) && PetTrees.treeLevel(mob) < 0, "Sulking: its tree rests");
		PetMood.setMood(mob, 60.0);
		helper.assertTrue(PetTrees.treeLevel(mob) >= 1, "and works again once it cheers up");
		helper.succeed();
	}
}
