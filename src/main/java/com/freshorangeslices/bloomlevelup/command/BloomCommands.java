package com.freshorangeslices.bloomlevelup.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.definition.CategoryDefinition;
import com.freshorangeslices.bloomlevelup.definition.DefinitionSet;
import com.freshorangeslices.bloomlevelup.definition.MajorDefinition;
import com.freshorangeslices.bloomlevelup.definition.StatDefinition;
import com.freshorangeslices.bloomlevelup.network.BloomSync;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.CategoryProgress;
import com.freshorangeslices.bloomlevelup.player.OwnedMajor;
import com.freshorangeslices.bloomlevelup.player.PlayerXpWallet;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.BloomResetResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.progression.XpGainResult;

/**
 * {@code /bloom} — a temporary, text-only front end for the framework so every rule can be exercised in
 * game before the hub screen exists. Player sub-commands go through exactly the same
 * {@link ProgressionService} calls the hub will use; {@code /bloom admin} is for testing and pack authors.
 */
public final class BloomCommands {
	private BloomCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> register(dispatcher));
	}

	private static final SuggestionProvider<CommandSourceStack> CATEGORIES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(ids(BloomServices.definitions().categories(), CategoryDefinition::id), builder);

	private static final SuggestionProvider<CommandSourceStack> STATS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();

		for (CategoryDefinition category : BloomServices.definitions().categories()) {
			ids.addAll(ids(category.stats(), StatDefinition::id));
		}

		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> MAJORS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(ids(BloomServices.definitions().majors(), MajorDefinition::id), builder);

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("bloom")
				.executes(ctx -> info(ctx.getSource(), ctx.getSource().getPlayerOrException()))
				.then(literal("info")
						.executes(ctx -> info(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(argument("player", EntityArgument.player())
								.requires(PermissionPredicates.require(BloomLevelUp.id("command/inspect"), PermissionLevel.GAMEMASTERS))
								.executes(ctx -> info(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("allocate")
						.then(argument("stat", IdentifierArgument.id()).suggests(STATS)
								.then(argument("amount", IntegerArgumentType.integer())
										.executes(ctx -> allocate(ctx, ProgressionService.Pool.NATURAL))
										.then(literal("invested")
												.executes(ctx -> allocate(ctx, ProgressionService.Pool.INVESTED))))))
				.then(literal("toggle")
						.then(literal("stat").then(argument("stat", IdentifierArgument.id()).suggests(STATS)
								.executes(ctx -> act(ctx, (svc, p) -> svc.toggleStat(p, id(ctx, "stat"))))))
						.then(literal("major").then(argument("major", IdentifierArgument.id()).suggests(MAJORS)
								.executes(ctx -> act(ctx, (svc, p) -> svc.toggleMajor(p, id(ctx, "major")))))))
				.then(literal("buy")
						.then(argument("major", IdentifierArgument.id()).suggests(MAJORS)
								.executes(ctx -> act(ctx, (svc, p) -> svc.purchaseMajor(p, id(ctx, "major"))))
								.then(literal("swap")
										.executes(ctx -> act(ctx, (svc, p) -> svc.purchaseMajorWithSwap(p, id(ctx, "major")))))))
				.then(literal("refund")
						.then(argument("major", IdentifierArgument.id()).suggests(MAJORS)
								.executes(ctx -> act(ctx, (svc, p) -> svc.refundMajor(p, id(ctx, "major"))))))
				.then(literal("fund")
						.then(argument("category", IdentifierArgument.id()).suggests(CATEGORIES)
								.then(argument("points", IntegerArgumentType.integer(1))
										.executes(ctx -> {
											ServerPlayer player = ctx.getSource().getPlayerOrException();
											return act(ctx, (svc, p) -> svc.fundWithVanillaXp(p, id(ctx, "category"),
													IntegerArgumentType.getInteger(ctx, "points"), new PlayerXpWallet(player)));
										}))))
				.then(literal("invest")
						.then(argument("category", IdentifierArgument.id()).suggests(CATEGORIES)
								.then(argument("count", IntegerArgumentType.integer(1))
										.executes(ctx -> {
											ServerPlayer player = ctx.getSource().getPlayerOrException();
											return act(ctx, (svc, p) -> svc.invest(p, id(ctx, "category"),
													IntegerArgumentType.getInteger(ctx, "count"), new PlayerXpWallet(player)));
										}))))
				.then(literal("bloomreset")
						.executes(ctx -> {
							ctx.getSource().sendSuccess(() -> Component.translatable("bloom.command.reset.confirm_hint").withStyle(ChatFormatting.YELLOW), false);
							return 0;
						})
						.then(literal("confirm").executes(ctx -> bloomReset(ctx.getSource(), ctx.getSource().getPlayerOrException()))))
				.then(literal("admin")
						.requires(PermissionPredicates.require(BloomLevelUp.id("command/admin"), PermissionLevel.GAMEMASTERS))
						.then(literal("xp")
								.then(argument("player", EntityArgument.player())
										.then(argument("category", IdentifierArgument.id()).suggests(CATEGORIES)
												.then(argument("amount", IntegerArgumentType.integer(1))
														.executes(BloomCommands::adminXp)))))
						.then(literal("definitions").executes(ctx -> definitions(ctx.getSource())))));
	}

	// ------------------------------------------------------------------ handlers

	@FunctionalInterface
	private interface Action {
		ActionResult run(ProgressionService service, BloomProfile profile);
	}

	private static int act(CommandContext<CommandSourceStack> ctx, Action action) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ActionResult result = action.run(BloomServices.progression(), BloomAttachments.profile(player));

		if (result.ok()) {
			BloomSync.changed(player);
		}

		return report(ctx.getSource(), result);
	}

	private static int allocate(CommandContext<CommandSourceStack> ctx, ProgressionService.Pool pool) throws CommandSyntaxException {
		int amount = IntegerArgumentType.getInteger(ctx, "amount");
		return act(ctx, (svc, p) -> svc.allocate(p, id(ctx, "stat"), pool, amount));
	}

	private static int report(CommandSourceStack source, ActionResult result) {
		MutableComponent message = Component.translatable(result.translationKey());

		if (!result.involved().isEmpty()) {
			message.append(Component.literal(" " + String.join(", ", result.involved())));
		}

		if (result.ok()) {
			source.sendSuccess(() -> message.withStyle(ChatFormatting.GREEN), false);
			return 1;
		}

		source.sendFailure(message);
		return 0;
	}

	private static int adminXp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		String category = id(ctx, "category");

		if (BloomServices.definitions().category(category).isEmpty()) {
			return report(ctx.getSource(), ActionResult.of(ActionResult.Outcome.UNKNOWN_CATEGORY));
		}

		XpGainResult result = BloomServices.progression().addXp(BloomAttachments.profile(target), category,
				IntegerArgumentType.getInteger(ctx, "amount"));
		BloomSync.changed(target);
		ctx.getSource().sendSuccess(() -> Component.translatable("bloom.command.xp.granted",
				target.getDisplayName(), result.levelsGained(), result.newLevel(), result.majorLevelsGranted()), true);
		return 1;
	}

	private static int bloomReset(CommandSourceStack source, ServerPlayer player) {
		BloomResetResult result = BloomServices.progression().bloomReset(BloomAttachments.profile(player));
		BloomSync.changed(player);
		source.sendSuccess(() -> Component.translatable("bloom.command.reset.done",
				result.bloomCount(), result.majorLevelsRefunded(), result.totalPrestigeAwarded()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
		return 1;
	}

	private static int definitions(CommandSourceStack source) {
		DefinitionSet defs = BloomServices.definitions();
		source.sendSuccess(() -> Component.translatable("bloom.command.definitions.summary",
				defs.categories().size(), defs.majors().size(), defs.warnings().size()), false);

		for (String warning : defs.warnings()) {
			source.sendSuccess(() -> Component.literal(" - " + warning).withStyle(ChatFormatting.GOLD), false);
		}

		return 1;
	}

	private static int info(CommandSourceStack source, ServerPlayer player) {
		ProgressionService service = BloomServices.progression();
		DefinitionSet defs = service.definitions();
		BloomProfile profile = BloomAttachments.profile(player);

		source.sendSuccess(() -> Component.translatable("bloom.command.info.header", player.getDisplayName(),
				profile.availableMajorLevels(), profile.earnedMajorLevels(), profile.spentMajorLevels(), profile.bloomCount())
				.withStyle(ChatFormatting.AQUA), false);

		for (CategoryDefinition category : defs.categories()) {
			CategoryProgress progress = profile.category(category.id());
			MutableComponent line = Component.empty()
					.append(name(category.translationKey(), category.id()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
					.append(Component.translatable("bloom.command.info.category",
							progress.level(), (int) progress.xp(), (int) service.xpToNext(progress.level()),
							progress.naturalUnspent(), progress.investedUnspent()).withStyle(ChatFormatting.GRAY));

			if (service.investmentUnlocked(profile, category.id())) {
				line.append(Component.translatable("bloom.command.info.mastered").withStyle(ChatFormatting.GREEN));
			}

			source.sendSuccess(() -> line, false);

			for (StatDefinition stat : category.stats()) {
				boolean off = progress.disabledStats().contains(stat.id());
				MutableComponent statLine = Component.literal("   ")
						.append(name(stat.translationKey(), stat.id()))
						.append(Component.literal(": " + progress.rawStat(stat.id())))
						.withStyle(off ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY);

				if (off) {
					statLine.append(Component.translatable("bloom.command.info.off"));
				}

				source.sendSuccess(() -> statLine, false);
			}
		}

		if (!profile.majors().isEmpty()) {
			source.sendSuccess(() -> Component.translatable("bloom.command.info.majors").withStyle(ChatFormatting.AQUA), false);

			for (Map.Entry<String, OwnedMajor> entry : profile.majors().entrySet()) {
				String key = defs.major(entry.getKey()).map(MajorDefinition::translationKey).orElse(entry.getKey());
				boolean active = service.isMajorActive(profile, entry.getKey());
				source.sendSuccess(() -> Component.literal("   ")
						.append(name(key, entry.getKey()))
						.append(Component.literal(active ? "  [ON]" : "  [OFF]"))
						.withStyle(active ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), false);
			}
		}

		return 1;
	}

	// ------------------------------------------------------------------ helpers

	private static String id(CommandContext<CommandSourceStack> ctx, String name) {
		return IdentifierArgument.getId(ctx, name).toString();
	}

	private static MutableComponent name(String translationKey, String fallback) {
		return Component.translatableWithFallback(translationKey + ".name", fallback);
	}

	private static <T> List<String> ids(Iterable<T> items, Function<T, String> getter) {
		List<String> list = new ArrayList<>();

		for (T item : items) {
			list.add(getter.apply(item));
		}

		return list;
	}
}
