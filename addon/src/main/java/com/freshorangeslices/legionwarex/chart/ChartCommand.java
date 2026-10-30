package com.freshorangeslices.legionwarex.chart;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code /legionwarex chart find|lairs|beaten|forget} (operators): read a chart where the command runs, at once (a player
 * running it gets the treasure map in hand); list the lairs and whether each is in this dimension; list or forget the
 * beaten bosses. Each answer is also logged as "LegionWareX chart: ..." for the pack test.
 */
public final class ChartCommand {
	private ChartCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("chart")
				.then(Commands.literal("find").executes(ChartCommand::find))
				.then(Commands.literal("lairs").executes(ChartCommand::lairs))
				.then(Commands.literal("beaten").executes(ChartCommand::beaten))
				.then(Commands.literal("forget").executes(ChartCommand::forget));
	}

	private static void say(CommandSourceStack source, String text) {
		LegionWareX.LOGGER.info("LegionWareX chart: {}", text);
		source.sendSuccess(() -> Component.literal("LegionWareX chart: " + text), false);
	}

	private static int find(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		BlockPos from = BlockPos.containing(source.getPosition());
		LairSearch search = new LairSearch(level, from, AddonConfig.chartReach(), Lairs.all(), BeatenBosses.of(level.getServer()));
		int size = search.size();
		LairSearch.Found found = search.run();

		if (found == null) {
			say(source, "found no lair within " + AddonConfig.chartReach() + " blocks of " + from.toShortString() + " (" + search.searched()
					+ " of " + size + " lairs in this dimension, " + search.millis() + " ms)");
			return 0;
		}

		say(source, "found " + found.boss() + " in " + found.structure() + " at " + found.pos().getX() + ", " + found.pos().getZ() + ", "
				+ Math.round(found.distance()) + " blocks from " + from.toShortString() + (found.rematch() ? " (a rematch)" : "") + " ("
				+ search.searched() + " of " + size + " lairs in this dimension, " + search.millis() + " ms)");

		if (source.getEntity() instanceof ServerPlayer player) {
			ItemStack map = Charts.read(level, found);

			if (player.getMainHandItem().isEmpty()) {
				player.setItemInHand(InteractionHand.MAIN_HAND, map);
			} else if (!player.getInventory().add(map)) {
				player.drop(map, false);
			}
		}

		return (int) Math.min(Integer.MAX_VALUE, Math.round(found.distance()));
	}

	private static int lairs(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		List<String> here = new ArrayList<>();
		List<String> elsewhere = new ArrayList<>();
		List<String> missing = new ArrayList<>();

		for (Lairs.Lair lair : Lairs.all()) {
			Optional<Holder.Reference<Structure>> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
					.getHolder(ResourceKey.create(Registries.STRUCTURE, lair.structure()));
			List<ResourceLocation> absentBosses = lair.bosses().stream().filter(id -> !ForgeRegistries.ENTITY_TYPES.containsKey(id)).toList();

			if (holder.isEmpty() || absentBosses.size() == lair.bosses().size()) {
				missing.add(lair.structure() + (holder.isEmpty() ? "" : " (no " + absentBosses + ")"));
			} else if (level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder.get()).isEmpty()) {
				elsewhere.add(lair.structure().toString());
			} else {
				here.add(lair.structure() + (absentBosses.isEmpty() ? "" : " (no " + absentBosses + ")"));
			}
		}

		say(source, Lairs.all().size() + " lairs; in " + level.dimension().location() + " (" + here.size() + "): " + String.join(", ", here)
				+ "; other dimensions (" + elsewhere.size() + "): " + String.join(", ", elsewhere)
				+ "; not in the game (" + missing.size() + "): " + String.join(", ", missing));
		return here.size();
	}

	private static int beaten(CommandContext<CommandSourceStack> context) {
		Set<ResourceLocation> beaten = BeatenBosses.of(context.getSource().getServer()).all();
		say(context.getSource(), beaten.size() + " bosses beaten: " + beaten);
		return beaten.size();
	}

	private static int forget(CommandContext<CommandSourceStack> context) {
		BeatenBosses.of(context.getSource().getServer()).clear();
		say(context.getSource(), "beaten bosses forgotten");
		return 1;
	}
}
