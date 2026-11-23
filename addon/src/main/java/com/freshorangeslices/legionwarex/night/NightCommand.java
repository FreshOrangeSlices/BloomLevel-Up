package com.freshorangeslices.legionwarex.night;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code /legionwarex night}: where the cycle stands; {@code themes}: every night and its mobs (installed, and any whose
 * mod is installed but isn't in the game: a wrong id); {@code start <night>}: that night now (the time set to dusk's end
 * if it's day); {@code stop}; {@code in <days>}: the next one that many days from today; {@code show}: one of each of
 * tonight's installed mobs in rows in front of you, with the night's health; {@code check}: 40 natural spawns of a
 * monster that isn't the night's, tried where you stand, and what came instead. Logged as "LegionWareX night:".
 */
public final class NightCommand {
	private NightCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("night")
				.executes(NightCommand::status)
				.then(Commands.literal("themes").executes(NightCommand::themes))
				.then(Commands.literal("start").then(Commands.argument("night", ResourceLocationArgument.id())
						.suggests((context, builder) -> SharedSuggestionProvider.suggestResource(NightTable.all().keySet(), builder))
						.executes(NightCommand::start)))
				.then(Commands.literal("stop").executes(NightCommand::stop))
				.then(Commands.literal("in").then(Commands.argument("days", IntegerArgumentType.integer(0, 365)).executes(context -> {
					ServerLevel level = context.getSource().getServer().overworld();
					NightCycle.of(level).nextIn(level, IntegerArgumentType.getInteger(context, "days"));
					return status(context);
				})))
				.then(Commands.literal("show").executes(NightCommand::show))
				.then(Commands.literal("check").executes(NightCommand::check));
	}

	private static int say(CommandSourceStack source, String text, boolean ok) {
		NightCycle.log(text);

		if (ok) {
			source.sendSuccess(() -> Component.literal("LegionWareX night: " + text), true);
		} else {
			source.sendFailure(Component.literal("LegionWareX night: " + text));
		}

		return ok ? 1 : 0;
	}

	private static int status(CommandContext<CommandSourceStack> context) {
		ServerLevel level = context.getSource().getServer().overworld();
		NightCycle cycle = NightCycle.of(level);
		NightTable.Theme active = cycle.active();
		long day = level.getDayTime() / AddonConfig.DAY;
		String deck = String.join(", ", cycle.deck().stream().map(ResourceLocation::getPath).toList());
		long blood = BloodMoon.nextDay(level);
		String text = (active != null ? "tonight is " + active.name() + " (" + active.id() + ", night #" + cycle.count() + ")"
				: "no themed night now; the next on day " + cycle.nextDay() + " (in " + Math.max(0L, cycle.nextDay() - day) + " days)")
				+ "; today is day " + day + "; the deck: " + (deck.isEmpty() ? "(shuffled when next dealt)" : deck)
				+ (blood >= 0L ? "; the Blood Moon is next due on day " + blood : "")
				+ (AddonConfig.themedNights() ? "" : " (themed nights are off in the config)");
		return say(context.getSource(), text, true);
	}

	private static int themes(CommandContext<CommandSourceStack> context) {
		List<String> rows = new ArrayList<>();
		List<String> wrong = new ArrayList<>();

		for (NightTable.Theme theme : NightTable.all().values()) {
			rows.add(theme.id() + " " + theme.name() + " (health x" + theme.health() + ", " + theme.installed().size() + " of " + theme.mobs().size()
					+ " installed)");

			for (NightTable.Mob mob : theme.mobs()) {
				if (!mob.present() && ModList.get().isLoaded(mob.entity().getNamespace())) {
					wrong.add(mob.entity() + " (" + theme.id().getPath() + ")");
				}
			}
		}

		return say(context.getSource(), NightTable.all().size() + " themes: " + String.join("; ", rows)
				+ (wrong.isEmpty() ? "" : "; not in the game though their mod is: " + String.join(", ", wrong)), true);
	}

	private static int start(CommandContext<CommandSourceStack> context) {
		ServerLevel level = context.getSource().getServer().overworld();
		ResourceLocation id = ResourceLocationArgument.getId(context, "night");
		NightTable.Theme theme = NightTable.get(id);

		if (theme == null) {
			return say(context.getSource(), "no night " + id + " (there are " + String.join(", ", NightTable.all().keySet().stream().map(String::valueOf).toList()) + ")", false);
		}

		NightCycle cycle = NightCycle.of(level);

		if (cycle.active() != null) {
			cycle.end(level, "another was started by command");
		}

		long time = level.getDayTime();
		long day = time / AddonConfig.DAY;
		long hour = time % AddonConfig.DAY;

		if (hour < NightCycle.DUSK || hour >= NightCycle.DAWN) {
			// As Myths & Legends' own command does: the time set to the night of today.
			level.setDayTime(day * AddonConfig.DAY + 13000L);
		}

		cycle.start(level, theme, day);
		return say(context.getSource(), "started " + theme.id() + " (" + theme.name() + ")", true);
	}

	private static int stop(CommandContext<CommandSourceStack> context) {
		ServerLevel level = context.getSource().getServer().overworld();
		NightCycle cycle = NightCycle.of(level);

		if (cycle.active() == null) {
			return say(context.getSource(), "no themed night to stop", false);
		}

		cycle.end(level, "stopped by command");
		return say(context.getSource(), "stopped", true);
	}

	private static int show(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		NightTable.Theme theme = NightEvents.active(level);

		if (theme == null) {
			return say(source, "no themed night on (start one first)", false);
		}

		BlockPos start = BlockPos.containing(source.getPosition());
		List<String> placed = new ArrayList<>();
		List<String> failed = new ArrayList<>();
		int i = 0;

		for (NightTable.Mob mob : theme.installed()) {
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.offset((i % 8) * 4 - 14, 0, 10 + (i / 8) * 5));
			i++;
			Mob spawned = NightEvents.spawn(level, theme, mob, at, level.getRandom(), MobSpawnType.COMMAND, false);
			(spawned == null ? failed : placed).add(spawned == null ? mob.entity().toString() : describe(spawned));
		}

		return say(source, "placed " + placed.size() + " of " + (placed.size() + failed.size()) + " installed mobs of " + theme.id() + ": "
				+ String.join(", ", placed) + (failed.isEmpty() ? "" : "; couldn't place: " + String.join(", ", failed)), !placed.isEmpty());
	}

	/** "id (hp 40/40, base 20)". */
	static String describe(Mob mob) {
		double base = mob.getAttributeBaseValue(Attributes.MAX_HEALTH);
		return ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()) + " (hp " + Math.round(mob.getHealth()) + "/" + Math.round(mob.getMaxHealth())
				+ ", base " + Math.round(base) + ")";
	}

	private static int check(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		NightTable.Theme theme = NightEvents.active(level);

		if (theme == null || level.dimension() != Level.OVERWORLD) {
			return say(source, "no themed night on here (start one first)", false);
		}

		EntityType<?> standIn = null;

		for (EntityType<?> type : List.of(EntityType.ZOMBIE, EntityType.CREEPER, EntityType.WITCH, EntityType.SKELETON, EntityType.DROWNED,
				EntityType.ZOMBIE_VILLAGER)) {
			if (!theme.has(type)) {
				standIn = type;
				break;
			}
		}

		if (standIn == null) {
			return say(source, "every stand-in monster is one of " + theme.id() + "'s", false);
		}

		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(source.getPosition()).offset(0, 0, 6));
		NightTable.Climate here = NightTable.Climate.at(level, at);
		Map<String, Integer> came = new TreeMap<>();
		int tries = 40;
		int replaced = 0;
		int extras = 0;
		double health = 0.0;

		for (int i = 0; i < tries; i++) {
			if (!(standIn.create(level) instanceof Mob mob)) {
				break;
			}

			BlockPos spot = at.offset(i % 5 * 3 - 6, 0, i / 5 % 2 * 3);
			spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot);
			mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
			NightEvents.lastReplacement = null;
			NightEvents.lastExtra = null;
			NightEvents.forgetGroup();
			ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(spot), MobSpawnType.NATURAL, null, null);
			Mob instead = NightEvents.lastReplacement;

			if (mob.isSpawnCancelled() && instead != null) {
				replaced++;
				came.merge(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(instead.getType())), 1, Integer::sum);
				health += instead.getMaxHealth() / Math.max(1.0, instead.getAttributeBaseValue(Attributes.MAX_HEALTH));
				instead.discard();
			}

			if (NightEvents.lastExtra != null) {
				extras++;
				NightEvents.lastExtra.discard();
			}

			mob.discard();
		}

		List<String> kinds = came.entrySet().stream().map(entry -> entry.getKey() + " x" + entry.getValue()).toList();
		double expected = NightEvents.share(theme) * theme.strength(here);
		return say(source, "check: " + replaced + " of " + tries + " natural " + ForgeRegistries.ENTITY_TYPES.getKey(standIn) + " spawns came as "
				+ theme.id().getPath() + " mobs (expected about " + Math.round(expected * tries) + ", climate " + here.name().toLowerCase(Locale.ROOT)
				+ "), health x" + (replaced == 0 ? 0 : Math.round(health / replaced * 100.0) / 100.0) + ": " + String.join(", ", kinds) + "; " + extras
				+ " more came beside them (expected about " + Math.round(replaced * NightEvents.more()) + ")", true);
	}
}
