package com.freshorangeslices.bloompets;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;

/**
 * Releasing a pet (FreshOrangeSlices' playtest, 2026-11-05): the pet screen's Release (it asks first) and
 * {@code /bloompets release}. The pet comes off its owner's list, and whatever its storage (or a companion's food pouch)
 * held goes to them. A Bloom pet goes back to the wild: no owner mark, untamed, out of the party and off its post, its
 * levels gone. A companion stays its owner's in its own mod (Bloom Pets never takes that away) but leaves the list for
 * good, with its Bloom levels, Spirit and tree; given to someone else, it can be listed again. A pet that isn't loaded
 * leaves the list now and is set free the next time it loads; an entry whose pet is gone for good (a duplicate, one that
 * vanished without dying) just leaves.
 */
public final class Release {
	private Release() {
	}

	/** Releases the player's pet; says so. */
	public static Component release(ServerPlayer player, PetRegistry.Record record) {
		PetRegistry registry = PetRegistry.get(player.server);
		Entity live = Pets.loaded(player.server, record.pet);
		registry.remove(record.pet);
		giveBack(player, record);

		if (live != null) {
			free(live, player.getUUID());
		} else {
			registry.releaseLater(record.pet, player.getUUID());
		}

		BloomPets.LOGGER.info("Bloom Pets: {} released {} ({}, {})", player.getGameProfile().getName(), record.name, record.type,
				live != null ? "loaded" : "away");
		return Component.translatable("bloompets.release.done", record.name).withStyle(ChatFormatting.GRAY);
	}

	/** What its storage held, into its owner's inventory (or at their feet). */
	private static void giveBack(ServerPlayer player, PetRegistry.Record record) {
		if (record.items.isEmpty()) {
			return;
		}

		SimpleContainer box = new SimpleContainer(Math.max(record.items.size(), Math.max(record.slots, 1)));
		box.fromTag(record.items);

		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack stack = box.getItem(i);

			if (!stack.isEmpty() && !player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}

		record.items = new ListTag();
	}

	/** Sets the pet free (now, or as it loads after a release while it was away). Public for the game tests. */
	public static void free(Entity entity, UUID owner) {
		boolean companion = Pets.kind(entity) == Pets.Kind.MODDED;

		if (entity instanceof Mob mob) {
			Fetch.finish(mob, false);
			mob.setTarget(null);
			mob.getNavigation().stop();
		}

		Pets.forget(entity);

		if (companion) {
			// Still its owner's in its own mod: kept off the list by this mark.
			Pets.data(entity).putUUID("releasedBy", owner);
		} else if (entity instanceof TamableAnimal tamable && owner.equals(tamable.getOwnerUUID())) {
			tamable.setOrderedToSit(false);
			tamable.setInSittingPose(false);
			tamable.setTame(false);
			tamable.setOwnerUUID(null);
		} else if (entity instanceof AbstractHorse horse && owner.equals(horse.getOwnerUUID())) {
			horse.setTamed(false);
			horse.setOwnerUUID(null);
		}

		if (!companion && entity instanceof Allay allay) {
			allay.getBrain().eraseMemory(MemoryModuleType.LIKED_PLAYER);
		}

		if (entity instanceof LivingEntity living) {
			PetSkills.refresh(living);
		}

		if (entity.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 12,
					0.3, 0.3, 0.3, 0.02);
		}
	}
}
