package com.freshorangeslices.bloomlevelup.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.freshorangeslices.bloomlevelup.client.NameTags;

/** Players' nameplates get their {@code [lvl N]} tag. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void bloomlevelup$levelTag(Entity entity, CallbackInfoReturnable<Component> cir) {
		if (entity instanceof Player player) {
			cir.setReturnValue(NameTags.decorate(player, cir.getReturnValue()));
		}
	}
}
