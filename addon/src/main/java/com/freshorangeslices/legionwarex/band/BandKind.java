package com.freshorangeslices.legionwarex.band;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The bands the add-on sends out: who leads them and who follows, by weight. Types from a mod that isn't installed are
 * left out (a war band is then vanilla's pillagers and vindicators; a pirate band needs Bosses'Rise).
 */
public enum BandKind {
	/** Illagers, mixed: vanilla's and Illager Invasion's, led by one with the ominous banner. */
	WAR("war",
			weights("minecraft:pillager", 4, "minecraft:vindicator", 3, "illagerinvasion:marauder", 3),
			weights("minecraft:pillager", 30, "minecraft:vindicator", 20, "illagerinvasion:marauder", 15,
					"illagerinvasion:provoker", 10, "illagerinvasion:alchemist", 10, "illagerinvasion:basher", 8)),
	/**
	 * Bosses'Rise's pirates (the Kraken's crew, out on their own): rooks and crossbow pirates, led by a rook. The Pirate
	 * Captain stays the Kraken ship's own (FreshOrangeSlices: too fine a figure for a band grunt).
	 */
	PIRATE("pirate",
			weights("block_factorys_bosses:pirate_rook", 1),
			weights("block_factorys_bosses:pirate_rook", 6, "block_factorys_bosses:crossbow_pirate", 4));

	public final String id;
	private final Map<ResourceLocation, Integer> leaders;
	private final Map<ResourceLocation, Integer> members;

	BandKind(String id, Map<ResourceLocation, Integer> leaders, Map<ResourceLocation, Integer> members) {
		this.id = id;
		this.leaders = leaders;
		this.members = members;
	}

	public static BandKind byId(String id) {
		for (BandKind kind : values()) {
			if (kind.id.equals(id)) {
				return kind;
			}
		}

		return null;
	}

	/** Whether this band can be made here: its leader's mod is installed. */
	public boolean available() {
		return !present(leaders).isEmpty();
	}

	/** A leader, by weight; null when none of the leaders' mods is installed. */
	public EntityType<?> pickLeader(RandomSource random) {
		return pick(present(leaders), random);
	}

	/** A follower, by weight; falls back to a leader type when no follower's mod is installed. */
	public EntityType<?> pickMember(RandomSource random) {
		List<Map.Entry<EntityType<?>, Integer>> present = present(members);
		return pick(present.isEmpty() ? present(leaders) : present, random);
	}

	private static List<Map.Entry<EntityType<?>, Integer>> present(Map<ResourceLocation, Integer> weights) {
		List<Map.Entry<EntityType<?>, Integer>> out = new ArrayList<>();

		for (Map.Entry<ResourceLocation, Integer> entry : weights.entrySet()) {
			// Entity types are a defaulted registry: an id that isn't there would come back as a pig.
			if (ForgeRegistries.ENTITY_TYPES.containsKey(entry.getKey())) {
				out.add(Map.entry(ForgeRegistries.ENTITY_TYPES.getValue(entry.getKey()), entry.getValue()));
			}
		}

		return out;
	}

	private static EntityType<?> pick(List<Map.Entry<EntityType<?>, Integer>> weighted, RandomSource random) {
		int total = weighted.stream().mapToInt(Map.Entry::getValue).sum();

		if (total <= 0) {
			return null;
		}

		int roll = random.nextInt(total);

		for (Map.Entry<EntityType<?>, Integer> entry : weighted) {
			roll -= entry.getValue();

			if (roll < 0) {
				return entry.getKey();
			}
		}

		return weighted.get(weighted.size() - 1).getKey();
	}

	private static Map<ResourceLocation, Integer> weights(Object... idsAndWeights) {
		Map<ResourceLocation, Integer> out = new LinkedHashMap<>();

		for (int i = 0; i < idsAndWeights.length; i += 2) {
			out.put(new ResourceLocation((String) idsAndWeights[i]), (Integer) idsAndWeights[i + 1]);
		}

		return out;
	}
}
