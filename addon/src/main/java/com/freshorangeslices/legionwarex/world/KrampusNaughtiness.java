package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Born in Chaos's naughtiness, its Krampus meter (0 to 100, kept per player in its capability): on its own, a player who
 * kills villagers, wandering traders or animals builds it up, hears warnings, meets two henchmen, and at 100 Krampus comes
 * for them (with Born in Chaos's numbers about the 11th adult villager). The pack's own (FreshOrangeSlices, 2026-10-31):
 * <ul>
 * <li>animals don't count: Born in Chaos's animal handler ({@value #ANIMALS}) is taken off the Forge bus
 * ({@code krampusAnimalNaughtiness} off);</li>
 * <li>Krampus comes at the 5th villager or trader ({@code krampusVillagerKills}): Born in Chaos's villager handler
 * ({@value #VILLAGERS}) is taken off the bus too, and this one fills the same meter by 100 / that many a kill (a baby
 * villager one and a half), with Born in Chaos's own warnings and henchmen on the way (two fifths: far warning; three
 * fifths: two henchmen; four fifths: close warning), Krampus at 100, after which the meter starts again from 0.</li>
 * </ul>
 * The meter stays Born in Chaos's, so its Evilometer reads it and its good deeds (taming, cookies, gingerbread) still
 * lower it; its game rule for the meter still turns all of it off. Both handlers are put back when their setting says
 * so. Reached by name, without compiling against Born in Chaos. Logged as "LegionWareX naughtiness: ...", which the pack
 * test reads; {@code /legionwarex naughtiness [set <value>]} reads or sets the meter of whoever runs it.
 */
public final class KrampusNaughtiness {
	static final String MOD = "born_in_chaos_v1";
	static final String ANIMALS = "net.mcreator.borninchaosv.procedures.KillinganimalsProcedure";
	static final String VILLAGERS = "net.mcreator.borninchaosv.procedures.KillingVillagersProcedure";
	static final String VARIABLES = "net.mcreator.borninchaosv.network.BornInChaosV1ModVariables";
	static final String RULES = "net.mcreator.borninchaosv.init.BornInChaosV1ModGameRules";
	static final double FULL = 100.0;
	private static final ResourceLocation KRAMPUS = new ResourceLocation(MOD, "krampus");
	private static final ResourceLocation HENCHMAN = new ResourceLocation(MOD, "krampus_henchman");
	private static final ResourceLocation NAUGHTY_CHILD = new ResourceLocation(MOD, "naughty_child");
	private static boolean animalsOff;
	private static boolean villagersOurs;

	private KrampusNaughtiness() {
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		if (!ModList.get().isLoaded(MOD)) {
			return;
		}

		boolean animals = AddonConfig.krampusAnimalNaughtiness();
		int kills = AddonConfig.krampusVillagerKills();
		String animalsDone = toggle(ANIMALS, !animals, animalsOff);
		animalsOff = animalsDone == null ? !animals : animalsOff;
		String villagersDone = toggle(VILLAGERS, kills > 0, villagersOurs);
		villagersOurs = villagersDone == null ? kills > 0 : villagersOurs;
		String problems = (animalsDone == null ? "" : "; " + animalsDone) + (villagersDone == null ? "" : "; " + villagersDone);
		LegionWareX.LOGGER.info("LegionWareX naughtiness: killing animals {} toward Krampus; villagers and traders do, Krampus at {}{}",
				animalsOff ? "doesn't count" : "counts", villagersOurs ? "the " + ordinal(kills) + " kill" : "Born in Chaos's own count", problems);
	}

	/**
	 * Takes a Born in Chaos handler off the Forge bus (off true) or puts it back, then checks the bus. Null when that worked,
	 * else what went wrong (for the log line).
	 */
	private static String toggle(String name, boolean off, boolean wasOff) {
		Class<?> handler;

		try {
			handler = Class.forName(name, false, KrampusNaughtiness.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError e) {
			return "couldn't find " + name + ": " + e;
		}

		try {
			if (off && !wasOff) {
				MinecraftForge.EVENT_BUS.unregister(handler);
			} else if (!off && wasOff) {
				MinecraftForge.EVENT_BUS.register(handler);
			}
		} catch (RuntimeException e) {
			return "couldn't change " + name + ": " + e;
		}

		Boolean listening = listening(handler);

		if (listening == null) {
			return null;
		}

		return listening == !off ? null : name.substring(name.lastIndexOf('.') + 1) + " is " + (listening ? "still" : "not") + " on the bus";
	}

	/** Whether a class's static listeners are on the Forge bus (null when the bus can't be read). */
	static Boolean listening(Class<?> handler) {
		try {
			Field field = MinecraftForge.EVENT_BUS.getClass().getDeclaredField("listeners");
			field.setAccessible(true);
			return ((Map<?, ?>) field.get(MinecraftForge.EVENT_BUS)).containsKey(handler);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	static String ordinal(int n) {
		int mod100 = n % 100;
		String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		};
		return n + suffix;
	}

	// The pack's villager count, in Born in Chaos's place.

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDeath(LivingDeathEvent event) {
		LivingEntity dead = event.getEntity();

		if (!villagersOurs || event.isCanceled() || !(dead instanceof Villager || dead instanceof WanderingTrader)
				|| !(event.getSource().getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level) || !ruleOn(level)) {
			return;
		}

		int kills = AddonConfig.krampusVillagerKills();

		if (kills <= 0) {
			return;
		}

		Double before = meter(player);

		if (before == null) {
			return;
		}

		double step = FULL / kills * (dead.isBaby() ? 1.5 : 1.0);
		double after = before + step;
		String what;

		if (after >= FULL - 1.0E-6) {
			Entity krampus = spawnNear(level, player, KRAMPUS);
			what = krampus == null ? "Krampus couldn't be placed" : "Krampus came";

			if (krampus != null) {
				sound(level, player, "krampus_alert_close", 0.8F);
				award(player, NAUGHTY_CHILD);
				after = 0.0;
			}
		} else if (crossed(before, after, 0.8)) {
			sound(level, player, "krampus_alert_close", 0.8F);
			what = "close warning";
		} else if (crossed(before, after, 0.6)) {
			sound(level, player, "krampus_alert", 0.7F);
			boolean one = spawnNear(level, player, HENCHMAN) != null;
			boolean two = spawnNear(level, player, HENCHMAN) != null;
			what = (one ? 1 : 0) + (two ? 1 : 0) + " henchmen";
		} else if (crossed(before, after, 0.4)) {
			sound(level, player, "krampus_alert_far", 0.7F);
			what = "far warning";
		} else {
			what = "no sign yet";
		}

		setMeter(player, after);
		LegionWareX.LOGGER.info("LegionWareX naughtiness: {} killed {}: {} -> {} of {}, {}", player.getScoreboardName(),
				ForgeRegistries.ENTITY_TYPES.getKey(dead.getType()), Math.round(before), Math.round(before + step), Math.round(FULL), what);
	}

	static boolean crossed(double before, double after, double share) {
		double mark = FULL * share - 1.0E-6;
		return before < mark && after >= mark;
	}

	/** Born in Chaos's game rule for the meter (on when it can't be read). */
	@SuppressWarnings("unchecked")
	static boolean ruleOn(ServerLevel level) {
		try {
			Object key = Class.forName(RULES, false, KrampusNaughtiness.class.getClassLoader()).getField("NAUGHTINESS_MECHANICS").get(null);
			return level.getGameRules().getBoolean((GameRules.Key<GameRules.BooleanValue>) key);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return true;
		}
	}

	/** Born in Chaos's player variables (its capability), or null. */
	private static Object variables(ServerPlayer player) {
		try {
			Capability<?> capability = (Capability<?>) Class.forName(VARIABLES, false, KrampusNaughtiness.class.getClassLoader())
					.getField("PLAYER_VARIABLES_CAPABILITY").get(null);
			return player.getCapability(capability, null).resolve().orElse(null);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	/** The player's naughtiness, or null when Born in Chaos's variables can't be reached. */
	public static Double meter(ServerPlayer player) {
		Object variables = variables(player);

		if (variables == null) {
			return null;
		}

		try {
			return variables.getClass().getField("naughtiness").getDouble(variables);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** Sets the player's naughtiness and syncs it to their client the way Born in Chaos does. Whether it took. */
	public static boolean setMeter(ServerPlayer player, double value) {
		Object variables = variables(player);

		if (variables == null) {
			return false;
		}

		try {
			variables.getClass().getField("naughtiness").setDouble(variables, value);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}

		try {
			Method sync = variables.getClass().getMethod("syncPlayerVariables", Entity.class);
			sync.invoke(variables, player);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// The value is set on the server; the client only shows it on the Evilometer.
		}

		return true;
	}

	private static void sound(ServerLevel level, ServerPlayer player, String name, float volume) {
		SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(MOD, name));

		if (sound != null) {
			level.playSound(null, player.blockPosition(), sound, SoundSource.NEUTRAL, volume, 1.0F);
		}
	}

	private static void award(ServerPlayer player, ResourceLocation id) {
		Advancement advancement = player.server.getAdvancements().getAdvancement(id);

		if (advancement != null) {
			AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);

			for (String criterion : progress.getRemainingCriteria()) {
				player.getAdvancements().award(advancement, criterion);
			}
		}
	}

	/** One of Born in Chaos's mobs a few blocks from the player, on solid ground with room to stand, set on them. */
	static Entity spawnNear(ServerLevel level, ServerPlayer player, ResourceLocation id) {
		EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);

		if (type == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
			return null;
		}

		RandomSource random = level.getRandom();
		BlockPos spot = null;

		for (int attempt = 0; attempt < 40 && spot == null; attempt++) {
			int dx = (2 + random.nextInt(4)) * (random.nextBoolean() ? 1 : -1);
			int dz = random.nextInt(9) - 4;
			BlockPos column = attempt % 2 == 0 ? player.blockPosition().offset(dx, 0, dz) : player.blockPosition().offset(dz, 0, dx);

			for (int dy = 2; dy >= -3 && spot == null; dy--) {
				BlockPos pos = column.above(dy);
				BlockPos below = pos.below();

				if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getFluidState(pos).isEmpty()
						&& level.noCollision(type.getAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
					spot = pos;
				}
			}
		}

		Entity entity = type.spawn(level, spot != null ? spot : player.blockPosition(), MobSpawnType.EVENT);

		if (entity instanceof Mob mob) {
			mob.setTarget(player);
		}

		return entity;
	}

	/** {@code /legionwarex naughtiness [set <value>]}: the meter of whoever runs it (a player), read or set. */
	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("naughtiness")
				.executes(KrampusNaughtiness::show)
				.then(Commands.literal("set").then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1000.0))
						.executes(context -> set(context, DoubleArgumentType.getDouble(context, "value")))));
	}

	private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Double value = meter(player);
		String text = value == null ? "Born in Chaos's meter isn't here"
				: player.getScoreboardName() + " " + Math.round(value) + " of " + Math.round(FULL) + (villagersOurs
						? ", Krampus at the " + ordinal(AddonConfig.krampusVillagerKills()) + " villager" : ", Born in Chaos's own count");
		return say(context.getSource(), text, value != null);
	}

	private static int set(CommandContext<CommandSourceStack> context, double value) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		boolean ok = setMeter(player, value);
		return say(context.getSource(), ok ? player.getScoreboardName() + " set to " + Math.round(value) : "Born in Chaos's meter isn't here", ok);
	}

	private static int say(CommandSourceStack source, String text, boolean ok) {
		LegionWareX.LOGGER.info("LegionWareX naughtiness: {}", text);

		if (ok) {
			source.sendSuccess(() -> Component.literal("LegionWareX naughtiness: " + text), true);
		} else {
			source.sendFailure(Component.literal("LegionWareX naughtiness: " + text));
		}

		return ok ? 1 : 0;
	}
}
