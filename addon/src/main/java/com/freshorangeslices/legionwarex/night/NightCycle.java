package com.freshorangeslices.legionwarex.night;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * When the themed nights come and which is next, kept with the world (data/legionwarex_nights.dat, the Overworld's). The
 * nights come from a deck: every night with an installed mob, shuffled, each dealt once before the deck is shuffled again
 * (never the same night twice running across a shuffle). A themed night comes every {@code themedNightEveryDays}: the
 * morning of its day players are told which, it starts at dusk (time 12500) and ends at dawn (23500), or as soon as the
 * time jumps (sleeping, a time command). Myths & Legends' Blood Moon wins a night they'd share: the themed night waits for
 * the next one (and stops if a Blood Moon rises during it). Bosses some mobs wait for ({@code after}) are kept here once a
 * player kills one.
 */
public final class NightCycle extends SavedData {
	private static final String NAME = "legionwarex_nights";
	public static final long DUSK = 12500L;
	public static final long DAWN = 23500L;
	/** A night isn't started this late (it would be over before it was seen). */
	private static final long TOO_LATE = 22500L;
	private static final int DAY = AddonConfig.DAY;

	private final List<ResourceLocation> deck = new ArrayList<>();
	private final Set<ResourceLocation> beaten = new HashSet<>();
	private ResourceLocation active;
	private ResourceLocation last;
	private long activeDay = -1L;
	private long nextDay = -1L;
	private long warnedDay = -1L;
	/** How many themed nights this world has had; a night's mobs carry the number of theirs. */
	private int count;

	public static NightCycle of(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(NightCycle::load, NightCycle::new, NAME);
	}

	/** For the game tests: a cycle of its own, not the world's. */
	public static NightCycle fresh() {
		return new NightCycle();
	}

	/** Tonight's night, or null. */
	public NightTable.Theme active() {
		return NightTable.get(active);
	}

	public int count() {
		return count;
	}

	public long nextDay() {
		return nextDay;
	}

	public List<ResourceLocation> deck() {
		return List.copyOf(deck);
	}

	/** Whether a player has killed this boss (kept since the add-on saw it, or in an online player's stats). */
	public boolean beaten(ServerLevel level, ResourceLocation boss) {
		if (beaten.contains(boss)) {
			return true;
		}

		var type = ForgeRegistries.ENTITY_TYPES.getValue(boss);

		if (type == null || !ForgeRegistries.ENTITY_TYPES.containsKey(boss)) {
			return false;
		}

		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (player.getStats().getValue(Stats.ENTITY_KILLED.get(type)) > 0) {
				beaten.add(boss);
				setDirty();
				return true;
			}
		}

		return false;
	}

	public void beat(ResourceLocation boss) {
		if (beaten.add(boss)) {
			setDirty();
		}
	}

	/** The next night the deck would deal (shuffling a new deck if need be), without dealing it; null with no night installed. */
	public NightTable.Theme peek(RandomSource random) {
		deck.removeIf(id -> NightTable.get(id) == null || NightTable.get(id).installed().isEmpty());

		if (deck.isEmpty()) {
			List<ResourceLocation> all = new ArrayList<>(NightTable.all().values().stream().filter(theme -> !theme.installed().isEmpty())
					.map(NightTable.Theme::id).toList());

			for (int i = all.size() - 1; i > 0; i--) {
				int j = random.nextInt(i + 1);
				ResourceLocation swap = all.get(i);
				all.set(i, all.get(j));
				all.set(j, swap);
			}

			// Never the same night twice running across a shuffle.
			if (all.size() > 1 && all.get(0).equals(last)) {
				all.add(all.remove(0));
			}

			deck.addAll(all);
			setDirty();
		}

		return deck.isEmpty() ? null : NightTable.get(deck.get(0));
	}

	/** Deals the next night off the deck. */
	public NightTable.Theme deal(RandomSource random) {
		NightTable.Theme theme = peek(random);

		if (theme != null) {
			deck.remove(theme.id());
			last = theme.id();
			setDirty();
		}

		return theme;
	}

	/** Sets the next themed night this many days from today. */
	public void nextIn(ServerLevel level, int days) {
		nextDay = level.getDayTime() / DAY + days;
		warnedDay = -1L;
		setDirty();
	}

	/** Once a second on the Overworld: starts, ends or puts off a night by the time of day. */
	public void tick(ServerLevel level) {
		tick(level, level.getDayTime());
	}

	public void tick(ServerLevel level, long time) {
		long day = time / DAY;
		long hour = time % DAY;
		int every = AddonConfig.themedNightEveryDays();

		if (active != null) {
			long start = activeDay * DAY;

			if (!AddonConfig.themedNights()) {
				end(level, "themed nights were turned off");
			} else if (time >= start + DAWN || time < start + DUSK - 500L) {
				end(level, "dawn");
			} else if (BloodMoon.up(level)) {
				end(level, "the Blood Moon rose");
			} else if (active() == null) {
				end(level, "its file is gone");
			}

			return;
		}

		// A new world (or the time set far back): the first in a full cycle.
		if (nextDay < 0L || nextDay > day + every) {
			nextDay = day + every;
			setDirty();
		}

		if (!AddonConfig.themedNights() || day < nextDay) {
			return;
		}

		if (BloodMoon.tonight(level, day)) {
			nextDay = day + 1;
			setDirty();
			log("the Blood Moon has tonight (day " + day + "); the themed night waits for the next");
			return;
		}

		if (hour < DUSK - 500L) {
			if (warnedDay != day) {
				warnedDay = day;
				setDirty();
				NightTable.Theme next = peek(level.getRandom());

				if (next != null) {
					tell(level, Component.literal(next.omen() + " ").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
							.append(Component.literal("Tonight: ").withStyle(ChatFormatting.GRAY)).append(next.title()));
					log("the morning of " + next.id() + " (day " + day + ")");
				}
			}
		} else if (hour >= DUSK && hour < TOO_LATE) {
			NightTable.Theme theme = deal(level.getRandom());

			if (theme == null) {
				nextDay = day + every;
				setDirty();
				return;
			}

			start(level, theme, day);
		} else if (hour >= TOO_LATE) {
			nextDay = day + 1;
			setDirty();
		}
	}

	/** Starts this night now (tonight: the day's own night). */
	public void start(ServerLevel level, NightTable.Theme theme, long day) {
		active = theme.id();
		activeDay = day;
		count++;
		setDirty();

		for (ServerPlayer player : level.players()) {
			showTitle(player, theme);
		}

		log(theme.id() + " (" + theme.name() + ") begins, themed night #" + count + " (day " + day + "); " + theme.installed().size() + " of "
				+ theme.mobs().size() + " mobs installed, health x" + theme.health());
	}

	public void end(ServerLevel level, String why) {
		NightTable.Theme theme = active();
		log((theme == null ? String.valueOf(active) : theme.id() + " (" + theme.name() + ")") + " is over: " + why);
		active = null;
		nextDay = Math.max(activeDay, level.getDayTime() / DAY) + AddonConfig.themedNightEveryDays();
		activeDay = -1L;
		setDirty();
		NightMobs.sweep(level, this);
	}

	public static void showTitle(ServerPlayer player, NightTable.Theme theme) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 80, 30));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(theme.subtitle()).withStyle(ChatFormatting.GRAY)));
		player.connection.send(new ClientboundSetTitleTextPacket(theme.title()));
	}

	private static void tell(ServerLevel level, Component text) {
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(text);
		}
	}

	static void log(String text) {
		LegionWareX.LOGGER.info("LegionWareX night: {}", text);
	}

	private static NightCycle load(CompoundTag tag) {
		NightCycle cycle = new NightCycle();

		for (Tag id : tag.getList("deck", Tag.TAG_STRING)) {
			ResourceLocation night = ResourceLocation.tryParse(id.getAsString());

			if (night != null) {
				cycle.deck.add(night);
			}
		}

		for (Tag id : tag.getList("beaten", Tag.TAG_STRING)) {
			ResourceLocation boss = ResourceLocation.tryParse(id.getAsString());

			if (boss != null) {
				cycle.beaten.add(boss);
			}
		}

		cycle.active = tag.contains("active") ? ResourceLocation.tryParse(tag.getString("active")) : null;
		cycle.last = tag.contains("last") ? ResourceLocation.tryParse(tag.getString("last")) : null;
		cycle.activeDay = tag.getLong("activeDay");
		cycle.nextDay = tag.contains("nextDay") ? tag.getLong("nextDay") : -1L;
		cycle.warnedDay = tag.contains("warnedDay") ? tag.getLong("warnedDay") : -1L;
		cycle.count = tag.getInt("count");
		return cycle;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag list = new ListTag();
		deck.forEach(id -> list.add(StringTag.valueOf(id.toString())));
		tag.put("deck", list);
		ListTag bosses = new ListTag();
		beaten.forEach(id -> bosses.add(StringTag.valueOf(id.toString())));
		tag.put("beaten", bosses);

		if (active != null) {
			tag.putString("active", active.toString());
		}

		if (last != null) {
			tag.putString("last", last.toString());
		}

		tag.putLong("activeDay", activeDay);
		tag.putLong("nextDay", nextDay);
		tag.putLong("warnedDay", warnedDay);
		tag.putInt("count", count);
		return tag;
	}
}
