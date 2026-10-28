package com.freshorangeslices.bloomlevelup.client.compat;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.client.gui.SideButton;

/**
 * JEI (optional): Bloom's button column beside the inventory is an area JEI keeps its item list and bookmarks clear of.
 * JEI finds this class by its annotation and is the only thing that loads it, so Bloom runs the same without JEI.
 */
@JeiPlugin
public final class JeiCompat implements IModPlugin {
	private static final ResourceLocation ID = new ResourceLocation(BloomLevelUp.MOD_ID, "buttons");

	@Override
	public ResourceLocation getPluginUid() {
		return ID;
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGenericGuiContainerHandler(AbstractContainerScreen.class, new IGuiContainerHandler<AbstractContainerScreen<?>>() {
			@Override
			public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen<?> screen) {
				return SideButton.columnAreas(screen);
			}
		});
	}
}
