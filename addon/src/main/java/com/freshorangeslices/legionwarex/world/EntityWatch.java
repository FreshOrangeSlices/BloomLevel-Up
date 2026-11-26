package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.night.NightMobs;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * {@code /legionwarex watch <entity>} (operators): says in the watcher's chat, and in the log as "LegionWareX watch:", when
 * an entity of that type appears, is stopped from appearing, dies or is removed, with why: the removal reason, how many
 * ticks it lived, its health, what last hurt it, and the calls that removed it, so a mod that deletes something shows by
 * name. Made for the Undying Tentacle (FreshOrangeSlices, 2026-11-21: "Nothing appears, audio jingle does play"); works
 * for any entity. When one appears it also says how it was spawned (its spawn type, the light on its spot, whether it's a
 * themed night's mob) and the calls that brought it, so a mod spawning mobs where they shouldn't shows by name
 * (FreshOrangeSlices, 2026-11-23: zombies in a well-lit base). A player watcher hears about those within 64 blocks of
 * them; the console hears about all of them. {@code watch off} stops every watch, {@code watch} lists them. Not saved: a
 * restart clears it.
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
	/** How each watched mob was spawned (from its finalize event), until it joins. */
	private static final Map<UUID, MobSpawnType> SPAWN_TYPES = new ConcurrentHashMap<>();
	/** A player watcher hears about an entity within this many blocks of them; the console hears about all. */
	private static final double NEAR = 64.0;

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
		context.getSource().sendSuccess(() -> Component.literal("Watching " + id + ": you'll be told when one appears, dies or is removed"
				+ (watcher.equals(CONSOLE) ? "" : " within " + (int) NEAR + " blocks of you") + ", with how it spawned"), true);
		return 1;
	}

	private static int off(CommandContext<CommandSourceStack> context) {
		int count = WATCHERS.size();
		WATCHERS.clear();
		JOINED.clear();
		SPAWN_TYPES.clear();
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

		MobSpawnType type = SPAWN_TYPES.remove(entity.getUUID());

		if (event.isCanceled()) {
			report(level.getServer(), id, entity, name(entity) + " was stopped from appearing at " + where(entity) + " (a mod cancelled it)");
			return;
		}

		JOINED.put(entity.getUUID(), level.getGameTime());
		report(level.getServer(), id, entity, name(entity) + " appeared at " + where(entity)
				+ (event.loadedFromDisk() ? " (loaded with its chunk)" : ": " + spawned(level, entity, type) + "; calls: " + calls()));
	}

	/** Keeps how a watched mob is being spawned, for its line as it appears (a spawn a mod turned away never appears). */
	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
		Mob mob = event.getEntity();

		if (!event.isSpawnCancelled() && watched(mob) != null) {
			SPAWN_TYPES.put(mob.getUUID(), event.getSpawnType());
		}
	}

	/** "spawned as natural, torchlight 0, light 4": its spawn type (none: added to the world straight), its spot's light, a themed night's. */
	private static String spawned(ServerLevel level, Entity entity, MobSpawnType type) {
		BlockPos pos = entity.blockPosition();
		return (type == null ? "no spawn type (added straight to the world)" : "spawned as " + type.name().toLowerCase(Locale.ROOT))
				+ ", torchlight " + level.getBrightness(LightLayer.BLOCK, pos) + ", light " + level.getMaxLocalRawBrightness(pos)
				+ (NightMobs.isNight(entity) ? ", a themed night's mob" + (NightMobs.isExtra(entity) ? " (an extra)" : "") : "");
	}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public static void onDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		ResourceLocation id = watched(entity);

		if (id != null && entity.level() instanceof ServerLevel level) {
			report(level.getServer(), id, entity, name(entity) + " died" + (event.isCanceled() ? " (a mod cancelled the death)" : "") + ": " + damage(event.getSource()));
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
		report(level.getServer(), id, entity, name(entity) + " was removed" + age + ": " + entity.getRemovalReason() + health + hurt + "; calls: " + calls());
	}

	/**
	 * While a watch is on, a player's use of an item from the watched entity's mod, to the log: started, nearly done,
	 * finished or let go (with the calls), so a summon that never comes shows whether the use itself was cut short (the
	 * Undying Tentacle: its Ghost Tentacles come only when the 2-second use finishes).
	 */
	private static void use(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent event, String what) {
		if (WATCHERS.isEmpty() || !(event.getEntity() instanceof net.minecraft.world.entity.player.Player player) || player.level().isClientSide()) {
			return;
		}

		ResourceLocation item = ForgeRegistries.ITEMS.getKey(event.getItem().getItem());

		if (item == null || WATCHERS.keySet().stream().noneMatch(id -> id.getNamespace().equals(item.getNamespace()))) {
			return;
		}

		LegionWareX.LOGGER.info("LegionWareX watch: {} {} {} ({} ticks left{})", player.getGameProfile().getName(), what, item, event.getDuration(),
				what.equals("let go of") ? "; calls: " + calls() : "");
	}

	@SubscribeEvent
	public static void onUseStart(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
		use(event, "started using");
	}

	@SubscribeEvent
	public static void onUseTick(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Tick event) {
		if (event.getDuration() == 1) {
			use(event, "is about to finish using");
		}
	}

	@SubscribeEvent
	public static void onUseStop(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Stop event) {
		use(event, "let go of");
	}

	@SubscribeEvent
	public static void onUseFinish(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
		use(event, "finished using");
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		WATCHERS.clear();
		LEFT.clear();
		JOINED.clear();
		SPAWN_TYPES.clear();
	}

	private static ResourceLocation watched(Entity entity) {
		if (WATCHERS.isEmpty() || entity.level().isClientSide()) {
			return null;
		}

		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		return id != null && WATCHERS.containsKey(id) ? id : null;
	}

	/** One line about this entity, to its watchers near it (and the console): nothing when no one who watches is near. */
	private static void report(MinecraftServer server, ResourceLocation id, Entity about, String text) {
		boolean console = false;
		List<ServerPlayer> near = new ArrayList<>();

		for (UUID watcher : WATCHERS.getOrDefault(id, Set.of())) {
			if (watcher.equals(CONSOLE)) {
				console = true;
				continue;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(watcher);

			if (player != null && player.level() == about.level() && player.distanceToSqr(about) <= NEAR * NEAR) {
				near.add(player);
			}
		}

		if (!console && near.isEmpty()) {
			return;
		}

		int left = LEFT.merge(id, -1, Integer::sum);

		if (left < 0) {
			return;
		}

		String line = left == 0 ? text + " (no more reports for " + id + " until you watch it again)" : text;
		LegionWareX.LOGGER.info("LegionWareX watch: {}", line);

		for (ServerPlayer player : near) {
			player.sendSystemMessage(Component.literal("[watch] " + line).withStyle(ChatFormatting.GRAY));
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
