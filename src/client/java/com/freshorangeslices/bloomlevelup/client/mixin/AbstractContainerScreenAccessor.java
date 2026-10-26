package com.freshorangeslices.bloomlevelup.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Read-only access to an inventory screen's panel position, so the hub button can sit beside it. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int bloomlevelup$leftPos();

	@Accessor("topPos")
	int bloomlevelup$topPos();

	@Accessor("imageWidth")
	int bloomlevelup$imageWidth();

	@Accessor("imageHeight")
	int bloomlevelup$imageHeight();
}
