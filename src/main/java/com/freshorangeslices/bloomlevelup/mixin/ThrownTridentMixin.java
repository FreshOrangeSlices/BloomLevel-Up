package com.freshorangeslices.bloomlevelup.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** A piercing trident or thrown weapon (Charged Throw) keeps flying through the mobs it hits instead of bouncing off. */
@Mixin(ThrownTrident.class)
abstract class ThrownTridentMixin {
	@Shadow
	private boolean dealtDamage;

	@Unique
	private boolean bloomlevelup$piercing;

	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void bloomlevelup$decidePierce(EntityHitResult hit, CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		IntOpenHashSet pierced = ((AbstractArrowAccessor) self).bloomlevelup$piercedIds();
		bloomlevelup$piercing = self.getPierceLevel() > 0 && (pierced == null ? 0 : pierced.size()) < self.getPierceLevel();
	}

	/** The bounce off the target (velocity reversed and slowed): skipped while piercing. */
	@WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/ThrownTrident;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
	private void bloomlevelup$keepFlying(ThrownTrident self, Vec3 motion, Operation<Void> original) {
		if (!bloomlevelup$piercing) {
			original.call(self, motion);
		}
	}

	@Inject(method = "onHitEntity", at = @At("RETURN"))
	private void bloomlevelup$passThrough(EntityHitResult hit, CallbackInfo ci) {
		if (!bloomlevelup$piercing) {
			return;
		}

		bloomlevelup$piercing = false;
		AbstractArrowAccessor arrow = (AbstractArrowAccessor) this;
		IntOpenHashSet pierced = arrow.bloomlevelup$piercedIds();

		if (pierced == null) {
			pierced = new IntOpenHashSet(5);
			arrow.bloomlevelup$setPiercedIds(pierced);
		}

		pierced.add(hit.getEntity().getId());
		this.dealtDamage = false; // still flying: it can hit the next one
	}
}
