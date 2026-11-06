package com.freshorangeslices.legionwarex.armor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/**
 * The dot in the bottom-left corner of one of the player's armor slots (FreshOrangeSlices, 2026-11-05: inside the slot,
 * bottom left, a green dot): green while the piece shows, red while it's hidden. A click on it is the dot's (the slot
 * under it doesn't take it); anywhere else in the slot is the slot's. Placed every frame by the slot it belongs to (the
 * recipe book moves the inventory; the creative inventory has its armor slots only on its inventory tab), and drawn
 * over the slot's item ({@link ArmorClient}'s foreground hook), since a button is drawn before a container's items.
 */
final class ArmorDot extends Button {
	/** Its click area (a little more than the dot, so it's easy to hit). */
	static final int W = 6;
	static final int H = 6;
	private final AbstractContainerScreen<?> screen;
	/** The armor index: 0 boots to 3 helmet (the inventory's slot 36 to 39). */
	private final int index;

	ArmorDot(AbstractContainerScreen<?> screen, int index) {
		super(0, 0, W, H, Component.empty(), button -> ArmorClient.toggle(index), DEFAULT_NARRATION);
		this.screen = screen;
		this.index = index;
	}

	/** In its slot's bottom-left corner, or false when the slot isn't on the screen. */
	private boolean place() {
		Player player = Minecraft.getInstance().player;

		if (player == null) {
			return false;
		}

		for (Slot slot : screen.getMenu().slots) {
			if (slot.container == player.getInventory() && slot.getContainerSlot() == 36 + index && slot.isActive()) {
				setX(screen.getGuiLeft() + slot.x);
				setY(screen.getGuiTop() + slot.y + 16 - H);
				return true;
			}
		}

		return false;
	}

	/** Only placed here; drawn over the items by {@link #draw}. */
	@Override
	public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// Off the screen (and not clickable) while its slot isn't there.
		if (!place()) {
			setX(-1000);
			active = false;
			return;
		}

		active = true;
	}

	/** Draws it, the graphics already moved to the inventory's corner (the foreground hook's). */
	void draw(GuiGraphics g, int mouseX, int mouseY) {
		if (!active || !visible) {
			return;
		}

		boolean hidden = (ArmorClient.own() & 1 << index) != 0;
		boolean over = mouseX >= getX() && mouseX < getX() + W && mouseY >= getY() && mouseY < getY() + H;
		int x0 = getX() - screen.getGuiLeft();
		int y0 = getY() - screen.getGuiTop();
		int color = hidden ? (over ? 0xFFFF7A7A : 0xFFE04040) : over ? 0xFF8CFF8C : 0xFF4FD24F;
		// Over the slot's item and its durability bar, under a carried item and tooltips: a round dot with a dark rim.
		g.pose().pushPose();
		g.pose().translate(0.0F, 0.0F, 220.0F);
		g.fill(x0 + 1, y0, x0 + W - 1, y0 + H, 0xC0000000);
		g.fill(x0, y0 + 1, x0 + W, y0 + H - 1, 0xC0000000);
		g.fill(x0 + 2, y0 + 1, x0 + W - 2, y0 + H - 1, color);
		g.fill(x0 + 1, y0 + 2, x0 + W - 1, y0 + H - 2, color);
		// A glint.
		g.fill(x0 + 2, y0 + 2, x0 + 3, y0 + 3, 0x90FFFFFF);
		g.pose().popPose();
	}
}
