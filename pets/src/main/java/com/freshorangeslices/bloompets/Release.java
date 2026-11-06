package com.freshorangeslices.bloompets;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Releasing a pet (FreshOrangeSlices' playtest, 2026-11-05): the pet screen's Release (it asks first) and
 * {@code /bloompets release}. The pet comes off its owner's list, and whatever its storage (or a companion's food pouch)
 * held goes to them. Every pet goes back to the wild (FreshOrangeSlices: companions too, Ganyu and Claymore included):
 * a Bloom pet loses its owner mark and is untamed, out of the party and off its post, its levels gone; a companion is
 * untamed in its own mod as well (a Migamigos one loses its owner and is wild again, befriended again as any wild one),
 * with its Bloom levels, Spirit and tree gone. A summoned companion (Zoni's, crafted, never wild) goes back into its
 * device instead (FreshOrangeSlices): its gear drops at its feet, it's gone, and its owner gets its summon item (in
 * place of an empty one they carry). If a companion's mod won't let go of it, it stays its owner's there but leaves the list for good
 * (marked {@code releasedBy}). A pet that isn't loaded leaves the list now and is set free the next time it loads; an
 * entry whose pet is gone for good (a duplicate, one that vanished without dying) just leaves.
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
		Item summon = record.kind == Pets.Kind.MODDED ? summonItem(record.type) : null;

		// A summoned companion goes back into its device: its summon item for its owner now, wherever it is.
		if (summon != null) {
			giveSummon(player, summon);
		}

		if (live != null) {
			free(live, player.getUUID(), false);
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

	/** A companion untamed in its own mod: a tamed animal's way (and its mod's own "tamed" flag), or a Migamigos one's owner. */
	private static boolean untameCompanion(Entity entity, UUID owner) {
		if (entity instanceof TamableAnimal tamable && owner.equals(tamable.getOwnerUUID())) {
			tamable.setOrderedToSit(false);
			tamable.setInSittingPose(false);
			tamable.setTame(false);
			tamable.setOwnerUUID(null);
			modTamedFlag(entity);
			return true;
		}

		if (Amigos.has(entity) && Pets.clearMigamigosOwner(entity, owner)) {
			// A wild one roams.
			Amigos.setFollowing(entity, false);
			Amigos.setAllowWandering(entity, true);
			return true;
		}

		return false;
	}

	/** An MCreator companion's own "tamed" flag (Zoni's {@code DATA_Tamed}), off too. */
	@SuppressWarnings("unchecked")
	private static void modTamedFlag(Entity entity) {
		try {
			Object accessor = entity.getClass().getField("DATA_Tamed").get(null);

			if (accessor instanceof EntityDataAccessor<?> data) {
				entity.getEntityData().set((EntityDataAccessor<Boolean>) data, false);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// No such flag: the vanilla taming was all.
		}
	}

	/**
	 * A companion released before releasing untamed them in their own mod (Bloom Pets 0.5.0 and 0.5.1 only took them off
	 * the list): as it loads, it's untamed there too, if it's still that owner's (a summoned one goes back into its
	 * device on its first tick, its summon item to its owner). Public for the game tests.
	 */
	public static void finishOldRelease(Entity entity) {
		var data = Pets.dataOrNull(entity);

		if (data == null || !data.hasUUID("releasedBy") || Pets.kind(entity) != Pets.Kind.MODDED) {
			return;
		}

		UUID owner = data.getUUID("releasedBy");

		if (summonItem(type(entity)) != null) {
			data.remove("releasedBy");
			data.putUUID(BACK_TO_DEVICE, owner);
			data.putBoolean(GIVE_SUMMON, true);
		} else if (untameCompanion(entity, owner)) {
			data.remove("releasedBy");
			BloomPets.LOGGER.info("Bloom Pets: {}, released earlier, is wild again", entity.getName().getString());
		}
	}

	/** Marks on a summoned companion going back into its device on its next tick (not while it's being loaded). */
	private static final String BACK_TO_DEVICE = "backToDevice";
	private static final String GIVE_SUMMON = "giveSummon";

	private static String type(Entity entity) {
		var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		return key == null ? "" : key.toString();
	}

	/** A summoned companion's summon item (Zoni's: its kind's id and "_summon_item"), or null for any other. */
	public static Item summonItem(String type) {
		ResourceLocation kind = ResourceLocation.tryParse(type);

		if (kind == null) {
			return null;
		}

		ResourceLocation item = new ResourceLocation(kind.getNamespace(), kind.getPath() + "_summon_item");
		return ForgeRegistries.ITEMS.containsKey(item) ? ForgeRegistries.ITEMS.getValue(item) : null;
	}

	/** The summon item for its owner: in place of an empty one they carry (as Zoni's own return does), or added. */
	private static void giveSummon(ServerPlayer player, Item summon) {
		ResourceLocation id = ForgeRegistries.ITEMS.getKey(summon);
		ResourceLocation emptyId = id == null ? null : new ResourceLocation(id.getNamespace(), "empty_summon_item");
		Item empty = emptyId != null && ForgeRegistries.ITEMS.containsKey(emptyId) ? ForgeRegistries.ITEMS.getValue(emptyId) : null;

		if (empty != null) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(empty)) {
					player.getInventory().removeItem(i, 1);
					break;
				}
			}
		}

		ItemStack stack = new ItemStack(summon);

		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/** Its next tick: a summoned companion marked to go back into its device goes. */
	static void tick(LivingEntity entity) {
		var data = Pets.dataOrNull(entity);

		if (data == null || !data.hasUUID(BACK_TO_DEVICE)) {
			return;
		}

		UUID owner = data.getUUID(BACK_TO_DEVICE);
		boolean give = data.getBoolean(GIVE_SUMMON);
		data.remove(BACK_TO_DEVICE);
		data.remove(GIVE_SUMMON);
		backToDevice(entity, owner, give);
	}

	/**
	 * A summoned companion (Zoni's) goes back into its device: its gear dropped where it stands, and it's gone (no death:
	 * its mod's death code doesn't run). With {@code give}, its summon item goes to its owner (if they're on; else it's
	 * left where the companion was).
	 */
	private static void backToDevice(Entity entity, UUID owner, boolean give) {
		if (entity instanceof LivingEntity living) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack stack = living.getItemBySlot(slot);

				if (!stack.isEmpty()) {
					living.spawnAtLocation(stack.copy());
					living.setItemSlot(slot, ItemStack.EMPTY);
				}
			}
		}

		Item summon = summonItem(type(entity));

		if (give && summon != null) {
			ServerPlayer player = entity.getServer() == null ? null : entity.getServer().getPlayerList().getPlayer(owner);

			if (player != null) {
				giveSummon(player, summon);
			} else {
				entity.spawnAtLocation(new ItemStack(summon));
			}
		}

		if (entity.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 16,
					0.3, 0.4, 0.3, 0.02);
			level.playSound(null, entity.blockPosition(), SoundEvents.BEEHIVE_ENTER, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}

		BloomPets.LOGGER.info("Bloom Pets: {} went back into its summon device", entity.getName().getString());
		entity.discard();
	}

	/**
	 * Sets the pet free: now (released from the screen or the command), or as it loads after a release while it was away
	 * ({@code loading}: then a summoned companion goes back into its device on its first tick, not while it's being
	 * loaded). Public for the game tests.
	 */
	public static void free(Entity entity, UUID owner, boolean loading) {
		boolean companion = Pets.kind(entity) == Pets.Kind.MODDED;

		// A summoned companion (Zoni's) goes back into its device; its summon item went to its owner as they released it.
		if (companion && summonItem(type(entity)) != null) {
			Pets.forget(entity);

			if (loading) {
				Pets.data(entity).putUUID(BACK_TO_DEVICE, owner);
			} else {
				backToDevice(entity, owner, false);
			}

			return;
		}

		if (entity instanceof Mob mob) {
			Fetch.finish(mob, false);
			mob.setTarget(null);
			mob.getNavigation().stop();
		}

		Pets.forget(entity);

		if (companion) {
			// Untamed in its own mod too; if that can't be done, still its owner's there and kept off the list by this mark.
			if (!untameCompanion(entity, owner)) {
				Pets.data(entity).putUUID("releasedBy", owner);
			}
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
