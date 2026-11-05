package com.freshorangeslices.bloompets;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * A Bloom pet's storage (small 9 slots, medium 18, pack 27; the Vex has none), kept with the pet's entry in the world's
 * pet list, so it opens from anywhere. Modded companions keep their own mod's inventory and have none here.
 */
public final class PetStorage {
	private PetStorage() {
	}

	/** Opens the pet's storage for its owner; returns a message if it has none, else null. */
	public static Component open(ServerPlayer player, PetRegistry.Record record) {
		if (record.kind != Pets.Kind.BLOOM || record.slots <= 0) {
			return Component.translatable("bloompets.storage.none", record.name);
		}

		// A sulking pet won't carry things (what it has stays safe).
		Entity live = Pets.loaded(player.server, record.pet);

		if (live != null ? PetMood.sad(live) : record.mood < PetMood.SAD_BELOW) {
			return Component.translatable("bloompets.storage.sad", record.name);
		}

		int rows = Math.min(6, Math.max(1, record.slots / 9));
		MenuType<?> type = switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
		PetRegistry registry = PetRegistry.get(player.server);
		SimpleContainer container = new SimpleContainer(rows * 9) {
			@Override
			public void setChanged() {
				super.setChanged();
				record.items = createTag();
				registry.setDirty();
			}
		};
		container.fromTag(record.items);
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(type, id, inventory, container, rows),
				Component.translatable("bloompets.storage.title", record.name)));
		return null;
	}

	/** A pet that's gone for good leaves what it carried where it was. */
	public static void dropAll(Entity where, PetRegistry.Record record) {
		if (record.items.isEmpty()) {
			return;
		}

		SimpleContainer container = new SimpleContainer(Math.max(record.items.size(), 1));
		container.fromTag(record.items);
		Containers.dropContents(where.level(), where.blockPosition(), container);
		record.items = new net.minecraft.nbt.ListTag();
	}
}
