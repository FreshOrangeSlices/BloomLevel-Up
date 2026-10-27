package com.freshorangeslices.bloomlevelup.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PackDefaultsTest {
	@Test
	void readsThePacksInOrderAndTheShader() {
		PackDefaults.Defaults defaults = PackDefaults.parse(PackDefaults.lines(
				"# comment\nresourcePacks=file/FA.zip|file/FA+Details-v2.3.zip| file/FA+Player-v1.1.zip\nshaderPack=Complementary.zip\nother=1"));
		assertEquals(List.of("file/FA.zip", "file/FA+Details-v2.3.zip", "file/FA+Player-v1.1.zip"), defaults.resourcePacks());
		assertEquals("Complementary.zip", defaults.shaderPack());
		assertNull(PackDefaults.parse(List.of("resourcePacks=a")).shaderPack());
		assertTrue(PackDefaults.parse(List.of("resourcePacks=a")).keys().isEmpty());
	}

	@Test
	void readsKeyBindings() {
		PackDefaults.Defaults defaults = PackDefaults.parse(List.of(
				"keys=iris.keybind.reload:key.keyboard.unknown| key.bloomlevelup.weapon_skill:key.keyboard.v|broken|:key.keyboard.x"));
		assertEquals(Map.of("iris.keybind.reload", "key.keyboard.unknown", "key.bloomlevelup.weapon_skill", "key.keyboard.v"), defaults.keys());
	}

	@Test
	void movesTheListedKeysAndNothingElse() {
		List<String> options = PackDefaults.lines("resourcePacks:[\"vanilla\",\"file/Mine.zip\"]\nkey_iris.keybind.reload:key.keyboard.r\nkey_key.jump:key.keyboard.space");
		List<String> out = PackDefaults.mergeOptions(options, List.of(),
				Map.of("iris.keybind.reload", "key.keyboard.unknown", "keybinds.combat_roll.roll", "key.keyboard.r"));
		assertEquals(List.of("resourcePacks:[\"vanilla\",\"file/Mine.zip\"]", "key_iris.keybind.reload:key.keyboard.unknown",
				"key_key.jump:key.keyboard.space", "key_keybinds.combat_roll.roll:key.keyboard.r"), out, "no packs: the pack list stays as it is");
	}

	@Test
	void switchesThePacksOnAndKeepsEverythingElse() {
		List<String> options = PackDefaults.lines("version:4660\nfov:0.5\nresourcePacks:[\"vanilla\",\"fabric\",\"file/Mine.zip\"]\nguiScale:2\nincompatibleResourcePacks:[]");
		List<String> out = PackDefaults.mergeOptions(options, List.of("file/FA.zip", "file/FA+Player.zip"));
		assertTrue(out.contains("fov:0.5") && out.contains("guiScale:2") && out.contains("version:4660"), "other settings stay");
		assertTrue(out.contains("resourcePacks:[\"vanilla\",\"file/FA.zip\",\"file/FA+Player.zip\",\"file/Mine.zip\"]"),
				"pack's packs in order above vanilla, the player's own on top, no 'fabric': " + out);
		// Not added to the "accepted though incompatible" list: the game skips a pack that's there but fits.
		assertTrue(out.contains("incompatibleResourcePacks:[]"), "incompatible list untouched: " + out);
	}

	@Test
	void forgesModResourcesStayUnderThePacks() {
		List<String> options = PackDefaults.lines("resourcePacks:[\"vanilla\",\"file/Mine.zip\",\"mod_resources\"]");
		List<String> out = PackDefaults.mergeOptions(options, List.of("file/FA.zip"));
		assertEquals(List.of("resourcePacks:[\"vanilla\",\"mod_resources\",\"file/FA.zip\",\"file/Mine.zip\"]"), out);
	}

	@Test
	void writesAnOptionsFileFromNothing() {
		List<String> out = PackDefaults.mergeOptions(new ArrayList<>(), List.of("file/FA.zip"));
		assertEquals(List.of("resourcePacks:[\"vanilla\",\"file/FA.zip\"]"), out);
	}

	@Test
	void anOptionsFileFromNothingGetsTheVersionSoKeysByNameLoad() {
		assertEquals(List.of("version:4671", "fov:0.5"), PackDefaults.withVersion(PackDefaults.lines("fov:0.5"), 4671));
		assertEquals(List.of("version:100", "fov:0.5"), PackDefaults.withVersion(PackDefaults.lines("version:100\nfov:0.5"), 4671), "the game's own version stays");
		assertEquals(List.of("fov:0.5"), PackDefaults.withVersion(PackDefaults.lines("fov:0.5"), null));
		assertEquals(Integer.valueOf(4671), PackDefaults.dataVersion("{\n  \"id\": \"26.3\",\n  \"world_version\": 4671,\n  \"series_id\": \"main\"}"));
		assertNull(PackDefaults.dataVersion("{}"));
	}

	@Test
	void readsEscapedJson() {
		assertEquals(List.of("file/A&B.zip", "q\"uote"), PackDefaults.jsonList("[\"file/A\\u0026B.zip\",\"q\\\"uote\"]"));
		assertEquals(List.of(), PackDefaults.jsonList("not json"));
	}

	@Test
	void picksTheShaderAndKeepsTheRestOfIris() {
		List<String> out = PackDefaults.mergeIris(PackDefaults.lines("enableShaders=false\nmaxShadowRenderDistance=32\nshaderPack=Other.zip"), "C.zip");
		assertEquals(List.of("maxShadowRenderDistance=32", "enableShaders=true", "shaderPack=C.zip"), out);
	}

	@Test
	void anOlderIrisFileIsUpdatedToo() throws Exception {
		Path config = Files.createTempDirectory("bloom-pack-defaults-iris").resolve("config");
		Files.createDirectories(config.resolve("bloomlevelup"));
		Files.writeString(config.resolve(PackDefaults.FILE), "shaderPack=C.zip\n", StandardCharsets.UTF_8);
		Files.writeString(config.resolve("iris.properties"), "enableShaders=false\nshaderPack=Old.zip\n", StandardCharsets.UTF_8);

		assertTrue(PackDefaults.apply(config, config.resolve("options.txt"), 3465, line -> { }));
		assertTrue(Files.readString(config.resolve("oculus.properties")).contains("shaderPack=C.zip"));
		assertTrue(Files.readString(config.resolve("iris.properties")).contains("enableShaders=true"));
		assertTrue(Files.readString(config.resolve("iris.properties")).contains("shaderPack=C.zip"));
	}

	@Test
	void appliesOncePerVersionOfTheDefaults() throws Exception {
		Path dir = Files.createTempDirectory("bloom-pack-defaults");
		Path config = dir.resolve("config");
		Path options = dir.resolve("options.txt");
		Files.createDirectories(config.resolve("bloomlevelup"));
		Files.writeString(config.resolve(PackDefaults.FILE), "resourcePacks=file/FA.zip\nshaderPack=C.zip\nkeys=iris.keybind.reload:key.keyboard.unknown\n",
				StandardCharsets.UTF_8);
		List<String> logs = new ArrayList<>();

		assertTrue(PackDefaults.apply(config, options, 4671, logs::add));
		assertTrue(Files.readString(options).startsWith("version:4671\n"), "a version line first, so the game doesn't run the old key fixes");
		assertTrue(Files.readString(options).contains("\"file/FA.zip\""));
		assertTrue(Files.readString(options).contains("key_iris.keybind.reload:key.keyboard.unknown"));
		assertTrue(Files.readString(config.resolve("oculus.properties")).contains("shaderPack=C.zip"), "Oculus's settings file");
		assertFalse(Files.exists(config.resolve("iris.properties")), "no iris.properties made for nothing");

		// The player switches the pack off: the same defaults don't switch it back on.
		Files.writeString(options, "resourcePacks:[\"vanilla\"]\n", StandardCharsets.UTF_8);
		assertFalse(PackDefaults.apply(config, options, logs::add));
		assertFalse(Files.readString(options).contains("FA.zip"));

		// A pack update that changes the list applies again.
		Files.writeString(config.resolve(PackDefaults.FILE), "resourcePacks=file/FA.zip|file/New.zip\n", StandardCharsets.UTF_8);
		assertTrue(PackDefaults.apply(config, options, logs::add));
		assertTrue(Files.readString(options).contains("\"file/FA.zip\",\"file/New.zip\""));

		// No data version and no options.txt yet: the packs go on, the keys wait (a key by name would fail the file).
		Files.delete(options);
		Files.writeString(config.resolve(PackDefaults.FILE), "resourcePacks=file/FA.zip\nkeys=iris.keybind.reload:key.keyboard.unknown\n", StandardCharsets.UTF_8);
		assertTrue(PackDefaults.apply(config, options, logs::add));
		assertTrue(Files.readString(options).contains("\"file/FA.zip\"") && !Files.readString(options).contains("key_"), Files.readString(options));

		// Outside the pack (no defaults file): nothing.
		assertFalse(PackDefaults.apply(dir.resolve("nothing"), options, logs::add));
	}
}
