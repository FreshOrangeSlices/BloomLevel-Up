package com.freshorangeslices.bloompets.network;

import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.Calling;
import com.freshorangeslices.bloompets.Fainting;
import com.freshorangeslices.bloompets.Party;
import com.freshorangeslices.bloompets.PetLevels;
import com.freshorangeslices.bloompets.PetMood;
import com.freshorangeslices.bloompets.PetTrees;
import com.freshorangeslices.bloompets.PetRegistry;
import com.freshorangeslices.bloompets.PetStorage;
import com.freshorangeslices.bloompets.Pets;
import com.freshorangeslices.bloompets.PetsConfig;
import com.freshorangeslices.bloompets.Species;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The pet screen's messages: the client asks for its player's pets (or acts on one: call, party, storage) and the
 * server answers with the whole list, as the world keeps it plus what's live for the pets that are loaded.
 */
public final class PetsNetwork {
	private static final String VERSION = "7";
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(BloomPets.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

	private PetsNetwork() {
	}

	public static void register() {
		int id = 0;
		CHANNEL.messageBuilder(Request.class, id++, NetworkDirection.PLAY_TO_SERVER)
				.encoder(Request::encode).decoder(Request::decode).consumerMainThread(Request::handle).add();
		CHANNEL.messageBuilder(PetList.class, id++, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(PetList::encode).decoder(PetList::decode).consumerMainThread(PetList::handle).add();
	}

	/** What the player can do with a pet from the screen (REFRESH: the list again for an open screen, never opening it). */
	public enum Action {
		LIST, CALL, PARTY, STORAGE, REFRESH, POST,
		/** One and the Same: pick a tree (the argument), reset it, switch a cooldown ability (the argument) off or on. */
		TREE, TREE_RESET, ABILITY
	}

	/** Sends the player their pets; {@code open} opens the screen, else it only refreshes an open one. */
	public static void sendList(ServerPlayer player, boolean open, Component message) {
		sendList(player, open, message, false);
	}

	/** As above; {@code bloomView} opens the screen on its Bloom view (if the player has One and the Same). */
	public static void sendList(ServerPlayer player, boolean open, Component message, boolean bloomView) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), PetList.of(player, open, message, bloomView));
	}

	/** Client to server: list my pets, or act on one of them (with an argument for some actions). */
	public record Request(Action action, UUID pet, String arg) {
		public Request(Action action, UUID pet) {
			this(action, pet, "");
		}

		public static Request list() {
			return new Request(Action.LIST, new UUID(0, 0));
		}

		void encode(FriendlyByteBuf buf) {
			buf.writeEnum(action);
			buf.writeUUID(pet);
			buf.writeUtf(arg, 64);
		}

		static Request decode(FriendlyByteBuf buf) {
			return new Request(buf.readEnum(Action.class), buf.readUUID(), buf.readUtf(64));
		}

		void handle(Supplier<NetworkEvent.Context> context) {
			ServerPlayer player = context.get().getSender();
			context.get().setPacketHandled(true);

			if (player == null) {
				return;
			}

			if (action == Action.LIST || action == Action.REFRESH) {
				sendList(player, action == Action.LIST, null);
				return;
			}

			PetRegistry.Record record = PetRegistry.get(player.server).find(pet);

			if (record == null || !player.getUUID().equals(PetRegistry.get(player.server).ownerOf(pet))) {
				return;
			}

			Component message = switch (action) {
				case CALL -> Calling.call(player, record);
				case PARTY -> Party.toggle(player, record);
				case STORAGE -> PetStorage.open(player, record);
				case POST -> com.freshorangeslices.bloompets.PetPosts.toggle(player, record);
				case TREE -> PetTrees.pick(player, record, arg);
				case TREE_RESET -> PetTrees.reset(player, record);
				case ABILITY -> PetTrees.toggle(player, record, arg);
				default -> null;
			};

			if (action != Action.STORAGE) {
				sendList(player, false, message);
			} else if (message != null) {
				player.displayClientMessage(message, true);
			}
		}
	}

	/**
	 * One pet as the screen shows it: also its level, its XP into that level and what the next takes (0 at 100), the
	 * ticks left of its rest if it fainted, its skill tree (a {@link com.freshorangeslices.bloompets.PetSkills.Kind}
	 * id, or empty), and its mood (0 to 100) and hunger (0 to 20),
	 * whether it loves you, whether it's tired, whether it keeps a post; One and the Same: whether it can follow a tree,
	 * its tree (or empty), its switched-off abilities (comma-separated), a companion's ticks left before it can pick again;
	 * its stats when it's loaded (attack damage and armor, -1 when away; an Amigo's damage from its levels, -1 for others)
	 * and an Amigo's levels waiting for its next kill.
	 */
	public record Entry(UUID pet, String name, String type, Pets.Kind kind, String category, String dimension, BlockPos pos,
			boolean party, boolean loaded, float health, float maxHealth, int slots, CompoundTag look, int level, float xp, float xpNext,
			int rest, String skills, float mood, float hunger, boolean loved, boolean tired, boolean posted, boolean treeable, String tree,
			String abilitiesOff, int treeWait, float damage, float armor, float amigoPower, int owed) {
		void encode(FriendlyByteBuf buf) {
			buf.writeUUID(pet);
			buf.writeUtf(name);
			buf.writeUtf(type);
			buf.writeEnum(kind);
			buf.writeUtf(category);
			buf.writeUtf(dimension);
			buf.writeBlockPos(pos);
			buf.writeBoolean(party);
			buf.writeBoolean(loaded);
			buf.writeFloat(health);
			buf.writeFloat(maxHealth);
			buf.writeVarInt(slots);
			buf.writeNbt(look);
			buf.writeVarInt(level);
			buf.writeFloat(xp);
			buf.writeFloat(xpNext);
			buf.writeVarInt(rest);
			buf.writeUtf(skills);
			buf.writeFloat(mood);
			buf.writeFloat(hunger);
			buf.writeBoolean(loved);
			buf.writeBoolean(tired);
			buf.writeBoolean(posted);
			buf.writeBoolean(treeable);
			buf.writeUtf(tree);
			buf.writeUtf(abilitiesOff);
			buf.writeVarInt(treeWait);
			buf.writeFloat(damage);
			buf.writeFloat(armor);
			buf.writeFloat(amigoPower);
			buf.writeVarInt(owed);
		}

		static Entry decode(FriendlyByteBuf buf) {
			return new Entry(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readEnum(Pets.Kind.class), buf.readUtf(), buf.readUtf(),
					buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readNbt(),
					buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readUtf(), buf.readFloat(), buf.readFloat(),
					buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readVarInt(),
					buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
		}
	}

	/**
	 * Server to client: the player's pets, how long until their party can change, whether they have One and the Same, and
	 * whether a screen this opens starts on its Bloom view.
	 */
	public record PetList(boolean open, int partyWait, List<Entry> pets, Component message, boolean oneAndTheSame, boolean bloomView) {
		static PetList of(ServerPlayer player, boolean open, Component message, boolean bloomView) {
			PetRegistry registry = PetRegistry.get(player.server);
			long now = player.server.overworld().getGameTime();
			long last = registry.partyChanged(player.getUUID());
			int wait = last == Long.MIN_VALUE ? 0 : (int) Math.max(0, PetsConfig.partyCooldownTicks() - (now - last));
			List<Entry> pets = new ArrayList<>();

			for (PetRegistry.Record r : registry.pets(player.getUUID())) {
				Entity live = Pets.loaded(player.server, r.pet);
				float health = live instanceof LivingEntity living ? living.getHealth() : r.health;
				float max = live instanceof LivingEntity living ? living.getMaxHealth() : r.maxHealth;
				CompoundTag look = r.look;
				String category = "companion";
				Species species = null;

				if (r.kind == Pets.Kind.BLOOM) {
					var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(r.type));
					species = type == null ? null : com.freshorangeslices.bloompets.SpeciesData.of(type);
					category = species == null ? "utility" : species.category();
				}

				int level = live != null ? PetLevels.level(live) : r.level;
				double xp = live != null ? PetLevels.xp(live) : r.xp;
				boolean combat = r.kind == Pets.Kind.MODDED || species != null && species.combat();
				float next = live != null ? (float) PetLevels.xpNext(live)
						: level >= PetLevels.MAX ? 0.0F : r.xpNext > 0 ? (float) r.xpNext : (float) PetLevels.xpToNext(level, combat);
				int rest = live != null ? Fainting.ticksLeft(live) : (int) Math.max(0, r.faintedUntil - now);
				String skills = species == null || species.skills() == null ? "" : species.skills();
				// Bloom pets and companions alike have mood and hunger (Phase 5a).
				boolean bloom = r.kind == Pets.Kind.BLOOM || r.kind == Pets.Kind.MODDED;
				float mood = !bloom ? -1.0F : (float) (live != null ? PetMood.mood(live) : r.mood);
				float hunger = (float) (live != null ? PetMood.hunger(live) : r.hunger);
				boolean loved = bloom && (live != null ? PetMood.loved(live) : r.loved);
				boolean tired = bloom && (live != null ? PetMood.tired(live) : r.tired);
				boolean treeable = r.kind == Pets.Kind.MODDED || combat;
				String tree = live != null ? (PetTrees.tree(live) == null ? "" : PetTrees.tree(live).id()) : r.tree == null ? "" : r.tree;
				String off = String.join(",", live != null ? PetTrees.abilitiesOff(live) : r.abilitiesOff);
				long treeWaitUntil = live != null ? Pets.data(live).getLong("treeWait") : r.treeWait;
				LivingEntity alive = live instanceof LivingEntity living ? living : null;
				var attack = alive == null ? null : alive.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
				Float power = live == null ? null : com.freshorangeslices.bloompets.Amigos.extraPower(live);
				Integer owed = live == null ? null : com.freshorangeslices.bloompets.Amigos.owedLevel(live);
				Integer amigoLevel = live == null ? null : com.freshorangeslices.bloompets.Amigos.level(live);
				pets.add(new Entry(r.pet, r.name, r.type, r.kind, category, r.dimension, r.pos, r.party, live != null, health, max,
						r.slots, look == null ? new CompoundTag() : look, level, (float) xp, next, rest, skills, mood, hunger, loved, tired,
						live != null ? com.freshorangeslices.bloompets.PetPosts.posted(live) : r.post != null, treeable, tree, off,
						(int) Math.min(Integer.MAX_VALUE, Math.max(0, treeWaitUntil - now)),
						attack == null ? -1.0F : (float) attack.getValue(), alive == null ? -1.0F : alive.getArmorValue(),
						power == null ? -1.0F : power, owed == null || amigoLevel == null ? 0 : Math.max(0, owed - amigoLevel)));
			}

			return new PetList(open, wait, pets, message, com.freshorangeslices.bloompets.BloomBridge.oneAndTheSame(player), bloomView);
		}

		void encode(FriendlyByteBuf buf) {
			buf.writeBoolean(open);
			buf.writeVarInt(partyWait);
			buf.writeVarInt(pets.size());
			pets.forEach(e -> e.encode(buf));
			buf.writeBoolean(message != null);

			if (message != null) {
				buf.writeComponent(message);
			}

			buf.writeBoolean(oneAndTheSame);
			buf.writeBoolean(bloomView);
		}

		static PetList decode(FriendlyByteBuf buf) {
			boolean open = buf.readBoolean();
			int wait = buf.readVarInt();
			int count = buf.readVarInt();
			List<Entry> pets = new ArrayList<>(count);

			for (int i = 0; i < count; i++) {
				pets.add(Entry.decode(buf));
			}

			Component message = buf.readBoolean() ? buf.readComponent() : null;
			boolean oneAndTheSame = buf.readBoolean();
			return new PetList(open, wait, pets, message, oneAndTheSame, buf.readBoolean());
		}

		void handle(Supplier<NetworkEvent.Context> context) {
			context.get().setPacketHandled(true);
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.freshorangeslices.bloompets.client.PetsClient.receive(this));
		}
	}
}
