package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.Fainting;
import com.freshorangeslices.bloompets.Party;
import com.freshorangeslices.bloompets.PetEvents;
import com.freshorangeslices.bloompets.PetLevels;
import com.freshorangeslices.bloompets.PetPosts;
import com.freshorangeslices.bloompets.PetRegistry;
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
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Bloom Pets, Phase 3c: posts and their jobs. */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PetPostGameTests {
	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pets-test"));
		BlockPos abs = helper.absolutePos(at);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static <T extends Mob> T pet(GameTestHelper helper, EntityType<T> type, ServerPlayer owner, BlockPos at, int level) {
		T mob = helper.spawn(type, at);
		mob.setNoAi(true);
		Pets.claim(mob, owner);
		PetEvents.observe(mob);
		PetLevels.setLevel(mob, level);
		mob.setOnGround(true);
		return mob;
	}

	private static PetRegistry.Record record(GameTestHelper helper, Mob pet) {
		return PetRegistry.get(helper.getLevel().getServer()).find(pet.getUUID());
	}

	/** Posts the pet where it stands, as the Post button would (its entry too). */
	private static void post(GameTestHelper helper, Mob pet) {
		PetPosts.take(pet, pet.blockPosition());
		PetEvents.observe(pet);
	}

	@GameTest(template = "empty")
	public static void aPostNeedsLevel8AndAFreePlace(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Cow young = pet(helper, EntityType.COW, owner, new BlockPos(1, 2, 1), 1);
		PetPosts.toggle(owner, record(helper, young));
		helper.assertFalse(PetPosts.posted(young), "Not before level 8");
		PetLevels.setLevel(young, 8);
		PetPosts.toggle(owner, record(helper, young));
		helper.assertTrue(PetPosts.posted(young) && record(helper, young).post != null, "At level 8 it takes its post");

		Cow second = pet(helper, EntityType.COW, owner, new BlockPos(3, 2, 1), 8);
		Cow third = pet(helper, EntityType.COW, owner, new BlockPos(5, 2, 1), 8);
		Cow fourth = pet(helper, EntityType.COW, owner, new BlockPos(1, 2, 5), 8);
		PetPosts.toggle(owner, record(helper, second));
		PetPosts.toggle(owner, record(helper, third));
		PetPosts.toggle(owner, record(helper, fourth));
		helper.assertTrue(PetPosts.posted(third) && !PetPosts.posted(fourth), "Three posts at most");

		PetPosts.toggle(owner, record(helper, young));
		helper.assertFalse(PetPosts.posted(young) || record(helper, young).post != null, "Unpost calls it off");
		Party.toggle(owner, record(helper, fourth));
		PetPosts.toggle(owner, record(helper, fourth));
		helper.assertFalse(PetPosts.posted(fourth), "A party pet can't take a post");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theBeeTendsTheCropsAroundItsPost(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(6, 1, 6));
		Mob bee = pet(helper, EntityType.BEE, owner, new BlockPos(3, 3, 3), 8);
		post(helper, bee);

		for (int x = 1; x <= 5; x++) {
			helper.setBlock(new BlockPos(x, 1, 5), Blocks.FARMLAND);
			helper.setBlock(new BlockPos(x, 2, 5), Blocks.WHEAT);
		}

		int grown = PetPosts.tend(helper.getLevel(), PetPosts.post(bee), 6, 4, bee.getRandom());
		int older = 0;

		for (int x = 1; x <= 5; x++) {
			if (helper.getBlockState(new BlockPos(x, 2, 5)).getValue(CropBlock.AGE) > 0) {
				older++;
			}
		}

		helper.assertTrue(grown == 4 && older == 4, "Four of the five crops grew a stage (" + grown + ", " + older + ")");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theAllayCollectsDropsIntoItsStorage(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(6, 1, 6));
		Allay allay = pet(helper, EntityType.ALLAY, owner, new BlockPos(2, 2, 2), 8);
		post(helper, allay);
		helper.spawnItem(Items.DIAMOND, 4.5F, 2.0F, 4.5F);
		int stored = PetPosts.collect(allay, PetPosts.post(allay), 8);
		SimpleContainer box = new SimpleContainer(record(helper, allay).slots);
		box.fromTag(record(helper, allay).items);
		helper.assertTrue(stored == 1 && box.countItem(Items.DIAMOND) == 1, "The diamond is in its storage (" + stored + ")");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aGuardGoesForMonstersNearItsPostButNotCreepers(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(6, 1, 6));
		IronGolem golem = pet(helper, EntityType.IRON_GOLEM, owner, new BlockPos(1, 2, 1), 8);
		post(helper, golem);
		Creeper creeper = helper.spawn(EntityType.CREEPER, new BlockPos(3, 2, 1));
		creeper.setNoAi(true);
		helper.assertTrue(PetPosts.nearestMonster(helper.getLevel(), PetPosts.post(golem), PetPosts.guardRadius(golem), golem) == null,
				"Not a creeper (it would blow the place up)");
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 1));
		zombie.setNoAi(true);
		helper.assertTrue(PetPosts.nearestMonster(helper.getLevel(), PetPosts.post(golem), PetPosts.guardRadius(golem), golem) == zombie,
				"The zombie");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aCatAtItsPostKeepsCreepersOffEveryone(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(6, 1, 6));
		ServerPlayer stranger = player(helper, new BlockPos(5, 1, 1));
		Mob cat = pet(helper, EntityType.CAT, owner, new BlockPos(1, 2, 1), 8);
		post(helper, cat);
		PetPosts.second(cat);
		Creeper creeper = helper.spawn(EntityType.CREEPER, new BlockPos(3, 2, 3));
		creeper.setNoAi(true);
		creeper.setTarget(stranger);
		helper.assertTrue(creeper.getTarget() == null, "The creeper doesn't go for anyone near the cat's post");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aPetAtItsPostWorksAndFaints(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(6, 1, 6));
		Cow cow = pet(helper, EntityType.COW, owner, new BlockPos(1, 2, 1), 8);
		post(helper, cow);
		helper.assertTrue(PetLevels.active(cow), "A pet at its post earns XP");
		cow.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
		helper.assertTrue(cow.isAlive() && Fainting.fainted(cow), "and faints there rather than dying");
		helper.succeed();
	}
}
