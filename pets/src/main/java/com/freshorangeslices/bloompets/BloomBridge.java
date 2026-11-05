package com.freshorangeslices.bloompets;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What Bloom Pets asks of Bloom, without compiling against it: whether a player has One and the Same on (Bloom's
 * {@code BloomApi.isMajorActive}, found by name), Bloom's Bleed, and Bloom's boss list ({@code #bloomlevelup:bosses}).
 * Without Bloom: nobody has One and the Same, nothing Bleeds, and nothing is a boss.
 */
public final class BloomBridge {
	public static final String ONE_AND_THE_SAME = "bloomlevelup:combat/one_and_the_same";
	private static final ResourceLocation BLEED = new ResourceLocation("bloomlevelup", "bleed");
	public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("bloomlevelup", "bosses"));
	/** For the game tests (Bloom isn't in Bloom Pets' test run): players who count as having One and the Same on. */
	private static final Map<UUID, Boolean> TEST_OVERRIDES = new ConcurrentHashMap<>();
	private static Method isMajorActive;
	private static boolean looked;
	private static boolean failureLogged;
	private static String status = "not asked yet";

	private BloomBridge() {
	}

	/** Whether the player has One and the Same (owned and on). */
	public static boolean oneAndTheSame(Player player) {
		Boolean override = TEST_OVERRIDES.get(player.getUUID());

		if (override != null) {
			return override;
		}

		Method method = method();

		if (method == null) {
			return false;
		}

		try {
			return Boolean.TRUE.equals(method.invoke(null, player, ONE_AND_THE_SAME));
		} catch (ReflectiveOperationException | RuntimeException e) {
			if (!failureLogged) {
				failureLogged = true;
				status = "found, but asking it failed (" + e + (e.getCause() != null ? ", " + e.getCause() : "") + ")";
				BloomPets.LOGGER.warn("Bloom Pets: asking Bloom's API about One and the Same failed", e);
			}

			return false;
		}
	}

	/** How asking Bloom has gone: found, not found (and why), or found but failing (and why). For logs. */
	public static synchronized String status() {
		return status;
	}

	/** Game tests: this player counts as having One and the Same on (or off). */
	public static void setForTests(UUID player, boolean on) {
		TEST_OVERRIDES.put(player, on);
	}

	private static synchronized Method method() {
		if (!looked) {
			looked = true;

			try {
				isMajorActive = Class.forName("com.freshorangeslices.bloomlevelup.api.BloomApi").getMethod("isMajorActive", Player.class, String.class);
				status = "found";
				BloomPets.LOGGER.info("Bloom Pets: Bloom's API found (One and the Same is asked of it)");
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				isMajorActive = null;
				status = "not found (" + e + ")";
				BloomPets.LOGGER.info("Bloom Pets: Bloom's API not found ({}): no pet trees", e.toString());
			}
		}

		return isMajorActive;
	}

	/** Bloom's Bleed, or null without Bloom. */
	public static MobEffect bleed() {
		return ForgeRegistries.MOB_EFFECTS.getValue(BLEED);
	}

	public static boolean boss(Entity entity) {
		return entity.getType().is(BOSSES);
	}
}
