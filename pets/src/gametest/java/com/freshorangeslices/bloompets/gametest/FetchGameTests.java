package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.Fetch;
import com.freshorangeslices.bloompets.PetEvents;
import com.freshorangeslices.bloompets.PetLevels;
import com.freshorangeslices.bloompets.PetMood;
import com.freshorangeslices.bloompets.PetRegistry;
import com.freshorangeslices.bloompets.Pets;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Bloom Pets, Phase 5c: fetch, the plugin's way, with the Ball. */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FetchGameTests {
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pets-test"));
		BlockPos abs = helper.absolutePos(new BlockPos(4, 1, 4));
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static <T extends Mob> T pet(GameTestHelper helper, EntityType<T> type, ServerPlayer owner, boolean party) {
		T mob = helper.spawn(type, new BlockPos(2, 2, 2));
		mob.setNoAi(true);
		Pets.claim(mob, owner);
		PetRegistry.Record record = PetEvents.observe(mob);

		if (party) {
			record.party = true;
			Pets.setParty(mob, true);
		}

		return mob;
	}

	private static int balls(ServerPlayer player) {
		int count = 0;

		for (ItemStack stack : player.getInventory().items) {
			if (stack.is(BloomPets.BALL.get())) {
				count += stack.getCount();
			}
		}

		return count;
	}

	@SuppressWarnings("unchecked")
	@GameTest(template = "empty")
	public static void everyBloomPetButTheGolemPlays(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		helper.assertTrue(Fetch.plays(pet(helper, EntityType.WOLF, owner, false)), "A wolf plays fetch");
		helper.assertTrue(Fetch.plays(pet(helper, EntityType.COW, owner, false)), "So does a cow");
		helper.assertFalse(Fetch.plays(pet(helper, EntityType.IRON_GOLEM, owner, false)), "The Iron Golem doesn't");

		ResourceLocation claymore = new ResourceLocation("migamigos", "claymore");
		helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(claymore), "Migamigos is in the test run");
		Mob companion = helper.spawn((EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(claymore), new BlockPos(5, 2, 5));
		Pets.claim(companion, owner);
		helper.assertFalse(Fetch.plays(companion), "and companions don't");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theBallComesBackWithAReward(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		ItemStack ball = new ItemStack(BloomPets.BALL.get());
		helper.assertFalse(Fetch.throwBall(owner, ball), "No one in the party: nothing thrown");

		Wolf wolf = pet(helper, EntityType.WOLF, owner, true);
		PetLevels.setLevel(wolf, 5);
		double mood = PetMood.mood(wolf);
		helper.assertTrue(Fetch.throwBall(owner, ball) && Fetch.playing(wolf) && Fetch.balls().size() == 1, "Thrown for the party pet");
		helper.assertFalse(Fetch.throwBall(owner, ball), "One ball at a time");

		Fetch.finish(wolf, true);
		helper.assertTrue(!Fetch.playing(wolf) && balls(owner) == 1, "Brought back: the ball is in its owner's inventory");
		helper.assertTrue(Math.abs(PetLevels.xp(wolf) - Fetch.XP) < 1.0E-6, "+" + Fetch.XP + " XP (" + PetLevels.xp(wolf) + ")");
		helper.assertTrue(Math.abs(PetMood.mood(wolf) - Math.min(PetMood.MAX_MOOD, mood + Fetch.MOOD)) < 1.0E-6, "+" + Fetch.MOOD + " mood");

		helper.assertTrue(Fetch.throwBall(owner, ball), "Again");
		Fetch.finish(wolf, true);
		helper.assertTrue(Math.abs(PetLevels.xp(wolf) - Fetch.XP) < 1.0E-6 && balls(owner) == 2, "Within 15 seconds: the ball back, no more XP");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aBallThatCantBeBroughtStillComesBack(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		IronGolem golem = pet(helper, EntityType.IRON_GOLEM, owner, true);
		helper.assertFalse(Fetch.throwBall(owner, new ItemStack(BloomPets.BALL.get())), "A golem in the party won't play: nothing thrown");
		Pets.setParty(golem, false);
		PetRegistry.get(helper.getLevel().getServer()).find(golem.getUUID()).party = false;

		Wolf wolf = pet(helper, EntityType.WOLF, owner, true);
		helper.assertTrue(Fetch.throwBall(owner, new ItemStack(BloomPets.BALL.get())), "Thrown");
		Fetch.finish(wolf, false);
		helper.assertTrue(balls(owner) == 1 && Math.abs(PetLevels.xp(wolf)) < 1.0E-6, "Not brought: the ball is back anyway, without a reward");
		helper.succeed();
	}
}
