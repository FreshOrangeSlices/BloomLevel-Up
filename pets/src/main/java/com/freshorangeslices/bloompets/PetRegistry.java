package com.freshorangeslices.bloompets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Every player's pets, kept with the world (data/bloompets_pets.dat in the Overworld's folder), so the list shows pets
 * that aren't loaded right now and a Bloom pet's storage lives here. Brought up to date whenever a pet loads, every
 * few seconds while it's loaded, and when it unloads.
 */
public final class PetRegistry extends SavedData {
	private static final String NAME = "bloompets_pets";
	private final Map<UUID, List<Record>> byOwner = new LinkedHashMap<>();
	private final Map<UUID, UUID> ownerOf = new HashMap<>();
	private final Map<UUID, Long> partyChanged = new HashMap<>();

	/** One pet, as last seen. */
	public static final class Record {
		public UUID pet;
		public String type;
		public Pets.Kind kind;
		public String name;
		public String dimension;
		public BlockPos pos;
		public boolean party;
		/** Fill its health the next time it loads (it joined the party while away). */
		public boolean heal;
		public long seen;
		public int slots;
		public ListTag items = new ListTag();

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("pet", pet);
			tag.putString("type", type);
			tag.putString("kind", kind.name());
			tag.putString("name", name);
			tag.putString("dimension", dimension);
			tag.putLong("pos", pos.asLong());
			tag.putBoolean("party", party);
			tag.putBoolean("heal", heal);
			tag.putLong("seen", seen);
			tag.putInt("slots", slots);
			tag.put("items", items);
			return tag;
		}

		static Record load(CompoundTag tag) {
			Record record = new Record();
			record.pet = tag.getUUID("pet");
			record.type = tag.getString("type");
			record.kind = Pets.Kind.valueOf(tag.getString("kind"));
			record.name = tag.getString("name");
			record.dimension = tag.getString("dimension");
			record.pos = BlockPos.of(tag.getLong("pos"));
			record.party = tag.getBoolean("party");
			record.heal = tag.getBoolean("heal");
			record.seen = tag.getLong("seen");
			record.slots = tag.getInt("slots");
			record.items = tag.getList("items", Tag.TAG_COMPOUND);
			return record;
		}
	}

	public static PetRegistry get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(PetRegistry::load, PetRegistry::new, NAME);
	}

	public List<Record> pets(UUID owner) {
		return byOwner.getOrDefault(owner, List.of());
	}

	public Record find(UUID pet) {
		UUID owner = ownerOf.get(pet);
		return owner == null ? null : pets(owner).stream().filter(r -> r.pet.equals(pet)).findFirst().orElse(null);
	}

	public UUID ownerOf(UUID pet) {
		return ownerOf.get(pet);
	}

	/** The owner's Bloom pet in the party, or null. */
	public Record partyPet(UUID owner) {
		return pets(owner).stream().filter(r -> r.party).findFirst().orElse(null);
	}

	public long partyChanged(UUID owner) {
		return partyChanged.getOrDefault(owner, Long.MIN_VALUE);
	}

	public void setPartyChanged(UUID owner, long gameTime) {
		partyChanged.put(owner, gameTime);
		setDirty();
	}

	/** Adds the pet to its owner's list, or brings its entry up to date (a pet that changed hands moves over). */
	public Record upsert(UUID owner, Entity entity) {
		UUID previous = ownerOf.get(entity.getUUID());
		Record record = previous == null ? null : find(entity.getUUID());

		if (record != null && !previous.equals(owner)) {
			byOwner.get(previous).remove(record);
			record.party = false;
			byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(record);
		}

		if (record == null) {
			record = new Record();
			record.pet = entity.getUUID();
			Species species = Pets.species(entity);
			record.slots = species == null ? 0 : species.storage();
			byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(record);
		}

		ownerOf.put(entity.getUUID(), owner);
		var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
		record.type = key == null ? "unknown" : key.toString();
		record.kind = Pets.kind(entity) == null ? Pets.Kind.MODDED : Pets.kind(entity);
		record.name = entity.getName().getString();
		record.dimension = entity.level().dimension().location().toString();
		record.pos = entity.blockPosition();
		record.seen = entity.level().getGameTime();
		setDirty();
		return record;
	}

	/** Takes the pet off its owner's list; returns its entry (with its storage), or null. */
	public Record remove(UUID pet) {
		UUID owner = ownerOf.remove(pet);

		if (owner == null) {
			return null;
		}

		Record record = pets(owner).stream().filter(r -> r.pet.equals(pet)).findFirst().orElse(null);
		byOwner.get(owner).remove(record);
		setDirty();
		return record;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag owners = new ListTag();

		for (Map.Entry<UUID, List<Record>> entry : byOwner.entrySet()) {
			CompoundTag owner = new CompoundTag();
			owner.putUUID("owner", entry.getKey());

			if (partyChanged.containsKey(entry.getKey())) {
				owner.putLong("partyChanged", partyChanged.get(entry.getKey()));
			}

			ListTag pets = new ListTag();
			entry.getValue().forEach(record -> pets.add(record.save()));
			owner.put("pets", pets);
			owners.add(owner);
		}

		tag.put("owners", owners);
		return tag;
	}

	public static PetRegistry load(CompoundTag tag) {
		PetRegistry registry = new PetRegistry();

		for (Tag element : tag.getList("owners", Tag.TAG_COMPOUND)) {
			CompoundTag owner = (CompoundTag) element;
			UUID id = owner.getUUID("owner");

			if (owner.contains("partyChanged")) {
				registry.partyChanged.put(id, owner.getLong("partyChanged"));
			}

			List<Record> pets = new ArrayList<>();

			for (Tag pet : owner.getList("pets", Tag.TAG_COMPOUND)) {
				try {
					Record record = Record.load((CompoundTag) pet);
					pets.add(record);
					registry.ownerOf.put(record.pet, id);
				} catch (RuntimeException e) {
					BloomPets.LOGGER.warn("Bloom Pets: skipped a pet entry it couldn't read: {}", e.getMessage());
				}
			}

			registry.byOwner.put(id, pets);
		}

		return registry;
	}
}
