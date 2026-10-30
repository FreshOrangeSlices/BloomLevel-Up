package com.freshorangeslices.legionwarex.band;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Band pirates are quicker than the Kraken ship's crew (FreshOrangeSlices' playtest: they walked and swung as if made to
 * be killed). They walk 40% faster: a pirate rook (0.22) a bit quicker than a zombie, a crossbow pirate (0.25) as fast
 * as a pillager. And a rook waits 1.25 s between swings instead of 2 s.
 * <p>
 * Bosses'Rise times a rook's swing with two synced counters (its public ATTACK_COOLDOWN and ATTACK_ANIMTIME, read by
 * name): while no swing is playing and its target is within 16 blocks the cooldown counts up one a tick, and at 40, with
 * the target within 1.5 blocks, the 20-tick swing starts (the hit lands 16 ticks in). The add-on counts the cooldown up
 * faster (3 extra steps in every 5 ticks: 40 steps in 25 ticks), so the swing itself, and its animation, stay as they are.
 */
public final class PirateTuning {
	/** The speed bonus, as a share of the base speed. */
	public static final double SPEED_BONUS = 0.4;
	/** Bosses'Rise's cooldown before a rook may swing, in steps (one a tick by itself). */
	public static final int COOLDOWN_STEPS = 40;
	/** The wait the add-on brings that down to, in ticks. */
	public static final int COOLDOWN_TICKS = 25;
	/** Extra steps in every 5 ticks: (40 - 25) / 25 = 3 in 5. */
	private static final int EXTRA_IN_5 = 3;
	private static final UUID SPEED_ID = UUID.fromString("5d0f6a52-7f1e-4c39-9a51-3b8e2c0a4b11");
	private static final Map<Class<?>, Counters> COUNTERS = new ConcurrentHashMap<>();

	private PirateTuning() {
	}

	/** A band pirate's walking speed, 40% up for good (saved with the mob). */
	public static void quicken(Mob mob) {
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed != null && speed.getModifier(SPEED_ID) == null) {
			speed.addPermanentModifier(new AttributeModifier(SPEED_ID, "LegionWareX band pirate", SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
		}
	}

	/** Every tick, before the mob's own: a rook's swing cooldown counts up faster. Nothing for a mob without the counters. */
	public static void tick(Mob mob) {
		Counters counters = COUNTERS.computeIfAbsent(mob.getClass(), Counters::of);

		if (counters == Counters.NONE || mob.tickCount % 5 >= EXTRA_IN_5) {
			return;
		}

		SynchedEntityData data = mob.getEntityData();
		LivingEntity target = mob.getTarget();

		// Only while the rook's own counter is counting: no swing playing, a target within 16 blocks, not yet at 40.
		if (data.get(counters.swing) > 0 || target == null || target.distanceTo(mob) >= 16.0F) {
			return;
		}

		int cooldown = data.get(counters.cooldown);

		if (cooldown < COOLDOWN_STEPS) {
			data.set(counters.cooldown, cooldown + 1);
		}
	}

	/** Whether a mob has a rook's swing counters (Bosses'Rise's pirate rook). */
	public static boolean hasSwingCounters(Mob mob) {
		return COUNTERS.computeIfAbsent(mob.getClass(), Counters::of) != Counters.NONE;
	}

	/** The rook's swing cooldown now, or -1 for a mob without one (the game tests). */
	public static int cooldown(Mob mob) {
		Counters counters = COUNTERS.computeIfAbsent(mob.getClass(), Counters::of);
		return counters == Counters.NONE ? -1 : mob.getEntityData().get(counters.cooldown);
	}

	private record Counters(EntityDataAccessor<Integer> cooldown, EntityDataAccessor<Integer> swing) {
		static final Counters NONE = new Counters(null, null);

		@SuppressWarnings("unchecked")
		static Counters of(Class<?> type) {
			try {
				Field cooldown = type.getField("ATTACK_COOLDOWN");
				Field swing = type.getField("ATTACK_ANIMTIME");

				if (Modifier.isStatic(cooldown.getModifiers()) && Modifier.isStatic(swing.getModifiers())
						&& cooldown.get(null) instanceof EntityDataAccessor<?> c && swing.get(null) instanceof EntityDataAccessor<?> s) {
					return new Counters((EntityDataAccessor<Integer>) c, (EntityDataAccessor<Integer>) s);
				}
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// Not a rook (or a Bosses'Rise that renamed them): left as it is.
			}

			return NONE;
		}
	}
}
