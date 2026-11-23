package com.freshorangeslices.legionwarex.night;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;

/**
 * Myths & Legends' Blood Moon, read from its own saved data by name (no dependency on the mod): whether one is up, and
 * whether one is due tonight. Its {@code BloodMoonSavedData} keeps whether it's on, the day of the last and the day of the
 * next (every 20 to 29 days); a Blood Moon comes the night of a day at or past its next. Without the mod: never.
 */
public final class BloodMoon {
	private static final String DATA = "com.ovinter.mythsandlegends.api.data.BloodMoonSavedData";
	/** For the game tests: a Blood Moon up (and due) when true, none when false; null asks the mod. */
	public static Boolean testUp;
	private static boolean looked;
	private static Method get;
	private static Method isActive;
	private static Method next;

	private BloodMoon() {
	}

	private static Object data(ServerLevel level) {
		if (!looked) {
			looked = true;

			try {
				Class<?> type = Class.forName(DATA);
				get = type.getMethod("get", ServerLevel.class);
				isActive = type.getMethod("isActive");
				next = type.getMethod("getNextBloodMoon");
				LegionWareX.LOGGER.info("LegionWareX night: Myths & Legends' Blood Moon found; themed nights keep off its nights");
			} catch (ReflectiveOperationException | LinkageError e) {
				get = null;
			}
		}

		if (get == null) {
			return null;
		}

		try {
			return get.invoke(null, level.getServer().overworld());
		} catch (ReflectiveOperationException | RuntimeException e) {
			LegionWareX.LOGGER.warn("LegionWareX night: couldn't read Myths & Legends' Blood Moon: {}", e.toString());
			get = null;
			return null;
		}
	}

	/** Whether a Blood Moon is up now. */
	public static boolean up(ServerLevel level) {
		if (testUp != null) {
			return testUp;
		}

		Object data = data(level);

		try {
			return data != null && (boolean) isActive.invoke(data);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/**
	 * Whether the night of this day is the Blood Moon's: one is up, or one is due today (or was yesterday, the server off
	 * that night). One overdue by more is taken as not coming (Myths & Legends' own rule turned off, say), so it can't hold
	 * the themed nights back for good.
	 */
	public static boolean tonight(ServerLevel level, long day) {
		if (testUp != null) {
			return testUp;
		}

		Object data = data(level);

		try {
			if (data == null) {
				return false;
			}

			long due = (long) next.invoke(data);
			return (boolean) isActive.invoke(data) || due >= 0L && day >= due && day - due <= 1L;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** The day the next Blood Moon is due, or -1 (none, or no Myths & Legends). */
	public static long nextDay(ServerLevel level) {
		Object data = data(level);

		try {
			return data == null ? -1L : (long) next.invoke(data);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return -1L;
		}
	}
}
