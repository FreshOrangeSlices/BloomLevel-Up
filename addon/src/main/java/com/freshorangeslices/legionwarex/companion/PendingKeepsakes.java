package com.freshorangeslices.legionwarex.companion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Keepsakes for owners who weren't online when their companion fell; handed over when they next join. */
public final class PendingKeepsakes extends SavedData {
	private static final String NAME = "legionwarex_keepsakes";

	private final Map<UUID, List<ItemStack>> pending = new HashMap<>();

	public static PendingKeepsakes get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(PendingKeepsakes::load, PendingKeepsakes::new, NAME);
	}

	private static PendingKeepsakes load(CompoundTag tag) {
		PendingKeepsakes data = new PendingKeepsakes();

		for (Tag entry : tag.getList("Pending", Tag.TAG_COMPOUND)) {
			CompoundTag owner = (CompoundTag) entry;
			List<ItemStack> stacks = new ArrayList<>();

			for (Tag item : owner.getList("Items", Tag.TAG_COMPOUND)) {
				ItemStack stack = ItemStack.of((CompoundTag) item);

				if (!stack.isEmpty()) {
					stacks.add(stack);
				}
			}

			if (owner.hasUUID("Owner") && !stacks.isEmpty()) {
				data.pending.put(owner.getUUID("Owner"), stacks);
			}
		}

		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag list = new ListTag();

		for (Map.Entry<UUID, List<ItemStack>> entry : pending.entrySet()) {
			CompoundTag owner = new CompoundTag();
			owner.putUUID("Owner", entry.getKey());
			ListTag items = new ListTag();
			entry.getValue().forEach(stack -> items.add(stack.save(new CompoundTag())));
			owner.put("Items", items);
			list.add(owner);
		}

		tag.put("Pending", list);
		return tag;
	}

	public void add(UUID owner, ItemStack stack) {
		pending.computeIfAbsent(owner, key -> new ArrayList<>()).add(stack);
		setDirty();
	}

	/** The owner's waiting keepsakes, taken out. */
	public List<ItemStack> take(UUID owner) {
		List<ItemStack> stacks = pending.remove(owner);

		if (stacks == null) {
			return List.of();
		}

		setDirty();
		return stacks;
	}

	/** The owner's waiting keepsakes, left in place. */
	public List<ItemStack> peek(UUID owner) {
		return List.copyOf(pending.getOrDefault(owner, List.of()));
	}
}
