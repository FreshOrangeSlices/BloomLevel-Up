package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Illagers grow rarer where they keep turning up (FreshOrangeSlices, 2026-11-16). Each arrival (a vanilla patrol, a war
 * band, a pillager scout) warms the chunk it came to; the next one goes ahead with a chance of 1 / (1 + strength x
 * warmth), warmth being what's in the chunks within the config's radius, cooled by half every few days. Illagers that
 * spawn in the wild like other monsters are thinned by the same warmth but don't add to it (too many come and go out of
 * sight). Raids (event spawns), structures' own illagers (an outpost's, a fort's), spawners, spawn eggs and commands
 * are left alone. Kept with each level (data/legionwarex_illager_warmth.dat).
 */
public final class IllagerCrowding extends SavedData {
	private static final String NAME = "legionwarex_illager_warmth";
	/** Illagers that don't extend Minecraft's own (The Graveyard's corrupted ones), as optional tag entries. */
	public static final TagKey<EntityType<?>> ILLAGERS = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(LegionWareX.MOD_ID, "crowded_illagers"));
	/** A patrol's or a pack's members come in the same tick within this many blocks of the first. */
	private static final double GROUP_DISTANCE = 32.0;
	/** Warmth below this is forgotten. */
	private static final double FORGET = 0.02;

	private final Map<Long, Warmth> chunks = new HashMap<>();
	// The last group's decision, so a patrol comes whole or not at all (not saved).
	private long groupTick = Long.MIN_VALUE;
	private MobSpawnType groupType;
	private BlockPos groupAt = BlockPos.ZERO;
	private boolean groupAllowed;
	/** The latest game time asked about, for forgetting cooled chunks when saving. */
	private long lastSeen;

	private static final class Warmth {
		double value;
		long tick;

		double at(long now) {
			return value * Math.pow(0.5, (double) Math.max(0L, now - tick) / halfLife());
		}
	}

	public static IllagerCrowding of(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(IllagerCrowding::load, IllagerCrowding::new, NAME);
	}

	/** How long warmth takes to halve, in ticks. */
	public static long halfLife() {
		return Math.max(1L, Math.round(AddonConfig.illagerCoolingDays() * AddonConfig.DAY));
	}

	/** The chance an arrival goes ahead where it's this warm. */
	public static double chance(double warmth) {
		return 1.0 / (1.0 + AddonConfig.illagerCrowdingStrength() * Math.max(0.0, warmth));
	}

	/** The warmth at a spot: what's in the chunks within the radius, cooled to {@code now}. */
	public double warmthAt(BlockPos pos, long now) {
		int radius = AddonConfig.illagerCrowdingRadius();
		int chunkX = SectionPos.blockToSectionCoord(pos.getX());
		int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
		double sum = 0.0;
		lastSeen = Math.max(lastSeen, now);

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz <= radius * radius) {
					Warmth warmth = chunks.get(ChunkPos.asLong(chunkX + dx, chunkZ + dz));

					if (warmth != null) {
						sum += warmth.at(now);
					}
				}
			}
		}

		return sum;
	}

	/** Adds to the warmth of the chunk a spot is in (a negative amount takes away). */
	public void warm(BlockPos pos, long now, double amount) {
		long key = ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
		Warmth warmth = chunks.computeIfAbsent(key, k -> new Warmth());
		warmth.value = Math.max(0.0, warmth.at(now) + amount);
		warmth.tick = now;
		lastSeen = Math.max(lastSeen, now);

		if (warmth.value < FORGET) {
			chunks.remove(key);
		}

		setDirty();
	}

	/** Forgets the warmth within the radius of a spot; how many chunks had some. */
	public int cool(BlockPos pos) {
		int radius = AddonConfig.illagerCrowdingRadius();
		int chunkX = SectionPos.blockToSectionCoord(pos.getX());
		int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
		int cooled = 0;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz <= radius * radius && chunks.remove(ChunkPos.asLong(chunkX + dx, chunkZ + dz)) != null) {
					cooled++;
				}
			}
		}

		setDirty();
		return cooled;
	}

	/**
	 * Whether an arrival at a spot goes ahead (the add-on's war bands and scouts ask before they come). Doesn't warm:
	 * {@link #arrived} does, once they've come.
	 */
	public static boolean roll(ServerLevel level, BlockPos pos, RandomSource random, String what) {
		if (!AddonConfig.illagerCrowding()) {
			return true;
		}

		double warmth = of(level).warmthAt(pos, level.getGameTime());
		double chance = chance(warmth);

		if (chance >= 1.0 || random.nextDouble() < chance) {
			return true;
		}

		log(what + " kept away from " + pos.toShortString() + " (warmth " + round(warmth) + ", chance " + Math.round(chance * 100) + "%)");
		return false;
	}

	/** An arrival came to a spot: its chunk warms by one. */
	public static void arrived(ServerLevel level, BlockPos pos, String what) {
		if (AddonConfig.illagerCrowding()) {
			IllagerCrowding data = of(level);
			data.warm(pos, level.getGameTime(), 1.0);
			log(what + " came to " + pos.toShortString() + " (warmth there now " + round(data.warmthAt(pos, level.getGameTime())) + ")");
		}
	}

	/** An illager that counts: Minecraft's own kind or the tag's, and not in a raid. */
	public static boolean counts(Mob mob) {
		return (mob instanceof AbstractIllager || mob.getType().is(ILLAGERS)) && !(mob instanceof Raider raider && raider.hasActiveRaid());
	}

	/** Vanilla's patrols (each member) and natural spawns: thinned where it's warm; a patrol that comes warms its spot. */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
		MobSpawnType type = event.getSpawnType();

		if (!AddonConfig.illagerCrowding() || event.isSpawnCancelled() || (type != MobSpawnType.PATROL && type != MobSpawnType.NATURAL)
				|| (type == MobSpawnType.NATURAL && !AddonConfig.illagerCrowdingNatural()) || !counts(event.getEntity())) {
			return;
		}

		ServerLevel level = event.getLevel().getLevel();
		BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());

		if (type == MobSpawnType.NATURAL && inSpawnStructure(level, pos)) {
			return;
		}

		if (!decide(level, pos, type)) {
			event.setSpawnCancelled(true);
		}
	}

	/** One member's say: the group's decision if it's part of the last group, else a new roll (and a patrol that comes warms). */
	static boolean decide(ServerLevel level, BlockPos pos, MobSpawnType type) {
		IllagerCrowding data = of(level);
		long now = level.getGameTime();

		if (data.groupTick == now && data.groupType == type && data.groupAt.closerThan(pos, GROUP_DISTANCE)) {
			return data.groupAllowed;
		}

		double warmth = data.warmthAt(pos, now);
		double chance = chance(warmth);
		boolean allowed = chance >= 1.0 || level.getRandom().nextDouble() < chance;
		data.groupTick = now;
		data.groupType = type;
		data.groupAt = pos.immutable();
		data.groupAllowed = allowed;

		if (type == MobSpawnType.PATROL) {
			if (allowed) {
				data.warm(pos, now, 1.0);
				log("a patrol came to " + pos.toShortString() + " (warmth there now " + round(data.warmthAt(pos, now)) + ")");
			} else {
				log("a patrol kept away from " + pos.toShortString() + " (warmth " + round(warmth) + ", chance " + Math.round(chance * 100) + "%)");
			}
		}

		return allowed;
	}

	/** Inside a structure that spawns its own monsters (an outpost's pillagers, a fort's crew), where its own rules hold. */
	static boolean inSpawnStructure(ServerLevel level, BlockPos pos) {
		StructureManager structures = level.structureManager();

		for (Structure structure : structures.getAllStructuresAt(pos).keySet()) {
			if (structure.spawnOverrides().containsKey(MobCategory.MONSTER) && structures.getStructureAt(pos, structure).isValid()) {
				return true;
			}
		}

		return false;
	}

	/** {@code /legionwarex illagers [cool|warm <n>]} (operators): the warmth where it runs, and the next arrival's chance. */
	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("illagers")
				.executes(IllagerCrowding::show)
				.then(Commands.literal("cool").executes(context -> {
					BlockPos at = BlockPos.containing(context.getSource().getPosition());
					int cooled = of(context.getSource().getLevel()).cool(at);
					return say(context.getSource(), "cooled " + cooled + " chunk(s) around " + at.toShortString());
				}))
				.then(Commands.literal("warm").then(Commands.argument("amount", DoubleArgumentType.doubleArg(-1000.0, 1000.0))
						.executes(context -> {
							ServerLevel level = context.getSource().getLevel();
							BlockPos at = BlockPos.containing(context.getSource().getPosition());
							of(level).warm(at, level.getGameTime(), DoubleArgumentType.getDouble(context, "amount"));
							return show(context);
						})));
	}

	private static int show(CommandContext<CommandSourceStack> context) {
		ServerLevel level = context.getSource().getLevel();
		BlockPos at = BlockPos.containing(context.getSource().getPosition());
		double warmth = of(level).warmthAt(at, level.getGameTime());
		return say(context.getSource(), "warmth " + round(warmth) + " at " + at.toShortString() + ": the next patrol, war band or scout here has a "
				+ Math.round(chance(warmth) * 100) + "% chance" + (AddonConfig.illagerCrowding() ? "" : " (off in the config: they always come)")
				+ "; warmth halves every " + round(AddonConfig.illagerCoolingDays()) + " days");
	}

	private static int say(CommandSourceStack source, String text) {
		log(text);
		source.sendSuccess(() -> Component.literal("LegionWareX illagers: " + text), true);
		return 1;
	}

	/** Forgets the group decision (game tests spawn one patrol after another in the same tick). */
	public void forgetGroup() {
		groupTick = Long.MIN_VALUE;
	}

	static String round(double value) {
		return String.valueOf(Math.round(value * 100.0) / 100.0);
	}

	static void log(String message) {
		LegionWareX.LOGGER.info("LegionWareX illagers: {}", message);
	}

	private static IllagerCrowding load(CompoundTag tag) {
		IllagerCrowding data = new IllagerCrowding();

		for (Tag element : tag.getList("chunks", Tag.TAG_COMPOUND)) {
			CompoundTag entry = (CompoundTag) element;
			Warmth warmth = new Warmth();
			warmth.value = entry.getDouble("warmth");
			warmth.tick = entry.getLong("tick");
			data.chunks.put(entry.getLong("chunk"), warmth);
		}

		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag list = new ListTag();
		Iterator<Map.Entry<Long, Warmth>> entries = chunks.entrySet().iterator();

		while (entries.hasNext()) {
			Map.Entry<Long, Warmth> entry = entries.next();

			// Cooled right down by the last time anyone looked: forgotten rather than saved.
			if (entry.getValue().at(lastSeen) < FORGET) {
				entries.remove();
				continue;
			}

			CompoundTag row = new CompoundTag();
			row.putLong("chunk", entry.getKey());
			row.putDouble("warmth", entry.getValue().value);
			row.putLong("tick", entry.getValue().tick);
			list.add(row);
		}

		tag.put("chunks", list);
		return tag;
	}
}
