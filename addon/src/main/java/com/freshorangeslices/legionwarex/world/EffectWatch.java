package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * A detector (FreshOrangeSlices, 2026-10-30: plain zombies seen with long regeneration, invisibility, strength and
 * resistance, which no mod in the pack seems to give): the first time a zombie gets a long effect (over a minute, or
 * endless) from a given place in the code, the server log says which, as "LegionWareX effects: ...", with the calls
 * that led there (mixins show as "handler$" methods in Minecraft's own classes). Once per effect and place, so the log
 * stays short.
 */
public final class EffectWatch {
	private static final int LONG = 1200;
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	private static final StackWalker WALKER = StackWalker.getInstance();

	private EffectWatch() {
	}

	@SubscribeEvent
	public static void onEffectAdded(MobEffectEvent.Added event) {
		MobEffectInstance effect = event.getEffectInstance();

		if (!(event.getEntity() instanceof Zombie zombie) || zombie.level().isClientSide()
				|| !(effect.isInfiniteDuration() || effect.getDuration() > LONG)) {
			return;
		}

		List<String> calls = WALKER.walk(frames -> frames.skip(1)
				.map(frame -> frame.getClassName() + "." + frame.getMethodName())
				.filter(call -> !call.startsWith("net.minecraftforge.eventbus.") && !call.startsWith("java.") && !call.startsWith("jdk.")
						&& !call.startsWith(EffectWatch.class.getName()) && !call.startsWith("net.minecraftforge.event.ForgeEventFactory"))
				.limit(8).toList());
		String effectId = String.valueOf(ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect()));
		String where = calls.isEmpty() ? "?" : calls.get(0);

		if (!SEEN.add(effectId + " " + where) || SEEN.size() > 200) {
			return;
		}

		Entity source = event.getEffectSource();
		LegionWareX.LOGGER.info("LegionWareX effects: {} got {} (level {}, {}, particles {}) from {}; calls: {}",
				ForgeRegistries.ENTITY_TYPES.getKey(zombie.getType()), effectId, effect.getAmplifier() + 1,
				effect.isInfiniteDuration() ? "endless" : effect.getDuration() + " ticks", effect.isVisible(),
				source == null ? "no entity" : ForgeRegistries.ENTITY_TYPES.getKey(source.getType()), String.join(" < ", calls));
	}
}
