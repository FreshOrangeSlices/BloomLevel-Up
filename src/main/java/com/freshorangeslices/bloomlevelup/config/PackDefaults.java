package com.freshorangeslices.bloomlevelup.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Bloom Pack's first-launch look: its resource packs switched on in the right order and its shader picked.
 * Launchers don't reliably copy a pack's own options.txt (and copying it would wipe a player's settings on every
 * update), so the pack ships {@code config/bloomlevelup/pack-defaults.properties} instead and Bloom applies it
 * before the game starts, once per change of that file. Only the resource pack list and the shader are touched;
 * every other setting stays the player's. Plain Java, no game classes, so it can be unit tested.
 */
public final class PackDefaults {
	public static final String FILE = "bloomlevelup/pack-defaults.properties";
	public static final String MARKER = "bloomlevelup/pack-defaults.applied";

	private PackDefaults() {
	}

	/** What the pack wants: resource packs bottom to top (ids as options.txt has them), and a shader pack or null. */
	public record Defaults(List<String> resourcePacks, String shaderPack) {
	}

	/** Reads {@code resourcePacks=a|b|c} and {@code shaderPack=name} lines; anything else is ignored. */
	public static Defaults parse(List<String> lines) {
		List<String> packs = new ArrayList<>();
		String shader = null;

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
			}
		}

		return new Defaults(List.copyOf(packs), shader);
	}

	/**
	 * options.txt with the pack's resource packs on, in order, above vanilla; packs the player added themselves stay
	 * on, above the pack's. The pack's packs are also marked as accepted even if the game thinks they're for another
	 * version (it would otherwise switch them off again). The old "fabric" entry is dropped (26.3 has no such pack).
	 */
	public static List<String> mergeOptions(List<String> lines, List<String> packs) {
		List<String> out = new ArrayList<>(lines);
		List<String> current = jsonList(value(out, "resourcePacks"));
		List<String> on = new ArrayList<>();
		on.add("vanilla");
		on.addAll(packs);

		for (String pack : current) {
			if (!on.contains(pack) && !pack.equals("fabric")) {
				on.add(pack);
			}
		}

		Set<String> accepted = new LinkedHashSet<>(jsonList(value(out, "incompatibleResourcePacks")));
		accepted.addAll(packs);
		put(out, "resourcePacks", toJson(on));
		put(out, "incompatibleResourcePacks", toJson(new ArrayList<>(accepted)));
		return out;
	}

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

	/**
	 * Applies the defaults in {@code configDir} to {@code optionsFile} and Iris's config, unless this exact version
	 * of them was applied before. Returns whether it changed anything.
	 */
	public static boolean apply(Path configDir, Path optionsFile, Consumer<String> log) throws IOException {
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

		if (!defaults.resourcePacks().isEmpty()) {
			List<String> options = Files.isRegularFile(optionsFile) ? Files.readAllLines(optionsFile, StandardCharsets.UTF_8) : List.of();
			Files.write(optionsFile, mergeOptions(options, defaults.resourcePacks()), StandardCharsets.UTF_8);
		}

		if (defaults.shaderPack() != null) {
			Path iris = configDir.resolve("iris.properties");
			List<String> lines = Files.isRegularFile(iris) ? Files.readAllLines(iris, StandardCharsets.UTF_8) : List.of();
			Files.write(iris, mergeIris(lines, defaults.shaderPack()), StandardCharsets.UTF_8);
		}

		Files.createDirectories(marker.getParent());
		Files.writeString(marker, stamp + "\n", StandardCharsets.UTF_8);
		log.accept("Bloom Pack defaults applied: " + defaults.resourcePacks().size() + " resource packs on"
				+ (defaults.shaderPack() == null ? "" : ", shader " + defaults.shaderPack()));
		return true;
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
