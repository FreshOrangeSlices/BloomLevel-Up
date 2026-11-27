package com.freshorangeslices.legionwarex.jei;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.nio.file.Path;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * The pack test's JEI audit (FreshOrangeSlices, 2026-11-26: "can we look over all of JEI and double check if it's up to
 * date with everything"). Does nothing unless the client was started with {@code -Dlegionwarex.jei=<file>}: then, once
 * JEI is running in a world, {@link JeiAuditDump} writes every registered item beside what JEI knows, shows and can
 * show a recipe for, and JEI's recipe categories, for launch_test.py to compare with the pack's own hidden items. JEI
 * finds this class by its annotation and only where JEI is installed; nothing else in the add-on touches JEI.
 */
@JeiPlugin
public final class JeiAudit implements IModPlugin {
	private static final ResourceLocation UID = new ResourceLocation(LegionWareX.MOD_ID, "audit");

	@Override
	public ResourceLocation getPluginUid() {
		return UID;
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		String file = System.getProperty("legionwarex.jei");

		if (file != null && !file.isBlank()) {
			JeiAuditDump.arm(runtime, Path.of(file));
		}
	}
}
