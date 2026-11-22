package com.freshorangeslices.legionwarex.companion;

import com.freshorangeslices.legionwarex.summon.Summons;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * A player's own attacks never land on their companions, pets or summons: swings, Better Combat's sweeps, arrows, their
 * TNT (FreshOrangeSlices, 2026-11-02: each hit cost a Migamigos companion some of its patience, and at none it turns on
 * its owner; a Claymore was lost that way). Nor do the attacks of the owner's other companions, pets and summons; and
 * since 0.4.0 none of those ever lands on the owner, and a summon's never lands on any player (2026-11-22: the Pirate
 * Saber's pirates hit their owner with sword and bolt). The hit is called off before it lands, so nothing counts it as
 * being hurt by its own side either. Everyone else's hits, and harm with no attacker (fire, falls, lava), land as usual.
 */
public final class OwnerHits {
	private OwnerHits() {
	}

	/** Whether this hit comes from the target's own side: its owner, or another companion or pet of that owner. */
	public static boolean ownSide(LivingEntity target, Entity attacker) {
		if (attacker == null || attacker == target) {
			return false;
		}

		UUID owner = Companions.ownerOf(target);
		return owner != null && (owner.equals(attacker.getUUID()) || owner.equals(Companions.ownerOf(attacker)));
	}

	/** Why this hit may not land ("its own side", "its owner", "a player"), or null when it may. */
	public static String refusal(LivingEntity target, Entity attacker) {
		if (attacker == null || attacker == target) {
			return null;
		}

		if (ownSide(target, attacker)) {
			return "its own side";
		}

		UUID side = Companions.ownerOf(attacker);

		if (side != null && side.equals(target.getUUID())) {
			return "its owner";
		}

		return target instanceof Player && Summons.isSummon(attacker) ? "a player" : null;
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onAttack(LivingAttackEvent event) {
		LivingEntity target = event.getEntity();
		Entity attacker = event.getSource().getEntity();

		if (target.level().isClientSide()) {
			return;
		}

		String why = refusal(target, attacker);

		if (why != null) {
			event.setCanceled(true);

			// Hits on the owner or another player are news (the pack's summons turned on their owner): logged once a kind.
			if (target instanceof Player && attacker instanceof LivingEntity living) {
				Summons.noteHit(living, "hit at " + why + " and was stopped", target);
			}
		}
	}

	/** Damage that came some way past the attack event (a mod hurting directly) is stopped here, and logged. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onHurt(LivingHurtEvent event) {
		LivingEntity target = event.getEntity();
		Entity attacker = event.getSource().getEntity();

		if (target.level().isClientSide() || event.isCanceled()) {
			return;
		}

		String why = refusal(target, attacker);

		if (why != null) {
			event.setCanceled(true);

			if (attacker instanceof LivingEntity living) {
				Summons.noteHit(living, "hurt " + why + " past the attack event and was stopped", target);
			}
		}
	}
}
