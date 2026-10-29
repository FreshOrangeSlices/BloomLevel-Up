package com.freshorangeslices.legionwarex.companion;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A keepsake holds a fallen companion: its saved data (level, experience, stats, settings, name, owner) and when it
 * may come back. Bringing it back makes the same companion again, at full health, where the keepsake is used.
 */
public final class Keepsakes {
	public static final String COMPANION = "Companion";
	public static final String READY_AT = "ReadyAt";
	public static final String OWNER = "Owner";
	public static final String NAME = "CompanionName";

	/**
	 * What the saved data loses: what dropped when it died (Migamigos drops the backpack; its gear drops or is lost as
	 * any mob's), so nothing is doubled, and what belongs to that one life (health, effects, place, its UUID).
	 */
	static final List<String> LEFT_OUT = List.of("Items", "ArmorItems", "HandItems", "ArmorDropChances", "HandDropChances",
			"Health", "AbsorptionAmount", "DeathTime", "HurtTime", "HurtByTimestamp", "Fire", "Air", "FallDistance", "TicksFrozen",
			"HasVisualFire", "ActiveEffects", "Brain", "Pos", "Motion", "Rotation", "OnGround", "PortalCooldown", "UUID", "Leash",
			"Passengers", "SleepingX", "SleepingY", "SleepingZ");

	public enum Revival { REVIVED, RESTING, EMPTY, FAILED }

	private Keepsakes() {
	}

	/** A keepsake of the companion, ready from the given game time. */
	public static ItemStack of(LivingEntity companion, UUID owner, long readyAt) {
		CompoundTag data = new CompoundTag();
		companion.saveWithoutId(data);
		LEFT_OUT.forEach(data::remove);
		data.putString("id", EntityType.getKey(companion.getType()).toString());

		ItemStack stack = new ItemStack(LegionWareX.COMPANION_KEEPSAKE.get());
		CompoundTag tag = stack.getOrCreateTag();
		tag.put(COMPANION, data);
		tag.putLong(READY_AT, readyAt);
		tag.putUUID(OWNER, owner);
		tag.putString(NAME, Component.Serializer.toJson(companion.getName()));
		return stack;
	}

	/** Brings the companion back at the spot (feet), facing the way given, unless the keepsake is still resting. */
	public static Revival revive(ServerLevel level, Vec3 at, float yaw, ItemStack stack) {
		CompoundTag tag = stack.getTag();

		if (tag == null || !tag.contains(COMPANION, Tag.TAG_COMPOUND)) {
			return Revival.EMPTY;
		}

		if (ticksLeft(level, stack) > 0) {
			return Revival.RESTING;
		}

		Optional<Entity> made = EntityType.create(tag.getCompound(COMPANION).copy(), level);

		if (made.isEmpty() || !(made.get() instanceof LivingEntity living)) {
			return Revival.FAILED;
		}

		living.moveTo(at.x, at.y, at.z, yaw, 0.0F);
		living.setYHeadRot(yaw);
		living.setHealth(living.getMaxHealth());
		return level.addFreshEntity(living) ? Revival.REVIVED : Revival.FAILED;
	}

	/** Ticks until the keepsake can bring its companion back (0 when it can). */
	public static long ticksLeft(Level level, ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag == null ? 0 : Math.max(0, tag.getLong(READY_AT) - level.getGameTime());
	}

	/** The companion's name, as it was (its own or a name tag). */
	public static Component name(ItemStack stack) {
		CompoundTag tag = stack.getTag();

		if (tag != null && tag.contains(NAME, Tag.TAG_STRING)) {
			try {
				Component name = Component.Serializer.fromJson(tag.getString(NAME));

				if (name != null) {
					return name;
				}
			} catch (RuntimeException e) {
				// Fall through to the plain word.
			}
		}

		return Component.translatable("item.legionwarex.companion_keepsake.someone");
	}

	/** Its level, as Migamigos keeps it, or 0. */
	public static int companionLevel(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		return tag == null ? 0 : tag.getCompound(COMPANION).getInt("AmigoLevel");
	}

	/** "12:30" for a span of ticks (minutes and seconds of play). */
	public static String time(long ticks) {
		long seconds = (ticks + 19) / 20;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
