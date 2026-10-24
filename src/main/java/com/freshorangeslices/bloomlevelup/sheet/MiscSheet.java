package com.freshorangeslices.bloomlevelup.sheet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscIds;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;
import com.freshorangeslices.bloomlevelup.stats.StatFormulas;

/** The Misc sheet: experience, food, movement, effects and storage. */
final class MiscSheet {
	/** Blocks per second for each point of the movement speed attribute while walking (vanilla). */
	private static final double BLOCKS_PER_SPEED = 43.178;

	private MiscSheet() {
	}

	static void build(ServerPlayer player, Sheet sheet) {
		BloomConfig config = BloomServices.config();
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);
		int wisdom = service.statValue(profile, MiscIds.WISDOM);
		int endurance = service.statValue(profile, MiscIds.ENDURANCE);
		int speed = service.statValue(profile, MiscIds.SPEED);
		double wisdomMultiplier = StatFormulas.wisdomMultiplier(wisdom);
		double foodLasts = 1.0 / StatFormulas.enduranceExhaustionFactor(endurance);
		double movement = new Loadout(player, player.getMainHandItem()).value(Attributes.MOVEMENT_SPEED);

		sheet.summary(Sheet.times(wisdomMultiplier), "misc.wisdom");
		sheet.summary(Sheet.times(foodLasts), "misc.food_lasts");
		sheet.summary(Sheet.plusPct(speed / 100.0), "misc.speed");
		sheet.summary(Sheet.plain(movement * BLOCKS_PER_SPEED), "misc.walk");

		sheet.heading("misc.h.xp");
		sheet.row(Sheet.times(wisdomMultiplier), "misc.wisdom_row", Sheet.num(wisdom));
		sheet.note("misc.wisdom.note");
		sheet.row(Sheet.plain(config.miscXpPerOrbPoint), "misc.xp_per_orb");

		if (service.isMajorActive(profile, MiscIds.XP_INSURANCE)) {
			sheet.row(Sheet.pct(config.xpInsuranceShare), "misc.insurance");
			sheet.note("misc.insurance.now", Sheet.num(MiscSkill.insuredXpFor(player)));
		}

		sheet.heading("misc.h.food");
		sheet.row(Sheet.times(foodLasts), "misc.endurance", Sheet.num(endurance));
		sheet.row(Sheet.pct(1.0 - StatFormulas.enduranceExhaustionFactor(endurance)), "misc.exhaustion_cut");

		if (service.isMajorActive(profile, MiscIds.IRON_STOMACH)) {
			sheet.row(Sheet.plain(config.ironStomachNutrition + " / " + Sheet.num(config.ironStomachSaturation)), "misc.iron_stomach");
		}

		if (service.isMajorActive(profile, MiscIds.SECOND_SERVING)) {
			sheet.row(Sheet.word("value.on"), "misc.second_serving");
		}

		if (service.isMajorActive(profile, MiscIds.SELECTIVE_CLEANSING)) {
			sheet.row(Sheet.word("value.on"), "misc.cleansing");
		}

		sheet.heading("misc.h.movement");
		sheet.row(Sheet.plusPct(speed / 100.0), "misc.speed_row", Sheet.num(speed));
		sheet.total(Sheet.plain(movement * BLOCKS_PER_SPEED), "misc.walk");
		sheet.note("misc.walk.note");

		if (service.isMajorActive(profile, MiscIds.ASCENSION)) {
			sheet.row(Sheet.word(MiscSkill.grounded(player) ? "value.grounded" : "value.ready"), "misc.ascension");
		}

		boolean wine = service.isMajorActive(profile, MiscIds.WATER_TO_WINE);
		boolean overdose = service.isMajorActive(profile, MiscIds.OVERDOSE);
		boolean fuel = service.isMajorActive(profile, MiscIds.BECOME_MY_FUEL);

		if (wine || overdose || fuel) {
			sheet.heading("misc.h.effects");

			if (wine) {
				sheet.row(Sheet.seconds(config.waterToWineMinTicks), "misc.wine");
			}

			if (overdose) {
				sheet.row(Sheet.plus(config.overdoseMaxBoost), "misc.overdose");
			}

			if (fuel) {
				sheet.row(Sheet.seconds(config.fuelEffectTicks), "misc.fuel");
			}
		}

		boolean pocket = service.isMajorActive(profile, MiscIds.HOARDERS_POCKET);
		boolean overflow = service.isMajorActive(profile, MiscIds.STACK_OVERFLOW);
		boolean runes = service.isMajorActive(profile, MiscIds.RUNE_EMBEDDING);
		boolean overenchant = service.isMajorActive(profile, MiscIds.OVERENCHANT);

		if (pocket || overflow || runes || overenchant) {
			sheet.heading("misc.h.items");

			if (pocket) {
				sheet.row(Sheet.plain(9), "misc.pocket");
			}

			if (overflow) {
				sheet.row(Sheet.plain(config.stackOverflowMax), "misc.stack");
			}

			if (runes) {
				sheet.row(Sheet.plain(5), "misc.runes");
			}

			if (overenchant) {
				sheet.row(Sheet.plain(config.overenchantMaxLevel), "misc.overenchant");
			}
		}
	}
}
