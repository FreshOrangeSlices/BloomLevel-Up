package com.freshorangeslices.bloompets;

import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A companion that dies and comes back keeps what it had here (FreshOrangeSlices' playtest, 2026-11-05). Zoni's
 * Companions bring a fallen companion back from its summon device as a fresh entity, which Bloom Pets would otherwise
 * list at level 1. So when a listed companion dies (not a Migamigos one: the add-on's keepsake brings that very one
 * back), its level and XP, Spirit (and whether it was settled), tree and switched-off abilities, mood and name are kept
 * for its owner, by its kind; the next companion of that kind the owner gets picks them up, the newest fallen first (a
 * few are kept a kind). Its storage isn't: what it carried falls where it died.
 */
public final class Comeback {
	/** Fallen companions kept a kind, per owner. */
	static final int MAX_KEPT = 5;
	private static final List<String> KEEP = List.of("level", "xp", "spirit", "spiritSettled", "tree", "abilitiesOff", "treeWait",
			"mood", "lovedUntil");

	private Comeback() {
	}

	private static String type(Entity entity) {
		var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		return key == null ? "unknown" : key.toString();
	}

	/** A listed companion died: what it had here is kept for its owner. */
	static void fallen(LivingEntity pet, PetRegistry registry) {
		UUID owner = Pets.owner(pet);
		CompoundTag data = Pets.dataOrNull(pet);

		if (owner == null || data == null || Pets.kind(pet) != Pets.Kind.MODDED || Amigos.has(pet)) {
			return;
		}

		CompoundTag kept = new CompoundTag();

		for (String key : KEEP) {
			if (data.contains(key)) {
				kept.put(key, data.get(key).copy());
			}
		}

		if (pet.hasCustomName()) {
			kept.putString("name", Component.Serializer.toJson(pet.getCustomName()));
		}

		registry.rememberFallen(owner, type(pet), kept);
	}

	/**
	 * A companion just listed: if its owner lost one of its kind, it picks up where that one left off. Returns whether it
	 * did. Before its Spirit is rolled, so it keeps the fallen one's.
	 */
	static boolean restore(LivingEntity pet, PetRegistry registry) {
		UUID owner = Pets.owner(pet);

		if (owner == null || Pets.kind(pet) != Pets.Kind.MODDED || Amigos.has(pet)) {
			return false;
		}

		CompoundTag kept = registry.takeFallen(owner, type(pet));

		if (kept == null) {
			return false;
		}

		CompoundTag data = Pets.data(pet);

		for (String key : KEEP) {
			if (kept.contains(key)) {
				data.put(key, kept.get(key).copy());
			}
		}

		if (kept.contains("name")) {
			Component name = Component.Serializer.fromJson(kept.getString("name"));

			if (name != null) {
				pet.setCustomName(name);
			}
		}

		PetSkills.refresh(pet);

		if (PetLevels.maxed(pet)) {
			PetSkills.goldName(pet);
		}

		ServerPlayer player = pet.getServer() == null ? null : pet.getServer().getPlayerList().getPlayer(owner);

		if (player != null) {
			player.displayClientMessage(Component.translatable("bloompets.comeback", pet.getName(), PetLevels.level(pet))
					.withStyle(ChatFormatting.LIGHT_PURPLE), true);
		}

		BloomPets.LOGGER.info("Bloom Pets: {} came back at level {} for {}", pet.getName().getString(), PetLevels.level(pet), owner);
		return true;
	}
}
