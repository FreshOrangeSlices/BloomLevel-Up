package com.freshorangeslices.bloomlevelup.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.level.block.Blocks;

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
import com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding;

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
		// A short Haste on top of the long one is stretched but ignored by vanilla: it must not mark Haste.
		player.addEffect(new MobEffectInstance(MobEffects.HASTE, 200, 0));

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

		// Shift-clicking matching items in must not top up the stack either (vanilla's merge skips mayPlace).
		player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 5));
		again.quickMoveStack(player, HoardersPocket.SIZE); // menu slot 9 = inventory slot 9
		helper.assertValueEqual(12, again.getSlot(3).getItem().getCount(), "Take-out only: no shift-click merge");
		helper.assertValueEqual(5, player.getInventory().getItem(9).getCount(), "The diamonds stay in the inventory");
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

		// Rainbow builds on Crossbow Infinity: switching that off switches Rainbow's free ammo off too.
		helper.assertTrue(BloomServices.progression().toggleMajor(BloomAttachments.profile(player), CombatIds.CROSSBOW_INFINITY).ok(), "Toggle");
		ItemStack more = new ItemStack(Items.SPECTRAL_ARROW, 3);
		ProjectileWeaponItemInvoker.bloomlevelup$useAmmo(bow, more, player, false);
		helper.assertValueEqual(2, more.getCount(), "No Rainbow without Crossbow Infinity on");

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
		helper.assertValueEqual(100, applied.getDuration(), "For 5 seconds");
		helper.succeed();
	}

	@GameTest
	public void becomeMyFuelWorksThroughArrowsAndTridents(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ServerLevel level = helper.getLevel();
		FuelImprint weakness = new FuelImprint(MobEffects.WEAKNESS, 1);

		ItemStack bow = new ItemStack(Items.BOW);
		bow.set(BloomComponents.FUEL, weakness);
		var arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(level, player, new ItemStack(Items.ARROW), bow);
		LivingEntity shot = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		shot.hurtServer(level, player.damageSources().arrow(arrow, player), 2.0F);
		helper.assertTrue(shot.hasEffect(MobEffects.WEAKNESS), "A fuelled bow's arrow applies the fuel");

		ItemStack trident = new ItemStack(Items.TRIDENT);
		trident.set(BloomComponents.FUEL, weakness);
		var thrown = new net.minecraft.world.entity.projectile.arrow.ThrownTrident(level, player, trident);
		LivingEntity speared = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 2, 3));
		speared.hurtServer(level, player.damageSources().trident(thrown, player), 2.0F);
		helper.assertTrue(speared.hasEffect(MobEffects.WEAKNESS), "A thrown fuelled trident applies the fuel");
		helper.assertValueEqual(1, speared.getEffect(MobEffects.WEAKNESS).getAmplifier(), "At the imprinted level");
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
		BlockPos above = helper.absolutePos(new BlockPos(2, 4, 2));
		player.setPos(above.getX() + 0.5, above.getY(), above.getZ() + 0.5); // in the air
		grant(player, MiscIds.ASCENSION);
		helper.assertTrue(player.getAbilities().mayfly, "Ascension grants flight");

		player.getAbilities().flying = true;
		ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(player, player.damageSources().generic(), 2.0F, 2.0F, false);
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
		helper.assertTrue(player.causeFallDamage(10.0, 1.0F, player.damageSources().fall()), "Allowed to fly but not flying: a fall hurts");
		player.getAbilities().flying = true;
		helper.assertFalse(player.causeFallDamage(10.0, 1.0F, player.damageSources().fall()), "Flying: no fall damage");
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

	@GameTest
	public void stackOverflowHoldsThreeHundred(GameTestHelper helper) {
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

		// Saved and loaded intact, as an item and inside container contents (the pocket's format).
		var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
		ItemStack big = new ItemStack(Items.DIRT, 300);
		var saved = ItemStack.CODEC.encodeStart(ops, big).getOrThrow();
		helper.assertValueEqual(300, ItemStack.CODEC.parse(ops, saved).getOrThrow().getCount(), "A 300 stack survives saving");
		var contents = net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(big));
		var savedContents = net.minecraft.world.item.component.ItemContainerContents.CODEC.encodeStart(ops, contents).getOrThrow();
		NonNullList<ItemStack> loaded = NonNullList.withSize(1, ItemStack.EMPTY);
		net.minecraft.world.item.component.ItemContainerContents.CODEC.parse(ops, savedContents).getOrThrow().copyInto(loaded);
		helper.assertValueEqual(300, loaded.get(0).getCount(), "...and inside container contents");

		// Putting items back into a slot that already holds more than the item's normal max must finish.
		player.getInventory().placeItemBackInInventory(new ItemStack(Items.ENDER_PEARL, 30), net.minecraft.util.Prediction.SERVER_ONLY);
		helper.assertValueEqual(280, player.getInventory().getItem(slot).getCount(), "Put-back tops up the big stack (and returns)");
		player.getInventory().removeItem(slot, 30);

		// The pocket's client-side screen uses the same limit.
		var clientPocket = new net.minecraft.world.inventory.ChestMenu(HoardersPocket.MENU, 9, player.getInventory(),
				new HoardersPocket.PocketContainer(player, null), 1);
		helper.assertValueEqual(300, clientPocket.getSlot(0).getMaxStackSize(new ItemStack(Items.DIRT)), "Pocket slots take 300 on both sides");

		// Dropping a big stack makes normal-size item entities (so hoppers and mobs can't cut it down).
		player.drop(new ItemStack(Items.COBBLESTONE, 300), false, net.minecraft.util.Prediction.SERVER_ONLY);
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

	private static ItemStack book(GameTestHelper helper, Object... enchantmentsAndLevels) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);

		for (int i = 0; i < enchantmentsAndLevels.length; i += 2) {
			@SuppressWarnings("unchecked")
			net.minecraft.resources.ResourceKey<Enchantment> key = (net.minecraft.resources.ResourceKey<Enchantment>) enchantmentsAndLevels[i];
			int level = (Integer) enchantmentsAndLevels[i + 1];
			Holder<Enchantment> holder = enchantment(helper, key);
			EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(holder, level));
		}

		return book;
	}

	private static void embed(ServerPlayer player, ItemStack... runes) {
		NonNullList<ItemStack> slots = NonNullList.withSize(RuneEmbedding.SIZE, ItemStack.EMPTY);

		for (int i = 0; i < runes.length; i++) {
			slots.set(i, runes[i]);
		}

		RuneEmbedding.store(player, slots);
		RuneEmbedding.refresh(player);
	}

	@GameTest
	public void runeSlotsAcceptOnlyValidBooks(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.RUNE_EMBEDDING);
		RuneEmbedding.RuneMenu menu = new RuneEmbedding.RuneMenu(1, player.getInventory(), player, true);

		helper.assertTrue(menu.getSlot(0).mayPlace(book(helper, Enchantments.PROTECTION, 4)), "Protection in a body slot");
		helper.assertFalse(menu.getSlot(0).mayPlace(book(helper, Enchantments.SHARPNESS, 5)), "No Sharpness in a body slot");
		helper.assertTrue(menu.getSlot(4).mayPlace(book(helper, Enchantments.SHARPNESS, 5)), "Sharpness in the bare-hand slot");
		helper.assertFalse(menu.getSlot(4).mayPlace(book(helper, Enchantments.PROTECTION, 4)), "No Protection in the bare-hand slot");
		helper.assertFalse(menu.getSlot(0).mayPlace(book(helper, Enchantments.BINDING_CURSE, 1)), "No curses");
		helper.assertFalse(menu.getSlot(0).mayPlace(book(helper, Enchantments.MENDING, 1)), "No Mending (nothing to repair)");
		helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.DIAMOND)), "Only enchanted books");

		menu.getSlot(0).set(book(helper, Enchantments.PROTECTION, 4));
		helper.assertFalse(menu.getSlot(1).mayPlace(book(helper, Enchantments.PROTECTION, 3)), "No duplicate enchantments");
		helper.assertTrue(menu.getSlot(1).mayPlace(book(helper, Enchantments.FEATHER_FALLING, 4)), "A different one is fine");
		menu.removed(player);
		helper.assertTrue(!RuneEmbedding.runes(player).get(0).isEmpty(), "Runes are saved on the player");
		helper.succeed();
	}

	@GameTest
	public void bodyRunesProtectOnlyWithoutArmor(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.RUNE_EMBEDDING);
		ServerLevel level = helper.getLevel();
		embed(player, book(helper, Enchantments.PROTECTION, 4), book(helper, Enchantments.RESPIRATION, 3));

		helper.assertTrue(EnchantmentHelper.getDamageProtection(level, player, player.damageSources().generic()) > 0.0F, "Protection rune protects");
		near(helper, 3.0, player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.OXYGEN_BONUS), "Respiration III rune");

		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		RuneEmbedding.refresh(player);
		near(helper, 0.0, EnchantmentHelper.getDamageProtection(level, player, player.damageSources().generic()), "Wearing armor turns body runes off");
		near(helper, 0.0, player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.OXYGEN_BONUS), "...attributes too");
		helper.succeed();
	}

	@GameTest
	public void bareHandRuneEnchantsFists(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		grant(player, MiscIds.RUNE_EMBEDDING);
		ServerLevel level = helper.getLevel();
		ItemStack fist = book(helper, Enchantments.SHARPNESS, 5, Enchantments.LOOTING, 3);
		embed(player, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, fist);
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));

		float bare = EnchantmentHelper.modifyDamage(level, player.getWeaponItem(), zombie, player.damageSources().playerAttack(player), 5.0F);
		near(helper, 8.0, bare, "Sharpness V on bare hands: +3");
		helper.assertValueEqual(3, EnchantmentHelper.getEnchantmentLevel(enchantment(helper, Enchantments.LOOTING), player), "Looting III bare-handed");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		float holding = EnchantmentHelper.modifyDamage(level, player.getWeaponItem(), zombie, player.damageSources().playerAttack(player), 5.0F);
		near(helper, 5.0, holding, "Holding anything turns the bare-hand rune off");
		helper.succeed();
	}
}
