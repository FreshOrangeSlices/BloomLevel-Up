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
 * The pet screen's tab on the inventory: just left of Bloom's Skill Hub button when Bloom is there (found by its class,
 * so Bloom Pets doesn't need Bloom), else beside the inventory's top-left corner. Placed every frame, so it follows
 * the recipe book and Bloom's own buttons.
 */
public final class PetsTabButton extends Button {
	private static final ItemStack ICON = new ItemStack(Items.LEAD);
	private final AbstractContainerScreen<?> screen;

	PetsTabButton(AbstractContainerScreen<?> screen) {
		super(0, 0, 20, 20, Component.empty(), button -> PetsClient.request(), DEFAULT_NARRATION);
		this.screen = screen;
		setTooltip(Tooltip.create(Component.translatable("bloompets.screen.open")));
		place();
	}

	private void place() {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget widget && widget != this && widget.getClass().getSimpleName().equals("HubButton")) {
				setX(widget.getX() - width - 2);
				setY(widget.getY());
				return;
			}
		}

		setX(screen.getGuiLeft() - width - 2);
		setY(screen.getGuiTop());
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		place();
		super.renderWidget(graphics, mouseX, mouseY, partialTick);
		graphics.renderItem(ICON, getX() + 2, getY() + 2);
	}
}
