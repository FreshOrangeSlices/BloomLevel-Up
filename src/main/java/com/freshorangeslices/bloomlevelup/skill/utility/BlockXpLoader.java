package com.freshorangeslices.bloomlevelup.skill.utility;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.StrictJsonParser;

import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;

/** Loads {@code data/<ns>/bloom/xp/blocks/*.json} into the {@link BlockXpTable}. */
public final class BlockXpLoader extends SimpleReloadListener<BlockXpTable> {
	public static final Identifier ID = BloomLevelUp.id("block_xp");

	private static final FileToIdConverter FILES = FileToIdConverter.json("bloom/xp/blocks");

	private record File(String category, Map<String, Double> values) {
		static final Codec<File> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.optionalFieldOf("category", SkillXp.UTILITY).forGetter(File::category),
				Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("values").forGetter(File::values)
		).apply(instance, File::new));
	}

	@Override
	protected BlockXpTable prepare(SharedState state) {
		List<BlockXpTable.Raw> raws = new ArrayList<>();
		// Sorted for a stable override order across packs and platforms.
		Map<Identifier, Resource> resources = new TreeMap<>(FILES.listMatchingResources(state.resourceManager()));

		for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
			Identifier id = FILES.fileToId(entry.getKey());

			try (Reader reader = entry.getValue().openAsReader()) {
				JsonElement json = StrictJsonParser.parse(reader);
				File.CODEC.parse(JsonOps.INSTANCE, json)
						.resultOrPartial(error -> BloomLevelUp.LOGGER.error("Couldn't parse Bloom block XP '{}': {}", id, error))
						.ifPresent(file -> raws.add(new BlockXpTable.Raw(id, file.category(), file.values())));
			} catch (IOException | JsonParseException | IllegalArgumentException e) {
				BloomLevelUp.LOGGER.error("Couldn't read Bloom block XP '{}'", id, e);
			}
		}

		return BlockXpTable.build(raws);
	}

	@Override
	protected void apply(BlockXpTable prepared, SharedState state) {
		BlockXpTable.set(prepared);
		BloomLevelUp.LOGGER.info("Loaded {} Bloom block XP entries", prepared.size());
	}
}
