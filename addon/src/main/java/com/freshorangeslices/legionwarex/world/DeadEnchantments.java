package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Enchantments switched off (FreshOrangeSlices, 2026-11-22: "switch off the dead enchantments"): ones that do nothing in
 * the pack, by default Spell Engine's Spell Infinity (no spell in the pack costs an item). Skarrier Mobs' Bluntness, Mass
 * and Mobility were too until its Morgenstern came back (0.6.2, 2026-11-23). Their mods have no setting for them, so:
 * <ul>
 *   <li>the enchanting table never offers one (books too), nor does anything else that enchants by level (a mob's
 *   gear, enchant_with_levels loot): {@code mixin/EnchantmentHelperMixin} drops them from vanilla's list of
 *   enchantments to pick from;</li>
 *   <li>villagers and the wandering trader never sell one: each of their trades is asked again (up to 8 times) when
 *   it would, and offers nothing if it still does;</li>
 *   <li>loot never carries one ({@link DeadEnchantmentLoot}, a global loot modifier): it's taken off, and a book left
 *   with none becomes a plain book.</li>
 * </ul>
 * The list is the config's {@code switchedOffEnchantments}. {@code /legionwarex enchantments} rolls the table, the librarian's
 * book trade and a loot book many times and logs what it found as "LegionWareX enchantments:" (the pack test reads it).
 */
public final class DeadEnchantments {
	private static final int TRADE_TRIES = 8;
	/** Extra ones for the game tests (the default list's mods aren't in the add-on's test run). */
	public static final Set<ResourceLocation> TEST_EXTRA = new java.util.concurrent.CopyOnWriteArraySet<>();
	private static List<? extends String> cachedList;
	private static Set<ResourceLocation> cached = Set.of();

	private DeadEnchantments() {
	}

	public static Set<ResourceLocation> ids() {
		List<? extends String> list = AddonConfig.deadEnchantments();

		if (list != cachedList) {
			cached = list.stream().map(ResourceLocation::tryParse).filter(java.util.Objects::nonNull).collect(Collectors.toUnmodifiableSet());
			cachedList = list;
		}

		return cached;
	}

	public static boolean isDead(Enchantment enchantment) {
		if (enchantment == null) {
			return false;
		}

		ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
		return id != null && (ids().contains(id) || !TEST_EXTRA.isEmpty() && TEST_EXTRA.contains(id));
	}

	public static boolean hasDead(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.isEnchanted() && !stack.is(Items.ENCHANTED_BOOK)) {
			return false;
		}

		for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
			if (isDead(enchantment)) {
				return true;
			}
		}

		return false;
	}

	/** The stack without its switched-off enchantments (a book left with none is a plain book); itself if it has none. */
	public static ItemStack strip(ItemStack stack) {
		if (!hasDead(stack)) {
			return stack;
		}

		Map<Enchantment, Integer> kept = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(stack));
		kept.keySet().removeIf(DeadEnchantments::isDead);

		if (stack.is(Items.ENCHANTED_BOOK)) {
			if (kept.isEmpty()) {
				return new ItemStack(Items.BOOK, stack.getCount());
			}

			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK, stack.getCount());
			kept.forEach((enchantment, level) -> EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(enchantment, level)));
			return book;
		}

		ItemStack copy = stack.copy();
		EnchantmentHelper.setEnchantments(kept, copy);
		return copy;
	}

	// ------------------------------------------------------------------ trades

	/** A trade asked again while it would sell (or take) something with a switched-off enchantment. */
	public record Clean(VillagerTrades.ItemListing inner) implements VillagerTrades.ItemListing {
		@Override
		public MerchantOffer getOffer(Entity trader, RandomSource random) {
			for (int i = 0; i < TRADE_TRIES; i++) {
				MerchantOffer offer = inner.getOffer(trader, random);

				if (offer == null || !hasDead(offer.getResult()) && !hasDead(offer.getBaseCostA()) && !hasDead(offer.getCostB())) {
					return offer;
				}
			}

			return null;
		}
	}

	static void clean(List<VillagerTrades.ItemListing> listings) {
		listings.replaceAll(listing -> listing instanceof Clean ? listing : new Clean(listing));
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onVillagerTrades(VillagerTradesEvent event) {
		event.getTrades().values().forEach(DeadEnchantments::clean);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onWandererTrades(WandererTradesEvent event) {
		clean(event.getGenericTrades());
		clean(event.getRareTrades());
	}

	// ------------------------------------------------------------------ the check

	/** {@code /legionwarex enchantments}: rolls the table, the librarian's book trade and a loot book, and says what came. */
	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("enchantments").requires(source -> source.hasPermission(2)).executes(context -> {
			CommandSourceStack source = context.getSource();
			String text = check(source.getLevel());
			LegionWareX.LOGGER.info("LegionWareX enchantments: {}", text);
			source.sendSuccess(() -> Component.literal(text), false);
			return 1;
		});
	}

	/**
	 * Vanilla's librarian enchanted-book trade, unwrapped: the librarian's trade that sells enchanted books (its class
	 * isn't public, and Forge puts the trades back after its event, wrapped), or, if none does, one made from its class.
	 */
	public static VillagerTrades.ItemListing librarianBooks(Entity trader) {
		RandomSource random = RandomSource.create(7L);
		Int2ObjectMap<VillagerTrades.ItemListing[]> librarian = VillagerTrades.TRADES.get(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN);

		if (librarian != null) {
			for (VillagerTrades.ItemListing[] level : librarian.values()) {
				for (VillagerTrades.ItemListing listing : level) {
					for (int i = 0; i < 4; i++) {
						try {
							MerchantOffer offer = listing.getOffer(trader, random);

							// Forge writes the trades back after its event, so the table holds the add-on's wrappers.
							if (offer != null && offer.getResult().is(Items.ENCHANTED_BOOK)) {
								return listing instanceof Clean clean ? clean.inner() : listing;
							}
						} catch (RuntimeException ignored) {
							break;
						}
					}
				}
			}
		}

		try {
			java.lang.reflect.Constructor<?> make = Class.forName("net.minecraft.world.entity.npc.VillagerTrades$EnchantBookForEmeralds").getDeclaredConstructor(int.class);
			make.setAccessible(true);
			return (VillagerTrades.ItemListing) make.newInstance(1);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LegionWareX.LOGGER.warn("LegionWareX enchantments: no librarian book trade found ({})", e.toString());
			return null;
		}
	}

	/** The check's findings, as one line. Public for the game tests. */
	public static String check(net.minecraft.server.level.ServerLevel level) {
		RandomSource random = RandomSource.create(2026L);
		List<String> present = new ArrayList<>();
		List<String> absent = new ArrayList<>();

		for (ResourceLocation id : ids()) {
			(ForgeRegistries.ENCHANTMENTS.containsKey(id) ? present : absent).add(id.toString());
		}

		int tableDead = 0;
		int tableRolls = 2000;

		for (int i = 0; i < tableRolls; i++) {
			ItemStack stack = new ItemStack(i % 2 == 0 ? Items.BOOK : Items.IRON_SWORD);

			for (EnchantmentInstance instance : EnchantmentHelper.selectEnchantment(random, stack, 1 + random.nextInt(30), i % 4 == 0)) {
				if (isDead(instance.enchantment)) {
					tableDead++;
				}
			}
		}

		Entity trader = EntityType.VILLAGER.create(level);
		VillagerTrades.ItemListing books = librarianBooks(trader);

		if (books == null) {
			return "no librarian book trade found to check";
		}

		Clean cleaned = new Clean(books);
		int tradeRolls = 500;
		int rawDead = 0;
		int tradeDead = 0;

		for (int i = 0; i < tradeRolls; i++) {
			MerchantOffer raw = books.getOffer(trader, random);
			MerchantOffer offer = cleaned.getOffer(trader, random);

			if (raw != null && hasDead(raw.getResult())) {
				rawDead++;
			}

			if (offer != null && hasDead(offer.getResult())) {
				tradeDead++;
			}
		}

		// Loot: a book carrying every switched-off enchantment there is and Unbreaking keeps only Unbreaking.
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1));

		for (String id : present) {
			EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(id)), 1));
		}

		ItemStack stripped = strip(book);
		boolean lootOk = !hasDead(stripped) && EnchantmentHelper.getEnchantments(stripped).containsKey(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);

		return String.format(java.util.Locale.ROOT, "switched off: %s%s; the table: %d of %d rolls had one; trades: %d of %d books had one "
				+ "(%d asked plainly); loot: %s", present.isEmpty() ? "none in this game" : String.join(", ", present),
				absent.isEmpty() ? "" : " (not in this game: " + String.join(", ", absent) + ")", tableDead, tableRolls, tradeDead, tradeRolls,
				rawDead, lootOk ? "a book loses them, keeping the rest" : "NOT taken off");
	}
}
