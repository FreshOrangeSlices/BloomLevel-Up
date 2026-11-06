package com.freshorangeslices.bloompets;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * {@code /bloompets} (also {@code /pets}): list your pets, put one in your party, call one, open one's storage, the
 * roster, the pet screen; and for operators, give a player a bonded pet, set a pet's level or give it XP.
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
				.then(Commands.literal("post").then(pet().executes(c -> act(c, Action.POST))))
				.then(Commands.literal("tree").then(Commands.argument("tree", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("sword", "axe", "trident", "unarmed", "reset"), b))
						.then(pet().executes(PetsCommand::tree))))
				.then(Commands.literal("species").executes(PetsCommand::species))
				.then(Commands.literal("screen").executes(c -> {
					com.freshorangeslices.bloompets.network.PetsNetwork.sendList(c.getSource().getPlayerOrException(), true, null);
					return 1;
				}).then(Commands.literal("bloom").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					boolean has = BloomBridge.oneAndTheSame(player);
					BloomPets.LOGGER.info("Bloom Pets: {} opens the pet screen's Bloom view: One and the Same {} (Bloom's API {})",
							player.getGameProfile().getName(), has ? "on" : "off", BloomBridge.status());

					if (!has) {
						c.getSource().sendFailure(Component.translatable("bloompets.tree.needs"));
					}

					com.freshorangeslices.bloompets.network.PetsNetwork.sendList(player, true, null, has);
					return 1;
				})))
				.then(Commands.literal("level").requires(source -> source.hasPermission(2))
						.then(Commands.argument("level", IntegerArgumentType.integer(1, PetLevels.MAX)).then(pet().executes(c -> level(c, false)))))
				.then(Commands.literal("xp").requires(source -> source.hasPermission(2))
						.then(Commands.argument("amount", IntegerArgumentType.integer(1)).then(pet().executes(c -> level(c, true)))))
				.then(Commands.literal("info").requires(source -> source.hasPermission(2))
						.then(pet().executes(c -> info(c, c.getSource().getPlayerOrException()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(c -> info(c, EntityArgument.getPlayer(c, "player"))))))
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
		PARTY, CALL, STORAGE, POST
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
			case POST -> PetPosts.toggle(player, record);
		};

		if (message != null) {
			context.getSource().sendSuccess(() -> message, false);
		}

		return 1;
	}

	/** Operators: sets one of their pets' level, or gives it XP (it has to be loaded: the pet carries its level). */
	private static int level(CommandContext<CommandSourceStack> context, boolean xp) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String wanted = StringArgumentType.getString(context, "pet");
		PetRegistry.Record record = find(player, wanted);
		Entity pet = record == null ? null : Pets.loaded(player.server, record.pet);

		if (record == null) {
			context.getSource().sendFailure(Component.translatable("bloompets.command.nopet", wanted));
			return 0;
		}

		if (!(pet instanceof LivingEntity living)) {
			context.getSource().sendFailure(Component.translatable("bloompets.command.notloaded", record.name));
			return 0;
		}

		if (xp) {
			PetLevels.addXp(living, IntegerArgumentType.getInteger(context, "amount"));
		} else {
			PetLevels.setLevel(living, IntegerArgumentType.getInteger(context, "level"));
		}

		context.getSource().sendSuccess(() -> Component.translatable("bloompets.command.level", record.name, PetLevels.level(living)), true);
		return PetLevels.level(living);
	}

	/**
	 * Operators: everything Bloom Pets keeps about one pet (one of the player's, by its number or name), to answer "my pet
	 * didn't level": its level and XP (an Amigo's own, with Bloom Pets' pace count and the levels waiting for its next
	 * kill), its tree, mood and hunger, where it stands (party, post, fainted) and where it is. From the pet itself when
	 * it's loaded, else from its entry.
	 */
	private static int info(CommandContext<CommandSourceStack> context, ServerPlayer owner) {
		String wanted = StringArgumentType.getString(context, "pet");
		PetRegistry.Record r = find(owner, wanted);

		if (r == null) {
			context.getSource().sendFailure(Component.translatable("bloompets.command.nopet", wanted));
			return 0;
		}

		Entity pet = Pets.loaded(owner.server, r.pet);
		boolean live = pet instanceof LivingEntity;
		long now = owner.server.overworld().getGameTime();
		List<String> lines = new java.util.ArrayList<>();
		lines.add(r.name + " (" + r.type + ", " + r.kind.name().toLowerCase(Locale.ROOT) + ") of " + owner.getGameProfile().getName()
				+ (live ? "" : " · not loaded: from its entry"));

		if (live && Amigos.has(pet)) {
			CompoundTag data = Pets.data(pet);
			lines.add(String.format(Locale.ROOT, "Amigo level %d, XP %d / %d · waiting for its next kill: %d levels · Bloom Pets' pace: level %s, XP %.1f",
					Amigos.level(pet), Amigos.xp(pet), Amigos.xpNext(pet, Amigos.level(pet)),
					Math.max(0, Amigos.owedLevel(pet) - Amigos.level(pet)),
					data.contains("paceLevel") ? Integer.toString(data.getInt("paceLevel")) : "not started", data.getDouble("paceXp")));
		} else {
			lines.add(String.format(Locale.ROOT, "Level %d, XP %.1f / %.0f", live ? PetLevels.level(pet) : r.level,
					live ? PetLevels.xp(pet) : r.xp, live ? PetLevels.xpNext(pet) : r.xpNext));
		}

		PetTrees.Tree tree = live ? PetTrees.tree(pet) : PetTrees.Tree.byId(r.tree);
		long treeWait = (live ? Pets.data(pet).getLong("treeWait") : r.treeWait) - now;
		String off = String.join(",", live ? PetTrees.abilitiesOff(pet) : r.abilitiesOff);
		lines.add("Tree: " + (tree == null ? "none" : tree.id() + (live ? (PetTrees.treeLevel(pet) >= 0 ? " (working)" : " (resting)") : ""))
				+ (off.isEmpty() ? "" : " · off: " + off) + (treeWait > 0 ? " · can pick again in " + clock(treeWait) : "")
				+ " · owner has One and the Same: " + (PetRegistry.get(owner.server).oneAndTheSame(owner.getUUID()) ? "yes" : "no"));

		if (r.kind == Pets.Kind.BLOOM) {
			double mood = live ? PetMood.mood(pet) : r.mood;
			double hunger = live ? PetMood.hunger(pet) : r.hunger;
			boolean loved = live ? PetMood.loved(pet) : r.loved;
			boolean tired = live ? PetMood.tired(pet) : r.tired;
			lines.add(String.format(Locale.ROOT, "Mood %.0f%s · hunger %.1f / 20%s%s", mood, mood < 30.0 ? " (sulking)" : "", hunger,
					loved ? " · loves its owner" : "", tired ? " · tired" : ""));
		}

		int rest = live ? Fainting.ticksLeft(pet) : (int) Math.max(0, r.faintedUntil - now);
		boolean posted = live ? PetPosts.posted(pet) : r.post != null;
		lines.add((r.party ? "In the party" : posted ? "At its post" : r.kind == Pets.Kind.MODDED ? "A companion" : "At home")
				+ (rest > 0 ? " · fainted, back in " + clock(rest) : "") + " · " + r.dimension + " " + r.pos.toShortString()
				+ (live ? String.format(Locale.ROOT, " · health %.0f / %.0f", ((LivingEntity) pet).getHealth(), ((LivingEntity) pet).getMaxHealth()) : ""));

		for (String line : lines) {
			context.getSource().sendSuccess(() -> Component.literal(line), false);
		}

		return 1;
	}

	private static String clock(long ticks) {
		long seconds = ticks / 20;
		return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
	}

	/** One and the Same: pick a pet's weapon tree, or reset it. */
	private static int tree(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		String wanted = StringArgumentType.getString(context, "pet");
		String tree = StringArgumentType.getString(context, "tree");
		PetRegistry.Record record = find(player, wanted);

		if (record == null) {
			context.getSource().sendFailure(Component.translatable("bloompets.command.nopet", wanted));
			return 0;
		}

		Component message = "reset".equals(tree) ? PetTrees.reset(player, record) : PetTrees.pick(player, record, tree);

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
			String kind = r.kind == Pets.Kind.MODDED ? "companion" : r.party ? "party" : r.post != null ? "at its post" : "Bloom pet";
			text.append(Component.literal("\n" + (i + 1) + ". ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(r.name).withStyle(r.party ? ChatFormatting.LIGHT_PURPLE
							: r.kind == Pets.Kind.MODDED ? ChatFormatting.DARK_RED : ChatFormatting.GOLD))
					.append(Component.literal(String.format(Locale.ROOT, "  Lv %d %s, %s  %s %d %d %d%s", r.level, shortType(r.type), kind,
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
