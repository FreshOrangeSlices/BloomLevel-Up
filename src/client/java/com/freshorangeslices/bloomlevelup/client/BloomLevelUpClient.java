package com.freshorangeslices.bloomlevelup.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

import com.freshorangeslices.bloomlevelup.skill.SideBridge;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;

/**
 * Client wiring, called from the mod constructor on the physical client only. The client never owns progression;
 * it renders what the server sent (the snapshots arrive through {@link ClientPackets}).
 */
public final class BloomLevelUpClient {
	private BloomLevelUpClient() {
	}

	public static void init(IEventBus modBus) {
		// Lets shared code (Unrestricted Riptide's trident use) ask about the local player's Majors.
		SideBridge.setClientConfig(ClientBloomState::config);
		SideBridge.setClientMajorActive((player, majorId) -> player == Minecraft.getInstance().player && ClientBloomState.ready()
				&& ClientBloomState.profile() != null && ClientBloomState.service().isMajorActive(ClientBloomState.profile(), majorId));
		SideBridge.setClientCopySource(player -> player == Minecraft.getInstance().player && ClientBloomState.ready() && ClientBloomState.profile() != null
				? ClientBloomState.service().activeCopySource(ClientBloomState.profile(), CombatIds.COPYCAT).orElse(null) : null);

		MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientPackets.disconnected());
	}
}
