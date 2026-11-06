package com.freshorangeslices.legionwarex.armor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/**
 * The eye beside one of the player's armor slots: open while the piece shows, struck through in red while it's hidden.
 * Placed every frame by the slot it belongs to (the recipe book moves the inventory; the creative inventory has its
 * armor slots only on its inventory tab): on the survival inventory just right of the slot, on the edge of the figure's
 * window; on the creative one beside the slot on the side away from the figure.
 */
final class EyeButton extends Button {
	private static final int SIZE = 8;
	/** The eye, a row of 8 pixels a string: X the outline, o the pupil. */
	private static final String[] EYE = {
			"        ",
			"  XXXX  ",
			" X    X ",
			"X  oo  X",
			"X  oo  X",
			" X    X ",
			"  XXXX  ",
			"        "};
	private static final String[] KEYS = {"feet", "legs", "chest", "head"};
	private final AbstractContainerScreen<?> screen;
	/** The armor index: 0 boots to 3 helmet (the inventory's slot 36 to 39). */
	private final int index;
	private Boolean tipFor;

	EyeButton(AbstractContainerScreen<?> screen, int index) {
		super(0, 0, SIZE, SIZE, Component.empty(), button -> ArmorClient.toggle(index), DEFAULT_NARRATION);
		this.screen = screen;
		this.index = index;
	}

	/** Beside its slot, or false when the slot isn't on the screen. */
	private boolean place() {
		Player player = Minecraft.getInstance().player;

		if (player == null) {
			return false;
		}

		for (Slot slot : screen.getMenu().slots) {
			if (slot.container == player.getInventory() && slot.getContainerSlot() == 36 + index && slot.isActive()) {
				boolean creative = screen instanceof CreativeModeInventoryScreen;
				int x = creative && slot.x < 88 ? slot.x - SIZE - 2 : slot.x + 18;
				setX(screen.getGuiLeft() + x);
				setY(screen.getGuiTop() + slot.y + 4);
				return true;
			}
		}

		return false;
	}

	@Override
	public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// Off the screen (and not clickable) while its slot isn't there.
		if (!place()) {
			setX(-1000);
			active = false;
			return;
		}

		active = true;
		boolean hidden = (ArmorClient.own() & 1 << index) != 0;

		if (tipFor == null || tipFor != hidden) {
			tipFor = hidden;
			setTooltip(Tooltip.create(Component.translatable(hidden ? "gui.legionwarex.armor_hidden" : "gui.legionwarex.armor_shown",
					Component.translatable("gui.legionwarex.armor_piece." + KEYS[index]))));
		}

		int x0 = getX();
		int y0 = getY();
		g.fill(x0, y0, x0 + SIZE, y0 + SIZE, isHoveredOrFocused() ? 0xC0303030 : 0x90000000);
		int line = hidden ? 0xFF7A7A7A : isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFD8D8D8;
		int pupil = hidden ? 0xFF5A5A5A : 0xFF6FB7FF;

		for (int row = 0; row < SIZE; row++) {
			for (int column = 0; column < SIZE; column++) {
				char c = EYE[row].charAt(column);

				if (c != ' ') {
					g.fill(x0 + column, y0 + row, x0 + column + 1, y0 + row + 1, c == 'o' ? pupil : line);
				}
			}
		}

		// Hidden: a red stroke through it.
		if (hidden) {
			for (int i = 0; i < SIZE; i++) {
				g.fill(x0 + i, y0 + SIZE - 1 - i, x0 + i + 1, y0 + SIZE - i, 0xFFE04040);
			}
		}
	}
}
