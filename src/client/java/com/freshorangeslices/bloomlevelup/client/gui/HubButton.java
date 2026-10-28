package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import com.freshorangeslices.bloomlevelup.client.IconTextures;

/**
 * The "open Skill Hub" button at the top of Bloom's column just outside the inventory panel's left edge.
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
	 * First of Bloom's buttons: the top of its column just outside the panel's top-left corner (beside the recipe book's
	 * tabs when that's open), or under whatever other mods put there ({@link SideButton#place}); the same in the
	 * survival and the creative inventory.
	 */
	private void reposition() {
		SideButton.place(this, screen, 0);
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
