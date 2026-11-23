package com.freshorangeslices.legionwarex.world;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Other mods' compat code that would run before the mod it reaches for is built, held until common setup.
 *
 * <p>Copper Age Backport (0.1.4) sets up its Sophisticated Storage compat in its own constructor, and that touches
 * {@code StoragePacketHandler.INSTANCE}. Sophisticated Storage only learns its network protocol version in its own
 * constructor, so when Copper Age is built first the channel gets a null protocol, the class fails to load
 * ({@code ExceptionInInitializerError} from a {@code NullPointerException}) and the game doesn't start. Copper Age
 * catches {@code Exception} around its compat modules, not errors. The mixin
 * ({@code mixin/CopperAgeStorageCompatMixin}) hands the compat here instead of letting it run; at common setup, once
 * every mod is built, it runs as written, so copper golems still use Sophisticated chests.
 */
public final class LateCompat {
	private static final Logger LOG = LogUtils.getLogger();
	private static final List<Object> HELD = new ArrayList<>();
	private static boolean ready;

	private LateCompat() {
	}

	/** From the mixin, as a compat module's {@code init()} starts: true holds it (the mixin cancels the call). */
	public static synchronized boolean hold(Object compat) {
		if (ready) {
			return false;
		}

		HELD.add(compat);
		LOG.info("LegionWareX compat: {} waits for common setup (its mod isn't built yet)", compat.getClass().getName());
		return true;
	}

	/** On the mod bus. The work runs on the main thread after every mod's own common setup. */
	public static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(LateCompat::runHeld);
	}

	/** Runs every held compat's {@code init()} as written; how many ran. From now on nothing is held. */
	public static synchronized int runHeld() {
		ready = true;
		int ran = 0;

		for (Object compat : List.copyOf(HELD)) {
			String name = compat.getClass().getName();

			try {
				compat.getClass().getMethod("init").invoke(compat);
				ran++;
				LOG.info("LegionWareX compat: {} ran at common setup", name);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				LOG.error("LegionWareX compat: {} failed at common setup", name, e);
			}
		}

		HELD.clear();
		return ran;
	}

	/** For the game tests: hold again, as during mod construction. */
	public static synchronized void holdAgainForTest() {
		ready = false;
	}
}
