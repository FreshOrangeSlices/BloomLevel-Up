package com.freshorangeslices.legionwarex.companion;

import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * A player's own attacks never land on their companions or pets: swings, Better Combat's sweeps, arrows, their TNT
 * (FreshOrangeSlices, 2026-11-02: each hit cost a Migamigos companion some of its patience, and at none it turns on its
 * owner; a Claymore was lost that way). Nor do the attacks of the owner's other companions and pets. The hit is called
 * off before it lands, so the companion doesn't count it as being hurt by its owner either. Everyone else's hits, and
 * harm with no attacker (fire, falls, lava), land as usual.
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

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onAttack(LivingAttackEvent event) {
		LivingEntity target = event.getEntity();

		if (!target.level().isClientSide() && ownSide(target, event.getSource().getEntity())) {
			event.setCanceled(true);
		}
	}
}
