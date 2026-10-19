package com.freshorangeslices.bloomlevelup.gametest.client;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.client.gui.HubScreen;

/**
 * Boots a real client into a singleplayer world: client mixins must apply, the profile must sync, the
 * inventory button and the Skill Hub must open, and a left click must pass through the combat key hook.
 * Screenshots land in the run's artifacts.
 */
public class BloomClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("bloom admin xp @a bloomlevelup:combat 100000");
			singleplayer.getServer().runCommand("bloom admin xp @a bloomlevelup:utility 100000");
			context.waitFor(client -> ClientBloomState.ready() && ClientBloomState.profile() != null
					&& ClientBloomState.profile().category("bloomlevelup:combat").level() >= 100);

			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			context.takeScreenshot("bloom_inventory_button");

			context.setScreen(HubScreen::new);
			context.waitTicks(2);
			context.takeScreenshot("bloom_skill_hub");
			context.setScreen(() -> null);

			// An empty-handed left click goes through the Charge / Lightning Summon hook.
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(2);
		}
	}
}
