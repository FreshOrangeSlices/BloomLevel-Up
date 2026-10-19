package com.freshorangeslices.bloomlevelup.skill.utility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * Block-break XP values from {@code data/<ns>/bloom/xp/blocks/*.json}. Keys are block ids or {@code #tags};
 * an exact id beats any tag, otherwise the highest matching tag wins (so a broad {@code #c:ores} fallback
 * never lowers a specific ore). Resolved per block and cached until the next data/tag reload.
 */
public final class BlockXpTable {
	public record Entry(String category, double xp) {
	}

	private static volatile BlockXpTable current = new BlockXpTable(Map.of(), List.of());

	private final Map<Identifier, Entry> exact;
	private final List<Map.Entry<TagKey<Block>, Entry>> tagged;
	private final Map<Block, Optional<Entry>> cache = new IdentityHashMap<>();

	BlockXpTable(Map<Identifier, Entry> exact, List<Map.Entry<TagKey<Block>, Entry>> tagged) {
		this.exact = exact;
		this.tagged = tagged;
	}

	public static BlockXpTable get() {
		return current;
	}

	static void set(BlockXpTable table) {
		current = table;
	}

	/** Tags are rebound after a reload, so cached per-block answers must be recomputed. */
	public static void invalidate() {
		synchronized (current.cache) {
			current.cache.clear();
		}
	}

	public Optional<Entry> lookup(BlockState state) {
		Block block = state.getBlock();

		synchronized (cache) {
			Optional<Entry> cached = cache.get(block);

			if (cached != null) {
				return cached;
			}

			Optional<Entry> resolved = resolve(state);
			cache.put(block, resolved);
			return resolved;
		}
	}

	private Optional<Entry> resolve(BlockState state) {
		Entry direct = exact.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));

		if (direct != null) {
			return Optional.of(direct);
		}

		Entry best = null;

		for (Map.Entry<TagKey<Block>, Entry> entry : tagged) {
			if (state.is(entry.getKey()) && (best == null || entry.getValue().xp() > best.xp())) {
				best = entry.getValue();
			}
		}

		return Optional.ofNullable(best);
	}

	public int size() {
		return exact.size() + tagged.size();
	}

	/** Merges raw files in load order; later files override earlier ones for the same key. */
	static BlockXpTable build(List<Raw> files) {
		Map<Identifier, Entry> exact = new HashMap<>();
		Map<Identifier, Entry> tags = new HashMap<>();

		for (Raw file : files) {
			for (Map.Entry<String, Double> value : file.values().entrySet()) {
				String key = value.getKey();
				boolean tag = key.startsWith("#");
				Identifier id = Identifier.tryParse(tag ? key.substring(1) : key);

				if (id == null || !(value.getValue() >= 0.0)) {
					BloomLevelUp.LOGGER.warn("[Bloom XP] Ignoring bad block XP entry '{}' in {}", key, file.source());
					continue;
				}

				Entry entry = new Entry(file.category(), value.getValue());
				(tag ? tags : exact).put(id, entry);
			}
		}

		List<Map.Entry<TagKey<Block>, Entry>> tagged = new ArrayList<>();
		tags.forEach((id, entry) -> tagged.add(Map.entry(TagKey.create(Registries.BLOCK, id), entry)));
		return new BlockXpTable(Map.copyOf(exact), List.copyOf(tagged));
	}

	record Raw(Identifier source, String category, Map<String, Double> values) {
	}
}
