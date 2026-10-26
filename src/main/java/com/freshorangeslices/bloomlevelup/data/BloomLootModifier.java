package com.freshorangeslices.bloomlevelup.data;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.skill.utility.UtilitySkill;

/**
 * Bloom's one global loot modifier ({@code data/bloomlevelup/loot_modifiers/drops.json}, listed in Forge's
 * {@code global_loot_modifiers.json}): hands every loot roll to {@link UtilitySkill#modifyDrops}, which decides by
 * the table (blocks: Yield and treasure; fishing: XP and Triple Catch). It stands in for Fabric's loot event.
 */
public final class BloomLootModifier extends LootModifier {
	private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
			DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BloomLevelUp.MOD_ID);

	public static final java.util.function.Supplier<Codec<BloomLootModifier>> CODEC = Suppliers.memoize(() ->
			RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, BloomLootModifier::new)));

	public static final RegistryObject<Codec<? extends IGlobalLootModifier>> DROPS = SERIALIZERS.register("drops", () -> CODEC.get());

	public BloomLootModifier(LootItemCondition[] conditions) {
		super(conditions);
	}

	public static void register(IEventBus modBus) {
		SERIALIZERS.register(modBus);
	}

	@Override
	protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
		UtilitySkill.modifyDrops(context.getQueriedLootTableId(), context, generatedLoot);
		return generatedLoot;
	}

	@Override
	public Codec<? extends IGlobalLootModifier> codec() {
		return CODEC.get();
	}
}
