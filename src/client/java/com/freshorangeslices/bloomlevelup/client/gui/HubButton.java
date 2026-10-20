package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
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

	/** Survival inventory: right of the recipe book button. Creative: just outside the panel's top-right corner. */
	private void reposition() {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;

		if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
			this.setX(accessor.bloomlevelup$leftPos() + 126);
			this.setY(accessor.bloomlevelup$topPos() + 60);
		} else {
			this.setX(accessor.bloomlevelup$leftPos() + accessor.bloomlevelup$imageWidth() + 2);
			this.setY(accessor.bloomlevelup$topPos() + 4);
		}
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		reposition();
		this.extractDefaultSprite(g);
		g.blit(RenderPipelines.GUI_TEXTURED, IconTextures.PLACEHOLDER, this.getX() + 2, this.getY() + 2, 0.0F, 0.0F, 16, 16,
				IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		ClientScreens.open(new HubScreen());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
