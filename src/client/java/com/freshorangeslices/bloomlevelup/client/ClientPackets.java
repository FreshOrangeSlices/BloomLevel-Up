package com.freshorangeslices.bloomlevelup.client;

import java.util.function.Consumer;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.network.SnapshotCodecs;

/**
 * Client end of {@code BloomNetwork}: what each server message does. Only ever called on the physical client,
 * on the main thread. Messages the HUD and nameplates use go to listeners the client wiring installs.
 */
public final class ClientPackets {
	private static Consumer<BloomPayloads.NameTag> nameTags = tag -> {
	};
	private static Consumer<BloomPayloads.Meter> meters = meter -> {
	};

	private ClientPackets() {
	}

	public static void onNameTag(Consumer<BloomPayloads.NameTag> listener) {
		nameTags = listener;
	}

	public static void onMeter(Consumer<BloomPayloads.Meter> listener) {
		meters = listener;
	}

	public static void definitions(BloomPayloads.SyncDefinitions message) {
		try {
			SnapshotCodecs.DefinitionsSnapshot snapshot = SnapshotCodecs.decodeDefinitions(message.json());
			ClientBloomState.setDefinitions(snapshot.definitions(), snapshot.config());
		} catch (RuntimeException e) {
			BloomLevelUp.LOGGER.error("Couldn't read Bloom definitions from the server", e);
		}
	}

	public static void profile(BloomPayloads.SyncProfile message) {
		try {
			ClientBloomState.setProfile(SnapshotCodecs.decodeProfile(message.json()));
		} catch (RuntimeException e) {
			BloomLevelUp.LOGGER.error("Couldn't read Bloom profile from the server", e);
		}
	}

	public static void result(BloomPayloads.Result message) {
		ClientBloomState.setLastResult(message);
	}

	public static void nameTag(BloomPayloads.NameTag message) {
		nameTags.accept(message);
	}

	public static void meter(BloomPayloads.Meter message) {
		meters.accept(message);
	}

	public static void sheet(SheetPayloads.View message) {
		ClientBloomState.setSheet(message);
	}

	/** Whether the server we're connected to has Bloom's channel. */
	public static boolean serverHasBloom() {
		net.minecraft.client.multiplayer.ClientPacketListener connection = net.minecraft.client.Minecraft.getInstance().getConnection();
		return connection != null && com.freshorangeslices.bloomlevelup.network.BloomNetwork.CHANNEL.isRemotePresent(connection.getConnection());
	}

	/** Leaving a server forgets everything it sent. */
	public static void disconnected() {
		ClientBloomState.clear();
	}
}
