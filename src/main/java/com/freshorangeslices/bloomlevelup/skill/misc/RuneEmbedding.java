package com.freshorangeslices.bloomlevelup.skill.misc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;

/**
 * Rune Embedding: five enchanted-book slots on the player. Slots 1-4 are body runes, which work only while all
 * four armor slots are empty; slot 5 is the bare-hand rune, which works only with an empty main hand. Books are
 * never used up. Curses, duplicate enchantments and anything outside the rune tags are refused.
 * <p>
 * How the effects reach vanilla: the body runes are folded into one invisible item per armor slot, which the
 * game's enchantment code visits as if worn (protection, thorns, feather falling...), plus their attribute
 * effects (respiration, swift sneak...). The bare-hand rune becomes the "weapon" of a bare-handed attack
 * (sharpness, knockback, fire aspect...). Looting and anything else asked by level read the runes too.
 */
public final class RuneEmbedding {
	public static final int SIZE = 5;
	public static final int UNARMED_SLOT = 4;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public static final AttachmentType<ItemContainerContents> RUNES = AttachmentRegistry.create(BloomLevelUp.id("runes"),
			builder -> builder.persistent(ItemContainerContents.CODEC).initializer(() -> ItemContainerContents.EMPTY).copyOnDeath());

	/** What the runes add up to, rebuilt only when the stored runes change. */
	private record Built(ItemContainerContents source, Map<EquipmentSlot, ItemStack> body, ItemStack unarmed) {
	}

	private static final Map<Player, Built> BUILT = new WeakHashMap<>();
	private static final Map<Player, Multimap<Holder<Attribute>, AttributeModifier>> APPLIED = new WeakHashMap<>();

	private RuneEmbedding() {
	}

	public static void init() {
	}

	// ------------------------------------------------------------------ what may be embedded

	private static ItemEnchantments stored(ItemStack stack) {
		return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	/**
	 * Whether a book may go in this rune slot, given what the other slots hold: an enchanted book whose every
	 * enchantment is in the slot's rune tag, none a curse, none already embedded elsewhere.
	 */
	public static boolean valid(int slot, ItemStack stack, List<ItemStack> others) {
		if (!stack.is(Items.ENCHANTED_BOOK) || stored(stack).isEmpty()) {
			return false;
		}

		for (Holder<Enchantment> enchantment : stored(stack).keySet()) {
			if (enchantment.is(EnchantmentTags.CURSE) || !enchantment.is(slot == UNARMED_SLOT ? BloomTags.RUNE_UNARMED : BloomTags.RUNE_BODY)) {
				return false;
			}

			for (ItemStack other : others) {
				if (stored(other).getLevel(enchantment) > 0) {
					return false; // duplicates are blocked: swap the old rune out first
				}
			}
		}

		return true;
	}

	public static NonNullList<ItemStack> runes(Player player) {
		NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		player.getAttachedOrElse(RUNES, ItemContainerContents.EMPTY).copyInto(items);
		return items;
	}

	public static void store(Player player, NonNullList<ItemStack> items) {
		player.setAttached(RUNES, ItemContainerContents.fromItems(items));
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
		ItemContainerContents source = player.getAttachedOrElse(RUNES, ItemContainerContents.EMPTY);
		Built cached = BUILT.get(player);

		if (cached != null && cached.source() == source) {
			return cached;
		}

		NonNullList<ItemStack> runes = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		source.copyInto(runes);
		Map<EquipmentSlot, ItemEnchantments.Mutable> body = new EnumMap<>(EquipmentSlot.class);
		ItemEnchantments.Mutable unarmed = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

		for (int i = 0; i < SIZE; i++) {
			for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored(runes.get(i)).entrySet()) {
				Holder<Enchantment> enchantment = entry.getKey();
				int level = entry.getIntValue();

				if (i == UNARMED_SLOT) {
					if (enchantment.value().matchingSlot(EquipmentSlot.MAINHAND)) {
						unarmed.set(enchantment, Math.max(level, unarmed.getLevel(enchantment)));
					}

					continue;
				}

				// Body runes aren't tied to a piece: each enchantment sits in the first armor slot it works in.
				for (EquipmentSlot slot : ARMOR) {
					if (enchantment.value().matchingSlot(slot)) {
						ItemEnchantments.Mutable onSlot = body.computeIfAbsent(slot, key -> new ItemEnchantments.Mutable(ItemEnchantments.EMPTY));
						onSlot.set(enchantment, Math.max(level, onSlot.getLevel(enchantment)));
						break;
					}
				}
			}
		}

		Map<EquipmentSlot, ItemStack> bodyStacks = new EnumMap<>(EquipmentSlot.class);
		body.forEach((slot, enchantments) -> bodyStacks.put(slot, virtual(enchantments.toImmutable())));
		ItemEnchantments unarmedEnchantments = unarmed.toImmutable();
		Built result = new Built(source, bodyStacks, unarmedEnchantments.isEmpty() ? ItemStack.EMPTY : virtual(unarmedEnchantments));
		BUILT.put(player, result);
		return result;
	}

	/** An invisible carrier for enchantments (never placed anywhere; a book can't be damaged or broken). */
	private static ItemStack virtual(ItemEnchantments enchantments) {
		ItemStack stack = new ItemStack(Items.BOOK);
		stack.set(DataComponents.ENCHANTMENTS, enchantments);
		return stack;
	}

	// ------------------------------------------------------------------ hooks (EnchantmentHelper / Player mixins)

	/** Body runes join the equipment the game iterates (protection, thorns, feather falling...). */
	public static void visitBody(LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor visitor) {
		if (!(entity instanceof ServerPlayer player) || !bodyActive(player)) {
			return;
		}

		for (Map.Entry<EquipmentSlot, ItemStack> entry : built(player).body().entrySet()) {
			EnchantmentHelper.runIterationOnItem(entry.getValue(), entry.getKey(), entity, visitor);
		}
	}

	/** Enchantment level the runes give this entity (body while armorless, bare-hand while empty-handed). */
	public static int level(Holder<Enchantment> enchantment, LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || !majorActive(player)) {
			return 0;
		}

		Built built = built(player);
		int level = 0;

		if (bodyActive(player)) {
			for (ItemStack stack : built.body().values()) {
				level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack));
			}
		}

		if (unarmedActive(player) && !built.unarmed().isEmpty()) {
			level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(enchantment, built.unarmed()));
		}

		return level;
	}

	/** A bare-handed attack's "weapon" carries the bare-hand rune (sharpness, knockback, fire aspect...). */
	public static ItemStack weaponItem(Player player, ItemStack original) {
		if (!original.isEmpty() || !(player instanceof ServerPlayer) || !unarmedActive(player)) {
			return original;
		}

		ItemStack unarmed = built(player).unarmed();
		return unarmed.isEmpty() ? original : unarmed.copy();
	}

	/** Attribute effects of the runes (respiration, swift sneak, depth strider, sweeping edge...). */
	public static void refresh(ServerPlayer player) {
		Multimap<Holder<Attribute>, AttributeModifier> wanted = HashMultimap.create();

		if (majorActive(player)) {
			Built built = built(player);

			if (bodyActive(player)) {
				built.body().forEach((slot, stack) -> EnchantmentHelper.forEachModifier(stack, slot,
						(attribute, modifier) -> wanted.put(attribute, renamed(slot, modifier))));
			}

			if (unarmedActive(player) && !built.unarmed().isEmpty()) {
				EnchantmentHelper.forEachModifier(built.unarmed(), EquipmentSlot.MAINHAND,
						(attribute, modifier) -> wanted.put(attribute, renamed(EquipmentSlot.MAINHAND, modifier)));
			}
		}

		Multimap<Holder<Attribute>, AttributeModifier> applied = APPLIED.get(player);

		if (wanted.equals(applied)) {
			return;
		}

		if (applied != null) {
			player.getAttributes().removeAttributeModifiers(applied);
		}

		player.getAttributes().addTransientAttributeModifiers(wanted);
		APPLIED.put(player, wanted);
	}

	/** Our own ids, so a rune's modifier never collides with the same enchantment on real gear. */
	private static AttributeModifier renamed(EquipmentSlot slot, AttributeModifier modifier) {
		return new AttributeModifier(BloomLevelUp.id("rune/" + slot.getSerializedName() + "/" + modifier.id().getNamespace() + "/" + modifier.id().getPath()),
				modifier.amount(), modifier.operation());
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
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new RuneMenu(id, inventory, player, canEmbed), title));
	}

	/** Five slots like a hopper (runes 1-4 = body, 5 = bare hand), then the inventory and hotbar. */
	public static final class RuneMenu extends AbstractContainerMenu {
		private final Player owner;
		private final boolean canEmbed;
		private final SimpleContainer container;

		public RuneMenu(int id, Inventory inventory, Player owner, boolean canEmbed) {
			super(MenuType.HOPPER, id);
			this.owner = owner;
			this.canEmbed = canEmbed;
			NonNullList<ItemStack> stored = runes(owner);
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
				});
			}

			this.addStandardInventorySlots(inventory, 8, 51);
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
			NonNullList<ItemStack> copy = NonNullList.withSize(SIZE, ItemStack.EMPTY);

			for (int i = 0; i < SIZE; i++) {
				copy.set(i, this.container.getItem(i).copy());
			}

			store(this.owner, copy);

			if (this.owner instanceof ServerPlayer player) {
				refresh(player);
			}
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
