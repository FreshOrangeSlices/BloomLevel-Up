package com.freshorangeslices.legionwarex.sentinel;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code /legionwarex sentinel [<entity>|all|check]} (operators): a sentinel where the command runs (the one the table
 * picks for the biome there, or the one named); every installed kind in a row, 4 blocks apart, for a look at them all;
 * or how the sentinels within 96 blocks are doing (alive, burning, helmet, health). Each answer is also logged as
 * "LegionWareX sentinel: ..." for the pack test.
 */
public final class SentinelCommand {
	private SentinelCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("sentinel")
				.executes(context -> one(context, null))
				.then(Commands.literal("all").executes(SentinelCommand::all))
				.then(Commands.literal("check").executes(SentinelCommand::check))
				.then(Commands.argument("entity", ResourceLocationArgument.id())
						.suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
								SentinelTable.all().stream().filter(SentinelTable.Entry::present).map(SentinelTable.Entry::entity), builder))
						.executes(context -> one(context, ResourceLocationArgument.getId(context, "entity"))));
	}

	private static void say(CommandSourceStack source, String text, boolean ok) {
		LegionWareX.LOGGER.info("LegionWareX sentinel: {}", text);

		if (ok) {
			source.sendSuccess(() -> Component.literal("LegionWareX sentinel: " + text), true);
		} else {
			source.sendFailure(Component.literal("LegionWareX sentinel: " + text));
		}
	}

	private static BlockPos ground(ServerLevel level, BlockPos at) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
	}

	private static int one(CommandContext<CommandSourceStack> context, ResourceLocation id) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		BlockPos at = ground(level, BlockPos.containing(source.getPosition()));
		SentinelTable.Entry entry;

		if (id == null) {
			entry = SentinelTable.pick(level.getBiome(at), level.getRandom(), false);
		} else {
			entry = SentinelTable.all().stream().filter(e -> e.entity().equals(id)).findFirst()
					.orElse(new SentinelTable.Entry(id, 1, List.of(), false));
		}

		if (entry == null || !entry.present()) {
			say(source, "no sentinel for " + (id == null ? "this biome" : id), false);
			return 0;
		}

		Mob mob = Sentinels.spawn(level, entry, at, false);
		say(source, mob == null ? "couldn't place " + entry.entity() + " at " + at.toShortString() : describe(mob), mob != null);
		return mob == null ? 0 : 1;
	}

	private static int all(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		BlockPos start = BlockPos.containing(source.getPosition());
		List<String> placed = new ArrayList<>();
		List<String> failed = new ArrayList<>();
		int i = 0;

		for (SentinelTable.Entry entry : SentinelTable.all()) {
			if (!entry.present()) {
				continue;
			}

			BlockPos at = ground(level, start.offset((i % 8) * 4 - 14, 0, 10 + (i / 8) * 5));
			i++;
			Mob mob = Sentinels.spawn(level, entry, at, false);
			(mob == null ? failed : placed).add(entry.entity().toString());
		}

		say(source, "placed " + placed.size() + " of " + (placed.size() + failed.size()) + " installed kinds: " + String.join(", ", placed)
				+ (failed.isEmpty() ? "" : "; couldn't place: " + String.join(", ", failed)), true);
		return placed.size();
	}

	private static int check(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		List<Mob> sentinels = level.getEntitiesOfClass(Mob.class, new AABB(BlockPos.containing(source.getPosition())).inflate(96), Sentinels::isSentinel);
		List<String> rows = sentinels.stream().map(SentinelCommand::describe).toList();
		say(source, sentinels.size() + " sentinels near: " + String.join("; ", rows), true);
		return sentinels.size();
	}

	/** "id (hp 40/40, helmet, burning, post 1 away)" for logs and the pack test. */
	static String describe(Mob mob) {
		List<String> notes = new ArrayList<>();
		notes.add("hp " + Math.round(mob.getHealth()) + "/" + Math.round(mob.getMaxHealth()));

		if (!mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			notes.add("helmet");
		}

		if (mob.isOnFire()) {
			notes.add("burning");
		}

		BlockPos post = Sentinels.postOf(mob);
		notes.add(post == null ? "roams" : "post " + Math.round(Math.sqrt(mob.blockPosition().distSqr(post))) + " away");
		return ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()) + " (" + String.join(", ", notes) + ")";
	}
}
