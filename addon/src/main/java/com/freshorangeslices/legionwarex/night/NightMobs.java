package com.freshorangeslices.legionwarex.night;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * A themed night's mobs: tagged {@code legionwarex_night}, with the night's id and number in their persistent data and
 * the night's health (a modifier times their own, kept with them). Never made persistent: they despawn like any monster,
 * and what's left of them once their night is over goes as soon as no player is within 32 blocks (also as their chunk
 * loads). One a player has name-tagged (or otherwise made persistent) stays.
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
		return isNight(mob) && over && !mob.isPersistenceRequired() && !playerNear;
	}

	/** Leftovers of an ended night with no player near go; returns how many. */
	public static int sweep(ServerLevel level, NightCycle cycle) {
		List<Mob> gone = new ArrayList<>();

		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof Mob mob && isNight(mob) && leaves(mob, cycle, playerNear(level, mob))) {
				gone.add(mob);
			}
		}

		gone.forEach(Entity::discard);

		if (!gone.isEmpty()) {
			NightCycle.log(gone.size() + " mob(s) of an ended night went (no player within " + (int) NEAR + " blocks)");
		}

		return gone.size();
	}

	static boolean playerNear(ServerLevel level, Entity entity) {
		return level.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), NEAR, EntitySelector.NO_SPECTATORS) != null;
	}
}
