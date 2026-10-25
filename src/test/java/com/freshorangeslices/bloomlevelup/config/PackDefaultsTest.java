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

import org.junit.jupiter.api.Test;

class PackDefaultsTest {
	@Test
	void readsThePacksInOrderAndTheShader() {
		PackDefaults.Defaults defaults = PackDefaults.parse(PackDefaults.lines(
				"# comment\nresourcePacks=file/FA.zip|file/FA+Details-v2.3.zip| file/FA+Player-v1.1.zip\nshaderPack=Complementary.zip\nother=1"));
		assertEquals(List.of("file/FA.zip", "file/FA+Details-v2.3.zip", "file/FA+Player-v1.1.zip"), defaults.resourcePacks());
		assertEquals("Complementary.zip", defaults.shaderPack());
		assertNull(PackDefaults.parse(List.of("resourcePacks=a")).shaderPack());
	}

	@Test
	void switchesThePacksOnAndKeepsEverythingElse() {
		List<String> options = PackDefaults.lines("version:4660\nfov:0.5\nresourcePacks:[\"vanilla\",\"fabric\",\"file/Mine.zip\"]\nguiScale:2");
		List<String> out = PackDefaults.mergeOptions(options, List.of("file/FA.zip", "file/FA+Player.zip"));
		assertTrue(out.contains("fov:0.5") && out.contains("guiScale:2") && out.contains("version:4660"), "other settings stay");
		assertTrue(out.contains("resourcePacks:[\"vanilla\",\"file/FA.zip\",\"file/FA+Player.zip\",\"file/Mine.zip\"]"),
				"pack's packs in order above vanilla, the player's own on top, no 'fabric': " + out);
		assertTrue(out.contains("incompatibleResourcePacks:[\"file/FA.zip\",\"file/FA+Player.zip\"]"), "accepted even if flagged: " + out);
	}

	@Test
	void writesAnOptionsFileFromNothing() {
		List<String> out = PackDefaults.mergeOptions(new ArrayList<>(), List.of("file/FA.zip"));
		assertEquals(List.of("resourcePacks:[\"vanilla\",\"file/FA.zip\"]", "incompatibleResourcePacks:[\"file/FA.zip\"]"), out);
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
	void appliesOncePerVersionOfTheDefaults() throws Exception {
		Path dir = Files.createTempDirectory("bloom-pack-defaults");
		Path config = dir.resolve("config");
		Path options = dir.resolve("options.txt");
		Files.createDirectories(config.resolve("bloomlevelup"));
		Files.writeString(config.resolve(PackDefaults.FILE), "resourcePacks=file/FA.zip\nshaderPack=C.zip\n", StandardCharsets.UTF_8);
		List<String> logs = new ArrayList<>();

		assertTrue(PackDefaults.apply(config, options, logs::add));
		assertTrue(Files.readString(options).contains("\"file/FA.zip\""));
		assertTrue(Files.readString(config.resolve("iris.properties")).contains("shaderPack=C.zip"));

		// The player switches the pack off: the same defaults don't switch it back on.
		Files.writeString(options, "resourcePacks:[\"vanilla\"]\n", StandardCharsets.UTF_8);
		assertFalse(PackDefaults.apply(config, options, logs::add));
		assertFalse(Files.readString(options).contains("FA.zip"));

		// A pack update that changes the list applies again.
		Files.writeString(config.resolve(PackDefaults.FILE), "resourcePacks=file/FA.zip|file/New.zip\n", StandardCharsets.UTF_8);
		assertTrue(PackDefaults.apply(config, options, logs::add));
		assertTrue(Files.readString(options).contains("\"file/FA.zip\",\"file/New.zip\""));

		// Outside the pack (no defaults file): nothing.
		assertFalse(PackDefaults.apply(dir.resolve("nothing"), options, logs::add));
	}
}
