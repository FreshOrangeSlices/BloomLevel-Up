package com.freshorangeslices.bloomlevelup.sheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.network.BloomNetwork;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads.Row;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;

/**
 * The stat sheet (the hub's Stats button): the client asks for a tab, an inventory slot, a target and how each
 * switch is set; the server works everything out from the real game rules and answers with finished lines.
 * The client never computes a number itself. It can also ask to compare against a pinned item or the other
 * build (each line then carries the difference).
 */
public final class StatSheets {
	/** Requests a player may send per second (the hub asks once a second, and again on every click). */
	private static final int PER_SECOND = 20;
	/** Lines where a smaller number is the better one (health lost, waiting times). */
	private static final Set<String> LOWER_IS_BETTER = Set.of("bloom.sheet.armor.step.health", "bloom.sheet.armor.lost", "bloom.sheet.combat.cooldown",
			"bloom.sheet.combat.load_time", "bloom.sheet.combat.draw_time", "bloom.sheet.utility.break_time");
	private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
	private static final Map<UUID, long[]> BUDGET = new HashMap<>();

	private StatSheets() {
	}

	/** The packets are registered with the rest in {@link BloomNetwork}; this only forgets state. */
	public static void init() {
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> BUDGET.remove(event.getEntity().getUUID()));
		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
			BUDGET.clear();
			Dummies.clear();
		});
	}

	/** A request from a client (on the server thread). */
	public static void handle(ServerPlayer player, SheetPayloads.Request request) {
		if (allowed(player)) {
			BloomNetwork.send(player, build(player, request));
		}
	}

	private static boolean allowed(ServerPlayer player) {
		long second = player.level().getGameTime() / 20L;
		long[] budget = BUDGET.computeIfAbsent(player.getUUID(), key -> new long[] {second, 0L});

		if (budget[0] != second) {
			budget[0] = second;
			budget[1] = 0L;
		}

		return ++budget[1] <= PER_SECOND;
	}

	/**
	 * The sheet for a request (public for game tests). Compared with a pinned item: the chosen item's numbers, each
	 * followed by how far it is from the pinned one's. Compared with the other build: the other build's numbers,
	 * each followed by how far it is from the build in use. Green is better, red worse.
	 */
	public static SheetPayloads.View build(ServerPlayer player, SheetPayloads.Request request) {
		ItemStack item = item(player, request.slot());
		BloomProfile profile = BloomAttachments.profile(player);
		Sheet shown = compute(player, request.category(), item, request.target(), request.toggles());
		Sheet baseline = null;
		Component note = null;

		if (request.compareBuild() && profile.spareBuild().isPresent()) {
			baseline = shown;
			shown = BloomServices.progression().withOtherBuild(profile,
					() -> compute(player, request.category(), item, request.target(), request.toggles()));
			int inUse = profile.activeBuild() + 1;
			note = Component.translatable("bloom.sheet.ui.build.note", String.valueOf(3 - inUse), String.valueOf(inUse));
		} else if (request.compareSlot() != SheetPayloads.Request.NO_COMPARE && request.compareSlot() != request.slot()) {
			ItemStack pinned = item(player, request.compareSlot());
			baseline = compute(player, request.category(), pinned, request.target(), request.toggles());
			note = Component.translatable("bloom.sheet.ui.pin.note", pinned.isEmpty() ? Component.translatable("bloom.sheet.ui.bare") : pinned.getHoverName());
		}

		List<Row> summary = baseline == null ? shown.summary : diff(shown.summary, baseline.summary);
		List<Row> details = baseline == null ? shown.details : diff(shown.details, baseline.details);

		if (note != null) {
			summary = new ArrayList<>(summary);
			summary.add(0, new Row(Row.NOTE, note, Component.empty()));
			details = new ArrayList<>(details);
			details.add(0, new Row(Row.NOTE, note, Component.empty()));
		}

		return new SheetPayloads.View(request.id(), request.category(), request.slot(), item.copy(), shown.targets, shown.target, shown.toggles,
				shown.gauge, shown.gaugeCaption, shown.gaugeBadge, summary, details);
	}

	private static Sheet compute(ServerPlayer player, String category, ItemStack item, int target, List<String> toggles) {
		Sheet sheet = new Sheet(toggles, target);

		try {
			if (category.equals(SkillXp.COMBAT)) {
				CombatSheet.build(player, item, sheet);
			} else if (category.equals(SkillXp.ARMOR)) {
				ArmorSheet.build(player, sheet);
			} else if (category.equals(SkillXp.UTILITY)) {
				UtilitySheet.build(player, item, sheet);
			} else if (category.equals(SkillXp.MISC)) {
				MiscSheet.build(player, sheet);
			} else {
				sheet.summary(Sheet.word("value.none"), "unknown");
			}
		} catch (RuntimeException e) {
			BloomLevelUp.LOGGER.error("Bloom stat sheet for {} failed", category, e);
			sheet.summary.clear();
			sheet.details.clear();
			sheet.summary(Sheet.word("value.error"), "error");
		}

		return sheet;
	}

	/** {@code rows}, each number that differs from its line in {@code baseline} followed by "(+0.5)": green if better. */
	private static List<Row> diff(List<Row> rows, List<Row> baseline) {
		List<Row> matched = pair(rows, baseline);
		List<Row> out = new ArrayList<>(rows.size());

		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			Row was = matched.get(i);
			Double now = number(row);
			Double then = was == null ? null : number(was);

			if (now == null || then == null || Math.abs(now - then) < 0.005) {
				out.add(row);
				continue;
			}

			double delta = now - then;
			boolean better = lowerIsBetter(row, now) ? delta < 0.0 : delta > 0.0;
			MutableComponent value = row.value().copy()
					.append(Sheet.colored("  (" + (delta > 0.0 ? "+" : "-") + Sheet.num(Math.abs(delta)) + suffix(row) + ")", better ? Sheet.GOOD : Sheet.BAD));
			out.add(new Row(row.kind(), row.label(), value));
		}

		return out;
	}

	/**
	 * For each of {@code rows}, the line of {@code baseline} it stands for, or null: lines are matched by their
	 * label with the numbers in it left out ("Endurance (12)" is "Endurance (13)" in the other sheet), the n-th of
	 * a name with the n-th. Headings and notes are never matched.
	 */
	private static List<Row> pair(List<Row> rows, List<Row> baseline) {
		Map<String, List<Row>> byShape = new HashMap<>();

		for (Row row : baseline) {
			if (row.kind() != Row.HEADING && row.kind() != Row.NOTE) {
				byShape.computeIfAbsent(shape(row.label()), key -> new ArrayList<>()).add(row);
			}
		}

		Map<String, Integer> seen = new HashMap<>();
		List<Row> out = new ArrayList<>(rows.size());

		for (Row row : rows) {
			if (row.kind() == Row.HEADING || row.kind() == Row.NOTE) {
				out.add(null);
				continue;
			}

			String shape = shape(row.label());
			int index = seen.merge(shape, 1, Integer::sum) - 1;
			List<Row> matches = byShape.get(shape);
			out.add(matches == null || index >= matches.size() ? null : matches.get(index));
		}

		return out;
	}

	/** A label's translation keys and words with every number replaced by "#" (the same on any server language). */
	private static String shape(Component label) {
		return NUMBER.matcher(shapeOf(label)).replaceAll("#");
	}

	private static String shapeOf(Object part) {
		if (!(part instanceof Component component)) {
			return String.valueOf(part);
		}

		StringBuilder out = new StringBuilder();

		if (component.getContents() instanceof TranslatableContents contents) {
			out.append(contents.getKey()).append('(');

			for (Object arg : contents.getArgs()) {
				out.append(shapeOf(arg)).append(',');
			}

			out.append(')');
		} else {
			out.append(component.plainCopy().getString());
		}

		for (Component sibling : component.getSiblings()) {
			out.append(shapeOf(sibling));
		}

		return out.toString();
	}

	/** The first number in a line's value ("7.62  (-24%)" → 7.62, "×1.5" → 1.5, "-30%" → -30), or null. */
	private static Double number(Row row) {
		Matcher matcher = NUMBER.matcher(row.value().getString());
		return matcher.find() ? Double.parseDouble(matcher.group()) : null;
	}

	/** "%" or " s" when the first number in the value carries it. */
	private static String suffix(Row row) {
		String text = row.value().getString();
		Matcher matcher = NUMBER.matcher(text);

		if (!matcher.find()) {
			return "";
		}

		String rest = text.substring(matcher.end());
		return rest.startsWith("%") ? "%" : rest.startsWith(" s") ? " s" : "";
	}

	/**
	 * Whether a smaller number is the better one on this line: health lost, waiting times, and cuts shown as a green
	 * "-24%" (a bigger cut is better).
	 */
	private static boolean lowerIsBetter(Row row, double value) {
		if (row.label().getContents() instanceof TranslatableContents contents
				&& (LOWER_IS_BETTER.contains(contents.getKey()) || contents.getKey().startsWith("bloom.sheet.armor.kind."))) {
			return true;
		}

		TextColor color = row.value().getStyle().getColor();
		return value < 0.0 && color != null && color.getValue() == Sheet.GOOD;
	}

	/** -1 = the main hand; -2 = bare hands; 0-35 the inventory (0-8 the hotbar); 40 the off hand. */
	static ItemStack item(ServerPlayer player, int slot) {
		Inventory inventory = player.getInventory();

		if (slot == -2) {
			return ItemStack.EMPTY;
		}

		if (slot < 0 || slot >= inventory.getContainerSize()) {
			return player.getMainHandItem();
		}

		return inventory.getItem(slot);
	}
}
