package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** One place for screen switching, so a future rename in Minecraft's API is a one-line fix. */
public final class ClientScreens {
	private ClientScreens() {
	}

	public static void open(Screen screen) {
		Minecraft.getInstance().setScreenAndShow(screen);
	}
}
