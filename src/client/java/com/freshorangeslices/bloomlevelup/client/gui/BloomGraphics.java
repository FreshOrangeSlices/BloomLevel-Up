package com.freshorangeslices.bloomlevelup.client.gui;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * Forge 1.20.1 build: the drawing calls the Fabric 26.3 client is written with ({@code text}, {@code blit} with a
 * tint, {@code blitSprite}, layers), done with 1.20.1's {@link GuiGraphics}. It keeps the GUI code line for line the
 * same as main's, so changes port across by hand.
 *
 * <p>Differences it covers: 1.20.1 has no GUI sprite atlas (the two sprites Bloom uses are drawn from their
 * textures here, the gold frame as a nine-slice), blits take their size before their UV, a tint is a shader
 * colour, layers ({@link #nextStratum}) are a step up in depth, and tooltips are handed to the open screen to draw
 * last.
 */
public final class BloomGraphics {
	/** Stands in for 26.3's render pipeline argument (1.20.1 has one textured GUI shader). */
	public static final Object GUI_TEXTURED = new Object();
	/** How far up in depth each layer sits: above items (drawn 150 up, 16 deep) and below tooltips (400 up). */
	private static final float STRATUM = 200.0F;
	/** The gold frame: {@code textures/gui/sprites/hub/frame.png}, 28x28 with a 6-pixel nine-slice border. */
	private static final ResourceLocation FRAME_SPRITE = BloomLevelUp.id("hub/frame");
	private static final ResourceLocation FRAME_TEXTURE = BloomLevelUp.id("textures/gui/sprites/hub/frame.png");
	private static final int FRAME_SIZE = 28;
	private static final int FRAME_BORDER = 6;
	/** 1.20.1's advancement frames: one 256x256 sheet, 26x26 each (AdvancementWidget). */
	private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
	private static final ResourceLocation ADVANCEMENT_WIDGETS = new ResourceLocation("textures/gui/advancements/widgets.png");

	private static GuiGraphics lastGraphics;
	private static BloomGraphics last;

	private final GuiGraphics graphics;
	private final Pose pose;
	private int strata;

	private BloomGraphics(GuiGraphics graphics) {
		this.graphics = graphics;
		this.pose = new Pose(graphics);
	}

	/** The adapter for this frame's graphics (one per frame: 1.20.1 makes a new GuiGraphics every frame). */
	public static BloomGraphics of(GuiGraphics graphics) {
		if (graphics != lastGraphics) {
			lastGraphics = graphics;
			last = new BloomGraphics(graphics);
		}

		return last;
	}

	public GuiGraphics vanilla() {
		return graphics;
	}

	/** Drops the layers {@link #nextStratum} added; call once the screen has drawn everything. */
	public void finish() {
		while (strata > 0) {
			graphics.pose().popPose();
			strata--;
		}
	}

	// ------------------------------------------------------------------ shapes and text

	public void fill(int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0, y0, x1, y1, color);
	}

	public void text(Font font, Component text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color);
	}

	public void text(Font font, String text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color);
	}

	public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color);
	}

	public void centeredText(Font font, Component text, int x, int y, int color) {
		graphics.drawCenteredString(font, text, x, y, color);
	}

	/** A vanilla button's background (1.20.1's widgets sheet: disabled, normal, hovered). */
	public void buttonBackground(AbstractWidget widget) {
		int row = !widget.active ? 0 : widget.isHoveredOrFocused() ? 2 : 1;
		graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		RenderSystem.enableDepthTest();
		graphics.blitNineSliced(WIDGETS, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), 20, 4, 200, 20, 0, 46 + row * 20);
	}

	public void item(ItemStack stack, int x, int y) {
		graphics.renderItem(stack, x, y);
	}

	public void enableScissor(int x0, int y0, int x1, int y1) {
		graphics.enableScissor(x0, y0, x1, y1);
	}

	public void disableScissor() {
		graphics.disableScissor();
	}

	public Pose pose() {
		return pose;
	}

	/** Everything drawn after this sits above everything drawn before it (items included). */
	public void nextStratum() {
		graphics.pose().pushPose();
		graphics.pose().translate(0.0F, 0.0F, STRATUM);
		strata++;
	}

	/** A tooltip at the mouse, drawn after everything else: by the open screen if there is one, else right here. */
	public void setTooltipForNextFrame(Font font, List<FormattedCharSequence> lines, int x, int y) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.screen != null) {
			minecraft.screen.setTooltipForNextRenderPass(lines);
		} else {
			graphics.renderTooltip(font, lines, x, y);
		}
	}

	// ------------------------------------------------------------------ textures

	/** {@code width} x {@code height} of a {@code textureWidth} x {@code textureHeight} texture, from u, v, unscaled. */
	public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth,
			int textureHeight) {
		blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, 0xFFFFFFFF);
	}

	public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth,
			int textureHeight, int color) {
		blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color);
	}

	/** A {@code regionWidth} x {@code regionHeight} part of the texture from u, v, scaled to {@code width} x {@code height}. */
	public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int regionWidth,
			int regionHeight, int textureWidth, int textureHeight) {
		blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, 0xFFFFFFFF);
	}

	/** As above, tinted by an ARGB colour (white: as is). */
	public void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int regionWidth,
			int regionHeight, int textureWidth, int textureHeight, int color) {
		boolean tinted = color != 0xFFFFFFFF;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		if (tinted) {
			RenderSystem.setShaderColor((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F,
					(color >>> 24) / 255.0F);
		}

		graphics.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);

		if (tinted) {
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		}
	}

	/**
	 * The GUI sprites Bloom draws: its gold frame (nine-slice, any size) and vanilla's advancement frames (26x26).
	 * 1.20.1 keeps these as plain textures, not sprites.
	 */
	public void blitSprite(Object pipeline, ResourceLocation sprite, int x, int y, int width, int height) {
		if (sprite.equals(FRAME_SPRITE)) {
			nineSlice(FRAME_TEXTURE, x, y, width, height, FRAME_SIZE, FRAME_BORDER);
			return;
		}

		if (sprite.getNamespace().equals("minecraft") && sprite.getPath().startsWith("advancements/")) {
			String name = sprite.getPath().substring("advancements/".length());
			int u = name.startsWith("challenge") ? 26 : name.startsWith("goal") ? 52 : 0;
			int v = name.endsWith("_obtained") && !name.endsWith("unobtained") ? 128 : 154;
			RenderSystem.enableBlend();
			graphics.blit(ADVANCEMENT_WIDGETS, x, y, width, height, u, v, 26, 26, 256, 256);
			return;
		}

		BloomLevelUp.LOGGER.debug("Bloom: no 1.20.1 stand-in for sprite {}", sprite);
	}

	/** A square nine-slice texture stretched to {@code width} x {@code height}: corners as is, edges and middle stretched. */
	private void nineSlice(ResourceLocation texture, int x, int y, int width, int height, int size, int border) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		int inner = size - 2 * border;
		int midW = Math.max(0, width - 2 * border);
		int midH = Math.max(0, height - 2 * border);
		int right = x + width - border;
		int bottom = y + height - border;

		// Corners.
		graphics.blit(texture, x, y, border, border, 0, 0, border, border, size, size);
		graphics.blit(texture, right, y, border, border, size - border, 0, border, border, size, size);
		graphics.blit(texture, x, bottom, border, border, 0, size - border, border, border, size, size);
		graphics.blit(texture, right, bottom, border, border, size - border, size - border, border, border, size, size);
		// Edges.
		graphics.blit(texture, x + border, y, midW, border, border, 0, inner, border, size, size);
		graphics.blit(texture, x + border, bottom, midW, border, border, size - border, inner, border, size, size);
		graphics.blit(texture, x, y + border, border, midH, 0, border, border, inner, size, size);
		graphics.blit(texture, right, y + border, border, midH, size - border, border, border, inner, size, size);
		// Middle.
		graphics.blit(texture, x + border, y + border, midW, midH, border, border, inner, inner, size, size);
	}

	/** 26.3's 2D pose calls on 1.20.1's pose stack. */
	public static final class Pose {
		private final GuiGraphics graphics;

		private Pose(GuiGraphics graphics) {
			this.graphics = graphics;
		}

		public void pushMatrix() {
			graphics.pose().pushPose();
		}

		public void popMatrix() {
			graphics.pose().popPose();
		}

		public void translate(float x, float y) {
			graphics.pose().translate(x, y, 0.0F);
		}

		public void scale(float factor) {
			graphics.pose().scale(factor, factor, 1.0F);
		}

		public void scale(float x, float y) {
			graphics.pose().scale(x, y, 1.0F);
		}
	}
}
