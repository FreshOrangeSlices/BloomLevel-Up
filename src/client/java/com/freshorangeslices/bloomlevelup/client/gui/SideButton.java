package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.freshorangeslices.bloomlevelup.network.BloomNetwork;

import com.freshorangeslices.bloomlevelup.client.mixin.AbstractContainerScreenAccessor;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads.CombatAction;

/**
 * Item-icon buttons under the Skill Hub button, in Bloom's column outside the inventory: Hoarder's Pocket (a chest) and
 * Rune Embedding (an enchanted book). They only ask; the server opens the screen or says why not.
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
		if (BloomNetwork.canSendToServer()) {
			BloomNetwork.sendToServer(new BloomPayloads.CombatInput(action));
		}
	}

	/** Under the hub button, in Bloom's column beside the inventory (see {@link #columnY}). */
	private void reposition() {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		this.setX(columnX(accessor));
		this.setY(columnY(screen, accessor, this.row));
	}

	/** X of Bloom's column: just outside the inventory panel's right edge (it follows the recipe book). */
	static int columnX(AbstractContainerScreenAccessor accessor) {
		return accessor.bloomlevelup$leftPos() + accessor.bloomlevelup$imageWidth() + 2;
	}

	/**
	 * Y of place {@code index} in Bloom's column (0 = the hub button), top down from the panel's top-right corner.
	 * Buttons other mods put in that spot come first: the column starts under them (FreshOrangeSlices' playtest: under
	 * the panel it ran into Inventory Profiles Next's profile bar). Worked out each frame, so late arrivals count.
	 */
	static int columnY(AbstractContainerScreen<?> screen, AbstractContainerScreenAccessor accessor, int index) {
		int x = columnX(accessor);
		int ours = 0;

		for (GuiEventListener child : screen.children()) {
			if (child instanceof HubButton || child instanceof SideButton) {
				ours++;
			}
		}

		int height = Math.max(1, ours) * (SIZE + 2);
		int top = accessor.bloomlevelup$topPos() + 4;

		for (int pass = 0; pass < 8; pass++) {
			int below = top;

			for (GuiEventListener child : screen.children()) {
				if (child instanceof AbstractWidget widget && !(child instanceof HubButton) && !(child instanceof SideButton) && widget.visible
						&& widget.getX() < x + SIZE && widget.getX() + widget.getWidth() > x
						&& widget.getY() < below + height && widget.getY() + widget.getHeight() > below) {
					below = Math.max(below, widget.getY() + widget.getHeight() + 2);
				}
			}

			if (below == top) {
				break;
			}

			top = below;
		}

		return top + index * (SIZE + 2);
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		BloomGraphics g = BloomGraphics.of(graphics);
		reposition();
		g.buttonBackground(this);
		g.item(this.icon, this.getX() + 2, this.getY() + 2);
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
