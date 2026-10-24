package com.freshorangeslices.bloomlevelup.sheet;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;

/**
 * The stat sheet (the hub's Stats button): the client asks for a tab, an inventory slot, a target and which
 * switches are on; the server works everything out from the real game rules and answers with finished lines.
 * The client never computes a number itself.
 */
public final class StatSheets {
	/** Requests a player may send per second (the hub asks once a second, and again on every click). */
	private static final int PER_SECOND = 20;
	private static final Map<UUID, long[]> BUDGET = new HashMap<>();

	private StatSheets() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(SheetPayloads.Request.TYPE, SheetPayloads.Request.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SheetPayloads.View.TYPE, SheetPayloads.View.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SheetPayloads.Request.TYPE, (payload, context) -> handle(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> BUDGET.remove(listener.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BUDGET.clear();
			Dummies.clear();
		});
	}

	private static void handle(ServerPlayer player, SheetPayloads.Request request) {
		long second = player.level().getGameTime() / 20L;
		long[] budget = BUDGET.computeIfAbsent(player.getUUID(), key -> new long[] {second, 0L});

		if (budget[0] != second) {
			budget[0] = second;
			budget[1] = 0L;
		}

		if (++budget[1] > PER_SECOND || !ServerPlayNetworking.canSend(player, SheetPayloads.View.TYPE)) {
			return;
		}

		ServerPlayNetworking.send(player, build(player, request));
	}

	/** The sheet for a request (public for game tests). */
	public static SheetPayloads.View build(ServerPlayer player, SheetPayloads.Request request) {
		ItemStack item = item(player, request.slot());
		Sheet sheet = new Sheet(Set.copyOf(request.toggles()), request.target());
		String category = request.category();

		try {
			if (category.equals(SkillXp.COMBAT)) {
				CombatSheet.build(player, item, sheet);
			} else if (category.equals(SkillXp.ARMOR)) {
				ArmorSheet.build(player, sheet);
			} else if (category.equals(SkillXp.UTILITY)) {
				UtilitySheet.build(player, item, sheet);
			} else if (category.equals(SkillXp.MISC)) {
				MiscSheet.build(player, sheet);
			} else {
				sheet.summary(Sheet.word("value.none"), "unknown");
			}
		} catch (RuntimeException e) {
			BloomLevelUp.LOGGER.error("Bloom stat sheet for {} failed", category, e);
			sheet.summary.clear();
			sheet.details.clear();
			sheet.summary(Sheet.word("value.error"), "error");
		}

		return new SheetPayloads.View(request.id(), category, request.slot(), item.copy(), sheet.targets, sheet.target, sheet.toggles, sheet.gauge,
				sheet.gaugeCaption, sheet.gaugeBadge, sheet.summary, sheet.details);
	}

	/** -1 = the main hand; -2 = bare hands; 0-35 the inventory (0-8 the hotbar); 40 the off hand. */
	static ItemStack item(ServerPlayer player, int slot) {
		Inventory inventory = player.getInventory();

		if (slot == -2) {
			return ItemStack.EMPTY;
		}

		if (slot < 0 || slot >= inventory.getContainerSize()) {
			return player.getMainHandItem();
		}

		return inventory.getItem(slot);
	}
}
