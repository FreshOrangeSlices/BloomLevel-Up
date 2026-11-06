package com.freshorangeslices.bloompets;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Companions' Spirits (docs/design/PETS.md, Phase 5b; FreshOrangeSlices, 2026-11-05). Another mod's companion gets one
 * of the combat kinds' skill trees when it's added to its owner's pet list, at even odds (Wolf, Iron Golem, Polar
 * Bear, Goat, Panda; never the Vex): the "Spirit of the Wolf" and so on, with a Bloom pet's skills and numbers at the
 * companion's own level, working whenever it isn't sulking (a companion is always with its owner's band). The owner is
 * told at once above the hotbar, and the pet screen asks Keep or Reroll until it's settled; **one reroll, ever**,
 * always to another kind. Companions listed before this version get theirs the first time it sees them.
 * <p>
 * A companion at its top level (100, or a Migamigos Amigo's 99) gets the level-100 perks: a gold name and a sparkle,
 * and a second wind once each time it's called (a blow that would kill it leaves it at half health).
 */
public final class Spirits {
	private static final PetSkills.Kind[] KINDS = PetSkills.Kind.values();

	private Spirits() {
	}

	/** The companion's Spirit, or null (not a companion, or none rolled yet). */
	public static PetSkills.Kind of(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		return data == null || Pets.kind(pet) != Pets.Kind.MODDED ? null : PetSkills.Kind.byId(data.getString("spirit"));
	}

	/** Whether its owner has kept it or used the reroll (no more choosing). */
	public static boolean settled(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		return data != null && data.getBoolean("spiritSettled");
	}

	public static String nameKey(PetSkills.Kind kind) {
		return "bloompets.spirit.name." + kind.id();
	}

	/** An even pick of the combat kinds, never {@code not} (the reroll's). */
	public static PetSkills.Kind roll(RandomSource random, PetSkills.Kind not) {
		PetSkills.Kind kind;

		do {
			kind = KINDS[random.nextInt(KINDS.length)];
		} while (kind == not);

		return kind;
	}

	/** A listed companion without a Spirit gets one now, and its owner (if they're on) is told and asked. */
	static void ensure(LivingEntity pet) {
		if (pet.level().isClientSide() || Pets.kind(pet) != Pets.Kind.MODDED || Pets.owner(pet) == null || of(pet) != null) {
			return;
		}

		set(pet, roll(pet.getRandom(), null));
		ServerPlayer owner = pet.getServer() == null ? null : pet.getServer().getPlayerList().getPlayer(Pets.owner(pet));

		if (owner != null) {
			offer(owner, pet);
		}
	}

	/** Gives it this Spirit (tests, and the roll). */
	public static void set(LivingEntity pet, PetSkills.Kind kind) {
		Pets.data(pet).putString("spirit", kind.id());
		PetSkills.refresh(pet);
		PetEvents.observe(pet);
	}

	/**
	 * "Claymore gained the Spirit of the Wolf: open your pets (K) to keep it or reroll", above the hotbar (not chat:
	 * FreshOrangeSlices, 2026-11-05); the pet screen asks Keep or Reroll when its page opens.
	 */
	static void offer(ServerPlayer owner, LivingEntity pet) {
		PetSkills.Kind kind = of(pet);

		if (kind == null || settled(pet)) {
			return;
		}

		owner.displayClientMessage(Component.translatable("bloompets.spirit.gained.bar", pet.getName(),
				Component.translatable(nameKey(kind)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
		owner.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.2F);
	}

	/** The owner keeps the Spirit it has. */
	public static Component keep(ServerPlayer player, PetRegistry.Record record) {
		LivingEntity pet = pet(player, record);

		if (pet == null) {
			return Component.translatable("bloompets.post.away", record.name);
		}

		if (of(pet) == null || settled(pet)) {
			return Component.translatable("bloompets.spirit.settled", record.name);
		}

		Pets.data(pet).putBoolean("spiritSettled", true);
		PetEvents.observe(pet);
		return Component.translatable("bloompets.spirit.kept", record.name, Component.translatable(nameKey(of(pet))));
	}

	/** The one reroll: another kind, and then it's settled. */
	public static Component reroll(ServerPlayer player, PetRegistry.Record record) {
		LivingEntity pet = pet(player, record);

		if (pet == null) {
			return Component.translatable("bloompets.post.away", record.name);
		}

		PetSkills.Kind before = of(pet);

		if (before == null || settled(pet)) {
			return Component.translatable("bloompets.spirit.settled", record.name);
		}

		Pets.data(pet).putBoolean("spiritSettled", true);
		set(pet, roll(pet.getRandom(), before));

		if (pet.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ENCHANT, pet.getX(), pet.getY() + pet.getBbHeight() * 0.6, pet.getZ(), 30, 0.4, 0.5, 0.4, 0.6);
		}

		return Component.translatable("bloompets.spirit.rerolled", record.name, Component.translatable(nameKey(of(pet))));
	}

	private static LivingEntity pet(ServerPlayer player, PetRegistry.Record record) {
		return Pets.loaded(player.server, record.pet) instanceof LivingEntity living && Pets.kind(living) == Pets.Kind.MODDED ? living : null;
	}

	/**
	 * The second wind of a companion at its top level: a blow that would kill it leaves it at half health, once each time
	 * it's called. Taken as the blow lands, before it dies: a companion's own mod may do things as it dies that no
	 * called-off death undoes (Zoni's drop its gear and swap its summon device). Last, so armour and other mods have had
	 * their say on how hard the blow is.
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDamage(LivingDamageEvent event) {
		LivingEntity pet = event.getEntity();
		DamageSource source = event.getSource();

		if (event.isCanceled() || pet.level().isClientSide() || event.getAmount() < pet.getHealth() || Pets.kind(pet) != Pets.Kind.MODDED
				|| Pets.owner(pet) == null || !PetLevels.maxed(pet) || source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return;
		}

		CompoundTag data = Pets.data(pet);

		if (data.getBoolean("windUsed")) {
			return;
		}

		data.putBoolean("windUsed", true);
		event.setCanceled(true);
		pet.setHealth(pet.getMaxHealth() * 0.5F);
		ServerLevel level = (ServerLevel) pet.level();
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pet.getX(), pet.getY() + pet.getBbHeight() * 0.5, pet.getZ(), 40, 0.4, 0.5, 0.4, 0.4);
		level.playSound(null, pet.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.5F, 1.4F);
		ServerPlayer owner = pet.getServer().getPlayerList().getPlayer(Pets.owner(pet));

		if (owner != null) {
			owner.displayClientMessage(Component.translatable("bloompets.secondwind", pet.getName()).withStyle(ChatFormatting.GOLD), true);
		}
	}
}
