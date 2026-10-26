package com.freshorangeslices.bloomlevelup.network;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.client.ClientPackets;

/**
 * The mod's one network channel (Forge's stand-in for Fabric's payload types). Server to client: snapshots and
 * results, handled by {@link ClientPackets} on the client only. Client to server: requests, which the server
 * validates. Both sides must run the same Bloom protocol.
 */
public final class BloomNetwork {
	/** Bump when any message's shape changes. */
	private static final String PROTOCOL = "2";

	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(BloomLevelUp.id("main"), () -> PROTOCOL, PROTOCOL::equals,
			PROTOCOL::equals);

	private static int nextId;

	private BloomNetwork() {
	}

	public static void init() {
		toClient(BloomPayloads.SyncDefinitions.class, BloomPayloads.SyncDefinitions::write, BloomPayloads.SyncDefinitions::read,
				message -> ClientPackets.definitions(message));
		toClient(BloomPayloads.SyncProfile.class, BloomPayloads.SyncProfile::write, BloomPayloads.SyncProfile::read,
				message -> ClientPackets.profile(message));
		toClient(BloomPayloads.Result.class, BloomPayloads.Result::write, BloomPayloads.Result::read, message -> ClientPackets.result(message));
		toClient(BloomPayloads.NameTag.class, BloomPayloads.NameTag::write, BloomPayloads.NameTag::read, message -> ClientPackets.nameTag(message));
		toClient(BloomPayloads.Meter.class, BloomPayloads.Meter::write, BloomPayloads.Meter::read, message -> ClientPackets.meter(message));
		toClient(SheetPayloads.View.class, SheetPayloads.View::write, SheetPayloads.View::read, message -> ClientPackets.sheet(message));

		toServer(BloomPayloads.Request.class, BloomPayloads.Request::write, BloomPayloads.Request::read, BloomSync::handle);
		toServer(BloomPayloads.CombatInput.class, BloomPayloads.CombatInput::write, BloomPayloads.CombatInput::read,
				(player, message) -> com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities.handle(player, message.action()));
		toServer(BloomPayloads.MovementInput.class, BloomPayloads.MovementInput::write, BloomPayloads.MovementInput::read,
				(player, message) -> com.freshorangeslices.bloomlevelup.skill.armor.ArmorSkill.movementInput(player, message.moving()));
	}

	/** A server → client message. {@code handler} runs on the client's main thread, and only on a physical client. */
	private static <M> void toClient(Class<M> type, BiConsumer<M, FriendlyByteBuf> writer, Function<FriendlyByteBuf, M> reader,
			java.util.function.Consumer<M> handler) {
		CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(writer)
				.decoder(reader)
				.consumerMainThread((M message, Supplier<NetworkEvent.Context> context) ->
						DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handler.accept(message)))
				.add();
	}

	/** A client → server message, handled on the server thread with the sending player. */
	private static <M> void toServer(Class<M> type, BiConsumer<M, FriendlyByteBuf> writer, Function<FriendlyByteBuf, M> reader,
			BiConsumer<ServerPlayer, M> handler) {
		CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
				.encoder(writer)
				.decoder(reader)
				.consumerMainThread((M message, Supplier<NetworkEvent.Context> context) -> {
					ServerPlayer sender = context.get().getSender();

					if (sender != null) {
						handler.accept(sender, message);
					}
				})
				.add();
	}

	public static void send(ServerPlayer player, Object message) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
	}

	/** Client side: a request to the server. */
	public static void sendToServer(Object message) {
		CHANNEL.sendToServer(message);
	}
}
