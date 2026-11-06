package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.Amigos;
import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.PetEvents;
import com.freshorangeslices.bloompets.PetLevels;
import com.freshorangeslices.bloompets.PetMood;
import com.freshorangeslices.bloompets.PetRegistry;
import com.freshorangeslices.bloompets.PetSkills;
import com.freshorangeslices.bloompets.Pets;
import com.freshorangeslices.bloompets.Spirits;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bloom Pets, Phase 5b: companions' Spirits, on a real Migamigos companion (Migamigos is in this test run only). A
 * Spirit comes when the companion is listed; one reroll, ever, to another kind; its skills work at the companion's
 * level; an Amigo at 99 gets the top level's second wind.
 */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritGameTests {
	private static final ResourceLocation CLAYMORE = new ResourceLocation("migamigos", "claymore");

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pets-test"));
		BlockPos abs = helper.absolutePos(new BlockPos(4, 1, 4));
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	@SuppressWarnings("unchecked")
	private static Mob companion(GameTestHelper helper, ServerPlayer owner) {
		helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(CLAYMORE), "Migamigos is in the test run");
		Mob mob = helper.spawn((EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(CLAYMORE), new BlockPos(2, 2, 2));
		mob.setNoAi(true);
		Pets.claim(mob, owner);
		PetEvents.observe(mob);
		return mob;
	}

	private static PetRegistry.Record record(GameTestHelper helper, Mob pet) {
		return PetRegistry.get(helper.getLevel().getServer()).find(pet.getUUID());
	}

	@GameTest(template = "empty")
	public static void aCompanionGetsASpiritWhenListed(GameTestHelper helper) {
		Mob mob = companion(helper, player(helper));
		helper.assertTrue(Spirits.of(mob) != null && !Spirits.settled(mob), "A Spirit, waiting to be kept or rerolled: " + Spirits.of(mob));
		helper.assertTrue(PetSkills.kind(mob) == Spirits.of(mob), "Its skills are its Spirit's");
		helper.assertTrue(record(helper, mob).spirit.equals(Spirits.of(mob).id()), "and its entry knows it");

		java.util.Set<PetSkills.Kind> seen = java.util.EnumSet.noneOf(PetSkills.Kind.class);

		for (int i = 0; i < 400; i++) {
			seen.add(Spirits.roll(mob.getRandom(), null));
		}

		helper.assertTrue(seen.size() == PetSkills.Kind.values().length, "Every combat kind comes up (no Vex among them): " + seen);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theRerollIsOnceAndAlwaysAnotherKind(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		Mob mob = companion(helper, owner);
		Spirits.set(mob, PetSkills.Kind.WOLF);
		Spirits.reroll(owner, record(helper, mob));
		PetSkills.Kind after = Spirits.of(mob);
		helper.assertTrue(after != null && after != PetSkills.Kind.WOLF && Spirits.settled(mob), "Rerolled to another kind, and settled: " + after);
		Spirits.reroll(owner, record(helper, mob));
		Spirits.keep(owner, record(helper, mob));
		helper.assertTrue(Spirits.of(mob) == after, "No second reroll");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theIronGolemsSpiritGivesHealthUntilItSulks(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		Mob mob = companion(helper, owner);
		Spirits.set(mob, PetSkills.Kind.WOLF);
		float before = mob.getMaxHealth();
		Spirits.set(mob, PetSkills.Kind.IRON_GOLEM);
		float expected = (float) PetSkills.colossusHealth(PetLevels.level(mob));
		helper.assertTrue(Math.abs(mob.getMaxHealth() - before - expected) < 1.0E-4,
				"Iron Colossus: +" + expected + " health at its level (" + before + " to " + mob.getMaxHealth() + ")");

		PetMood.setMood(mob, 10.0);
		PetSkills.refresh(mob);
		helper.assertTrue(Math.abs(mob.getMaxHealth() - before) < 1.0E-4, "Sulking, it rests");

		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
		zombie.setNoAi(true);
		helper.assertTrue(PetSkills.attack(mob, zombie, 75, 4.0F, 100.0) == 6.0F, "Its Spirit's skills at 75: +2 on every hit");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void anAmigoAtItsTopGetsASecondWind(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		Mob mob = companion(helper, owner);

		try {
			mob.getClass().getMethod("setLevel", int.class).invoke(mob, Amigos.MAX);
		} catch (ReflectiveOperationException e) {
			helper.fail("Migamigos' setLevel: " + e);
		}

		helper.assertTrue(PetLevels.maxed(mob), "Amigo level 99 is its top");
		mob.hurt(mob.damageSources().generic(), 1000.0F);
		helper.assertTrue(mob.isAlive() && mob.getHealth() > 0.0F, "A second wind: it's still standing (" + mob.getHealth() + ")");
		mob.hurt(mob.damageSources().generic(), 1000.0F);
		helper.assertTrue(!mob.isAlive(), "Only once until it's called again");
		helper.succeed();
	}
}
