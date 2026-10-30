package com.freshorangeslices.legionwarex.chart;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Where a pirate chart leads: the nearest lair (of {@link Lairs}) within reach whose bosses aren't all beaten, or, when
 * every lair in reach is beaten, the nearest one for a rematch. It looks one lair structure at a time ({@link #step()}),
 * so a chart read in the game spreads the work over a tick per structure instead of freezing the server, and it never
 * looks further than its reach: each structure's search stops at the ring of its placement grid that covers it.
 */
public final class LairSearch {
	private final ServerLevel level;
	private final BlockPos from;
	private final int reach;
	private final Deque<Candidate> unbeaten = new ArrayDeque<>();
	private final Deque<Candidate> beaten = new ArrayDeque<>();
	private boolean rematch;
	private Found best;
	private int searched;
	private long nanos;

	/** A lair found: the structure's start, where the chart's X goes, and the boss it's for (the first not yet beaten). */
	public record Found(BlockPos pos, ResourceLocation structure, ResourceLocation boss, double distance, boolean rematch) {
	}

	private record Candidate(Holder<Structure> structure, Lairs.Lair lair) {
	}

	public LairSearch(ServerLevel level, BlockPos from, int reach, List<Lairs.Lair> lairs, BeatenBosses beatenBosses) {
		this.level = level;
		this.from = from.immutable();
		this.reach = reach;
		Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

		for (Lairs.Lair lair : lairs) {
			Optional<Holder.Reference<Structure>> holder = structures.getHolder(ResourceKey.create(Registries.STRUCTURE, lair.structure()));

			// Only lairs that are in the world and whose bosses are too (an optional mod that isn't installed costs nothing).
			if (holder.isEmpty() || lair.bosses().stream().noneMatch(ForgeRegistries.ENTITY_TYPES::containsKey)
					|| level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder.get()).isEmpty()) {
				continue;
			}

			(beatenBosses.isBeaten(lair) ? beaten : unbeaten).add(new Candidate(holder.get(), lair));
		}
	}

	/** How many lair structures this search will look through (those that can be in this dimension). */
	public int size() {
		return unbeaten.size() + beaten.size() + searched;
	}

	/** Looks for one lair structure; true once the search is done. */
	public boolean step() {
		if (unbeaten.isEmpty() && best == null && !rematch && !beaten.isEmpty()) {
			rematch = true;
			unbeaten.addAll(beaten);
			beaten.clear();
		}

		Candidate next = unbeaten.poll();

		if (next != null) {
			long start = System.nanoTime();
			look(next);
			nanos += System.nanoTime() - start;
			searched++;
		}

		return unbeaten.isEmpty() && (best != null || rematch || beaten.isEmpty());
	}

	/** All at once (the command and the game tests). */
	public Found run() {
		while (!step()) {
			// One structure a step.
		}

		return best;
	}

	public Found found() {
		return best;
	}

	public int searched() {
		return searched;
	}

	public long millis() {
		return nanos / 1_000_000L;
	}

	private void look(Candidate candidate) {
		int radius = 1;

		for (StructurePlacement placement : level.getChunkSource().getGeneratorState().getPlacementsForStructure(candidate.structure())) {
			// Rings of the placement's grid: one more ring is one more 'spacing' of chunks.
			int spacing = placement instanceof RandomSpreadStructurePlacement spread ? spread.spacing() : 32;
			radius = Math.max(radius, (int) Math.ceil(reach / (16.0 * Math.max(1, spacing))));
		}

		Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(candidate.structure()), from, radius, false);

		if (hit == null) {
			return;
		}

		BlockPos pos = hit.getFirst();
		double distance = Math.sqrt(Math.pow(pos.getX() - from.getX(), 2) + Math.pow(pos.getZ() - from.getZ(), 2));

		if (distance > reach || (best != null && distance >= best.distance())) {
			return;
		}

		BeatenBosses beatenBosses = BeatenBosses.of(level.getServer());
		ResourceLocation boss = candidate.lair().bosses().stream()
				.filter(id -> ForgeRegistries.ENTITY_TYPES.containsKey(id) && (rematch || !beatenBosses.isBeaten(id)))
				.findFirst().orElse(candidate.lair().bosses().get(0));
		best = new Found(pos, candidate.lair().structure(), boss, distance, rematch);
	}

	/** The boss's name, as the game shows it. */
	public static Component bossName(ResourceLocation boss) {
		EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(boss);
		return type != null && ForgeRegistries.ENTITY_TYPES.containsKey(boss) ? type.getDescription() : Component.literal(boss.toString());
	}
}
