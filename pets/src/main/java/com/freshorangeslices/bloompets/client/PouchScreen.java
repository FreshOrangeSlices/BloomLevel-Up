package com.freshorangeslices.bloompets.client;

import com.freshorangeslices.bloompets.PouchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** A companion's food pouch: the hopper's screen with its row of five painted over and two slots in the middle. */
public final class PouchScreen extends AbstractContainerScreen<PouchMenu> {
	private static final ResourceLocation HOPPER = new ResourceLocation("textures/gui/container/hopper.png");
	/** The hopper picture's panel grey, and where its first slot's frame is. */
	private static final int GREY = 0xFFC6C6C6;
	private static final int FRAME_X = 43;
	private static final int FRAME_Y = 19;

	public PouchScreen(PouchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageHeight = 133;
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderBackground(g);
		super.render(g, mouseX, mouseY, partialTick);
		renderTooltip(g, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		g.blit(HOPPER, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		g.fill(leftPos + FRAME_X, topPos + FRAME_Y, leftPos + FRAME_X + 5 * 18, topPos + FRAME_Y + 18, GREY);

		for (int i = 0; i < PouchMenu.SLOTS; i++) {
			g.blit(HOPPER, leftPos + PouchMenu.SLOT_X - 1 + i * 18, topPos + FRAME_Y, FRAME_X, FRAME_Y, 18, 18);
		}
	}
}
