package com.freshorangeslices.legionwarex.chart;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The lair bosses someone on this world has beaten (a player, or a player's pet, landed the killing blow), kept with the
 * world (data/legionwarex_beaten_bosses.dat). Shared by everyone: a chart read by one player skips a boss their friend
 * has beaten, since they play the world together.
 */
public final class BeatenBosses extends SavedData {
	private static final String NAME = "legionwarex_beaten_bosses";
	private final Set<ResourceLocation> beaten = new LinkedHashSet<>();

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

		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag list = new ListTag();
		beaten.forEach(id -> list.add(StringTag.valueOf(id.toString())));
		tag.put("beaten", list);
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

	/** For the game tests and /legionwarex chart forget. */
	public void clear() {
		if (!beaten.isEmpty()) {
			beaten.clear();
			setDirty();
		}
	}

	public Set<ResourceLocation> all() {
		return Set.copyOf(beaten);
	}
}
