package com.freshorangeslices.bloomlevelup.skill;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.network.BloomSync;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.progression.XpGainResult;

/**
 * The one entry point every activity uses to award category XP: applies the per-category pacing multiplier,
 * skips creative/spectator players, syncs the client and gives level-up feedback.
 */
public final class SkillXp {
	public static final String UTILITY = BloomLevelUp.key("utility");
	public static final String COMBAT = BloomLevelUp.key("combat");
	public static final String ARMOR = BloomLevelUp.key("armor");
	public static final String MISC = BloomLevelUp.key("misc");

	private SkillXp() {
	}

	public static void award(ServerPlayer player, String categoryId, double amount) {
		if (!(amount > 0.0) || player.isCreative() || player.isSpectator()) {
			return;
		}

		double scaled = amount * multiplier(categoryId);

		if (!(scaled > 0.0)) {
			return;
		}

		XpGainResult result = BloomServices.progression().addXp(BloomAttachments.profile(player), categoryId, scaled);
		BloomSync.changed(player);

		if (result.leveledUp()) {
			Component name = Component.translatableWithFallback("bloom.category." + categoryId.replace(':', '.') + ".name", categoryId);
			Component message = result.masteredNow()
					? Component.translatable("bloom.levelup.mastered", name).withStyle(ChatFormatting.GOLD)
					: Component.translatable("bloom.levelup", name, result.newLevel()).withStyle(ChatFormatting.AQUA);
			player.sendSystemMessage(message, true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
					SoundSource.PLAYERS, 0.5F, result.majorLevelsGranted() > 0 ? 1.4F : 1.0F);

			if (result.majorLevelsGranted() > 0) {
				player.sendSystemMessage(Component.translatable("bloom.levelup.major", result.majorLevelsGranted()).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
	}

	private static double multiplier(String categoryId) {
		BloomConfig config = BloomServices.config();

		if (UTILITY.equals(categoryId)) {
			return config.utilityXpMultiplier;
		} else if (COMBAT.equals(categoryId)) {
			return config.combatXpMultiplier;
		} else if (ARMOR.equals(categoryId)) {
			return config.armorXpMultiplier;
		} else if (MISC.equals(categoryId)) {
			return config.miscXpMultiplier;
		}

		return 1.0;
	}
}
