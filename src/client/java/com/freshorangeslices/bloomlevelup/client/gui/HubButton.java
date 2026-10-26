package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import com.freshorangeslices.bloomlevelup.client.IconTextures;
import com.freshorangeslices.bloomlevelup.client.mixin.AbstractContainerScreenAccessor;

/**
 * The "open Skill Hub" button that sits just outside the top-right corner of the inventory panel.
 * It re-reads the panel position every frame, so it follows the inventory when the recipe book opens.
 */
public final class HubButton extends AbstractButton {
	private static final int SIZE = 20;
	private final AbstractContainerScreen<?> screen;

	public HubButton(AbstractContainerScreen<?> screen) {
		super(0, 0, SIZE, SIZE, Component.translatable("bloom.hub.open"));
		this.screen = screen;
		this.setTooltip(Tooltip.create(Component.translatable("bloom.hub.open")));
		reposition();
	}

	/**
	 * Survival inventory: the right end of Bloom's row just under the panel's bottom-right corner (other mods crowd the
	 * top, the left and the space around the player). Creative: just outside the panel's top-right corner.
	 */
	private void reposition() {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;

		if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
			this.setX(SideButton.rowX(accessor, 0));
			this.setY(SideButton.rowY(accessor));
		} else {
			this.setX(accessor.bloomlevelup$leftPos() + accessor.bloomlevelup$imageWidth() + 2);
			this.setY(accessor.bloomlevelup$topPos() + 4);
		}
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		BloomGraphics g = BloomGraphics.of(graphics);
		reposition();
		g.buttonBackground(this);
		g.blit(BloomGraphics.GUI_TEXTURED, IconTextures.PLACEHOLDER, this.getX() + 2, this.getY() + 2, 0.0F, 0.0F, 16, 16,
				IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE);
	}

	@Override
	public void onPress() {
		ClientScreens.open(new HubScreen());
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
