package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code /legionwarex watch <entity>} (operators): says in the watcher's chat, and in the log as "LegionWareX watch:", when
 * an entity of that type appears, is stopped from appearing, dies or is removed, with why: the removal reason, how many
 * ticks it lived, its health, what last hurt it, and the calls that removed it, so a mod that deletes something shows by
 * name. Made for the Undying Tentacle (FreshOrangeSlices, 2026-11-21: "Nothing appears, audio jingle does play"); works
 * for any entity. {@code watch off} stops every watch, {@code watch} lists them. Not saved: a restart clears it.
 */
public final class EntityWatch {
	/** The console (or a command block) as a watcher: it reads the log. */
	private static final UUID CONSOLE = new UUID(0L, 0L);
	/** Reports per watched type until it's turned on again, so a common mob can't flood the chat. */
	private static final int REPORTS = 100;
	private static final StackWalker WALKER = StackWalker.getInstance();
	private static final Map<ResourceLocation, Set<UUID>> WATCHERS = new ConcurrentHashMap<>();
	private static final Map<ResourceLocation, Integer> LEFT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> JOINED = new HashMap<>();

	private EntityWatch() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("watch").executes(EntityWatch::list)
				.then(Commands.literal("off").executes(EntityWatch::off))
				.then(Commands.argument("entity", ResourceLocationArgument.id())
						.suggests((context, builder) -> SharedSuggestionProvider.suggestResource(ForgeRegistries.ENTITY_TYPES.getKeys(), builder))
						.executes(EntityWatch::toggle));
	}

	private static int toggle(CommandContext<CommandSourceStack> context) {
		ResourceLocation id = ResourceLocationArgument.getId(context, "entity");

		if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
			context.getSource().sendFailure(Component.literal("No entity " + id));
			return 0;
		}

		UUID watcher = watcher(context.getSource());
		Set<UUID> set = WATCHERS.computeIfAbsent(id, key -> ConcurrentHashMap.newKeySet());

		if (set.remove(watcher)) {
			if (set.isEmpty()) {
				WATCHERS.remove(id);
			}

			context.getSource().sendSuccess(() -> Component.literal("Stopped watching " + id), true);
			return 1;
		}

		set.add(watcher);
		LEFT.put(id, REPORTS);
		context.getSource().sendSuccess(() -> Component.literal("Watching " + id + ": you'll be told when one appears, dies or is removed"), true);
		return 1;
	}

	private static int off(CommandContext<CommandSourceStack> context) {
		int count = WATCHERS.size();
		WATCHERS.clear();
		JOINED.clear();
		context.getSource().sendSuccess(() -> Component.literal("Stopped " + count + " watch" + (count == 1 ? "" : "es")), true);
		return count;
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		String which = WATCHERS.isEmpty() ? "nothing" : String.join(", ", WATCHERS.keySet().stream().map(ResourceLocation::toString).sorted().toList());
		context.getSource().sendSuccess(() -> Component.literal("Watching " + which), false);
		return WATCHERS.size();
	}

	private static UUID watcher(CommandSourceStack source) {
		return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : CONSOLE;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public static void onJoin(EntityJoinLevelEvent event) {
		Entity entity = event.getEntity();
		ResourceLocation id = watched(entity);

		if (id == null || !(event.getLevel() instanceof ServerLevel level)) {
			return;
		}

		if (event.isCanceled()) {
			report(level.getServer(), id, name(entity) + " was stopped from appearing at " + where(entity) + " (a mod cancelled it)");
			return;
		}

		JOINED.put(entity.getUUID(), level.getGameTime());
		report(level.getServer(), id, name(entity) + " appeared at " + where(entity) + (event.loadedFromDisk() ? " (loaded with its chunk)" : ""));
	}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public static void onDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		ResourceLocation id = watched(entity);

		if (id != null && entity.level() instanceof ServerLevel level) {
			report(level.getServer(), id, name(entity) + " died" + (event.isCanceled() ? " (a mod cancelled the death)" : "") + ": " + damage(event.getSource()));
		}
	}

	@SubscribeEvent
	public static void onLeave(EntityLeaveLevelEvent event) {
		Entity entity = event.getEntity();
		ResourceLocation id = watched(entity);

		if (id == null || !(event.getLevel() instanceof ServerLevel level)) {
			return;
		}

		Long joined = JOINED.remove(entity.getUUID());
		String age = joined == null ? "" : " after " + (level.getGameTime() - joined) + " ticks";
		String health = entity instanceof LivingEntity living ? ", health " + Math.round(living.getHealth() * 10) / 10.0 : "";
		String hurt = entity instanceof LivingEntity living && living.getLastDamageSource() != null ? ", last hurt by " + damage(living.getLastDamageSource()) : "";
		report(level.getServer(), id, name(entity) + " was removed" + age + ": " + entity.getRemovalReason() + health + hurt + "; calls: " + calls());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		WATCHERS.clear();
		LEFT.clear();
		JOINED.clear();
	}

	private static ResourceLocation watched(Entity entity) {
		if (WATCHERS.isEmpty() || entity.level().isClientSide()) {
			return null;
		}

		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		return id != null && WATCHERS.containsKey(id) ? id : null;
	}

	private static void report(MinecraftServer server, ResourceLocation id, String text) {
		int left = LEFT.merge(id, -1, Integer::sum);

		if (left < 0) {
			return;
		}

		String line = left == 0 ? text + " (no more reports for " + id + " until you watch it again)" : text;
		LegionWareX.LOGGER.info("LegionWareX watch: {}", line);

		for (UUID watcher : WATCHERS.getOrDefault(id, Set.of())) {
			ServerPlayer player = watcher.equals(CONSOLE) ? null : server.getPlayerList().getPlayer(watcher);

			if (player != null) {
				player.sendSystemMessage(Component.literal("[watch] " + line).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private static String name(Entity entity) {
		return entity.getName().getString() + " #" + entity.getId();
	}

	private static String where(Entity entity) {
		return Math.round(entity.getX()) + " " + Math.round(entity.getY()) + " " + Math.round(entity.getZ()) + " in " + entity.level().dimension().location();
	}

	private static String damage(DamageSource source) {
		Entity by = source.getEntity();
		return source.getMsgId() + (by == null ? "" : " from " + by.getName().getString());
	}

	/** Who removed it: the calls that led here, past the event bus, the add-on and the game's entity bookkeeping. */
	private static String calls() {
		List<String> calls = WALKER.walk(frames -> frames.skip(1)
				.map(frame -> frame.getClassName() + "." + frame.getMethodName())
				.filter(call -> !call.startsWith("net.minecraftforge.eventbus.") && !call.startsWith("java.") && !call.startsWith("jdk.")
						&& !call.startsWith(EntityWatch.class.getName()) && !call.startsWith("net.minecraftforge.event.ForgeEventFactory")
						&& !call.startsWith("net.minecraft.world.level.entity.") && !call.startsWith("net.minecraft.server.level.ServerLevel$EntityCallbacks"))
				.limit(10).toList());
		return calls.isEmpty() ? "?" : String.join(" < ", calls);
	}
}
