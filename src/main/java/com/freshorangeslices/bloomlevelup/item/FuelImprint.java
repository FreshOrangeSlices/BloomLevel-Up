package com.freshorangeslices.bloomlevelup.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffect;

/**
 * Become My Fuel: the harmful effect a weapon carries (item data, so it stays with the weapon whoever holds it).
 * Every hit with the weapon applies it at this amplifier.
 */
public record FuelImprint(Holder<MobEffect> effect, int amplifier) {
	public static final Codec<FuelImprint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			MobEffect.CODEC.fieldOf("effect").forGetter(FuelImprint::effect),
			Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(FuelImprint::amplifier)
	).apply(instance, FuelImprint::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, FuelImprint> STREAM_CODEC = StreamCodec.composite(
			MobEffect.STREAM_CODEC, FuelImprint::effect,
			ByteBufCodecs.VAR_INT, FuelImprint::amplifier,
			FuelImprint::new);
}
