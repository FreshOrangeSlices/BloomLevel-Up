package com.freshorangeslices.bloomlevelup.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Setting one value in another mod's config file, keeping the rest of the file as it is (the player's own settings,
 * comments, order). Three kinds of file, by extension: {@code .json} (a {@code /} path into nested objects, the value
 * as JSON), {@code .toml} (a key, optionally under a {@code [Section]} written in front of it, the value as TOML), and
 * everything else as {@code key = value} lines (Xaero's {@code .cfg}, {@code .properties}). A key that isn't there yet
 * is added; a file that isn't there yet is started with just that key (the mod fills in the rest when it loads).
 * Plain Java, no game classes, for {@link PackDefaults}.
 */
public final class PackSettings {
	private static final Pattern BARE_TOML_KEY = Pattern.compile("[A-Za-z0-9_-]+");

	private PackSettings() {
	}

	/** The file's lines with the setting applied, or null when the file can't be read as its kind (left alone then). */
	public static List<String> apply(String fileName, List<String> lines, String key, String value) {
		String name = fileName.toLowerCase(java.util.Locale.ROOT);

		if (name.endsWith(".json")) {
			return setJson(lines, key, value);
		}

		if (name.endsWith(".toml")) {
			return setToml(lines, key, value);
		}

		return setLine(lines, key, value, name.endsWith(".cfg") ? " = " : "=");
	}

	// ------------------------------------------------------------------ key = value lines

	/** {@code key = value}: the first line with that key gets the value (its own spacing kept), or one is added. */
	public static List<String> setLine(List<String> lines, String key, String value, String separator) {
		List<String> out = new ArrayList<>(lines);

		for (int i = 0; i < out.size(); i++) {
			String line = out.get(i);
			String trimmed = line.strip();

			if (trimmed.startsWith("#") || !trimmed.contains("=")) {
				continue;
			}

			if (trimmed.substring(0, trimmed.indexOf('=')).strip().equals(key)) {
				int eq = line.indexOf('=');
				String space = line.substring(eq + 1).startsWith(" ") ? " " : "";
				out.set(i, line.substring(0, eq + 1) + space + value);
				return out;
			}
		}

		out.add(key + separator + value);
		return out;
	}

	// ------------------------------------------------------------------ TOML (Forge's config files)

	/**
	 * {@code [Section]key} or {@code key}: the key's line in that section gets the value, or the key is added at the
	 * end of the section (indented like its neighbours), or the section is added at the end. Section names compare
	 * without their quotes ({@code ["Gameplay Changes"]} is {@code [Gameplay Changes]}), keys too.
	 */
	public static List<String> setToml(List<String> lines, String spec, String value) {
		String section = "";
		String key = spec.strip();

		if (key.startsWith("[") && key.contains("]")) {
			section = header(key.substring(1, key.indexOf(']')));
			key = key.substring(key.indexOf(']') + 1).strip();
		}

		List<String> out = new ArrayList<>(lines);
		String current = "";
		int lastInSection = section.isEmpty() ? -1 : -2;
		String indent = null;

		for (int i = 0; i < out.size(); i++) {
			String line = out.get(i);
			String trimmed = line.strip();

			if (trimmed.startsWith("[") && !trimmed.startsWith("[[") && trimmed.contains("]")) {
				current = header(trimmed.substring(1, trimmed.lastIndexOf(']')));

				if (current.equals(section)) {
					lastInSection = i;
				}

				continue;
			}

			if (!current.equals(section)) {
				continue;
			}

			if (!trimmed.isEmpty()) {
				lastInSection = i;
			}

			if (trimmed.startsWith("#") || !trimmed.contains("=")) {
				continue;
			}

			String lineKey = unquote(trimmed.substring(0, trimmed.indexOf('=')).strip());

			if (indent == null) {
				indent = line.substring(0, line.length() - line.stripLeading().length());
			}

			if (lineKey.equals(key)) {
				int eq = line.indexOf('=');
				out.set(i, line.substring(0, eq + 1) + " " + value);
				return out;
			}
		}

		String entry = (indent == null ? (section.isEmpty() ? "" : "\t") : indent) + tomlKey(key) + " = " + value;

		if (lastInSection == -2) {
			if (!out.isEmpty() && !out.get(out.size() - 1).isBlank()) {
				out.add("");
			}

			List<String> parts = new ArrayList<>();

			for (String part : section.split("\\.")) {
				parts.add(tomlKey(part));
			}

			out.add("[" + String.join(".", parts) + "]");
			out.add(entry);
		} else if (lastInSection == -1) {
			// A root key goes before the first section, so it stays a root key.
			int first = 0;

			while (first < out.size() && !out.get(first).strip().startsWith("[")) {
				first++;
			}

			out.add(first, entry);
		} else {
			out.add(lastInSection + 1, entry);
		}

		return out;
	}

	/** A section header's dotted name without quotes or spaces around its parts. */
	static String header(String raw) {
		List<String> parts = new ArrayList<>();
		StringBuilder part = new StringBuilder();
		boolean quoted = false;

		for (char c : raw.toCharArray()) {
			if (c == '"') {
				quoted = !quoted;
			} else if (c == '.' && !quoted) {
				parts.add(part.toString().strip());
				part.setLength(0);
			} else {
				part.append(c);
			}
		}

		parts.add(part.toString().strip());
		return String.join(".", parts);
	}

	private static String unquote(String key) {
		return key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"") ? key.substring(1, key.length() - 1).replace("\\\"", "\"") : key;
	}

	private static String tomlKey(String key) {
		return BARE_TOML_KEY.matcher(key).matches() ? key : "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	// ------------------------------------------------------------------ JSON

	/** A {@code /} path into nested objects (made where missing) set to the value; null if the file isn't JSON. */
	public static List<String> setJson(List<String> lines, String path, String value) {
		String text = String.join("\n", lines).strip();
		Object root;
		Object parsed;

		try {
			root = text.isEmpty() ? new LinkedHashMap<String, Object>() : Json.parse(text);
			parsed = Json.parse(value);
		} catch (IllegalArgumentException e) {
			return null;
		}

		if (!(root instanceof Map)) {
			return null;
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> node = (Map<String, Object>) root;
		String[] parts = path.split("/");

		for (int i = 0; i < parts.length - 1; i++) {
			Object next = node.get(parts[i]);

			if (next == null) {
				next = new LinkedHashMap<String, Object>();
				node.put(parts[i], next);
			}

			if (!(next instanceof Map)) {
				return null;
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> child = (Map<String, Object>) next;
			node = child;
		}

		node.put(parts[parts.length - 1], parsed);
		return new ArrayList<>(List.of(Json.write(root).split("\n", -1)));
	}

	/** A small JSON reader and pretty writer: objects keep their key order, numbers their spelling. */
	static final class Json {
		/** JSON's null, so a key set to null isn't taken for a missing one. */
		static final Object NULL = new Object() {
			@Override
			public String toString() {
				return "null";
			}
		};

		/** A number as written ({@code 1}, {@code 0.5}, {@code 1e3}). */
		record Number(String text) {
		}

		private final String text;
		private int at;

		private Json(String text) {
			this.text = text;
		}

		static Object parse(String text) {
			Json json = new Json(text);
			Object value = json.value();
			json.space();

			if (json.at != text.length()) {
				throw new IllegalArgumentException("Trailing text at " + json.at);
			}

			return value;
		}

		private void space() {
			while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
				at++;
			}
		}

		private Object value() {
			space();

			if (at >= text.length()) {
				throw new IllegalArgumentException("Unexpected end");
			}

			char c = text.charAt(at);

			if (c == '{') {
				Map<String, Object> map = new LinkedHashMap<>();
				at++;
				space();

				if (peek('}')) {
					at++;
					return map;
				}

				while (true) {
					space();
					String key = string();
					space();
					expect(':');
					map.put(key, value());
					space();

					if (peek(',')) {
						at++;
						continue;
					}

					expect('}');
					return map;
				}
			}

			if (c == '[') {
				List<Object> list = new ArrayList<>();
				at++;
				space();

				if (peek(']')) {
					at++;
					return list;
				}

				while (true) {
					list.add(value());
					space();

					if (peek(',')) {
						at++;
						continue;
					}

					expect(']');
					return list;
				}
			}

			if (c == '"') {
				return string();
			}

			if (text.startsWith("true", at)) {
				at += 4;
				return Boolean.TRUE;
			}

			if (text.startsWith("false", at)) {
				at += 5;
				return Boolean.FALSE;
			}

			if (text.startsWith("null", at)) {
				at += 4;
				return NULL;
			}

			int start = at;

			while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
				at++;
			}

			if (start == at) {
				throw new IllegalArgumentException("Unexpected '" + c + "' at " + at);
			}

			return new Number(text.substring(start, at));
		}

		private boolean peek(char c) {
			return at < text.length() && text.charAt(at) == c;
		}

		private void expect(char c) {
			if (!peek(c)) {
				throw new IllegalArgumentException("Expected '" + c + "' at " + at);
			}

			at++;
		}

		private String string() {
			expect('"');
			StringBuilder out = new StringBuilder();

			while (at < text.length()) {
				char c = text.charAt(at++);

				if (c == '"') {
					return out.toString();
				}

				if (c != '\\') {
					out.append(c);
					continue;
				}

				if (at >= text.length()) {
					break;
				}

				char escaped = text.charAt(at++);

				switch (escaped) {
					case 'n' -> out.append('\n');
					case 't' -> out.append('\t');
					case 'r' -> out.append('\r');
					case 'b' -> out.append('\b');
					case 'f' -> out.append('\f');
					case 'u' -> {
						if (at + 4 > text.length()) {
							throw new IllegalArgumentException("Bad \\u escape");
						}

						out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
						at += 4;
					}
					default -> out.append(escaped);
				}
			}

			throw new IllegalArgumentException("Unclosed string");
		}

		static String write(Object value) {
			StringBuilder out = new StringBuilder();
			write(out, value, "");
			return out.toString();
		}

		private static void write(StringBuilder out, Object value, String indent) {
			if (value instanceof Map<?, ?> map) {
				if (map.isEmpty()) {
					out.append("{}");
					return;
				}

				out.append("{\n");
				int i = 0;

				for (Map.Entry<?, ?> entry : map.entrySet()) {
					out.append(indent).append("  ");
					quote(out, String.valueOf(entry.getKey()));
					out.append(": ");
					write(out, entry.getValue(), indent + "  ");
					out.append(++i < map.size() ? ",\n" : "\n");
				}

				out.append(indent).append('}');
			} else if (value instanceof List<?> list) {
				if (list.isEmpty()) {
					out.append("[]");
					return;
				}

				out.append("[\n");

				for (int i = 0; i < list.size(); i++) {
					out.append(indent).append("  ");
					write(out, list.get(i), indent + "  ");
					out.append(i + 1 < list.size() ? ",\n" : "\n");
				}

				out.append(indent).append(']');
			} else if (value instanceof String string) {
				quote(out, string);
			} else if (value instanceof Number number) {
				out.append(number.text());
			} else {
				out.append(value);
			}
		}

		private static void quote(StringBuilder out, String string) {
			out.append('"');

			for (char c : string.toCharArray()) {
				switch (c) {
					case '"' -> out.append("\\\"");
					case '\\' -> out.append("\\\\");
					case '\n' -> out.append("\\n");
					case '\t' -> out.append("\\t");
					case '\r' -> out.append("\\r");
					case '\b' -> out.append("\\b");
					case '\f' -> out.append("\\f");
					default -> {
						if (c < 0x20) {
							out.append(String.format("\\u%04x", (int) c));
						} else {
							out.append(c);
						}
					}
				}
			}

			out.append('"');
		}
	}
}
