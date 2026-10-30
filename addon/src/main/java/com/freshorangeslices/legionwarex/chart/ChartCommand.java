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
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code /legionwarex chart find|lairs|beaten|forget|probe <structure>} (operators): read a chart where the command runs, at once (a player
 * running it gets the treasure map in hand, and the lair counts as charted); list the lairs and whether each is in this
 * dimension; list or forget the beaten bosses and charted lairs. Each answer is also logged as "LegionWareX chart: ..." for the pack test.
 */
public final class ChartCommand {
	private ChartCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("chart")
				.then(Commands.literal("find").executes(ChartCommand::find))
				.then(Commands.literal("lairs").executes(ChartCommand::lairs))
				.then(Commands.literal("beaten").executes(ChartCommand::beaten))
				.then(Commands.literal("forget").executes(ChartCommand::forget))
				.then(Commands.literal("probe").then(Commands.argument("structure", ResourceLocationArgument.id())
						.executes(context -> probe(context, ResourceLocationArgument.getId(context, "structure")))));
	}

	/**
	 * One structure as the chart search sees it here: its placements, and the game's own map-structure search (as
	 * /locate, 100 rings) from where the command runs, timed. For finding out why a lair isn't found.
	 */
	private static int probe(CommandContext<CommandSourceStack> context, ResourceLocation id) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		BlockPos from = BlockPos.containing(source.getPosition());
		Optional<Holder.Reference<Structure>> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
				.getHolder(ResourceKey.create(Registries.STRUCTURE, id));

		if (holder.isEmpty()) {
			say(source, "probe " + id + ": no such structure");
			return 0;
		}

		List<String> placements = new ArrayList<>();

		for (StructurePlacement placement : level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder.get())) {
			placements.add(placement.getClass().getSimpleName() + (placement instanceof RandomSpreadStructurePlacement spread
					? " spacing " + spread.spacing() + " separation " + spread.separation() : ""));
		}

		long start = System.nanoTime();
		var hit = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder.get()), from, 100, false);
		long ms = (System.nanoTime() - start) / 1_000_000L;
		say(source, "probe " + id + " in " + level.dimension().location() + ": placements " + placements + "; nearest "
				+ (hit == null ? "none within 100 rings" : hit.getFirst().toShortString() + ", "
				+ Math.round(Math.sqrt(hit.getFirst().distSqr(new BlockPos(from.getX(), hit.getFirst().getY(), from.getZ())))) + " blocks")
				+ " (" + ms + " ms)");
		return hit == null ? 0 : 1;
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

		// As a chart read here: the next one leads elsewhere.
		BeatenBosses.of(level.getServer()).chart(found);
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
		BeatenBosses log = BeatenBosses.of(context.getSource().getServer());
		Set<ResourceLocation> beaten = log.all();
		say(context.getSource(), beaten.size() + " bosses beaten: " + beaten + "; " + log.chartedCount() + " lairs charted, by boss: " + log.chartedBosses());
		return beaten.size();
	}

	private static int forget(CommandContext<CommandSourceStack> context) {
		BeatenBosses.of(context.getSource().getServer()).clear();
		say(context.getSource(), "beaten bosses and charted lairs forgotten");
		return 1;
	}
}
