package com.freshorangeslices.bloompets;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The party (FreshOrangeSlices, 2026-11-04): one Bloom pet at a time, the one that follows its owner and has its
 * Bloom bonuses. Modded companions are always with their owner and need no place. Joining fills the pet's health, so
 * changes wait out a cooldown (5 minutes by default).
 */
public final class Party {
	private Party() {
	}

	/** Puts the pet in its owner's party, or takes it out if it's in; returns what to tell the player. */
	public static Component toggle(ServerPlayer player, PetRegistry.Record record) {
		PetRegistry registry = PetRegistry.get(player.server);

		if (record.kind == Pets.Kind.MODDED) {
			return Component.translatable("bloompets.party.modded", record.name);
		}

		long now = player.server.overworld().getGameTime();
		long last = registry.partyChanged(player.getUUID());
		int cooldown = PetsConfig.partyCooldownTicks();

		if (last != Long.MIN_VALUE && now - last < cooldown) {
			long seconds = (cooldown - (now - last) + 19) / 20;
			return Component.translatable("bloompets.party.wait", String.format("%d:%02d", seconds / 60, seconds % 60));
		}

		PetRegistry.Record current = registry.partyPet(player.getUUID());

		if (current != null) {
			current.party = false;
			Entity entity = Pets.loaded(player.server, current.pet);

			if (entity != null) {
				Pets.setParty(entity, false);
			}
		}

		registry.setPartyChanged(player.getUUID(), now);

		if (current == record) {
			return Component.translatable("bloompets.party.left", record.name);
		}

		record.party = true;
		record.post = null;
		Entity entity = Pets.loaded(player.server, record.pet);

		if (entity != null) {
			// Joining the party takes it off its post.
			PetPosts.leave(entity);
			Pets.setParty(entity, true);

			if (entity instanceof LivingEntity living) {
				living.setHealth(living.getMaxHealth());
			}
		} else {
			record.heal = true;
		}

		registry.setDirty();
		return Component.translatable("bloompets.party.joined", record.name);
	}
}
