package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Leftovers of an older pack (FreshOrangeSlices' server is updated by copying the new files over the old, so a file a
 * later version no longer ships stays, and Open Loader keeps loading it: a recipe taken out, a spawn change undone).
 * The pack build lists every file of its data pack in {@code config/openloader/data/legionwarex/files.json}; as the
 * game starts, before any world or data pack loads, the add-on deletes the folder's files it doesn't list, and the
 * folders left empty, and logs them ("LegionWareX pack files:"). Without the list (an older pack, or none) nothing is
 * touched, and nothing outside that folder ever is.
 */
public final class PackFiles {
	public static final String FOLDER = "config/openloader/data/legionwarex";
	public static final String LIST = "files.json";

	private PackFiles() {
	}

	/** Deletes the folder's unlisted files; returns their paths, relative to the folder (empty if there's no list). */
	public static List<String> clean(Path gameDir) {
		Path folder = gameDir.resolve(FOLDER).normalize();
		Path list = folder.resolve(LIST);
		List<String> removed = new ArrayList<>();

		if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(list, LinkOption.NOFOLLOW_LINKS)) {
			return removed;
		}

		Set<String> keep = new HashSet<>();

		try (Reader reader = Files.newBufferedReader(list, StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

			for (JsonElement file : json.getAsJsonArray("files")) {
				keep.add(file.getAsString().replace('\\', '/'));
			}
		} catch (IOException | RuntimeException e) {
			LegionWareX.LOGGER.warn("LegionWareX pack files: {} not readable, nothing removed ({})", list, e.toString());
			return removed;
		}

		if (keep.size() < 2) {
			LegionWareX.LOGGER.warn("LegionWareX pack files: {} lists almost nothing, nothing removed", list);
			return removed;
		}

		keep.add(LIST);
		keep.add("pack.mcmeta");
		List<Path> files;

		try (Stream<Path> walk = Files.walk(folder)) {
			files = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList();
		} catch (IOException e) {
			LegionWareX.LOGGER.warn("LegionWareX pack files: {} not readable, nothing removed ({})", folder, e.toString());
			return removed;
		}

		for (Path file : files) {
			String relative = folder.relativize(file).toString().replace('\\', '/');

			if (!keep.contains(relative) && file.normalize().startsWith(folder)) {
				try {
					Files.delete(file);
					removed.add(relative);
				} catch (IOException e) {
					LegionWareX.LOGGER.warn("LegionWareX pack files: couldn't remove {} ({})", relative, e.toString());
				}
			}
		}

		try (Stream<Path> walk = Files.walk(folder)) {
			for (Path dir : walk.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !path.equals(folder))
					.sorted(Comparator.reverseOrder()).toList()) {
				try (Stream<Path> inside = Files.list(dir)) {
					if (inside.findAny().isEmpty()) {
						Files.delete(dir);
					}
				}
			}
		} catch (IOException e) {
			LegionWareX.LOGGER.debug("LegionWareX pack files: empty folders left ({})", e.toString());
		}

		return removed;
	}

	/** Run from the add-on's constructor. */
	public static void cleanAtStart(Path gameDir) {
		try {
			Path list = gameDir.resolve(FOLDER).resolve(LIST);
			boolean listed = Files.isRegularFile(list);
			List<String> removed = clean(gameDir);

			if (!removed.isEmpty()) {
				LegionWareX.LOGGER.info("LegionWareX pack files: removed {} file(s) an older version left in {}: {}", removed.size(), FOLDER,
						removed.size() > 60 ? removed.subList(0, 60) + " ..." : removed);
			} else if (listed) {
				LegionWareX.LOGGER.info("LegionWareX pack files: {} matches this version's list", FOLDER);
			}
		} catch (RuntimeException e) {
			LegionWareX.LOGGER.warn("LegionWareX pack files: check failed ({})", e.toString());
		}
	}
}
