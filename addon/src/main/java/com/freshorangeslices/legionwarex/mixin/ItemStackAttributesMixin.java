package com.freshorangeslices.legionwarex.mixin;

import com.freshorangeslices.legionwarex.magic.SpellEngineItems;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * After Spell Engine's own mixin on the same method has replaced a Spell Engine item's attributes (priority 1500: applied
 * after the default 1000, so this runs after it), Forge's attribute event is fired again on that result, so other mods'
 * and the add-on's additions count for those items too ({@link SpellEngineItems}).
 */
@Mixin(value = ItemStack.class, priority = 1500)
public abstract class ItemStackAttributesMixin {
	@Inject(method = "getAttributeModifiers", at = @At("RETURN"), cancellable = true)
	private void legionwarex$eventAfterSpellEngine(EquipmentSlot slot, CallbackInfoReturnable<Multimap<Attribute, AttributeModifier>> info) {
		ItemStack stack = (ItemStack) (Object) this;

		if (SpellEngineItems.replaced(stack)) {
			info.setReturnValue(ForgeHooks.getAttributeModifiers(stack, slot, info.getReturnValue()));
		}
	}
}
