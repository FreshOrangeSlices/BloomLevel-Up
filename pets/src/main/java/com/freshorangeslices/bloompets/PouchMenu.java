package com.freshorangeslices.bloompets;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A companion's food pouch ({@link PetStorage#POUCH} slots of cooked food) over the player's inventory, laid out as the
 * hopper's screen (the client draws it on the hopper's picture with two slots).
 */
public final class PouchMenu extends AbstractContainerMenu {
	public static final int SLOTS = PetStorage.POUCH;
	/** The pouch's first slot, from the screen's left (the hopper's row, two slots in its middle). */
	public static final int SLOT_X = 71;
	public static final int SLOT_Y = 20;
	private final Container pouch;

	/** The client's side (it's told what's in it). */
	public PouchMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(SLOTS));
	}

	public PouchMenu(int id, Inventory inventory, Container pouch) {
		super(BloomPets.POUCH.get(), id);
		checkContainerSize(pouch, SLOTS);
		this.pouch = pouch;
		pouch.startOpen(inventory.player);

		for (int i = 0; i < SLOTS; i++) {
			addSlot(new Slot(pouch, i, SLOT_X + i * 18, SLOT_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.is(Bonding.COMPANION_FOOD);
				}
			});
		}

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 51 + row * 18));
			}
		}

		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(inventory, column, 8 + column * 18, 109));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return pouch.stillValid(player);
	}

	/** Shift-click: food into the pouch, anything out of it into the inventory. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);

		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();

		if (index < SLOTS) {
			if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!stack.is(Bonding.COMPANION_FOOD) || !moveItemStackTo(stack, 0, SLOTS, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		return moved;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		pouch.stopOpen(player);
	}
}
