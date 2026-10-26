package com.freshorangeslices.bloomlevelup.skill.misc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.datafixers.util.Pair;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.RegistryObject;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.StoredItems;
import com.freshorangeslices.bloomlevelup.registry.BloomMenus;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;

/**
 * Rune Embedding: five enchanted-book slots on the player. Slots 1-4 are body runes, which work only while all
 * four armor slots are empty; slot 5 is the bare-hand rune, which works only with an empty main hand. Books are
 * never used up. Curses, duplicate enchantments and anything outside the rune tags are refused.
 * <p>
 * How the effects reach vanilla 1.20.1 (enchantments there are asked for by level, not visited as effects): every
 * "what level of X does this entity have" answer includes the runes (respiration, depth strider, swift sneak, fire
 * aspect, knockback, looting...), protection adds the body runes' protection, thorns and the bare-hand rune's
 * on-hit enchantments run after a hit, and a bare-handed attack's damage bonus (sharpness, smite...) comes from the
 * bare-hand rune.
 */
public final class RuneEmbedding {
	public static final int SIZE = 5;
	public static final int UNARMED_SLOT = 4;
	private static final String KEY = "runes";
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** What the runes add up to, rebuilt only when the stored runes change. */
	private record Built(List<ItemStack> source, Map<Enchantment, Integer> body, Map<Enchantment, Integer> unarmed, ItemStack unarmedStack) {
	}

	private static final Map<Player, Built> BUILT = new WeakHashMap<>();

	private RuneEmbedding() {
	}

	/** Its own screen type, so the slots can show what goes in them (the client draws it with RuneScreen). */
	public static final RegistryObject<MenuType<RuneMenu>> MENU = BloomMenus.MENUS.register("rune_embedding",
			() -> new MenuType<>((id, inventory) -> new RuneMenu(id, inventory, inventory.player,
					com.freshorangeslices.bloomlevelup.skill.SideBridge.majorActive(inventory.player, MiscIds.RUNE_EMBEDDING)),
					FeatureFlags.VANILLA_SET));

	/** Empty-slot pictures: the four armor outlines for the body runes, a sword outline for the bare-hand rune. */
	private static final ResourceLocation[] SLOT_ICONS = {
			InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
			InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
			InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
			InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
			new ResourceLocation("item/empty_slot_sword")
	};

	/** Whether a slot of the rune screen is a body rune (0-3) rather than the bare-hand rune (4). */
	public static boolean isBodySlot(int index) {
		return index >= 0 && index < UNARMED_SLOT;
	}

	// ------------------------------------------------------------------ what may be embedded

	private static Map<Enchantment, Integer> stored(ItemStack stack) {
		return stack.isEmpty() ? Map.of() : EnchantmentHelper.getEnchantments(stack);
	}

	/**
	 * Whether a book may go in this rune slot, given what the other slots hold: an enchanted book whose every
	 * enchantment is in the slot's rune tag, none a curse, none already embedded elsewhere.
	 */
	public static boolean valid(int slot, ItemStack stack, List<ItemStack> others) {
		if (!stack.is(Items.ENCHANTED_BOOK) || stored(stack).isEmpty()) {
			return false;
		}

		for (Enchantment enchantment : stored(stack).keySet()) {
			if (enchantment.isCurse() || !BloomTags.is(enchantment, slot == UNARMED_SLOT ? BloomTags.RUNE_UNARMED : BloomTags.RUNE_BODY)) {
				return false;
			}

			for (ItemStack other : others) {
				if (stored(other).getOrDefault(enchantment, 0) > 0) {
					return false; // duplicates are blocked: swap the old rune out first
				}
			}
		}

		return true;
	}

	public static NonNullList<ItemStack> runes(Player player) {
		return StoredItems.read(player, KEY, SIZE);
	}

	public static void store(Player player, NonNullList<ItemStack> items) {
		StoredItems.write(player, KEY, items);
		BUILT.remove(player);
		HoardersPocket.markHasItems(player, MiscIds.RUNES_HAS_ITEMS, items);
	}

	// ------------------------------------------------------------------ when the runes work

	private static boolean majorActive(Player player) {
		return player instanceof ServerPlayer serverPlayer
				&& BloomServices.progression().isMajorActive(BloomAttachments.profile(serverPlayer), MiscIds.RUNE_EMBEDDING);
	}

	public static boolean bodyActive(Player player) {
		if (!majorActive(player)) {
			return false;
		}

		for (EquipmentSlot slot : ARMOR) {
			if (!player.getItemBySlot(slot).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	public static boolean unarmedActive(Player player) {
		return majorActive(player) && player.getMainHandItem().isEmpty();
	}

	private static Built built(Player player) {
		Built cached = BUILT.get(player);

		if (cached != null) {
			return cached;
		}

		NonNullList<ItemStack> runes = runes(player);
		Map<Enchantment, Integer> body = new LinkedHashMap<>();
		Map<Enchantment, Integer> unarmed = new LinkedHashMap<>();

		for (int i = 0; i < SIZE; i++) {
			for (Map.Entry<Enchantment, Integer> entry : stored(runes.get(i)).entrySet()) {
				Map<Enchantment, Integer> into = i == UNARMED_SLOT ? unarmed : body;
				into.merge(entry.getKey(), entry.getValue(), Math::max);
			}
		}

		ItemStack unarmedStack = ItemStack.EMPTY;

		if (!unarmed.isEmpty()) {
			// An invisible carrier for the bare-hand rune (never placed anywhere): the attack's "weapon".
			unarmedStack = new ItemStack(Items.BOOK);
			EnchantmentHelper.setEnchantments(unarmed, unarmedStack);
		}

		Built result = new Built(runes, Map.copyOf(body), Map.copyOf(unarmed), unarmedStack);
		BUILT.put(player, result);
		return result;
	}

	// ------------------------------------------------------------------ hooks (EnchantmentHelper / Player mixins)

	/** Enchantment level the runes give this entity (body while armorless, bare-hand while empty-handed). */
	public static int level(Enchantment enchantment, LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || !majorActive(player)) {
			return 0;
		}

		Built built = built(player);
		int level = 0;

		if (bodyActive(player)) {
			level = Math.max(level, built.body().getOrDefault(enchantment, 0));
		}

		if (unarmedActive(player)) {
			level = Math.max(level, built.unarmed().getOrDefault(enchantment, 0));
		}

		return level;
	}

	/** Protection the body runes give against {@code source} (added to what worn armor gives). */
	public static int protection(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player) || !bodyActive(player)) {
			return 0;
		}

		int total = 0;

		for (Map.Entry<Enchantment, Integer> entry : built(player).body().entrySet()) {
			total += entry.getKey().getDamageProtection(entry.getValue(), source);
		}

		return total;
	}

	/** After the wearer is hit: the body runes' answers (thorns). */
	public static void afterHurt(LivingEntity user, Entity attacker) {
		if (attacker == null || !(user instanceof ServerPlayer player) || !bodyActive(player)) {
			return;
		}

		for (Map.Entry<Enchantment, Integer> entry : built(player).body().entrySet()) {
			entry.getKey().doPostHurt(user, attacker, entry.getValue());
		}
	}

	/** After a bare-handed hit lands: the bare-hand rune's on-hit enchantments (bane of arthropods' slowness...). */
	public static void afterAttack(LivingEntity user, Entity target) {
		if (!(user instanceof ServerPlayer player) || !unarmedActive(player)) {
			return;
		}

		for (Map.Entry<Enchantment, Integer> entry : built(player).unarmed().entrySet()) {
			entry.getKey().doPostAttack(user, target, entry.getValue());
		}
	}

	/** A bare-handed attack's "weapon" for its damage bonus: the bare-hand rune (sharpness, smite...). */
	public static ItemStack weaponItem(Player player, ItemStack original) {
		if (!original.isEmpty() || !(player instanceof ServerPlayer) || !unarmedActive(player)) {
			return original;
		}

		ItemStack unarmed = built(player).unarmedStack();
		return unarmed.isEmpty() ? original : unarmed;
	}

	// ------------------------------------------------------------------ the rune screen

	/** Opens the runes: full access with the Major, take-out-only without it (if anything is embedded). */
	public static void open(ServerPlayer player) {
		boolean canEmbed = majorActive(player);

		if (!canEmbed && runes(player).stream().allMatch(ItemStack::isEmpty)) {
			player.sendSystemMessage(Component.translatable("bloom.misc.runes.locked").withStyle(ChatFormatting.RED), true);
			return;
		}

		Component title = Component.translatable(canEmbed ? "bloom.misc.runes.title" : "bloom.misc.runes.title_locked");
		// (The empty slots show what goes where; the client explains them on hover.)
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new RuneMenu(id, inventory, player, canEmbed), title));
	}

	/** Five slots like a hopper (runes 1-4 = body, 5 = bare hand), then the inventory and hotbar. */
	public static final class RuneMenu extends AbstractContainerMenu {
		/** The rune slots come first; the player's inventory follows. */
		public static final int SLOTS = SIZE;
		private final Player owner;
		private final boolean canEmbed;
		private final SimpleContainer container;

		public RuneMenu(int id, Inventory inventory, Player owner, boolean canEmbed) {
			super(MENU.get(), id);
			this.owner = owner;
			this.canEmbed = canEmbed;
			NonNullList<ItemStack> stored = owner.level().isClientSide() ? NonNullList.withSize(SIZE, ItemStack.EMPTY) : runes(owner);
			this.container = new SimpleContainer(SIZE) {
				@Override
				public void setChanged() {
					super.setChanged();
					save();
				}
			};

			for (int i = 0; i < SIZE; i++) {
				this.container.getItems().set(i, stored.get(i));
			}

			for (int i = 0; i < SIZE; i++) {
				int index = i;
				this.addSlot(new Slot(this.container, i, 44 + i * 18, 20) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return RuneMenu.this.canEmbed && valid(index, stack, others(index));
					}

					@Override
					public int getMaxStackSize() {
						return 1;
					}

					@Override
					public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
						return Pair.of(InventoryMenu.BLOCK_ATLAS, SLOT_ICONS[index]);
					}
				});
			}

			BloomMenus.addInventorySlots(this::addSlot, inventory, 8, 51);
		}

		private List<ItemStack> others(int index) {
			List<ItemStack> others = new ArrayList<>(SIZE - 1);

			for (int i = 0; i < SIZE; i++) {
				if (i != index) {
					others.add(this.container.getItem(i));
				}
			}

			return others;
		}

		private void save() {
			if (this.owner.level().isClientSide()) {
				return; // the client's copy of the screen never writes the runes
			}

			NonNullList<ItemStack> copy = NonNullList.withSize(SIZE, ItemStack.EMPTY);

			for (int i = 0; i < SIZE; i++) {
				copy.set(i, this.container.getItem(i).copy());
			}

			store(this.owner, copy);
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			Slot slot = this.slots.get(index);

			if (!slot.hasItem() || (index >= SIZE && !this.canEmbed)) {
				return ItemStack.EMPTY;
			}

			ItemStack stack = slot.getItem();
			ItemStack original = stack.copy();

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
