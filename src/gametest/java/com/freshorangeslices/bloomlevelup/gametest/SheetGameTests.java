package com.freshorangeslices.bloomlevelup.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.sheet.StatSheets;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;

/**
 * The stat sheet against the real thing: the numbers it shows have to be what a hit actually does. (Sheet values
 * are shown with two decimals, hence the looser tolerance.)
 */
public class SheetGameTests {
	private static final double EPS = 0.011;

	/** A fake player that can be hurt (Fabric's are invulnerable). */
	private static final class HurtablePlayer extends FakePlayer {
		HurtablePlayer(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "bloom-sheet"));
		}

		@Override
		public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
			return false;
		}
	}

	private static ServerPlayer player(GameTestHelper helper, ItemStack mainHand) {
		ServerPlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bloom-sheet"));
		place(helper, player, mainHand);
		return player;
	}

	private static void place(GameTestHelper helper, ServerPlayer player, ItemStack mainHand) {
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
	}

	private static void grant(ServerPlayer player, String... majors) {
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);

		for (String category : List.of(SkillXp.UTILITY, SkillXp.COMBAT, SkillXp.ARMOR, SkillXp.MISC)) {
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
		ActionResult result = BloomServices.progression().allocate(BloomAttachments.profile(player), stat, ProgressionService.Pool.NATURAL, amount);

		if (!result.ok()) {
			throw new IllegalStateException("Couldn't allocate " + stat + ": " + result.outcome());
		}
	}

	private static SheetPayloads.View sheet(ServerPlayer player, String category, int slot, int target, String... toggles) {
		return StatSheets.build(player, new SheetPayloads.Request(1, category, slot, target, List.of(toggles)));
	}

	/** The number on a summary line, by its lang key ("3.2  (-68%)" reads as 3.2). */
	private static double value(SheetPayloads.View view, String key) {
		for (SheetPayloads.Row row : view.summary()) {
			if (row.label().getContents() instanceof TranslatableContents contents && contents.getKey().equals("bloom.sheet." + key)) {
				String text = row.value().getString().trim();
				int space = text.indexOf(' ');
				return Double.parseDouble(space > 0 ? text.substring(0, space) : text);
			}
		}

		throw new IllegalStateException("The sheet has no " + key + " line: " + view.summary());
	}

	private static void near(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < EPS, what + ": expected " + expected + " but the sheet says " + actual);
	}

	@GameTest
	public void combatSheetMatchesTheHitPipeline(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<Enchantment> sharpness = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.enchant(sharpness, 3);
		// Hold an iron sword and ask about the diamond one in slot 5: the sheet swaps it in without touching the player.
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		player.getInventory().setItem(5, sword);
		grant(player, CombatIds.FIRST_STRIKE);
		points(player, CombatIds.POWER, 50);
		points(player, CombatIds.CRITICAL, 40);
		StatEffects.refresh(player);

		SheetPayloads.View plain = sheet(player, SkillXp.COMBAT, 5, 0, "first_strike=max");
		SheetPayloads.View armored = sheet(player, SkillXp.COMBAT, 5, 2, "first_strike=max");
		helper.assertTrue(player.getMainHandItem().is(Items.IRON_SWORD), "The sheet doesn't change what's in hand");
		helper.assertTrue(plain.toggles().stream().anyMatch(toggle -> toggle.id().equals("first_strike") && toggle.mode() == SheetPayloads.Toggle.MAX),
				"First Strike is offered, and on");
		helper.assertTrue(plain.gauge().is(ItemTags.SWORDS), "The picture is a sword");

		// The same swing for real. Fake players never tick, so the sword's own damage is added by hand.
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		StatEffects.refresh(player);
		DamageSource source = player.damageSources().playerAttack(player);
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		float attack = (float) sword.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE), EquipmentSlot.MAINHAND);
		float enchant = EnchantmentHelper.modifyDamage(level, sword, zombie, source, attack) - attack;
		helper.assertTrue(Math.abs(attack - 10.0F) < 1.0E-4F, "1 base + 6 diamond sword + 3 Power = 10, was " + attack);

		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		double hit = CombatSkill.modifyIncoming(zombie, source, attack + enchant);
		CombatSkill.endSwingForTests();
		near(helper, hit, value(plain, "combat.hit"), "A full-strength hit (Sharpness III, First Strike)");

		LivingEntity fresh = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 2, 1));
		CombatSkill.recordSwingForTests(player, fresh, 1.0F, true, false);
		double crit = CombatSkill.modifyIncoming(fresh, source, attack * 1.5F + enchant);
		CombatSkill.endSwingForTests();
		near(helper, crit, value(plain, "combat.crit"), "A jump crit (40 Critical)");

		near(helper, CombatRules.getDamageAfterAbsorb(zombie, (float) hit, source, 20.0F, 8.0F), value(armored, "combat.hit"),
				"The same hit on full diamond armor");
		helper.succeed();
	}

	@GameTest
	public void liveSwitchesFollowTheFight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		grant(player, CombatIds.PREDATOR);
		StatEffects.refresh(player);
		DamageSource source = player.damageSources().playerAttack(player);
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		float attack = (float) new ItemStack(Items.IRON_SWORD).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_DAMAGE, player.getAttributeValue(Attributes.ATTACK_DAMAGE), EquipmentSlot.MAINHAND);

		double fresh = value(sheet(player, SkillXp.COMBAT, -1, 0), "combat.hit");
		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		CombatSkill.modifyIncoming(zombie, source, attack); // Predator: 1 stack on this zombie now
		CombatSkill.endSwingForTests();

		SheetPayloads.View live = sheet(player, SkillXp.COMBAT, -1, 0);
		SheetPayloads.View max = sheet(player, SkillXp.COMBAT, -1, 0, "predator=max");
		SheetPayloads.View off = sheet(player, SkillXp.COMBAT, -1, 0, "predator=off");
		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		double second = CombatSkill.modifyIncoming(zombie, source, attack);
		CombatSkill.endSwingForTests();

		near(helper, second, value(live, "combat.hit"), "Live: the next hit on the same zombie, with its 1 Predator stack");
		near(helper, fresh, value(off, "combat.hit"), "Off: no stacks");
		near(helper, fresh * 1.5, value(max, "combat.hit"), "Max: 5 stacks, +50%");
		helper.assertTrue(live.toggles().stream().anyMatch(toggle -> toggle.id().equals("predator") && toggle.mode() == SheetPayloads.Toggle.LIVE
				&& toggle.live().getString().contains("1")), "The Predator switch says Live (1 stacks)");
		helper.succeed();
	}

	@GameTest
	public void armorSheetMatchesARealHit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true); // mob hits scale with difficulty; Normal leaves them alone
		ServerPlayer player = new HurtablePlayer(level);
		place(helper, player, ItemStack.EMPTY);
		grant(player, ArmorIds.IRON_SKIN);
		points(player, ArmorIds.DEFENSE, 100);
		StatEffects.refresh(player);
		helper.assertTrue(player.getArmorValue() == 10, "100 Defense = 10 armor, was " + player.getArmorValue());

		SheetPayloads.View view = sheet(player, SkillXp.ARMOR, -1, 1); // a hit of 10
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
		float before = player.getHealth();
		helper.assertTrue(player.hurtServer(level, player.damageSources().mobAttack(zombie), 10.0F), "The hit lands");
		near(helper, before - player.getHealth(), value(view, "armor.kind.mob"), "Health lost to a mob's hit of 10 (10 armor, Iron Skin)");
		helper.succeed();
	}

	@GameTest
	public void everyTabHasASheet(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_PICKAXE));
		grant(player);

		for (String category : List.of(SkillXp.COMBAT, SkillXp.ARMOR, SkillXp.UTILITY, SkillXp.MISC)) {
			SheetPayloads.View view = sheet(player, category, -1, -1);
			helper.assertFalse(view.summary().isEmpty(), category + " has a summary");
			helper.assertFalse(view.details().isEmpty(), category + " has a breakdown");
			helper.assertFalse(view.summary().stream().anyMatch(row -> row.label().getContents() instanceof TranslatableContents contents
					&& contents.getKey().equals("bloom.sheet.error")), category + " worked out without an error");
		}

		// Bare hands, a bow and a trident each get their own sections.
		player.getInventory().setItem(6, new ItemStack(Items.BOW));
		player.getInventory().setItem(7, new ItemStack(Items.TRIDENT));
		helper.assertFalse(sheet(player, SkillXp.COMBAT, -2, 0).summary().isEmpty(), "Bare hands");
		helper.assertTrue(sheet(player, SkillXp.COMBAT, 6, 0).summary().stream().anyMatch(row -> row.label().getContents() instanceof TranslatableContents c
				&& c.getKey().equals("bloom.sheet.combat.arrow_bow")), "A bow shows its arrows");
		helper.assertTrue(sheet(player, SkillXp.COMBAT, 7, 0).summary().stream().anyMatch(row -> row.label().getContents() instanceof TranslatableContents c
				&& c.getKey().equals("bloom.sheet.combat.thrown")), "A trident shows its throw");
		helper.succeed();
	}
}
