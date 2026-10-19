package com.freshorangeslices.bloomlevelup.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.item.BloomComponents;
import com.freshorangeslices.bloomlevelup.item.FuelImprint;
import com.freshorangeslices.bloomlevelup.mixin.ProjectileWeaponItemInvoker;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.misc.HoardersPocket;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscIds;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/** Misc on a real server with fake players (survival, never ticking). */
public class MiscGameTests {
	private static final double EPS = 1e-3;

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bloom-misc"));
		BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
		player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return player;
	}

	/** Every category to 100 (40 Major Levels), then buy the Majors in order. */
	private static void grant(ServerPlayer player, String... majors) {
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);

		for (String category : List.of(SkillXp.COMBAT, SkillXp.UTILITY, SkillXp.ARMOR, SkillXp.MISC)) {
			service.addXp(profile, category, 1.0e9);
		}

		for (String major : majors) {
			ActionResult result = service.purchaseMajor(profile, major);

			if (!result.ok()) {
				throw new IllegalStateException("Couldn't buy " + major + ": " + result.outcome());
			}
		}

		MiscSkill.refresh(player);
	}

	private static void near(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < EPS, what + ": expected " + expected + " but was " + actual);
	}

	private static Holder<Enchantment> enchantment(GameTestHelper helper, net.minecraft.resources.ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	// ------------------------------------------------------------------ XP and stats

	@GameTest
	public void orbsGiveMiscXpAndWisdomMultipliesVanillaXp(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);

		// Exactly Misc level 50 (50 + 10L XP per level), so the orb's Misc XP shows up as the bar.
		service.addXp(profile, SkillXp.MISC, 14750.0);
		helper.assertValueEqual(50, profile.category(SkillXp.MISC).level(), "Misc level");
		helper.assertTrue(service.allocate(profile, MiscIds.WISDOM, ProgressionService.Pool.NATURAL, 50).ok(), "Wisdom 50");

		ServerLevel level = helper.getLevel();
		ExperienceOrb orb = new ExperienceOrb(level, player.getX(), player.getY(), player.getZ(), 10);
		level.addFreshEntity(orb);
		orb.playerTouch(player);

		near(helper, 2.5, profile.category(SkillXp.MISC).xp(), "Misc XP = 25% of the orb's own value (not Wisdom's)");
		helper.assertValueEqual(30, player.totalExperience, "Wisdom 50 = 3x vanilla XP");
		helper.succeed();
	}

	@GameTest
	public void enduranceSlowsHunger(GameTestHelper helper) {
		ServerPlayer plain = player(helper);
		ServerPlayer enduring = player(helper);
		grant(enduring);
		helper.assertTrue(BloomServices.progression().allocate(BloomAttachments.profile(enduring), MiscIds.ENDURANCE,
				ProgressionService.Pool.NATURAL, 100).ok(), "Endurance 100");

		for (ServerPlayer player : List.of(plain, enduring)) {
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(5.0F);
			player.causeFoodExhaustion(5.0F); // over 4 drains a point of saturation
			player.getFoodData().tick(player);
		}

		near(helper, 4.0, plain.getFoodData().getSaturationLevel(), "Without Endurance 5 exhaustion costs saturation");
		near(helper, 5.0, enduring.getFoodData().getSaturationLevel(), "Endurance 100: 5 exhaustion counts as 3.3");
		helper.succeed();
	}

	// ------------------------------------------------------------------ food

	@GameTest
	public void ironStomachMakesPufferfishSafeAndFilling(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.IRON_STOMACH);
		player.getFoodData().setFoodLevel(0);
		player.getFoodData().setSaturation(0.0F);

		new ItemStack(Items.PUFFERFISH).finishUsingItem(helper.getLevel(), player);

		helper.assertFalse(player.hasEffect(MobEffects.POISON), "No poison");
		helper.assertFalse(player.hasEffect(MobEffects.HUNGER), "No hunger");
		helper.assertFalse(player.hasEffect(MobEffects.NAUSEA), "No nausea");
		helper.assertValueEqual(6, player.getFoodData().getFoodLevel(), "Fills like cooked chicken");
		near(helper, 6.0, player.getFoodData().getSaturationLevel(), "Saturation floor (capped by the food level)");

		// Without the Major the pufferfish is as nasty as ever.
		ServerPlayer plain = player(helper);
		new ItemStack(Items.PUFFERFISH).finishUsingItem(helper.getLevel(), plain);
		helper.assertTrue(plain.hasEffect(MobEffects.POISON), "Poisoned without Iron Stomach");
		helper.succeed();
	}

	@GameTest
	public void selectiveCleansingKeepsBuffs(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.SELECTIVE_CLEANSING);
		ServerLevel level = helper.getLevel();

		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200, 0));
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 1200, 0));
		new ItemStack(Items.MILK_BUCKET).finishUsingItem(level, player);
		helper.assertTrue(player.hasEffect(MobEffects.SPEED), "Milk keeps Speed");
		helper.assertFalse(player.hasEffect(MobEffects.POISON), "Milk clears Poison");

		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0));
		new ItemStack(Items.CARROT).finishUsingItem(level, player);
		helper.assertFalse(player.hasEffect(MobEffects.WEAKNESS), "A carrot clears Weakness");
		helper.assertTrue(player.hasEffect(MobEffects.SPEED), "...and keeps Speed");

		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0));
		new ItemStack(Items.APPLE).finishUsingItem(level, player);
		helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "Apples don't cleanse");
		helper.succeed();
	}

	@GameTest
	public void secondServingEatsWhenFull(GameTestHelper helper) {
		ServerPlayer plain = player(helper);
		ServerPlayer hungry = player(helper);
		grant(hungry, MiscIds.SECOND_SERVING);
		plain.getFoodData().setFoodLevel(20);
		hungry.getFoodData().setFoodLevel(20);

		helper.assertFalse(plain.canEat(false), "Full players can't eat normally");
		helper.assertTrue(hungry.canEat(false), "Second Serving eats anyway");
		helper.succeed();
	}

	// ------------------------------------------------------------------ effects

	@GameTest
	public void waterToWineStretchesAndCutsOnlyItsOwn(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.WATER_TO_WINE);

		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0));
		player.addEffect(new MobEffectInstance(MobEffects.HASTE, 20000, 0));
		player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));

		helper.assertValueEqual(12000, player.getEffect(MobEffects.SPEED).getDuration(), "30 s of Speed becomes 10 minutes");
		helper.assertValueEqual(12000, player.getEffect(MobEffects.WEAKNESS).getDuration(), "Debuffs too");
		helper.assertValueEqual(20000, player.getEffect(MobEffects.HASTE).getDuration(), "Long effects are untouched");
		helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION).isInfiniteDuration(), "Infinite stays infinite");

		ProgressionService service = BloomServices.progression();
		helper.assertTrue(service.toggleMajor(BloomAttachments.profile(player), MiscIds.WATER_TO_WINE).ok(), "Toggle off");
		MiscSkill.refresh(player);

		helper.assertValueEqual(2400, player.getEffect(MobEffects.SPEED).getDuration(), "Stretched effects drop to 2 minutes");
		helper.assertValueEqual(2400, player.getEffect(MobEffects.WEAKNESS).getDuration(), "Stretched debuffs too");
		helper.assertValueEqual(20000, player.getEffect(MobEffects.HASTE).getDuration(), "Naturally long effects keep their time");
		helper.succeed();
	}

	@GameTest
	public void overdoseClimbsTwoLevels(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.OVERDOSE);

		int[] expected = {0, 1, 2, 2};

		for (int amplifier : expected) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0));
			helper.assertValueEqual(amplifier, player.getEffect(MobEffects.SPEED).getAmplifier(), "Speed I re-applied climbs to III and stops");
		}

		// Beacon-style (ambient) effects don't climb.
		player.addEffect(new MobEffectInstance(MobEffects.HASTE, 300, 0, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.HASTE, 300, 0, true, true));
		helper.assertValueEqual(0, player.getEffect(MobEffects.HASTE).getAmplifier(), "Ambient effects are left alone");
		helper.succeed();
	}

	// ------------------------------------------------------------------ XP Insurance

	@GameTest
	public void xpInsuranceReturnsHalfOfTheLoss(GameTestHelper helper) {
		ServerPlayer dead = player(helper);
		ServerPlayer respawned = player(helper);
		grant(dead, MiscIds.XP_INSURANCE);
		dead.setExperienceLevels(30);
		dead.experienceProgress = 0.0F;

		ServerPlayerEvents.COPY_FROM.invoker().copyFromPlayer(dead, respawned, false);

		// Level 30 = 1395 XP: 100 drops as orbs, 1295 is lost, half of it (647) comes back.
		helper.assertValueEqual(647, respawned.totalExperience, "Half the lost XP comes back");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Hoarder's Pocket

	@GameTest
	public void hoardersPocketStoresAndBlocksStorage(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.HOARDERS_POCKET);

		HoardersPocket.PocketMenu menu = new HoardersPocket.PocketMenu(1, player.getInventory(), player, true);
		helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.SHULKER_BOX)), "No shulker boxes");
		helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.BUNDLE)), "No bundles");
		helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.DIAMOND)), "Diamonds are fine");

		menu.getSlot(3).set(new ItemStack(Items.DIAMOND, 12));
		menu.removed(player);

		HoardersPocket.PocketMenu again = new HoardersPocket.PocketMenu(2, player.getInventory(), player, false);
		helper.assertValueEqual(12, again.getSlot(3).getItem().getCount(), "Contents are saved on the player");
		helper.assertFalse(again.getSlot(0).mayPlace(new ItemStack(Items.DIAMOND)), "Without the Major it's take-out only");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Rainbow

	@GameTest
	public void rainbowSavesEveryAmmo(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, CombatIds.CROSSBOW_INFINITY, MiscIds.RAINBOW);
		ItemStack bow = new ItemStack(Items.BOW);
		ItemStack spectral = new ItemStack(Items.SPECTRAL_ARROW, 3);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		player.setItemInHand(InteractionHand.OFF_HAND, spectral); // (Inventory.add would move the stack and empty ours)

		ItemStack shot = ProjectileWeaponItemInvoker.bloomlevelup$useAmmo(bow, spectral, player, false);
		helper.assertFalse(shot.isEmpty(), "Fired a round");
		helper.assertValueEqual(3, spectral.getCount(), "Rainbow: spectral arrows from a bow aren't used up");

		ServerPlayer plain = player(helper);
		grant(plain, CombatIds.CROSSBOW_INFINITY);
		ItemStack tipped = new ItemStack(Items.TIPPED_ARROW, 2);
		ProjectileWeaponItemInvoker.bloomlevelup$useAmmo(new ItemStack(Items.BOW), tipped, plain, false);
		helper.assertValueEqual(1, tipped.getCount(), "Without Rainbow it's normal ammo use");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Become My Fuel

	@GameTest
	public void becomeMyFuelImprintsAndApplies(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.BECOME_MY_FUEL);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.STRONG_SLOWNESS);
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		player.setItemInHand(InteractionHand.OFF_HAND, potion);

		player.getOffhandItem().finishUsingItem(helper.getLevel(), player);

		FuelImprint fuel = player.getMainHandItem().get(BloomComponents.FUEL);
		helper.assertTrue(fuel != null, "The sword carries the potion's effect");
		helper.assertTrue(fuel.effect().value() == MobEffects.SLOWNESS.value(), "It's Slowness");
		helper.assertValueEqual(3, fuel.amplifier(), "At the potion's level (Slowness IV)");
		helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "The drinker still suffers the potion");

		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 2.0F);
		MobEffectInstance applied = zombie.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(applied != null, "Hits apply the fuel");
		helper.assertValueEqual(3, applied.getAmplifier(), "At the imprinted level");
		helper.assertValueEqual(200, applied.getDuration(), "For 10 seconds");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Overenchant

	@GameTest
	public void overenchantPassesTheCapAndTooExpensive(GameTestHelper helper) {
		Holder<Enchantment> sharpness = enchantment(helper, Enchantments.SHARPNESS);

		for (boolean overenchant : new boolean[] {false, true}) {
			ServerPlayer player = player(helper);
			grant(player, overenchant ? new String[] {MiscIds.OVERENCHANT} : new String[0]);

			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			sword.enchant(sharpness, 5);
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(sharpness, 5));

			AnvilMenu anvil = new AnvilMenu(3, player.getInventory());
			anvil.getSlot(0).set(sword);
			anvil.getSlot(1).set(book);
			anvil.createResult();
			ItemStack result = anvil.getSlot(2).getItem();
			helper.assertValueEqual(overenchant ? 6 : 5, EnchantmentHelper.getItemEnchantmentLevel(sharpness, result),
					overenchant ? "Overenchant: V + V = VI" : "Vanilla caps Sharpness at V");

			ItemStack worn = new ItemStack(Items.DIAMOND_SWORD);
			worn.enchant(sharpness, 4);
			worn.set(DataComponents.REPAIR_COST, 60);
			ItemStack book4 = new ItemStack(Items.ENCHANTED_BOOK);
			EnchantmentHelper.updateEnchantments(book4, mutable -> mutable.set(sharpness, 4));
			AnvilMenu expensive = new AnvilMenu(4, player.getInventory());
			expensive.getSlot(0).set(worn);
			expensive.getSlot(1).set(book4);
			expensive.createResult();
			helper.assertValueEqual(!overenchant, expensive.getSlot(2).getItem().isEmpty(),
					overenchant ? "No Too Expensive with Overenchant" : "Too Expensive in vanilla");
		}

		helper.succeed();
	}

	// ------------------------------------------------------------------ Ascension

	@GameTest
	public void ascensionFliesAndIsGroundedByDamage(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.ASCENSION);
		helper.assertTrue(player.getAbilities().mayfly, "Ascension grants flight");

		player.getAbilities().flying = true;
		ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(player, player.damageSources().generic(), 2.0F, 2.0F, false);
		helper.assertFalse(player.getAbilities().mayfly, "Damage while flying takes flight away");
		helper.assertFalse(player.getAbilities().flying, "...and drops you");
		helper.assertTrue(MiscSkill.grounded(player), "Grounded until landing");

		player.setOnGround(true);
		MiscSkill.checkLanding(player);
		helper.assertTrue(player.getAbilities().mayfly, "Landing gives flight back");

		helper.assertTrue(BloomServices.progression().toggleMajor(BloomAttachments.profile(player), MiscIds.ASCENSION).ok(), "Toggle off");
		MiscSkill.refresh(player);
		helper.assertFalse(player.getAbilities().mayfly, "Switching Ascension off takes flight away");
		helper.succeed();
	}
}
