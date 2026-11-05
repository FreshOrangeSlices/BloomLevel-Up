package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.Bonding;
import com.freshorangeslices.bloompets.Fainting;
import com.freshorangeslices.bloompets.Party;
import com.freshorangeslices.bloompets.PetEvents;
import com.freshorangeslices.bloompets.PetLevels;
import com.freshorangeslices.bloompets.PetRegistry;
import com.freshorangeslices.bloompets.PetSkills;
import com.freshorangeslices.bloompets.PetXp;
import com.freshorangeslices.bloompets.Pets;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Bloom Pets, Phase 2: the level curve, XP, combat pets' skills, fainting and the level-100 perks. */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PetLevelGameTests {
	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pets-test"));
		BlockPos abs = helper.absolutePos(at);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	/** A mob made the player's pet (as bonding would), at that level, in their party or not. */
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

	private static Zombie zombie(GameTestHelper helper, BlockPos at) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setNoAi(true);
		return zombie;
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0E-3;
	}

	@GameTest(template = "empty")
	public static void theCurveIsBloomsHalvedAndAQuarterForNonCombatPets(GameTestHelper helper) {
		helper.assertTrue(near(PetLevels.xpToNext(1, true), 30.0) && near(PetLevels.xpToNext(99, true), 520.0), "Combat: 30 for level 2, 520 for 100");
		helper.assertTrue(near(PetLevels.xpToNext(1, false), 15.0), "Non-combat: half that, 15 for level 2");
		double combat = 0;
		double passive = 0;

		for (int level = 1; level < PetLevels.MAX; level++) {
			combat += PetLevels.xpToNext(level, true);
			passive += PetLevels.xpToNext(level, false);
		}

		helper.assertTrue(near(combat, 27225.0) && near(passive, 13612.5), "27,225 XP to 100 for a fighter, 13,612.5 for the rest, not " + combat + " and " + passive);
		helper.assertTrue(near(PetSkills.goreChance(15), 7.5) && near(PetSkills.goreChance(100), 50.0), "Gore: half the level in %, 50% at 100");
		helper.assertTrue(near(PetSkills.soothChance(20), 15.0) && near(PetSkills.soothChance(100), 60.0) && PetSkills.soothHeal(20) == 3
				&& PetSkills.soothHeal(100) == 9, "Soothing Presence: 15% to 60%, 3 to 9 health");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aPartyWolfEarnsXpForTheDamageItDeals(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 1, true);
		Wolf homeWolf = pet(helper, EntityType.WOLF, owner, new BlockPos(6, 2, 1), 1, false);
		Zombie first = zombie(helper, new BlockPos(1, 2, 4));
		Zombie second = zombie(helper, new BlockPos(6, 2, 4));
		float before = first.getHealth();
		first.hurt(helper.getLevel().damageSources().mobAttack(wolf), 4.0F);
		double dealt = before - first.getHealth();
		helper.assertTrue(dealt > 0 && near(PetLevels.xp(wolf), dealt * 0.125), "XP: 0.125 a point dealt (" + dealt + " dealt), not " + PetLevels.xp(wolf));
		second.hurt(helper.getLevel().damageSources().mobAttack(homeWolf), 4.0F);
		helper.assertTrue(PetLevels.xp(homeWolf) == 0.0, "A wolf outside the party earns nothing");
		helper.assertTrue(near(PetXp.multiplier(wolf), 1.0), "A wolf's multiplier is 1");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void xpRollsOverIntoLevelsUpTo100(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 1, true);
		helper.assertTrue(PetLevels.addXp(wolf, 30.0 + 35.0 + 1.0) == 2, "65 XP is two levels (30, then 35)");
		helper.assertTrue(PetLevels.level(wolf) == 3 && near(PetLevels.xp(wolf), 1.0), "Level 3 with 1 XP over");
		helper.assertTrue(PetRegistry.get(helper.getLevel().getServer()).find(wolf.getUUID()).level == 3, "Its entry shows level 3");
		PetLevels.setLevel(wolf, 99);
		PetLevels.addXp(wolf, 100000.0);
		helper.assertTrue(PetLevels.level(wolf) == 100 && PetLevels.xp(wolf) == 0.0, "It stops at 100");
		helper.assertTrue(wolf.getCustomName() != null && wolf.getCustomName().getStyle().getColor() != null, "A gold name at 100");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void attackSkillsAddUp(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 75, true);
		Zombie zombie = zombie(helper, new BlockPos(1, 2, 4));
		helper.assertTrue(PetSkills.attack(wolf, zombie, 75, 4.0F, 99.0) == 6.0F, "Level 75: +2 on every bite");
		helper.assertTrue(PetSkills.attack(wolf, zombie, 75, 4.0F, 0.0) == 10.0F, "and Rending Bite doubles it: 4 + 4 + 2");
		helper.assertTrue(PetSkills.attack(wolf, zombie, 14, 4.0F, 0.0) == 4.0F, "No Rending Bite before 15");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void defensiveSkillsOpenWithLevels(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		ServerPlayer stranger = player(helper, new BlockPos(5, 1, 5));
		Wolf furry = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 25, true);
		Wolf pup = pet(helper, EntityType.WOLF, owner, new BlockPos(3, 2, 1), 24, false);
		float full = furry.getHealth();
		furry.hurt(helper.getLevel().damageSources().playerAttack(stranger), 4.0F);
		helper.assertTrue(near(full - furry.getHealth(), 2.0), "Winter Coat at 25: a 4 blow takes 2, not " + (full - furry.getHealth()));
		pup.hurt(helper.getLevel().damageSources().playerAttack(stranger), 4.0F);
		helper.assertTrue(near(full - pup.getHealth(), 4.0), "Without it, 4");

		Wolf blessed = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 5), 35, true);
		blessed.setHealth(10.0F);
		blessed.hurt(helper.getLevel().damageSources().magic(), 4.0F);
		helper.assertTrue(near(blessed.getHealth(), 14.0), "Moonblessed at 35: magic heals it, health " + blessed.getHealth());

		Wolf stormy = pet(helper, EntityType.WOLF, owner, new BlockPos(5, 2, 5), 50, true);
		float before = stormy.getHealth();
		stormy.hurt(helper.getLevel().damageSources().explosion(null, null), 6.0F);
		helper.assertTrue(near(before - stormy.getHealth(), 1.0), "Storm Hide at 50: a 6 blast takes 1, not " + (before - stormy.getHealth()));
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aPartyPetFaintsInsteadOfDying(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		ServerPlayer stranger = player(helper, new BlockPos(5, 1, 5));
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 1, true);
		Wolf homeWolf = pet(helper, EntityType.WOLF, owner, new BlockPos(6, 2, 1), 1, false);
		wolf.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
		helper.assertTrue(wolf.isAlive() && wolf.getHealth() > 0 && Fainting.fainted(wolf), "The party wolf fainted, alive");
		helper.assertTrue(Fainting.ticksLeft(wolf) > 0, "and rests a while");
		wolf.invulnerableTime = 0;
		helper.assertFalse(wolf.hurt(helper.getLevel().damageSources().playerAttack(stranger), 5.0F), "Nothing hurts it while it rests");
		helper.assertTrue(PetRegistry.get(helper.getLevel().getServer()).find(wolf.getUUID()) != null, "It's still on its owner's list");
		homeWolf.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
		helper.assertTrue(homeWolf.isDeadOrDying(), "A wolf outside the party is a plain pet: it dies");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aMaxLevelPetGetsASecondWindFirst(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 100, true);
		wolf.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
		helper.assertTrue(wolf.isAlive() && !Fainting.fainted(wolf) && near(wolf.getHealth(), wolf.getMaxHealth() * 0.5),
				"A second wind: back to half health, " + wolf.getHealth());
		wolf.invulnerableTime = 0;
		wolf.hurt(helper.getLevel().damageSources().generic(), 1000.0F);
		helper.assertTrue(wolf.isAlive() && Fainting.fainted(wolf), "Only once: the next time it faints");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void guardianInterventionSavesItsOwnerOnceADay(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		IronGolem golem = pet(helper, EntityType.IRON_GOLEM, owner, new BlockPos(1, 2, 1), 20, true);
		helper.assertTrue(near(golem.getMaxHealth(), 100.0 + 10.0), "Iron Colossus at 20: +10 health, " + golem.getMaxHealth());
		Zombie zombie = zombie(helper, new BlockPos(6, 2, 6));
		LivingDeathEvent fatal = new LivingDeathEvent(owner, helper.getLevel().damageSources().mobAttack(zombie));
		PetSkills.onOwnerDeath(fatal);
		helper.assertTrue(fatal.isCanceled() && near(owner.getHealth(), 1.0) && owner.hasEffect(MobEffects.DAMAGE_RESISTANCE),
				"Saved at half a heart, with Resistance");
		LivingDeathEvent again = new LivingDeathEvent(owner, helper.getLevel().damageSources().mobAttack(zombie));
		PetSkills.onOwnerDeath(again);
		helper.assertFalse(again.isCanceled(), "Not twice the same day");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void feedingANonCombatPartyPetGivesXp(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Cow cow = pet(helper, EntityType.COW, owner, new BlockPos(1, 2, 1), 1, false);
		ItemStack wheat = new ItemStack(Items.WHEAT, 5);
		helper.assertTrue(Bonding.feed(owner, cow, wheat) == Bonding.Result.OWNED_BY_YOU && wheat.getCount() == 5,
				"Out of the party and at full health: not hungry, nothing eaten");
		Party.toggle(owner, PetRegistry.get(helper.getLevel().getServer()).find(cow.getUUID()));
		helper.assertTrue(Bonding.feed(owner, cow, wheat) == Bonding.Result.FED_OWN && wheat.getCount() == 4, "In the party: fed");
		helper.assertTrue(near(PetLevels.xp(cow), 10.0), "+10 XP, not " + PetLevels.xp(cow));
		cow.setHealth(4.0F);
		Bonding.feed(owner, cow, wheat);
		helper.assertTrue(near(PetLevels.xp(cow), 10.0) && cow.getHealth() > 4.0F, "Again at once: it heals, but no XP for 30 seconds");
		helper.assertFalse(PetLevels.combat(cow), "A cow levels on the non-combat curve");
		helper.succeed();
	}
}
