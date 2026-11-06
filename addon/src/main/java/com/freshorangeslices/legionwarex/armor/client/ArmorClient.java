package com.freshorangeslices.legionwarex.armor.client;

import com.freshorangeslices.legionwarex.armor.ArmorNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Hidden armor on the client: the masks the server sends, by entity id; while an entity with one is drawn, its hidden
 * pieces are taken out of its armor slots (this client's copy only) and put straight back after, so its armor layer,
 * any mod's armor model and the inventory's figure all leave them out. And the dot in each armor slot's bottom-left
 * corner of the player's own inventory (the creative one's too; green shown, red hidden), with a line on a hidden
 * piece's tooltip.
 */
public final class ArmorClient {
	private static final Map<Integer, Integer> MASKS = new HashMap<>();
	/** What was taken out of each entity being drawn right now, to go straight back. */
	private static final Map<LivingEntity, ItemStack[]> TAKEN = new IdentityHashMap<>();

	private ArmorClient() {
	}

	public static void init() {
		MinecraftForge.EVENT_BUS.register(ArmorClient.class);
	}

	public static void receive(int entity, int mask) {
		if (mask == 0) {
			MASKS.remove(entity);
		} else {
			MASKS.put(entity, mask);
		}
	}

	/** This client's player's own mask. */
	static int own() {
		var player = Minecraft.getInstance().player;
		return player == null ? 0 : MASKS.getOrDefault(player.getId(), 0);
	}

	/** One of the player's own pieces switched (shown at once; the server confirms). */
	static void toggle(int index) {
		var player = Minecraft.getInstance().player;

		if (player != null) {
			receive(player.getId(), own() ^ 1 << index);
			ArmorNetwork.CHANNEL.sendToServer(new ArmorNetwork.Toggle(index));
		}
	}

	@SubscribeEvent
	public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
		LivingEntity entity = event.getEntity();
		int mask = MASKS.getOrDefault(entity.getId(), 0);

		if (mask == 0 || TAKEN.containsKey(entity) || !(entity.getArmorSlots() instanceof List<ItemStack> armor) || armor.size() < 4) {
			return;
		}

		ItemStack[] taken = new ItemStack[4];

		for (int i = 0; i < 4; i++) {
			if ((mask & 1 << i) != 0) {
				taken[i] = armor.get(i);
				armor.set(i, ItemStack.EMPTY);
			}
		}

		TAKEN.put(entity, taken);
	}

	@SubscribeEvent
	public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
		putBack(event.getEntity());
	}

	private static void putBack(LivingEntity entity) {
		ItemStack[] taken = TAKEN.remove(entity);

		if (taken == null || !(entity.getArmorSlots() instanceof List<ItemStack> armor) || armor.size() < 4) {
			return;
		}

		for (int i = 0; i < 4; i++) {
			if (taken[i] != null) {
				armor.set(i, taken[i]);
			}
		}
	}

	/** A safety net: anything still out (a drawing that never finished) goes back. */
	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.START && !TAKEN.isEmpty()) {
			new ArrayList<>(TAKEN.keySet()).forEach(ArmorClient::putBack);
		}
	}

	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		MASKS.clear();
		TAKEN.clear();
	}

	/** The dots, over the slots' items (a container draws its items after its buttons). */
	@SubscribeEvent
	public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
		for (var child : event.getContainerScreen().children()) {
			if (child instanceof ArmorDot dot) {
				dot.draw(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
			}
		}
	}

	/** A hidden piece says so on its tooltip (the one being worn: the very stack in the armor slot). */
	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		var player = Minecraft.getInstance().player;
		int mask = own();

		if (player == null || mask == 0 || event.getEntity() != player) {
			return;
		}

		for (int i = 0; i < 4; i++) {
			if ((mask & 1 << i) != 0 && player.getInventory().armor.get(i) == event.getItemStack()) {
				event.getToolTip().add(Component.translatable("gui.legionwarex.armor_hidden_line").withStyle(ChatFormatting.GRAY));
				return;
			}
		}
	}

	@SubscribeEvent
	public static void onScreenInit(ScreenEvent.Init.Post event) {
		if (event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen) {
			for (int i = 0; i < 4; i++) {
				event.addListener(new ArmorDot((AbstractContainerScreen<?>) event.getScreen(), i));
			}
		}
	}
}
