package com.freshorangeslices.bloomlevelup.player;

import net.minecraft.server.level.ServerPlayer;

import com.freshorangeslices.bloomlevelup.progression.VanillaXpWallet;

/**
 * Adapts a player's vanilla experience to {@link VanillaXpWallet}. The raw total is recomputed from the
 * visible level + bar (vanilla's {@code totalExperience} field drifts after {@code /xp levels} changes).
 */
public final class PlayerXpWallet implements VanillaXpWallet {
	private final ServerPlayer player;

	public PlayerXpWallet(ServerPlayer player) {
		this.player = player;
	}

	@Override
	public int totalPoints() {
		return pointsForLevel(player.experienceLevel) + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
	}

	@Override
	public void spend(int points) {
		int remaining = Math.max(0, totalPoints() - points);
		player.setExperienceLevels(0);
		player.setExperiencePoints(0);
		player.totalExperience = 0;
		player.giveExperiencePoints(remaining);
	}

	/** Vanilla's total XP needed to reach {@code level} from zero. */
	public static int pointsForLevel(int level) {
		if (level <= 16) {
			return level * level + 6 * level;
		}

		if (level <= 31) {
			return (int) (2.5 * level * level - 40.5 * level + 360);
		}

		return (int) (4.5 * level * level - 162.5 * level + 2220);
	}
}
