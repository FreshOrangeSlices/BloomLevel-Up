package com.freshorangeslices.legionwarex.world;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/**
 * Takes the switched-off enchantments ({@link DeadEnchantments}) off whatever any loot table makes (chests, fishing,
 * mobs, blocks); a book left with none becomes a plain book (data/legionwarex/loot_modifiers/dead_enchantments.json).
 */
public final class DeadEnchantmentLoot extends LootModifier {
	public static final Supplier<Codec<DeadEnchantmentLoot>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(instance -> codecStart(instance)
			.apply(instance, DeadEnchantmentLoot::new)));

	public DeadEnchantmentLoot(LootItemCondition[] conditions) {
		super(conditions);
	}

	@Override
	protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
		for (int i = 0; i < loot.size(); i++) {
			loot.set(i, DeadEnchantments.strip(loot.get(i)));
		}

		return loot;
	}

	@Override
	public Codec<? extends IGlobalLootModifier> codec() {
		return CODEC.get();
	}
}
