package com.freshorangeslices.legionwarex.armor;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
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

/** Hidden armor's messages: a player switching a piece of their own, and an entity's mask for the clients that see it. */
public final class ArmorNetwork {
	private static final String VERSION = "1";
	/** Lenient: a client or server without the add-on still connects (it just doesn't hide anything). */
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(LegionWareX.MOD_ID, "armor"),
			() -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

	private ArmorNetwork() {
	}

	public static void register() {
		CHANNEL.messageBuilder(Toggle.class, 0, NetworkDirection.PLAY_TO_SERVER)
				.encoder(Toggle::encode).decoder(Toggle::decode).consumerMainThread(Toggle::handle).add();
		CHANNEL.messageBuilder(Mask.class, 1, NetworkDirection.PLAY_TO_CLIENT)
				.encoder(Mask::encode).decoder(Mask::decode).consumerMainThread(Mask::handle).add();
	}

	static void sendMask(LivingEntity entity, int mask) {
		CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), new Mask(entity.getId(), mask));
	}

	static void sendTo(ServerPlayer player, Entity entity, int mask) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Mask(entity.getId(), mask));
	}

	/** Client to server: one of my armor pieces (0 boots to 3 helmet) hidden or shown again. */
	public record Toggle(int index) {
		void encode(FriendlyByteBuf buf) {
			buf.writeByte(index);
		}

		static Toggle decode(FriendlyByteBuf buf) {
			return new Toggle(buf.readByte());
		}

		void handle(Supplier<NetworkEvent.Context> context) {
			ServerPlayer player = context.get().getSender();
			context.get().setPacketHandled(true);

			if (player != null) {
				HiddenArmor.toggle(player, index);
			}
		}
	}

	/** Server to client: which of this entity's armor pieces are hidden. */
	public record Mask(int entity, int mask) {
		void encode(FriendlyByteBuf buf) {
			buf.writeVarInt(entity);
			buf.writeByte(mask);
		}

		static Mask decode(FriendlyByteBuf buf) {
			return new Mask(buf.readVarInt(), buf.readByte());
		}

		void handle(Supplier<NetworkEvent.Context> context) {
			context.get().setPacketHandled(true);
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.freshorangeslices.legionwarex.armor.client.ArmorClient.receive(entity, mask));
		}
	}
}
