package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * No endless buffs on zombies (FreshOrangeSlices, 2026-10-30: plain zombies kept turning up with regeneration,
 * invisibility, strength or resistance lasting 2^31 ticks, confusing to fight; it began before Born in Chaos joined the
 * pack, and no mod's code or data in the pack gives that duration). A good effect over an hour long is refused for any
 * zombie (husks, drowned and modded zombies too), whoever gives it, and taken off zombies already in a world when they
 * load. Short ones (a witch's potion, a spell) and harmful ones are left alone. The first refusal from each place in the
 * code is logged as "LegionWareX buffs: ..." with the calls that led there, so the source can be found.
 */
public final class ZombieBuffs {
	/** One hour: anything longer counts as endless. */
	public static final int LONG = 72000;
	private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
	private static final StackWalker WALKER = StackWalker.getInstance();

	private ZombieBuffs() {
	}

	/** An endless good effect on a zombie: the kind this refuses. */
	public static boolean refused(LivingEntity entity, MobEffectInstance effect) {
		return entity instanceof Zombie && effect.getEffect().getCategory() == MobEffectCategory.BENEFICIAL
				&& (effect.isInfiniteDuration() || effect.getDuration() > LONG) && !AddonConfig.zombieEndlessBuffs();
	}

	@SubscribeEvent
	public static void onApplicable(MobEffectEvent.Applicable event) {
		LivingEntity entity = event.getEntity();

		if (entity.level().isClientSide() || !refused(entity, event.getEffectInstance())) {
			return;
		}

		event.setResult(Event.Result.DENY);
		log("refused", entity, event.getEffectInstance());
	}

	/** Given past the check above (forceAddEffect skips it): only logged, so the source shows; it goes when the zombie next loads. */
	@SubscribeEvent
	public static void onAdded(MobEffectEvent.Added event) {
		if (!event.getEntity().level().isClientSide() && refused(event.getEntity(), event.getEffectInstance())) {
			log("forced past the check", event.getEntity(), event.getEffectInstance());
		}
	}

	private static void log(String what, LivingEntity entity, MobEffectInstance effect) {
		List<String> calls = WALKER.walk(frames -> frames.skip(1)
				.map(frame -> frame.getClassName() + "." + frame.getMethodName())
				.filter(call -> !call.startsWith("net.minecraftforge.eventbus.") && !call.startsWith("java.") && !call.startsWith("jdk.")
						&& !call.startsWith(ZombieBuffs.class.getName()) && !call.startsWith("net.minecraftforge.event.ForgeEventFactory"))
				.limit(8).toList());
		String effectId = String.valueOf(ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect()));

		if (LOGGED.size() < 100 && LOGGED.add(what + " " + effectId + " " + (calls.isEmpty() ? "?" : calls.get(0)))) {
			LegionWareX.LOGGER.info("LegionWareX buffs: {} {} (level {}, {}) on {}; calls: {}", what, effectId, effect.getAmplifier() + 1,
					effect.isInfiniteDuration() ? "endless" : effect.getDuration() + " ticks", ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()),
					String.join(" < ", calls));
		}
	}

	/** Zombies saved with one keep it until they load again: then it goes. */
	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Zombie zombie) || zombie.getActiveEffects().isEmpty()) {
			return;
		}

		List<MobEffect> endless = zombie.getActiveEffects().stream().filter(effect -> refused(zombie, effect)).map(MobEffectInstance::getEffect).toList();
		endless.forEach(zombie::removeEffect);
	}
}
