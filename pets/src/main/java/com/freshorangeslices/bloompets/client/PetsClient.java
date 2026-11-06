package com.freshorangeslices.bloompets.client;

import com.freshorangeslices.bloompets.network.PetsNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The client side of Bloom Pets: the pet screen's key (K) and its tab beside Bloom's hub button on the inventory, and the
 * pet list the server sends. Only ever loaded on a client.
 */
public final class PetsClient {
	public static final KeyMapping OPEN = new KeyMapping("key.bloompets.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.bloompets");

	private PetsClient() {
	}

	public static void init(IEventBus modBus) {
		modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN));
		modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(
				() -> MenuScreens.register(com.freshorangeslices.bloompets.BloomPets.POUCH.get(), PouchScreen::new)));
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase != TickEvent.Phase.END) {
				return;
			}

			while (OPEN.consumeClick()) {
				if (Minecraft.getInstance().player != null) {
					request();
				}
			}
		});
		MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
			if (event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen) {
				event.addListener(new PetsTabButton((AbstractContainerScreen<?>) event.getScreen()));
			}
		});
	}

	/** Asks the server for the player's pets; the screen opens when they come. */
	public static void request() {
		PetsNetwork.CHANNEL.sendToServer(PetsNetwork.Request.list());
	}

	/** The list again, for the open screen (levels and health as they change). */
	public static void refresh() {
		PetsNetwork.CHANNEL.sendToServer(new PetsNetwork.Request(PetsNetwork.Action.REFRESH, new UUID(0, 0)));
	}

	public static void act(PetsNetwork.Action action, UUID pet) {
		PetsNetwork.CHANNEL.sendToServer(new PetsNetwork.Request(action, pet));
	}

	/** An action with an argument (a tree to pick, an ability to switch). */
	public static void act(PetsNetwork.Action action, UUID pet, String arg) {
		PetsNetwork.CHANNEL.sendToServer(new PetsNetwork.Request(action, pet, arg));
	}

	public static void receive(PetsNetwork.PetList list) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.screen instanceof PetScreen screen) {
			screen.refresh(list);
		} else if (list.open()) {
			minecraft.setScreen(new PetScreen(list));
		} else if (list.message() != null && minecraft.player != null) {
			minecraft.player.displayClientMessage(list.message(), true);
		}
	}
}
