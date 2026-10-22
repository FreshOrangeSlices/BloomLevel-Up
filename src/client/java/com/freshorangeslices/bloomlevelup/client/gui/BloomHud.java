package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.client.combat.CombatControls;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;

/**
 * Combat feedback on the HUD:
 * <ul>
 *   <li>a charge meter under the crosshair while the weapon-skill key is held (gold when ready to release);</li>
 *   <li>Straight Shot swaps the crosshair for a dot: red while drawing, green once the bow is fully drawn;</li>
 *   <li>a Bloodlust rage bar right of the hotbar: stacks, the damage bonus, and how long it lasts.</li>
 * </ul>
 */
public final class BloomHud {
	private static final int CHARGE_BACK = 0xA0000000;
	private static final int CHARGE_FILL = 0xFFE8E8E8;
	private static final int CHARGE_READY = 0xFFFFB13B;
	private static final int DOT_DRAWING = 0xFFE04040;
	private static final int DOT_READY = 0xFF55E055;
	private static final int RAGE_BACK = 0xA0200000;
	private static final int RAGE_FILL = 0xFFC0202A;
	private static final int RAGE_TEXT = 0xFFE05555;
	private static final int IMPACT_BACK = 0xA0201000;
	private static final int IMPACT_FILL = 0xFFFF9A2E;
	private static final int IMPACT_FULL = 0xFFFFC94A;
	private static final int IMPACT_TEXT = 0xFFFFB04A;
	/** Stored Impact bar: 4 segments of 0.5 damage; every full bar (2 damage) becomes one "×N". */
	private static final int IMPACT_SEGMENTS = 4;
	private static final int IMPACT_PER_BAR = 200;

	private static int bloodlustStacks;
	private static float bloodlustBonus;
	private static int bloodlustTicksLeft;
	private static int bloodlustTotalTicks = 1;

	private BloomHud() {
	}

	public static void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, BloomHud::crosshair);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, BloomLevelUp.id("charge_meter"), BloomHud::chargeMeter);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, BloomLevelUp.id("bloodlust"), BloomHud::bloodlust);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, BloomLevelUp.id("stored_impact"), BloomHud::storedImpact);

		ClientPlayNetworking.registerGlobalReceiver(BloomPayloads.Bloodlust.TYPE, (payload, context) -> {
			bloodlustStacks = payload.stacks();
			bloodlustBonus = payload.bonus();
			bloodlustTicksLeft = payload.ticksLeft();
			bloodlustTotalTicks = Math.max(1, payload.totalTicks());
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (bloodlustTicksLeft > 0 && --bloodlustTicksLeft == 0) {
				bloodlustStacks = 0;
			}

			if (client.player == null) {
				bloodlustStacks = 0;
				bloodlustTicksLeft = 0;
			}
		});
	}

	private static int width() {
		return Minecraft.getInstance().getWindow().getGuiScaledWidth();
	}

	private static int height() {
		return Minecraft.getInstance().getWindow().getGuiScaledHeight();
	}

	// ------------------------------------------------------------------ crosshair / Straight Shot

	private static HudElement crosshair(HudElement original) {
		return (graphics, delta) -> {
			if (!straightShotDot(graphics)) {
				original.extractRenderState(graphics, delta);
			}
		};
	}

	/** Draws Straight Shot's dot instead of the crosshair. Returns false to draw the normal crosshair. */
	private static boolean straightShotDot(GuiGraphicsExtractor g) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null || !ClientBloomState.ready() || !minecraft.options.getCameraType().isFirstPerson()) {
			return false;
		}

		ItemStack bow = player.isUsingItem() ? player.getUseItem() : player.getMainHandItem();

		if (!Weapons.is(bow, Family.BOW) || !CombatSkill.hasAnySide(player, CombatIds.STRAIGHT_SHOT, Family.BOW, bow)) {
			return false;
		}

		boolean drawn = player.isUsingItem() && BowItem.getPowerForTime(player.getTicksUsingItem()) >= 1.0F;
		int cx = width() / 2;
		int cy = height() / 2;
		g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xA0000000);
		g.fill(cx - 1, cy - 1, cx + 1, cy + 1, drawn ? DOT_READY : DOT_DRAWING);
		return true;
	}

	// ------------------------------------------------------------------ charge meter

	private static void chargeMeter(GuiGraphicsExtractor g, DeltaTracker delta) {
		float progress = CombatControls.chargeProgress();

		if (progress < 0.0F || Minecraft.getInstance().player == null) {
			return;
		}

		int w = 24;
		int x = width() / 2 - w / 2;
		int y = height() / 2 + 12;
		boolean ready = CombatControls.isCharged();
		g.fill(x - 1, y - 1, x + w + 1, y + 3, CHARGE_BACK);
		g.fill(x, y, x + Math.round(w * progress), y + 2, ready ? CHARGE_READY : CHARGE_FILL);

		if (ready && (System.currentTimeMillis() / 250L) % 2L == 0L) {
			g.fill(x - 1, y - 1, x + w + 1, y, CHARGE_READY);
			g.fill(x - 1, y + 2, x + w + 1, y + 3, CHARGE_READY);
		}
	}

	// ------------------------------------------------------------------ Stored Impact

	/** Orange, segmented, above the Bloodlust bar: what the open window holds and how long it has left. */
	private static void storedImpact(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		com.freshorangeslices.bloomlevelup.player.BloomProfile profile = ClientBloomState.profile();

		if (minecraft.player == null || minecraft.level == null || profile == null || !ClientBloomState.ready()) {
			return;
		}

		long stored = profile.counter(CombatIds.STORED_IMPACT_HUNDREDTHS);
		long ticksLeft = profile.counter(CombatIds.STORED_IMPACT_EXPIRES_AT) - minecraft.level.getGameTime();

		if (stored <= 0 || ticksLeft <= 0) {
			return;
		}

		long cap = ClientBloomState.config() == null ? 2000 : Math.round(ClientBloomState.config().storedImpactCap * 100.0);
		boolean full = stored >= cap;
		long bars = stored / IMPACT_PER_BAR;
		double partial = full ? 1.0 : (stored % IMPACT_PER_BAR) / (double) IMPACT_PER_BAR;
		long seconds = ticksLeft / 20;

		int x = width() / 2 + 91 + 34;
		int y = height() - 36;
		int segment = 14;
		String time = String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
		Component label = bars > 0 ? Component.translatable("bloom.hud.stored_impact.stacks", bars, time)
				: Component.translatable("bloom.hud.stored_impact", time);
		g.text(minecraft.font, label, x, y - 11, IMPACT_TEXT);

		for (int i = 0; i < IMPACT_SEGMENTS; i++) {
			int sx = x + i * (segment + 1);
			g.fill(sx - 1, y - 1, sx + segment + 1, y + 4, IMPACT_BACK);
			double fill = Math.max(0.0, Math.min(1.0, partial * IMPACT_SEGMENTS - i));

			if (fill > 0.0) {
				g.fill(sx, y, sx + (int) Math.round(segment * fill), y + 3, full ? IMPACT_FULL : IMPACT_FILL);
			}
		}
	}

	// ------------------------------------------------------------------ Bloodlust

	private static void bloodlust(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();

		if (bloodlustStacks <= 0 || bloodlustTicksLeft <= 0 || minecraft.player == null) {
			return;
		}

		int x = width() / 2 + 91 + 34;
		int y = height() - 12;
		int w = 60;
		Component label = Component.translatable("bloom.hud.bloodlust", bloodlustStacks, Math.round(bloodlustBonus * 100.0F));
		g.text(minecraft.font, label, x, y - 11, RAGE_TEXT);
		g.fill(x - 1, y - 1, x + w + 1, y + 4, RAGE_BACK);
		g.fill(x, y, x + Math.round(w * (bloodlustTicksLeft / (float) bloodlustTotalTicks)), y + 3, RAGE_FILL);
	}
}
