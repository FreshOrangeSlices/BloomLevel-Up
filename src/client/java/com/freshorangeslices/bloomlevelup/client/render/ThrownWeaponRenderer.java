package com.freshorangeslices.bloomlevelup.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.entity.ThrownWeapon;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;

/**
 * A Catch! throw in flight: the weapon's own 3D item model along its flight line. Axes, swords and maces turn end over
 * end; spears (and anything thrown point first) fly tip first like a trident. Stuck in a block, it stays put.
 */
public final class ThrownWeaponRenderer extends EntityRenderer<ThrownWeapon, ThrownWeaponRenderer.State> {
	/** Degrees per tick a spinning weapon turns (about two turns a second). */
	private static final float SPIN_PER_TICK = 36.0F;
	/** The item model's size: its item-frame look is half a block across, so this makes a sword about as long as held. */
	private static final float SCALE = 1.25F;

	public static final class State extends EntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		float yRot;
		float xRot;
		float spin;
	}

	private final ItemModelResolver items;

	public ThrownWeaponRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.items = context.getItemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ThrownWeapon entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		ItemStack stack = entity.getItem();
		// FIXED (the item-frame look): the item stands upright in its own plane, mirrored like in a frame (tip top left).
		items.updateForNonLiving(state.item, stack, ItemDisplayContext.FIXED, entity);
		state.yRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
		state.xRot = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
		boolean pointFirst = Weapons.is(stack, Family.SPEAR) || Weapons.is(stack, Family.TRIDENT);
		state.spin = pointFirst || entity.stuck() ? 0.0F : (entity.tickCount + partialTick) * SPIN_PER_TICK;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		// Vanilla's arrow and trident frame: after these two, local X runs along the flight line.
		poseStack.rotate(Axis.YP.rotationDegrees(state.yRot - 90.0F));
		poseStack.rotate(Axis.ZP.rotationDegrees(state.xRot));
		// End over end around the sideways axis, then the item's diagonal (handle to tip) laid along local X.
		poseStack.rotate(Axis.ZP.rotationDegrees(-state.spin + TIP_ALONG_X));
		poseStack.scale(SCALE, SCALE, SCALE);
		state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** Turns the item's handle-to-tip diagonal (bottom right to top left in a frame) onto local +X, tip first. */
	private static final float TIP_ALONG_X = -135.0F;
}
