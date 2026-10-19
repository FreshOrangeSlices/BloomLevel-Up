package com.freshorangeslices.bloomlevelup.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import com.freshorangeslices.bloomlevelup.skill.combat.BloomDamage;

/**
 * Bleed (Spear Impale): half a heart per second that ignores armor (Resistance still applies).
 * Reapplying refreshes the duration; instances never stack.
 */
public final class BleedEffect extends MobEffect {
	public BleedEffect() {
		super(MobEffectCategory.HARMFUL, 0x9B111E);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return duration % 20 == 0;
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		entity.hurtServer(level, BloomDamage.bleed(level), 1.0F + amplifier);
		return true;
	}
}
