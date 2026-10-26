package com.freshorangeslices.bloomlevelup.registry;

import java.util.function.Consumer;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/** The mod's screens' menu types (Hoarder's Pocket, Rune Embedding), and the slot layout they share. */
public final class BloomMenus {
	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, BloomLevelUp.MOD_ID);

	private BloomMenus() {
	}

	public static void register(IEventBus modBus) {
		// Loads the classes holding the menu types, so they're on the register before it fires.
		com.freshorangeslices.bloomlevelup.skill.misc.HoardersPocket.MENU.getId();
		com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding.MENU.getId();
		MENUS.register(modBus);
	}

	/** The player's inventory (three rows) and hotbar under a menu, the way vanilla lays them out. */
	public static void addInventorySlots(Consumer<Slot> add, Inventory inventory, int x, int y) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				add.accept(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
			}
		}

		for (int column = 0; column < 9; column++) {
			add.accept(new Slot(inventory, column, x + column * 18, y + 58));
		}
	}
}
