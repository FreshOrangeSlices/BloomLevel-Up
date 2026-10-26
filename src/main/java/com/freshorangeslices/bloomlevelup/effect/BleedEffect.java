package com.freshorangeslices.bloomlevelup.effect;

import org.joml.Vector3f;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

import com.freshorangeslices.bloomlevelup.skill.combat.BloomDamage;

/**
 * Bleed (Slash): half a heart per second that ignores armor (Resistance still applies).
 * Reapplying refreshes the duration; instances never stack. It's applied without the potion swirl and shows
 * dark red drips instead, with a spatter on every damage tick.
 */
public final class BleedEffect extends MobEffect {
	private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0x7A / 255.0F, 0x0A / 255.0F, 0x12 / 255.0F), 1.1F);

	public BleedEffect() {
		super(MobEffectCategory.HARMFUL, 0x9B111E);
	}

	@Override
	public boolean isDurationEffectTick(int duration, int amplifier) {
		return duration % 5 == 0;
	}

	@Override
	public void applyEffectTick(LivingEntity entity, int amplifier) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		double width = entity.getBbWidth();
		double height = entity.getBbHeight();
		level.sendParticles(BLOOD, entity.getX(), entity.getY(0.55), entity.getZ(), 2, width * 0.3, height * 0.2, width * 0.3, 0.0);

		// Damage once a second (every 4th call).
		if (entity.tickCount % 20 < 5) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), entity.getX(), entity.getY(0.6),
					entity.getZ(), 6, width * 0.25, height * 0.15, width * 0.25, 0.05);
			entity.hurt(BloomDamage.bleed(level), 1.0F + amplifier);
		}
	}
}
