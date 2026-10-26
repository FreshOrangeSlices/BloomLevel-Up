package com.freshorangeslices.bloomlevelup.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.entity.ThrownWeapon;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;

/**
 * A Catch! throw in flight: the weapon's own 3D item model along its flight line. Axes and swords turn end over
 * end; anything thrown point first (a trident through Copycat) flies tip first. Stuck in a block, it stays put.
 * (Forge 1.20.1 build: drawn straight from the entity, as 1.20.1 renders them.)
 */
public final class ThrownWeaponRenderer extends EntityRenderer<ThrownWeapon> {
	/** Degrees per tick a spinning weapon turns (about two turns a second). */
	private static final float SPIN_PER_TICK = 36.0F;
	/** The item model's size: its item-frame look is half a block across, so this makes a sword about as long as held. */
	private static final float SCALE = 1.25F;
	/** Turns the item's handle-to-tip diagonal (bottom right to top left in a frame) onto local +X, tip first. */
	private static final float TIP_ALONG_X = -135.0F;

	private final ItemRenderer items;

	public ThrownWeaponRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.items = context.getItemRenderer();
	}

	@Override
	public void render(ThrownWeapon entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		ItemStack stack = entity.getItem();
		float yRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
		float xRot = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
		boolean pointFirst = Weapons.is(stack, Family.TRIDENT);
		float spin = pointFirst || entity.stuck() ? 0.0F : (entity.tickCount + partialTick) * SPIN_PER_TICK;

		poseStack.pushPose();
		// Vanilla's arrow and trident frame: after these two, local X runs along the flight line.
		poseStack.mulPose(Axis.YP.rotationDegrees(yRot - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(xRot));
		// End over end around the sideways axis, then the item's diagonal (handle to tip) laid along local X.
		poseStack.mulPose(Axis.ZP.rotationDegrees(-spin + TIP_ALONG_X));
		poseStack.scale(SCALE, SCALE, SCALE);
		// FIXED (the item-frame look): the item stands upright in its own plane, mirrored like in a frame (tip top left).
		items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack, buffers, entity.level(), entity.getId());
		poseStack.popPose();
		super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
	}

	@Override
	@SuppressWarnings("deprecation")
	public ResourceLocation getTextureLocation(ThrownWeapon entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
