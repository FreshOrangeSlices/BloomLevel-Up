package com.freshorangeslices.bloomlevelup.sheet;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.utility.UtilityIds;
import com.freshorangeslices.bloomlevelup.skill.utility.UtilitySkill;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/**
 * The Utility sheet: how fast a tool works the block it's made for (vanilla's Player.getDestroySpeed with the
 * tool swapped in), what Yield and Preservation add, and the numbers of the Utility Majors the player owns.
 */
final class UtilitySheet {
	private static final String[] MATERIALS = {"wooden", "stone", "copper", "iron", "diamond", "netherite"};

	private UtilitySheet() {
	}

	/** A kind of tool, the block it's measured on, and the tool name the picture compares with. */
	private record Work(String key, BlockState block, String tool) {
	}

	static void build(ServerPlayer player, ItemStack tool, Sheet sheet) {
		ServerLevel level = (ServerLevel) player.level();
		BloomConfig config = BloomServices.config();
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);
		Loadout loadout = new Loadout(player, tool);
		Work work = work(tool);

		// ---- mining speed (Player.getDestroySpeed)
		float toolSpeed = tool.getDestroySpeed(work.block());
		double enchantSpeed = toolSpeed > 1.0F ? loadout.value(Attributes.MINING_EFFICIENCY) : 0.0;
		double speed = toolSpeed + enchantSpeed;
		double haste = MobEffectUtil.hasDigSpeed(player) ? 1.0 + (MobEffectUtil.getDigSpeedAmplification(player) + 1) * 0.2 : 1.0;
		MobEffectInstance fatigue = player.getEffect(MobEffects.MINING_FATIGUE);
		double fatigueFactor = fatigue == null ? 1.0 : Math.pow(0.3, fatigue.getAmplifier() + 1);
		double breakSpeed = loadout.value(Attributes.BLOCK_BREAK_SPEED);
		double total = speed * haste * fatigueFactor * breakSpeed;
		boolean harvests = !work.block().requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(work.block());
		float hardness = work.block().getDestroySpeed(level, player.blockPosition());
		double ticks = total <= 0.0 ? Double.POSITIVE_INFINITY : Math.ceil(hardness * (harvests ? 30.0 : 100.0) / total);
		boolean instant = total > hardness * 30.0 && harvests;
		Component blockName = work.block().getBlock().getName();
		int efficiency = UtilitySkill.efficiency(player, tool);
		int yield = service.statValue(profile, UtilityIds.YIELD);
		int preservationPoints = service.statValue(profile, UtilityIds.PRESERVATION);
		boolean shovelMastery = UtilitySkill.shovelBoost(player, tool);
		int preservation = shovelMastery ? StatFormulas.boosted(preservationPoints, config.shovelMasteryBonus) : preservationPoints;

		sheet.summary(Sheet.plain(total), "utility.speed", blockName);
		sheet.summary(instant ? Sheet.word("value.instant") : Sheet.seconds(ticks), "utility.break_time", blockName);
		sheet.summary(Sheet.plusPct(efficiency / 100.0), "utility.efficiency");
		sheet.summary(yieldValue(yield), "utility.yield");
		sheet.summary(Sheet.pct(StatFormulas.preservationSaveChance(preservation)), "utility.preservation");

		sheet.heading("utility.h.speed", blockName);
		sheet.row(Sheet.plain(toolSpeed), tool.isEmpty() ? "utility.hand_speed" : "utility.tool_speed");

		if (enchantSpeed > 0.0) {
			sheet.row(Sheet.plus(enchantSpeed), "utility.enchant_speed");
		}

		if (haste != 1.0) {
			sheet.row(Sheet.times(haste), "utility.haste");
		}

		if (fatigueFactor != 1.0) {
			sheet.row(Sheet.times(fatigueFactor), "utility.fatigue");
		}

		sheet.row(Sheet.times(breakSpeed), "utility.break_speed", Sheet.num(efficiency));

		if (shovelMastery) {
			sheet.note("utility.shovel_mastery.note", Sheet.num(config.shovelMasteryBonus * 100.0));
		}

		sheet.total(Sheet.plain(total), "utility.speed_total");
		sheet.row(instant ? Sheet.word("value.instant") : Sheet.seconds(ticks), "utility.break_time", blockName);

		if (!harvests) {
			sheet.note("utility.wrong_tool");
		}

		sheet.note("utility.speed.note");

		// ---- gathering
		sheet.heading("utility.h.gather");
		sheet.row(Sheet.plain(yield / 100), "utility.yield_copies", Sheet.num(yield));
		sheet.row(Sheet.pct(yield % 100 / 100.0), "utility.yield_chance");
		sheet.note("utility.yield.note");

		if (shovelMastery) {
			int boosted = StatFormulas.boosted(yield, config.shovelMasteryBonus);
			double chance = config.excavationTreasureBaseChance + boosted / 100.0 * config.excavationTreasureChancePer100Yield;
			sheet.row(Sheet.pct(chance), "utility.treasure");
		}

		// ---- durability
		sheet.heading("utility.h.durability");
		Holder<Enchantment> unbreakingHolder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
		int unbreaking = EnchantmentHelper.getItemEnchantmentLevel(unbreakingHolder, tool);
		double vanillaSave = unbreaking <= 0 ? 0.0 : 1.0 - 1.0 / (unbreaking + 1.0);
		double bloomSave = StatFormulas.preservationSaveChance(preservation);
		sheet.row(Sheet.pct(vanillaSave), "utility.unbreaking", Sheet.num(unbreaking));
		sheet.row(Sheet.pct(bloomSave), "utility.preservation_save", Sheet.num(preservation));
		sheet.total(Sheet.pct(StatFormulas.anyOf(vanillaSave, bloomSave)), "utility.saves_total");

		if (tool.isDamageableItem()) {
			sheet.row(Sheet.plain((tool.getMaxDamage() - tool.getDamageValue()) + " / " + tool.getMaxDamage()), "utility.durability_left");
		}

		// ---- Majors
		boolean any = false;

		if (service.isMajorActive(profile, UtilityIds.VEIN_MINING)) {
			any = heading(sheet, any);
			sheet.row(Sheet.plain(config.veinMiningMaxBlocks), "utility.vein");
		}

		if (service.isMajorActive(profile, UtilityIds.TIMBER)) {
			any = heading(sheet, any);
			sheet.row(Sheet.plain(config.timberMaxLogs), "utility.timber");
		}

		if (service.isMajorActive(profile, UtilityIds.GROWTH_AURA)) {
			any = heading(sheet, any);
			sheet.row(Sheet.pct(config.growthAuraChance), "utility.growth", Sheet.num(config.growthAuraRadius), Sheet.num(config.growthAuraIntervalTicks / 20.0));
		}

		if (service.isMajorActive(profile, UtilityIds.MASTER_SHEARING)) {
			any = heading(sheet, any);
			sheet.row(Sheet.pct(config.masterShearingChance), "utility.shearing");
		}

		if (service.isMajorActive(profile, UtilityIds.TRIPLE_CATCH)) {
			heading(sheet, any);
			sheet.row(Sheet.plain(config.tripleCatchRolls), "utility.triple_catch");
		}

		gauge(level, sheet, work, total);
	}

	private static boolean heading(Sheet sheet, boolean already) {
		if (!already) {
			sheet.heading("utility.h.majors");
		}

		return true;
	}

	private static Component yieldValue(int yield) {
		int copies = yield / 100;
		int chance = yield % 100;
		return Sheet.colored(copies > 0 ? "+" + copies + (chance > 0 ? " & " + chance + "%" : "") : chance + "%", copies > 0 || chance > 0 ? Sheet.GOOD : Sheet.PLAIN);
	}

	private static Work work(ItemStack tool) {
		if (tool.is(ItemTags.SHOVELS)) {
			return new Work("shovel", Blocks.DIRT.defaultBlockState(), "shovel");
		}

		if (tool.is(ItemTags.AXES)) {
			return new Work("axe", Blocks.OAK_LOG.defaultBlockState(), "axe");
		}

		if (tool.is(ItemTags.HOES)) {
			return new Work("hoe", Blocks.HAY_BLOCK.defaultBlockState(), "hoe");
		}

		return new Work("pickaxe", Blocks.STONE.defaultBlockState(), "pickaxe");
	}

	/**
	 * The picture: the vanilla tool of the same kind (and Efficiency level) whose own speed on the block is closest
	 * to the player's (Haste, Mining Fatigue and Bloom's Efficiency included); past Netherite with Efficiency V, how
	 * many times faster the player is.
	 */
	private static void gauge(ServerLevel level, Sheet sheet, Work work, double speed) {
		Holder<Enchantment> efficiency = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
		ItemStack best = ItemStack.EMPTY;
		double bestSpeed = 0.0;
		int bestLevel = 0;
		ItemStack top = ItemStack.EMPTY;
		double topSpeed = 0.0;
		int topLevel = 0;

		for (String material : MATERIALS) {
			Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(material + "_" + work.tool()));

			if (item.isEmpty() || item.get() == Items.AIR) {
				continue;
			}

			for (int enchantLevel = 0; enchantLevel <= 5; enchantLevel++) {
				ItemStack candidate = new ItemStack(item.get());

				if (enchantLevel > 0) {
					candidate.enchant(efficiency, enchantLevel);
				}

				float base = candidate.getDestroySpeed(work.block());
				double[] bonus = {0.0};

				if (base > 1.0F) {
					EnchantmentHelper.forEachModifier(candidate, EquipmentSlot.MAINHAND, (attribute, modifier) -> {
						if (attribute.value() == Attributes.MINING_EFFICIENCY.value()) {
							bonus[0] += modifier.amount();
						}
					});
				}

				double candidateSpeed = base + bonus[0];
				double gap = Math.abs(candidateSpeed - speed);
				double bestGap = Math.abs(bestSpeed - speed);

				if (best.isEmpty() || gap < bestGap - 1.0E-4 || (Math.abs(gap - bestGap) <= 1.0E-4 && enchantLevel < bestLevel)) {
					best = candidate;
					bestSpeed = candidateSpeed;
					bestLevel = enchantLevel;
				}

				if (candidateSpeed > topSpeed) {
					top = candidate;
					topSpeed = candidateSpeed;
					topLevel = enchantLevel;
				}
			}
		}

		if (best.isEmpty()) {
			return;
		}

		boolean past = speed > topSpeed + 1.0E-4;
		ItemStack shown = past ? top : best;
		int shownLevel = past ? topLevel : bestLevel;
		Component name = shownLevel > 0
				? Component.translatable("bloom.sheet.gauge.with", shown.getHoverName(), efficiency.value().description(),
						Component.translatable("enchantment.level." + shownLevel))
				: shown.getHoverName();
		sheet.gauge = shown;
		sheet.gaugeCaption = Component.translatable(past ? "bloom.sheet.gauge.tool_past" : "bloom.sheet.gauge.tool", name,
				Sheet.num(past ? topSpeed : bestSpeed));
		sheet.gaugeBadge = past && topSpeed > 0.0 ? Sheet.colored("×" + Sheet.num(speed / topSpeed), Sheet.GOLD) : Component.empty();
	}
}
