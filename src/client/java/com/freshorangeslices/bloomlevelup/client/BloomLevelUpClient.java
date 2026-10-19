package com.freshorangeslices.bloomlevelup.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. The hub screen, widgets, icons and visual effects will be registered here; the client
 * never owns progression state, it only renders synced snapshots and sends requests.
 */
public final class BloomLevelUpClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Hub screen, key mapping and sync receivers arrive with the networking/UI chunk.
	}
}
