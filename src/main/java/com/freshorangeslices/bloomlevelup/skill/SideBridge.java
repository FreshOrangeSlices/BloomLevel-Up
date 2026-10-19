package com.freshorangeslices.bloomlevelup.skill;

import java.util.function.BiPredicate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;

/**
 * A few Majors change code that also runs on the client (Unrestricted Riptide's trident use). Common code
 * can't touch client classes, so the client installs its own answer here at startup.
 */
public final class SideBridge {
	private static volatile BiPredicate<Player, String> clientMajorActive = (player, majorId) -> false;

	private SideBridge() {
	}

	public static void setClientMajorActive(BiPredicate<Player, String> query) {
		clientMajorActive = query;
	}

	public static boolean clientMajorActive(Player player, String majorId) {
		return clientMajorActive.test(player, majorId);
	}

	/** Whether a Major is active for this player, on either side: the server reads the profile, the client its synced copy. */
	public static boolean majorActive(Player player, String majorId) {
		if (player instanceof ServerPlayer serverPlayer) {
			return BloomServices.progression().isMajorActive(BloomAttachments.profile(serverPlayer), majorId);
		}

		return player != null && clientMajorActive(player, majorId);
	}
}
