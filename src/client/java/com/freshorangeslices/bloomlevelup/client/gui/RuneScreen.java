package com.freshorangeslices.bloomlevelup.client.gui;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import com.freshorangeslices.bloomlevelup.skill.misc.RuneEmbedding;

/**
 * Rune Embedding: the hopper layout, with armor outlines in the four body-rune slots and a sword outline in
 * the bare-hand slot. Hovering an empty slot says what it takes.
 */
public final class RuneScreen extends AbstractContainerScreen<RuneEmbedding.RuneMenu> {
	private static final ResourceLocation HOPPER = new ResourceLocation("textures/gui/container/hopper.png");

	public RuneScreen(RuneEmbedding.RuneMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 133;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		this.renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, delta);
		this.renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
		int x = (this.width - this.imageWidth) / 2;
		int y = (this.height - this.imageHeight) / 2;
		graphics.blit(HOPPER, x, y, 0, 0, this.imageWidth, this.imageHeight);
	}

	@Override
	protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		if (this.hoveredSlot != null && !this.hoveredSlot.hasItem() && this.menu.getCarried().isEmpty()
				&& this.hoveredSlot.index < RuneEmbedding.RuneMenu.SLOTS) {
			boolean body = RuneEmbedding.isBodySlot(this.hoveredSlot.index);
			String key = body ? "bloom.misc.runes.slot.body" : "bloom.misc.runes.slot.hand";
			List<FormattedCharSequence> lines = new java.util.ArrayList<>();
			lines.add(Component.translatable(key).withStyle(ChatFormatting.WHITE).getVisualOrderText());
			lines.addAll(this.font.split(Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY), 180));
			graphics.renderTooltip(this.font, lines, mouseX, mouseY);
			return;
		}

		super.renderTooltip(graphics, mouseX, mouseY);
	}
}
