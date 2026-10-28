package com.freshorangeslices.bloomlevelup.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.freshorangeslices.bloomlevelup.network.BloomNetwork;

import com.freshorangeslices.bloomlevelup.client.mixin.AbstractContainerScreenAccessor;
import com.freshorangeslices.bloomlevelup.client.mixin.RecipeBookComponentAccessor;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads.CombatAction;

/**
 * Item-icon buttons under the Skill Hub button, in Bloom's column outside the inventory: Hoarder's Pocket (a chest) and
 * Rune Embedding (an enchanted book). They only ask; the server opens the screen or says why not.
 */
public final class SideButton extends AbstractButton {
	private static final int SIZE = 20;
	/** Space between Bloom's column and what it sits beside. */
	private static final int GAP = 2;
	/** The recipe book draws its chosen tab this much left of where the tab is. */
	private static final int CHOSEN_TAB_SHIFT = 2;
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

	/** Under the hub button, in Bloom's column beside the inventory (see {@link #column}, {@link #columnY}). */
	private void reposition() {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		this.setX(column(screen, accessor).x());
		this.setY(columnY(screen, accessor, this.row));
	}

	/** Where Bloom's column goes: its x, and the y it starts from before other mods' buttons push it down. */
	record Column(int x, int top) {
	}

	/**
	 * Bloom's column: just outside the inventory panel's left edge, from its top (FreshOrangeSlices' playtest: outside
	 * the right edge, the game's status effects covered it, and JEI's item list fills that side). With the recipe book
	 * open: left of the book's category tabs; where there isn't room for that (the game's 854×480 window, and 720p, 1440p
	 * and 4K at the automatic GUI scale), under the tabs; and if that runs off the bottom too, outside the panel's right
	 * edge as before.
	 */
	static Column column(AbstractContainerScreen<?> screen, AbstractContainerScreenAccessor accessor) {
		int leftPos = accessor.bloomlevelup$leftPos();
		int top = accessor.bloomlevelup$topPos() + 4;
		List<? extends AbstractWidget> tabs = openRecipeBookTabs(screen);
		int tabsLeft = tabs.stream().mapToInt(AbstractWidget::getX).min().orElse(Integer.MAX_VALUE);
		int x = Math.min(leftPos, tabsLeft - CHOSEN_TAB_SHIFT) - GAP - SIZE;

		if (x >= GAP) {
			return new Column(x, top);
		}

		if (!tabs.isEmpty()) {
			int under = tabs.stream().mapToInt(tab -> tab.getY() + tab.getHeight()).max().getAsInt() + GAP;

			if (under + ownButtons(screen) * (SIZE + GAP) <= screen.height) {
				return new Column(tabsLeft, under);
			}
		}

		return new Column(leftPos + accessor.bloomlevelup$imageWidth() + GAP, top);
	}

	/** The open recipe book's tabs that show (none when the screen has no recipe book or it's closed). */
	private static List<? extends AbstractWidget> openRecipeBookTabs(Screen screen) {
		if (screen instanceof RecipeUpdateListener listener && listener.getRecipeBookComponent().isVisible()) {
			RecipeBookComponent book = listener.getRecipeBookComponent();
			return ((RecipeBookComponentAccessor) book).bloomlevelup$tabButtons().stream().filter(tab -> tab.visible).toList();
		}

		return List.of();
	}

	private static int ownButtons(Screen screen) {
		int ours = 0;

		for (GuiEventListener child : screen.children()) {
			if (child instanceof HubButton || child instanceof SideButton) {
				ours++;
			}
		}

		return Math.max(1, ours);
	}

	/** Where Bloom's buttons are on {@code screen} right now (none on other screens), for JEI to keep its lists clear of. */
	public static List<Rect2i> columnAreas(Screen screen) {
		List<Rect2i> areas = new ArrayList<>();

		for (GuiEventListener child : screen.children()) {
			if ((child instanceof HubButton || child instanceof SideButton) && child instanceof AbstractWidget widget && widget.visible) {
				areas.add(new Rect2i(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
			}
		}

		return areas;
	}

	/**
	 * Y of place {@code index} in Bloom's column (0 = the hub button), top down from where {@link #column} starts it.
	 * Buttons other mods put in that spot come first: the column starts under them (FreshOrangeSlices' playtest: under
	 * the panel it ran into Inventory Profiles Next's profile bar). Worked out each frame, so late arrivals count.
	 */
	static int columnY(AbstractContainerScreen<?> screen, AbstractContainerScreenAccessor accessor, int index) {
		Column column = column(screen, accessor);
		int x = column.x();
		int height = ownButtons(screen) * (SIZE + GAP);
		int top = column.top();

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
