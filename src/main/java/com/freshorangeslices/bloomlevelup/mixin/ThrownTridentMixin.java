package com.freshorangeslices.bloomlevelup.mixin;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * Piercing for tridents and Catch! throws. Vanilla tridents ignore their pierce level: every hit bounces the
 * trident back and ends its flight. With a pierce level (Piercing Shot spread by Copycat, a Charged Throw)
 * the hit doesn't bounce and the trident keeps flying through, like a piercing arrow: it passes through as
 * many mobs as its pierce level, and the next one stops it as usual (then it comes back with Loyalty).
 */
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

	@WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/arrow/ThrownTrident;deflect(Lnet/minecraft/world/entity/projectile/ProjectileDeflection;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EntityReference;ZLnet/minecraft/world/phys/Vec3;)Z"))
	private boolean bloomlevelup$keepFlying(ThrownTrident self, ProjectileDeflection deflection, Entity hit, EntityReference<Entity> owner,
			boolean byAttack, Vec3 scale, Operation<Boolean> original) {
		return bloomlevelup$piercing ? false : original.call(self, deflection, hit, owner, byAttack, scale);
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
