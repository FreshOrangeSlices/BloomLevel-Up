package com.freshorangeslices.bloomlevelup.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

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
 * Combat on a real server with fake players. Fake players never tick, so swings that depend on a charged
 * attack (crits, full-strength hits) are recorded with explicit state; the damage itself is real.
 */
public class CombatGameTests {
	private static final double EPS = 1e-3;

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer player(GameTestHelper helper, ItemStack mainHand) {
		ServerPlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bloom-test"));
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
		return helper.spawn(EntityTypes.ZOMBIE, new BlockPos(x, 2, z));
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

	@GameTest
	public void damageXpAndKillBonus(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		ServerLevel level = helper.getLevel();
		LivingEntity zombie = zombie(helper, 1, 1);

		zombie.hurtServer(level, hit(player), 8.0F);
		double real = 20.0 - zombie.getHealth();
		helper.assertTrue(real > 0.0, "The hit landed");
		near(helper, real * 0.5, combatXp(player), "0.5 XP per point of real damage");

		zombie.hurtServer(level, hit(player), 100.0F);
		helper.assertFalse(zombie.isAlive(), "Second hit kills");
		// Damage XP is capped at the 20 health it had (10 XP); the kill adds half of that.
		near(helper, 15.0, combatXp(player), "Damage XP capped by health, plus a 50% kill bonus");
		helper.succeed();
	}

	// ------------------------------------------------------------------ stats

	@GameTest
	public void criticalAndFirstStrikeMultiply(GameTestHelper helper) {
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

	@GameTest
	public void penetrationIgnoresArmor(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		points(player, CombatIds.PENETRATION, 100);
		LivingEntity zombie = zombie(helper, 1, 1);
		near(helper, 5.0, CombatSkill.penetrate(hit(player), zombie, 10.0F), "100 Penetration ignores half the armor");
		helper.succeed();
	}

	@GameTest
	public void lootingAddsVirtualLevels(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		points(player, CombatIds.LOOTING, 100);
		Holder<Enchantment> looting = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
		helper.assertValueEqual(3, EnchantmentHelper.getEnchantmentLevel(looting, player), "100 Looting = Looting III");
		helper.succeed();
	}

	// ------------------------------------------------------------------ weapon Majors

	@GameTest
	public void executeFinishesLowTargetsWithSwords(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer swordsman = player(helper, new ItemStack(Items.IRON_SWORD));
		grant(swordsman, CombatIds.EXECUTE);
		LivingEntity low = zombie(helper, 1, 1);
		low.setHealth(10.0F);
		low.hurtServer(level, hit(swordsman), 5.0F);
		helper.assertFalse(low.isAlive(), "Under 30% after the hit: executed");

		ServerPlayer axeman = player(helper, new ItemStack(Items.IRON_AXE));
		grant(axeman, CombatIds.EXECUTE);
		LivingEntity other = zombie(helper, 3, 1);
		other.setHealth(10.0F);
		other.hurtServer(level, hit(axeman), 5.0F);
		helper.assertTrue(other.isAlive(), "Execute is a sword Major");

		// Copycat spreads it to the axe.
		grant(axeman, CombatIds.COPYCAT);
		BloomServices.progression().selectCopySource(BloomAttachments.profile(axeman), CombatIds.COPYCAT, CombatIds.EXECUTE);
		LivingEntity third = zombie(helper, 5, 1);
		third.setHealth(10.0F);
		third.hurtServer(level, hit(axeman), 5.0F);
		helper.assertFalse(third.isAlive(), "Copycat: Execute with an axe");
		helper.succeed();
	}

	@GameTest
	public void guaranteedCriticalCritsForTwoLessDamage(GameTestHelper helper) {
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

	@GameTest
	public void armorBreakWearsArmorOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer breaker = player(helper, new ItemStack(Items.IRON_AXE));
		grant(breaker, CombatIds.ARMOR_BREAK);
		ServerPlayer plain = player(helper, new ItemStack(Items.IRON_AXE));

		LivingEntity armored = zombie(helper, 1, 1);
		armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		armored.hurtServer(level, hit(breaker), 8.0F);
		LivingEntity control = zombie(helper, 3, 1);
		control.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		control.hurtServer(level, hit(plain), 8.0F);

		int broken = armored.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
		int normal = control.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
		helper.assertTrue(broken >= normal + 3, "Armor Break adds about half the damage as wear (" + broken + " vs " + normal + ")");
		helper.succeed();
	}

	@GameTest
	public void bleedHurtsThroughArmor(GameTestHelper helper) {
		LivingEntity zombie = zombie(helper, 1, 1);
		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		zombie.tickCount = 0; // a damage tick (drips in between)
		BloomEffects.BLEED.value().applyEffectTick(helper.getLevel(), zombie, 0);
		near(helper, 19.0, zombie.getHealth(), "Half a heart, armor ignored");
		zombie.addEffect(new MobEffectInstance(BloomEffects.BLEED, 60));
		helper.assertTrue(zombie.hasEffect(BloomEffects.BLEED), "Bleed is a real status effect");
		helper.succeed();
	}

	@GameTest
	public void comboAndPredatorRamp(GameTestHelper helper) {
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

	@GameTest
	public void deathFromAboveAddsMaceFallDamage(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		grant(player, CombatIds.STORED_IMPACT, CombatIds.DEATH_FROM_ABOVE);
		LivingEntity zombie = zombie(helper, 1, 1);
		player.fallDistance = 3.0;
		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, false);
		near(helper, 5.0 + 12.0, CombatSkill.modifyIncoming(zombie, hit(player), 5.0F), "3 blocks of fall = +12, like a mace");
		CombatSkill.endSwingForTests();
		player.fallDistance = 0.0;
		helper.succeed();
	}

	@GameTest
	public void brawlerHitsHarderBareHanded(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		grant(player, CombatIds.BRAWLER);
		StatEffects.refresh(player);
		near(helper, 7.0, player.getAttributeValue(Attributes.ATTACK_DAMAGE), "1 base + 6 Brawler");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		StatEffects.refresh(player);
		near(helper, 1.0, player.getAttributeValue(Attributes.ATTACK_DAMAGE), "Only with an empty hand");
		helper.succeed();
	}

	@GameTest
	public void crossbowInfinitySavesPlainArrows(GameTestHelper helper) {
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

	@GameTest
	public void lightningChainsBetweenEnemies(GameTestHelper helper) {
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

	@GameTest
	public void lightningNeedsTwoEnemies(GameTestHelper helper) {
		withSmallRadii(() -> {
			ServerPlayer player = player(helper, new ItemStack(Items.TRIDENT));
			LivingEntity alone = zombie(helper, 4, 4);
			helper.assertFalse(CombatSkill.lightningChain(helper.getLevel(), player, alone, 4.0F), "One enemy: no lightning");
		});
		helper.succeed();
	}

	@GameTest
	public void overkillSplashesAndBloodlustBuilds(GameTestHelper helper) {
		withSmallRadii(() -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer player = player(helper, ItemStack.EMPTY);
			grant(player, CombatIds.OVERKILL, CombatIds.BLOODLUST);
			LivingEntity weak = zombie(helper, 3, 3);
			LivingEntity bystander = zombie(helper, 4, 3);
			weak.setHealth(2.0F);
			weak.hurtServer(level, hit(player), 20.0F);
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

	@GameTest
	public void batterUpSweepsWithAnAxe(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_AXE));
		grant(player, CombatIds.FULL_SWEEP, CombatIds.BATTER_UP);
		LivingEntity target = zombie(helper, 3, 3);
		LivingEntity neighbour = zombie(helper, 3, 4);
		CombatSkill.recordSwingForTests(player, target, 1.0F, false, true);
		target.hurtServer(level, hit(player), 6.0F);
		CombatSkill.runQueuedForTests();
		CombatSkill.endSwingForTests();
		helper.assertTrue(neighbour.getHealth() < 20.0F, "The neighbour was swept");
		helper.succeed();
	}

	@GameTest
	public void spearJabCountsAsTheSwing(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SPEAR));
		grant(player, CombatIds.FULL_SWEEP, CombatIds.BATTER_UP);
		LivingEntity target = zombie(helper, 3, 3);
		LivingEntity neighbour = zombie(helper, 3, 4);
		player.setOnGround(true);
		// A jab stabs through PiercingWeapon -> stabAttack, never Player.attack.
		CombatSkill.jabForTests(player, 1.0F);
		player.stabAttack(net.minecraft.world.entity.EquipmentSlot.MAINHAND, target, 6.0F, true, false, false);
		CombatSkill.endJab(player);
		CombatSkill.runQueuedForTests();
		CombatSkill.endSwingForTests();
		helper.assertTrue(target.getHealth() < 20.0F, "The jab hit");
		helper.assertTrue(neighbour.getHealth() < 20.0F, "Batter Up! swept from the jab");
		helper.succeed();
	}

	@GameTest
	public void longReachStretchesTheSpear(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SPEAR));
		float before = player.getAttackRangeWith(player.getMainHandItem()).maxReach();
		grant(player, CombatIds.LONG_REACH);
		float after = player.getAttackRangeWith(player.getMainHandItem()).maxReach();
		near(helper, before + 2.0, after, "Long Reach adds 2 blocks to the spear's own reach");
		BloomServices.progression().toggleMajor(BloomAttachments.profile(player), CombatIds.LONG_REACH);
		near(helper, before, player.getAttackRangeWith(player.getMainHandItem()).maxReach(), "Switched off: back to normal");
		helper.succeed();
	}

	@GameTest
	public void storedImpactAddsUpInOneWindow(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.MACE));
		grant(player, CombatIds.STORED_IMPACT);
		CombatSkill.storeImpactForTests(player, 4.0F);
		CombatSkill.storeImpactForTests(player, 6.0F);
		LivingEntity zombie = zombie(helper, 1, 1);
		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		near(helper, 11.0, CombatSkill.modifyIncoming(zombie, hit(player), 6.0F), "Both falls stored: 50% of 4 + 6 = +5");
		CombatSkill.endSwingForTests();

		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		near(helper, 6.0, CombatSkill.modifyIncoming(zombie, hit(player), 6.0F), "Spent by the hit");
		CombatSkill.endSwingForTests();

		CombatSkill.storeImpactForTests(player, 4.0F);
		BloomAttachments.profile(player).setCounter(CombatIds.STORED_IMPACT_EXPIRES_AT, helper.getLevel().getGameTime());
		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		near(helper, 6.0, CombatSkill.modifyIncoming(zombie, hit(player), 6.0F), "Lost when the window closes");
		CombatSkill.endSwingForTests();
		helper.succeed();
	}

	@GameTest
	public void windImpactBurstsFromTheGround(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.MACE));
		grant(player, CombatIds.WIND_IMPACT);
		helper.setBlock(new BlockPos(4, 0, 4), net.minecraft.world.level.block.Blocks.STONE);
		LivingEntity zombie = zombie(helper, 2, 4);
		player.setXRot(90.0F);
		player.xRotO = 90.0F;
		helper.assertTrue(CombatSkill.windBurst(player), "Right-clicking the ground bursts");
		helper.assertTrue(zombie.getDeltaMovement().horizontalDistance() > 0.05, "The zombie was blown away");
		helper.assertTrue(player.getCooldowns().isOnCooldown(player.getMainHandItem()), "Then a short cooldown");
		helper.assertFalse(CombatSkill.windBurst(player), "Not again during the cooldown");
		helper.succeed();
	}

	@GameTest
	public void weaponSkillMajorsOnOneWeaponConflict(GameTestHelper helper) {
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

	@GameTest
	public void catchThrowsSpearsAndReturnsTridents(GameTestHelper helper) {
		helper.assertTrue(CombatAbilities.throwable(new ItemStack(Items.IRON_SPEAR)), "Catch! throws spears");
		ServerPlayer player = player(helper, new ItemStack(Items.TRIDENT));
		grant(player, CombatIds.LIGHTNING_SHOCK, CombatIds.CATCH); // Catch! needs a Trident Major first
		net.minecraft.world.entity.projectile.arrow.ThrownTrident trident =
				net.minecraft.world.entity.EntityTypes.TRIDENT.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		trident.setOwner(player);
		trident.setPos(player.getX(), player.getY() + 1.0, player.getZ());
		helper.getLevel().addFreshEntity(trident);
		byte loyalty = trident.getEntityData().get(com.freshorangeslices.bloomlevelup.mixin.ThrownTridentAccessor.bloomlevelup$loyalty());
		helper.assertValueEqual((byte) 3, loyalty, "A trident thrown with Catch! comes back like Loyalty III");
		trident.discard();
		helper.succeed();
	}

	@GameTest
	public void auraSwordPulsesAfterFiveHits(GameTestHelper helper) {
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
			target.hurtServer(level, hit(player), 1.0F + i);
			CombatSkill.endSwingForTests();
		}

		helper.assertValueEqual(20.0F, nearby.getHealth(), "Nothing yet");
		CombatSkill.runQueuedForTests();
		helper.assertTrue(nearby.getHealth() < 20.0F, "The aura pulse reached the nearby zombie");
	}

	// ------------------------------------------------------------------ input Majors

	@GameTest
	public void chargedSwingDealsDouble(GameTestHelper helper) {
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

	@GameTest
	public void chargedSwingsAndThrowsWearTheWeapon(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		grant(player, CombatIds.CHARGE_ATTACK, CombatIds.HEAVY_HANDS);
		LivingEntity zombie = zombie(helper, 1, 1);
		CombatAbilities.armChargeForTests(player);
		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		CombatSkill.modifyIncoming(zombie, hit(player), 6.0F);
		CombatSkill.endSwingForTests();
		// A sword hit wears it by 1 on its own (not run here); the charge adds the rest of the 2.
		helper.assertValueEqual(BloomServices.config().chargedSwingDurabilityCost - 1, player.getMainHandItem().getDamageValue(),
				"A charged swing's extra wear");

		ServerPlayer thrower = player(helper, new ItemStack(Items.IRON_AXE));
		grant(thrower, CombatIds.LIGHTNING_SHOCK, CombatIds.CATCH);
		helper.assertTrue(CombatAbilities.throwForTests(thrower, false), "Thrown");
		ThrownWeapon axe = helper.getLevel().getEntitiesOfClass(ThrownWeapon.class, thrower.getBoundingBox().inflate(4.0)).get(0);
		helper.assertValueEqual(BloomServices.config().catchThrowDurabilityCost, axe.getItem().getDamageValue(), "A throw's wear");
		axe.discard();

		ItemStack worn = new ItemStack(Items.IRON_AXE);
		worn.setDamageValue(worn.getMaxDamage() - BloomServices.config().catchThrowDurabilityCost);
		thrower.setItemInHand(InteractionHand.MAIN_HAND, worn);
		helper.assertFalse(CombatAbilities.throwForTests(thrower, false), "Too worn to throw");
		helper.assertTrue(thrower.getMainHandItem() == worn, "And it stays in hand");
		helper.succeed();
	}

	@GameTest
	public void thrownWeaponsHitForTheirOwnDamage(GameTestHelper helper) {
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

	@GameTest(maxTicks = 80)
	public void thrownWeaponsPierceWithAPierceLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		BlockPos aside = helper.absolutePos(new BlockPos(1, 1, 1));
		player.setPos(aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5); // out of the line of fire
		LivingEntity first = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 6));
		LivingEntity second = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 6));
		((net.minecraft.world.entity.Mob) first).setNoAi(true);
		((net.minecraft.world.entity.Mob) second).setNoAi(true);

		// Pierce 1: through the first zombie, and the second one stops it (then it comes back).
		ThrownWeapon thrown = ThrownWeapon.create(level, player, new ItemStack(Items.IRON_SWORD));
		((com.freshorangeslices.bloomlevelup.mixin.AbstractArrowAccessor) thrown).bloomlevelup$setPierceLevel((byte) 1);
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

	@GameTest
	public void masterOfArmsLetsClashingSkillsBeOwned(GameTestHelper helper) {
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
		helper.assertValueEqual(1, thrown.size(), "One thrown sword");
		ThrownWeapon sword = thrown.get(0);
		helper.assertTrue(sword.isCharged() && sword.getPierceLevel() >= BloomServices.config().chargedThrowPierce, "Charged and piercing");
		helper.assertValueEqual(BloomServices.config().chargedThrowDurabilityCost, sword.getItem().getDamageValue(), "A charged throw's wear");
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

	@GameTest
	public void stormCallStrikesSeveralBolts(GameTestHelper helper) {
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
		helper.assertValueEqual(bolts, helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LightningBolt.class, area).size(),
				"One bolt each");
		ItemStack trident = player.getMainHandItem();
		int single = (int) Math.ceil(trident.getMaxDamage() * BloomServices.config().lightningSummonCostRatio);
		helper.assertTrue(trident.getDamageValue() > single, "It costs more than one bolt: " + trident.getDamageValue());
		helper.assertTrue(player.getCooldowns().isOnCooldown(trident), "Then a cooldown");
	}

	@GameTest
	public void galeThrowPullsMobsInThenBlowsThemAway(GameTestHelper helper) {
		var config = BloomServices.config();
		double pullRadius = config.galeThrowPullRadius;
		config.galeThrowPullRadius = 3.5; // keep it inside this test's area

		try {
			ServerLevel level = helper.getLevel();
			ServerPlayer player = player(helper, ItemStack.EMPTY);
			grant(player, CombatIds.WIND_IMPACT, CombatIds.MASTER_OF_ARMS);
			LivingEntity zombie = zombie(helper, 6, 4);
			BlockPos landing = helper.absolutePos(new BlockPos(4, 1, 4));
			net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);

			zombie.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			CombatAbilities.galeLandingForTests(level, player, at);
			helper.assertTrue(zombie.getDeltaMovement().x < -0.05, "Pulled toward the landing: " + zombie.getDeltaMovement());

			zombie.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			CombatSkill.runQueuedForTests();
			helper.assertTrue(zombie.getDeltaMovement().x > 0.05, "Then blown away: " + zombie.getDeltaMovement());
		} finally {
			config.galeThrowPullRadius = pullRadius;
		}

		helper.succeed();
	}

	@GameTest
	public void hugeVanillaXpIsCountedAndSpentExactly(GameTestHelper helper) {
		ServerPlayer player = player(helper, ItemStack.EMPTY);
		player.setExperienceLevels(10_000);
		var wallet = new com.freshorangeslices.bloomlevelup.player.PlayerXpWallet(player);
		long total = wallet.totalPoints();
		helper.assertValueEqual(448_377_220L, total, "10,000 levels in raw XP");

		wallet.spend(1_000);
		helper.assertValueEqual(total - 1_000, wallet.totalPoints(), "Exactly 1000 points gone");
		helper.assertValueEqual(9_999, player.experienceLevel, "Level 9,999 and most of a bar left");

		// Far past an int: still counted, and spending leaves the rest exact.
		player.setExperienceLevels(100_000);
		long big = wallet.totalPoints();
		helper.assertTrue(big > Integer.MAX_VALUE, "100,000 levels is more than an int holds: " + big);
		wallet.spend(5);
		helper.assertValueEqual(big - 5, wallet.totalPoints(), "Exactly 5 points gone");
		helper.succeed();
	}

	@GameTest
	public void assaultRifleLoadsAndFiresAMagazine(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.CROSSBOW));
		grant(player, CombatIds.ASSAULT_RIFLE);
		player.getInventory().add(new ItemStack(Items.ARROW, 20));
		ItemStack crossbow = player.getMainHandItem();
		crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));

		CombatAbilities.fillMagazine(player, crossbow);
		helper.assertValueEqual(16, crossbow.get(DataComponents.CHARGED_PROJECTILES).size(), "A full magazine");
		helper.assertValueEqual(5, player.getInventory().countItem(Items.ARROW), "15 more arrows loaded");

		CombatAbilities.fireFromMagazine((CrossbowItem) crossbow.getItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertValueEqual(15, crossbow.get(DataComponents.CHARGED_PROJECTILES).size(), "One shot per click");
		helper.succeed();
	}
}
