package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.freshorangeslices.bloomlevelup.client.mixin.AbstractContainerScreenAccessor;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;

/** Hoarder's Pocket: a chest button under the Skill Hub button. The server opens the pocket (or says why not). */
public final class PocketButton extends AbstractButton {
	private static final int SIZE = 20;
	private static final ItemStack ICON = new ItemStack(Items.CHEST);
	private final AbstractContainerScreen<?> screen;

	public PocketButton(AbstractContainerScreen<?> screen) {
		super(0, 0, SIZE, SIZE, Component.translatable("bloom.misc.pocket.open"));
		this.screen = screen;
		this.setTooltip(Tooltip.create(Component.translatable("bloom.misc.pocket.open")));
		reposition();
	}

	private void reposition() {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		this.setX(accessor.bloomlevelup$leftPos() + accessor.bloomlevelup$imageWidth() + 2);
		this.setY(accessor.bloomlevelup$topPos() + 4 + SIZE + 2);
	}

	public static void open() {
		if (ClientPlayNetworking.canSend(BloomPayloads.CombatInput.TYPE)) {
			ClientPlayNetworking.send(new BloomPayloads.CombatInput(BloomPayloads.CombatAction.OPEN_POCKET));
		}
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		reposition();
		this.extractDefaultSprite(g);
		g.item(ICON, this.getX() + 2, this.getY() + 2);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		open();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
