package com.freshorangeslices.bloomlevelup.skill;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.freshorangeslices.bloomlevelup.config.BloomConfig;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;

/**
 * A few Majors change code that also runs on the client (Unrestricted Riptide's trident use). Common code
 * can't touch client classes, so the client installs its own answer here at startup.
 */
public final class SideBridge {
	private static volatile BiPredicate<Player, String> clientMajorActive = (player, majorId) -> false;
	private static volatile Supplier<BloomConfig> clientConfig = () -> null;

	private SideBridge() {
	}

	public static void setClientMajorActive(BiPredicate<Player, String> query) {
		clientMajorActive = query;
	}

	public static void setClientConfig(Supplier<BloomConfig> config) {
		clientConfig = config;
	}

	/** The config that applies to this player: the server's own, or on the client the copy the server synced. */
	public static BloomConfig config(Player player) {
		if (!(player instanceof ServerPlayer)) {
			BloomConfig synced = clientConfig.get();

			if (synced != null) {
				return synced;
			}
		}

		return BloomServices.config();
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
