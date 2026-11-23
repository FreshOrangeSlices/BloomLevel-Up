package com.freshorangeslices.legionwarex.night;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * A themed night's mobs: tagged {@code legionwarex_night}, with the night's id and number in their persistent data and
 * the night's health (a modifier times their own, kept with them). They despawn like any monster, and what's left of
 * them once their night is over goes as soon as no player is within 32 blocks (also as their chunk loads). The extras
 * (one more beside a night's mob, {@code themedNightMoreMobs}) are made persistent so they don't count toward the game's
 * monster cap (or the night would only have as many as any other), and despawn by this class's rule instead, as vanilla
 * does: at once with no player within 128 blocks, now and then beyond 32. One a player has name-tagged stays, as does a
 * mob (not an extra) something else made persistent, such as one that picked up a player's gear.
 */
public final class NightMobs {
	public static final String TAG = "legionwarex_night";
	private static final String DATA = "legionwarex.night";
	private static final UUID HEALTH_ID = UUID.fromString("5b2e8d14-7c3a-4f69-a1d0-9e4c6b8f2a37");
	/** No player this close: a leftover from an earlier night goes. */
	public static final double NEAR = 32.0;

	private NightMobs() {
	}

	public static boolean isNight(Entity entity) {
		return entity.getTags().contains(TAG);
	}

	/** The number of the night it came on (0 for none). */
	public static int nightOf(Entity entity) {
		return entity.getPersistentData().getCompound(DATA).getInt("n");
	}

	/** Whether it's an extra (persistent for the monster cap's sake, despawned by {@link #sweep}). */
	public static boolean isExtra(Entity entity) {
		return entity.getPersistentData().getCompound(DATA).getBoolean("extra");
	}

	/** Marks a night's mob as an extra: persistent, so the monster cap doesn't count it. */
	public static void markExtra(Mob mob) {
		mob.getPersistentData().getCompound(DATA).putBoolean("extra", true);
		mob.setPersistenceRequired();
	}

	/** Whether a player has kept it: a name tag, or (not an extra) made persistent by something else. */
	public static boolean kept(Mob mob) {
		return mob.hasCustomName() || mob.isPersistenceRequired() && !isExtra(mob);
	}

	/** Marks a mob as one of this night's, its health raised as it joins ({@link #boost}). */
	public static void mark(Mob mob, NightTable.Theme theme, int night) {
		mob.addTag(TAG);
		CompoundTag data = new CompoundTag();
		data.putString("night", theme.id().toString());
		data.putInt("n", night);
		data.putDouble("health", theme.health());
		mob.getPersistentData().put(DATA, data);
	}

	/** Gives a marked mob its night's health (once; filled up). */
	public static void boost(Mob mob) {
		CompoundTag data = mob.getPersistentData().getCompound(DATA);
		double health = data.getDouble("health");
		AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);

		if (health <= 1.0 || max == null || max.getModifier(HEALTH_ID) != null) {
			return;
		}

		max.addPermanentModifier(new AttributeModifier(HEALTH_ID, "LegionWareX themed night", health - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
		mob.setHealth(mob.getMaxHealth());
	}

	/** Whether a night mob goes: its night is over (none on, or a later one), it isn't kept, and no player is near. */
	public static boolean leaves(Mob mob, NightCycle cycle, boolean playerNear) {
		boolean over = cycle.active() == null || nightOf(mob) != cycle.count();
		return isNight(mob) && over && !kept(mob) && !playerNear;
	}

	/** Whether an extra of tonight's night despawns now, as vanilla's monsters do (checked every 5 seconds: about 1 in 8). */
	public static boolean despawns(Mob mob, double nearestPlayer, RandomSource random) {
		if (!isExtra(mob) || kept(mob)) {
			return false;
		}

		return nearestPlayer > 128.0 || nearestPlayer > NEAR && random.nextInt(8) == 0;
	}

	/** Leftovers of an ended night with no player near go; returns how many. */
	public static int sweep(ServerLevel level, NightCycle cycle) {
		List<Mob> gone = new ArrayList<>();

		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof Mob mob && isNight(mob) && (leaves(mob, cycle, playerNear(level, mob))
					|| isExtra(mob) && despawns(mob, nearestPlayer(level, mob), level.getRandom()))) {
				gone.add(mob);
			}
		}

		gone.forEach(Entity::discard);

		if (!gone.isEmpty()) {
			long ended = gone.stream().filter(mob -> !isExtra(mob) || cycle.active() == null || nightOf(mob) != cycle.count()).count();

			if (ended > 0) {
				NightCycle.log(ended + " mob(s) of an ended night went (no player within " + (int) NEAR + " blocks)");
			}
		}

		return gone.size();
	}

	/** How far the nearest player is (not spectators), or infinity. */
	static double nearestPlayer(ServerLevel level, Entity entity) {
		var player = level.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), -1.0, EntitySelector.NO_SPECTATORS);
		return player == null ? Double.POSITIVE_INFINITY : player.distanceTo(entity);
	}

	static boolean playerNear(ServerLevel level, Entity entity) {
		return level.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), NEAR, EntitySelector.NO_SPECTATORS) != null;
	}
}
