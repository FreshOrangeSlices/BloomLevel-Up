package com.freshorangeslices.bloompets;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * Party pets never die (the plugin's way, kept for the party pet): at no health they faint and rest where they fell for
 * 5 minutes, unhurt by anything meanwhile, then get up with half their health. A level-100 pet first gets a second wind
 * (back to half health), once each time it comes out (called, or joining the party). Pets outside the party are plain
 * pets and can die (FreshOrangeSlices, 2026-11-05); a pet at its post faints there, as in the plugin. {@code /kill} still kills; a fall into the void brings it to its
 * owner first.
 */
public final class Fainting {
	private static final String UNTIL = "faintedUntil";
	/** The pets resting right now (loaded), so the every-tick work only looks at them. */
	private static final Set<UUID> RESTING = ConcurrentHashMap.newKeySet();

	private Fainting() {
	}

	private static long now(Entity pet) {
		return pet.getServer() != null ? pet.getServer().overworld().getGameTime() : pet.level().getGameTime();
	}

	public static boolean fainted(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		return data != null && data.contains(UNTIL) && data.getLong(UNTIL) > now(pet);
	}

	static boolean resting(Entity pet) {
		return !RESTING.isEmpty() && RESTING.contains(pet.getUUID());
	}

	/** Ticks of rest left, or 0. */
	public static int ticksLeft(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		return data == null || !data.contains(UNTIL) ? 0 : (int) Math.max(0, data.getLong(UNTIL) - now(pet));
	}

	/** The pet comes out (called, or joining the party): a level-100 pet has its second wind again. */
	public static void cameOut(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);

		if (data != null) {
			data.remove("windUsed");
		}
	}

	/**
	 * The pet would die: a party pet gets its second wind, or faints, instead. Returns true if it lives (then the death
	 * is called off).
	 */
	public static boolean save(LivingEntity pet, DamageSource source) {
		if (pet.level().isClientSide() || Pets.kind(pet) != Pets.Kind.BLOOM || !Pets.inParty(pet) && !PetPosts.posted(pet) || Pets.owner(pet) == null
				|| source.is(DamageTypes.GENERIC_KILL)) {
			return false;
		}

		ServerPlayer owner = pet.getServer().getPlayerList().getPlayer(Pets.owner(pet));

		if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			// Out of the void to its owner's side; if it can't get there, it's lost.
			if (owner == null || owner.level() != pet.level()) {
				return false;
			}

			Calling.bring(pet, owner);
		}

		CompoundTag data = Pets.data(pet);

		if (fainted(pet)) {
			pet.setHealth(1.0F);
			return true;
		}

		ServerLevel level = (ServerLevel) pet.level();

		if (PetLevels.level(pet) >= PetLevels.MAX && !data.getBoolean("windUsed")) {
			data.putBoolean("windUsed", true);
			pet.setHealth(pet.getMaxHealth() * 0.5F);
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pet.getX(), pet.getY() + pet.getBbHeight() * 0.5, pet.getZ(), 40,
					0.4, 0.5, 0.4, 0.4);
			level.playSound(null, pet.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.5F, 1.4F);

			if (owner != null) {
				owner.displayClientMessage(Component.translatable("bloompets.secondwind", pet.getName()).withStyle(ChatFormatting.GOLD), true);
			}

			return true;
		}

		data.putLong(UNTIL, now(pet) + PetsConfig.restTicks());
		RESTING.add(pet.getUUID());
		PetMood.fainted(pet);
		pet.setHealth(1.0F);
		pet.clearFire();
		pet.removeAllEffects();
		rest(pet);
		level.sendParticles(ParticleTypes.POOF, pet.getX(), pet.getY() + pet.getBbHeight() * 0.5, pet.getZ(), 20,
				pet.getBbWidth() * 0.5, pet.getBbHeight() * 0.5, pet.getBbWidth() * 0.5, 0.02);
		level.playSound(null, pet.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.NEUTRAL, 1.0F, 0.7F);

		if (owner != null) {
			int seconds = PetsConfig.restTicks() / 20;
			owner.sendSystemMessage(Component.translatable("bloompets.faint", pet.getName(), String.format("%d:%02d", seconds / 60, seconds % 60))
					.withStyle(ChatFormatting.GRAY));
		}

		PetEvents.observe(pet);
		return true;
	}

	/** Every tick while it rests: it lies still (no target, no walking), sitting if it can. */
	static void rest(LivingEntity pet) {
		if (pet instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
			mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}

		if (pet instanceof TamableAnimal tamable) {
			tamable.setInSittingPose(true);
		}

		pet.setDeltaMovement(0.0, Math.min(0.0, pet.getDeltaMovement().y), 0.0);

		if (pet.tickCount % 20 == 0) {
			pet.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 9, false, false, false));
		}
	}

	/** Once a second: is its rest over? (And a pet that loaded mid-rest goes back to resting.) */
	static void check(LivingEntity pet) {
		CompoundTag data = Pets.dataOrNull(pet);

		if (data == null || !data.contains(UNTIL)) {
			RESTING.remove(pet.getUUID());
			return;
		}

		if (fainted(pet)) {
			RESTING.add(pet.getUUID());
			return;
		}

		RESTING.remove(pet.getUUID());
		data.remove(UNTIL);
		pet.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		pet.setHealth(Math.max(pet.getHealth(), (float) (pet.getMaxHealth() * PetsConfig.restHealth())));

		if (pet instanceof TamableAnimal tamable) {
			tamable.setInSittingPose(tamable.isOrderedToSit());
		}

		if (pet.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pet.getX(), pet.getY() + pet.getBbHeight() * 0.7, pet.getZ(), 8,
					0.4, 0.3, 0.4, 0.0);
		}

		ServerPlayer owner = pet.getServer() == null || Pets.owner(pet) == null ? null : pet.getServer().getPlayerList().getPlayer(Pets.owner(pet));

		if (owner != null) {
			owner.displayClientMessage(Component.translatable("bloompets.faint.back", pet.getName()), true);
		}

		PetEvents.observe(pet);
	}
}
