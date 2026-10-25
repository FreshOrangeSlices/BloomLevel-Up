package com.freshorangeslices.bloomlevelup.gametest.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.SharedConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.compat.BetterCombat;
import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.client.combat.CombatControls;
import com.freshorangeslices.bloomlevelup.client.compat.BetterCombatClient;
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
		if (Boolean.getBoolean("bloom.packtest")) {
			// The whole Bloom Pack (runClientGameTestPack): only the launch checks run there.
			packLaunch(context);

			if (!failures.isEmpty()) {
				throw new AssertionError("Bloom Pack launch checks failed: " + String.join(" | ", failures));
			}

			return;
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitFor(client -> ClientBloomState.ready() && ClientBloomState.profile() != null);

			if (BetterCombat.installed()) {
				// The Better Combat run (runClientGameTestBetterCombat): only what Better Combat changes on the client.
				betterCombatSlam(context, singleplayer);
			} else {
				everything(context, singleplayer);
			}
		}

		if (!failures.isEmpty()) {
			throw new AssertionError("Bloom client checks failed: " + String.join(" | ", failures));
		}
	}

	/**
	 * The pack launch test: the game starts with every pack mod, its resource packs and the shader, makes a normal
	 * world (structures and mobs on), and shows the world, the player, a Better Combat swing and the hub. What's
	 * loaded goes to the log (BLOOMTEST lines); each step leaves a screenshot.
	 */
	private void packLaunch(ClientGameTestContext context) {
		context.waitTicks(20);
		context.takeScreenshot("pack_title");
		context.runOnClient(client -> BloomLevelUp.LOGGER.info("BLOOMTEST pack: {} mods loaded; resource packs on: {}; shader in use: {}",
				FabricLoader.getInstance().getAllMods().size(), client.getResourcePackRepository().getSelectedIds(), shaderInUse()));
		// The run folder has no options.txt: Bloom has to switch the pack's resource packs and shader on by itself
		// (config/bloomlevelup/pack-defaults.properties, applied before the game starts).
		boolean packsOn = context.computeOnClient(client -> client.getResourcePackRepository().getSelectedIds().stream()
				.anyMatch(id -> id.startsWith("file/FreshAnimations")) && client.getResourcePackRepository().getSelectedIds().stream()
				.anyMatch(id -> id.startsWith("file/FA+Player")));

		if (!packsOn) {
			failures.add("the pack's resource packs weren't switched on at the first launch");
		}

		context.runOnClient(client -> BloomLevelUp.LOGGER.info("BLOOMTEST pack: every resource pack there is: {}",
				client.getResourcePackRepository().getAvailableIds()));

		if (!"true".equals(shaderInUse())) {
			failures.add("the pack's shader wasn't on at the first launch");
		}

		packKeys(context);
		// CI draws in software: with the shader on, a frame takes seconds. The world part runs without it (and at a
		// short view distance); the shader comes back for one picture at the end.
		context.runOnClient(client -> {
			setShaders(false);
			client.options.renderDistance().set(3);
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().adjustSettings(creator -> {
			Holder<WorldPreset> normal = creator.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET)
					.getOrThrow(WorldPresets.NORMAL);
			creator.setWorldType(new WorldCreationUiState.WorldTypeEntry(normal));
			creator.setSeed("bloom");
			creator.setGenerateStructures(true);
			creator.getGameRules().set(GameRules.SPAWN_MOBS, true, null);
		}).create()) {
			try {
				singleplayer.getConnection().waitForChunksRender(SharedConstants.TICKS_PER_MINUTE * 3);
			} catch (AssertionError slow) {
				// Slow software drawing isn't what this test is about: note it and carry on with what's there.
				BloomLevelUp.LOGGER.info("BLOOMTEST pack: chunks still drawing after 3 minutes of ticks, carrying on");
			}

			context.waitFor(client -> ClientBloomState.ready() && ClientBloomState.profile() != null, SharedConstants.TICKS_PER_MINUTE);
			context.waitTicks(40);
			rpgSorting(singleplayer);

			// Origins asks for an origin on the first join: picture it, then pick Human so the rest can go on.
			String screen = context.computeOnClient(client -> client.gui.screen() == null ? "none" : client.gui.screen().getClass().getName());
			BloomLevelUp.LOGGER.info("BLOOMTEST pack: screen after joining: {}", screen);
			context.takeScreenshot("pack_join");
			singleplayer.getServer().runCommand("origin set @a origins:origin origins:human");
			context.waitTicks(10);
			context.setScreen(() -> null);

			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("weather clear");
			singleplayer.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 10");
			// A clear, flat patch of grass in front, whatever the spawn looks like, so the mobs show. It reaches under
			// the camera too, so the player stands on the same floor as the mobs (spawning on snow layers left the
			// mobs a layer too low).
			singleplayer.getServer().runCommand("execute as @a at @s run fill ~-4 ~ ~-2 ~4 ~4 ~9 air");
			singleplayer.getServer().runCommand("execute as @a at @s run fill ~-4 ~-1 ~-2 ~4 ~-1 ~9 grass_block");
			context.waitTicks(10);
			singleplayer.getServer().runCommand("execute as @a at @s run summon villager ^-1 ^ ^4 {NoAI:1b,Rotation:[180f,0f]}");
			singleplayer.getServer().runCommand("execute as @a at @s run summon zombie ^1.5 ^ ^5 {NoAI:1b,Rotation:[160f,0f]}");
			singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:iron_sword");
			context.waitTicks(40);
			context.takeScreenshot("pack_world");

			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			context.takeScreenshot("pack_player");

			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(4);
			context.takeScreenshot("pack_swing");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			context.setScreen(HubScreen::new);
			context.waitTicks(5);

			if (!context.computeOnClient(client -> client.gui.screen() instanceof HubScreen)) {
				failures.add("the hub didn't open with the pack");
			}

			context.takeScreenshot("pack_hub");
			context.setScreen(() -> null);

			mobGallery(context, singleplayer);

			context.runOnClient(client -> setShaders(true));
			context.waitTicks(100);
			context.takeScreenshot("pack_shader");
			context.runOnClient(client -> BloomLevelUp.LOGGER.info("BLOOMTEST pack: in the world, shader in use: {}", shaderInUse()));
		}
	}

	/**
	 * The keys the pack moves on the first launch: the roll keeps R, Bloom's weapon skill is on V and Iris's "reload
	 * shaders" is unbound, so nothing else starts on R or V. Every other key two bindings share goes to the log
	 * (vanilla's F3 combinations are left out: they share letters on purpose).
	 */
	private void packKeys(ClientGameTestContext context) {
		String[][] expected = {{"keybinds.combat_roll.roll", "key.keyboard.r"}, {"key.bloomlevelup.weapon_skill", "key.keyboard.v"},
			{"iris.keybind.reload", "key.keyboard.unknown"}};
		java.util.Map<String, List<String>> byKey = context.computeOnClient(client -> {
			java.util.Map<String, List<String>> keys = new java.util.TreeMap<>();

			for (net.minecraft.client.KeyMapping mapping : client.options.keyMappings) {
				if (!mapping.getName().startsWith("key.debug.")) {
					keys.computeIfAbsent(mapping.saveString(), key -> new ArrayList<>()).add(mapping.getName());
				}
			}

			return keys;
		});

		for (String[] pair : expected) {
			List<String> on = byKey.getOrDefault(pair[1], List.of());

			if (!on.contains(pair[0])) {
				failures.add(pair[0] + " isn't on " + pair[1]);
			} else if (!pair[1].endsWith("unknown") && on.size() > 1) {
				failures.add(pair[1] + " is shared: " + on);
			}
		}

		List<String> shared = new ArrayList<>();
		byKey.forEach((key, names) -> {
			if (names.size() > 1 && !key.endsWith("unknown")) {
				shared.add(key + " " + names);
			}
		});
		BloomLevelUp.LOGGER.info("BLOOMTEST pack: keys shared by more than one binding: {}; game data version for options.txt: {}",
				shared.isEmpty() ? "none" : shared, com.freshorangeslices.bloomlevelup.PackDefaultsLaunch.dataVersion());
	}

	/**
	 * The RPG Series' weapons in Bloom's weapon families (their maces, hammers, spears, glaives and double axes are in
	 * #minecraft:swords, which Bloom's sword tag takes in, so the not_sword tag has to keep them out), blade or blunt,
	 * and their armor's class (1 light, 2 medium, 3 heavy). Also the roll's quarter second of invulnerability from the
	 * pack's Combat Roll config.
	 */
	private void rpgSorting(TestSingleplayerContext singleplayer) {
		String[][] weapons = {
			{"minecraft:iron_sword", "[SWORD]"}, {"minecraft:iron_axe", "[AXE]"},
			{"paladins:iron_claymore", "[SWORD]"}, {"rogues:iron_dagger", "[SWORD]"}, {"rogues:iron_sickle", "[SWORD]"},
			{"rogues:iron_double_axe", "[AXE]"}, {"paladins:iron_mace", "[MACE]"}, {"paladins:iron_great_hammer", "[MACE]"},
			{"archers:iron_spear", "[SPEAR]"}, {"rogues:iron_glaive", "[SPEAR]"},
			{"archers:composite_longbow", "[BOW]"}, {"archers:mechanic_shortbow", "[BOW]"},
			{"archers:heavy_crossbow", "[CROSSBOW]"}, {"archers:rapid_crossbow", "[CROSSBOW]"},
			{"wizards:staff_wizard", "[]"}, {"paladins:holy_wand", "[]"}};
		String[][] armor = {
			{"wizards:wizard_robe_chest", "1"}, {"armory_rpgs:astral_robe_chest", "1"}, {"archers:archer_armor_chest", "1"},
			{"rogues:warrior_armor_chest", "1"}, {"armory_rpgs:slayer_armor_chest", "1"}, {"paladins:paladin_armor_chest", "2"},
			{"paladins:crusader_armor_chest", "3"}, {"rogues:berserker_armor_chest", "3"}, {"armory_rpgs:justicar_armor_chest", "3"}};
		List<String> report = singleplayer.getServer().computeOnServer(server -> {
			List<String> out = new ArrayList<>();

			for (String[] row : weapons) {
				java.util.Optional<net.minecraft.world.item.Item> item = net.minecraft.core.registries.BuiltInRegistries.ITEM
						.getOptional(net.minecraft.resources.Identifier.parse(row[0]));
				net.minecraft.world.item.ItemStack stack = item.map(net.minecraft.world.item.ItemStack::new).orElse(null);
				String got = stack == null ? "missing" : com.freshorangeslices.bloomlevelup.skill.combat.Weapons.families(stack).toString();
				String edge = stack == null ? "" : (stack.is(com.freshorangeslices.bloomlevelup.registry.BloomTags.WEAPON_BLUNT) ? " blunt" : "")
						+ (stack.is(com.freshorangeslices.bloomlevelup.registry.BloomTags.WEAPON_BLADE)
							&& !stack.is(com.freshorangeslices.bloomlevelup.registry.BloomTags.WEAPON_NOT_BLADE) ? " blade" : "");
				out.add((got.equals(row[1]) ? "" : "WRONG ") + row[0] + "=" + got + edge);
			}

			for (String[] row : armor) {
				java.util.Optional<net.minecraft.world.item.Item> item = net.minecraft.core.registries.BuiltInRegistries.ITEM
						.getOptional(net.minecraft.resources.Identifier.parse(row[0]));
				String got = item.map(i -> String.valueOf(com.freshorangeslices.bloomlevelup.skill.armor.ArmorStyles
						.weightOf(new net.minecraft.world.item.ItemStack(i)))).orElse("missing");
				out.add((got.equals(row[1]) ? "" : "WRONG ") + row[0] + "=" + got);
			}

			out.add("roll invulnerable ticks=" + rollInvulnerableTicks());
			return out;
		});
		BloomLevelUp.LOGGER.info("BLOOMTEST pack: RPG gear in Bloom: {}", report);

		for (String line : report) {
			if (line.startsWith("WRONG")) {
				failures.add(line);
			}
		}

		if (!report.contains("roll invulnerable ticks=5")) {
			failures.add("the roll's invulnerability isn't the pack's 5 ticks: " + report.get(report.size() - 1));
		}
	}

	/** Combat Roll's server setting, read by name so Bloom never depends on it. */
	private static String rollInvulnerableTicks() {
		try {
			Object config = Class.forName("net.combat_roll.CombatRollMod").getField("config").get(null);
			return String.valueOf(config.getClass().getField("invulnerable_ticks_upon_roll").get(config));
		} catch (ReflectiveOperationException | LinkageError e) {
			return "unreadable (" + e + ")";
		}
	}

	/** One close-up: the time of day, where each mob stands (relative to the camera) and the command that makes it. */
	private record Shot(String name, String time, String[] spots, String[] commands) {
	}

	/**
	 * Close-ups of the mobs whose looks the pack changes: villagers from the front, from the side and at night (two
	 * each: a plain one that keeps its AI but can't walk, so it looks at the camera, and a snowy farmer standing
	 * still), then Fresh Animations' mobs (and its add-ons' chest and minecart) three at a time. Night vision keeps
	 * the night shots bright.
	 */
	private void mobGallery(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("effect give @a night_vision infinite 0 true");
		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
		String looking = "summon villager ~ ~ ~ {Rotation:[180f,0f],attributes:[{id:\"minecraft:movement_speed\",base:0.0}]}";
		String farmer = "summon villager ~ ~ ~ {NoAI:1b,Rotation:[%sf,0f],VillagerData:{type:\"minecraft:snow\",profession:\"minecraft:farmer\",level:2}}";
		String plainStill = "summon villager ~ ~ ~ {NoAI:1b,Rotation:[%sf,0f]}";
		String[] two = {"^1.1 ^ ^4", "^-1.1 ^ ^4"};
		String[] three = {"^1.6 ^ ^3", "^ ^ ^3", "^-1.6 ^ ^3"};
		Shot[] shots = {
			new Shot("pack_villagers_front", "noon", two, new String[] {looking, farmer.formatted("180")}),
			new Shot("pack_villagers_side", "noon", two, new String[] {plainStill.formatted("90"), farmer.formatted("90")}),
			new Shot("pack_villagers_night", "midnight", two, new String[] {looking, farmer.formatted("180")}),
			new Shot("pack_mobs_1", "midnight", three, new String[] {"summon sheep", "summon cow", "summon wandering_trader"}),
			new Shot("pack_mobs_2", "midnight", three, new String[] {"summon zombie", "summon skeleton", "summon creeper"}),
			new Shot("pack_mobs_3", "midnight", three, new String[] {"summon pig", "summon iron_golem", "summon chicken"}),
			// Fresh Animations' Spiders and Objects add-ons. A crafting table (a plain block) next to the chest shows
			// where the chest should sit.
			new Shot("pack_mobs_4", "midnight", three, new String[] {"summon spider", "setblock ~ ~ ~ chest[facing=north]",
				"setblock ~ ~ ~ crafting_table"}),
			new Shot("pack_mobs_5", "noon", three, new String[] {"summon minecart", "setblock ~ ~ ~ ender_chest[facing=north]",
				"summon oak_boat"}),
			// The RPG Series' gear (its 3D armor comes from Armor Model API): a wizard, a paladin and an archer.
			new Shot("pack_rpg_gear", "noon", three, new String[] {
				gearStand("wizards", "wizard_robe", "wizards:staff_wizard"),
				gearStand("paladins", "paladin_armor", "paladins:iron_claymore"),
				gearStand("archers", "archer_armor", "archers:composite_longbow")}),
		};

		for (Shot shot : shots) {
			singleplayer.getServer().runCommand("time set " + shot.time());
			singleplayer.getServer().runCommand("execute as @a at @s run kill @e[type=!player,distance=..12]");
			singleplayer.getServer().runCommand("execute as @a at @s run fill ~-3 ~ ~1 ~3 ~1 ~5 air");

			for (int i = 0; i < shot.commands().length; i++) {
				String command = shot.commands()[i];
				boolean plain = command.startsWith("summon") && !command.contains("{");
				// "rotated ~ 0": the camera looks slightly down, and ^ ^ ^3 along that line ends half a block under
				// the floor (that's what sank the earlier close-ups).
				singleplayer.getServer().runCommand("execute as @a at @s rotated ~ 0 positioned " + shot.spots()[i] + " run " + command
						+ (plain ? " ~ ~ ~ {NoAI:1b,Rotation:[180f,0f]}" : ""));
			}

			// What each villager is (type and job), in the log next to the picture.
			singleplayer.getServer().runCommand("execute as @e[type=villager] run data get entity @s VillagerData");
			singleplayer.getServer().runCommand("execute as @e[type=villager] run data get entity @s Age");
			singleplayer.getServer().runCommand("execute as @e[type=villager] run data get entity @s Pos");
			context.waitTicks(60);
			context.takeScreenshot(shot.name());
		}

		// The player in a wizard's robes with a staff, from the front: the RPG armor on Fresh Animations' player model.
		singleplayer.getServer().runCommand("execute as @a at @s run kill @e[type=!player,distance=..12]");

		for (String piece : new String[] {"head", "chest", "legs", "feet"}) {
			singleplayer.getServer().runCommand("item replace entity @a armor." + piece + " with wizards:wizard_robe_" + piece);
		}

		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with wizards:staff_wizard");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(40);
		context.takeScreenshot("pack_rpg_player");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		for (String piece : new String[] {"head", "chest", "legs", "feet"}) {
			singleplayer.getServer().runCommand("item replace entity @a armor." + piece + " with minecraft:air");
		}

		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
	}

	/** An armor stand facing the camera in one of the RPG Series' armor sets ({@code <set>_head} and so on), holding a weapon. */
	private static String gearStand(String namespace, String set, String weapon) {
		StringBuilder equipment = new StringBuilder();

		for (String piece : new String[] {"head", "chest", "legs", "feet"}) {
			equipment.append(piece).append(":{id:\"").append(namespace).append(':').append(set).append('_').append(piece).append("\"},");
		}

		return "summon armor_stand ~ ~ ~ {Rotation:[180f,0f],ShowArms:1b,equipment:{" + equipment + "mainhand:{id:\"" + weapon + "\"}}}";
	}

	/** Turns Iris's shader pack on or off (nothing without Iris). */
	private static void setShaders(boolean on) {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object config = api.getMethod("getConfig").invoke(api.getMethod("getInstance").invoke(null));
			Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
		} catch (ReflectiveOperationException | LinkageError e) {
			BloomLevelUp.LOGGER.info("BLOOMTEST pack: couldn't switch the shader {}: {}", on ? "on" : "off", e.toString());
		}
	}

	/** Whether Iris is drawing a shader pack ("no Iris" without it; asked by name so Bloom never depends on Iris). */
	private static String shaderInUse() {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object instance = api.getMethod("getInstance").invoke(null);
			return String.valueOf(api.getMethod("isShaderPackInUse").invoke(instance));
		} catch (ReflectiveOperationException | LinkageError e) {
			return "no Iris";
		}
	}

	/** Everything the client checks without Better Combat. */
	private void everything(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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
		statSheetScreenshots(context, singleplayer);

		hubClicks(context);
		buyPointWithHugeXp(context, singleplayer);
		braceHud(context, singleplayer);

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

	/**
	 * With Better Combat: a charged Charge Attack swing (hold the weapon-skill key, let go) plays the overhead slam
	 * instead of the next combo swing and lands for double damage. Screenshots from the front, during the wind-up
	 * and as it lands.
	 */
	private void betterCombatSlam(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("bloom admin xp @a bloomlevelup:combat 100000");
		singleplayer.getServer().runCommand("execute as @a run bloom buy bloomlevelup:combat/charge_attack");
		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:iron_axe");
		singleplayer.getServer().runCommand("time set noon");
		singleplayer.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
		context.waitTicks(2);
		// A husk (no burning in daylight), standing still right in front.
		singleplayer.getServer().runCommand("execute as @a at @s run summon husk ^ ^ ^2.2 {NoAI:1b,Rotation:[180f,0f]}");
		context.waitFor(client -> ClientBloomState.profile().owns("bloomlevelup:combat/charge_attack") && client.player.getMainHandItem().is(Items.IRON_AXE));
		context.waitTicks(30);
		// From behind, so the husk in front isn't between the camera and the slam.
		context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));

		context.getInput().holdKey(CombatControls.skillKeyForTests());
		context.waitTicks(30);
		context.getInput().releaseKey(CombatControls.skillKeyForTests());
		context.waitTicks(2);
		context.takeScreenshot("bloom_bc_slam_windup");
		context.waitTicks(5);
		context.takeScreenshot("bloom_bc_slam_hit");
		check(context, "a charged release plays the slam", client -> "bettercombat:one_handed_slam".equals(BetterCombatClient.lastSlamForTests()));
		context.waitTicks(10);
		float health = singleplayer.getServer().computeOnServer(server -> {
			for (var level : server.getAllLevels()) {
				for (var entity : level.getAllEntities()) {
					if (entity.getType() == net.minecraft.world.entity.EntityTypes.HUSK && entity instanceof net.minecraft.world.entity.LivingEntity living) {
						return living.getHealth();
					}
				}
			}

			return -1.0F;
		});
		BloomLevelUp.LOGGER.info("BLOOMTEST slam: husk health after the charged swing {}", health);

		// An iron axe hits for 9, a charged swing for twice that (the husk's 2 armor points take a little off).
		if (!(health >= 0.0F && health < 20.0F - 12.0F)) {
			failures.add("the charged slam hit for double damage (husk health " + health + ")");
		}

		context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
	}

	/**
	 * The stat sheet (the hub's Stats button) with a diamond sword in hand and Predator owned: the summary, the
	 * same with Predator switched to its max, the full breakdown, and the Armor tab's sheet. GUI scale 3.
	 */
	private void statSheetScreenshots(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("bloom admin xp @a bloomlevelup:armor 100000");
		singleplayer.getServer().runCommand("execute as @a run bloom buy bloomlevelup:combat/predator");
		singleplayer.getServer().runCommand("execute as @a run bloom buy bloomlevelup:armor/iron_skin");
		// A diamond sword with Smite V: the sheet shows its extra damage against undead in green.
		singleplayer.getServer().runOnServer(server -> {
			var smite = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
					.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SMITE);

			for (var serverPlayer : server.getPlayerList().getPlayers()) {
				net.minecraft.world.item.ItemStack sword = new net.minecraft.world.item.ItemStack(Items.DIAMOND_SWORD);
				sword.enchant(smite, 5);
				serverPlayer.getInventory().setItem(0, sword);
			}
		});
		singleplayer.getServer().runCommand("item replace entity @a hotbar.1 with minecraft:bow");
		singleplayer.getServer().runCommand("item replace entity @a hotbar.2 with minecraft:iron_axe");
		singleplayer.getServer().runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate");
		context.waitFor(client -> ClientBloomState.profile().owns("bloomlevelup:combat/predator") && client.player.getInventory().getItem(0).is(Items.DIAMOND_SWORD));

		context.runOnClient(client -> {
			ClientBloomState.selectCategory("bloomlevelup:combat");
			client.options.guiScale().set(3);
			client.resizeGui();
		});
		context.setScreen(HubScreen::new);
		context.waitTicks(2);
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof HubScreen hub) {
				hub.openSheetForTests();
			}
		});
		check(context, "the Combat stat sheet arrives", client -> client.gui.screen() instanceof HubScreen hub && hub.sheetOpenForTests()
				&& ClientBloomState.sheet() != null && ClientBloomState.sheet().category().equals("bloomlevelup:combat")
				&& !ClientBloomState.sheet().summary().isEmpty());
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(3);
		context.takeScreenshot("bloom_sheet_combat");

		context.runOnClient(client -> {
			if (client.gui.screen() instanceof HubScreen hub) {
				hub.sheetForTests(false, "predator");
			}
		});
		check(context, "switching Predator to its max", client -> ClientBloomState.sheet() != null
				&& ClientBloomState.sheet().toggles().stream().anyMatch(toggle -> toggle.id().equals("predator") && toggle.mode() == com.freshorangeslices.bloomlevelup.network.SheetPayloads.Toggle.MAX));
		context.waitTicks(3);
		context.takeScreenshot("bloom_sheet_combat_predator");

		context.runOnClient(client -> {
			if (client.gui.screen() instanceof HubScreen hub) {
				hub.sheetForTests(true, null);
			}
		});
		context.waitTicks(3);
		context.takeScreenshot("bloom_sheet_combat_details");

		// Pin the sword in hand, then pick the iron axe: every line shows how the axe differs from the sword.
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof HubScreen hub) {
				hub.sheetForTests(false, null);
				hub.sheetSlotForTests(-1, 2);
			}
		});
		check(context, "pinning the sword and picking the axe compares them", client -> ClientBloomState.sheet() != null
				&& ClientBloomState.sheet().slot() == 2 && !ClientBloomState.sheet().summary().isEmpty()
				&& ClientBloomState.sheet().summary().get(0).label().getContents() instanceof TranslatableContents note
				&& note.getKey().equals("bloom.sheet.ui.pin.note"));
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(3);
		context.takeScreenshot("bloom_sheet_compare");
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof HubScreen hub) {
				hub.sheetForTests(false, "predator", false);
			}
		});
		context.waitTicks(2);
		context.setScreen(() -> null);

		// A Major's details window links to the sheet, with its switch on Max.
		context.setScreen(HubScreen::new);
		context.waitTicks(2);
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof HubScreen hub) {
				hub.openDetailsForTests("bloomlevelup:combat/predator");
				hub.showInSheetForTests();
			}
		});
		check(context, "the details window's link opens the sheet with Predator on Max", client -> client.gui.screen() instanceof HubScreen hub
				&& hub.sheetOpenForTests() && !hub.detailsOpenForTests() && hub.sheetStateForTests().contains("predator=1"));
		context.setScreen(() -> null);

		context.runOnClient(client -> ClientBloomState.selectCategory("bloomlevelup:armor"));
		context.setScreen(HubScreen::new);
		context.waitTicks(2);
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof HubScreen hub) {
				hub.openSheetForTests();
			}
		});
		check(context, "the Armor stat sheet arrives", client -> ClientBloomState.sheet() != null
				&& ClientBloomState.sheet().category().equals("bloomlevelup:armor") && !ClientBloomState.sheet().summary().isEmpty());
		context.waitTicks(3);
		context.takeScreenshot("bloom_sheet_armor");
		context.setScreen(() -> null);
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

		// tab:scale[:folded] (folded = the stats window folded away, so the window narrows to the page).
		for (String shot : List.of("combat:3", "combat:4", "armor:3", "utility:3", "utility:3:folded", "misc:3")) {
			String[] parts = shot.split(":");
			String tab = parts[0];
			int scale = Integer.parseInt(parts[1]);
			boolean folded = parts.length > 2;
			context.runOnClient(client -> {
				ClientBloomState.selectCategory("bloomlevelup:" + tab);
				HubScreen.foldStatsForTests(folded);
				client.options.guiScale().set(scale);
				client.resizeGui();
			});
			context.setScreen(HubScreen::new);
			context.getInput().setCursorPos(2, 2); // no tooltip in the way
			context.waitTicks(3);
			context.takeScreenshot("bloom_hub_" + tab + (folded ? "_folded" : "") + "_s" + scale);
			context.setScreen(() -> null);
		}

		context.runOnClient(client -> HubScreen.foldStatsForTests(false));

		context.runOnClient(client -> {
			client.options.guiScale().set(0);
			client.resizeGui();
		});
		context.getInput().resizeWindow(854, 480);
		context.waitTicks(2);
	}

	/** Brace!'s HUD icon at 1920x1080, GUI scale 3: lit while crouched still, then cooling down after standing up. */
	private void braceHud(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("bloom admin xp @a bloomlevelup:armor 100000");
		singleplayer.getServer().runCommand("execute as @a run bloom buy bloomlevelup:armor/brace");
		context.waitFor(client -> ClientBloomState.profile().owns(ArmorIds.BRACE));
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(client -> {
			client.options.guiScale().set(3);
			client.resizeGui();
		});

		context.getInput().holdKey(options -> options.keyShift);
		context.waitFor(client -> ClientBloomState.profile().counter(ArmorIds.BRACED) != 0);
		context.waitTicks(2);
		context.takeScreenshot("bloom_hud_braced");
		context.getInput().releaseKey(options -> options.keyShift);
		context.waitFor(client -> ClientBloomState.profile().counter(ArmorIds.BRACED) == 0);
		context.waitTicks(20);
		context.takeScreenshot("bloom_hud_brace_cooldown");

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
		click(context, major, InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTicks(3);
		context.takeScreenshot("bloom_hub_major_details");
		check(context, "right click opens the details window", client -> client.gui.screen() instanceof HubScreen hub && hub.detailsOpenForTests());
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(2);
		check(context, "Esc closes the details window, not the hub", client -> client.gui.screen() instanceof HubScreen hub && !hub.detailsOpenForTests());
		click(context, major, InputConstants.MOUSE_BUTTON_LEFT);
		check(context, "left click switches back on", client -> owned(major) != null && owned(major).enabled());
		click(context, major, InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTicks(3);
		check(context, "right click no longer refunds a single Major", client -> owned(major) != null);
		// The tab's white reset button (past its confirm screen) refunds every Major in the tab.
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof HubScreen hub) {
				hub.resetTabMajorsForTests();
			}
		});
		check(context, "the tab reset refunds it", client -> owned(major) == null);
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
