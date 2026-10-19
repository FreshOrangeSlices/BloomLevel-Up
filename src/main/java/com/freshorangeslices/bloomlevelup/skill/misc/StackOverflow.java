package com.freshorangeslices.bloomlevelup.skill.misc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.skill.SideBridge;

/**
 * Stack Overflow: in the owner's own inventory and Hoarder's Pocket, anything that already stacks holds up to
 * 300 per slot. Chests, hoppers and other players keep vanilla limits (a big stack splits as it goes in).
 * Item stacks of up to {@link #HARD_CAP} are allowed by the save codecs (mixins), so nothing is lost on save.
 */
public final class StackOverflow {
	/** The most any stack can hold anywhere; the save codecs accept counts up to this. */
	public static final int HARD_CAP = 300;

	private StackOverflow() {
	}

	private static int cap() {
		return Math.max(1, Math.min(HARD_CAP, BloomServices.config().stackOverflowMax));
	}

	/** Stackable items only: weapons, armor, potions and other single items stay single. */
	public static boolean eligible(ItemStack stack) {
		return !stack.isEmpty() && stack.getMaxStackSize() > 1;
	}

	/** The per-slot limit for a stack in this player's inventory or pocket. Works on both sides. */
	public static int limit(Player player, ItemStack stack, int vanilla) {
		if (player == null || !eligible(stack) || !SideBridge.majorActive(player, MiscIds.STACK_OVERFLOW)) {
			return vanilla;
		}

		return Math.max(vanilla, cap());
	}

	/** Without the Major, stacks bigger than vanilla allows are split back up (what doesn't fit drops). */
	public static void normalize(ServerPlayer player) {
		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			int max = Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize());

			if (!stack.isEmpty() && stack.getCount() > max) {
				ItemStack extra = stack.copyWithCount(stack.getCount() - max);
				stack.setCount(max);
				inventory.placeItemBackInInventory(extra, Prediction.SERVER_ONLY);
			}
		}
	}
}
