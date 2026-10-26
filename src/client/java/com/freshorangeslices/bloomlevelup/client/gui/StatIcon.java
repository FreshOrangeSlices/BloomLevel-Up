package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.freshorangeslices.bloomlevelup.client.IconTextures;

/**
 * A stat's icon in the hub's stats window, which is also its on/off switch: a corner dot, green when on and
 * red when off, like an owned Major. Stats that can't be switched show no dot and don't react to clicks.
 */
final class StatIcon extends AbstractButton {
	private static final int SIZE = 18;
	private final ResourceLocation icon;
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
	StatIcon(int x, int y, ResourceLocation icon, boolean dimmed, boolean showDot, boolean on, boolean clickable, Runnable action) {
		super(x, y, SIZE, SIZE, Component.translatable(on ? "bloom.hub.on" : "bloom.hub.off"));
		this.icon = icon;
		this.dimmed = dimmed;
		this.showDot = showDot;
		this.on = on;
		this.action = action;
		this.active = clickable;
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		BloomGraphics g = BloomGraphics.of(graphics);
		int x = this.getX();
		int y = this.getY();
		g.blit(BloomGraphics.GUI_TEXTURED, icon, x, y, 0.0F, 0.0F, SIZE, SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE,
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
	public void onPress() {
		this.action.run();
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
