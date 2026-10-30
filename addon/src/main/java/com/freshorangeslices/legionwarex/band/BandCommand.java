package com.freshorangeslices.legionwarex.band;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

/**
 * {@code /legionwarex band war|pirate [size]} (operators): a band a few blocks from where the command runs, at once
 * and anywhere (no daylight, coast or cadence), the size a patrol would be there unless given. For trying them out.
 */
public final class BandCommand {
	private BandCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		LiteralArgumentBuilder<CommandSourceStack> band = Commands.literal("band");

		for (BandKind kind : BandKind.values()) {
			band.then(Commands.literal(kind.id)
					.executes(context -> call(context, kind, 0))
					.then(Commands.argument("size", IntegerArgumentType.integer(1, 12))
							.executes(context -> call(context, kind, IntegerArgumentType.getInteger(context, "size")))));
		}

		return band;
	}

	private static int call(CommandContext<CommandSourceStack> context, BandKind kind, int size) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		BlockPos origin = BlockPos.containing(source.getPosition());
		RandomSource random = level.getRandom();

		if (!kind.available()) {
			source.sendFailure(Component.literal("LegionWareX band: no " + kind.id + " band without its mod"));
			return 0;
		}

		for (int attempt = 0; attempt < 24; attempt++) {
			BlockPos spot = attempt == 0 ? origin : Bands.offset(origin, random, 1, 6);
			List<String> leftOut = new ArrayList<>();
			List<Mob> band = Bands.spawn(level, kind, spot, random, size > 0 ? size : Bands.vanillaSize(level, spot), true, leftOut);

			if (!band.isEmpty()) {
				String text = Bands.describe(kind, band) + (leftOut.isEmpty() ? "" : "; left out: " + String.join("; ", leftOut));
				Bands.log(text + " (by command)");
				source.sendSuccess(() -> Component.literal("LegionWareX band: " + text), true);
				return band.size();
			}
		}

		source.sendFailure(Component.literal("LegionWareX band: no room for a " + kind.id + " band near " + origin.toShortString()));
		return 0;
	}
}
