package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.companion.Companions;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Monsters tougher the farther from spawn (FreshOrangeSlices, 2026-11-25: "health should ideally increase via distance",
 * "from 4k blocks to 6k", bosses half). Health only, never damage: normal within {@code farStart} (1000 blocks), then
 * +{@code farHealthPerStep} (50%) of its own per {@code farStep} (1000), up to x3.5 at 6000; a boss (Bloom's
 * {@code bloomlevelup:bosses}) at {@code farBossShare} of that (x2.25). Set once, where the monster first enters the
 * world (a tick later, so an owner set just after spawning counts), as a modifier on its max health that stays with it;
 * one at full health stays at full. No existing mod did this on Forge 1.20.1 (Scaling Mob Difficulty scales by time,
 * Distant Dangers is NeoForge 1.21.1 only), so the add-on does.
 */
public final class FarHealth {
	public static final UUID ID = UUID.fromString("3b8e4c21-7d5a-4f60-9a1e-b2c94d7f0e85");
	public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("bloomlevelup", "bosses"));
	private static final List<WeakReference<Mob>> PENDING = new ArrayList<>();

	private FarHealth() {
	}

	/** The health multiplier at this distance. */
	public static double multiplier(double distance, boolean boss) {
		double share = boss ? AddonConfig.farBossShare() : 1.0;
		return 1.0 + FarFromSpawn.steps(distance) * AddonConfig.farHealthPerStep() * share;
	}

	public static boolean boss(Mob mob) {
		return mob.getType().is(BOSSES);
	}

	/** Anything a player owns: a tamed or summoned monster, a Bloom pet, a companion. */
	static boolean owned(Mob mob) {
		return (mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null)
				|| mob.getPersistentData().contains("bloompets.owner")
				|| Companions.owner(mob) != null;
	}

	/** Gives a monster its distance health; false if it has it already or needs none. */
	public static boolean apply(Mob mob, double distance) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);

		if (health == null || health.getModifier(ID) != null) {
			return false;
		}

		double multiplier = multiplier(distance, boss(mob));

		if (multiplier <= 1.0001) {
			return false;
		}

		boolean full = mob.getHealth() >= mob.getMaxHealth() - 0.01F;
		health.addPermanentModifier(new AttributeModifier(ID, "LegionWareX distance health", multiplier - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));

		if (full) {
			mob.setHealth(mob.getMaxHealth());
		}

		return true;
	}

	static boolean counts(Mob mob) {
		if (!(mob instanceof Enemy) || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}

		String dimension = level.dimension().location().toString();
		return AddonConfig.farHealthDimensions().contains(dimension);
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)) {
			return;
		}

		synchronized (PENDING) {
			PENDING.add(new WeakReference<>(mob));
		}
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}

		List<WeakReference<Mob>> now;

		synchronized (PENDING) {
			if (PENDING.isEmpty()) {
				return;
			}

			now = new ArrayList<>(PENDING);
			PENDING.clear();
		}

		for (WeakReference<Mob> ref : now) {
			Mob mob = ref.get();

			if (mob != null && mob.isAlive() && !owned(mob) && counts(mob)) {
				apply(mob, FarFromSpawn.distance((ServerLevel) mob.level(), mob.getX(), mob.getZ()));
			}
		}
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		LegionWareX.LOGGER.info("LegionWareX distance: monsters' health x{} at {} blocks, x{} at {} and beyond (bosses x{}), in {}",
				String.format("%.2f", multiplier(AddonConfig.farStart() + AddonConfig.farStep(), false)), AddonConfig.farStart() + AddonConfig.farStep(),
				String.format("%.2f", multiplier(1.0e9, false)), AddonConfig.farStart() + AddonConfig.farStep() * AddonConfig.farSteps(),
				String.format("%.2f", multiplier(1.0e9, true)), AddonConfig.farHealthDimensions());
	}
}
