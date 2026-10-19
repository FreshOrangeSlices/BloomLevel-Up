package com.freshorangeslices.bloomlevelup.skill.misc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.skill.SideBridge;

/**
 * Stack Overflow: in the owner's own inventory and Hoarder's Pocket, anything that already stacks holds up to
 * 300 per slot. Chests, hoppers and other players keep vanilla limits (a big stack splits as it goes in).
 * Item stacks of up to {@link #HARD_CAP} are allowed by the save codecs (mixins), so nothing is lost on save.
 */
public final class StackOverflow {
	/** The most any stack can hold anywhere; the save codecs accept counts up to this. */
	public static final int HARD_CAP = 300;
	/** What a vanilla container slot allows at most (Container's default). */
	private static final int VANILLA_SLOT_MAX = 99;

	private StackOverflow() {
	}

	private static int cap(Player player) {
		return Math.max(1, Math.min(HARD_CAP, SideBridge.config(player).stackOverflowMax));
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

		return Math.max(vanilla, cap(player));
	}

	/** Without the Major, stacks bigger than vanilla allows are split back up (what doesn't fit drops). */
	public static void normalize(ServerPlayer player) {
		normalizePocket(player);

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

	/**
	 * The pocket's big stacks split into its free slots first, then into the inventory, then onto the ground.
	 * Skipped while the pocket is open (its screen saves its own copy).
	 */
	private static void normalizePocket(ServerPlayer player) {
		if (player.containerMenu instanceof HoardersPocket.PocketMenu) {
			return;
		}

		NonNullList<ItemStack> items = HoardersPocket.items(player);
		List<ItemStack> overflow = new ArrayList<>();

		for (ItemStack stack : items) {
			int max = Math.min(VANILLA_SLOT_MAX, stack.getMaxStackSize());

			if (!stack.isEmpty() && stack.getCount() > max) {
				overflow.add(stack.copyWithCount(stack.getCount() - max));
				stack.setCount(max);
			}
		}

		if (overflow.isEmpty()) {
			return;
		}

		for (ItemStack extra : overflow) {
			int max = Math.min(VANILLA_SLOT_MAX, extra.getMaxStackSize());

			for (int i = 0; i < items.size() && !extra.isEmpty(); i++) {
				if (items.get(i).isEmpty()) {
					items.set(i, extra.split(max));
				}
			}

			if (!extra.isEmpty()) {
				player.getInventory().placeItemBackInInventory(extra, Prediction.SERVER_ONLY);
			}
		}

		HoardersPocket.store(player, items);
	}
}
