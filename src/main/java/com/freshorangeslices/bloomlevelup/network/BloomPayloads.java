package com.freshorangeslices.bloomlevelup.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * Every packet the mod sends. Server to client carries snapshots (definitions + config, the player's profile)
 * and the outcome of a request; client to server only ever carries a request, which the server validates.
 */
public final class BloomPayloads {
	/** Upper bound for JSON snapshots; definitions for a large modpack stay far below this. */
	static final int MAX_JSON = 1 << 20;
	static final int MAX_ID = 256;

	private BloomPayloads() {
	}

	/** Server → client: every category, stat and Major currently loaded, plus the server config. */
	public record SyncDefinitions(String json) implements CustomPacketPayload {
		public static final Type<SyncDefinitions> TYPE = new Type<>(BloomLevelUp.id("sync_definitions"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SyncDefinitions> CODEC =
				CustomPacketPayload.codec(SyncDefinitions::write, SyncDefinitions::new);

		private SyncDefinitions(RegistryFriendlyByteBuf buf) {
			this(buf.readUtf(MAX_JSON));
		}

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeUtf(json, MAX_JSON);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server → client: the player's own profile. */
	public record SyncProfile(String json) implements CustomPacketPayload {
		public static final Type<SyncProfile> TYPE = new Type<>(BloomLevelUp.id("sync_profile"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SyncProfile> CODEC =
				CustomPacketPayload.codec(SyncProfile::write, SyncProfile::new);

		private SyncProfile(RegistryFriendlyByteBuf buf) {
			this(buf.readUtf(MAX_JSON));
		}

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeUtf(json, MAX_JSON);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** What the hub asks the server to do. */
	public enum Action {
		ALLOCATE_NATURAL,
		ALLOCATE_INVESTED,
		TOGGLE_STAT,
		BUY_MAJOR,
		BUY_MAJOR_SWAP,
		REFUND_MAJOR,
		TOGGLE_MAJOR,
		FUND,
		INVEST,
		/** Copycat: target = the weapon Major to copy, or empty to clear. */
		SET_COPY_SOURCE
	}

	/** Client → server: one request from the hub. The server never trusts it beyond "the player asked for this". */
	public record Request(Action action, String target, int amount) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(BloomLevelUp.id("request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
				CustomPacketPayload.codec(Request::write, Request::new);

		private Request(RegistryFriendlyByteBuf buf) {
			this(buf.readEnum(Action.class), buf.readUtf(MAX_ID), buf.readVarInt());
		}

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeEnum(action);
			buf.writeUtf(target, MAX_ID);
			buf.writeVarInt(amount);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server → client: how a request turned out (the profile sync carries the new state). */
	public record Result(Action action, String target, String outcome, List<String> involved) implements CustomPacketPayload {
		public static final Type<Result> TYPE = new Type<>(BloomLevelUp.id("result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC =
				CustomPacketPayload.codec(Result::write, Result::new);

		private Result(RegistryFriendlyByteBuf buf) {
			this(buf.readEnum(Action.class), buf.readUtf(MAX_ID), buf.readUtf(MAX_ID), readList(buf));
		}

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeEnum(action);
			buf.writeUtf(target, MAX_ID);
			buf.writeUtf(outcome, MAX_ID);
			buf.writeVarInt(involved.size());

			for (String id : involved) {
				buf.writeUtf(id, MAX_ID);
			}
		}

		private static List<String> readList(RegistryFriendlyByteBuf buf) {
			int size = Math.min(buf.readVarInt(), 512);
			List<String> list = new ArrayList<>(size);

			for (int i = 0; i < size; i++) {
				list.add(buf.readUtf(MAX_ID));
			}

			return list;
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Combat abilities that need a key: what the player just did. */
	public enum CombatAction {
		/** Charge Attack / Heavy Hands: the charge is complete; the next full swing deals double damage. */
		CHARGED,
		/** Lightning Summon: call lightning where the player is looking. */
		SUMMON_LIGHTNING,
		/** Catch!: throw the weapon in the main hand. */
		THROW,
		/** Misc / Hoarder's Pocket: open the pocket (the server checks the Major). */
		OPEN_POCKET,
		/** Misc / Rune Embedding: open the rune slots (the server checks the Major). */
		OPEN_RUNES
	}

	/** Client → server: a combat ability key. The server checks the Major, the weapon and cooldowns itself. */
	public record CombatInput(CombatAction action) implements CustomPacketPayload {
		public static final Type<CombatInput> TYPE = new Type<>(BloomLevelUp.id("combat_input"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CombatInput> CODEC =
				CustomPacketPayload.codec(CombatInput::write, CombatInput::new);

		private CombatInput(RegistryFriendlyByteBuf buf) {
			this(buf.readEnum(CombatAction.class));
		}

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeEnum(action);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server → client: one player's nameplate tag, {@code [lvl N]} with N = spent Major Levels, green once mastered. */
	public record NameTag(UUID player, int spent, boolean mastered) implements CustomPacketPayload {
		public static final Type<NameTag> TYPE = new Type<>(BloomLevelUp.id("name_tag"));
		public static final StreamCodec<RegistryFriendlyByteBuf, NameTag> CODEC =
				CustomPacketPayload.codec(NameTag::write, NameTag::new);

		private NameTag(RegistryFriendlyByteBuf buf) {
			this(buf.readUUID(), buf.readVarInt(), buf.readBoolean());
		}

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeUUID(player);
			buf.writeVarInt(spent);
			buf.writeBoolean(mastered);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
