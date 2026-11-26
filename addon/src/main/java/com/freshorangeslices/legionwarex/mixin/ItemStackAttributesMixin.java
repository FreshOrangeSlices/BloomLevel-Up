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
 * For Spell Engine's items, the answer comes at the method's head: Spell Engine's own attributes for the slot, put
 * through Forge's attribute event, so other mods' and the add-on's additions count ({@link SpellEngineItems}). Spell
 * Engine's own mixin, at the method's return, cancels there with its attributes alone, so a fix at the return would
 * never run (the pack test's magic check: still "no copy" with one).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackAttributesMixin {
	@Inject(method = "getAttributeModifiers", at = @At("HEAD"), cancellable = true)
	private void legionwarex$spellEngineThroughTheEvent(EquipmentSlot slot, CallbackInfoReturnable<Multimap<Attribute, AttributeModifier>> info) {
		ItemStack stack = (ItemStack) (Object) this;
		Multimap<Attribute, AttributeModifier> own = SpellEngineItems.forSlot(stack, slot);

		if (own != null) {
			info.setReturnValue(ForgeHooks.getAttributeModifiers(stack, slot, own));
		}
	}
}
