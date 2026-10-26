package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A small gold-rimmed plate along the top of the Skill Hub, in the same golds as the window's frame
 * ({@code textures/gui/sprites/hub/frame.png}), showing an item or a 64x64 picture at 16x16: the category tabs
 * (an item each; the open tab is lit and can't be clicked) and the two reset buttons (a picture each; greyed
 * out while they can't be used). The name or explanation shows as a tooltip.
 */
final class HubTab extends AbstractButton {
	static final int OUTLINE = 0xFF2B1A06;
	static final int SHINE = 0xFFFFF1A8;
	static final int LIGHT = 0xFFF2C443;
	static final int MID = 0xFFC98F1F;
	static final int DEEP = 0xFFA06E16;
	static final int SHADOW = 0xFF6B430C;
	private static final int PICTURE_SIZE = 64;

	private final ItemStack item;
	private final ResourceLocation picture;
	private final boolean selected;
	private final Runnable action;

	/** A category tab showing {@code item}. */
	static HubTab tab(int x, int y, int width, int height, Component name, ItemStack item, boolean selected, Runnable action) {
		return new HubTab(x, y, width, height, name, item, null, selected, action);
	}

	/** A button showing a 64x64 {@code picture}; {@code active} false greys it out. */
	static HubTab button(int x, int y, int size, Component tooltip, ResourceLocation picture, boolean active, Runnable action) {
		HubTab button = new HubTab(x, y, size, size, tooltip, ItemStack.EMPTY, picture, false, action);
		button.active = active;
		return button;
	}

	private HubTab(int x, int y, int width, int height, Component label, ItemStack item, ResourceLocation picture, boolean selected, Runnable action) {
		super(x, y, width, height, label);
		this.item = item;
		this.picture = picture;
		this.selected = selected;
		this.action = action;
		this.active = !selected;
		this.setTooltip(Tooltip.create(label));
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		BloomGraphics g = BloomGraphics.of(graphics);
		int x0 = this.getX();
		int y0 = this.getY();
		int x1 = x0 + this.getWidth();
		int y1 = y0 + this.getHeight();
		boolean hover = !selected && this.active && this.isHoveredOrFocused();
		boolean dim = !selected && !this.active;

		g.fill(x0, y0, x1, y1, OUTLINE);
		// Gold rim: lit along the top and left, shaded along the bottom and right.
		g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, selected ? LIGHT : hover ? MID : dim ? SHADOW : DEEP);
		g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, selected ? SHINE : hover ? LIGHT : dim ? DEEP : MID);
		g.fill(x0 + 1, y0 + 2, x0 + 2, y1 - 1, selected ? SHINE : hover ? LIGHT : dim ? DEEP : MID);
		g.fill(x0 + 2, y1 - 2, x1 - 1, y1 - 1, SHADOW);
		g.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 2, SHADOW);
		g.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, selected ? 0xFF4A3A22 : hover ? 0xFF33291D : 0xFF201A13);

		int cx = (x0 + x1) / 2;
		int cy = (y0 + y1) / 2;

		if (picture != null) {
			g.blit(BloomGraphics.GUI_TEXTURED, picture, cx - 8, cy - 8, 0.0F, 0.0F, 16, 16, PICTURE_SIZE, PICTURE_SIZE, PICTURE_SIZE, PICTURE_SIZE,
					dim ? 0xFF5A5A5A : 0xFFFFFFFF);
		} else if (!item.isEmpty()) {
			g.item(item, cx - 8, cy - 8);
		}
	}

	@Override
	public void onPress() {
		this.action.run();
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
