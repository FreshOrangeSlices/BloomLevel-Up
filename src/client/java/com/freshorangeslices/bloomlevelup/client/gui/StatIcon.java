package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.freshorangeslices.bloomlevelup.client.IconTextures;

/**
 * A stat's icon in the hub's stats window, which is also its on/off switch: a corner dot, green when on and
 * red when off, like an owned Major. Stats that can't be switched show no dot and don't react to clicks.
 */
final class StatIcon extends AbstractButton {
	private static final int SIZE = 18;
	private final Identifier icon;
	private final boolean dimmed;
	private final boolean showDot;
	private final boolean on;
	private final Runnable action;

	/**
	 * @param dimmed   draw the icon greyed out (the stat's level points are switched off)
	 * @param showDot  whether this stat has a switch here at all
	 * @param on       the switch's state
	 * @param clickable whether clicking flips it now
	 */
	StatIcon(int x, int y, Identifier icon, boolean dimmed, boolean showDot, boolean on, boolean clickable, Runnable action) {
		super(x, y, SIZE, SIZE, Component.translatable(on ? "bloom.hub.on" : "bloom.hub.off"));
		this.icon = icon;
		this.dimmed = dimmed;
		this.showDot = showDot;
		this.on = on;
		this.action = action;
		this.active = clickable;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = this.getX();
		int y = this.getY();
		g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0F, 0.0F, SIZE, SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE,
				IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, dimmed ? 0xFF7A7A7A : 0xFFFFFFFF);

		if (this.active && this.isHoveredOrFocused()) {
			g.fill(x - 1, y - 1, x + SIZE + 1, y, 0xFFFFFFFF);
			g.fill(x - 1, y + SIZE, x + SIZE + 1, y + SIZE + 1, 0xFFFFFFFF);
			g.fill(x - 1, y, x, y + SIZE, 0xFFFFFFFF);
			g.fill(x + SIZE, y, x + SIZE + 1, y + SIZE, 0xFFFFFFFF);
		}

		if (showDot) {
			HubScreen.dot(g, x + SIZE - 6, y, on ? 0xFF4FD34F : 0xFFE0453A, on ? 0xFF1E6B1E : 0xFF6B1A14);
		}
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
