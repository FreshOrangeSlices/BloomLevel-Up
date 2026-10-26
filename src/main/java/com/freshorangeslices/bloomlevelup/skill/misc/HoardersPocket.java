package com.freshorangeslices.bloomlevelup.skill.misc;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.RegistryObject;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.network.BloomSync;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.StoredItems;
import com.freshorangeslices.bloomlevelup.registry.BloomMenus;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;

/**
 * Hoarder's Pocket: 9 personal slots saved on the player, kept through death, dimension travel and Bloom+ /
 * refunds. It opens as a one-row chest screen (its own menu type, so big Stack Overflow stacks show right).
 * Without the Major (refunded, Bloom+, switched off) the pocket still opens to take things out, but not to put
 * things in, so nothing is ever stranded.
 */
public final class HoardersPocket {
	public static final int SIZE = 9;

	/** Where the pocket's items live in the player's Bloom capability (kept through death). */
	private static final String KEY = "pocket";

	/**
	 * The pocket's own menu type. On the client it is a plain one-row chest screen whose container uses the same
	 * per-stack limit as the server, so Stack Overflow's big stacks show (and predict) correctly.
	 */
	public static final RegistryObject<MenuType<ChestMenu>> MENU = BloomMenus.MENUS.register("hoarders_pocket",
			() -> new MenuType<>((id, inventory) -> new ChestMenu(HoardersPocket.MENU.get(), id, inventory, new PocketContainer(inventory.player, null), 1),
					net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

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

		/** The owner (the Slot mixin gives this container Stack Overflow's limit for them). */
		public Player owner() {
			return this.owner;
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

	/**
	 * Storage items (shulker boxes, bundles, backpacks...) can't go in the pocket: the tag, shulker boxes, bundles, and
	 * anything that holds items of its own (an item handler, as modded backpacks have).
	 */
	public static boolean allowed(ItemStack stack) {
		return stack.isEmpty() || !(stack.is(BloomTags.POCKET_BLOCKED) || stack.getItem() instanceof BundleItem
				|| (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock)
				|| stack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent());
	}

	public static NonNullList<ItemStack> items(Player player) {
		return StoredItems.read(player, KEY, SIZE);
	}

	public static void store(Player player, NonNullList<ItemStack> items) {
		StoredItems.write(player, KEY, items);
		markHasItems(player, MiscIds.POCKET_HAS_ITEMS, items);
	}

	/** Keeps a synced "anything inside?" flag, so the button stays (take-out only) after a refund or Bloom+. */
	static void markHasItems(Player player, String counter, List<ItemStack> items) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		long value = items.stream().anyMatch(stack -> !stack.isEmpty()) ? 1 : 0;
		BloomProfile profile = BloomAttachments.profile(serverPlayer);

		if (profile.counter(counter) != value) {
			profile.setCounter(counter, value);
			BloomSync.changed(serverPlayer);
		}
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
			super(MENU.get(), id);
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

			BloomMenus.addInventorySlots(this::addSlot, inventory, 8, 50);
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
				slot.set(ItemStack.EMPTY);
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
