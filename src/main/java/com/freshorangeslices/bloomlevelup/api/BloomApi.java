package com.freshorangeslices.bloomlevelup.api;

import net.minecraft.world.entity.player.Player;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.XpGainResult;

/**
 * Small, stable surface for addons and modpack integrations. Query methods return values or copies;
 * the only mutation offered is awarding XP, which goes through the normal progression rules.
 * Call from the logical server.
 */
public final class BloomApi {
	private BloomApi() {
	}

	/** Read-only snapshot of a player's profile. */
	public static BloomProfile snapshot(Player player) {
		return BloomAttachments.profile(player).copy();
	}

	public static int level(Player player, String categoryId) {
		return BloomAttachments.profile(player).category(categoryId).level();
	}

	/** Effective stat points (0 while toggled off). */
	public static int stat(Player player, String statId) {
		return BloomServices.progression().statValue(BloomAttachments.profile(player), statId);
	}

	public static boolean isMajorActive(Player player, String majorId) {
		return BloomServices.progression().isMajorActive(BloomAttachments.profile(player), majorId);
	}

	public static XpGainResult awardXp(Player player, String categoryId, double amount) {
		return BloomServices.progression().addXp(BloomAttachments.profile(player), categoryId, amount);
	}
}
