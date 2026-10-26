package com.freshorangeslices.bloomlevelup.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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
	public record Request(int id, String category, int slot, int target, List<String> toggles, int compareSlot, boolean compareBuild) {
		/** {@code compareSlot} when nothing is pinned. */
		public static final int NO_COMPARE = -100;

		/** A plain request: no pinned item, no other build. */
		public Request(int id, String category, int slot, int target, List<String> toggles) {
			this(id, category, slot, target, toggles, NO_COMPARE, false);
		}

		public static Request read(FriendlyByteBuf buf) {
			return new Request(buf.readVarInt(), buf.readUtf(BloomPayloads.MAX_ID), buf.readInt(), buf.readVarInt(),
					readList(buf, MAX_TOGGLES, b -> b.readUtf(BloomPayloads.MAX_ID)), buf.readInt(), buf.readBoolean());
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeUtf(category, BloomPayloads.MAX_ID);
			buf.writeInt(slot);
			buf.writeVarInt(target);
			buf.writeVarInt(Math.min(toggles.size(), MAX_TOGGLES));

			for (int i = 0; i < Math.min(toggles.size(), MAX_TOGGLES); i++) {
				buf.writeUtf(toggles.get(i), BloomPayloads.MAX_ID);
			}

			buf.writeInt(compareSlot);
			buf.writeBoolean(compareBuild);
		}
	}

	/** One line of a sheet: a heading, a "label ... value" row, or a grey note under the row before it. */
	public record Row(int kind, Component label, Component value) {
		public static final int HEADING = 0;
		public static final int ROW = 1;
		public static final int NOTE = 2;
		/** A row that sums up the rows above it (drawn with a line over it). */
		public static final int TOTAL = 3;

		static Row read(FriendlyByteBuf buf) {
			return new Row(buf.readVarInt(), buf.readComponent(), buf.readComponent());
		}

		void write(FriendlyByteBuf buf) {
			buf.writeVarInt(kind);
			buf.writeComponent(label);
			buf.writeComponent(value);
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

		static Toggle read(FriendlyByteBuf buf) {
			return new Toggle(buf.readUtf(BloomPayloads.MAX_ID), buf.readComponent(),
					buf.readComponent(), buf.readVarInt(), buf.readComponent());
		}

		void write(FriendlyByteBuf buf) {
			buf.writeUtf(id, BloomPayloads.MAX_ID);
			buf.writeComponent(name);
			buf.writeComponent(tooltip);
			buf.writeVarInt(mode);
			buf.writeComponent(live);
		}
	}

	/**
	 * Server → client: a finished sheet. {@code weapon} is the item it was worked out for, {@code targets} the
	 * picker's choices ({@code target} the chosen one), {@code gauge} the vanilla item it compares to (with a
	 * caption and a badge such as "×1.4"), then the short summary and the full breakdown.
	 */
	public record View(int id, String category, int slot, ItemStack weapon, List<Component> targets, int target, List<Toggle> toggles,
			ItemStack gauge, Component gaugeCaption, Component gaugeBadge, List<Row> summary, List<Row> details) {

		public static View read(FriendlyByteBuf buf) {
			return new View(buf.readVarInt(), buf.readUtf(BloomPayloads.MAX_ID), buf.readVarInt(), buf.readItem(),
					readList(buf, MAX_TOGGLES, FriendlyByteBuf::readComponent), buf.readVarInt(), readList(buf, MAX_TOGGLES, Toggle::read),
					buf.readItem(), buf.readComponent(),
					buf.readComponent(), readList(buf, MAX_ROWS, Row::read), readList(buf, MAX_ROWS, Row::read));
		}

		public void write(FriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeUtf(category, BloomPayloads.MAX_ID);
			buf.writeVarInt(slot);
			buf.writeItem(weapon);
			writeList(buf, targets, MAX_TOGGLES, (b, c) -> b.writeComponent(c));
			buf.writeVarInt(target);
			writeList(buf, toggles, MAX_TOGGLES, (b, t) -> t.write(b));
			buf.writeItem(gauge);
			buf.writeComponent(gaugeCaption);
			buf.writeComponent(gaugeBadge);
			writeList(buf, summary, MAX_ROWS, (b, r) -> r.write(b));
			writeList(buf, details, MAX_ROWS, (b, r) -> r.write(b));
		}
	}

	private static <T> List<T> readList(FriendlyByteBuf buf, int max, Function<FriendlyByteBuf, T> reader) {
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

	private static <T> void writeList(FriendlyByteBuf buf, List<T> list, int max, java.util.function.BiConsumer<FriendlyByteBuf, T> writer) {
		int size = Math.min(list.size(), max);
		buf.writeVarInt(size);

		for (int i = 0; i < size; i++) {
			writer.accept(buf, list.get(i));
		}
	}
}
