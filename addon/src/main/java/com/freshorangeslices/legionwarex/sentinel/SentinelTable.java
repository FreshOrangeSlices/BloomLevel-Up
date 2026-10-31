package com.freshorangeslices.legionwarex.sentinel;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Who stands sentinel where: data files at {@code data/<namespace>/legionwarex/sentinels/<name>.json}, each
 * {@code {"sentinels": [{"entity": "<id>", "weight": 10, "biomes": ["#<biome tag>", "<biome id>", ...], "roams": false,
 * "nbt": "{...}", "xp": 0}]}}. No biomes means any Overworld land. A roamer (a pillager scout) wanders instead of holding
 * its spot. {@code nbt} (SNBT) is merged into the mob as it's placed, for a mod's own variant of it (Migamigos' hostile
 * Enemigos: {@code {IsEnemigo:1b}}); {@code xp} is experience on top of its own when a player kills it. Entries whose mob
 * isn't installed are skipped when one is picked, so the add-on ships entries for mods the pack may not have.
 */
public final class SentinelTable extends SimpleJsonResourceReloadListener {
	private static final Gson GSON = new GsonBuilder().create();
	private static volatile List<Entry> entries = List.of();

	public SentinelTable() {
		super(GSON, LegionWareX.MOD_ID + "/sentinels");
	}

	/**
	 * One kind of sentinel, the biomes it stands in (ids or #tags; none: anywhere) and how often, by weight; nbt (or null)
	 * merged into it as it's placed, and xp on top of its own when a player kills it.
	 */
	public record Entry(ResourceLocation entity, int weight, List<String> biomes, boolean roams, CompoundTag nbt, int xp) {
		public Entry(ResourceLocation entity, int weight, List<String> biomes, boolean roams) {
			this(entity, weight, biomes, roams, null, 0);
		}

		public boolean present() {
			return ForgeRegistries.ENTITY_TYPES.containsKey(entity);
		}

		public EntityType<?> type() {
			return ForgeRegistries.ENTITY_TYPES.getValue(entity);
		}

		/** Whether it stands in this biome (no list: any). */
		public boolean fits(Holder<Biome> biome) {
			if (biomes.isEmpty()) {
				return true;
			}

			for (String spec : biomes) {
				if (spec.startsWith("#") ? biome.is(TagKey.create(Registries.BIOME, new ResourceLocation(spec.substring(1))))
						: biome.is(ResourceKey.create(Registries.BIOME, new ResourceLocation(spec)))) {
					return true;
				}
			}

			return false;
		}
	}

	public static List<Entry> all() {
		return entries;
	}

	/** For the game tests: the table as if loaded from data. */
	public static void set(List<Entry> list) {
		entries = List.copyOf(list);
	}

	/**
	 * A sentinel for this biome, by weight among the installed ones that fit it; with none fitting, one of those that
	 * stand anywhere. Null when there's none (no table, or no installed mob in it).
	 */
	public static Entry pick(Holder<Biome> biome, RandomSource random, boolean roamers) {
		List<Entry> fitting = new ArrayList<>();
		List<Entry> anywhere = new ArrayList<>();

		for (Entry entry : entries) {
			if (entry.roams() != roamers || !entry.present() || entry.weight() <= 0) {
				continue;
			}

			if (entry.biomes().isEmpty()) {
				anywhere.add(entry);
			}

			if (entry.fits(biome)) {
				fitting.add(entry);
			}
		}

		List<Entry> from = fitting.isEmpty() ? anywhere : fitting;
		int total = from.stream().mapToInt(Entry::weight).sum();

		if (total <= 0) {
			return null;
		}

		int roll = random.nextInt(total);

		for (Entry entry : from) {
			roll -= entry.weight();

			if (roll < 0) {
				return entry;
			}
		}

		return from.get(from.size() - 1);
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
		List<Entry> out = new ArrayList<>();

		for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
			try {
				for (JsonElement element : file.getValue().getAsJsonObject().getAsJsonArray("sentinels")) {
					JsonObject entry = element.getAsJsonObject();
					List<String> biomes = new ArrayList<>();

					if (entry.has("biomes")) {
						entry.getAsJsonArray("biomes").forEach(biome -> biomes.add(biome.getAsString()));
					}

					CompoundTag nbt = null;

					if (entry.has("nbt")) {
						try {
							nbt = TagParser.parseTag(entry.get("nbt").getAsString());
						} catch (CommandSyntaxException e) {
							LegionWareX.LOGGER.warn("LegionWareX: sentinel {} in {} has nbt that couldn't be read, left out: {}", entry.get("entity"),
									file.getKey(), e.getMessage());
							continue;
						}
					}

					out.add(new Entry(new ResourceLocation(entry.get("entity").getAsString()), entry.has("weight") ? entry.get("weight").getAsInt() : 10,
							List.copyOf(biomes), entry.has("roams") && entry.get("roams").getAsBoolean(), nbt,
							entry.has("xp") ? Math.max(0, entry.get("xp").getAsInt()) : 0));
				}
			} catch (RuntimeException e) {
				LegionWareX.LOGGER.warn("LegionWareX: sentinel file {} couldn't be read: {}", file.getKey(), e.toString());
			}
		}

		set(out);
		LegionWareX.LOGGER.info("LegionWareX: {} sentinel kinds ({} installed)", out.size(), out.stream().filter(Entry::present).count());
	}
}
