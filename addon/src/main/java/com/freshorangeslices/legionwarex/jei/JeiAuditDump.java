package com.freshorangeslices.legionwarex.jei;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** The audit itself, ten seconds after JEI is running with a player in a world (see {@link JeiAudit}). */
final class JeiAuditDump {
	private static IJeiRuntime runtime;
	private static Path out;
	private static int wait;
	private static boolean listening;
	private static boolean done;

	private JeiAuditDump() {
	}

	static synchronized void arm(IJeiRuntime jei, Path file) {
		runtime = jei;
		out = file;
		wait = 200;

		if (!listening) {
			listening = true;
			MinecraftForge.EVENT_BUS.addListener(JeiAuditDump::onTick);
		}
	}

	private static void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END || done || runtime == null || Minecraft.getInstance().player == null || --wait > 0) {
			return;
		}

		done = true;

		try {
			long start = System.currentTimeMillis();
			int items = write();
			LegionWareX.LOGGER.info("LegionWareX JEI audit: {} items written to {} in {} ms", items, out, System.currentTimeMillis() - start);
		} catch (Throwable e) {
			LegionWareX.LOGGER.warn("LegionWareX JEI audit failed", e);
		}
	}

	private static <R> long count(IRecipeManager recipes, IRecipeCategory<R> category) {
		return recipes.createRecipeLookup(category.getRecipeType()).get().count();
	}

	private static int write() throws Exception {
		Minecraft minecraft = Minecraft.getInstance();
		Map<Item, ItemStack> known = new HashMap<>();

		for (ItemStack stack : runtime.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK)) {
			known.putIfAbsent(stack.getItem(), stack);
		}

		Set<Item> shown = new HashSet<>();

		for (ItemStack stack : runtime.getIngredientFilter().getFilteredItemStacks()) {
			shown.add(stack.getItem());
		}

		Set<Item> inTab = new HashSet<>();

		try {
			CreativeModeTabs.tryRebuildTabContents(minecraft.player.connection.enabledFeatures(), true, minecraft.level.registryAccess());
		} catch (RuntimeException e) {
			LegionWareX.LOGGER.warn("LegionWareX JEI audit: creative tabs not rebuilt ({})", e.toString());
		}

		for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
			for (ItemStack stack : tab.getDisplayItems()) {
				inTab.add(stack.getItem());
			}
		}

		IRecipeManager recipes = runtime.getRecipeManager();
		IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();
		JsonObject root = new JsonObject();
		JsonArray categories = new JsonArray();

		for (IRecipeCategory<?> category : recipes.createRecipeCategoryLookup().get().toList()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("id", category.getRecipeType().getUid().toString());
			entry.addProperty("title", category.getTitle().getString());
			entry.addProperty("recipes", count(recipes, category));
			categories.add(entry);
		}

		root.add("categories", categories);
		JsonArray list = new JsonArray();
		int n = 0;

		for (Item item : ForgeRegistries.ITEMS) {
			ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);

			if (item == Items.AIR || id == null) {
				continue;
			}

			JsonObject entry = new JsonObject();
			entry.addProperty("id", id.toString());
			entry.addProperty("known", known.containsKey(item));
			entry.addProperty("shown", shown.contains(item));
			entry.addProperty("tab", inTab.contains(item));

			if (known.containsKey(item)) {
				ItemStack stack = known.get(item);
				JsonArray made = new JsonArray();
				JsonArray used = new JsonArray();
				IFocus<ItemStack> output = focuses.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
				IFocus<ItemStack> input = focuses.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);
				recipes.createRecipeCategoryLookup().limitFocus(List.of(output)).get().forEach(c -> made.add(c.getRecipeType().getUid().toString()));
				recipes.createRecipeCategoryLookup().limitFocus(List.of(input)).get().forEach(c -> used.add(c.getRecipeType().getUid().toString()));
				entry.add("made", made);
				entry.add("used", used);
			}

			list.add(entry);
			n++;
		}

		root.add("items", list);
		Files.createDirectories(out.toAbsolutePath().getParent());
		Files.writeString(out, new GsonBuilder().create().toJson(root), StandardCharsets.UTF_8);
		return n;
	}
}
