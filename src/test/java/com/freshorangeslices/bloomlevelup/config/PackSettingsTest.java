package com.freshorangeslices.bloomlevelup.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PackSettingsTest {
	@Test
	void setsACfgLineAndKeepsTheRest() {
		List<String> out = PackSettings.apply("xaero/minimap/profiles/default.cfg",
				PackDefaults.lines("# comment\nprofile_name = Default\nminimap_shape = 0\nradar_main_dot_size = 2"), "minimap_shape", "1");
		assertEquals(List.of("# comment", "profile_name = Default", "minimap_shape = 1", "radar_main_dot_size = 2"), out);
		assertEquals(List.of("a=1", "b=2"), PackSettings.apply("x.properties", PackDefaults.lines("a=1"), "b", "2"), "a missing key is added");
		assertEquals(List.of("minimap_shape = 1"), PackSettings.apply("new.cfg", new java.util.ArrayList<>(), "minimap_shape", "1"), "a new file");
	}

	@Test
	void setsATomlKeyInItsSection() {
		List<String> toml = PackDefaults.lines("#Client\n[Rolling]\n\t#Will there be a GUI element\n\t\"Will there be a GUI element displaying current roll count\" = true\n"
				+ "\t\"Roll GUI element X offset\" = 0\n\n[\"Boss bars\"]\n\tdefault = \"VANILLA\"");
		List<String> out = PackSettings.apply("block_factorys_bosses-client.toml", toml,
				"[Rolling]Will there be a GUI element displaying current roll count", "false");
		assertTrue(out.contains("\t\"Will there be a GUI element displaying current roll count\" = false"), String.join("\n", out));
		assertTrue(out.contains("\tdefault = \"VANILLA\""), "other sections stay");

		List<String> quoted = PackSettings.apply("x.toml", toml, "[Boss bars]default", "\"OFF\"");
		assertTrue(quoted.contains("\tdefault = \"OFF\""), "a quoted section name matches without its quotes: " + quoted);

		List<String> added = PackSettings.apply("x.toml", toml, "[Rolling]new_key", "3");
		assertEquals("\tnew_key = 3", added.get(added.indexOf("\t\"Roll GUI element X offset\" = 0") + 1), "added at the end of its section");

		List<String> section = PackSettings.apply("x.toml", PackDefaults.lines("[a]\n\tb = 1"), "[Gameplay Changes]Can players use the Roll ability", "false");
		assertEquals(List.of("[a]", "\tb = 1", "", "[\"Gameplay Changes\"]", "\t\"Can players use the Roll ability\" = false"), section);

		assertEquals(List.of("enable_roll = false", "[x]", "\ty = 1"),
				PackSettings.apply("x.toml", PackDefaults.lines("[x]\n\ty = 1"), "enable_roll", "false"), "a root key goes before the first section");
		assertEquals(List.of("enable_roll = true"), PackSettings.apply("x.toml", PackDefaults.lines("enable_roll = false"), "enable_roll", "true"));
	}

	@Test
	void setsAJsonPathAndKeepsTheRest() {
		List<String> json = PackDefaults.lines("{\n  \"ModSettings\": {\n    \"enable_auto_refill\": true,\n    \"other\": 0.5\n  },\n  \"list\": [1, \"a\\\"b\"]\n}");
		String out = String.join("\n", PackSettings.apply("inventoryprofiles.json", json, "ModSettings/enable_auto_refill", "false"));
		assertEquals("{\n  \"ModSettings\": {\n    \"enable_auto_refill\": false,\n    \"other\": 0.5\n  },\n  \"list\": [\n    1,\n    \"a\\\"b\"\n  ]\n}", out);

		String made = String.join("\n", PackSettings.apply("new.json", new java.util.ArrayList<>(), "A/b", "{\"c\": [true, null]}"));
		assertEquals("{\n  \"A\": {\n    \"b\": {\n      \"c\": [\n        true,\n        null\n      ]\n    }\n  }\n}", made, "objects made where missing");

		assertNull(PackSettings.apply("bad.json", PackDefaults.lines("not json"), "a", "1"), "a file that isn't JSON is left alone");
		assertNull(PackSettings.apply("x.json", PackDefaults.lines("{\"a\": 1}"), "a/b", "1"), "a path through a number is refused");
	}
}
