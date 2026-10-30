package com.freshorangeslices.legionwarex.chart;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The lair bosses someone on this world has beaten (a player, or a player's pet, landed the killing blow), and the lairs
 * charts have led to (by the chunk the lair starts in, and how often to each boss), kept with the world (data/legionwarex_beaten_bosses.dat). Shared
 * by everyone: a chart read by one player skips a boss their friend has beaten, and a lair their friend's chart showed,
 * since they play the world together.
 */
public final class BeatenBosses extends SavedData {
	private static final String NAME = "legionwarex_beaten_bosses";
	private final Set<ResourceLocation> beaten = new LinkedHashSet<>();
	private final Set<Long> charted = new LinkedHashSet<>();
	private final Map<ResourceLocation, Integer> chartedBosses = new LinkedHashMap<>();

	public static BeatenBosses of(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(BeatenBosses::load, BeatenBosses::new, NAME);
	}

	private static BeatenBosses load(CompoundTag tag) {
		BeatenBosses data = new BeatenBosses();

		for (Tag id : tag.getList("beaten", Tag.TAG_STRING)) {
			ResourceLocation boss = ResourceLocation.tryParse(id.getAsString());

			if (boss != null) {
				data.beaten.add(boss);
			}
		}

		for (long chunk : tag.getLongArray("charted")) {
			data.charted.add(chunk);
		}

		CompoundTag bosses = tag.getCompound("chartedBosses");

		for (String id : bosses.getAllKeys()) {
			ResourceLocation boss = ResourceLocation.tryParse(id);

			if (boss != null) {
				data.chartedBosses.put(boss, bosses.getInt(id));
			}
		}

		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag list = new ListTag();
		beaten.forEach(id -> list.add(StringTag.valueOf(id.toString())));
		tag.put("beaten", list);
		tag.put("charted", new LongArrayTag(charted.stream().mapToLong(Long::longValue).toArray()));
		CompoundTag bosses = new CompoundTag();
		chartedBosses.forEach((boss, count) -> bosses.putInt(boss.toString(), count));
		tag.put("chartedBosses", bosses);
		return tag;
	}

	public boolean isBeaten(ResourceLocation boss) {
		return beaten.contains(boss);
	}

	/** Whether every boss of a lair is beaten. */
	public boolean isBeaten(Lairs.Lair lair) {
		return beaten.containsAll(lair.bosses());
	}

	/** True when it's newly beaten. */
	public boolean add(ResourceLocation boss) {
		boolean added = beaten.add(boss);

		if (added) {
			setDirty();
		}

		return added;
	}

	/** Whether a chart has already led to the lair starting in this chunk ({@link net.minecraft.world.level.ChunkPos#toLong}). */
	public boolean isCharted(long chunk) {
		return charted.contains(chunk);
	}

	/** How many charts have led to this boss (charts favour the boss they've led to least). */
	public int timesCharted(ResourceLocation boss) {
		return chartedBosses.getOrDefault(boss, 0);
	}

	/** A chart now leads to this lair: later charts lead to other lairs, and other bosses first. */
	public void chart(LairSearch.Found found) {
		charted.add(found.chunk());
		chartedBosses.merge(found.boss(), 1, Integer::sum);
		setDirty();
	}

	public int chartedCount() {
		return charted.size();
	}

	/** Each boss charts have led to, and how many times. */
	public Map<ResourceLocation, Integer> chartedBosses() {
		return Map.copyOf(chartedBosses);
	}

	/** For the game tests and /legionwarex chart forget: beaten bosses and charted lairs alike. */
	public void clear() {
		if (!beaten.isEmpty() || !charted.isEmpty() || !chartedBosses.isEmpty()) {
			beaten.clear();
			charted.clear();
			chartedBosses.clear();
			setDirty();
		}
	}

	/** Only the charted lairs (the game tests). */
	public void clearCharted() {
		if (!charted.isEmpty() || !chartedBosses.isEmpty()) {
			charted.clear();
			chartedBosses.clear();
			setDirty();
		}
	}

	public Set<ResourceLocation> all() {
		return Set.copyOf(beaten);
	}
}
