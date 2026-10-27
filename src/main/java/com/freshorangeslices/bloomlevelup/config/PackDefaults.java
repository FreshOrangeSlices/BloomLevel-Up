package com.freshorangeslices.bloomlevelup.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Bloom Pack's first-launch setup: its resource packs switched on in the right order, its shader picked and a few
 * key bindings moved so no two mods start on the same key.
 * Launchers don't reliably copy a pack's own options.txt (and copying it would wipe a player's settings on every
 * update), so the pack ships {@code config/bloomlevelup/pack-defaults.properties} instead and Bloom applies it
 * before the game starts, once per change of that file. Only the resource pack list, the shader and the listed keys
 * are touched; every other setting stays the player's. Plain Java, no game classes, so it can be unit tested.
 */
public final class PackDefaults {
	public static final String FILE = "bloomlevelup/pack-defaults.properties";
	public static final String MARKER = "bloomlevelup/pack-defaults.applied";

	private PackDefaults() {
	}

	/**
	 * What the pack wants: resource packs bottom to top (ids as options.txt has them), a shader pack or null, and key
	 * bindings by name ({@code iris.keybind.reload}) with the key each starts on ({@code key.keyboard.v}).
	 */
	public record Defaults(List<String> resourcePacks, String shaderPack, Map<String, String> keys) {
	}

	/**
	 * Reads {@code resourcePacks=a|b|c}, {@code shaderPack=name} and {@code keys=name:key|name:key} lines; anything
	 * else is ignored.
	 */
	public static Defaults parse(List<String> lines) {
		List<String> packs = new ArrayList<>();
		String shader = null;
		Map<String, String> keys = new LinkedHashMap<>();

		for (String raw : lines) {
			String line = raw.strip();

			if (line.startsWith("#") || !line.contains("=")) {
				continue;
			}

			String key = line.substring(0, line.indexOf('=')).strip();
			String value = line.substring(line.indexOf('=') + 1).strip();

			if (key.equals("resourcePacks")) {
				for (String pack : value.split("\\|")) {
					if (!pack.isBlank()) {
						packs.add(pack.strip());
					}
				}
			} else if (key.equals("shaderPack") && !value.isEmpty()) {
				shader = value;
			} else if (key.equals("keys")) {
				for (String entry : value.split("\\|")) {
					int colon = entry.lastIndexOf(':');

					if (colon > 0 && colon < entry.length() - 1) {
						keys.put(entry.substring(0, colon).strip(), entry.substring(colon + 1).strip());
					}
				}
			}
		}

		return new Defaults(List.copyOf(packs), shader, Map.copyOf(keys));
	}

	/**
	 * options.txt with the pack's resource packs on, in order, above vanilla; packs the player added themselves stay
	 * on, above the pack's. The old "fabric" entry is dropped (26.3 has no such pack); Forge's "mod_resources" stays
	 * right above vanilla, under the pack's. The list of packs accepted
	 * despite a version mismatch is left alone: the game skips a pack for one launch if it's on that list but fits
	 * the version after all.
	 */
	public static List<String> mergeOptions(List<String> lines, List<String> packs) {
		return mergeOptions(lines, packs, Map.of());
	}

	/** As above, and each listed key binding set to its key ({@code key_<name>:<key>}); no packs leaves the list alone. */
	public static List<String> mergeOptions(List<String> lines, List<String> packs, Map<String, String> keys) {
		List<String> out = new ArrayList<>(lines);

		for (Map.Entry<String, String> key : new java.util.TreeMap<>(keys).entrySet()) {
			put(out, "key_" + key.getKey(), key.getValue());
		}

		if (packs.isEmpty()) {
			return out;
		}

		List<String> current = jsonList(value(out, "resourcePacks"));
		List<String> on = new ArrayList<>();
		on.add("vanilla");

		if (current.contains("mod_resources")) {
			on.add("mod_resources");
		}

		on.addAll(packs);

		for (String pack : current) {
			if (!on.contains(pack) && !pack.equals("fabric")) {
				on.add(pack);
			}
		}

		put(out, "resourcePacks", toJson(on));
		return out;
	}

	/**
	 * Where the shader goes: Oculus (Iris's Forge port) keeps Iris's settings in oculus.properties, which is written; an
	 * iris.properties already there (an older install, which Oculus moves over) is updated too.
	 */
	public static final List<String> SHADER_CONFIGS = List.of("oculus.properties", "iris.properties");

	/** iris.properties with shaders on and the pack's shader picked; the player's other Iris settings stay. */
	public static List<String> mergeIris(List<String> lines, String shaderPack) {
		List<String> out = new ArrayList<>();

		for (String line : lines) {
			if (!line.startsWith("enableShaders=") && !line.startsWith("shaderPack=")) {
				out.add(line);
			}
		}

		out.add("enableShaders=true");
		out.add("shaderPack=" + shaderPack);
		return out;
	}

	/** As below, without knowing the game's data version (key bindings then only go into an options.txt the game wrote). */
	public static boolean apply(Path configDir, Path optionsFile, Consumer<String> log) throws IOException {
		return apply(configDir, optionsFile, null, log);
	}

	/**
	 * Applies the defaults in {@code configDir} to {@code optionsFile} and Oculus's config, unless this exact version
	 * of them was applied before. Returns whether it changed anything.
	 *
	 * <p>{@code dataVersion} (the game's, from its version.json) goes on an options.txt Bloom writes from nothing: an
	 * options.txt without a version line is read as one from long ago and run through every old fix, and the 1.13 one
	 * reads each {@code key_} value as a number, so a key written by name fails the whole file. Without it, keys are
	 * only set in an options.txt that already has its version.
	 */
	public static boolean apply(Path configDir, Path optionsFile, Integer dataVersion, Consumer<String> log) throws IOException {
		Path file = configDir.resolve(FILE);

		if (!Files.isRegularFile(file)) {
			return false;
		}

		List<String> raw = Files.readAllLines(file, StandardCharsets.UTF_8);
		String stamp = Integer.toHexString(String.join("\n", raw).strip().hashCode());
		Path marker = configDir.resolve(MARKER);

		if (Files.isRegularFile(marker) && Files.readString(marker, StandardCharsets.UTF_8).strip().equals(stamp)) {
			return false;
		}

		Defaults defaults = parse(raw);

		Map<String, String> keys = defaults.keys();

		if (!defaults.resourcePacks().isEmpty() || !keys.isEmpty()) {
			List<String> options = withVersion(Files.isRegularFile(optionsFile) ? Files.readAllLines(optionsFile, StandardCharsets.UTF_8)
					: List.of(), dataVersion);

			if (!keys.isEmpty() && value(options, "version") == null) {
				log.accept("Bloom Pack defaults: the game's data version is unknown, so the key bindings are left as they are");
				keys = Map.of();
			}

			Files.write(optionsFile, mergeOptions(options, defaults.resourcePacks(), keys), StandardCharsets.UTF_8);
		}

		if (defaults.shaderPack() != null) {
			for (String name : SHADER_CONFIGS) {
				Path shaderConfig = configDir.resolve(name);

				if (name.equals(SHADER_CONFIGS.get(0)) || Files.isRegularFile(shaderConfig)) {
					List<String> lines = Files.isRegularFile(shaderConfig) ? Files.readAllLines(shaderConfig, StandardCharsets.UTF_8) : List.of();
					Files.write(shaderConfig, mergeIris(lines, defaults.shaderPack()), StandardCharsets.UTF_8);
				}
			}
		}

		Files.createDirectories(marker.getParent());
		Files.writeString(marker, stamp + "\n", StandardCharsets.UTF_8);
		log.accept("Bloom Pack defaults applied: " + defaults.resourcePacks().size() + " resource packs on"
				+ (defaults.shaderPack() == null ? "" : ", shader " + defaults.shaderPack())
				+ (keys.isEmpty() ? "" : ", keys " + new java.util.TreeMap<>(keys)));
		return true;
	}

	/** options.txt lines with a {@code version:} line first if they have none and the data version is known. */
	public static List<String> withVersion(List<String> lines, Integer dataVersion) {
		List<String> out = new ArrayList<>(lines);

		if (dataVersion != null && value(out, "version") == null) {
			out.add(0, "version:" + dataVersion);
		}

		return out;
	}

	/** The data version in a version.json ({@code "world_version": 4671}), or null. */
	public static Integer dataVersion(String versionJson) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"world_version\"\\s*:\\s*(\\d+)").matcher(versionJson == null ? "" : versionJson);
		return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
	}

	// ------------------------------------------------------------------ options.txt lines and JSON string lists

	private static String value(List<String> lines, String key) {
		for (String line : lines) {
			if (line.startsWith(key + ":")) {
				return line.substring(key.length() + 1);
			}
		}

		return null;
	}

	private static void put(List<String> lines, String key, String value) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith(key + ":")) {
				lines.set(i, key + ":" + value);
				return;
			}
		}

		lines.add(key + ":" + value);
	}

	/** A JSON array of strings ({@code ["a","b"]}) as a list; anything unreadable is an empty list. */
	public static List<String> jsonList(String json) {
		List<String> out = new ArrayList<>();

		if (json == null) {
			return out;
		}

		String text = json.strip();

		if (!text.startsWith("[") || !text.endsWith("]")) {
			return out;
		}

		StringBuilder current = null;

		for (int i = 1; i < text.length() - 1; i++) {
			char c = text.charAt(i);

			if (current == null) {
				if (c == '"') {
					current = new StringBuilder();
				}

				continue;
			}

			if (c == '\\' && i + 1 < text.length() - 1) {
				char next = text.charAt(++i);

				if (next == 'u' && i + 4 < text.length() - 1) {
					current.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
					i += 4;
				} else {
					current.append(switch (next) {
						case 'n' -> '\n';
						case 't' -> '\t';
						default -> next;
					});
				}
			} else if (c == '"') {
				out.add(current.toString());
				current = null;
			} else {
				current.append(c);
			}
		}

		return out;
	}

	public static String toJson(List<String> values) {
		StringBuilder out = new StringBuilder("[");

		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				out.append(',');
			}

			out.append('"').append(values.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
		}

		return out.append(']').toString();
	}

	/** For tests: lines from text. */
	static List<String> lines(String text) {
		return new ArrayList<>(Arrays.asList(text.split("\n")));
	}
}
