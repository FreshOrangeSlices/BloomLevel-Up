package com.freshorangeslices.bloomlevelup.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.client.combat.CombatControls;
import com.freshorangeslices.bloomlevelup.client.gui.ClientScreens;
import com.freshorangeslices.bloomlevelup.entity.BloomEntities;
import com.freshorangeslices.bloomlevelup.client.gui.HubButton;
import com.freshorangeslices.bloomlevelup.client.gui.HubScreen;
import com.freshorangeslices.bloomlevelup.client.gui.SideButton;
import com.freshorangeslices.bloomlevelup.item.BloomComponents;
import com.freshorangeslices.bloomlevelup.item.FuelImprint;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.network.SnapshotCodecs;
import com.freshorangeslices.bloomlevelup.skill.SideBridge;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscIds;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/**
 * Client entrypoint: receives snapshots, adds the hub button to the inventory screen and registers the
 * (unbound by default) hub key. The client never owns progression; it renders what the server sent.
 */
public final class BloomLevelUpClient implements ClientModInitializer {
	private static KeyMapping openHubKey;
	private static KeyMapping openPocketKey;
	private static KeyMapping openRunesKey;

	@Override
	public void onInitializeClient() {
		// Lets shared code (Unrestricted Riptide's trident use) ask about the local player's Majors.
		SideBridge.setClientConfig(ClientBloomState::config);
		SideBridge.setClientMajorActive((player, majorId) -> player == Minecraft.getInstance().player && ClientBloomState.ready()
				&& ClientBloomState.profile() != null && ClientBloomState.service().isMajorActive(ClientBloomState.profile(), majorId));

		ClientPlayNetworking.registerGlobalReceiver(BloomPayloads.SyncDefinitions.TYPE, (payload, context) -> {
			try {
				SnapshotCodecs.DefinitionsSnapshot snapshot = SnapshotCodecs.decodeDefinitions(payload.json());
				ClientBloomState.setDefinitions(snapshot.definitions(), snapshot.config());
			} catch (RuntimeException e) {
				BloomLevelUp.LOGGER.error("Couldn't read Bloom definitions from the server", e);
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(BloomPayloads.SyncProfile.TYPE, (payload, context) -> {
			try {
				ClientBloomState.setProfile(SnapshotCodecs.decodeProfile(payload.json()));
			} catch (RuntimeException e) {
				BloomLevelUp.LOGGER.error("Couldn't read Bloom profile from the server", e);
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(BloomPayloads.Result.TYPE, (payload, context) -> ClientBloomState.setLastResult(payload));

		ClientPlayNetworking.registerGlobalReceiver(BloomPayloads.NameTag.TYPE, (payload, context) -> NameTags.accept(payload));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientBloomState.clear();
			NameTags.clear();
		});

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!ClientBloomState.ready()) {
				return;
			}

			AbstractContainerScreen<?> container = screen instanceof InventoryScreen inventory ? inventory
					: screen instanceof CreativeModeInventoryScreen creative ? creative : null;

			if (container == null) {
				return;
			}

			// The hub button, then one button per owned storage Major, stacked down the side.
			Screens.getWidgets(screen).add(new HubButton(container));
			int row = 1;

			if (owns(MiscIds.HOARDERS_POCKET)) {
				Screens.getWidgets(screen).add(SideButton.pocket(container, row++));
			}

			if (owns(MiscIds.RUNE_EMBEDDING)) {
				Screens.getWidgets(screen).add(SideButton.runes(container, row));
			}
		});

		KeyMapping.Category category = KeyMapping.Category.register(BloomLevelUp.id("main"));
		openHubKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.bloomlevelup.open_hub",
				InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));

		openPocketKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.bloomlevelup.open_pocket",
				InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
		openRunesKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.bloomlevelup.open_runes",
				InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));

		CombatControls.init(category);

		// Become My Fuel: show what a weapon carries.
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> {
			FuelImprint fuel = stack.get(BloomComponents.FUEL);

			if (fuel != null) {
				lines.add(Component.translatable("bloom.misc.fuel.tooltip", MiscSkill.effectName(fuel.effect(), fuel.amplifier()))
						.withStyle(ChatFormatting.DARK_PURPLE));
			}
		});
		EntityRendererRegistry.register(BloomEntities.THROWN_WEAPON, context -> new ThrownItemRenderer<>(context, 1.25F, false));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openPocketKey.consumeClick()) {
				if (client.player != null && ClientBloomState.ready()) {
					SideButton.openPocket();
				}
			}

			while (openRunesKey.consumeClick()) {
				if (client.player != null && ClientBloomState.ready()) {
					SideButton.openRunes();
				}
			}

			while (openHubKey.consumeClick()) {
				// Key clicks are only registered while no screen is open, so this never replaces another screen.
				if (client.player != null && ClientBloomState.ready()) {
					ClientScreens.open(new HubScreen());
				}
			}
		});
	}

	private static boolean owns(String majorId) {
		return ClientBloomState.profile() != null && ClientBloomState.profile().owns(majorId);
	}
}
