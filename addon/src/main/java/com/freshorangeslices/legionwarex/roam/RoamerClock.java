package com.freshorangeslices.legionwarex.roam;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * When each roaming boss last spawned out in the world (game time, which sleeping doesn't skip), kept with the world
 * (data/legionwarex_roamers.dat, the Overworld's), so the next of it waits {@code roamingBossWaitDays}.
 */
public final class RoamerClock extends SavedData {
	private static final String NAME = "legionwarex_roamers";
	private final Map<String, Long> last = new TreeMap<>();

	public static RoamerClock of(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(RoamerClock::load, RoamerClock::new, NAME);
	}

	private static RoamerClock load(CompoundTag tag) {
		RoamerClock clock = new RoamerClock();
		CompoundTag times = tag.getCompound("last");
		times.getAllKeys().forEach(key -> clock.last.put(key, times.getLong(key)));
		return clock;
	}

	/** When this boss last came, or -1 if never. */
	public long last(String id) {
		return last.getOrDefault(id, -1L);
	}

	public void record(String id, long gameTime) {
		last.put(id, gameTime);
		setDirty();
	}

	public void forget(String id) {
		if (last.remove(id) != null) {
			setDirty();
		}
	}

	public void forgetAll() {
		last.clear();
		setDirty();
	}

	public Map<String, Long> all() {
		return last;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		CompoundTag times = new CompoundTag();
		last.forEach(times::putLong);
		tag.put("last", times);
		return tag;
	}
}
