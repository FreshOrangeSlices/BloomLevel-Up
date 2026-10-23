package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * A category tab along the top of the Skill Hub: a dark plate with a gold rim, in the same golds as the
 * window's frame ({@code textures/gui/sprites/hub/frame.png}). The open tab is lit and can't be clicked.
 */
final class HubTab extends AbstractButton {
	static final int OUTLINE = 0xFF2B1A06;
	static final int SHINE = 0xFFFFF1A8;
	static final int LIGHT = 0xFFF2C443;
	static final int MID = 0xFFC98F1F;
	static final int DEEP = 0xFFA06E16;
	static final int SHADOW = 0xFF6B430C;

	private final boolean selected;
	private final Runnable action;

	HubTab(int x, int y, int width, int height, Component label, boolean selected, Runnable action) {
		super(x, y, width, height, label);
		this.selected = selected;
		this.action = action;
		this.active = !selected;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x0 = this.getX();
		int y0 = this.getY();
		int x1 = x0 + this.getWidth();
		int y1 = y0 + this.getHeight();
		boolean hover = !selected && this.isHoveredOrFocused();

		g.fill(x0, y0, x1, y1, OUTLINE);
		// Gold rim: lit along the top and left, shaded along the bottom and right.
		g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, selected ? LIGHT : hover ? MID : DEEP);
		g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, selected ? SHINE : hover ? LIGHT : MID);
		g.fill(x0 + 1, y0 + 2, x0 + 2, y1 - 1, selected ? SHINE : hover ? LIGHT : MID);
		g.fill(x0 + 2, y1 - 2, x1 - 1, y1 - 1, SHADOW);
		g.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 2, SHADOW);
		g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, selected ? 0xFF4A3A22 : hover ? 0xFF33291D : 0xFF201A13);

		int color = selected ? 0xFFFFD86B : hover ? 0xFFFFF1C9 : 0xFFCDBB95;
		g.centeredText(Minecraft.getInstance().font, this.getMessage(), (x0 + x1) / 2, y0 + (this.getHeight() - 8) / 2, color);
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
