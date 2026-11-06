package com.freshorangeslices.bloompets;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import org.joml.Vector3f;

/**
 * What a pet does when it isn't working (FreshOrangeSlices' playtest, 2026-11-05): **Follow** (walks after its owner,
 * and a fighter fights for them), **Wander** (roams around the spot it was left) or **Stay** (stands on its spot, and
 * goes back to it after a fight), for every pet, Bloom pets and companions alike: the pet screen's button,
 * {@code /bloompets mode}. Kept on the pet ({@code bloompets.mode}); a pet never told keeps what it does already (a
 * party pet follows, a tame wolf follows unless it's sitting, a Migamigos companion as it's set, other pets wander).
 * Joining the party sets Follow; leaving it, Wander where it is; a Call, Follow. A posted pet keeps its post whatever
 * its mode. A sitting wolf, cat or parrot stays by sitting; a Migamigos companion is set through its own Follow,
 * Wander and post (by name; the add-on keeps a staying one on its spot); everything else through Bloom Pets' goals.
 */
public final class PetModes {
	private PetModes() {
	}

	public enum Mode {
		FOLLOW, WANDER, STAY;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Mode byId(String id) {
			for (Mode mode : values()) {
				if (mode.id().equals(id)) {
					return mode;
				}
			}

			return null;
		}

		/** The next one, for the screen's button. */
		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	/** The pet's mode: as it was told, or what it does already. */
	public static Mode mode(Entity pet) {
		CompoundTag data = Pets.dataOrNull(pet);
		Mode told = data == null ? null : Mode.byId(data.getString("mode"));
		return told != null ? told : natural(pet);
	}

	/** What a pet never told does: a party pet follows; a tame one as it's set; a Migamigos companion as it's set; the rest wander. */
	static Mode natural(Entity pet) {
		if (Pets.inParty(pet)) {
			return Mode.FOLLOW;
		}

		if (Amigos.has(pet)) {
			return Amigos.following(pet) ? Mode.FOLLOW : Amigos.canWander(pet) ? Mode.WANDER : Mode.STAY;
		}

		if (pet instanceof TamableAnimal tamable && tamable.isTame()) {
			return tamable.isOrderedToSit() ? Mode.STAY : Mode.FOLLOW;
		}

		return Mode.WANDER;
	}

	/** Tells the pet: kept on it, its home here (where it wanders or stays), and its own mod's or vanilla's state to match. */
	public static void set(Mob pet, Mode mode) {
		Pets.data(pet).putString("mode", mode.id());
		Pets.setHome(pet, pet.blockPosition());

		if (Amigos.has(pet)) {
			Amigos.setFollowing(pet, mode == Mode.FOLLOW);
			Amigos.setAllowWandering(pet, mode != Mode.STAY);

			if (mode != Mode.FOLLOW) {
				Amigos.setPostPos(pet, new Vector3f((float) pet.getX(), (float) pet.getY(), (float) pet.getZ()));
			}
		} else if (pet instanceof TamableAnimal tamable && tamable.isTame()) {
			// A wolf, cat or parrot sits to stay; Zoni's companions don't sit, Bloom Pets' goals hold them instead.
			// One that doesn't sit is held "sitting" in Wander and Stay too, so its own follow goal lets go of it.
			boolean sits = PetGoals.sits(pet);
			tamable.setOrderedToSit(sits ? mode == Mode.STAY : mode != Mode.FOLLOW);
			tamable.setInSittingPose(sits && mode == Mode.STAY);
		}

		pet.getNavigation().stop();
		PetEvents.observe(pet);
	}

	/** The party's changed: joining it, the pet follows; leaving it, it wanders where it is. */
	static void partyChanged(Entity pet, boolean party) {
		if (pet instanceof Mob mob && !mob.level().isClientSide()) {
			set(mob, party ? Mode.FOLLOW : Mode.WANDER);
		}
	}

	/** Sets it, and says so. */
	public static Component change(Mob pet, Mode mode, String name) {
		set(pet, mode);
		return Component.translatable("bloompets.mode.set." + mode.id(), name);
	}

	/** The pet screen's button and the command: the player's pet (it has to be loaded) to this mode. */
	public static Component change(ServerPlayer player, PetRegistry.Record record, String id) {
		Mode mode = Mode.byId(id);

		if (mode == null) {
			return Component.translatable("bloompets.mode.unknown", id);
		}

		return Pets.loaded(player.server, record.pet) instanceof Mob mob ? change(mob, mode, mob.getName().getString())
				: Component.translatable("bloompets.away", record.name);
	}

	/** Names may be this long (as an anvil's). */
	public static final int NAME_MAX = 32;

	/**
	 * A new name for the player's pet (it has to be loaded), from the pet screen or the command: plain text (no colour
	 * codes); empty gives it back its own (a level-100 pet's stays gold). Two pets of a kind can then be told apart.
	 */
	public static Component rename(ServerPlayer player, PetRegistry.Record record, String wanted) {
		if (!(Pets.loaded(player.server, record.pet) instanceof Mob mob)) {
			return Component.translatable("bloompets.away", record.name);
		}

		String name = ChatFormatting.stripFormatting(wanted == null ? "" : wanted);
		name = name == null ? "" : name.replace("\u00a7", "").trim();

		if (name.length() > NAME_MAX) {
			name = name.substring(0, NAME_MAX).trim();
		}

		String before = record.name;
		mob.setCustomName(name.isEmpty() ? null : Component.literal(name));

		if (PetLevels.maxed(mob)) {
			PetSkills.goldName(mob);
		}

		PetEvents.observe(mob);
		return Component.translatable("bloompets.rename.done", before, mob.getName().getString());
	}
}
