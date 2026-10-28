package com.freshorangeslices.bloomlevelup.client.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.compat.BetterCombat;

/**
 * A tool for supporting modpacks, off unless the game starts with {@code -Dbloomlevelup.tooltips=<file>}: ten seconds
 * after the player is in a world it writes every weapon-like item's tooltip as a player sees it, plain, with Shift held
 * and with Ctrl held (many mods put a weapon's ability behind one of those), one JSON object per line. The server's
 * registry dump can't read these: most mods build tooltips with client-only code. CI runs it on LegionWareX to sort the
 * pack's weapons into ones with a skill, an effect, or neither.
 */
public final class TooltipDump {
	/** Held down for the tooltip being read (see {@code ScreenModifierKeysMixin}); never set otherwise. */
	public static boolean shiftDown;
	public static boolean controlDown;

	private static final Gson GSON = new Gson();

	private TooltipDump() {
	}

	public static void init() {
		String target = System.getProperty("bloomlevelup.tooltips");

		if (target == null || target.isBlank()) {
			return;
		}

		int[] ticks = {0};
		boolean[] done = {false};
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase != TickEvent.Phase.END || done[0]) {
				return;
			}

			Minecraft minecraft = Minecraft.getInstance();

			if (minecraft.player == null || minecraft.level == null) {
				ticks[0] = 0;
			} else if (++ticks[0] == 200) {
				done[0] = true;
				write(minecraft, Path.of(target));
			}
		});
	}

	private static void write(Minecraft minecraft, Path target) {
		List<String> lines = new ArrayList<>();

		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);

			if (!weaponLike(item, stack)) {
				continue;
			}

			JsonObject out = new JsonObject();
			out.addProperty("id", BuiltInRegistries.ITEM.getKey(item).toString());
			out.add("plain", tooltip(minecraft, stack, false, false));
			out.add("shift", tooltip(minecraft, stack, true, false));
			out.add("control", tooltip(minecraft, stack, false, true));
			lines.add(GSON.toJson(out));
		}

		try {
			Files.write(target, lines, StandardCharsets.UTF_8);
			BloomLevelUp.LOGGER.info("Bloom tooltip dump: {} weapons -> {}", lines.size(), target.toAbsolutePath());
		} catch (IOException e) {
			BloomLevelUp.LOGGER.warn("Bloom tooltip dump couldn't be written to {}", target, e);
		}
	}

	/** The same items the server's registry dump describes in full. */
	private static boolean weaponLike(Item item, ItemStack stack) {
		return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof ProjectileWeaponItem
				|| stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE)
				|| BetterCombat.describe(stack) != null;
	}

	private static JsonArray tooltip(Minecraft minecraft, ItemStack stack, boolean shift, boolean control) {
		JsonArray out = new JsonArray();
		shiftDown = shift;
		controlDown = control;

		try {
			for (Component line : stack.getTooltipLines(minecraft.player, TooltipFlag.NORMAL)) {
				out.add(line.getString());
			}
		} catch (Throwable e) {
			out.add("!error " + e);
		} finally {
			shiftDown = false;
			controlDown = false;
		}

		return out;
	}
}
