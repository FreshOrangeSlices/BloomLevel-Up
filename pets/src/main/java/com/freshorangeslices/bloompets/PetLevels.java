package com.freshorangeslices.bloompets;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Pet levels, 1 to 100 (docs/design/PETS.md, Levels), kept on the pet itself ({@code bloompets.level} and
 * {@code bloompets.xp} in its own data), so a pet that comes back (a Migamigos keepsake) keeps its level. The curve is
 * Bloom's own halved (25 + 5 x L); non-combat pets need half of that again. Only an active pet earns XP: the Bloom pet
 * in its owner's party, awake, or any of the owner's modded companions (always with them).
 */
public final class PetLevels {
	public static final int MAX = 100;
	/** Bloom Pets helps an Amigo up to this Amigo level; past it, it's Migamigos' own climb (FreshOrangeSlices, 2026-11-05). */
	public static final int AMIGO_PACE_CAP = 75;

	private PetLevels() {
	}

	/** XP from level {@code level} to the next. */
	public static double xpToNext(int level, boolean combat) {
		double xp = PetsConfig.curveBase() + PetsConfig.curveLinear() * level;
		return combat ? xp : xp * PetsConfig.passiveCurveFactor();
	}

	/** Combat pets (and modded companions, which all fight) level on the combat curve, by the damage they deal. */
	public static boolean combat(Entity pet) {
		if (Pets.kind(pet) == Pets.Kind.MODDED) {
			return true;
		}

		Species species = Pets.species(pet);
		return species != null && species.combat();
	}

	/** The pet's level: its own, or a Migamigos companion's Amigo level (FreshOrangeSlices, 2026-11-05: one level each). */
	public static int level(Entity pet) {
		Integer amigo = Amigos.level(pet);

		if (amigo != null) {
			return Math.max(1, Math.min(MAX, amigo));
		}

		CompoundTag data = Pets.dataOrNull(pet);
		return data == null ? 1 : Math.max(1, Math.min(MAX, data.getInt("level")));
	}

	public static double xp(Entity pet) {
		Integer amigo = Amigos.level(pet) == null ? null : Amigos.xp(pet);

		if (amigo != null) {
			return amigo;
		}

		CompoundTag data = Pets.dataOrNull(pet);
		return data == null ? 0.0 : data.getDouble("xp");
	}

	/** The XP its next level takes (an Amigo's from Migamigos' own curve), or 0 at the top. */
	public static double xpNext(Entity pet) {
		Integer amigo = Amigos.level(pet);

		if (amigo != null) {
			Integer next = amigo >= 99 ? null : Amigos.xpNext(pet, amigo);
			return next == null ? 0.0 : next;
		}

		int level = level(pet);
		return level >= MAX ? 0.0 : xpToNext(level, combat(pet));
	}

	/** Its top level: 100, or a Migamigos Amigo's 99 (it gets the level-100 perks there: FreshOrangeSlices, 2026-11-05). */
	public static int top(Entity pet) {
		return Amigos.level(pet) != null ? Amigos.MAX : MAX;
	}

	/** At its top level: a gold name, a sparkle, a second wind. */
	public static boolean maxed(Entity pet) {
		Pets.Kind kind = Pets.kind(pet);
		return (kind == Pets.Kind.BLOOM || kind == Pets.Kind.MODDED) && level(pet) >= top(pet);
	}

	/** Whether the pet earns XP right now: a Bloom pet in the party or at its post and awake, or a modded companion. */
	public static boolean active(Entity pet) {
		if (Pets.owner(pet) == null) {
			return false;
		}

		Pets.Kind kind = Pets.kind(pet);
		return kind == Pets.Kind.MODDED || kind == Pets.Kind.BLOOM && (Pets.inParty(pet) || PetPosts.posted(pet)) && !Fainting.fainted(pet);
	}

	/**
	 * Adds XP to an active pet, levelling it up as it fills; returns the levels gained. A Migamigos Amigo instead climbs
	 * at a Bloom pet's pace ({@link #amigoPace}).
	 */
	public static int addXp(LivingEntity pet, double amount) {
		if (Amigos.has(pet)) {
			return amigoPace(pet, amount);
		}

		int level = level(pet);

		if (amount <= 0 || level >= MAX) {
			return 0;
		}

		boolean combat = combat(pet);
		double xp = xp(pet) + amount;
		int start = level;

		while (level < MAX && xp >= xpToNext(level, combat)) {
			xp -= xpToNext(level, combat);
			level++;
		}

		store(pet, level, level >= MAX ? 0.0 : xp);

		if (level > start) {
			leveledUp(pet, start, level, true);
		}

		return level - start;
	}

	/**
	 * An Amigo at a Bloom pet's pace (FreshOrangeSlices, 2026-11-05): on its own, Migamigos' curve makes Amigo 50 about
	 * 2,300 zombie kills and 75 about 25,000, so a One and the Same tree's last effects would never come. Bloom Pets
	 * keeps its own count for the Amigo on the Bloom combat curve ({@code bloompets.paceLevel}, {@code paceXp}; it
	 * starts at the Amigo's level when first counted, nothing owed for the past), and each time that count would level
	 * a Bloom pet it gives the Amigo one Amigo level's worth of XP, up to Amigo level {@value #AMIGO_PACE_CAP}.
	 * Migamigos levels it on its next kill, with its own health and damage. Returns the Amigo levels given.
	 */
	private static int amigoPace(LivingEntity pet, double amount) {
		Integer amigo = Amigos.level(pet);

		if (amount <= 0 || amigo == null) {
			return 0;
		}

		CompoundTag data = Pets.data(pet);
		int pace = data.contains("paceLevel") ? Math.max(1, data.getInt("paceLevel")) : Math.max(1, Math.min(MAX, amigo));
		double xp = data.getDouble("paceXp") + amount;
		int given = 0;

		while (pace < MAX && xp >= xpToNext(pace, true)) {
			xp -= xpToNext(pace, true);
			pace++;

			if (Amigos.giveLevel(pet, AMIGO_PACE_CAP)) {
				given++;
			}
		}

		data.putInt("paceLevel", pace);
		data.putDouble("paceXp", pace >= MAX ? 0.0 : xp);
		return given;
	}

	/** Sets the pet's level (operators' command, tests), its XP back to the start of that level. */
	public static void setLevel(LivingEntity pet, int level) {
		if (Amigos.has(pet)) {
			return;
		}

		int before = level(pet);
		int after = Math.max(1, Math.min(MAX, level));
		store(pet, after, 0.0);

		if (after != before) {
			leveledUp(pet, before, after, after > before);
		}
	}

	private static void store(LivingEntity pet, int level, double xp) {
		CompoundTag data = Pets.data(pet);
		data.putInt("level", level);
		data.putDouble("xp", xp);
	}

	private static void leveledUp(LivingEntity pet, int from, int to, boolean announce) {
		PetSkills.refresh(pet);

		if (to >= top(pet)) {
			PetSkills.goldName(pet);
		}

		PetEvents.observe(pet);

		if (!(pet.level() instanceof ServerLevel level)) {
			return;
		}

		UUID ownerId = Pets.owner(pet);
		ServerPlayer ownerPlayer = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);

		if (ownerPlayer != null && to >= 10) {
			PetAdvancements.award(ownerPlayer, PetAdvancements.LEVEL_10);
		}

		if (ownerPlayer != null && to >= 25) {
			PetAdvancements.award(ownerPlayer, PetAdvancements.LEVEL_25);
		}

		if (!announce) {
			return;
		}

		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pet.getX(), pet.getY() + pet.getBbHeight() * 0.7, pet.getZ(), 12,
				pet.getBbWidth() * 0.5, pet.getBbHeight() * 0.4, pet.getBbWidth() * 0.5, 0.0);
		level.playSound(null, pet.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6F, 1.3F);
		UUID owner = Pets.owner(pet);
		ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);

		if (player == null) {
			return;
		}

		MutableComponent text = Component.translatable("bloompets.levelup", pet.getName(), to).withStyle(ChatFormatting.GOLD);
		PetSkills.Kind kind = PetSkills.kind(pet);
		List<Component> unlocked = new ArrayList<>();

		for (PetSkills.Slot slot : PetSkills.Slot.values()) {
			if (slot.level > from && slot.level <= to && (kind != null || slot == PetSkills.Slot.MAX && Pets.kind(pet) == Pets.Kind.BLOOM)) {
				unlocked.add(Component.translatable(PetSkills.nameKey(kind, slot)));
			}
		}

		for (int i = 0; i < unlocked.size(); i++) {
			text.append(Component.literal(i == 0 ? " " : ", ").withStyle(ChatFormatting.GRAY))
					.append(unlocked.get(i).copy().withStyle(ChatFormatting.YELLOW));
		}

		if (!unlocked.isEmpty()) {
			text.append(Component.translatable("bloompets.levelup.unlocked").withStyle(ChatFormatting.GRAY));
		}

		player.sendSystemMessage(text);
	}
}
