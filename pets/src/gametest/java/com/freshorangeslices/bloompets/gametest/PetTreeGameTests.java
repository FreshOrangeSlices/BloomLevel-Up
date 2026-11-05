package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.BloomBridge;
import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.PetEvents;
import com.freshorangeslices.bloompets.PetLevels;
import com.freshorangeslices.bloompets.PetRegistry;
import com.freshorangeslices.bloompets.PetTrees;
import com.freshorangeslices.bloompets.Pets;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Bloom Pets, Phase 4: One and the Same's weapon trees on pets. Bloom isn't in this run, so One and the Same is set by hand. */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PetTreeGameTests {
	private static ServerPlayer player(GameTestHelper helper, BlockPos at, boolean oneAndTheSame) {
		ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pets-test"));
		BlockPos abs = helper.absolutePos(at);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		BloomBridge.setForTests(player.getUUID(), oneAndTheSame);
		PetTrees.remember(player);
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
		return mob;
	}

	private static PetRegistry.Record record(GameTestHelper helper, Mob pet) {
		return PetRegistry.get(helper.getLevel().getServer()).find(pet.getUUID());
	}

	private static Zombie zombie(GameTestHelper helper, BlockPos at) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setNoAi(true);
		return zombie;
	}

	@GameTest(template = "empty")
	public static void aTreeNeedsOneAndTheSameAndACombatPet(GameTestHelper helper) {
		ServerPlayer without = player(helper, new BlockPos(4, 1, 4), false);
		Wolf lonely = pet(helper, EntityType.WOLF, without, new BlockPos(1, 2, 1), 50, true);
		PetTrees.pick(without, record(helper, lonely), "sword");
		helper.assertTrue(PetTrees.tree(lonely) == null, "No tree without One and the Same");

		ServerPlayer owner = player(helper, new BlockPos(5, 1, 5), true);
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(3, 2, 1), 50, true);
		PetTrees.pick(owner, record(helper, wolf), "sword");
		helper.assertTrue(PetTrees.tree(wolf) == PetTrees.Tree.SWORD && "sword".equals(record(helper, wolf).tree), "The wolf follows the Sword tree");
		PetTrees.pick(owner, record(helper, wolf), "axe");
		helper.assertTrue(PetTrees.tree(wolf) == PetTrees.Tree.SWORD, "It stays: no second pick without a reset");

		Cow cow = pet(helper, EntityType.COW, owner, new BlockPos(5, 2, 1), 50, true);
		PetTrees.pick(owner, record(helper, cow), "sword");
		helper.assertTrue(PetTrees.tree(cow) == null, "A cow (not a combat pet) can't");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aTreeRestsOutOfThePartyOrWithoutTheMajor(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4), true);
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 30, true);
		PetTrees.pick(owner, record(helper, wolf), "unarmed");
		helper.assertTrue(PetTrees.treeLevel(wolf) == 30, "Working in the party");
		Pets.setParty(wolf, false);
		helper.assertTrue(PetTrees.treeLevel(wolf) < 0, "Resting out of it");
		Pets.setParty(wolf, true);
		BloomBridge.setForTests(owner.getUUID(), false);
		PetTrees.remember(owner);
		helper.assertTrue(PetTrees.treeLevel(wolf) < 0 && PetTrees.tree(wolf) == PetTrees.Tree.UNARMED,
				"With One and the Same off it rests, its pick kept");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theSwordsExecuteAndUnarmedsBrawlerHitHarder(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4), true);
		Wolf swordsman = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 50, true);
		PetTrees.pick(owner, record(helper, swordsman), "sword");
		Zombie hurt = zombie(helper, new BlockPos(1, 2, 4));
		hurt.setHealth(10.0F);
		Zombie fresh = zombie(helper, new BlockPos(3, 2, 4));
		helper.assertTrue(Math.abs(PetTrees.attack(swordsman, hurt, 50, 4.0F) - 5.2F) < 1.0E-4, "Execute: +30% under 70% health");
		helper.assertTrue(PetTrees.attack(swordsman, fresh, 50, 4.0F) == 4.0F, "Nothing extra on a fresh one");

		Wolf brawler = pet(helper, EntityType.WOLF, owner, new BlockPos(5, 2, 1), 10, false);
		PetTrees.pick(owner, record(helper, brawler), "unarmed");
		helper.assertTrue(PetTrees.attack(brawler, fresh, 10, 4.0F) == 7.0F, "Brawler: +3");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aResetCostsABloomPetItsLevel(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4), true);
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 50, true);
		PetTrees.pick(owner, record(helper, wolf), "trident");
		PetTrees.reset(owner, record(helper, wolf));
		helper.assertTrue(PetTrees.tree(wolf) == null && PetLevels.level(wolf) == 1, "No tree, and back to level 1");
		PetTrees.pick(owner, record(helper, wolf), "axe");
		helper.assertTrue(PetTrees.tree(wolf) == PetTrees.Tree.AXE, "and free to pick again");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void cooldownAbilitiesSwitchOffAndOn(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4), true);
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 75, true);
		PetTrees.pick(owner, record(helper, wolf), "axe");
		PetTrees.toggle(owner, record(helper, wolf), "charge_attack");
		helper.assertTrue(PetTrees.abilitiesOff(wolf).contains("charge_attack"), "Charge Attack off");
		PetTrees.toggle(owner, record(helper, wolf), "charge_attack");
		helper.assertFalse(PetTrees.abilitiesOff(wolf).contains("charge_attack"), "and on again");
		PetTrees.toggle(owner, record(helper, wolf), "armor_break");
		helper.assertTrue(PetTrees.abilitiesOff(wolf).isEmpty(), "Armor Break isn't a cooldown ability: it doesn't switch");
		helper.succeed();
	}
}
