package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.PetEvents;
import com.freshorangeslices.bloompets.PetGoals;
import com.freshorangeslices.bloompets.PetModes;
import com.freshorangeslices.bloompets.PetRegistry;
import com.freshorangeslices.bloompets.Pets;
import com.freshorangeslices.bloompets.Release;
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

/**
 * Bloom Pets after FreshOrangeSlices' playtest (2026-11-05): every pet follows, wanders or stays (a wolf sits to stay,
 * anything else holds its spot), a pet can be renamed, and released (back to the wild, its storage to its owner).
 */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PetModeGameTests {
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pets-test"));
		BlockPos abs = helper.absolutePos(new BlockPos(4, 1, 4));
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static <T extends Mob> T pet(GameTestHelper helper, EntityType<T> type, ServerPlayer owner, boolean ai) {
		T mob = helper.spawn(type, new BlockPos(2, 2, 2));
		mob.setNoAi(!ai);
		Pets.claim(mob, owner);
		PetEvents.observe(mob);
		PetGoals.ensure(mob);
		return mob;
	}

	private static PetRegistry.Record record(GameTestHelper helper, Mob pet) {
		return PetRegistry.get(helper.getLevel().getServer()).find(pet.getUUID());
	}

	@GameTest(template = "empty")
	public static void aWolfSitsToStay(GameTestHelper helper) {
		Wolf wolf = pet(helper, EntityType.WOLF, player(helper), false);
		helper.assertTrue(wolf.isTame() && PetModes.mode(wolf) == PetModes.Mode.FOLLOW, "A tame wolf follows to begin with (" + PetModes.mode(wolf) + ")");
		helper.assertTrue(PetGoals.sits(wolf), "A wolf sits when told to");

		PetModes.set(wolf, PetModes.Mode.STAY);
		helper.assertTrue(wolf.isOrderedToSit() && wolf.isInSittingPose() && PetModes.mode(wolf) == PetModes.Mode.STAY, "Stay: it sits");
		PetModes.set(wolf, PetModes.Mode.WANDER);
		helper.assertTrue(!wolf.isOrderedToSit() && PetModes.mode(wolf) == PetModes.Mode.WANDER, "Wander: up, roaming near its spot");
		helper.assertTrue(record(helper, wolf).mode.equals("wander"), "The list keeps its mode (" + record(helper, wolf).mode + ")");
		PetModes.set(wolf, PetModes.Mode.FOLLOW);
		helper.assertTrue(!wolf.isOrderedToSit() && PetModes.mode(wolf) == PetModes.Mode.FOLLOW, "Follow: up, after its owner");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 240)
	public static void aCowOnStayGoesBackToItsSpot(GameTestHelper helper) {
		Cow cow = pet(helper, EntityType.COW, player(helper), true);
		helper.assertTrue(PetModes.mode(cow) == PetModes.Mode.WANDER, "A pet out of the party wanders to begin with");

		// Once it's landed: Stay there, then it's pushed off its spot.
		helper.runAfterDelay(20, () -> {
			PetModes.set(cow, PetModes.Mode.STAY);
			BlockPos home = Pets.home(cow);
			BlockPos away = helper.absolutePos(new BlockPos(6, 2, 6));
			cow.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			helper.succeedWhen(() -> helper.assertTrue(cow.blockPosition().distSqr(home) <= 2.0,
					"The cow walks back to its spot (" + cow.blockPosition() + ", its spot " + home + ")"));
		});
	}

	@GameTest(template = "empty")
	public static void aPetCanBeRenamed(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		Wolf wolf = pet(helper, EntityType.WOLF, owner, false);
		PetModes.rename(owner, record(helper, wolf), "§cRex  ");
		helper.assertTrue("Rex".equals(wolf.getName().getString()) && "Rex".equals(record(helper, wolf).name),
				"Renamed, colour codes and spaces left out (" + wolf.getName().getString() + ")");
		PetModes.rename(owner, record(helper, wolf), "");
		helper.assertTrue(!wolf.hasCustomName(), "An empty name gives it back its own");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aReleasedPetGoesBackToTheWild(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		Wolf wolf = pet(helper, EntityType.WOLF, owner, false);
		PetRegistry.Record record = record(helper, wolf);
		SimpleContainer box = new SimpleContainer(record.slots);
		box.setItem(0, new ItemStack(Items.DIAMOND, 3));
		record.items = box.createTag();

		Release.release(owner, record);
		helper.assertTrue(record(helper, wolf) == null, "It's off its owner's list");
		helper.assertTrue(!wolf.isTame() && Pets.owner(wolf) == null && !Pets.listed(wolf), "untamed, nobody's");
		helper.assertTrue(PetEvents.observe(wolf) == null && record(helper, wolf) == null, "and it doesn't come back on the list");
		helper.assertTrue(owner.getInventory().countItem(Items.DIAMOND) == 3, "What its storage held is its owner's");
		helper.succeed();
	}
}
