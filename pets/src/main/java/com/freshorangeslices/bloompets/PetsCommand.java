package com.freshorangeslices.bloompets;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * {@code /bloompets} (also {@code /pets}), until the pet screen: list your pets, put one in your party, call one,
 * open one's storage, the roster; and for operators, give a player a bonded pet.
 */
public final class PetsCommand {
	private PetsCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var root = Commands.literal(BloomPets.MOD_ID)
				.executes(PetsCommand::list)
				.then(Commands.literal("list").executes(PetsCommand::list))
				.then(Commands.literal("party").then(pet().executes(c -> act(c, Action.PARTY))))
				.then(Commands.literal("call").then(pet().executes(c -> act(c, Action.CALL))))
				.then(Commands.literal("storage").then(pet().executes(c -> act(c, Action.STORAGE))))
				.then(Commands.literal("species").executes(PetsCommand::species))
				.then(Commands.literal("screen").executes(c -> {
					com.freshorangeslices.bloompets.network.PetsNetwork.sendList(c.getSource().getPlayerOrException(), true, null);
					return 1;
				}))
				.then(Commands.literal("give").requires(source -> source.hasPermission(2))
						.then(Commands.argument("species", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(SpeciesData.all().stream().map(s -> s.id().getPath()), b))
								.executes(c -> give(c, c.getSource().getPlayerOrException()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> give(c, EntityArgument.getPlayer(c, "player"))))));
		dispatcher.register(root);
		dispatcher.register(Commands.literal("pets").executes(PetsCommand::list).redirect(dispatcher.getRoot().getChild(BloomPets.MOD_ID)));
	}

	private enum Action {
		PARTY, CALL, STORAGE
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> pet() {
		return Commands.argument("pet", StringArgumentType.greedyString()).suggests((c, b) -> {
			ServerPlayer player = c.getSource().getPlayer();
			List<String> names = player == null ? List.of()
					: PetRegistry.get(player.server).pets(player.getUUID()).stream().map(r -> r.name).toList();
			return SharedSuggestionProvider.suggest(names, b);
		});
	}

	/** The pet by its number in the list, or by name (the first one with it). */
	private static PetRegistry.Record find(ServerPlayer player, String wanted) {
		List<PetRegistry.Record> pets = PetRegistry.get(player.server).pets(player.getUUID());

		try {
			int index = Integer.parseInt(wanted.trim()) - 1;
			return index >= 0 && index < pets.size() ? pets.get(index) : null;
		} catch (NumberFormatException e) {
			return pets.stream().filter(r -> r.name.equalsIgnoreCase(wanted.trim())).findFirst().orElse(null);
		}
	}

	private static int act(CommandContext<CommandSourceStack> context, Action action) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String wanted = StringArgumentType.getString(context, "pet");
		PetRegistry.Record record = find(player, wanted);

		if (record == null) {
			context.getSource().sendFailure(Component.translatable("bloompets.command.nopet", wanted));
			return 0;
		}

		Component message = switch (action) {
			case PARTY -> Party.toggle(player, record);
			case CALL -> Calling.call(player, record);
			case STORAGE -> PetStorage.open(player, record);
		};

		if (message != null) {
			context.getSource().sendSuccess(() -> message, false);
		}

		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		List<PetRegistry.Record> pets = PetRegistry.get(player.server).pets(player.getUUID());

		if (pets.isEmpty()) {
			context.getSource().sendSuccess(() -> Component.translatable("bloompets.command.none"), false);
			return 0;
		}

		MutableComponent text = Component.translatable("bloompets.command.header", pets.size());

		for (int i = 0; i < pets.size(); i++) {
			PetRegistry.Record r = pets.get(i);
			boolean here = Pets.loaded(player.server, r.pet) != null;
			String kind = r.kind == Pets.Kind.MODDED ? "companion" : (r.party ? "party" : "Bloom pet");
			text.append(Component.literal("\n" + (i + 1) + ". ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(r.name).withStyle(r.party ? ChatFormatting.LIGHT_PURPLE
							: r.kind == Pets.Kind.MODDED ? ChatFormatting.DARK_RED : ChatFormatting.GOLD))
					.append(Component.literal(String.format(Locale.ROOT, "  %s, %s  %s %d %d %d%s", shortType(r.type), kind,
							shortDim(r.dimension), r.pos.getX(), r.pos.getY(), r.pos.getZ(), here ? "" : " (not loaded)")).withStyle(ChatFormatting.GRAY));
		}

		context.getSource().sendSuccess(() -> text, false);
		return pets.size();
	}

	private static int species(CommandContext<CommandSourceStack> context) {
		String text = SpeciesData.all().stream()
				.map(s -> s.displayName() + " (" + s.category() + "): " + s.feeds() + "x "
						+ s.foods().stream().map(i -> i.getDescription().getString()).collect(Collectors.joining(", "))
						+ (s.foodTags().isEmpty() ? "" : " " + s.foodTags().stream().map(t -> "#" + t.location()).collect(Collectors.joining(", "))))
				.collect(Collectors.joining("\n"));
		context.getSource().sendSuccess(() -> Component.literal(text), false);
		return SpeciesData.all().size();
	}

	/** Operators: spawns a pet of the species beside the player, already bonded to them. */
	private static int give(CommandContext<CommandSourceStack> context, ServerPlayer player) {
		Species species = SpeciesData.byName(StringArgumentType.getString(context, "species"));

		if (species == null) {
			context.getSource().sendFailure(Component.translatable("bloompets.command.nospecies"));
			return 0;
		}

		Entity entity = species.type().create(player.level());

		if (!(entity instanceof Mob mob)) {
			return 0;
		}

		mob.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
		Pets.claim(mob, player);
		player.level().addFreshEntity(mob);
		PetEvents.observe(mob);
		context.getSource().sendSuccess(() -> Component.translatable("bloompets.bond.done", mob.getName(), species.displayName()), true);
		return 1;
	}

	private static String shortType(String type) {
		String path = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
		return path.replace('_', ' ');
	}

	private static String shortDim(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dimension;
		};
	}
}
