package com.freshorangeslices.legionwarex.chart;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Charts being read: one {@link LairSearch} per player, a lair structure a tick. When it's done the player gets the
 * treasure map (or the blank chart back, with nothing in reach). A player who leaves meanwhile gets the blank chart back
 * as they go ({@link #loggedOut}), to read again next time.
 */
public final class ChartReading {
	private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();

	private record Job(LairSearch search, ItemStack blank, ServerLevel level) {
	}

	private ChartReading() {
	}

	public static boolean isReading(ServerPlayer player) {
		return JOBS.containsKey(player.getUUID());
	}

	public static void start(ServerLevel level, ServerPlayer player, ItemStack blank) {
		LairSearch search = new LairSearch(level, player.blockPosition(), AddonConfig.chartReach(), Lairs.all(), BeatenBosses.of(level.getServer()));
		JOBS.put(player.getUUID(), new Job(search, blank, level));
	}

	/** Each server tick: every reading takes one more step; finished ones are delivered. */
	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Job>> jobs = JOBS.entrySet().iterator();

		while (jobs.hasNext()) {
			Map.Entry<UUID, Job> entry = jobs.next();
			Job job = entry.getValue();

			if (!job.search().step()) {
				continue;
			}

			jobs.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			LairSearch.Found found = job.search().found();
			LegionWareX.LOGGER.info("LegionWareX chart: read by {}: {} ({} lairs in {} ms)", player == null ? entry.getKey() : player.getScoreboardName(),
					found == null ? "nothing in reach" : found.boss() + " at " + found.pos().toShortString() + ", " + Math.round(found.distance()) + " blocks",
					job.search().searched(), job.search().millis());

			if (player == null) {
				continue;
			}

			if (found == null) {
				give(player, job.blank());
				player.sendSystemMessage(Component.translatable("message.legionwarex.chart_none", AddonConfig.chartReach()).withStyle(ChatFormatting.GRAY));
				continue;
			}

			give(player, Charts.read(job.level(), found));
			player.level().playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
			player.sendSystemMessage(Component.translatable(found.rematch() ? "message.legionwarex.chart_read_beaten" : "message.legionwarex.chart_read",
					LairSearch.bossName(found.boss())).withStyle(ChatFormatting.GOLD));
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/** A player leaving mid-reading keeps the blank chart. */
	public static void loggedOut(ServerPlayer player) {
		Job job = JOBS.remove(player.getUUID());

		if (job != null) {
			give(player, job.blank());
		}
	}

	/** On server stop: nothing carries over. */
	public static void clear() {
		JOBS.clear();
	}
}
