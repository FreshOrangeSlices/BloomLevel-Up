package com.freshorangeslices.bloomlevelup.gametest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.client.NameTags;
import com.freshorangeslices.bloomlevelup.client.gui.HubScreen;
import com.freshorangeslices.bloomlevelup.client.gui.SideButton;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscIds;

/**
 * Boots a real client into a singleplayer world: client mixins must apply, the profile must sync, the
 * inventory button and the Skill Hub must open, Hoarder's Pocket must open through the server, the anvil
 * screen (Overenchant's mixin) must load, and a left click must pass through the combat key hook.
 * Screenshots land in the run's artifacts.
 */
public class BloomClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitFor(client -> ClientBloomState.ready() && ClientBloomState.profile() != null);
			singleplayer.getServer().runCommand("bloom admin xp @a bloomlevelup:combat 100000");
			singleplayer.getServer().runCommand("bloom admin xp @a bloomlevelup:utility 100000");
			singleplayer.getServer().runCommand("bloom admin xp @a bloomlevelup:misc 100000");
			singleplayer.getServer().runCommand("execute as @a run bloom buy bloomlevelup:misc/hoarders_pocket");
			context.waitFor(client -> ClientBloomState.ready() && ClientBloomState.profile() != null
					&& ClientBloomState.profile().category("bloomlevelup:combat").level() >= 100
					&& ClientBloomState.profile().owns(MiscIds.HOARDERS_POCKET));

			// Nameplates: the server sends each player's [lvl N] tag (N = spent Major Levels: Hoarder's Pocket = 5).
			context.waitFor(client -> NameTags.tag(client.player.getUUID()) != null && NameTags.tag(client.player.getUUID()).spent() == 5);

			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			context.takeScreenshot("bloom_inventory_button");

			context.setScreen(HubScreen::new);
			context.waitTicks(2);
			context.takeScreenshot("bloom_skill_hub");
			context.setScreen(() -> null);

			// Hoarder's Pocket: the button's request goes to the server, which opens a one-row chest screen.
			context.runOnClient(client -> SideButton.openPocket());
			context.waitForScreen(ContainerScreen.class);
			context.takeScreenshot("bloom_hoarders_pocket");
			context.setScreen(() -> null);

			// Overenchant's anvil-screen mixin only applies when the screen class loads, so open one.
			context.setScreen(() -> {
				Minecraft client = Minecraft.getInstance();
				return new AnvilScreen(new AnvilMenu(0, client.player.getInventory()), client.player.getInventory(), Component.literal("Anvil"));
			});
			context.waitTicks(2);
			context.takeScreenshot("bloom_anvil");
			context.setScreen(() -> null);

			// An empty-handed left click goes through the Charge / Lightning Summon hook.
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(2);
		}
	}
}
