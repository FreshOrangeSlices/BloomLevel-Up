package com.freshorangeslices.bloomlevelup.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * The stat sheet's packets. The client only says which tab, which inventory slot, which target and which
 * "show it at max" switches it wants; the server works every number out with the real game rules and sends back
 * finished lines of text.
 */
public final class SheetPayloads {
	static final int MAX_ROWS = 256;
	static final int MAX_TOGGLES = 32;

	private SheetPayloads() {
	}

	/**
	 * Client → server: the stat sheet for {@code category}, for the item in inventory {@code slot} (-1 = main hand,
	 * -2 = bare hands). {@code toggles}: "id=max" or "id=off" for each switch not left on Live.
	 */
	public record Request(int id, String category, int slot, int target, List<String> toggles) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(BloomLevelUp.id("sheet_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = CustomPacketPayload.codec(Request::write, Request::new);

		private Request(RegistryFriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readUtf(BloomPayloads.MAX_ID), buf.readVarInt(), buf.readVarInt(),
					readList(buf, MAX_TOGGLES, b -> b.readUtf(BloomPayloads.MAX_ID)));
		}

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeUtf(category, BloomPayloads.MAX_ID);
			buf.writeVarInt(slot);
			buf.writeVarInt(target);
			buf.writeVarInt(Math.min(toggles.size(), MAX_TOGGLES));

			for (int i = 0; i < Math.min(toggles.size(), MAX_TOGGLES); i++) {
				buf.writeUtf(toggles.get(i), BloomPayloads.MAX_ID);
			}
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** One line of a sheet: a heading, a "label ... value" row, or a grey note under the row before it. */
	public record Row(int kind, Component label, Component value) {
		public static final int HEADING = 0;
		public static final int ROW = 1;
		public static final int NOTE = 2;
		/** A row that sums up the rows above it (drawn with a line over it). */
		public static final int TOTAL = 3;

		static Row read(RegistryFriendlyByteBuf buf) {
			return new Row(buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf));
		}

		void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(kind);
			ComponentSerialization.STREAM_CODEC.encode(buf, label);
			ComponentSerialization.STREAM_CODEC.encode(buf, value);
		}
	}

	/**
	 * A switch for one effect the player owns: {@code mode} is Live (what you have right now, {@code live} says
	 * what that is), Max or Off.
	 */
	public record Toggle(String id, Component name, Component tooltip, int mode, Component live) {
		public static final int LIVE = 0;
		public static final int MAX = 1;
		public static final int OFF = 2;

		static Toggle read(RegistryFriendlyByteBuf buf) {
			return new Toggle(buf.readUtf(BloomPayloads.MAX_ID), ComponentSerialization.STREAM_CODEC.decode(buf),
					ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf));
		}

		void write(RegistryFriendlyByteBuf buf) {
			buf.writeUtf(id, BloomPayloads.MAX_ID);
			ComponentSerialization.STREAM_CODEC.encode(buf, name);
			ComponentSerialization.STREAM_CODEC.encode(buf, tooltip);
			buf.writeVarInt(mode);
			ComponentSerialization.STREAM_CODEC.encode(buf, live);
		}
	}

	/**
	 * Server → client: a finished sheet. {@code weapon} is the item it was worked out for, {@code targets} the
	 * picker's choices ({@code target} the chosen one), {@code gauge} the vanilla item it compares to (with a
	 * caption and a badge such as "×1.4"), then the short summary and the full breakdown.
	 */
	public record View(int id, String category, int slot, ItemStack weapon, List<Component> targets, int target, List<Toggle> toggles,
			ItemStack gauge, Component gaugeCaption, Component gaugeBadge, List<Row> summary, List<Row> details) implements CustomPacketPayload {
		public static final Type<View> TYPE = new Type<>(BloomLevelUp.id("sheet_view"));
		public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = CustomPacketPayload.codec(View::write, View::new);

		private View(RegistryFriendlyByteBuf buf) {
			this(buf.readVarInt(), buf.readUtf(BloomPayloads.MAX_ID), buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
					readList(buf, MAX_TOGGLES, ComponentSerialization.STREAM_CODEC::decode), buf.readVarInt(), readList(buf, MAX_TOGGLES, Toggle::read),
					ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
					ComponentSerialization.STREAM_CODEC.decode(buf), readList(buf, MAX_ROWS, Row::read), readList(buf, MAX_ROWS, Row::read));
		}

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeUtf(category, BloomPayloads.MAX_ID);
			buf.writeVarInt(slot);
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, weapon);
			writeList(buf, targets, MAX_TOGGLES, (b, c) -> ComponentSerialization.STREAM_CODEC.encode(b, c));
			buf.writeVarInt(target);
			writeList(buf, toggles, MAX_TOGGLES, (b, t) -> t.write(b));
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, gauge);
			ComponentSerialization.STREAM_CODEC.encode(buf, gaugeCaption);
			ComponentSerialization.STREAM_CODEC.encode(buf, gaugeBadge);
			writeList(buf, summary, MAX_ROWS, (b, r) -> r.write(b));
			writeList(buf, details, MAX_ROWS, (b, r) -> r.write(b));
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private static <T> List<T> readList(RegistryFriendlyByteBuf buf, int max, Function<RegistryFriendlyByteBuf, T> reader) {
		int size = buf.readVarInt();

		if (size < 0 || size > max) {
			throw new IllegalArgumentException("Bloom stat sheet: list of " + size + " (max " + max + ")");
		}

		List<T> out = new ArrayList<>(size);

		for (int i = 0; i < size; i++) {
			out.add(reader.apply(buf));
		}

		return out;
	}

	private static <T> void writeList(RegistryFriendlyByteBuf buf, List<T> list, int max, java.util.function.BiConsumer<RegistryFriendlyByteBuf, T> writer) {
		int size = Math.min(list.size(), max);
		buf.writeVarInt(size);

		for (int i = 0; i < size; i++) {
			writer.accept(buf, list.get(i));
		}
	}
}
