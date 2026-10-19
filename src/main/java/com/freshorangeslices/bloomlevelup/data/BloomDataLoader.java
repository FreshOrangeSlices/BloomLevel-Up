package com.freshorangeslices.bloomlevelup.data;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StrictJsonParser;

import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.definition.CategoryDefinition;
import com.freshorangeslices.bloomlevelup.definition.DefinitionSet;
import com.freshorangeslices.bloomlevelup.definition.MajorDefinition;

/**
 * Loads {@code data/<ns>/bloom/category/*.json} and {@code data/<ns>/bloom/major/**.json} on every data
 * reload, validates them into a {@link DefinitionSet} off-thread, then swaps it in on the server thread.
 */
public final class BloomDataLoader extends SimpleReloadListener<DefinitionSet> {
	public static final Identifier ID = BloomLevelUp.id("definitions");

	private static final FileToIdConverter CATEGORIES = FileToIdConverter.json("bloom/category");
	private static final FileToIdConverter MAJORS = FileToIdConverter.json("bloom/major");

	@Override
	protected DefinitionSet prepare(SharedState state) {
		ResourceManager manager = state.resourceManager();
		List<CategoryDefinition> categories = load(manager, CATEGORIES, DefinitionCodecs.RawCategory.CODEC, "category",
				(id, raw) -> raw.toDefinition(id));
		List<MajorDefinition> majors = load(manager, MAJORS, DefinitionCodecs.RawMajor.CODEC, "major",
				(id, raw) -> raw.toDefinition(id));
		return DefinitionSet.build(categories, majors);
	}

	@Override
	protected void apply(DefinitionSet prepared, SharedState state) {
		for (String warning : prepared.warnings()) {
			BloomLevelUp.LOGGER.warn("[Bloom definitions] {}", warning);
		}

		BloomLevelUp.LOGGER.info("Loaded {} Bloom categories, {} majors", prepared.categories().size(), prepared.majors().size());
		BloomServices.setDefinitions(prepared);
	}

	private static <R, D> List<D> load(ResourceManager manager, FileToIdConverter converter, Codec<R> codec, String kind,
			Converter<R, D> toDefinition) {
		List<D> out = new ArrayList<>();

		for (Map.Entry<Identifier, Resource> entry : converter.listMatchingResources(manager).entrySet()) {
			Identifier id = converter.fileToId(entry.getKey());

			try (Reader reader = entry.getValue().openAsReader()) {
				JsonElement json = StrictJsonParser.parse(reader);
				DataResult<R> result = codec.parse(JsonOps.INSTANCE, json);
				result.resultOrPartial(error -> BloomLevelUp.LOGGER.error("Couldn't parse Bloom {} '{}': {}", kind, id, error))
						.ifPresent(raw -> out.add(toDefinition.convert(id, raw)));
			} catch (IOException | JsonParseException | IllegalArgumentException e) {
				BloomLevelUp.LOGGER.error("Couldn't read Bloom {} '{}'", kind, id, e);
			}
		}

		return out;
	}

	@FunctionalInterface
	private interface Converter<R, D> {
		D convert(Identifier id, R raw);
	}
}
