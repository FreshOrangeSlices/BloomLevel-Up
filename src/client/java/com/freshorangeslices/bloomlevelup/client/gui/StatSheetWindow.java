package com.freshorangeslices.bloomlevelup.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.freshorangeslices.bloomlevelup.network.BloomNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.definition.MajorDefinition;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;

/**
 * The stat sheet: a window over the hub (its Stats button) with everything one tab's stats add up to. The
 * server works out every number (with the real game rules) and sends finished lines; this only draws them and
 * sends what the player picks: which weapon or tool from the inventory, which target, which "show it at max"
 * switches are on, and summary or full breakdown. It asks again every second so it stays current.
 *
 * <p>Right-clicking a weapon or tool pins it: every line then shows how the chosen one differs from it. Once
 * there's a second build, the Build chip shows the other build's numbers against the one in use.
 */
final class StatSheetWindow {
	/** Inventory slot meaning "the main hand" (what the sheet opens on). */
	static final int HELD = -1;
	/** Inventory slot meaning "bare hands" (the server reads it as an empty hand). */
	static final int BARE = -2;
	private static final int TEXT = 0xFFEFE6D2;
	private static final int MUTED = 0xFFA99F8A;
	private static final int GOLD = 0xFFF2C443;
	private static final int LABEL = 0xFFE5B567;
	private static final int LINE = 0xFF8A6A2A;
	private static final int HEADER = 30;
	private static final int SLOT = 18;
	private static final int CHIP_H = 13;
	/** The pinned weapon's outline. */
	private static final int PIN = 0xFF6EC6FF;
	/** The switch a Major's "see it in the stat sheet" link puts on Max. */
	private static final Map<String, String> LINKED_SWITCH = Map.ofEntries(Map.entry(CombatIds.PREDATOR, "predator"),
			Map.entry(CombatIds.BLOODLUST, "bloodlust"), Map.entry(CombatIds.COMBO, "combo"), Map.entry(CombatIds.FIRST_STRIKE, "first_strike"),
			Map.entry(CombatIds.CHARGE_ATTACK, "charged"), Map.entry(CombatIds.HEAVY_HANDS, "charged"),
			Map.entry(CombatIds.DEATH_FROM_ABOVE, "fall"), Map.entry(CombatIds.MARKSMAN, "marksman"),
			Map.entry(CombatIds.MASTER_OF_ARMS, "charged_throw"), Map.entry(ArmorIds.BRACE, "braced"));

	private final ResourceLocation frameSprite;
	private final int frame;
	private final String category;
	private final Component title;
	/** Each switch not on Live: Max or Off (SheetPayloads.Toggle.MAX / OFF). */
	private final Map<String, Integer> modes = new LinkedHashMap<>();
	private int slot = HELD;
	private int target = -1;
	/** The pinned weapon or tool (an inventory slot, or BARE), or NO_COMPARE. Not kept after the window closes. */
	private int pinned = SheetPayloads.Request.NO_COMPARE;
	/** Showing the other build's numbers against the one in use. Not kept after the window closes. */
	private boolean otherBuild;
	private boolean details;
	private double scroll;
	private int contentH;
	private int bodyTop;
	private int bodyH;
	private int requestId;
	private int sinceRequest;
	private final List<Hit> hits = new ArrayList<>();

	/** A clickable or hoverable spot drawn this frame. */
	private record Hit(int x0, int y0, int x1, int y1, Runnable action, Runnable rightAction, Component tooltip) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	/** One choice in the weapon/tool row: an inventory slot (or HELD / BARE) and what it shows. */
	private record Choice(int slot, ItemStack stack, Component tooltip) {
	}

	StatSheetWindow(String category, Component title, ResourceLocation frameSprite, int frame) {
		this.category = category;
		this.title = title;
		this.frameSprite = frameSprite;
		this.frame = frame;
		SheetMemory.Tab remembered = SheetMemory.get(category);
		this.slot = remembered.slot();
		this.target = remembered.target();
		this.details = remembered.details();
		this.modes.putAll(remembered.modes());
		request();
	}

	/** Keeps this tab's choices for next time (config/bloomlevelup-sheet.json). */
	private void remember() {
		SheetMemory.put(category, new SheetMemory.Tab(slot, target, Map.copyOf(modes), details));
	}

	String category() {
		return category;
	}

	void tick() {
		Player player = Minecraft.getInstance().player;

		// The pinned item left its slot: unpin. A second build that isn't there any more: back to this one.
		if (pinned >= 0 && player != null && player.getInventory().getItem(pinned).isEmpty()) {
			pinned = SheetPayloads.Request.NO_COMPARE;
			request();
		}

		if (otherBuild && ClientBloomState.profile().spareBuild().isEmpty()) {
			otherBuild = false;
			request();
		}

		if (++sinceRequest >= 20) {
			request();
		}
	}

	private void request() {
		sinceRequest = 0;
		requestId++;
		List<String> switches = new ArrayList<>();
		modes.forEach((id, mode) -> switches.add(id + (mode == SheetPayloads.Toggle.MAX ? "=max" : "=off")));
		BloomNetwork.sendToServer(new SheetPayloads.Request(requestId, category, slot, target, switches, pinned, otherBuild));
	}

	private SheetPayloads.View view() {
		SheetPayloads.View view = ClientBloomState.sheet();
		return view != null && view.category().equals(category) ? view : null;
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	private static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	// ------------------------------------------------------------------ geometry

	private static int screenW() {
		return Minecraft.getInstance().getWindow().getGuiScaledWidth();
	}

	private static int screenH() {
		return Minecraft.getInstance().getWindow().getGuiScaledHeight();
	}

	int w() {
		return Math.min(360, screenW() - 24);
	}

	int h() {
		return Math.min(262, screenH() - 24);
	}

	int x() {
		return (screenW() - w()) / 2;
	}

	int y() {
		return (screenH() - h()) / 2;
	}

	boolean inside(double x, double y) {
		return x >= x() && x < x() + w() && y >= y() && y < y() + h();
	}

	// ------------------------------------------------------------------ drawing

	void render(BloomGraphics g, int mouseX, int mouseY) {
		Font font = font();
		hits.clear();
		g.nextStratum();
		g.fill(0, 0, screenW(), screenH(), 0xB0000000);
		int x0 = x();
		int y0 = y();
		int w = w();
		int h = h();
		g.fill(x0, y0, x0 + w, y0 + h, 0xF6141210);
		g.blitSprite(BloomGraphics.GUI_TEXTURED, frameSprite, x0 - frame, y0 - frame, w + 2 * frame, h + 2 * frame);

		// Title, the summary / all stats tabs, and the close button.
		MajorDetails.big(g, font, MajorDetails.heading(title), x0 + 8, y0 + 5, 0xFFFFD86B);
		int closeX = x0 + w - 13;
		hits.add(new Hit(closeX - 2, y0 + 3, closeX + 10, y0 + 15, null, null, Component.translatable("bloom.hub.detail.close")));
		g.text(font, Component.literal("×"), closeX, y0 + 5, overClose(mouseX, mouseY) ? 0xFFFFFFFF : MUTED);
		int tabsRight = closeX - 6;
		Component all = Component.translatable("bloom.sheet.ui.details");
		Component summary = Component.translatable("bloom.sheet.ui.summary");
		int allX = tabsRight - chipWidth(font, all);
		int summaryX = allX - 3 - chipWidth(font, summary);
		chip(g, font, summaryX, y0 + 6, summary, !details, mouseX, mouseY, () -> pickPage(false), null);
		chip(g, font, allX, y0 + 6, all, details, mouseX, mouseY, () -> pickPage(true), null);

		// The other build, once there is one.
		BloomProfile profile = ClientBloomState.profile();

		if (profile.spareBuild().isPresent()) {
			int inUse = profile.activeBuild() + 1;
			String other = String.valueOf(3 - inUse);
			Component build = Component.translatable("bloom.sheet.ui.build", other);
			int buildX = summaryX - 8 - chipWidth(font, build);
			MutableComponent tip = Component.translatable("bloom.sheet.ui.build.tip", other, String.valueOf(inUse));

			if (otherBuild) {
				tip.append("\n").append(Component.translatable("bloom.sheet.ui.build.back").withStyle(style -> style.withColor(MUTED)));
			}

			chip(g, font, buildX, y0 + 6, build, otherBuild, mouseX, mouseY, this::toggleOtherBuild, tip);
		}

		SheetPayloads.View view = view();
		int y = y0 + HEADER - 6;

		// The weapon / tool row.
		List<Choice> choices = choices();

		if (!choices.isEmpty()) {
			Component label = Component.translatable(category.equals(SkillXp.UTILITY) ? "bloom.sheet.ui.tool" : "bloom.sheet.ui.weapon");
			g.text(font, label, x0 + 8, y + 5, LABEL);
			int sx = x0 + 12 + font.width(label);
			int maxX = x0 + w - 8;

			for (Choice choice : choices) {
				if (sx + SLOT > maxX) {
					break;
				}

				boolean selected = choice.slot() == slot;
				boolean pin = isPinned(choice.slot());
				boolean hover = mouseX >= sx && mouseX < sx + SLOT && mouseY >= y && mouseY < y + SLOT;

				if (pin) {
					g.fill(sx - 1, y - 1, sx + SLOT + 1, y + SLOT + 1, PIN);
				}

				g.fill(sx, y, sx + SLOT, y + SLOT, selected ? GOLD : pin ? PIN : hover ? 0xFF8A6A2A : 0xFF3A2F22);
				g.fill(sx + 1, y + 1, sx + SLOT - 1, y + SLOT - 1, selected ? 0xFF4A3A22 : pin ? 0xFF1C2E3A : 0xFF201A13);

				if (choice.stack().isEmpty()) {
					g.centeredText(font, Component.literal("✋"), sx + SLOT / 2, y + 5, MUTED);
				} else {
					g.item(choice.stack(), sx + 1, y + 1);
				}

				if (pin) {
					// A little pin in the corner.
					g.fill(sx + SLOT - 5, y + 1, sx + SLOT - 1, y + 5, PIN);
				}

				int pick = choice.slot();
				MutableComponent tip = choice.tooltip().copy().append("\n").append(Component.translatable(pin ? "bloom.sheet.ui.pin.on" : "bloom.sheet.ui.pin.tip")
						.withStyle(style -> style.withColor(pin ? PIN : MUTED)));
				hits.add(new Hit(sx, y, sx + SLOT, y + SLOT, () -> pickSlot(pick), () -> togglePin(pick), tip));
				sx += SLOT + 2;
			}

			y += SLOT + 4;
		}

		// The target picker and the switches.
		if (view != null && !view.targets().isEmpty()) {
			Component label = Component.translatable(category.equals(SkillXp.ARMOR) ? "bloom.sheet.ui.incoming" : "bloom.sheet.ui.target");
			g.text(font, label, x0 + 8, y + 3, LABEL);
			int cx = x0 + 12 + font.width(label);

			for (int i = 0; i < view.targets().size(); i++) {
				Component name = view.targets().get(i);
				int index = i;
				cx = chip(g, font, cx, y, name, i == view.target(), mouseX, mouseY, () -> pickTarget(index), null) + 3;
			}

			y += CHIP_H + 4;
		}

		if (view != null && !view.toggles().isEmpty()) {
			Component label = Component.translatable("bloom.sheet.ui.show_max");
			g.text(font, label, x0 + 8, y + 3, LABEL);
			int left = x0 + 12 + font.width(label);
			int cx = left;
			List<SheetPayloads.Toggle> offered = view.toggles();

			// "All": every switch at once, Live → Max → Off.
			if (offered.size() > 1) {
				int together = allMode(offered);
				Component allName = Component.translatable("bloom.sheet.ui.all").append(": ")
						.append(together < 0 ? Component.literal("…") : modeName(together, Component.empty()));
				cx = modeChip(g, font, cx, y, allName, together, mouseX, mouseY, () -> setAll(offered),
						Component.translatable("bloom.sheet.ui.all.tip")) + 3;
			}

			for (SheetPayloads.Toggle toggle : offered) {
				int mode = modes.getOrDefault(toggle.id(), SheetPayloads.Toggle.LIVE);
				Component name = toggle.name().copy().append(": ").append(modeName(mode, toggle.live()));
				int width = chipWidth(font, name);

				if (cx + width > x0 + w - 8 && cx > left) {
					cx = left;
					y += CHIP_H + 3;
				}

				String id = toggle.id();
				Component tip = toggle.tooltip().copy().append("\n").append(Component.translatable("bloom.sheet.ui.cycle").withStyle(style -> style.withColor(MUTED)));
				cx = modeChip(g, font, cx, y, name, mode, mouseX, mouseY, () -> cycle(id), tip) + 3;
			}

			y += CHIP_H + 4;
		}

		g.fill(x0 + 8, y, x0 + w - 8, y + 1, LINE);
		bodyTop = y + 4;
		bodyH = y0 + h - 14 - bodyTop;

		if (view == null) {
			g.centeredText(font, Component.translatable("bloom.sheet.ui.loading"), x0 + w / 2, bodyTop + 20, MUTED);
		} else {
			g.enableScissor(x0 + 4, bodyTop, x0 + w - 4, bodyTop + bodyH);
			int top = bodyTop - (int) Math.round(scroll);
			int bottom = details ? drawDetails(g, font, view, x0 + 10, top, w - 26) : drawSummary(g, font, view, x0 + 10, top, w - 26);
			g.disableScissor();
			contentH = bottom - top;
			clampScroll();

			if (contentH > bodyH) {
				int thumb = Math.max(12, bodyH * bodyH / contentH);
				int thumbTop = bodyTop + (int) Math.round((bodyH - thumb) * (scroll / Math.max(1.0, contentH - bodyH)));
				g.fill(x0 + w - 9, bodyTop, x0 + w - 7, bodyTop + bodyH, 0xFF2E261C);
				g.fill(x0 + w - 9, thumbTop, x0 + w - 7, thumbTop + thumb, 0xFFC98F1F);
			}
		}

		g.centeredText(font, Component.translatable("bloom.hub.detail.close"), x0 + w / 2, y0 + h - 11, 0xFF6E6250);

		for (Hit hit : hits) {
			if (hit.tooltip() != null && hit.contains(mouseX, mouseY) && !hit.tooltip().getString().isEmpty()) {
				g.setTooltipForNextFrame(font, font.split(hit.tooltip(), 220), mouseX, mouseY);
				break;
			}
		}
	}

	/** The gauge (the vanilla item it compares to, three times as big) on the left, the summary lines on the right. */
	private int drawSummary(BloomGraphics g, Font font, SheetPayloads.View view, int x, int y, int width) {
		int left = 0;

		if (!view.gauge().isEmpty()) {
			int box = 54;
			int bx = x + 4;
			g.fill(bx, y, bx + box, y + box, LINE);
			g.fill(bx + 1, y + 1, bx + box - 1, y + box - 1, 0xFF201A13);
			g.pose().pushMatrix();
			g.pose().translate(bx + 3, y + 3);
			g.pose().scale(3.0F, 3.0F);
			g.item(view.gauge(), 0, 0);
			g.pose().popMatrix();
			int cy = y + box + 4;

			for (FormattedCharSequence line : font.split(view.gaugeCaption(), 104)) {
				g.text(font, line, x, cy, MUTED);
				cy += 10;
			}

			if (!view.gaugeBadge().getString().isEmpty()) {
				g.text(font, view.gaugeBadge(), bx + box + 3, y + 2, GOLD);
			}

			left = 112;
		}

		int rx = x + left;
		int rw = width - left;
		int ry = y;

		for (SheetPayloads.Row row : view.summary()) {
			ry = drawRow(g, font, row, rx, ry, rw);
		}

		int captionBottom = view.gauge().isEmpty() ? y : y + 58 + 10 * font.split(view.gaugeCaption(), 104).size();
		int bottom = Math.max(ry, captionBottom) + 6;

		for (FormattedCharSequence line : font.split(Component.translatable("bloom.sheet.ui.more"), width)) {
			g.text(font, line, x, bottom, 0xFF6E6250);
			bottom += 10;
		}

		return bottom + 2;
	}

	private int drawDetails(BloomGraphics g, Font font, SheetPayloads.View view, int x, int y, int width) {
		int ry = y;
		boolean first = true;

		for (SheetPayloads.Row row : view.details()) {
			if (row.kind() == SheetPayloads.Row.HEADING && !first) {
				ry += 5;
			}

			ry = drawRow(g, font, row, x, ry, width);
			first = false;
		}

		return ry + 4;
	}

	/** One line: a heading, "label .... value", a total (with a rule over it) or a grey note. Returns the next y. */
	private static int drawRow(BloomGraphics g, Font font, SheetPayloads.Row row, int x, int y, int width) {
		switch (row.kind()) {
			case SheetPayloads.Row.HEADING -> {
				MajorDetails.big(g, font, MajorDetails.heading(row.label()), x, y, GOLD);
				return y + 16;
			}
			case SheetPayloads.Row.NOTE -> {
				int ny = y;

				for (FormattedCharSequence line : font.split(row.label(), width - 8)) {
					g.text(font, line, x + 8, ny, 0xFF8C8270);
					ny += 9;
				}

				return ny + 1;
			}
			default -> {
				boolean total = row.kind() == SheetPayloads.Row.TOTAL;
				int valueW = font.width(row.value());
				int ty = y;

				if (total) {
					g.fill(x, y, x + width, y + 1, 0xFF4A3B26);
					ty += 3;
				}

				List<FormattedCharSequence> lines = font.split(row.label(), Math.max(40, width - valueW - 8));

				for (int i = 0; i < lines.size(); i++) {
					g.text(font, lines.get(i), x + (total ? 0 : 4), ty + i * 10, total ? LABEL : TEXT);
				}

				g.text(font, row.value(), x + width - valueW, ty, 0xFFFFFFFF);
				return ty + Math.max(1, lines.size()) * 10 + 1;
			}
		}
	}

	/** "Live" (with what that is right now), "Max" or "Off". */
	private static Component modeName(int mode, Component live) {
		if (mode == SheetPayloads.Toggle.MAX) {
			return Component.translatable("bloom.sheet.ui.mode.max");
		}

		if (mode == SheetPayloads.Toggle.OFF) {
			return Component.translatable("bloom.sheet.ui.mode.off");
		}

		MutableComponent text = Component.translatable("bloom.sheet.ui.mode.live");
		return live.getString().isEmpty() ? text : text.append(" (").append(live).append(")");
	}

	/** A switch drawn by its mode: Live green, Max gold, Off dark. Returns its right edge. */
	private int modeChip(BloomGraphics g, Font font, int x, int y, Component text, int mode, int mouseX, int mouseY, Runnable action,
			Component tooltip) {
		int w = chipWidth(font, text);
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CHIP_H;
		int rim = mode == SheetPayloads.Toggle.MAX ? GOLD : mode == SheetPayloads.Toggle.LIVE ? 0xFF5FB04A : hover ? 0xFF8A6A2A : 0xFF3A2F22;
		int fill = mode == SheetPayloads.Toggle.MAX ? 0xFFC98F1F : mode == SheetPayloads.Toggle.LIVE ? 0xFF1F3A1A : 0xFF201A13;
		int color = mode == SheetPayloads.Toggle.MAX ? 0xFF1A1206 : mode == SheetPayloads.Toggle.LIVE ? 0xFFB9F2A6 : hover ? TEXT : MUTED;
		g.fill(x + 1, y, x + w - 1, y + CHIP_H, hover && mode != SheetPayloads.Toggle.OFF ? 0xFFFFFFFF : rim);
		g.fill(x, y + 1, x + w, y + CHIP_H - 1, hover && mode != SheetPayloads.Toggle.OFF ? 0xFFFFFFFF : rim);
		g.fill(x + 1, y + 1, x + w - 1, y + CHIP_H - 1, fill);
		g.text(font, text, x + 4, y + 3, color);
		hits.add(new Hit(x, y, x + w, y + CHIP_H, action, null, tooltip));
		return x + w;
	}

	/** A small rounded switch; returns its right edge. */
	private int chip(BloomGraphics g, Font font, int x, int y, Component text, boolean on, int mouseX, int mouseY, Runnable action,
			Component tooltip) {
		int w = chipWidth(font, text);
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CHIP_H;
		g.fill(x + 1, y, x + w - 1, y + CHIP_H, on ? GOLD : hover ? 0xFF8A6A2A : 0xFF3A2F22);
		g.fill(x, y + 1, x + w, y + CHIP_H - 1, on ? GOLD : hover ? 0xFF8A6A2A : 0xFF3A2F22);
		g.fill(x + 1, y + 1, x + w - 1, y + CHIP_H - 1, on ? 0xFFC98F1F : 0xFF201A13);
		g.text(font, text, x + 4, y + 3, on ? 0xFF1A1206 : hover ? TEXT : MUTED);
		hits.add(new Hit(x, y, x + w, y + CHIP_H, action, null, tooltip));
		return x + w;
	}

	private static int chipWidth(Font font, Component text) {
		return font.width(text) + 8;
	}

	// ------------------------------------------------------------------ choices

	/** The weapon (Combat) or tool (Utility) choices: what's in hand, bare hands, then the rest of the inventory. */
	private List<Choice> choices() {
		boolean combat = category.equals(SkillXp.COMBAT);
		boolean utility = category.equals(SkillXp.UTILITY);
		Player player = Minecraft.getInstance().player;

		if ((!combat && !utility) || player == null) {
			return List.of();
		}

		List<Choice> out = new ArrayList<>();
		ItemStack held = player.getMainHandItem();
		out.add(new Choice(HELD, held, Component.translatable("bloom.sheet.ui.held", held.isEmpty() ? Component.translatable("bloom.sheet.ui.bare")
				: held.getHoverName())));

		if (!held.isEmpty()) {
			out.add(new Choice(BARE, ItemStack.EMPTY, Component.translatable("bloom.sheet.ui.bare")));
		}

		Inventory inventory = player.getInventory();

		for (int i = 0; i < 36; i++) {
			ItemStack stack = inventory.getItem(i);

			if (stack.isEmpty() || stack == held || !(combat ? isWeapon(stack) : isTool(stack))) {
				continue;
			}

			out.add(new Choice(i, stack, stack.getHoverName()));
		}

		return out;
	}

	private static boolean isWeapon(ItemStack stack) {
		return !Weapons.families(stack).isEmpty();
	}

	private static boolean isTool(ItemStack stack) {
		return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
				|| stack.is(Items.SHEARS) || stack.is(Items.FISHING_ROD);
	}

	// ------------------------------------------------------------------ input

	private boolean overClose(double x, double y) {
		int closeX = x() + w() - 13;
		return x >= closeX - 2 && x < closeX + 10 && y >= y() + 3 && y < y() + 15;
	}

	/** Returns false when the click closes the window. {@code right}: the right mouse button (pins a weapon or tool). */
	boolean mouseClicked(double x, double y, boolean right) {
		if (!inside(x, y) || overClose(x, y)) {
			return right;
		}

		for (Hit hit : hits) {
			Runnable action = right ? hit.rightAction() : hit.action();

			if (action != null && hit.contains(x, y)) {
				click();
				action.run();
				return true;
			}
		}

		return true;
	}

	void mouseScrolled(double scrollY) {
		scroll -= scrollY * 20.0;
		clampScroll();
	}

	private void clampScroll() {
		scroll = Math.max(0.0, Math.min(scroll, Math.max(0.0, contentH - bodyH)));
	}

	private void pickPage(boolean all) {
		details = all;
		scroll = 0.0;
		remember();
	}

	private void pickSlot(int pick) {
		slot = pick;
		remember();
		request();
	}

	/** Whether a choice in the weapon row is the pinned one (the held item counts by the slot it's in). */
	private boolean isPinned(int choice) {
		return pinned != SheetPayloads.Request.NO_COMPARE && (choice == pinned || (choice == HELD && heldSlot() == pinned));
	}

	/** The inventory slot of what's in the main hand, or HELD if it isn't in the 36 slots. */
	private static int heldSlot() {
		Player player = Minecraft.getInstance().player;

		if (player == null) {
			return HELD;
		}

		ItemStack held = player.getMainHandItem();

		for (int i = 0; i < 36; i++) {
			if (!held.isEmpty() && player.getInventory().getItem(i) == held) {
				return i;
			}
		}

		return HELD;
	}

	/** Pins a weapon or tool (or unpins it); the item in hand is pinned by its slot, so it stays put when you scroll. */
	private void togglePin(int choice) {
		int slotOf = choice == HELD ? heldSlot() : choice;
		pinned = isPinned(choice) || slotOf == HELD ? SheetPayloads.Request.NO_COMPARE : slotOf;
		otherBuild = false;
		request();
	}

	private void toggleOtherBuild() {
		otherBuild = !otherBuild;
		pinned = SheetPayloads.Request.NO_COMPARE;
		request();
	}

	/**
	 * From a Major's details window: shows what it does, with its switch on Max and (for a weapon tree) a weapon
	 * of that tree picked, bare hands for Unarmed.
	 */
	void focus(MajorDefinition major) {
		String linked = LINKED_SWITCH.get(major.id());

		if (linked != null) {
			setMode(linked, SheetPayloads.Toggle.MAX);
		}

		Integer weapon = slotFor(major);

		if (weapon != null) {
			slot = weapon;
		}

		details = false;
		scroll = 0.0;
		remember();
		request();
	}

	/** The switch a Major's link puts on Max, or null. */
	static String linkedSwitch(String majorId) {
		return LINKED_SWITCH.get(majorId);
	}

	/** The weapon row's choice for a Major: bare hands for Unarmed, the held weapon or the first one of its tree. */
	private static Integer slotFor(MajorDefinition major) {
		List<Weapons.Family> families = new ArrayList<>();

		if (major.id().equals(CombatIds.MASTER_OF_ARMS)) {
			// Its charged throw: the weapons Catch! throws in this build (swords and axes).
			families.add(Weapons.Family.SWORD);
			families.add(Weapons.Family.AXE);
		}

		major.group().ifPresent(group -> {
			String tree = group.substring(group.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT);

			for (Weapons.Family family : Weapons.Family.values()) {
				if (family.name().equals(tree)) {
					families.add(family);
				}
			}
		});

		Player player = Minecraft.getInstance().player;

		if (families.isEmpty() || player == null) {
			return null;
		}

		if (families.contains(Weapons.Family.UNARMED)) {
			return BARE;
		}

		for (Weapons.Family family : families) {
			if (Weapons.is(player.getMainHandItem(), family)) {
				return HELD;
			}
		}

		for (Weapons.Family family : families) {
			for (int i = 0; i < 36; i++) {
				if (Weapons.is(player.getInventory().getItem(i), family)) {
					return i;
				}
			}
		}

		return null;
	}

	private void pickTarget(int index) {
		target = index;
		remember();
		request();
	}

	/** Live → Max → Off → Live. */
	private void cycle(String id) {
		int next = (modes.getOrDefault(id, SheetPayloads.Toggle.LIVE) + 1) % 3;
		setMode(id, next);
		remember();
		request();
	}

	private void setMode(String id, int mode) {
		if (mode == SheetPayloads.Toggle.LIVE) {
			modes.remove(id);
		} else {
			modes.put(id, mode);
		}
	}

	/** The mode every offered switch shares, or -1 when they differ. */
	private int allMode(List<SheetPayloads.Toggle> offered) {
		int shared = -2;

		for (SheetPayloads.Toggle toggle : offered) {
			int mode = modes.getOrDefault(toggle.id(), SheetPayloads.Toggle.LIVE);

			if (shared == -2) {
				shared = mode;
			} else if (shared != mode) {
				return -1;
			}
		}

		return shared < 0 ? SheetPayloads.Toggle.LIVE : shared;
	}

	/** Every switch to the next mode together (mixed ones start over at Live). */
	private void setAll(List<SheetPayloads.Toggle> offered) {
		int now = allMode(offered);
		int next = now < 0 ? SheetPayloads.Toggle.LIVE : (now + 1) % 3;

		for (SheetPayloads.Toggle toggle : offered) {
			setMode(toggle.id(), next);
		}

		remember();
		request();
	}

	// ------------------------------------------------------------------ game tests

	void showDetailsForTests(boolean all) {
		pickPage(all);
	}

	void setToggleForTests(String id, boolean on) {
		setMode(id, on ? SheetPayloads.Toggle.MAX : SheetPayloads.Toggle.OFF);
		request();
	}

	void pinForTests(int slot) {
		togglePin(slot);
	}

	void pickSlotForTests(int pick) {
		pickSlot(pick);
	}

	void otherBuildForTests() {
		toggleOtherBuild();
	}

	int slotForTests() {
		return slot;
	}

	Map<String, Integer> modesForTests() {
		return Map.copyOf(modes);
	}
}
