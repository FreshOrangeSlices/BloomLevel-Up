package com.freshorangeslices.legionwarex.night;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The themed nights: data files at {@code data/<namespace>/legionwarex/nights/<name>.json}, one night each (its id is the
 * file's: {@code legionwarex:bone}), {@code {"name": "Bone Night", "omen": "<the morning's line>", "subtitle": "<under the
 * title at dusk>", "color": "<chat colour>", "health": 1.5, "climate": "any|cold|hot", "mobs": [{"entity": "<id>",
 * "weight": 10, "climate": "cold", "max": 1, "after": "<boss id>"}], "share": 1.0, "patrols": false, "copies": 2}}. {@code share}
 * is this night's in place of {@code themedNightShare} (1: every other monster that would spawn naturally comes as the
 * night's), {@code patrols: false} holds vanilla's illager patrols and the add-on's pirate bands off that night, and
 * {@code copies} puts it in the deck that many times (never twice running). A mob's climate is the night's unless it names one;
 * {@code max} is how many of it may be loaded within 128 blocks of a spot (none: no limit), for the rare and the lone;
 * {@code after} keeps it out until a player has killed that boss (Skarrier's mobs that only come after the Wither). Mobs
 * whose mod isn't installed are left out when one is picked, so a night ships entries for mods the pack may not have.
 */
public final class NightTable extends SimpleJsonResourceReloadListener {
	private static final Gson GSON = new GsonBuilder().create();
	private static volatile Map<ResourceLocation, Theme> themes = Map.of();

	public NightTable() {
		super(GSON, LegionWareX.MOD_ID + "/nights");
	}

	/** Where a spot is: snowy (cold enough to snow there), hot (deserts, savannas, badlands) or neither. */
	public enum Climate {
		ANY, COLD, HOT;

		static Climate parse(String text) {
			return text == null ? null : valueOf(text.toUpperCase(Locale.ROOT));
		}

		public static Climate at(ServerLevel level, BlockPos pos) {
			Holder<Biome> biome = level.getBiome(pos);

			if (biome.value().coldEnoughToSnow(pos)) {
				return COLD;
			}

			return biome.value().getBaseTemperature() > 1.0F ? HOT : ANY;
		}

		/** How strong something of this climate is in a spot of that one: full in its own, less in temperate land, far less in the opposite. */
		public double share(Climate here) {
			if (this == ANY || this == here) {
				return 1.0;
			}

			double off = AddonConfig.themedNightOffClimateShare();
			return here == ANY ? off : off * off;
		}
	}

	/** One of a night's mobs: how often by weight, its climate, at most how many near a spot (0: any), and the boss it waits for (or null). */
	public record Mob(ResourceLocation entity, int weight, Climate climate, int max, ResourceLocation after) {
		public boolean present() {
			return ForgeRegistries.ENTITY_TYPES.containsKey(entity);
		}

		public EntityType<?> type() {
			return ForgeRegistries.ENTITY_TYPES.getValue(entity);
		}
	}

	/**
	 * A night: its id, what players see, the health its mobs get (times their own), its climate, its share of other
	 * monsters (below 0: the config's), whether patrols and pirate bands may come, its copies in the deck, and its mobs.
	 */
	public record Theme(ResourceLocation id, String name, String omen, String subtitle, ChatFormatting color, double health, Climate climate,
			double share, boolean patrols, int copies, List<Mob> mobs) {
		/** A night with the config's share, patrols allowed, once in the deck. */
		public Theme(ResourceLocation id, String name, String omen, String subtitle, ChatFormatting color, double health, Climate climate, List<Mob> mobs) {
			this(id, name, omen, subtitle, color, health, climate, -1.0, true, 1, mobs);
		}

		/** The mob entry for this kind, or null. */
		public Mob entry(EntityType<?> type) {
			ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
			return key == null ? null : mobs.stream().filter(mob -> mob.entity().equals(key)).findFirst().orElse(null);
		}

		public List<Mob> installed() {
			return mobs.stream().filter(Mob::present).toList();
		}

		/** Whether this kind of mob is one of the night's (installed or not, waiting for its boss or not). */
		public boolean has(EntityType<?> type) {
			ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
			return id != null && mobs.stream().anyMatch(mob -> mob.entity().equals(id));
		}

		/**
		 * How strong the night is in a spot of this climate: full in its own (a night of any climate everywhere), less
		 * outside it. Its cold or hot mobs in a night of any climate are rarer outside theirs through {@link #pick} instead.
		 */
		public double strength(Climate here) {
			return installed().isEmpty() ? 0.0 : climate.share(here);
		}

		/** One of its installed mobs for a spot of this climate, by weight times its share there, among those 'allowed' lets in; null when none. */
		public Mob pick(Climate here, RandomSource random, Predicate<Mob> allowed) {
			List<Mob> from = new ArrayList<>();
			double total = 0.0;

			for (Mob mob : installed()) {
				if (mob.weight() > 0 && allowed.test(mob)) {
					from.add(mob);
					total += mob.weight() * mob.climate().share(here);
				}
			}

			if (total <= 0.0) {
				return null;
			}

			double roll = random.nextDouble() * total;

			for (Mob mob : from) {
				roll -= mob.weight() * mob.climate().share(here);

				if (roll < 0.0) {
					return mob;
				}
			}

			return from.get(from.size() - 1);
		}

		public MutableComponent title() {
			return Component.literal(name).withStyle(color);
		}
	}

	/** Every night, by id (in id order when loaded from data). */
	public static Map<ResourceLocation, Theme> all() {
		return themes;
	}

	public static Theme get(ResourceLocation id) {
		return id == null ? null : themes.get(id);
	}

	/** For the game tests: the nights as if loaded from data. */
	public static void set(List<Theme> list) {
		Map<ResourceLocation, Theme> map = new LinkedHashMap<>();
		list.forEach(theme -> map.put(theme.id(), theme));
		themes = Collections.unmodifiableMap(map);
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
		List<Theme> out = new ArrayList<>();

		for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
			try {
				JsonObject json = file.getValue().getAsJsonObject();
				Climate climate = json.has("climate") ? Climate.parse(json.get("climate").getAsString()) : Climate.ANY;
				List<Mob> mobs = new ArrayList<>();

				for (JsonElement element : json.getAsJsonArray("mobs")) {
					JsonObject mob = element.getAsJsonObject();
					mobs.add(new Mob(new ResourceLocation(mob.get("entity").getAsString()), mob.has("weight") ? mob.get("weight").getAsInt() : 10,
							mob.has("climate") ? Climate.parse(mob.get("climate").getAsString()) : climate, mob.has("max") ? Math.max(0, mob.get("max").getAsInt()) : 0,
							mob.has("after") ? new ResourceLocation(mob.get("after").getAsString()) : null));
				}

				ChatFormatting color = json.has("color") ? ChatFormatting.getByName(json.get("color").getAsString()) : null;
				String name = json.get("name").getAsString();
				out.add(new Theme(file.getKey(), name, json.has("omen") ? json.get("omen").getAsString() : "Something feels wrong this morning.",
						json.has("subtitle") ? json.get("subtitle").getAsString() : "", color == null || !color.isColor() ? ChatFormatting.RED : color,
						json.has("health") ? Math.max(1.0, json.get("health").getAsDouble()) : 1.0, climate,
						json.has("share") ? Math.max(0.0, Math.min(1.0, json.get("share").getAsDouble())) : -1.0,
						!json.has("patrols") || json.get("patrols").getAsBoolean(), json.has("copies") ? Math.max(1, Math.min(4, json.get("copies").getAsInt())) : 1,
						List.copyOf(mobs)));
			} catch (RuntimeException e) {
				LegionWareX.LOGGER.warn("LegionWareX night: night file {} couldn't be read: {}", file.getKey(), e.toString());
			}
		}

		set(out);
		LegionWareX.LOGGER.info("LegionWareX night: {} themed nights ({})", out.size(),
				String.join(", ", out.stream().map(theme -> theme.id().getPath() + " " + theme.installed().size() + "/" + theme.mobs().size()).toList()));
	}
}
