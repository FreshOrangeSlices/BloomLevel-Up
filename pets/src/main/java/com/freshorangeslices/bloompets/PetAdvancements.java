package com.freshorangeslices.bloompets;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bloom Pets' quiet advancements ({@code data/bloompets/advancements/pets/*.json}: no display, one impossible criterion
 * awarded by code), for the quest book's Pets chapter to check: a pet bonded, one at level 10 and at 25, one at its
 * post, One and the Same owned, a tree picked, a game of fetch.
 */
public final class PetAdvancements {
	public static final String BOND = "bond";
	public static final String LEVEL_10 = "level_10";
	public static final String LEVEL_25 = "level_25";
	public static final String POST = "post";
	public static final String ONE_AND_THE_SAME = "one_and_the_same";
	public static final String TREE = "tree";
	public static final String FETCH = "fetch";

	private PetAdvancements() {
	}

	public static void award(ServerPlayer player, String id) {
		if (player == null || player.server == null) {
			return;
		}

		Advancement advancement = player.server.getAdvancements().getAdvancement(BloomPets.id("pets/" + id));

		if (advancement == null) {
			return;
		}

		AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);

		if (!progress.isDone()) {
			for (String criterion : progress.getRemainingCriteria()) {
				player.getAdvancements().award(advancement, criterion);
			}
		}
	}

	/** Whether the player has it (the game tests). */
	public static boolean has(ServerPlayer player, String id) {
		Advancement advancement = player.server.getAdvancements().getAdvancement(BloomPets.id("pets/" + id));
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}
}
