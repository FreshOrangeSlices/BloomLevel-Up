package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.Bonding;
import com.freshorangeslices.bloompets.PetEvents;
import com.freshorangeslices.bloompets.PetLevels;
import com.freshorangeslices.bloompets.PetMood;
import com.freshorangeslices.bloompets.PetRegistry;
import com.freshorangeslices.bloompets.PetStorage;
import com.freshorangeslices.bloompets.Pets;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Bloom Pets, Phase 3: mood and hunger (feeding, patting, eating from storage, sulking, loving you). */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PetCareGameTests {
	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pets-test"));
		BlockPos abs = helper.absolutePos(at);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static <T extends Mob> T pet(GameTestHelper helper, EntityType<T> type, ServerPlayer owner, BlockPos at, int level, boolean party) {
		T mob = helper.spawn(type, at);
		mob.setNoAi(true);
		Pets.claim(mob, owner);
		PetRegistry.Record record = PetEvents.observe(mob);

		if (party) {
			record.party = true;
			Pets.setParty(mob, true);
		}

		PetLevels.setLevel(mob, level);
		mob.setHealth(mob.getMaxHealth());
		return mob;
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0E-3;
	}

	@GameTest(template = "empty")
	public static void moodDropsInThePartyAndComesBackAtHome(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 1, true);
		PetMood.setMood(wolf, 50.0);
		PetMood.update(wolf, 180);
		helper.assertTrue(near(PetMood.mood(wolf), 49.0) && near(PetMood.hunger(wolf), 19.25),
				"In the party: a point of mood and 0.75 hunger in 3 minutes, not " + PetMood.mood(wolf) + " and " + PetMood.hunger(wolf));
		Cow cow = pet(helper, EntityType.COW, owner, new BlockPos(5, 2, 1), 1, false);
		PetMood.setMood(cow, 50.0);
		PetMood.update(cow, 60);
		helper.assertTrue(near(PetMood.mood(cow), 60.0) && near(PetMood.hunger(cow), 20.0), "At home a fed pet cheers up 10 a minute, and isn't hungrier");
		PetMood.setHunger(cow, 5.0);
		PetMood.update(cow, 60);
		helper.assertTrue(near(PetMood.mood(cow), 60.0), "but not a hungry one");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void hungerMakesItGlumFaster(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Wolf hungry = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 1, true);
		Wolf starving = pet(helper, EntityType.WOLF, owner, new BlockPos(5, 2, 1), 1, true);
		PetMood.setMood(hungry, 50.0);
		PetMood.setHunger(hungry, 5.0);
		PetMood.setMood(starving, 50.0);
		PetMood.setHunger(starving, 0.0);
		PetMood.update(hungry, 180);
		PetMood.update(starving, 180);
		helper.assertTrue(near(PetMood.mood(hungry), 48.0), "Hungry: twice as fast, " + PetMood.mood(hungry));
		helper.assertTrue(near(PetMood.mood(starving), 46.0), "Starving: four times, " + PetMood.mood(starving));
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aSadPetSulks(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		ServerPlayer stranger = player(helper, new BlockPos(5, 1, 5));
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 25, true);
		PetMood.setMood(wolf, 20.0);
		helper.assertTrue(PetMood.sad(wolf), "Under 30 it's sad");
		float full = wolf.getHealth();
		wolf.hurt(helper.getLevel().damageSources().playerAttack(stranger), 4.0F);
		helper.assertTrue(near(full - wolf.getHealth(), 4.0), "No Winter Coat while it sulks: took " + (full - wolf.getHealth()));
		PetRegistry.Record record = PetRegistry.get(helper.getLevel().getServer()).find(wolf.getUUID());
		helper.assertTrue(PetStorage.open(owner, record) != null, "and it won't carry things");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void feedingAndPattingCheerItUp(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Cow cow = pet(helper, EntityType.COW, owner, new BlockPos(1, 2, 1), 1, true);
		PetMood.setMood(cow, 50.0);
		PetMood.setHunger(cow, 10.0);
		ItemStack wheat = new ItemStack(Items.WHEAT, 5);
		helper.assertTrue(Bonding.feed(owner, cow, wheat) == Bonding.Result.FED_OWN, "Fed");
		helper.assertTrue(near(PetMood.hunger(cow), 16.0) && near(PetMood.mood(cow), 65.0), "+6 food, +15 mood");
		Bonding.feed(owner, cow, wheat);
		helper.assertTrue(near(PetMood.hunger(cow), 20.0) && near(PetMood.mood(cow), 65.0), "Full now; the cheer waits a minute");
		PetMood.pat(owner, cow);
		helper.assertTrue(near(PetMood.mood(cow), 70.0), "A pat: +5");
		PetMood.pat(owner, cow);
		helper.assertTrue(near(PetMood.mood(cow), 70.0), "and again only after 30 seconds");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void reachingFullMoodMakesItLoveYouUntilItFaints(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 1, true);
		PetMood.setMood(wolf, 96.0);
		PetMood.pat(owner, wolf);
		helper.assertTrue(PetMood.loved(wolf), "Full mood from a pat: it loves you");
		helper.assertTrue(near(PetMood.dropPace(wolf), 0.25), "and its mood drops at a quarter of the pace");
		wolf.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
		helper.assertFalse(PetMood.loved(wolf), "Fainting ends it");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aHungryPetEatsFromItsStorage(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Cow cow = pet(helper, EntityType.COW, owner, new BlockPos(1, 2, 1), 1, true);
		PetRegistry.Record record = PetRegistry.get(helper.getLevel().getServer()).find(cow.getUUID());
		SimpleContainer box = new SimpleContainer(record.slots);
		box.addItem(new ItemStack(Items.WHEAT, 3));
		record.items = box.createTag();
		PetMood.setHunger(cow, 10.0);
		PetMood.update(cow, 1);
		SimpleContainer after = new SimpleContainer(record.slots);
		after.fromTag(record.items);
		helper.assertTrue(PetMood.hunger(cow) > 15.0 && after.countItem(Items.WHEAT) == 2, "It ate one wheat from its storage: food "
				+ PetMood.hunger(cow) + ", " + after.countItem(Items.WHEAT) + " left");
		helper.succeed();
	}
}
