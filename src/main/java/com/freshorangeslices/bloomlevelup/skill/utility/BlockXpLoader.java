package com.freshorangeslices.bloomlevelup.skill.utility;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;

/** Loads {@code data/<ns>/bloom/xp/blocks/*.json} into the {@link BlockXpTable}. */
public final class BlockXpLoader extends SimplePreparableReloadListener<BlockXpTable> {
	public static final ResourceLocation ID = BloomLevelUp.id("block_xp");

	private static final FileToIdConverter FILES = FileToIdConverter.json("bloom/xp/blocks");

	private record File(String category, Map<String, Double> values) {
		static final Codec<File> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.optionalFieldOf("category", SkillXp.UTILITY).forGetter(File::category),
				Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("values").forGetter(File::values)
		).apply(instance, File::new));
	}

	@Override
	protected BlockXpTable prepare(ResourceManager manager, ProfilerFiller profiler) {
		List<BlockXpTable.Raw> raws = new ArrayList<>();
		// Sorted for a stable override order across packs and platforms.
		Map<ResourceLocation, Resource> resources = new TreeMap<>(FILES.listMatchingResources(manager));

		for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
			ResourceLocation id = FILES.fileToId(entry.getKey());

			try (Reader reader = entry.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
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
	protected void apply(BlockXpTable prepared, ResourceManager manager, ProfilerFiller profiler) {
		BlockXpTable.set(prepared);
		BloomLevelUp.LOGGER.info("Loaded {} Bloom block XP entries", prepared.size());
	}
}
