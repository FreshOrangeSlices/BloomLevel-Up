package com.freshorangeslices.bloomlevelup.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A 9x9 square button with a pixel glyph: + and − next to the stat icons (shift = 10 at a time) and the
 * arrow that folds the stats window away.
 */
final class MiniButton extends AbstractButton {
	enum Glyph {
		PLUS, MINUS, FOLD, UNFOLD
	}

	private static final int SIZE = 9;
	private final Glyph glyph;
	private final Consumer<Boolean> action;

	/** {@code action} gets whether shift was held. */
	MiniButton(int x, int y, Glyph glyph, Component tooltip, Consumer<Boolean> action) {
		super(x, y, SIZE, SIZE, tooltip);
		this.glyph = glyph;
		this.action = action;
		this.setTooltip(Tooltip.create(tooltip));
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		BloomGraphics g = BloomGraphics.of(graphics);
		int x = this.getX();
		int y = this.getY();
		int back = !this.active ? 0xFF1E1B18 : this.isHoveredOrFocused() ? 0xFF6A5A44 : 0xFF3A3128;
		int edge = !this.active ? 0xFF2E2822 : 0xFF8A7656;
		int ink = !this.active ? 0xFF5A5248 : 0xFFEFE6D2;
		g.fill(x, y, x + SIZE, y + SIZE, edge);
		g.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, back);

		switch (glyph) {
			case PLUS -> {
				g.fill(x + 2, y + 4, x + 7, y + 5, ink);
				g.fill(x + 4, y + 2, x + 5, y + 7, ink);
			}
			case MINUS -> g.fill(x + 2, y + 4, x + 7, y + 5, ink);
			case FOLD -> { // ▲
				g.fill(x + 4, y + 3, x + 5, y + 4, ink);
				g.fill(x + 3, y + 4, x + 6, y + 5, ink);
				g.fill(x + 2, y + 5, x + 7, y + 6, ink);
			}
			case UNFOLD -> { // ▼
				g.fill(x + 2, y + 3, x + 7, y + 4, ink);
				g.fill(x + 3, y + 4, x + 6, y + 5, ink);
				g.fill(x + 4, y + 5, x + 5, y + 6, ink);
			}
		}
	}

	@Override
	public void onPress() {
		this.action.accept(net.minecraft.client.gui.screens.Screen.hasShiftDown());
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
