package com.freshorangeslices.bloomlevelup.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.freshorangeslices.bloomlevelup.client.combat.CombatControls;
import com.freshorangeslices.bloomlevelup.client.debug.TooltipDump;
import com.freshorangeslices.bloomlevelup.client.gui.BloomHud;
import com.freshorangeslices.bloomlevelup.client.gui.ClientScreens;
import com.freshorangeslices.bloomlevelup.client.gui.HubButton;
import com.freshorangeslices.bloomlevelup.client.gui.HubScreen;
import com.freshorangeslices.bloomlevelup.client.gui.RuneScreen;
import com.freshorangeslices.bloomlevelup.client.gui.SideButton;
import com.freshorangeslices.bloomlevelup.client.render.ThrownWeaponRenderer;
import com.freshorangeslices.bloomlevelup.entity.BloomEntities;
import com.freshorangeslices.bloomlevelup.item.FuelImprint;
import com.freshorangeslices.bloomlevelup.skill.SideBridge;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.misc.HoardersPocket;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscIds;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding;

/**
 * Client wiring, called from the mod constructor on the physical client only: the snapshots (through
 * {@link ClientPackets}), the hub button in the inventory, the keys, the HUD and the renderers. The client never
 * owns progression; it renders what the server sent.
 */
public final class BloomLevelUpClient {
	/** The keys' group in Controls. */
	public static final String KEY_CATEGORY = "key.category.bloomlevelup.main";

	private static final KeyMapping OPEN_HUB = unbound("key.bloomlevelup.open_hub");
	private static final KeyMapping OPEN_SHEET = unbound("key.bloomlevelup.open_sheet");
	private static final KeyMapping OPEN_POCKET = unbound("key.bloomlevelup.open_pocket");
	private static final KeyMapping OPEN_RUNES = unbound("key.bloomlevelup.open_runes");

	private BloomLevelUpClient() {
	}

	private static KeyMapping unbound(String name) {
		return new KeyMapping(name, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
	}

	public static void init(IEventBus modBus) {
		// Lets shared code (Unrestricted Riptide's trident use) ask about the local player's Majors.
		SideBridge.setClientConfig(ClientBloomState::config);
		SideBridge.setClientMajorActive((player, majorId) -> player == Minecraft.getInstance().player && ClientBloomState.ready()
				&& ClientBloomState.profile() != null && ClientBloomState.service().isMajorActive(ClientBloomState.profile(), majorId));
		SideBridge.setClientStatValue((player, statId) -> player == Minecraft.getInstance().player && ClientBloomState.ready()
				&& ClientBloomState.profile() != null ? ClientBloomState.service().statValue(ClientBloomState.profile(), statId) : 0);
		SideBridge.setClientCopySource(player -> player == Minecraft.getInstance().player && ClientBloomState.ready() && ClientBloomState.profile() != null
				? ClientBloomState.service().activeCopySource(ClientBloomState.profile(), CombatIds.COPYCAT).orElse(null) : null);

		TooltipDump.init();
		ClientPackets.onNameTag(NameTags::accept);
		MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
			ClientPackets.disconnected();
			NameTags.clear();
		});

		modBus.addListener((RegisterKeyMappingsEvent event) -> {
			event.register(OPEN_HUB);
			event.register(OPEN_SHEET);
			event.register(OPEN_POCKET);
			event.register(OPEN_RUNES);
		});
		modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
			MenuScreens.register(HoardersPocket.MENU.get(), ContainerScreen::new);
			MenuScreens.register(RuneEmbedding.MENU.get(), RuneScreen::new);
		}));
		modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
				event.registerEntityRenderer(BloomEntities.THROWN_WEAPON.get(), ThrownWeaponRenderer::new));

		CombatControls.init(modBus);
		BloomHud.init(modBus);

		// The hub button beside the inventory, then one button per owned storage Major, stacked down the side.
		MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
			if (!ClientBloomState.ready()) {
				return;
			}

			AbstractContainerScreen<?> container = event.getScreen() instanceof InventoryScreen inventory ? inventory
					: event.getScreen() instanceof CreativeModeInventoryScreen creative ? creative : null;

			if (container == null) {
				return;
			}

			event.addListener(new HubButton(container));
			int row = 1;

			// After a refund or Bloom+ the buttons stay while anything is still inside (take-out only).
			if (owns(MiscIds.HOARDERS_POCKET) || counter(MiscIds.POCKET_HAS_ITEMS) > 0) {
				event.addListener(SideButton.pocket(container, row++));
			}

			if (owns(MiscIds.RUNE_EMBEDDING) || counter(MiscIds.RUNES_HAS_ITEMS) > 0) {
				event.addListener(SideButton.runes(container, row));
			}
		});

		// Players' nameplates: "Name [lvl N]".
		MinecraftForge.EVENT_BUS.addListener((RenderNameTagEvent event) -> {
			if (event.getEntity() instanceof Player player) {
				event.setContent(NameTags.decorate(player, event.getContent()));
			}
		});

		// Become My Fuel: show what a weapon carries.
		MinecraftForge.EVENT_BUS.addListener((ItemTooltipEvent event) -> {
			FuelImprint fuel = FuelImprint.get(event.getItemStack());

			if (fuel != null) {
				int ticks = ClientBloomState.config() == null ? 100 : ClientBloomState.config().fuelEffectTicks;
				String seconds = ticks % 20 == 0 ? Integer.toString(ticks / 20) : String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
				event.getToolTip().add(Component.translatable("bloom.misc.fuel.tooltip", MiscSkill.effectName(fuel.effect(), fuel.amplifier()), seconds)
						.withStyle(ChatFormatting.DARK_PURPLE));
			}
		});

		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				keys(Minecraft.getInstance());
			}
		});
	}

	private static void keys(Minecraft client) {
		while (OPEN_POCKET.consumeClick()) {
			if (client.player != null && ClientBloomState.ready()) {
				SideButton.openPocket();
			}
		}

		while (OPEN_RUNES.consumeClick()) {
			if (client.player != null && ClientBloomState.ready()) {
				SideButton.openRunes();
			}
		}

		while (OPEN_SHEET.consumeClick()) {
			// The stat sheet straight away, for the tab last open in the hub; closing it closes everything.
			if (client.player != null && ClientBloomState.ready()) {
				ClientScreens.open(HubScreen.onStatSheet());
			}
		}

		while (OPEN_HUB.consumeClick()) {
			// Key clicks are only registered while no screen is open, so this never replaces another screen.
			if (client.player != null && ClientBloomState.ready()) {
				ClientScreens.open(new HubScreen());
			}
		}
	}

	private static boolean owns(String majorId) {
		return ClientBloomState.profile() != null && ClientBloomState.profile().owns(majorId);
	}

	private static long counter(String key) {
		return ClientBloomState.profile() == null ? 0 : ClientBloomState.profile().counter(key);
	}
}
