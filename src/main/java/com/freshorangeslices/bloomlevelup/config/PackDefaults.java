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
 * The Bloom Pack's first-launch setup: its resource packs switched on in the right order, its shader picked, a few
 * key bindings moved so no two mods start on the same key, and a few settings in other mods' config files.
 * Launchers don't reliably copy a pack's own options.txt (and copying it would wipe a player's settings on every
 * update), so the pack ships {@code config/bloomlevelup/pack-defaults.properties} instead and Bloom applies it
 * before the game starts. Each part is applied once per change of that part: the marker file records what was set,
 * so a pack update that adds a key or a setting sets just that, and what the player changed since stays theirs. Only
 * the resource pack list, the shader, the listed keys and the listed settings are touched. Plain Java, no game
 * classes, so it can be unit tested.
 */
public final class PackDefaults {
	public static final String FILE = "bloomlevelup/pack-defaults.properties";
	public static final String MARKER = "bloomlevelup/pack-defaults.applied";
	/** Forge's key modifiers, as options.txt writes them after a key ({@code key.keyboard.k:CONTROL}). */
	private static final List<String> MODIFIERS = List.of("CONTROL", "SHIFT", "ALT", "NONE");

	private PackDefaults() {
	}

	/**
	 * What the pack wants: resource packs bottom to top (ids as options.txt has them), a shader pack or null, key
	 * bindings by name ({@code iris.keybind.reload}) with the key each starts on ({@code key.keyboard.v}), settings in
	 * other mods' config files and files copied in whole.
	 */
	public record Defaults(List<String> resourcePacks, String shaderPack, Map<String, String> keys, List<Setting> settings, List<Copy> copies) {
		public Defaults(List<String> resourcePacks, String shaderPack, Map<String, String> keys) {
			this(resourcePacks, shaderPack, keys, List.of(), List.of());
		}
	}

	/**
	 * One value in a config file under config/ ({@link PackSettings} for the kinds of file and how keys are written).
	 * A {@code setting} starts the file if it isn't there yet; a {@code patch} only changes a file that's there (where
	 * a mod copies its own defaults in on a first start, from defaultconfigs/, and the pack ships those).
	 */
	public record Setting(String file, String key, String value, boolean create) {
		public Setting(String file, String key, String value) {
			this(file, key, value, true);
		}
	}

	/** A file the pack ships under config/ copied over another one (a mod's whole default file). */
	public record Copy(String source, String target) {
	}

	/**
	 * Reads {@code resourcePacks=a|b|c}, {@code shaderPack=name} and {@code keys=name:key|name:key} lines (a key can
	 * carry a Forge modifier, {@code name:key.keyboard.k:CONTROL}), and any number of {@code setting=file|key|value}
	 * (a file under config/; the value is everything after the second bar), {@code patch=file|key|value} (the same, for
	 * a file that's already there) and {@code copy=source|target} lines;
	 * anything else is ignored.
	 */
	public static Defaults parse(List<String> lines) {
		List<String> packs = new ArrayList<>();
		String shader = null;
		Map<String, String> keys = new LinkedHashMap<>();
		List<Setting> settings = new ArrayList<>();
		List<Copy> copies = new ArrayList<>();

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

					// A Forge modifier after the key (key.keyboard.k:CONTROL) stays with it, as options.txt has it.
					if (colon > 0 && MODIFIERS.contains(entry.substring(colon + 1).strip())) {
						colon = entry.lastIndexOf(':', colon - 1);
					}

					if (colon > 0 && colon < entry.length() - 1) {
						keys.put(entry.substring(0, colon).strip(), entry.substring(colon + 1).strip());
					}
				}
			} else if (key.equals("setting") || key.equals("patch")) {
				String[] parts = value.split("\\|", 3);

				if (parts.length == 3 && !parts[0].isBlank() && !parts[1].isBlank()) {
					settings.add(new Setting(parts[0].strip(), parts[1].strip(), parts[2].strip(), key.equals("setting")));
				}
			} else if (key.equals("copy")) {
				String[] parts = value.split("\\|", 2);

				if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
					copies.add(new Copy(parts[0].strip(), parts[1].strip()));
				}
			}
		}

		return new Defaults(List.copyOf(packs), shader, Map.copyOf(keys), List.copyOf(settings), List.copyOf(copies));
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
	 * Applies the defaults in {@code configDir} to {@code optionsFile}, Oculus's config and the listed config files:
	 * each part that this version of them hasn't set before (the marker file keeps what was set). Returns whether it
	 * changed anything.
	 *
	 * <p>{@code dataVersion} (the game's, from its version.json) goes on an options.txt Bloom writes from nothing: an
	 * options.txt without a version line is read as one from long ago and run through every old fix, and the 1.13 one
	 * reads each {@code key_} value as a number, so a key written by name fails the whole file. Without it, keys are
	 * only set in an options.txt that already has its version (and wait for a later start otherwise).
	 */
	public static boolean apply(Path configDir, Path optionsFile, Integer dataVersion, Consumer<String> log) throws IOException {
		Path file = configDir.resolve(FILE);

		if (!Files.isRegularFile(file)) {
			return false;
		}

		List<String> raw = Files.readAllLines(file, StandardCharsets.UTF_8);
		Defaults defaults = parse(raw);
		Path marker = configDir.resolve(MARKER);
		Map<String, String> done = readMarker(marker, defaults, raw);
		Map<String, String> record = new LinkedHashMap<>(done);
		List<String> changes = new ArrayList<>();

		String packsStamp = String.join("|", defaults.resourcePacks());
		boolean packsDue = !defaults.resourcePacks().isEmpty() && !packsStamp.equals(done.get("resourcePacks"));
		Map<String, String> keysDue = new java.util.TreeMap<>();

		for (Map.Entry<String, String> key : defaults.keys().entrySet()) {
			if (!key.getValue().equals(done.get("key." + key.getKey()))) {
				keysDue.put(key.getKey(), key.getValue());
			}
		}

		if (packsDue || !keysDue.isEmpty()) {
			List<String> options = withVersion(Files.isRegularFile(optionsFile) ? Files.readAllLines(optionsFile, StandardCharsets.UTF_8)
					: List.of(), dataVersion);

			if (!keysDue.isEmpty() && value(options, "version") == null) {
				log.accept("Bloom Pack defaults: the game's data version is unknown, so the key bindings wait for the next start");
				keysDue = Map.of();
			}

			if (packsDue || !keysDue.isEmpty()) {
				Files.write(optionsFile, mergeOptions(options, packsDue ? defaults.resourcePacks() : List.of(), keysDue), StandardCharsets.UTF_8);
			}

			if (packsDue) {
				record.put("resourcePacks", packsStamp);
				changes.add(defaults.resourcePacks().size() + " resource packs on");
			}

			for (Map.Entry<String, String> key : keysDue.entrySet()) {
				record.put("key." + key.getKey(), key.getValue());
			}

			if (!keysDue.isEmpty()) {
				changes.add("keys " + keysDue);
			}
		}

		if (defaults.shaderPack() != null && !defaults.shaderPack().equals(done.get("shaderPack"))) {
			for (String name : SHADER_CONFIGS) {
				Path shaderConfig = configDir.resolve(name);

				if (name.equals(SHADER_CONFIGS.get(0)) || Files.isRegularFile(shaderConfig)) {
					List<String> lines = Files.isRegularFile(shaderConfig) ? Files.readAllLines(shaderConfig, StandardCharsets.UTF_8) : List.of();
					Files.write(shaderConfig, mergeIris(lines, defaults.shaderPack()), StandardCharsets.UTF_8);
				}
			}

			record.put("shaderPack", defaults.shaderPack());
			changes.add("shader " + defaults.shaderPack());
		}

		for (Copy copy : defaults.copies()) {
			Path source = inside(configDir, copy.source());
			Path target = inside(configDir, copy.target());

			if (source == null || target == null || !Files.isRegularFile(source)) {
				log.accept("Bloom Pack defaults: can't copy " + copy.source() + " to " + copy.target());
				continue;
			}

			String stamp = stamp(Files.readString(source, StandardCharsets.UTF_8));

			if (!stamp.equals(done.get("copy." + copy.target()))) {
				Files.createDirectories(target.getParent());
				Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				record.put("copy." + copy.target(), stamp);
				changes.add(copy.target());
			}
		}

		for (Setting setting : defaults.settings()) {
			String id = "setting." + setting.file() + "|" + setting.key();
			String stamp = stamp(setting.value());

			if (stamp.equals(done.get(id))) {
				continue;
			}

			Path target = inside(configDir, setting.file());

			if (target != null && !setting.create() && !Files.isRegularFile(target)) {
				// Not there yet: the mod starts it from the pack's defaultconfigs/ copy, which already has the value.
				record.put(id, stamp);
				continue;
			}

			List<String> lines = target == null ? null
					: Files.isRegularFile(target) ? Files.readAllLines(target, StandardCharsets.UTF_8) : new ArrayList<>();
			List<String> out = lines == null ? null : PackSettings.apply(setting.file(), lines, setting.key(), setting.value());

			if (out == null) {
				log.accept("Bloom Pack defaults: can't set " + setting.key() + " in " + setting.file() + " (not a file of its kind)");
				continue;
			}

			Files.createDirectories(target.getParent());
			Files.write(target, out, StandardCharsets.UTF_8);
			record.put(id, stamp);
			changes.add(setting.file() + " " + setting.key());
		}

		if (!record.equals(done) || !Files.isRegularFile(marker)) {
			writeMarker(marker, record);
		}

		if (changes.isEmpty()) {
			return false;
		}

		log.accept("Bloom Pack defaults applied: " + String.join(", ", changes));
		return true;
	}

	/** A path under config/, or null for one that would leave it. */
	private static Path inside(Path configDir, String relative) {
		Path base = configDir.toAbsolutePath().normalize();
		Path path = base.resolve(relative).normalize();
		return path.startsWith(base) && !path.equals(base) ? path : null;
	}

	private static String stamp(String text) {
		return Integer.toHexString(text.strip().hashCode());
	}

	/**
	 * What was set before, as {@code part=value} lines. A marker from before the parts were tracked holds one stamp for
	 * the whole file: if it's this file's, everything in it was set; if not (an update since), the resource packs and
	 * the shader count as set (an update shouldn't switch shaders back on), and the keys and settings are set again.
	 */
	static Map<String, String> readMarker(Path marker, Defaults defaults, List<String> raw) throws IOException {
		Map<String, String> done = new LinkedHashMap<>();

		if (!Files.isRegularFile(marker)) {
			return done;
		}

		List<String> lines = Files.readAllLines(marker, StandardCharsets.UTF_8);

		if (lines.stream().noneMatch(line -> line.contains("="))) {
			String old = String.join("", lines).strip();
			done.put("resourcePacks", String.join("|", defaults.resourcePacks()));

			if (defaults.shaderPack() != null) {
				done.put("shaderPack", defaults.shaderPack());
			}

			if (old.equals(Integer.toHexString(String.join("\n", raw).strip().hashCode()))) {
				defaults.keys().forEach((name, key) -> done.put("key." + name, key));
			}

			return done;
		}

		for (String line : lines) {
			if (!line.startsWith("#") && line.contains("=")) {
				done.put(line.substring(0, line.indexOf('=')), line.substring(line.indexOf('=') + 1));
			}
		}

		return done;
	}

	private static void writeMarker(Path marker, Map<String, String> record) throws IOException {
		List<String> out = new ArrayList<>();
		out.add("# What the Bloom Pack's defaults have set, so each part is set once per change (delete this file to set them all again).");
		record.forEach((part, value) -> out.add(part + "=" + value));
		Files.createDirectories(marker.getParent());
		Files.write(marker, out, StandardCharsets.UTF_8);
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
