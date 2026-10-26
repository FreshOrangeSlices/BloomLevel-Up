package com.freshorangeslices.bloomlevelup.gametest.forge;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import net.minecraft.gametest.framework.GameTest;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.BloomEffects;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.entity.ThrownWeapon;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;

/**
 * Combat on a real server with fake players (Forge 1.20.1 build: no mace or spear tests; Slash replaces Impale). Fake players never tick, so swings that depend on a charged
 * attack (crits, full-strength hits) are recorded with explicit state; the damage itself is real.
 */
@GameTestHolder(BloomLevelUp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatGameTests {
	private static final double EPS = 1e-3;

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer player(GameTestHelper helper, ItemStack mainHand) {
		ServerPlayer player = new TestPlayers.Vulnerable(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bloom-test"));
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
		return player;
	}

	/** Combat and Utility to 100 (20 Major Levels), then buy the Majors in order. */
	private static void grant(ServerPlayer player, String... majors) {
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);

		for (String category : List.of(SkillXp.COMBAT, SkillXp.UTILITY)) {
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
		BloomProfile profile = BloomAttachments.profile(player);

		if (profile.category(SkillXp.COMBAT).level() == 0) {
			grant(player);
		}

		ActionResult result = BloomServices.progression().allocate(profile, stat, ProgressionService.Pool.NATURAL, amount);

		if (!result.ok()) {
			throw new IllegalStateException("Couldn't allocate " + stat + ": " + result.outcome());
		}
	}

	private static LivingEntity zombie(GameTestHelper helper, int x, int z) {
		return helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
	}

	private static DamageSource hit(ServerPlayer player) {
		return player.damageSources().playerAttack(player);
	}

	private static double combatXp(ServerPlayer player) {
		return BloomAttachments.profile(player).category(SkillXp.COMBAT).xp();
	}

	private static void near(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < EPS, what + ": expected " + expected + " but was " + actual);
	}

	// ------------------------------------------------------------------ XP

	@GameTest(template = "empty")
	public static void damageXpAndKillBonus(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		ServerLevel level = helper.getLevel();
		LivingEntity zombie = zombie(helper, 1, 1);

		zombie.hurt(hit(player), 8.0F);
		double real = 20.0 - zombie.getHealth();
		helper.assertTrue(real > 0.0, "The hit landed");
		near(helper, real * 0.5, combatXp(player), "0.5 XP per point of real damage");

		zombie.hurt(hit(player), 100.0F);
		helper.assertFalse(zombie.isAlive(), "Second hit kills");
		// Damage XP is capped at the 20 health it had (10 XP); the kill adds half of that.
		near(helper, 15.0, combatXp(player), "Damage XP capped by health, plus a 50% kill bonus");
		helper.succeed();
	}

	// ------------------------------------------------------------------ stats

	@GameTest(template = "empty")
	public static void criticalAndFirstStrikeMultiply(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		grant(player, CombatIds.FIRST_STRIKE);
		points(player, CombatIds.CRITICAL, 100);
		LivingEntity zombie = zombie(helper, 1, 1);

		CombatSkill.recordSwingForTests(player, zombie, 1.0F, true, false);
		// 6 incoming (vanilla's 1.5x crit included) -> 4x crit at 100 Critical, then First Strike +50%.
		near(helper, 6.0 * (4.0 / 1.5) * 1.5, CombatSkill.modifyIncoming(zombie, hit(player), 6.0F), "Crit 4x then First Strike");
		CombatSkill.endSwingForTests();
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void penetrationIgnoresArmor(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		points(player, CombatIds.PENETRATION, 100);
		LivingEntity zombie = zombie(helper, 1, 1);
		near(helper, 5.0, CombatSkill.penetrate(hit(player), zombie, 10.0F), "100 Penetration ignores half the armor");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void lootingAddsVirtualLevels(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		points(player, CombatIds.LOOTING, 100);
		TestPlayers.equal(helper, 3, EnchantmentHelper.getEnchantmentLevel(Enchantments.MOB_LOOTING, player), "100 Looting = Looting III");
		helper.succeed();
	}

	// ------------------------------------------------------------------ weapon Majors

	@GameTest(template = "empty")
	public static void executeFinishesLowTargetsWithSwords(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer swordsman = player(helper, new ItemStack(Items.IRON_SWORD));
		grant(swordsman, CombatIds.EXECUTE);
		LivingEntity low = zombie(helper, 1, 1);
		low.setHealth(10.0F);
		low.hurt(hit(swordsman), 5.0F);
		helper.assertTrue(low.isAlive(), "The hit that takes it under 30% doesn't execute");
		helper.assertTrue(low.getHealth() <= 6.0F, "That hit took it under 30% (health " + low.getHealth() + ")");
		LivingEntity under = zombie(helper, 1, 3);
		under.setHealth(5.0F);
		under.hurt(hit(swordsman), 1.0F);
		helper.assertFalse(under.isAlive(), "Already under 30%: the next hit executes");

		ServerPlayer axeman = player(helper, new ItemStack(Items.IRON_AXE));
		grant(axeman, CombatIds.EXECUTE);
		LivingEntity other = zombie(helper, 3, 1);
		other.setHealth(5.0F);
		other.hurt(hit(axeman), 1.0F);
		helper.assertTrue(other.isAlive(), "Execute is a sword Major");

		// Copycat spreads it to the axe.
		grant(axeman, CombatIds.COPYCAT);
		BloomServices.progression().selectCopySource(BloomAttachments.profile(axeman), CombatIds.COPYCAT, CombatIds.EXECUTE);
		LivingEntity third = zombie(helper, 5, 1);
		third.setHealth(5.0F);
		third.hurt(hit(axeman), 1.0F);
		helper.assertFalse(third.isAlive(), "Copycat: Execute with an axe");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void guaranteedCriticalCritsForTwoLessDamage(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_AXE));
		grant(player, CombatIds.GUARANTEED_CRITICAL);
		LivingEntity zombie = zombie(helper, 1, 1);
		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		near(helper, 9.0, CombatSkill.modifyIncoming(zombie, hit(player), 6.0F), "A forced 1.5x crit");
		CombatSkill.endSwingForTests();
		StatEffects.refresh(player);
		helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) < 1.0, "-2 attack damage while holding the axe");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void armorBreakWearsArmorOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer breaker = player(helper, new ItemStack(Items.IRON_AXE));
		grant(breaker, CombatIds.ARMOR_BREAK);
		ServerPlayer plain = player(helper, new ItemStack(Items.IRON_AXE));

		LivingEntity armored = zombie(helper, 1, 1);
		armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		armored.hurt(hit(breaker), 8.0F);
		LivingEntity control = zombie(helper, 3, 1);
		control.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		control.hurt(hit(plain), 8.0F);

		int broken = armored.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
		int normal = control.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
		helper.assertTrue(broken >= normal + 3, "Armor Break adds about half the damage as wear (" + broken + " vs " + normal + ")");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void bleedHurtsThroughArmor(GameTestHelper helper) {
		LivingEntity zombie = zombie(helper, 1, 1);
		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		zombie.tickCount = 0; // a damage tick (drips in between)
		BloomEffects.BLEED.get().applyEffectTick(zombie, 0);
		near(helper, 19.0, zombie.getHealth(), "Half a heart, armor ignored");
		zombie.addEffect(new MobEffectInstance(BloomEffects.BLEED.get(), 60));
		helper.assertTrue(zombie.hasEffect(BloomEffects.BLEED.get()), "Bleed is a real status effect");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void comboAndPredatorRamp(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		grant(player, CombatIds.COMBO, CombatIds.PREDATOR);
		LivingEntity zombie = zombie(helper, 1, 1);
		double[] expected = {4.0, (4.0 + 0.25) * 1.1, (4.0 + 0.5) * 1.2};

		for (double value : expected) {
			CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
			near(helper, value, CombatSkill.modifyIncoming(zombie, hit(player), 4.0F), "Combo +0.25 and Predator +10% per hit");
			CombatSkill.endSwingForTests();
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void deathFromAboveAddsMaceFallDamage(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		grant(player, CombatIds.CHARGE_ATTACK, CombatIds.DEATH_FROM_ABOVE); // it needs an Axe Major first in this build
		LivingEntity zombie = zombie(helper, 1, 1);
		player.fallDistance = 3.0F;
		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, false);
		near(helper, 5.0 + 12.0, CombatSkill.modifyIncoming(zombie, hit(player), 5.0F), "3 blocks of fall = +12, like a mace");
		CombatSkill.endSwingForTests();
		player.fallDistance = 0.0F;
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void brawlerHitsHarderBareHanded(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		grant(player, CombatIds.BRAWLER);
		StatEffects.refresh(player);
		near(helper, 7.0, player.getAttributeValue(Attributes.ATTACK_DAMAGE), "1 base + 6 Brawler");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		StatEffects.refresh(player);
		near(helper, 1.0, player.getAttributeValue(Attributes.ATTACK_DAMAGE), "Only with an empty hand");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void crossbowInfinitySavesPlainArrows(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.CROSSBOW));
		grant(player, CombatIds.CROSSBOW_INFINITY);
		helper.assertTrue(CombatSkill.freeAmmo(new ItemStack(Items.CROSSBOW), new ItemStack(Items.ARROW), player), "Plain arrows are free");
		helper.assertFalse(CombatSkill.freeAmmo(new ItemStack(Items.CROSSBOW), new ItemStack(Items.SPECTRAL_ARROW), player), "Special arrows still cost");
		helper.assertFalse(CombatSkill.freeAmmo(new ItemStack(Items.BOW), new ItemStack(Items.ARROW), player), "Bows need Rainbow");
		helper.succeed();
	}

	// ------------------------------------------------------------------ area effects

	/** Area tests run beside other tests, so they shrink their radius to stay inside their own plot. */
	private static void withSmallRadii(Runnable test) {
		var config = BloomServices.config();
		double lightning = config.lightningShockRadius;
		double overkill = config.overkillRadius;
		double aura = config.auraSwordRadius;
		config.lightningShockRadius = 3.0;
		config.overkillRadius = 2.0;
		config.auraSwordRadius = 2.5;

		try {
			test.run();
		} finally {
			config.lightningShockRadius = lightning;
			config.overkillRadius = overkill;
			config.auraSwordRadius = aura;
		}
	}

	@GameTest(template = "empty")
	public static void lightningChainsBetweenEnemies(GameTestHelper helper) {
		withSmallRadii(() -> {
			ServerPlayer player = player(helper, new ItemStack(Items.TRIDENT));
			LivingEntity a = zombie(helper, 3, 3);
			LivingEntity b = zombie(helper, 4, 3);
			LivingEntity c = zombie(helper, 3, 4);
			helper.assertTrue(CombatSkill.lightningChain(helper.getLevel(), player, a, 4.0F), "Three enemies: the chain fires");
			helper.assertTrue(b.getHealth() >= 20.0F && c.getHealth() >= 20.0F, "The jumps travel before they land");
			CombatSkill.runQueuedForTests();
			// a -> nearest other -> the last other -> back to the nearest one not just hit (a, 1 block away).
			helper.assertTrue(a.getHealth() < 20.0F && b.getHealth() < 20.0F && c.getHealth() < 20.0F, "All three were shocked");
		});
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void lightningNeedsTwoEnemies(GameTestHelper helper) {
		withSmallRadii(() -> {
			ServerPlayer player = player(helper, new ItemStack(Items.TRIDENT));
			LivingEntity alone = zombie(helper, 4, 4);
			helper.assertFalse(CombatSkill.lightningChain(helper.getLevel(), player, alone, 4.0F), "One enemy: no lightning");
		});
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void overkillSplashesAndBloodlustBuilds(GameTestHelper helper) {
		withSmallRadii(() -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer player = player(helper, ItemStack.EMPTY);
			grant(player, CombatIds.OVERKILL, CombatIds.BLOODLUST);
			LivingEntity weak = zombie(helper, 3, 3);
			LivingEntity bystander = zombie(helper, 4, 3);
			weak.setHealth(2.0F);
			weak.hurt(hit(player), 20.0F);
			helper.assertFalse(weak.isAlive(), "Overkill kill");
			CombatSkill.runQueuedForTests();
			helper.assertTrue(bystander.getHealth() < 15.0F, "The excess splashed onto the neighbour, health " + bystander.getHealth());

			LivingEntity fresh = zombie(helper, 6, 6);
			// One kill = one Bloodlust stack (+8%); nothing else applies to this hit.
			near(helper, 10.8, CombatSkill.modifyIncoming(fresh, hit(player), 10.0F), "Bloodlust x1");
			CombatSkill.endSwingForTests();
		});
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void batterUpSweepsWithAnAxe(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_AXE));
		grant(player, CombatIds.FULL_SWEEP, CombatIds.BATTER_UP);
		LivingEntity target = zombie(helper, 3, 3);
		LivingEntity neighbour = zombie(helper, 3, 4);
		CombatSkill.recordSwingForTests(player, target, 1.0F, false, true);
		target.hurt(hit(player), 6.0F);
		CombatSkill.runQueuedForTests();
		CombatSkill.endSwingForTests();
		helper.assertTrue(neighbour.getHealth() < 20.0F, "The neighbour was swept");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void weaponSkillMajorsOnOneWeaponConflict(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		grant(player, CombatIds.CHARGE_ATTACK, CombatIds.HEAVY_HANDS);
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);
		service.purchaseMajor(profile, CombatIds.LIGHTNING_SHOCK);
		ActionResult result = service.purchaseMajor(profile, CombatIds.CATCH);
		helper.assertTrue(result.outcome() == ActionResult.Outcome.CONFLICT && result.involved().contains(CombatIds.HEAVY_HANDS)
				&& result.involved().contains(CombatIds.CHARGE_ATTACK), "Catch! can't be owned with Heavy Hands or Charge Attack: " + result);
		helper.assertTrue(service.purchaseMajor(profile, CombatIds.LIGHTNING_SUMMON).outcome() == ActionResult.Outcome.CONFLICT,
				"Lightning Summon can't be owned with Heavy Hands");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void catchReturnsTridents(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.TRIDENT));
		grant(player, CombatIds.LIGHTNING_SHOCK, CombatIds.CATCH); // Catch! needs a Trident Major first
		net.minecraft.world.entity.projectile.ThrownTrident trident = net.minecraft.world.entity.EntityType.TRIDENT.create(helper.getLevel());
		trident.setOwner(player);
		trident.setPos(player.getX(), player.getY() + 1.0, player.getZ());
		helper.getLevel().addFreshEntity(trident);
		byte loyalty = trident.getEntityData().get(com.freshorangeslices.bloomlevelup.mixin.ThrownTridentAccessor.bloomlevelup$loyalty());
		TestPlayers.equal(helper, (byte) 3, loyalty, "A trident thrown with Catch! comes back like Loyalty III");
		trident.discard();
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void auraSwordPulsesAfterFiveHits(GameTestHelper helper) {
		withSmallRadii(() -> auraSword(helper));
		helper.succeed();
	}

	private static void auraSword(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		grant(player, CombatIds.FULL_SWEEP, CombatIds.AURA_SWORD);
		LivingEntity target = zombie(helper, 3, 3);
		LivingEntity nearby = zombie(helper, 5, 3);

		for (int i = 1; i <= 5; i++) {
			CombatSkill.recordSwingForTests(player, target, 1.0F, false, true);
			// Rising damage gets through vanilla's hurt cooldown each time.
			target.hurt(hit(player), 1.0F + i);
			CombatSkill.endSwingForTests();
		}

		TestPlayers.equal(helper, 20.0F, nearby.getHealth(), "Nothing yet");
		CombatSkill.runQueuedForTests();
		helper.assertTrue(nearby.getHealth() < 20.0F, "The aura pulse reached the nearby zombie");
	}

	// ------------------------------------------------------------------ input Majors

	@GameTest(template = "empty")
	public static void chargedSwingDealsDouble(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_AXE));
		grant(player, CombatIds.CHARGE_ATTACK);
		LivingEntity zombie = zombie(helper, 1, 1);
		CombatAbilities.armChargeForTests(player);
		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		near(helper, 12.0, CombatSkill.modifyIncoming(zombie, hit(player), 6.0F), "Charged: 2x");
		CombatSkill.endSwingForTests();

		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		near(helper, 6.0, CombatSkill.modifyIncoming(zombie, hit(player), 6.0F), "The charge is spent");
		CombatSkill.endSwingForTests();
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void chargedSwingsAndThrowsWearTheWeapon(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		grant(player, CombatIds.CHARGE_ATTACK, CombatIds.HEAVY_HANDS);
		LivingEntity zombie = zombie(helper, 1, 1);
		CombatAbilities.armChargeForTests(player);
		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		CombatSkill.modifyIncoming(zombie, hit(player), 6.0F);
		CombatSkill.endSwingForTests();
		// A sword hit wears it by 1 on its own (not run here); the charge adds the rest of the 2.
		TestPlayers.equal(helper, BloomServices.config().chargedSwingDurabilityCost - 1, player.getMainHandItem().getDamageValue(),
				"A charged swing's extra wear");

		ServerPlayer thrower = player(helper, new ItemStack(Items.IRON_AXE));
		grant(thrower, CombatIds.LIGHTNING_SHOCK, CombatIds.CATCH);
		helper.assertTrue(CombatAbilities.throwForTests(thrower, false), "Thrown");
		ThrownWeapon axe = helper.getLevel().getEntitiesOfClass(ThrownWeapon.class, thrower.getBoundingBox().inflate(4.0)).get(0);
		TestPlayers.equal(helper, BloomServices.config().catchThrowDurabilityCost, axe.getItem().getDamageValue(), "A throw's wear");
		axe.discard();

		ItemStack worn = new ItemStack(Items.IRON_AXE);
		worn.setDamageValue(worn.getMaxDamage() - BloomServices.config().catchThrowDurabilityCost);
		thrower.setItemInHand(InteractionHand.MAIN_HAND, worn);
		helper.assertFalse(CombatAbilities.throwForTests(thrower, false), "Too worn to throw");
		helper.assertTrue(thrower.getMainHandItem() == worn, "And it stays in hand");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void thrownWeaponsHitForTheirOwnDamage(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		ThrownWeapon thrown = ThrownWeapon.create(helper.getLevel(), player, new ItemStack(Items.IRON_SWORD));
		helper.assertTrue(thrown.getItem().is(Items.IRON_SWORD), "It carries the sword");
		helper.assertTrue(thrown.syncedItem().is(Items.IRON_SWORD), "Clients are told it's a sword (not a trident)");
		helper.assertTrue(CombatAbilities.throwable(new ItemStack(Items.IRON_AXE)) && !CombatAbilities.throwable(new ItemStack(Items.TRIDENT)),
				"Axes throw with Catch!, tridents throw on their own");
		LivingEntity zombie = zombie(helper, 1, 1);
		DamageSource source = player.damageSources().trident(thrown, player);
		// A trident hit is 8; a thrown iron sword hits like the sword: 1 base + 5.
		near(helper, 6.0, CombatSkill.modifyIncoming(zombie, source, 8.0F), "Iron sword damage");
		CombatSkill.endSwingForTests();
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void thrownSwordsCarryTheirMajors(GameTestHelper helper) {
		// A Catch! throw hits with the sword thrown: Slash can make the target bleed (as on main).
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		grant(player, CombatIds.SLASH);
		ThrownWeapon thrown = ThrownWeapon.create(helper.getLevel(), player, new ItemStack(Items.IRON_SWORD));
		LivingEntity zombie = zombie(helper, 1, 1);
		var config = BloomServices.config();
		double chance = config.slashChance;
		config.slashChance = 1.0;

		try {
			helper.assertTrue(CombatSkill.firedWith(thrown).is(Items.IRON_SWORD), "The throw's weapon is the sword");
			zombie.hurt(player.damageSources().trident(thrown, player), 8.0F);
			helper.assertTrue(zombie.hasEffect(BloomEffects.BLEED.get()), "A thrown sword's hit can Bleed");
		} finally {
			config.slashChance = chance;
			CombatSkill.endSwingForTests();
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void bloomSideDamageDoesntKnockBack(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		LivingEntity zombie = zombie(helper, 2, 4);
		((net.minecraft.world.entity.Mob) zombie).setNoAi(true);
		zombie.hurt(com.freshorangeslices.bloomlevelup.skill.combat.BloomDamage.overkill(helper.getLevel(), player), 1.0F);
		helper.assertTrue(zombie.getDeltaMovement().horizontalDistance() < 1.0E-3, "Overkill's splash doesn't push, moved "
				+ zombie.getDeltaMovement());

		LivingEntity other = zombie(helper, 6, 4);
		((net.minecraft.world.entity.Mob) other).setNoAi(true);
		other.hurt(player.damageSources().playerAttack(player), 1.0F);
		helper.assertTrue(other.getDeltaMovement().horizontalDistance() > 0.05, "An ordinary hit still does");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void thrownWeaponsPierceWithAPierceLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		BlockPos aside = helper.absolutePos(new BlockPos(1, 1, 1));
		player.setPos(aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5); // out of the line of fire
		LivingEntity first = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 6));
		LivingEntity second = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 6));
		((net.minecraft.world.entity.Mob) first).setNoAi(true);
		((net.minecraft.world.entity.Mob) second).setNoAi(true);

		// Pierce 1: through the first zombie, and the second one stops it (then it comes back).
		ThrownWeapon thrown = ThrownWeapon.create(level, player, new ItemStack(Items.IRON_SWORD));
		thrown.setPierceLevel((byte) 1);
		BlockPos start = helper.absolutePos(new BlockPos(6, 1, 6));
		thrown.setPos(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
		thrown.setNoGravity(true);
		thrown.setDeltaMovement(-1.0, 0.0, 0.0);
		level.addFreshEntity(thrown);

		helper.succeedWhen(() -> {
			helper.assertTrue(first.getHealth() < 20.0F, "The first zombie was hit");
			helper.assertTrue(second.getHealth() < 20.0F, "The throw went on through it and hit the second");
		});
	}

	// ------------------------------------------------------------------ Master of Arms

	@GameTest(template = "empty")
	public static void masterOfArmsLetsClashingSkillsBeOwned(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		grant(player, CombatIds.CHARGE_ATTACK, CombatIds.LIGHTNING_SHOCK, CombatIds.MASTER_OF_ARMS, CombatIds.HEAVY_HANDS, CombatIds.CATCH);
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);
		helper.assertTrue(profile.owns(CombatIds.HEAVY_HANDS) && profile.owns(CombatIds.CATCH) && profile.owns(CombatIds.CHARGE_ATTACK),
				"Heavy Hands, Charge Attack and Catch! together");

		helper.assertTrue(service.toggleMajor(profile, CombatIds.MASTER_OF_ARMS).outcome() == ActionResult.Outcome.NOT_TOGGLEABLE,
				"Master of Arms can't be switched off");

		// Charged Throw: double the thrown sword's damage, and it pierces.
		LivingEntity zombie = zombie(helper, 1, 1);
		helper.assertTrue(CombatAbilities.throwForTests(player, true), "Charged throw");
		List<ThrownWeapon> thrown = helper.getLevel().getEntitiesOfClass(ThrownWeapon.class, player.getBoundingBox().inflate(4.0));
		TestPlayers.equal(helper, 1, thrown.size(), "One thrown sword");
		ThrownWeapon sword = thrown.get(0);
		helper.assertTrue(sword.isCharged() && sword.getPierceLevel() >= BloomServices.config().chargedThrowPierce, "Charged and piercing");
		TestPlayers.equal(helper, BloomServices.config().chargedThrowDurabilityCost, sword.getItem().getDamageValue(), "A charged throw's wear");
		near(helper, 12.0, CombatSkill.modifyIncoming(zombie, player.damageSources().trident(sword, player), 8.0F), "2x the iron sword's 6");
		CombatSkill.endSwingForTests();
		sword.discard();

		// Refunding it asks first, then takes every clashing Major it held together (Lightning Shock stays).
		ActionResult asks = service.refundMajor(profile, CombatIds.MASTER_OF_ARMS);
		helper.assertTrue(asks.outcome() == ActionResult.Outcome.REQUIRED_BY_OWNED
				&& asks.involved().equals(List.of(CombatIds.CATCH, CombatIds.CHARGE_ATTACK, CombatIds.HEAVY_HANDS)), "Asks first: " + asks);
		helper.assertTrue(service.refundMajorWithDependents(profile, CombatIds.MASTER_OF_ARMS).ok(), "Then refunds them all");
		helper.assertTrue(profile.majors().keySet().equals(java.util.Set.of(CombatIds.LIGHTNING_SHOCK)), "Only Lightning Shock left: "
				+ profile.majors().keySet());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void stormCallStrikesSeveralBolts(GameTestHelper helper) {
		var config = BloomServices.config();
		double radius = config.stormCallRadius;
		config.stormCallRadius = 1.0; // keep the bolts inside this test's area

		try {
			stormCall(helper);
		} finally {
			config.stormCallRadius = radius;
		}

		helper.succeed();
	}

	private static void stormCall(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.TRIDENT));
		grant(player, CombatIds.LIGHTNING_SUMMON, CombatIds.CHARGE_ATTACK, CombatIds.MASTER_OF_ARMS, CombatIds.HEAVY_HANDS);
		player.setXRot(90.0F); // looking straight down at the floor
		int bolts = BloomServices.config().stormCallBolts;

		helper.assertTrue(CombatAbilities.lightningForTests(player, true), "Storm Call struck");
		CombatSkill.runQueuedForTests();
		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(8.0);
		TestPlayers.equal(helper, bolts, helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, area).size(),
				"One bolt each");
		ItemStack trident = player.getMainHandItem();
		int single = (int) Math.ceil(trident.getMaxDamage() * BloomServices.config().lightningSummonCostRatio);
		helper.assertTrue(trident.getDamageValue() > single, "It costs more than one bolt: " + trident.getDamageValue());
		helper.assertTrue(player.getCooldowns().isOnCooldown(trident.getItem()), "Then a cooldown");
	}

	@GameTest(template = "empty")
	public static void hugeVanillaXpIsCountedAndSpentExactly(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		player.setExperienceLevels(10_000);
		var wallet = new com.freshorangeslices.bloomlevelup.player.PlayerXpWallet(player);
		long total = wallet.totalPoints();
		TestPlayers.equal(helper, 448_377_220L, total, "10,000 levels in raw XP");

		wallet.spend(1_000);
		TestPlayers.equal(helper, total - 1_000, wallet.totalPoints(), "Exactly 1000 points gone");
		TestPlayers.equal(helper, 9_999, player.experienceLevel, "Level 9,999 and most of a bar left");

		// Far past an int: still counted, and spending leaves the rest exact.
		player.setExperienceLevels(100_000);
		long big = wallet.totalPoints();
		helper.assertTrue(big > Integer.MAX_VALUE, "100,000 levels is more than an int holds: " + big);
		wallet.spend(5);
		TestPlayers.equal(helper, big - 5, wallet.totalPoints(), "Exactly 5 points gone");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void assaultRifleLoadsAndFiresAMagazine(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.CROSSBOW));
		grant(player, CombatIds.ASSAULT_RIFLE);
		player.getInventory().add(new ItemStack(Items.ARROW, 20));
		ItemStack crossbow = player.getMainHandItem();
		CombatAbilities.setLoaded(crossbow, List.of(new ItemStack(Items.ARROW)));

		CombatAbilities.fillMagazine(player, crossbow);
		TestPlayers.equal(helper, 16, CombatAbilities.loaded(crossbow).size(), "A full magazine");
		TestPlayers.equal(helper, 5, player.getInventory().countItem(Items.ARROW), "15 more arrows loaded");

		CombatAbilities.fireFromMagazine(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		TestPlayers.equal(helper, 15, CombatAbilities.loaded(crossbow).size(), "One shot per click");
		helper.assertTrue(CrossbowItem.isCharged(crossbow), "Still loaded");
		helper.succeed();
	}

	// ------------------------------------------------------------------ coverage round (build 87)

	@GameTest(template = "empty")
	public static void powerAndTempoBecomeAttributes(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		points(player, CombatIds.POWER, 50);
		points(player, CombatIds.TEMPO, 50);
		StatEffects.refresh(player);
		near(helper, 1.0 + 3.0, player.getAttributeValue(Attributes.ATTACK_DAMAGE), "50 Power = +3 attack damage");
		near(helper, 4.0 * 1.5, player.getAttributeValue(Attributes.ATTACK_SPEED), "50 Tempo = 1.5x attack speed");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void piercingAndStraightShotShapeBowArrows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack bow = new ItemStack(Items.BOW);
		ServerPlayer player = player(helper, bow);
		grant(player, CombatIds.PIERCING_SHOT, CombatIds.STRAIGHT_SHOT);

		// 1.20.1 arrows don't remember their bow: Bloom takes it from the shooter's hand as the arrow appears.
		Arrow arrow = new Arrow(level, player);
		level.addFreshEntity(arrow);
		helper.assertTrue(arrow.getPierceLevel() >= BloomServices.config().piercingShotLevel,
				"Piercing Shot: the arrow goes through 4 mobs, pierce " + arrow.getPierceLevel());
		helper.assertTrue(arrow.isNoGravity(), "Straight Shot: the arrow doesn't drop");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
		Arrow bolt = new Arrow(level, player);
		level.addFreshEntity(bolt);
		helper.assertTrue(bolt.getPierceLevel() == 0 && !bolt.isNoGravity(), "Bow Majors leave crossbow bolts alone");

		// A straight arrow that never lands vanishes after 5 s.
		arrow.tickCount = BloomServices.config().straightShotMaxTicks + 1;
		CombatSkill.endSwingForTests();
		helper.assertTrue(arrow.isRemoved(), "Straight Shot arrows vanish when they never land");
		bolt.discard();
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void marksmanAndReinforcedShotAddArrowDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack bow = new ItemStack(Items.BOW);
		ServerPlayer archer = player(helper, bow);
		grant(archer, CombatIds.MARKSMAN, CombatIds.REINFORCED_SHOT);
		LivingEntity zombie = zombie(helper, 1, 1);
		Arrow drawn = new Arrow(level, archer);
		CombatSkill.firedWithForTests(drawn, bow);
		drawn.setCritArrow(true); // a fully drawn shot

		double[][] cases = {{40.0, 9.0}, {25.0, 7.5}, {8.0, 6.0}};
		String[] what = {"40 blocks: the full +50%", "25 blocks: halfway, +25%", "Under 10 blocks: nothing"};

		for (int i = 0; i < cases.length; i++) {
			archer.setPos(zombie.getX() + cases[i][0], zombie.getY(), zombie.getZ());
			near(helper, cases[i][1], CombatSkill.modifyIncoming(zombie, archer.damageSources().arrow(drawn, archer), 6.0F), "Marksman at " + what[i]);
			CombatSkill.endSwingForTests();
		}

		archer.setPos(zombie.getX() + 40.0, zombie.getY(), zombie.getZ());
		Arrow loose = new Arrow(level, archer);
		CombatSkill.firedWithForTests(loose, bow);
		loose.setCritArrow(false);
		near(helper, 6.0, CombatSkill.modifyIncoming(zombie, archer.damageSources().arrow(loose, archer), 6.0F), "A half-drawn shot gets no Marksman");
		CombatSkill.endSwingForTests();

		Arrow bolt = new Arrow(level, archer);
		CombatSkill.firedWithForTests(bolt, new ItemStack(Items.CROSSBOW));
		near(helper, 9.0, CombatSkill.modifyIncoming(zombie, archer.damageSources().arrow(bolt, archer), 6.0F), "Reinforced Shot: +3 on crossbow bolts");
		CombatSkill.endSwingForTests();
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void disarmKnocksTheWeaponOut(GameTestHelper helper) {
		var config = BloomServices.config();
		double chance = config.disarmChance;
		config.disarmChance = 1.0; // the roll itself is plain random; this checks what a success does

		try {
			ServerLevel level = helper.getLevel();
			ServerPlayer boxer = player(helper, ItemStack.EMPTY);
			grant(boxer, CombatIds.DISARM);
			LivingEntity armed = zombie(helper, 1, 1);
			armed.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			CombatSkill.recordSwingForTests(boxer, armed, 1.0F, false, true);
			armed.hurt(hit(boxer), 2.0F);
			CombatSkill.runQueuedForTests();
			CombatSkill.endSwingForTests();
			helper.assertTrue(armed.getMainHandItem().isEmpty(), "A bare-handed hit knocks the sword out of its hand");

			ServerPlayer swordsman = player(helper, new ItemStack(Items.IRON_SWORD));
			grant(swordsman, CombatIds.DISARM);
			LivingEntity other = zombie(helper, 3, 1);
			other.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			CombatSkill.recordSwingForTests(swordsman, other, 1.0F, false, true);
			other.hurt(hit(swordsman), 2.0F);
			CombatSkill.runQueuedForTests();
			CombatSkill.endSwingForTests();
			helper.assertFalse(other.getMainHandItem().isEmpty(), "Disarm is a fists Major");
		} finally {
			config.disarmChance = chance;
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void slashBleedsOnFullStrengthSwordHits(GameTestHelper helper) {
		var config = BloomServices.config();
		double chance = config.slashChance;
		config.slashChance = 1.0; // the roll itself is plain random; this checks which hits may bleed

		try {
			ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
			grant(player, CombatIds.SLASH);
			LivingEntity full = zombie(helper, 3, 3);
			CombatSkill.recordSwingForTests(player, full, 1.0F, false, true);
			full.hurt(hit(player), 6.0F);
			CombatSkill.endSwingForTests();
			helper.assertTrue(full.hasEffect(BloomEffects.BLEED.get()), "A full-strength sword hit can Bleed");

			LivingEntity weak = zombie(helper, 5, 5);
			CombatSkill.recordSwingForTests(player, weak, 0.5F, false, true);
			weak.hurt(hit(player), 6.0F);
			CombatSkill.endSwingForTests();
			helper.assertTrue(weak.getHealth() < 20.0F, "The weak swing still hit");
			helper.assertFalse(weak.hasEffect(BloomEffects.BLEED.get()), "A half-strength swing never Bleeds");

			ServerPlayer axeman = player(helper, new ItemStack(Items.IRON_AXE));
			grant(axeman, CombatIds.SLASH);
			LivingEntity chopped = zombie(helper, 1, 5);
			CombatSkill.recordSwingForTests(axeman, chopped, 1.0F, false, true);
			chopped.hurt(hit(axeman), 6.0F);
			CombatSkill.endSwingForTests();
			helper.assertFalse(chopped.hasEffect(BloomEffects.BLEED.get()), "Slash is a sword Major");
		} finally {
			config.slashChance = chance;
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void unrestrictedRiptideNeedsNoEnchantment(GameTestHelper helper) {
		ItemStack trident = new ItemStack(Items.TRIDENT);
		ServerPlayer player = player(helper, trident);
		player.setShiftKeyDown(true);
		TestPlayers.equal(helper, 0, CombatSkill.riptideLevel(trident, player, 0), "Without the Major: no riptide");

		grant(player, CombatIds.UNRESTRICTED_RIPTIDE);
		TestPlayers.equal(helper, (int) Math.round(BloomServices.config().unrestrictedRiptideStrength), CombatSkill.riptideLevel(trident, player, 0),
				"Sneaking on release: riptide with no enchantment");
		helper.assertTrue(CombatSkill.riptideAnywhere(player), "No water or rain needed");
		TestPlayers.equal(helper, 2, CombatSkill.riptideLevel(trident, player, 2), "A real Riptide enchantment keeps its own level");

		player.setShiftKeyDown(false);
		TestPlayers.equal(helper, 0, CombatSkill.riptideLevel(trident, player, 0), "Not sneaking: a normal throw");
		helper.succeed();
	}


}
