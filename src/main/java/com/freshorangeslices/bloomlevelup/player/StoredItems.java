package com.freshorangeslices.bloomlevelup.player;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Fixed-size item lists kept in the player's Bloom capability (Hoarder's Pocket, Rune Embedding): saved with the
 * player and kept through death. Stored as {@code [{Slot, ...item}]} like a chest, so big Stack Overflow stacks
 * survive (the item save hooks write counts over 127 as an int).
 */
public final class StoredItems {
	private StoredItems() {
	}

	public static NonNullList<ItemStack> read(Player player, String key, int size) {
		NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
		ListTag list = BloomAttachments.items(player).getList(key, Tag.TAG_COMPOUND);

		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			int slot = entry.getByte("Slot") & 255;

			if (slot < size) {
				items.set(slot, ItemStack.of(entry));
			}
		}

		return items;
	}

	public static void write(Player player, String key, NonNullList<ItemStack> items) {
		ListTag list = new ListTag();

		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);

			if (!stack.isEmpty()) {
				CompoundTag entry = new CompoundTag();
				entry.putByte("Slot", (byte) i);
				stack.save(entry);
				list.add(entry);
			}
		}

		CompoundTag all = BloomAttachments.items(player);

		if (list.isEmpty()) {
			all.remove(key);
		} else {
			all.put(key, list);
		}
	}
}
