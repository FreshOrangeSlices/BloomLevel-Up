package com.freshorangeslices.bloompets.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A button in the Skill Hub's look: dark, a brown edge that turns orange under the mouse, light text (dim when off). */
final class PetButton extends Button {
	PetButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	@Override
	public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int x0 = getX();
		int y0 = getY();
		int x1 = x0 + width;
		int y1 = y0 + height;
		boolean lit = active && isHoveredOrFocused();
		g.fill(x0, y0, x1, y1, !active ? 0xFF1A1714 : lit ? 0xFF3A3128 : 0xFF2A241D);
		PetScreen.frame(g, x0, y0, x1, y1, !active ? 0xFF3A3128 : lit ? PetScreen.ACCENT : PetScreen.PANEL_EDGE);

		if (active) {
			g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0x18FFFFFF);
		}

		Font font = Minecraft.getInstance().font;
		int color = !active ? PetScreen.FAINT : lit ? PetScreen.GOLD : PetScreen.TEXT;
		g.drawCenteredString(font, getMessage(), x0 + width / 2, y0 + (height - 8) / 2, color);
	}
}
