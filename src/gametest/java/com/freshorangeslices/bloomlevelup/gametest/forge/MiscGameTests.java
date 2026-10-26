package com.freshorangeslices.bloomlevelup.gametest.forge;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.item.FuelImprint;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.StoredItems;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.ConsumeContext;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.HoardersPocket;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscIds;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding;

/**
 * Misc on a real server with fake players. Eating and drinking go through {@link #consume}, which brackets the food
 * the way finishing it does in game (the completeUsingItem hook), since fake players never tick.
 */
@GameTestHolder(BloomLevelUp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiscGameTests {
	private static final double EPS = 1e-3;

	private MiscGameTests() {
	}

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = new TestPlayers.Vulnerable(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bloom-misc"));
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

	/** Eats or drinks a stack the way finishing it does in game. */
	private static void consume(GameTestHelper helper, ServerPlayer player, ItemStack stack, boolean offhand) {
		ConsumeContext.begin(player, stack, offhand);

		try {
			stack.finishUsingItem(helper.getLevel(), player);
		} finally {
			ConsumeContext.end(player);
		}
	}

	private static ItemStack book(Object... enchantmentsAndLevels) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);

		for (int i = 0; i < enchantmentsAndLevels.length; i += 2) {
			EnchantedBookItem.addEnchantment(book, new EnchantmentInstance((Enchantment) enchantmentsAndLevels[i], (Integer) enchantmentsAndLevels[i + 1]));
		}

		return book;
	}

	// ------------------------------------------------------------------ XP and stats

	@GameTest(template = "empty")
	public static void orbsGiveMiscXpAndWisdomMultipliesVanillaXp(GameTestHelper helper) {
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

	@GameTest(template = "empty")
	public static void enduranceSlowsHunger(GameTestHelper helper) {
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

	@GameTest(template = "empty")
	public static void ironStomachMakesPufferfishSafeAndFilling(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.IRON_STOMACH);
		player.getFoodData().setFoodLevel(0);
		player.getFoodData().setSaturation(0.0F);

		consume(helper, player, new ItemStack(Items.PUFFERFISH), false);

		helper.assertFalse(player.hasEffect(MobEffects.POISON), "No poison");
		helper.assertFalse(player.hasEffect(MobEffects.HUNGER), "No hunger");
		helper.assertFalse(player.hasEffect(MobEffects.CONFUSION), "No nausea");
		helper.assertValueEqual(6, player.getFoodData().getFoodLevel(), "Fills like cooked chicken");
		near(helper, 6.0, player.getFoodData().getSaturationLevel(), "Saturation floor (capped by the food level)");

		// Without the Major the pufferfish is as nasty as ever.
		ServerPlayer plain = player(helper);
		consume(helper, plain, new ItemStack(Items.PUFFERFISH), false);
		helper.assertTrue(plain.hasEffect(MobEffects.POISON), "Poisoned without Iron Stomach");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void selectiveCleansingKeepsBuffs(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.SELECTIVE_CLEANSING);

		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 0));
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 1200, 0));
		consume(helper, player, new ItemStack(Items.MILK_BUCKET), false);
		helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "Milk keeps Speed");
		helper.assertFalse(player.hasEffect(MobEffects.POISON), "Milk clears Poison");

		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0));
		consume(helper, player, new ItemStack(Items.CARROT), false);
		helper.assertFalse(player.hasEffect(MobEffects.WEAKNESS), "A carrot clears Weakness");
		helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "...and keeps Speed");

		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 0));
		consume(helper, player, new ItemStack(Items.APPLE), false);
		helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "Apples don't cleanse");

		// Without the Major, milk clears everything.
		ServerPlayer plain = player(helper);
		plain.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 0));
		consume(helper, plain, new ItemStack(Items.MILK_BUCKET), false);
		helper.assertFalse(plain.hasEffect(MobEffects.MOVEMENT_SPEED), "Plain milk clears Speed too");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void secondServingEatsWhenFull(GameTestHelper helper) {
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

	@GameTest(template = "empty")
	public static void waterToWineStretchesAndCutsOnlyItsOwn(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.WATER_TO_WINE);

		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20000, 0));
		player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
		// A short Haste on top of the long one is stretched but ignored by vanilla: it must not mark Haste.
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 0));

		helper.assertValueEqual(12000, player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration(), "30 s of Speed becomes 10 minutes");
		helper.assertValueEqual(12000, player.getEffect(MobEffects.WEAKNESS).getDuration(), "Debuffs too");
		helper.assertValueEqual(20000, player.getEffect(MobEffects.DIG_SPEED).getDuration(), "Long effects are untouched");
		helper.assertTrue(player.getEffect(MobEffects.NIGHT_VISION).isInfiniteDuration(), "Infinite stays infinite");

		ProgressionService service = BloomServices.progression();
		helper.assertTrue(service.toggleMajor(BloomAttachments.profile(player), MiscIds.WATER_TO_WINE).ok(), "Toggle off");
		MiscSkill.refresh(player);

		helper.assertValueEqual(2400, player.getEffect(MobEffects.MOVEMENT_SPEED).getDuration(), "Stretched effects drop to 2 minutes");
		helper.assertValueEqual(2400, player.getEffect(MobEffects.WEAKNESS).getDuration(), "Stretched debuffs too");
		helper.assertValueEqual(20000, player.getEffect(MobEffects.DIG_SPEED).getDuration(), "Naturally long effects keep their time");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void overdoseClimbsTwoLevels(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.OVERDOSE);

		int[] expected = {0, 1, 2, 2};

		for (int amplifier : expected) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
			helper.assertValueEqual(amplifier, player.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier(), "Speed I re-applied climbs to III and stops");
		}

		// Beacon-style (ambient) effects don't climb.
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 300, 0, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 300, 0, true, true));
		helper.assertValueEqual(0, player.getEffect(MobEffects.DIG_SPEED).getAmplifier(), "Ambient effects are left alone");
		helper.succeed();
	}

	// ------------------------------------------------------------------ XP Insurance

	@GameTest(template = "empty")
	public static void xpInsuranceReturnsHalfOfTheLoss(GameTestHelper helper) {
		ServerPlayer dead = player(helper);
		ServerPlayer respawned = player(helper);
		grant(dead, MiscIds.XP_INSURANCE);
		dead.setExperienceLevels(30);
		dead.experienceProgress = 0.0F;

		// What respawning after death does: the profile is copied, then XP Insurance hands back its share.
		MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, dead, true));

		// Level 30 = 1395 XP: 100 drops as orbs, 1295 is lost, half of it (647) comes back.
		helper.assertValueEqual(647, respawned.totalExperience, "Half the lost XP comes back");
		helper.assertTrue(BloomAttachments.profile(respawned).owns(MiscIds.XP_INSURANCE), "The profile came along");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Hoarder's Pocket

	@GameTest(template = "empty")
	public static void hoardersPocketStoresAndBlocksStorage(GameTestHelper helper) {
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

		// Shift-clicking matching items in must not top up the stack either (vanilla's merge skips mayPlace).
		player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 5));
		again.quickMoveStack(player, HoardersPocket.SIZE); // menu slot 9 = inventory slot 9
		helper.assertValueEqual(12, again.getSlot(3).getItem().getCount(), "Take-out only: no shift-click merge");
		helper.assertValueEqual(5, player.getInventory().getItem(9).getCount(), "The diamonds stay in the inventory");

		// It's kept through death (the profile capability carries it to the new player).
		ServerPlayer respawned = player(helper);
		MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned, player, true));
		helper.assertValueEqual(12, HoardersPocket.items(respawned).get(3).getCount(), "The pocket survives death");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Rainbow

	@GameTest(template = "empty")
	public static void rainbowSavesEveryAmmo(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, CombatIds.CROSSBOW_INFINITY, MiscIds.RAINBOW);
		ItemStack bow = new ItemStack(Items.BOW);
		ItemStack spectral = new ItemStack(Items.SPECTRAL_ARROW, 3);

		// A bow doesn't use up arrows it treats as Infinity's (Forge asks the arrow item).
		helper.assertTrue(((ArrowItem) spectral.getItem()).isInfinite(spectral, bow, player), "Rainbow: spectral arrows from a bow aren't used up");
		helper.assertTrue(CombatSkill.freeAmmo(new ItemStack(Items.CROSSBOW), new ItemStack(Items.FIREWORK_ROCKET), player),
				"...nor fireworks from a crossbow");

		// Rainbow builds on Crossbow Infinity: switching that off switches Rainbow's free ammo off too.
		helper.assertTrue(BloomServices.progression().toggleMajor(BloomAttachments.profile(player), CombatIds.CROSSBOW_INFINITY).ok(), "Toggle");
		helper.assertFalse(((ArrowItem) spectral.getItem()).isInfinite(spectral, bow, player), "No Rainbow without Crossbow Infinity on");

		ServerPlayer plain = player(helper);
		grant(plain, CombatIds.CROSSBOW_INFINITY);
		ItemStack tipped = new ItemStack(Items.TIPPED_ARROW, 2);
		helper.assertFalse(((ArrowItem) tipped.getItem()).isInfinite(tipped, bow, plain), "Without Rainbow it's normal ammo use");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Become My Fuel

	@GameTest(template = "empty")
	public static void becomeMyFuelImprintsAndApplies(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.BECOME_MY_FUEL);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ItemStack potion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.STRONG_SLOWNESS);
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		player.setItemInHand(InteractionHand.OFF_HAND, potion);

		consume(helper, player, player.getOffhandItem(), true);

		FuelImprint fuel = FuelImprint.get(player.getMainHandItem());
		helper.assertTrue(fuel != null, "The sword carries the potion's effect");
		helper.assertTrue(fuel.effect() == MobEffects.MOVEMENT_SLOWDOWN, "It's Slowness");
		helper.assertValueEqual(3, fuel.amplifier(), "At the potion's level (Slowness IV)");
		helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The drinker still suffers the potion");

		LivingEntity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		zombie.hurt(player.damageSources().playerAttack(player), 2.0F);
		MobEffectInstance applied = zombie.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(applied != null, "Hits apply the fuel");
		helper.assertValueEqual(3, applied.getAmplifier(), "At the imprinted level");
		helper.assertValueEqual(100, applied.getDuration(), "For 5 seconds");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void becomeMyFuelWorksThroughArrowsAndTridents(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ServerLevel level = helper.getLevel();
		FuelImprint weakness = new FuelImprint(MobEffects.WEAKNESS, 1);

		ItemStack bow = new ItemStack(Items.BOW);
		FuelImprint.set(bow, weakness);
		Arrow arrow = new Arrow(level, player);
		CombatSkill.firedWithForTests(arrow, bow);
		LivingEntity shot = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		shot.hurt(player.damageSources().arrow(arrow, player), 2.0F);
		helper.assertTrue(shot.hasEffect(MobEffects.WEAKNESS), "A fuelled bow's arrow applies the fuel");

		ItemStack trident = new ItemStack(Items.TRIDENT);
		FuelImprint.set(trident, weakness);
		ThrownTrident thrown = new ThrownTrident(level, player, trident);
		LivingEntity speared = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
		speared.hurt(player.damageSources().trident(thrown, player), 2.0F);
		helper.assertTrue(speared.hasEffect(MobEffects.WEAKNESS), "A thrown fuelled trident applies the fuel");
		helper.assertValueEqual(1, speared.getEffect(MobEffects.WEAKNESS).getAmplifier(), "At the imprinted level");

		// The imprint is item data: it survives saving the item.
		ItemStack reloaded = ItemStack.of(trident.save(new CompoundTag()));
		helper.assertTrue(FuelImprint.get(reloaded) != null && FuelImprint.get(reloaded).effect() == MobEffects.WEAKNESS, "Saved with the item");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Overenchant

	@GameTest(template = "empty")
	public static void overenchantPassesTheCapAndTooExpensive(GameTestHelper helper) {
		for (boolean overenchant : new boolean[] {false, true}) {
			ServerPlayer player = player(helper);
			grant(player, overenchant ? new String[] {MiscIds.OVERENCHANT} : new String[0]);

			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			sword.enchant(Enchantments.SHARPNESS, 5);

			AnvilMenu anvil = new AnvilMenu(3, player.getInventory());
			anvil.getSlot(0).set(sword);
			anvil.getSlot(1).set(book(Enchantments.SHARPNESS, 5));
			anvil.createResult();
			ItemStack result = anvil.getSlot(2).getItem();
			helper.assertValueEqual(overenchant ? 6 : 5, EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, result),
					overenchant ? "Overenchant: V + V = VI" : "Vanilla caps Sharpness at V");

			ItemStack worn = new ItemStack(Items.DIAMOND_SWORD);
			worn.enchant(Enchantments.SHARPNESS, 4);
			worn.setRepairCost(60);
			AnvilMenu expensive = new AnvilMenu(4, player.getInventory());
			expensive.getSlot(0).set(worn);
			expensive.getSlot(1).set(book(Enchantments.SHARPNESS, 4));
			expensive.createResult();
			helper.assertValueEqual(!overenchant, expensive.getSlot(2).getItem().isEmpty(),
					overenchant ? "No Too Expensive with Overenchant" : "Too Expensive in vanilla");
		}

		helper.succeed();
	}

	// ------------------------------------------------------------------ Ascension

	@GameTest(template = "empty")
	public static void ascensionFliesAndIsGroundedByDamage(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		BlockPos above = helper.absolutePos(new BlockPos(2, 4, 2));
		player.setPos(above.getX() + 0.5, above.getY(), above.getZ() + 0.5); // in the air
		grant(player, MiscIds.ASCENSION);
		helper.assertTrue(player.getAbilities().mayfly, "Ascension grants flight");

		player.getAbilities().flying = true;
		player.hurt(player.damageSources().generic(), 2.0F);
		helper.assertFalse(player.getAbilities().mayfly, "Damage while flying takes flight away");
		helper.assertFalse(player.getAbilities().flying, "...and drops you");
		helper.assertTrue(MiscSkill.grounded(player), "Grounded until landing");

		MiscSkill.checkLanding(player);
		helper.assertTrue(MiscSkill.grounded(player), "Still in the air: still grounded");

		helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
		player.setPos(player.getX(), helper.absolutePos(new BlockPos(2, 1, 2)).getY(), player.getZ());
		MiscSkill.checkLanding(player);
		helper.assertTrue(player.getAbilities().mayfly, "Landing gives flight back");

		// Falls only stop hurting while actually flying.
		player.getAbilities().flying = false;
		player.invulnerableTime = 0;
		helper.assertTrue(player.causeFallDamage(10.0F, 1.0F, player.damageSources().fall()), "Allowed to fly but not flying: a fall hurts");
		player.getAbilities().flying = true;
		helper.assertFalse(player.causeFallDamage(10.0F, 1.0F, player.damageSources().fall()), "Flying: no fall damage");
		player.getAbilities().flying = false;

		helper.assertTrue(BloomServices.progression().toggleMajor(BloomAttachments.profile(player), MiscIds.ASCENSION).ok(), "Toggle off");
		MiscSkill.refresh(player);
		helper.assertFalse(player.getAbilities().mayfly, "Switching Ascension off takes flight away");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Stack Overflow

	private static int slotWith(ServerPlayer player, net.minecraft.world.item.Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(item)) {
				return i;
			}
		}

		return -1;
	}

	@GameTest(template = "empty")
	public static void stackOverflowHoldsThreeHundred(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.STACK_OVERFLOW);

		player.getInventory().add(new ItemStack(Items.ENDER_PEARL, 250));
		int slot = slotWith(player, Items.ENDER_PEARL);
		helper.assertValueEqual(250, player.getInventory().getItem(slot).getCount(), "250 ender pearls in one slot (normally 16)");
		helper.assertValueEqual(300, player.inventoryMenu.getSlot(9).getMaxStackSize(new ItemStack(Items.DIRT)), "Inventory slots take 300");
		helper.assertValueEqual(1, player.inventoryMenu.getSlot(5).getMaxStackSize(new ItemStack(Items.CARVED_PUMPKIN)), "Armor slots still hold 1");

		player.getInventory().add(new ItemStack(Items.IRON_SWORD));
		player.getInventory().add(new ItemStack(Items.IRON_SWORD));
		helper.assertValueEqual(1, player.getInventory().getItem(slotWith(player, Items.IRON_SWORD)).getCount(), "Swords stay single");

		// Saved and loaded intact, as an item and in the pocket's storage.
		ItemStack big = new ItemStack(Items.DIRT, 300);
		helper.assertValueEqual(300, ItemStack.of(big.save(new CompoundTag())).getCount(), "A 300 stack survives saving");
		NonNullList<ItemStack> stored = NonNullList.withSize(1, ItemStack.EMPTY);
		stored.set(0, big);
		StoredItems.write(player, "test", stored);
		helper.assertValueEqual(300, StoredItems.read(player, "test", 1).get(0).getCount(), "...and in the pocket's storage");
		StoredItems.write(player, "test", NonNullList.withSize(1, ItemStack.EMPTY));

		// ...and over the network (vanilla sends a stack's count as one byte).
		net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
		buf.writeItem(big);
		buf.writeItem(new ItemStack(Items.DIAMOND, 7));
		helper.assertValueEqual(300, buf.readItem().getCount(), "A 300 stack crosses the network whole");
		helper.assertValueEqual(7, buf.readItem().getCount(), "...and small stacks after it still read right");

		// Putting items back into a slot that already holds more than the item's normal max must finish.
		player.getInventory().placeItemBackInInventory(new ItemStack(Items.ENDER_PEARL, 30));
		helper.assertValueEqual(280, player.getInventory().getItem(slot).getCount(), "Put-back tops up the big stack (and returns)");
		player.getInventory().removeItem(slot, 30);

		// The pocket's client-side screen uses the same limit.
		var clientPocket = new net.minecraft.world.inventory.ChestMenu(HoardersPocket.MENU.get(), 9, player.getInventory(),
				new HoardersPocket.PocketContainer(player, null), 1);
		helper.assertValueEqual(300, clientPocket.getSlot(0).getMaxStackSize(new ItemStack(Items.DIRT)), "Pocket slots take 300 on both sides");

		// Dropping a big stack makes normal-size item entities (so hoppers and mobs can't cut it down).
		player.drop(new ItemStack(Items.COBBLESTONE, 300), false, false);
		var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(4.0),
				entity -> entity.getItem().is(Items.COBBLESTONE));
		int dropped = 0;

		for (var drop : drops) {
			helper.assertTrue(drop.getItem().getCount() <= 64, "No dropped stack above 64");
			dropped += drop.getItem().getCount();
		}

		helper.assertValueEqual(300, dropped, "All 300 dropped");
		drops.forEach(net.minecraft.world.entity.Entity::discard);

		// Switched off: big stacks split back to vanilla sizes.
		helper.assertTrue(BloomServices.progression().toggleMajor(BloomAttachments.profile(player), MiscIds.STACK_OVERFLOW).ok(), "Toggle off");
		MiscSkill.refresh(player);
		int pearls = 0;

		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);

			if (stack.is(Items.ENDER_PEARL)) {
				helper.assertTrue(stack.getCount() <= 16, "No pearl stack above 16 once it's off");
				pearls += stack.getCount();
			}
		}

		helper.assertValueEqual(250, pearls, "Nothing lost when splitting");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Rune Embedding

	private static void embed(ServerPlayer player, ItemStack... runes) {
		NonNullList<ItemStack> slots = NonNullList.withSize(RuneEmbedding.SIZE, ItemStack.EMPTY);

		for (int i = 0; i < runes.length; i++) {
			slots.set(i, runes[i]);
		}

		RuneEmbedding.store(player, slots);
	}

	@GameTest(template = "empty")
	public static void runeSlotsAcceptOnlyValidBooks(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.RUNE_EMBEDDING);
		RuneEmbedding.RuneMenu menu = new RuneEmbedding.RuneMenu(1, player.getInventory(), player, true);

		helper.assertTrue(menu.getSlot(0).mayPlace(book(Enchantments.ALL_DAMAGE_PROTECTION, 4)), "Protection in a body slot");
		helper.assertFalse(menu.getSlot(0).mayPlace(book(Enchantments.SHARPNESS, 5)), "No Sharpness in a body slot");
		helper.assertTrue(menu.getSlot(4).mayPlace(book(Enchantments.SHARPNESS, 5)), "Sharpness in the bare-hand slot");
		helper.assertFalse(menu.getSlot(4).mayPlace(book(Enchantments.ALL_DAMAGE_PROTECTION, 4)), "No Protection in the bare-hand slot");
		helper.assertFalse(menu.getSlot(0).mayPlace(book(Enchantments.BINDING_CURSE, 1)), "No curses");
		helper.assertFalse(menu.getSlot(0).mayPlace(book(Enchantments.MENDING, 1)), "No Mending (nothing to repair)");
		helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.DIAMOND)), "Only enchanted books");

		menu.getSlot(0).set(book(Enchantments.ALL_DAMAGE_PROTECTION, 4));
		helper.assertFalse(menu.getSlot(1).mayPlace(book(Enchantments.ALL_DAMAGE_PROTECTION, 3)), "No duplicate enchantments");
		helper.assertTrue(menu.getSlot(1).mayPlace(book(Enchantments.FALL_PROTECTION, 4)), "A different one is fine");
		menu.removed(player);
		helper.assertTrue(!RuneEmbedding.runes(player).get(0).isEmpty(), "Runes are saved on the player");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void bodyRunesProtectOnlyWithoutArmor(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.RUNE_EMBEDDING);
		embed(player, book(Enchantments.ALL_DAMAGE_PROTECTION, 4), book(Enchantments.RESPIRATION, 3));

		helper.assertTrue(RuneEmbedding.protection(player, player.damageSources().generic()) > 0, "Protection rune protects");
		helper.assertValueEqual(3, EnchantmentHelper.getRespiration(player), "Respiration III rune");

		// A real hit is reduced by it too (vanilla's own protection math, with the rune added).
		player.setHealth(20.0F);
		player.invulnerableTime = 0;
		player.hurt(player.damageSources().generic(), 10.0F);
		helper.assertTrue(player.getHealth() > 10.5F, "Protection IV rune softened a 10-damage hit, health " + player.getHealth());

		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		helper.assertValueEqual(0, RuneEmbedding.protection(player, player.damageSources().generic()), "Wearing armor turns body runes off");
		helper.assertValueEqual(0, EnchantmentHelper.getRespiration(player), "...the levels too");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void bareHandRuneEnchantsFists(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.RUNE_EMBEDDING);
		ItemStack fist = book(Enchantments.SHARPNESS, 5, Enchantments.MOB_LOOTING, 3);
		embed(player, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, fist);

		float bare = EnchantmentHelper.getDamageBonus(RuneEmbedding.weaponItem(player, player.getMainHandItem()), MobType.UNDEFINED);
		near(helper, 3.0, bare, "Sharpness V on bare hands: +3");
		helper.assertValueEqual(3, EnchantmentHelper.getEnchantmentLevel(Enchantments.MOB_LOOTING, player), "Looting III bare-handed");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		float holding = EnchantmentHelper.getDamageBonus(RuneEmbedding.weaponItem(player, player.getMainHandItem()), MobType.UNDEFINED);
		near(helper, 0.0, holding, "Holding anything turns the bare-hand rune off");
		helper.succeed();
	}
}
