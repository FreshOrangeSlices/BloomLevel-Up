package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A small square switch: a green dot when on, empty when off. */
final class DotToggle extends AbstractButton {
	private static final int DOT = 0xFF4FD34F;
	private static final int DOT_EDGE = 0xFF1E6B1E;
	private final boolean on;
	private final Runnable action;

	DotToggle(int x, int y, int size, boolean on, Component tooltip, Runnable action) {
		super(x, y, size, size, Component.translatable(on ? "bloom.hub.on" : "bloom.hub.off"));
		this.on = on;
		this.action = action;
		this.setTooltip(Tooltip.create(tooltip));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		this.extractDefaultSprite(g);

		if (!this.on) {
			return;
		}

		// A 6x6 round-ish dot in the middle.
		int cx = this.getX() + this.getWidth() / 2 - 3;
		int cy = this.getY() + this.getHeight() / 2 - 3;
		g.fill(cx + 1, cy, cx + 5, cy + 6, DOT_EDGE);
		g.fill(cx, cy + 1, cx + 6, cy + 5, DOT_EDGE);
		g.fill(cx + 1, cy + 1, cx + 5, cy + 5, DOT);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.action.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
