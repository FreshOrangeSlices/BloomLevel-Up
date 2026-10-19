package com.freshorangeslices.bloomlevelup.skill.misc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;

/**
 * Hoarder's Pocket: 9 personal slots saved on the player, kept through death, dimension travel and Bloom+ /
 * refunds. It opens as a one-row chest screen (its own menu type, so big Stack Overflow stacks show right).
 * Without the Major (refunded, Bloom+, switched off) the pocket still opens to take things out, but not to put
 * things in, so nothing is ever stranded.
 */
public final class HoardersPocket {
	public static final int SIZE = 9;

	public static final AttachmentType<ItemContainerContents> CONTENTS = AttachmentRegistry.create(BloomLevelUp.id("hoarders_pocket"),
			builder -> builder.persistent(ItemContainerContents.CODEC).initializer(() -> ItemContainerContents.EMPTY).copyOnDeath());

	/**
	 * The pocket's own menu type. On the client it is a plain one-row chest screen whose container uses the same
	 * per-stack limit as the server, so Stack Overflow's big stacks show (and predict) correctly.
	 */
	public static final MenuType<ChestMenu> MENU = Registry.register(BuiltInRegistries.MENU, BloomLevelUp.id("hoarders_pocket"),
			new MenuType<>((id, inventory) -> new ChestMenu(HoardersPocket.MENU, id, inventory, new PocketContainer(inventory.player, null), 1),
					FeatureFlags.VANILLA_SET));

	private HoardersPocket() {
	}

	/** The pocket's 9 slots: Stack Overflow's limit applies (so setItem never trims a big stack). */
	public static final class PocketContainer extends SimpleContainer {
		private final Player owner;
		private final Runnable onChange;

		public PocketContainer(Player owner, Runnable onChange) {
			super(SIZE);
			this.owner = owner;
			this.onChange = onChange;
		}

		@Override
		public int getMaxStackSize(ItemStack stack) {
			return StackOverflow.limit(this.owner, stack, super.getMaxStackSize(stack));
		}

		@Override
		public void setChanged() {
			super.setChanged();

			if (this.onChange != null) {
				this.onChange.run();
			}
		}
	}

	public static void init() {
	}

	/** Storage items (shulker boxes, bundles, backpacks...) can't go in the pocket. */
	public static boolean allowed(ItemStack stack) {
		return stack.isEmpty() || !(stack.is(BloomTags.POCKET_BLOCKED) || stack.has(DataComponents.CONTAINER)
				|| stack.has(DataComponents.BUNDLE_CONTENTS));
	}

	public static NonNullList<ItemStack> items(Player player) {
		NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		player.getAttachedOrElse(CONTENTS, ItemContainerContents.EMPTY).copyInto(items);
		return items;
	}

	public static void store(Player player, NonNullList<ItemStack> items) {
		player.setAttached(CONTENTS, ItemContainerContents.fromItems(items));
	}

	public static boolean isEmpty(Player player) {
		return items(player).stream().allMatch(ItemStack::isEmpty);
	}

	/** Opens the pocket: full access with the Major, take-out-only without it (if anything is left inside). */
	public static void open(ServerPlayer player) {
		boolean canDeposit = BloomServices.progression().isMajorActive(BloomAttachments.profile(player), MiscIds.HOARDERS_POCKET);

		if (!canDeposit && isEmpty(player)) {
			player.sendSystemMessage(Component.translatable("bloom.misc.pocket.locked").withStyle(ChatFormatting.RED), true);
			return;
		}

		Component title = Component.translatable(canDeposit ? "bloom.misc.pocket.title" : "bloom.misc.pocket.title_locked");
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PocketMenu(id, inventory, player, canDeposit), title));
	}

	/** Same slot layout as vanilla's one-row chest (pocket 0-8, then the inventory, then the hotbar). */
	public static final class PocketMenu extends AbstractContainerMenu {
		private final Player owner;
		private final PocketContainer container;
		private final boolean canDeposit;

		public PocketMenu(int id, Inventory inventory, Player owner, boolean canDeposit) {
			super(MENU, id);
			this.owner = owner;
			this.canDeposit = canDeposit;
			NonNullList<ItemStack> stored = items(owner);
			this.container = new PocketContainer(owner, this::save);

			for (int i = 0; i < SIZE; i++) {
				this.container.getItems().set(i, stored.get(i));
			}

			for (int i = 0; i < SIZE; i++) {
				this.addSlot(new Slot(this.container, i, 8 + i * 18, 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return canDeposit && allowed(stack);
					}
				});
			}

			this.addStandardInventorySlots(inventory, 8, 50);
		}

		private void save() {
			NonNullList<ItemStack> copy = NonNullList.withSize(SIZE, ItemStack.EMPTY);

			for (int i = 0; i < SIZE; i++) {
				copy.set(i, this.container.getItem(i).copy());
			}

			store(this.owner, copy);
		}

		public PocketContainer container() {
			return this.container;
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			Slot slot = this.slots.get(index);

			if (!slot.hasItem()) {
				return ItemStack.EMPTY;
			}

			ItemStack stack = slot.getItem();
			ItemStack original = stack.copy();

			// Take-out-only: vanilla's shift-click merge skips mayPlace, so refuse moving anything in here.
			if (index >= SIZE && !this.canDeposit) {
				return ItemStack.EMPTY;
			}

			if (index < SIZE) {
				if (!this.moveItemStackTo(stack, SIZE, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, SIZE, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}

			return original;
		}

		@Override
		public boolean stillValid(Player player) {
			return player == this.owner && player.isAlive();
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			save();
		}
	}
}
