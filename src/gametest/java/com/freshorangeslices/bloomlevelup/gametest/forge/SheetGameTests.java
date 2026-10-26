package com.freshorangeslices.bloomlevelup.gametest.forge;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.sheet.StatSheets;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;

/**
 * The stat sheet against the real thing (Forge 1.20.1 build): the numbers it shows have to be what a hit actually
 * does. (Sheet values are shown with two decimals, hence the looser tolerance.)
 */
@GameTestHolder(BloomLevelUp.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SheetGameTests {
	private static final double EPS = 0.011;
	private static final List<String> ALL = List.of(SkillXp.UTILITY, SkillXp.COMBAT, SkillXp.ARMOR, SkillXp.MISC);

	private static ServerPlayer player(GameTestHelper helper, ItemStack mainHand) {
		return TestPlayers.player(helper, mainHand);
	}

	private static void grant(ServerPlayer player, String... majors) {
		TestPlayers.grant(player, ALL, majors);
	}

	private static SheetPayloads.View sheet(ServerPlayer player, String category, int slot, int target, String... toggles) {
		return StatSheets.build(player, new SheetPayloads.Request(1, category, slot, target, List.of(toggles)));
	}

	/** The number on a summary line, by its lang key ("3.2  (-68%)" reads as 3.2). */
	private static double value(SheetPayloads.View view, String key) {
		for (SheetPayloads.Row row : view.summary()) {
			if (isKey(row, "bloom.sheet." + key)) {
				String text = row.value().getString().trim();
				int space = text.indexOf(' ');
				return Double.parseDouble(space > 0 ? text.substring(0, space) : text);
			}
		}

		throw new IllegalStateException("The sheet has no " + key + " line: " + view.summary());
	}

	/** The difference a compared summary line carries: "7  (+1)" reads as +1. */
	private static double delta(SheetPayloads.View view, String key) {
		for (SheetPayloads.Row row : view.summary()) {
			if (isKey(row, "bloom.sheet." + key)) {
				String text = row.value().getString();
				int open = text.lastIndexOf('(');
				int close = text.lastIndexOf(')');

				if (open < 0 || close < open) {
					throw new IllegalStateException("The " + key + " line shows no difference: " + text);
				}

				return Double.parseDouble(text.substring(open + 1, close).replace("%", "").replace(" s", "").trim());
			}
		}

		throw new IllegalStateException("The sheet has no " + key + " line: " + view.summary());
	}

	private static boolean isKey(SheetPayloads.Row row, String key) {
		return row.label().getContents() instanceof TranslatableContents contents && contents.getKey().equals(key);
	}

	private static boolean hasLine(SheetPayloads.View view, String key) {
		return view.summary().stream().anyMatch(row -> isKey(row, "bloom.sheet." + key));
	}

	private static void near(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < EPS, what + ": expected " + expected + " but the sheet says " + actual);
	}

	/** {@code base} plus a weapon's flat main-hand attack damage. */
	private static float attackWith(ItemStack weapon, double base) {
		double total = base;

		for (AttributeModifier modifier : weapon.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
			total += modifier.getAmount();
		}

		return (float) total;
	}

	@GameTest(template = "empty")
	public static void combatSheetMatchesTheHitPipeline(GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.enchant(Enchantments.SHARPNESS, 3);
		// Hold an iron sword and ask about the diamond one in slot 5: the sheet swaps it in without touching the player.
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		player.getInventory().setItem(5, sword);
		grant(player, CombatIds.FIRST_STRIKE);
		TestPlayers.points(player, CombatIds.POWER, 50);
		TestPlayers.points(player, CombatIds.CRITICAL, 40);
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
		LivingEntity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		float attack = attackWith(sword, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
		float enchant = EnchantmentHelper.getDamageBonus(sword, zombie.getMobType());
		helper.assertTrue(Math.abs(attack - 10.0F) < 1.0E-4F, "1 base + 6 diamond sword + 3 Power = 10, was " + attack);

		CombatSkill.recordSwingForTests(player, zombie, 1.0F, false, true);
		double hit = CombatSkill.modifyIncoming(zombie, source, attack + enchant);
		CombatSkill.endSwingForTests();
		near(helper, hit, value(plain, "combat.hit"), "A full-strength hit (Sharpness III, First Strike)");

		LivingEntity fresh = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 1));
		CombatSkill.recordSwingForTests(player, fresh, 1.0F, true, false);
		double crit = CombatSkill.modifyIncoming(fresh, source, attack * 1.5F + enchant);
		CombatSkill.endSwingForTests();
		near(helper, crit, value(plain, "combat.crit"), "A jump crit (40 Critical)");

		near(helper, CombatRules.getDamageAfterAbsorb((float) hit, 20.0F, 8.0F), value(armored, "combat.hit"), "The same hit on full diamond armor");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void sweepFollowsSweepingEdge(GameTestHelper helper) {
		if (com.freshorangeslices.bloomlevelup.compat.BetterCombat.installed()) {
			helper.succeed(); // Better Combat replaces a sword's sweep (BetterCombatGameTests.sheetFollowsTheCrowdRule)
			return;
		}

		// 1.20.1 sweeps only with Sweeping Edge: 1 + ratio x the hit (III: 3/4).
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.enchant(Enchantments.SWEEPING_EDGE, 3);
		ServerPlayer player = player(helper, sword);
		grant(player);
		SheetPayloads.View view = sheet(player, SkillXp.COMBAT, -1, 0);
		near(helper, 1.0 + 0.75 * value(view, "combat.hit"), value(view, "combat.sweep"), "Sweep with Sweeping Edge III");

		// Full Sweep: the whole blow.
		grant(player, CombatIds.FULL_SWEEP);
		SheetPayloads.View full = sheet(player, SkillXp.COMBAT, -1, 0);
		near(helper, 1.0 + value(full, "combat.hit"), value(full, "combat.sweep"), "Full Sweep");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void liveSwitchesFollowTheFight(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		grant(player, CombatIds.PREDATOR);
		StatEffects.refresh(player);
		DamageSource source = player.damageSources().playerAttack(player);
		LivingEntity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		float attack = attackWith(new ItemStack(Items.IRON_SWORD), player.getAttributeValue(Attributes.ATTACK_DAMAGE));

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

	@GameTest(template = "empty")
	public static void armorSheetMatchesARealHit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Difficulty difficulty = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.NORMAL, true); // mob hits scale with difficulty; Normal leaves them alone

		try {
			ServerPlayer player = player(helper, ItemStack.EMPTY);
			grant(player, ArmorIds.IRON_SKIN);
			TestPlayers.points(player, ArmorIds.DEFENSE, 100);
			StatEffects.refresh(player);
			helper.assertTrue(player.getArmorValue() == 10, "100 Defense = 10 armor, was " + player.getArmorValue());

			SheetPayloads.View view = sheet(player, SkillXp.ARMOR, -1, 0); // hit by a zombie
			LivingEntity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
			float before = player.getHealth();
			float zombieHit = (float) zombie.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
			helper.assertTrue(player.hurt(player.damageSources().mobAttack(zombie), zombieHit), "The hit lands");
			double lost = before - player.getHealth();
			near(helper, lost, value(view, "armor.lost"), "Health lost to a zombie's hit (10 armor, Iron Skin)");
			near(helper, Math.ceil(player.getMaxHealth() / lost - 1.0E-6), value(view, "armor.hits_to_down"), "Zombie hits to go down from full health");

			// A netherite sword with Sharpness V and Strength II: 1 + 7 + 6 (Strength II) + 3 (Sharpness V).
			SheetPayloads.View netherite = sheet(player, SkillXp.ARMOR, -1, 5);
			double swing = netherite.details().stream().filter(row -> isKey(row, "bloom.sheet.armor.step.hit"))
					.mapToDouble(row -> Double.parseDouble(row.value().getString().trim())).findFirst().orElse(-1.0);
			near(helper, 17.0, swing, "The netherite swing's hit");
			helper.assertTrue(netherite.gauge().is(Items.NETHERITE_SWORD), "The picture is the netherite sword");
		} finally {
			level.getServer().setDifficulty(difficulty, true);
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void comparingWeaponsAndBuilds(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_SWORD));
		player.getInventory().setItem(1, new ItemStack(Items.DIAMOND_SWORD));
		grant(player);
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);

		// The diamond sword picked, the iron one (slot 0, in hand) pinned: the diamond's numbers, each with how far
		// it is from the iron's.
		double iron = value(sheet(player, SkillXp.COMBAT, 0, 0), "combat.hit");
		double diamond = value(sheet(player, SkillXp.COMBAT, 1, 0), "combat.hit");
		SheetPayloads.View pinned = StatSheets.build(player, new SheetPayloads.Request(1, SkillXp.COMBAT, 1, 0, List.of(), 0, false));
		helper.assertTrue(diamond > iron, "A diamond sword hits harder than an iron one");
		near(helper, diamond, value(pinned, "combat.hit"), "The picked sword's own number");
		near(helper, diamond - iron, delta(pinned, "combat.hit"), "Its difference from the pinned one");
		helper.assertTrue(isKey(pinned.summary().get(0), "bloom.sheet.ui.pin.note"), "A note says what it's compared with");

		// Build 2 (in use) has 20 Power, Build 1 none: looking at Build 1 shows its numbers against Build 2's.
		helper.assertTrue(service.switchBuild(profile, 0L).ok(), "The second build is unlocked");
		TestPlayers.points(player, CombatIds.POWER, 20);
		double strong = value(sheet(player, SkillXp.COMBAT, -1, 0), "combat.hit");
		SheetPayloads.View other = StatSheets.build(player,
				new SheetPayloads.Request(1, SkillXp.COMBAT, -1, 0, List.of(), SheetPayloads.Request.NO_COMPARE, true));
		double weak = value(other, "combat.hit");
		near(helper, strong - 20 * StatEffects.POWER_PER_POINT, weak, "Build 1's hit, without the 20 Power");
		near(helper, weak - strong, delta(other, "combat.hit"), "Its difference from the build in use");
		helper.assertTrue(isKey(other.summary().get(0), "bloom.sheet.ui.build.note"), "A note says which build it is");
		helper.assertTrue(profile.activeBuild() == 1 && service.statValue(profile, CombatIds.POWER) == 20, "Looking changed nothing");
		near(helper, strong, value(sheet(player, SkillXp.COMBAT, -1, 0), "combat.hit"), "The build in use is back");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void everyTabHasASheet(GameTestHelper helper) {
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_PICKAXE));
		grant(player);

		for (String category : ALL) {
			SheetPayloads.View view = sheet(player, category, -1, -1);
			helper.assertFalse(view.summary().isEmpty(), category + " has a summary");
			helper.assertFalse(view.details().isEmpty(), category + " has a breakdown");
			helper.assertFalse(hasLine(view, "error"), category + " worked out without an error");
		}

		// Bare hands, a bow, a crossbow and a trident each get their own sections.
		player.getInventory().setItem(6, new ItemStack(Items.BOW));
		player.getInventory().setItem(7, new ItemStack(Items.TRIDENT));
		player.getInventory().setItem(8, new ItemStack(Items.CROSSBOW));
		helper.assertFalse(sheet(player, SkillXp.COMBAT, -2, 0).summary().isEmpty(), "Bare hands");
		helper.assertTrue(hasLine(sheet(player, SkillXp.COMBAT, 6, 0), "combat.arrow_bow"), "A bow shows its arrows");
		helper.assertTrue(hasLine(sheet(player, SkillXp.COMBAT, 7, 0), "combat.thrown"), "A trident shows its throw");
		helper.assertTrue(hasLine(sheet(player, SkillXp.COMBAT, 8, 0), "combat.load_time"), "A crossbow shows its load time");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void utilitySheetMatchesVanillaMining(GameTestHelper helper) {
		// Iron pickaxe on stone, no effects, no Efficiency points: vanilla's own speed (6), and Efficiency III adds 3x3+1.
		ServerPlayer player = player(helper, new ItemStack(Items.IRON_PICKAXE));
		grant(player);
		near(helper, 6.0, value(sheet(player, SkillXp.UTILITY, -1, -1), "utility.speed"), "Iron pickaxe on stone");

		ItemStack fast = new ItemStack(Items.IRON_PICKAXE);
		fast.enchant(Enchantments.BLOCK_EFFICIENCY, 3);
		player.setItemInHand(InteractionHand.MAIN_HAND, fast);
		near(helper, 16.0, value(sheet(player, SkillXp.UTILITY, -1, -1), "utility.speed"), "With Efficiency III");
		helper.succeed();
	}
}
