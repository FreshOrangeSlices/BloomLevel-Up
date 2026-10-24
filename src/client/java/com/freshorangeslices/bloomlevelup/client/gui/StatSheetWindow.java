package com.freshorangeslices.bloomlevelup.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;

/**
 * The stat sheet: a window over the hub (its Stats button) with everything one tab's stats add up to. The
 * server works out every number (with the real game rules) and sends finished lines; this only draws them and
 * sends what the player picks: which weapon or tool from the inventory, which target, which "show it at max"
 * switches are on, and summary or full breakdown. It asks again every second so it stays current.
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

	private final Identifier frameSprite;
	private final int frame;
	private final String category;
	private final Component title;
	private final Set<String> toggles = new LinkedHashSet<>();
	private int slot = HELD;
	private int target = -1;
	private boolean details;
	private double scroll;
	private int contentH;
	private int bodyTop;
	private int bodyH;
	private int requestId;
	private int sinceRequest;
	private final List<Hit> hits = new ArrayList<>();

	/** A clickable or hoverable spot drawn this frame. */
	private record Hit(int x0, int y0, int x1, int y1, Runnable action, Component tooltip) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	/** One choice in the weapon/tool row: an inventory slot (or HELD / BARE) and what it shows. */
	private record Choice(int slot, ItemStack stack, Component tooltip) {
	}

	StatSheetWindow(String category, Component title, Identifier frameSprite, int frame) {
		this.category = category;
		this.title = title;
		this.frameSprite = frameSprite;
		this.frame = frame;
		request();
	}

	String category() {
		return category;
	}

	void tick() {
		if (++sinceRequest >= 20) {
			request();
		}
	}

	private void request() {
		sinceRequest = 0;
		requestId++;
		ClientPlayNetworking.send(new SheetPayloads.Request(requestId, category, slot, target, List.copyOf(toggles)));
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

	void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Font font = font();
		hits.clear();
		g.nextStratum();
		g.fill(0, 0, screenW(), screenH(), 0xB0000000);
		int x0 = x();
		int y0 = y();
		int w = w();
		int h = h();
		g.fill(x0, y0, x0 + w, y0 + h, 0xF6141210);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, frameSprite, x0 - frame, y0 - frame, w + 2 * frame, h + 2 * frame);

		// Title, the summary / all stats tabs, and the close button.
		g.text(font, MajorDetails.fancy(title), x0 + 8, y0 + 7, 0xFFFFD86B);
		int closeX = x0 + w - 13;
		hits.add(new Hit(closeX - 2, y0 + 3, closeX + 10, y0 + 15, null, Component.translatable("bloom.hub.detail.close")));
		g.text(font, Component.literal("×"), closeX, y0 + 5, overClose(mouseX, mouseY) ? 0xFFFFFFFF : MUTED);
		int tabsRight = closeX - 6;
		Component all = Component.translatable("bloom.sheet.ui.details");
		Component summary = Component.translatable("bloom.sheet.ui.summary");
		int allX = tabsRight - chipWidth(font, all);
		int summaryX = allX - 3 - chipWidth(font, summary);
		chip(g, font, summaryX, y0 + 6, summary, !details, mouseX, mouseY, () -> pickPage(false), null);
		chip(g, font, allX, y0 + 6, all, details, mouseX, mouseY, () -> pickPage(true), null);

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
				boolean hover = mouseX >= sx && mouseX < sx + SLOT && mouseY >= y && mouseY < y + SLOT;
				g.fill(sx, y, sx + SLOT, y + SLOT, selected ? GOLD : hover ? 0xFF8A6A2A : 0xFF3A2F22);
				g.fill(sx + 1, y + 1, sx + SLOT - 1, y + SLOT - 1, selected ? 0xFF4A3A22 : 0xFF201A13);

				if (choice.stack().isEmpty()) {
					g.centeredText(font, Component.literal("✋"), sx + SLOT / 2, y + 5, MUTED);
				} else {
					g.item(choice.stack(), sx + 1, y + 1);
				}

				int pick = choice.slot();
				hits.add(new Hit(sx, y, sx + SLOT, y + SLOT, () -> pickSlot(pick), choice.tooltip()));
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

			for (SheetPayloads.Toggle toggle : view.toggles()) {
				int width = chipWidth(font, toggle.name());

				if (cx + width > x0 + w - 8 && cx > left) {
					cx = left;
					y += CHIP_H + 3;
				}

				String id = toggle.id();
				cx = chip(g, font, cx, y, toggle.name(), toggles.contains(id), mouseX, mouseY, () -> flip(id), toggle.tooltip()) + 3;
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
	private int drawSummary(GuiGraphicsExtractor g, Font font, SheetPayloads.View view, int x, int y, int width) {
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
		g.text(font, Component.translatable("bloom.sheet.ui.more"), rx, bottom, 0xFF6E6250);
		return bottom + 12;
	}

	private int drawDetails(GuiGraphicsExtractor g, Font font, SheetPayloads.View view, int x, int y, int width) {
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
	private static int drawRow(GuiGraphicsExtractor g, Font font, SheetPayloads.Row row, int x, int y, int width) {
		switch (row.kind()) {
			case SheetPayloads.Row.HEADING -> {
				g.text(font, MajorDetails.fancy(row.label()), x, y, GOLD);
				return y + 13;
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

	/** A small rounded switch; returns its right edge. */
	private int chip(GuiGraphicsExtractor g, Font font, int x, int y, Component text, boolean on, int mouseX, int mouseY, Runnable action,
			Component tooltip) {
		int w = chipWidth(font, text);
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CHIP_H;
		g.fill(x + 1, y, x + w - 1, y + CHIP_H, on ? GOLD : hover ? 0xFF8A6A2A : 0xFF3A2F22);
		g.fill(x, y + 1, x + w, y + CHIP_H - 1, on ? GOLD : hover ? 0xFF8A6A2A : 0xFF3A2F22);
		g.fill(x + 1, y + 1, x + w - 1, y + CHIP_H - 1, on ? 0xFFC98F1F : 0xFF201A13);
		g.text(font, text, x + 4, y + 3, on ? 0xFF1A1206 : hover ? TEXT : MUTED);
		hits.add(new Hit(x, y, x + w, y + CHIP_H, action, tooltip));
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

	/** Returns false when the click closes the window. */
	boolean mouseClicked(double x, double y) {
		if (!inside(x, y) || overClose(x, y)) {
			return false;
		}

		for (Hit hit : hits) {
			if (hit.action() != null && hit.contains(x, y)) {
				click();
				hit.action().run();
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
	}

	private void pickSlot(int pick) {
		slot = pick;
		request();
	}

	private void pickTarget(int index) {
		target = index;
		request();
	}

	private void flip(String id) {
		if (!toggles.remove(id)) {
			toggles.add(id);
		}

		request();
	}

	// ------------------------------------------------------------------ game tests

	void showDetailsForTests(boolean all) {
		pickPage(all);
	}

	void setToggleForTests(String id, boolean on) {
		if (on) {
			toggles.add(id);
		} else {
			toggles.remove(id);
		}

		request();
	}
}
