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
import com.freshorangeslices.bloomlevelup.network.BloomPayloads.CombatAction;

/**
 * Item-icon buttons beside the Skill Hub button outside the inventory: Hoarder's Pocket (a chest) and Rune Embedding
 * (an enchanted book). They only ask; the server opens the screen or says why not.
 */
public final class SideButton extends AbstractButton {
	private static final int SIZE = 20;
	private final AbstractContainerScreen<?> screen;
	private final int row;
	private final ItemStack icon;
	private final Runnable action;

	private SideButton(AbstractContainerScreen<?> screen, int row, ItemStack icon, Component label, Runnable action) {
		super(0, 0, SIZE, SIZE, label);
		this.screen = screen;
		this.row = row;
		this.icon = icon;
		this.action = action;
		this.setTooltip(Tooltip.create(label));
		reposition();
	}

	public static SideButton pocket(AbstractContainerScreen<?> screen, int row) {
		return new SideButton(screen, row, new ItemStack(Items.CHEST), Component.translatable("bloom.misc.pocket.open"), SideButton::openPocket);
	}

	public static SideButton runes(AbstractContainerScreen<?> screen, int row) {
		return new SideButton(screen, row, new ItemStack(Items.ENCHANTED_BOOK), Component.translatable("bloom.misc.runes.open"), SideButton::openRunes);
	}

	public static void openPocket() {
		send(CombatAction.OPEN_POCKET);
	}

	public static void openRunes() {
		send(CombatAction.OPEN_RUNES);
	}

	private static void send(CombatAction action) {
		if (ClientPlayNetworking.canSend(BloomPayloads.CombatInput.TYPE)) {
			ClientPlayNetworking.send(new BloomPayloads.CombatInput(action));
		}
	}

	/**
	 * Survival inventory: Bloom's row just under the panel's bottom-right corner, right to left (the hub button at the
	 * right end), clear of what accessory mods put beside the player. Creative: under the hub button outside the panel.
	 */
	private void reposition() {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;

		if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
			this.setX(rowX(accessor, this.row));
			this.setY(rowY(accessor));
		} else {
			this.setX(accessor.bloomlevelup$leftPos() + accessor.bloomlevelup$imageWidth() + 2);
			this.setY(accessor.bloomlevelup$topPos() + 4 + this.row * (SIZE + 2));
		}
	}

	/** X of place {@code index} in Bloom's row under the survival inventory (0 = the right end). */
	static int rowX(AbstractContainerScreenAccessor accessor, int index) {
		return accessor.bloomlevelup$leftPos() + accessor.bloomlevelup$imageWidth() - SIZE - index * (SIZE + 2);
	}

	/** Y of Bloom's row: just under the survival inventory's panel. */
	static int rowY(AbstractContainerScreenAccessor accessor) {
		return accessor.bloomlevelup$topPos() + accessor.bloomlevelup$imageHeight() + 2;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		reposition();
		this.extractDefaultSprite(g);
		g.item(this.icon, this.getX() + 2, this.getY() + 2);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.action.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
