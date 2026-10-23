package com.freshorangeslices.bloomlevelup.gametest.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.client.NameTags;
import com.freshorangeslices.bloomlevelup.client.gui.HubScreen;
import com.freshorangeslices.bloomlevelup.client.gui.SideButton;
import com.freshorangeslices.bloomlevelup.player.OwnedMajor;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscIds;

/**
 * Boots a real client into a singleplayer world: client mixins must apply, the profile must sync, the
 * inventory button and the Skill Hub must open, Hoarder's Pocket must open through the server, the anvil
 * screen (Overenchant's mixin) must load, and a left click must pass through the combat key hook.
 * Screenshots land in the run's artifacts.
 */
public class BloomClientGameTests implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

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

			combatTreeScreenshots(context, singleplayer);

			hubClicks(context);
			buyPointWithHugeXp(context, singleplayer);

			// Hoarder's Pocket: the button's request goes to the server, which opens a one-row chest screen.
			context.runOnClient(client -> SideButton.openPocket());
			context.waitForScreen(ContainerScreen.class);
			context.takeScreenshot("bloom_hoarders_pocket");
			context.setScreen(() -> null);

			// Rune Embedding has its own screen type now (slot outlines).
			singleplayer.getServer().runCommand("execute as @a run bloom buy bloomlevelup:misc/rune_embedding");
			context.waitFor(client -> ClientBloomState.profile().owns(MiscIds.RUNE_EMBEDDING));
			context.runOnClient(client -> SideButton.openRunes());
			context.waitForScreen(com.freshorangeslices.bloomlevelup.client.gui.RuneScreen.class);
			context.takeScreenshot("bloom_rune_embedding");
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

		if (!failures.isEmpty()) {
			throw new AssertionError("Bloom client checks failed: " + String.join(" | ", failures));
		}
	}

	/**
	 * The Combat tree with a few Majors owned (some lines lit) at 1920x1080 with GUI scale 3 and 4, and the
	 * other tabs at scale 3 (the screenshots job prints some as annotations), on the real background art.
	 */
	private void combatTreeScreenshots(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		for (String major : List.of("combat/full_sweep", "combat/lightning_shock", "combat/master_of_arms", "combat/catch", "combat/charge_attack")) {
			singleplayer.getServer().runCommand("execute as @a run bloom buy bloomlevelup:" + major);
		}

		context.waitFor(client -> ClientBloomState.profile().owns("bloomlevelup:combat/charge_attack"));
		context.getInput().resizeWindow(1920, 1080);

		for (String shot : List.of("combat:3", "combat:4", "armor:3", "utility:3", "misc:3")) {
			String tab = shot.substring(0, shot.indexOf(':'));
			int scale = Integer.parseInt(shot.substring(shot.indexOf(':') + 1));
			context.runOnClient(client -> {
				ClientBloomState.selectCategory("bloomlevelup:" + tab);
				client.options.guiScale().set(scale);
				client.resizeGui();
			});
			context.setScreen(HubScreen::new);
			context.getInput().setCursorPos(2, 2); // no tooltip in the way
			context.waitTicks(3);
			context.takeScreenshot("bloom_hub_" + tab + "_s" + scale);
			context.setScreen(() -> null);
		}

		context.runOnClient(client -> {
			client.options.guiScale().set(0);
			client.resizeGui();
		});
		context.getInput().resizeWindow(854, 480);
		context.waitTicks(2);
	}

	/** Buys, switches off, switches on and refunds a Major with real mouse clicks on the hub's grid. */
	private void hubClicks(ClientGameTestContext context) {
		String major = ArmorIds.TITAN_HIDE;
		context.runOnClient(client -> ClientBloomState.selectCategory("bloomlevelup:armor"));
		context.setScreen(HubScreen::new);
		context.waitTicks(2);

		click(context, major, InputConstants.MOUSE_BUTTON_LEFT);
		check(context, "left click buys", client -> owned(major) != null);
		click(context, major, InputConstants.MOUSE_BUTTON_LEFT);
		check(context, "left click switches off (still owned)", client -> owned(major) != null && !owned(major).enabled());
		context.takeScreenshot("bloom_hub_major_off");
		click(context, major, InputConstants.MOUSE_BUTTON_LEFT);
		check(context, "left click switches back on", client -> owned(major) != null && owned(major).enabled());
		click(context, major, InputConstants.MOUSE_BUTTON_RIGHT);
		check(context, "right click refunds", client -> owned(major) == null);
		context.setScreen(() -> null);
	}

	/** XP Mode's Buy point with 10,000 levels: the button must be clickable, and a click buys an Investment point. */
	private void buyPointWithHugeXp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		String combat = "bloomlevelup:combat";
		singleplayer.getServer().runCommand("xp add @a 10000 levels");
		context.waitFor(client -> client.player.experienceLevel >= 10000);
		context.runOnClient(client -> ClientBloomState.selectCategory(combat));
		context.setScreen(HubScreen::new);
		context.waitTicks(3);
		int before = context.computeOnClient(client -> ClientBloomState.profile().category(combat).investedPoints());
		int[] button = context.computeOnClient(client -> client.gui.screen() instanceof HubScreen hub ? hub.investButtonForTests() : null);
		String state = context.computeOnClient(client -> client.gui.screen() instanceof HubScreen hub ? hub.investStateForTests() : "no hub");
		BloomLevelUp.LOGGER.info("BLOOMTEST buy point: button {} active={} {}", button == null ? "missing" : button[0] + "," + button[1],
				button != null && button[2] == 1, state);

		if (button == null || button[2] != 1) {
			failures.add("Buy point clickable with 10,000 levels");
			context.setScreen(() -> null);
			return;
		}

		double[] at = context.computeOnClient(client -> new double[] {
				(button[0] + 0.5) * client.getWindow().getScreenWidth() / (double) client.getWindow().getGuiScaledWidth(),
				(button[1] + 0.5) * client.getWindow().getScreenHeight() / (double) client.getWindow().getGuiScaledHeight()});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		check(context, "Buy point buys an Investment point with 10,000 levels",
				client -> ClientBloomState.profile().category(combat).investedPoints() == before + 1);
		int levelAfter = context.computeOnClient(client -> client.player.experienceLevel);
		BloomLevelUp.LOGGER.info("BLOOMTEST after buying: level {}", levelAfter);
		context.setScreen(() -> null);
	}

	private static OwnedMajor owned(String major) {
		return ClientBloomState.profile().majors().get(major);
	}

	private static void click(ClientGameTestContext context, String major, int button) {
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof HubScreen hub) {
				hub.revealForTests(major); // small windows show only part of the page
			}
		});
		double[] at = context.computeOnClient(client -> {
			if (!(client.gui.screen() instanceof HubScreen hub)) {
				return null;
			}

			int[] gui = hub.majorCenterForTests(major);

			if (gui == null) {
				return null;
			}

			double sx = client.getWindow().getScreenWidth() / (double) client.getWindow().getGuiScaledWidth();
			double sy = client.getWindow().getScreenHeight() / (double) client.getWindow().getGuiScaledHeight();
			return new double[] {(gui[0] + 0.5) * sx, (gui[1] + 0.5) * sy};
		});

		if (at == null) {
			BloomLevelUp.LOGGER.info("BLOOMTEST no hub icon for {}", major);
			return;
		}

		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTick();
		context.getInput().pressMouse(button);
		context.waitTick();
		BloomLevelUp.LOGGER.info("BLOOMTEST click {} at window {},{} -> {}", button, at[0], at[1], HubScreen.lastClickForTests);
	}

	private void check(ClientGameTestContext context, String what, Predicate<Minecraft> condition) {
		boolean ok = false;

		for (int i = 0; i < 60 && !ok; i++) {
			ok = context.computeOnClient(condition::test);

			if (!ok) {
				context.waitTick();
			}
		}

		BloomLevelUp.LOGGER.info("BLOOMTEST {}: {} (last result {})", what, ok ? "ok" : "FAILED", ClientBloomState.lastResult());

		if (!ok) {
			failures.add(what);
		}
	}
}
