package com.freshorangeslices.bloomlevelup.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Every packet the mod sends. Server to client carries snapshots (definitions + config, the player's profile)
 * and the outcome of a request; client to server only ever carries a request, which the server validates.
 * Each one writes itself and has a {@code read}; {@link BloomNetwork} registers them on the mod's channel.
 */
public final class BloomPayloads {
	/** Upper bound for JSON snapshots; definitions for a large modpack stay far below this. */
	static final int MAX_JSON = 1 << 20;
	static final int MAX_ID = 256;

	private BloomPayloads() {
	}

	/** Server → client: every category, stat and Major currently loaded, plus the server config. */
	public record SyncDefinitions(String json) {
		public static SyncDefinitions read(FriendlyByteBuf buf) {
			return new SyncDefinitions(buf.readUtf(MAX_JSON));
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeUtf(json, MAX_JSON);
		}
	}

	/** Server → client: the player's own profile. */
	public record SyncProfile(String json) {
		public static SyncProfile read(FriendlyByteBuf buf) {
			return new SyncProfile(buf.readUtf(MAX_JSON));
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeUtf(json, MAX_JSON);
		}
	}

	/** What the hub asks the server to do. */
	public enum Action {
		ALLOCATE_NATURAL,
		ALLOCATE_INVESTED,
		TOGGLE_STAT,
		/** Switches only a stat's XP-invested (post-100) points off or on. */
		TOGGLE_INVESTED,
		BUY_MAJOR,
		BUY_MAJOR_SWAP,
		REFUND_MAJOR,
		TOGGLE_MAJOR,
		FUND,
		INVEST,
		/** Copycat: target = the weapon Major to copy, or empty to clear. */
		SET_COPY_SOURCE,
		/** Refunds a Major and every owned Major that depends on it (after the hub's confirm prompt). */
		REFUND_MAJOR_ALL,
		/** A tab's reset button: refunds every Major in the category {@code target} (cools down for 20 minutes). */
		RESET_MAJORS,
		/** The Bloom+ button: the category {@code target} back to level 0 for prestige Major Levels (after two prompts). */
		BLOOM_RESET,
		/** Switches to the other build (the second preset). */
		SWITCH_BUILD
	}

	/** Client → server: one request from the hub. The server never trusts it beyond "the player asked for this". */
	public record Request(Action action, String target, int amount) {
		public static Request read(FriendlyByteBuf buf) {
			return new Request(buf.readEnum(Action.class), buf.readUtf(MAX_ID), buf.readVarInt());
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeEnum(action);
			buf.writeUtf(target, MAX_ID);
			buf.writeVarInt(amount);
		}
	}

	/** Server → client: how a request turned out (the profile sync carries the new state). */
	public record Result(Action action, String target, String outcome, List<String> involved) {
		public static Result read(FriendlyByteBuf buf) {
			return new Result(buf.readEnum(Action.class), buf.readUtf(MAX_ID), buf.readUtf(MAX_ID), readList(buf));
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeEnum(action);
			buf.writeUtf(target, MAX_ID);
			buf.writeUtf(outcome, MAX_ID);
			buf.writeVarInt(involved.size());

			for (String id : involved) {
				buf.writeUtf(id, MAX_ID);
			}
		}

		private static List<String> readList(FriendlyByteBuf buf) {
			int size = Math.min(buf.readVarInt(), 512);
			List<String> list = new ArrayList<>(size);

			for (int i = 0; i < size; i++) {
				list.add(buf.readUtf(MAX_ID));
			}

			return list;
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
		OPEN_RUNES,
		/** Master of Arms (Charge + Catch!): a charged throw (harder, faster, pierces). */
		CHARGED_THROW,
		/** Master of Arms (Charge + Lightning Summon): several bolts around the aim point. */
		STORM_CALL
	}

	/** Client → server: a combat ability key. The server checks the Major, the weapon and cooldowns itself. */
	public record CombatInput(CombatAction action) {
		public static CombatInput read(FriendlyByteBuf buf) {
			return new CombatInput(buf.readEnum(CombatAction.class));
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeEnum(action);
		}
	}

	/**
	 * Client → server: whether the player is pressing a movement key (walk, strafe or jump), sent when it changes.
	 * 1.20.1 servers don't see a walking player's keys; Brace! ends on your own movement only.
	 */
	public record MovementInput(boolean moving) {
		public static MovementInput read(FriendlyByteBuf buf) {
			return new MovementInput(buf.readBoolean());
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeBoolean(moving);
		}
	}

	/**
	 * Server → client: one of the stacking combat meters for the HUD (Bloodlust, Predator, Combo): stacks, the
	 * bonus the next hit gets, and how long until they run out (0 stacks = hide the bar).
	 */
	public record Meter(int kind, int stacks, float bonus, int ticksLeft, int totalTicks) {
		public static final int BLOODLUST = 0;
		public static final int PREDATOR = 1;
		public static final int COMBO = 2;
		public static final int KINDS = 3;
		public static Meter read(FriendlyByteBuf buf) {
			return new Meter(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt());
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeVarInt(kind);
			buf.writeVarInt(stacks);
			buf.writeFloat(bonus);
			buf.writeVarInt(ticksLeft);
			buf.writeVarInt(totalTicks);
		}
	}

	/** Server → client: one player's nameplate tag, {@code [lvl N]} with N = spent Major Levels, green once mastered. */
	public record NameTag(UUID player, int spent, boolean mastered) {
		public static NameTag read(FriendlyByteBuf buf) {
			return new NameTag(buf.readUUID(), buf.readVarInt(), buf.readBoolean());
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeUUID(player);
			buf.writeVarInt(spent);
			buf.writeBoolean(mastered);
		}
	}
}
