package com.freshorangeslices.bloompets.network;

import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.Calling;
import com.freshorangeslices.bloompets.Party;
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
	private static final String VERSION = "1";
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

	/** What the player can do with a pet from the screen. */
	public enum Action {
		LIST, CALL, PARTY, STORAGE
	}

	/** Sends the player their pets; {@code open} opens the screen, else it only refreshes an open one. */
	public static void sendList(ServerPlayer player, boolean open, Component message) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), PetList.of(player, open, message));
	}

	/** Client to server: list my pets, or act on one of them. */
	public record Request(Action action, UUID pet) {
		public static Request list() {
			return new Request(Action.LIST, new UUID(0, 0));
		}

		void encode(FriendlyByteBuf buf) {
			buf.writeEnum(action);
			buf.writeUUID(pet);
		}

		static Request decode(FriendlyByteBuf buf) {
			return new Request(buf.readEnum(Action.class), buf.readUUID());
		}

		void handle(Supplier<NetworkEvent.Context> context) {
			ServerPlayer player = context.get().getSender();
			context.get().setPacketHandled(true);

			if (player == null) {
				return;
			}

			if (action == Action.LIST) {
				sendList(player, true, null);
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
				default -> null;
			};

			if (action != Action.STORAGE) {
				sendList(player, false, message);
			} else if (message != null) {
				player.displayClientMessage(message, true);
			}
		}
	}

	/** One pet as the screen shows it. */
	public record Entry(UUID pet, String name, String type, Pets.Kind kind, String category, String dimension, BlockPos pos,
			boolean party, boolean loaded, float health, float maxHealth, int slots, CompoundTag look) {
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
		}

		static Entry decode(FriendlyByteBuf buf) {
			return new Entry(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readEnum(Pets.Kind.class), buf.readUtf(), buf.readUtf(),
					buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readNbt());
		}
	}

	/** Server to client: the player's pets, and how long until their party can change. */
	public record PetList(boolean open, int partyWait, List<Entry> pets, Component message) {
		static PetList of(ServerPlayer player, boolean open, Component message) {
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

				if (r.kind == Pets.Kind.BLOOM) {
					var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(r.type));
					Species species = type == null ? null : com.freshorangeslices.bloompets.SpeciesData.of(type);
					category = species == null ? "utility" : species.category();
				}

				pets.add(new Entry(r.pet, r.name, r.type, r.kind, category, r.dimension, r.pos, r.party, live != null, health, max,
						r.slots, look == null ? new CompoundTag() : look));
			}

			return new PetList(open, wait, pets, message);
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
		}

		static PetList decode(FriendlyByteBuf buf) {
			boolean open = buf.readBoolean();
			int wait = buf.readVarInt();
			int count = buf.readVarInt();
			List<Entry> pets = new ArrayList<>(count);

			for (int i = 0; i < count; i++) {
				pets.add(Entry.decode(buf));
			}

			return new PetList(open, wait, pets, buf.readBoolean() ? buf.readComponent() : null);
		}

		void handle(Supplier<NetworkEvent.Context> context) {
			context.get().setPacketHandled(true);
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.freshorangeslices.bloompets.client.PetsClient.receive(this));
		}
	}
}
