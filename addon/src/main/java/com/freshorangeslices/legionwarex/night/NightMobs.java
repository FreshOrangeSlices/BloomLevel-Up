package com.freshorangeslices.legionwarex.night;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.freshorangeslices.legionwarex.Despawning;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * them once their night is over goes as soon as no player is within 32 blocks (also as their chunk loads); since 0.8.0
 * also by day with players near, in a puff of smoke, within the first minute or so of daylight (FreshOrangeSlices,
 * 2026-11-24: after The Damned its flyers and demons stayed and made the next days harder). The extras (one more beside
 * a night's mob, {@code themedNightMoreMobs}) are made persistent so they don't count toward the game's monster cap (or
 * the night would only have as many as any other), and despawn by this class's rule instead, as vanilla does: at once
 * with no player within 128 blocks, now and then beyond 32. Only what a player kept stays: a name tag, a leash, a rider,
 * or gear it picked up. A mob its own mod keeps for good (Born in Chaos's Scarlet Persecutor and Infernal Spirit,
 * Undead Unleashed's Flamebreather) goes like the rest; before 0.8.0 any persistent mob stayed.
 */
public final class NightMobs {
	public static final String TAG = "legionwarex_night";
	private static final String DATA = "legionwarex.night";
	private static final UUID HEALTH_ID = UUID.fromString("5b2e8d14-7c3a-4f69-a1d0-9e4c6b8f2a37");
	/** No player this close: a leftover from an earlier night goes. */
	public static final double NEAR = 32.0;
	/** Game tests: a leftover by day always fades (no roll). */
	public static boolean testFade;

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

	/** Whether a player has kept it: a name tag, a leash, a rider, or gear it picked up (not its own mod's persistence). */
	public static boolean kept(Mob mob) {
		return Despawning.keptByPlayer(mob) || Despawning.pickedUpGear(mob);
	}

	/** Whether it's of a night that is over (none on, or a later one). */
	public static boolean over(Mob mob, NightCycle cycle) {
		return isNight(mob) && (cycle.active() == null || nightOf(mob) != cycle.count());
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
		return !playerNear && over(mob, cycle) && !kept(mob);
	}

	/** Whether a leftover with a player near fades this sweep: by day, about 1 sweep in 4 (all gone within a minute or so). */
	public static boolean fades(Mob mob, NightCycle cycle, boolean daylight, RandomSource random) {
		return daylight && over(mob, cycle) && (testFade || random.nextInt(4) == 0) && !kept(mob);
	}

	/** Whether an extra of tonight's night despawns now, as vanilla's monsters do (checked every 5 seconds: about 1 in 8). */
	public static boolean despawns(Mob mob, double nearestPlayer, RandomSource random) {
		if (!isExtra(mob)) {
			return false;
		}

		return (nearestPlayer > 128.0 || nearestPlayer > NEAR && random.nextInt(8) == 0) && !kept(mob);
	}

	/** Leftovers of an ended night go (no player near, or by day in a puff of smoke), and extras despawn; returns how many. */
	public static int sweep(ServerLevel level, NightCycle cycle) {
		List<Mob> gone = new ArrayList<>();
		List<Mob> faded = new ArrayList<>();
		boolean daylight = level.isDay();

		for (Entity entity : level.getAllEntities()) {
			if (!(entity instanceof Mob mob) || !isNight(mob)) {
				continue;
			}

			boolean near = playerNear(level, mob);

			if (leaves(mob, cycle, near) || isExtra(mob) && despawns(mob, nearestPlayer(level, mob), level.getRandom())) {
				gone.add(mob);
			} else if (near && fades(mob, cycle, daylight, level.getRandom())) {
				faded.add(mob);
			}
		}

		gone.forEach(Entity::discard);
		faded.forEach(mob -> {
			puff(level, mob);
			mob.discard();
		});

		if (!gone.isEmpty()) {
			long ended = gone.stream().filter(mob -> over(mob, cycle)).count();

			if (ended > 0) {
				NightCycle.log(ended + " mob(s) of an ended night went (no player within " + (int) NEAR + " blocks)");
			}
		}

		if (!faded.isEmpty()) {
			NightCycle.log(faded.size() + " mob(s) of an ended night faded in the daylight");
		}

		return gone.size() + faded.size();
	}

	/** A puff of smoke where a leftover fades. */
	static void puff(ServerLevel level, Mob mob) {
		double width = mob.getBbWidth() / 2.0;
		double height = mob.getBbHeight() / 2.0;
		level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + height, mob.getZ(), 20, width, height, width, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + height, mob.getZ(), 8, width, height, width, 0.01);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.6F, 1.2F);
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
