package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.world.PackFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** An older pack's leftover data pack files go; listed ones and anything outside the folder stay. */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PackFilesGameTests {
	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text);
	}

	@GameTest(template = "empty")
	public static void unlistedFilesGoListedOnesStay(GameTestHelper helper) {
		Path game = null;

		try {
			game = Files.createTempDirectory("legionwarex-packfiles");
			Path folder = game.resolve(PackFiles.FOLDER);
			write(folder.resolve("pack.mcmeta"), "{}");
			write(folder.resolve("files.json"), "{\"files\": [\"pack.mcmeta\", \"data/a/recipes/keep.json\", \"files.json\"]}");
			write(folder.resolve("data/a/recipes/keep.json"), "{}");
			write(folder.resolve("data/a/recipes/old.json"), "{}");
			write(folder.resolve("data/b/tags/items/gone.json"), "{}");
			write(game.resolve("config/openloader/data/other/data/c/x.json"), "{}");
			List<String> removed = PackFiles.clean(game);
			helper.assertTrue(removed.containsAll(List.of("data/a/recipes/old.json", "data/b/tags/items/gone.json")) && removed.size() == 2, "the two leftovers go: " + removed);
			helper.assertTrue(Files.exists(folder.resolve("data/a/recipes/keep.json")) && Files.exists(folder.resolve("pack.mcmeta")), "listed files stay");
			helper.assertTrue(!Files.exists(folder.resolve("data/b")), "a folder left empty goes");
			helper.assertTrue(Files.exists(game.resolve("config/openloader/data/other/data/c/x.json")), "nothing outside the pack's folder is touched");
			Files.delete(folder.resolve("files.json"));
			write(folder.resolve("data/a/recipes/new.json"), "{}");
			helper.assertTrue(PackFiles.clean(game).isEmpty() && Files.exists(folder.resolve("data/a/recipes/new.json")), "without the list nothing is removed");
		} catch (IOException e) {
			helper.fail("temp files: " + e);
		} finally {
			if (game != null) {
				try (Stream<Path> walk = Files.walk(game)) {
					walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
				} catch (IOException ignored) {
				}
			}
		}

		helper.succeed();
	}
}
