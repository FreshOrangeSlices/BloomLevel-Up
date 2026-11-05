package com.freshorangeslices.bloompets.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The pet screen's tab on the inventory: under Bloom's Skill Hub button when Bloom is there (FreshOrangeSlices,
 * 2026-11-05), after Bloom's own buttons in its column (the Hoarder's Pocket and runes buttons, when the player has them),
 * so Bloom's column stays together and never has to move for it; at the end of Bloom's row when Bloom lays its buttons
 * out across the top (the recipe book open on a small screen). Bloom's buttons are found by their class names, so
 * Bloom Pets doesn't need Bloom; without it, the tab sits beside the inventory's top-left corner. Placed every frame, so
 * it follows the recipe book and Bloom's buttons.
 */
public final class PetsTabButton extends Button {
	private static final int GAP = 2;
	private static final ItemStack ICON = new ItemStack(Items.LEAD);
	private final AbstractContainerScreen<?> screen;

	PetsTabButton(AbstractContainerScreen<?> screen) {
		super(0, 0, 20, 20, Component.empty(), button -> PetsClient.request(), DEFAULT_NARRATION);
		this.screen = screen;
		setTooltip(Tooltip.create(Component.translatable("bloompets.screen.open")));
		place();
	}

	private void place() {
		AbstractWidget hub = null;
		AbstractWidget last = null;

		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget widget && widget != this && widget.visible) {
				String name = widget.getClass().getSimpleName();

				if (name.equals("HubButton") || name.equals("SideButton")) {
					hub = name.equals("HubButton") ? widget : hub;

					if (last == null || widget.getY() > last.getY() || widget.getY() == last.getY() && widget.getX() > last.getX()) {
						last = widget;
					}
				}
			}
		}

		if (hub != null) {
			// Bloom's row sits above the panel; its column starts beside it.
			if (hub.getY() + hub.getHeight() <= screen.getGuiTop()) {
				setX(last.getX() + last.getWidth() + GAP);
				setY(last.getY());
			} else {
				setX(hub.getX());
				setY(last.getY() + last.getHeight() + GAP);
			}

			return;
		}

		setX(screen.getGuiLeft() - width - GAP);
		setY(screen.getGuiTop());
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		place();
		super.renderWidget(graphics, mouseX, mouseY, partialTick);
		graphics.renderItem(ICON, getX() + 2, getY() + 2);
	}
}
