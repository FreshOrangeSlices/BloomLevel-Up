package com.freshorangeslices.bloompets;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Fetch, the plugin's way (docs/design/PETS.md, Phase 5c; FreshOrangeSlices, 2026-11-05). Right-click a Ball (one comes
 * with every new Bloom pet) and it's thrown for the pet in your party: every Bloom pet plays but the Iron Golem and the
 * Vex, and companions don't. The pet runs (or flies) to it, carries it back on its head and drops it at your feet, and
 * the ball is back in your inventory (also when it can't get there, after 25 seconds, or if anything goes wrong: a ball
 * is never lost). Each fetch: 22 XP and +8 mood, at most every 15 seconds.
 */
public final class Fetch {
	public static final double XP = 22.0;
	public static final double MOOD = 8.0;
	private static final long REWARD_EVERY = 15 * 20;
	private static final long TIMEOUT = 25 * 20;
	private static final double SPEED = 1.35;
	private static final double REACH = 48.0;
	/** Marks a thrown ball, so one left over after a restart turns back into a ball anyone can pick up. */
	private static final String THROWN = "bloompets.thrownBall";
	private static final Map<UUID, Game> GAMES = new HashMap<>();

	private Fetch() {
	}

	private static final class Game {
		final Mob pet;
		final ItemEntity ball;
		final UUID owner;
		ServerPlayer player;
		boolean returning;
		final long started;

		Game(Mob pet, ItemEntity ball, ServerPlayer player, long started) {
			this.pet = pet;
			this.ball = ball;
			this.owner = player.getUUID();
			this.player = player;
			this.started = started;
		}
	}

	/** Whether this pet plays fetch: a Bloom pet, but not the Iron Golem or the Vex. */
	public static boolean plays(Entity pet) {
		return Pets.kind(pet) == Pets.Kind.BLOOM && pet.getType() != EntityType.IRON_GOLEM && pet.getType() != EntityType.VEX;
	}

	/** Whether the pet is out after a ball right now. */
	public static boolean playing(Entity pet) {
		return GAMES.containsKey(pet.getUUID());
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** Throws one ball from {@code held} for the player's party pet; false (nothing thrown) if nobody can play. */
	public static boolean throwBall(ServerPlayer player, ItemStack held) {
		PetRegistry.Record party = PetRegistry.get(player.server).partyPet(player.getUUID());
		Entity found = party == null ? null : Pets.loaded(player.server, party.pet);

		if (!(found instanceof Mob pet) || pet.level() != player.level() || pet.distanceToSqr(player) > REACH * REACH) {
			player.displayClientMessage(Component.translatable("bloompets.fetch.none").withStyle(ChatFormatting.GRAY), true);
			return false;
		}

		if (!plays(pet)) {
			player.displayClientMessage(Component.translatable("bloompets.fetch.wont", pet.getName()).withStyle(ChatFormatting.GRAY), true);
			return false;
		}

		if (playing(pet) || Fainting.fainted(pet) || pet.isVehicle() || pet instanceof TamableAnimal tamable && tamable.isOrderedToSit()) {
			return false;
		}

		Vec3 eye = player.getEyePosition();
		ItemEntity ball = new ItemEntity(player.level(), eye.x, eye.y - 0.1, eye.z, held.copyWithCount(1));
		ball.setNeverPickUp();
		ball.setUnlimitedLifetime();
		ball.setThrower(player.getUUID());
		ball.getPersistentData().putBoolean(THROWN, true);
		ball.setDeltaMovement(player.getLookAngle().scale(1.1).add(0.0, 0.15, 0.0));
		player.level().addFreshEntity(ball);
		GAMES.put(pet.getUUID(), new Game(pet, ball, player, now(player.server)));
		player.level().playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.7F, 0.8F);
		return true;
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || GAMES.isEmpty()) {
			return;
		}

		for (Game game : new ArrayList<>(GAMES.values())) {
			tick(event.getServer(), game);
		}
	}

	private static void tick(MinecraftServer server, Game game) {
		Mob pet = game.pet;
		ItemEntity ball = game.ball;
		ServerPlayer owner = owner(server, game);

		if (owner == null || !pet.isAlive() || pet.isRemoved() || !ball.isAlive() || ball.level() != pet.level() || owner.level() != pet.level()
				|| now(server) - game.started > TIMEOUT || Fainting.fainted(pet)) {
			end(server, game, false);
			return;
		}

		if (!game.returning) {
			// It has the ball once it's at it (not the moment it's thrown: the throw starts at its owner's feet).
			if (ball.distanceToSqr(pet) < 1.6 * 1.6 && now(server) - game.started > 12) {
				game.returning = true;
				ball.setNoGravity(true);
				pet.level().playSound(null, pet.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 1.4F);
			} else {
				return;
			}
		}

		// Carried home on its head.
		ball.setPos(pet.getX(), pet.getY() + pet.getBbHeight() + 0.1, pet.getZ());
		ball.setDeltaMovement(Vec3.ZERO);

		if (pet.distanceToSqr(owner) < 2.4 * 2.4) {
			end(server, game, true);
		}
	}

	private static ServerPlayer owner(MinecraftServer server, Game game) {
		if (game.player != null && !game.player.isRemoved()) {
			return game.player;
		}

		game.player = server.getPlayerList().getPlayer(game.owner);
		return game.player;
	}

	/** The game's over: the ball back in its owner's inventory, and if the pet brought it, its reward. Public for the game tests. */
	public static void finish(Mob pet, boolean brought) {
		Game game = GAMES.get(pet.getUUID());

		if (game != null && pet.getServer() != null) {
			end(pet.getServer(), game, brought);
		}
	}

	private static void end(MinecraftServer server, Game game, boolean brought) {
		GAMES.remove(game.pet.getUUID());
		ItemStack stack = game.ball.getItem().copy();
		game.ball.discard();
		game.pet.getNavigation().stop();
		ServerPlayer owner = owner(server, game);

		if (owner != null) {
			if (!owner.getInventory().add(stack)) {
				owner.drop(stack, false);
			}
		} else if (game.pet.level() instanceof ServerLevel level) {
			// Its owner's gone: the ball waits on the ground where the pet is.
			level.addFreshEntity(new ItemEntity(level, game.pet.getX(), game.pet.getY() + 0.3, game.pet.getZ(), stack));
		}

		if (!brought || owner == null) {
			return;
		}

		Mob pet = game.pet;
		ServerLevel level = (ServerLevel) pet.level();
		level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + pet.getBbHeight() + 0.3, pet.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
		level.playSound(null, pet.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 0.7F, 1.2F);
		PetAdvancements.award(owner, PetAdvancements.FETCH);
		CompoundTag data = Pets.data(pet);
		long now = now(server);
		boolean reward = !data.contains("fetchAt") || now - data.getLong("fetchAt") >= REWARD_EVERY;

		if (reward) {
			data.putLong("fetchAt", now);
			PetMood.played(owner, pet);
		}

		if (reward && PetLevels.level(pet) < PetLevels.MAX) {
			PetLevels.addXp(pet, XP);
			owner.displayClientMessage(Component.translatable("bloompets.fetch.back.xp", pet.getName(), (int) XP).withStyle(ChatFormatting.LIGHT_PURPLE), true);
		} else {
			owner.displayClientMessage(Component.translatable("bloompets.fetch.back", pet.getName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
		}
	}

	/** A thrown ball that outlived its game (a restart): an ordinary ball again, for anyone to pick up. */
	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item) || !item.getPersistentData().getBoolean(THROWN)) {
			return;
		}

		for (Game game : GAMES.values()) {
			if (game.ball == item) {
				return;
			}
		}

		item.getPersistentData().remove(THROWN);
		item.setDefaultPickUpDelay();
		item.setNoGravity(false);
	}

	/**
	 * The pet's legs while it plays: to the ball, then home to its owner. Ahead of every other goal that moves it (following
	 * its owner, wandering, its post), so they don't pull it away mid-game.
	 */
	public static final class FetchGoal extends Goal {
		private final Mob pet;
		private int repath;

		public FetchGoal(Mob pet) {
			this.pet = pet;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return playing(pet);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			pet.getNavigation().stop();
		}

		@Override
		public void tick() {
			Game game = GAMES.get(pet.getUUID());

			if (game == null || pet.getServer() == null) {
				return;
			}

			Entity goal = game.returning ? owner(pet.getServer(), game) : game.ball;

			if (goal == null) {
				return;
			}

			pet.getLookControl().setLookAt(goal, 30.0F, 30.0F);

			if (--repath <= 0) {
				repath = 5;
				pet.getNavigation().moveTo(goal.getX(), goal.getY(), goal.getZ(), SPEED);
			}
		}
	}

	/** Every ball in a game right now (the game tests). */
	public static List<ItemEntity> balls() {
		List<ItemEntity> balls = new ArrayList<>();
		GAMES.values().forEach(game -> balls.add(game.ball));
		return balls;
	}

	/** A pet the player just got: a Ball for them (FreshOrangeSlices: one with every new Bloom pet). */
	public static void giveBall(ServerPlayer player) {
		ItemStack ball = new ItemStack(BloomPets.BALL.get());

		if (!player.getInventory().add(ball)) {
			player.drop(ball, false);
		}

		player.displayClientMessage(Component.translatable("bloompets.ball.got").withStyle(ChatFormatting.LIGHT_PURPLE), true);
	}
}
