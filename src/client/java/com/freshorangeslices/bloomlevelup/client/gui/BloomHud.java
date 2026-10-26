package com.freshorangeslices.bloomlevelup.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.client.ClientPackets;
import com.freshorangeslices.bloomlevelup.client.IconTextures;
import com.freshorangeslices.bloomlevelup.client.combat.CombatControls;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons;
import com.freshorangeslices.bloomlevelup.skill.combat.Weapons.Family;

/**
 * Combat feedback on the HUD:
 * <ul>
 *   <li>a charge meter under the crosshair while the weapon-skill key is held (gold when ready to release);</li>
 *   <li>Straight Shot swaps the crosshair for a dot: red while drawing, green once the bow is fully drawn;</li>
 *   <li>a Bloodlust rage bar right of the hotbar: stacks, the damage bonus, and how long it lasts;</li>
 *   <li>Predator and Combo bars left of the hotbar, the same way;</li>
 *   <li>Brace!'s icon right of those bars: lit while braced, dimmed with the seconds left while it cools down.</li>
 * </ul>
 * (Forge 1.20.1 build: Forge GUI overlays; no Stored Impact bar, the mace is gone.)
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

	private static final int PREDATOR_BACK = 0xA0180A24;
	private static final int PREDATOR_FILL = 0xFFB070FF;
	private static final int PREDATOR_TEXT = 0xFFC79BFF;
	private static final int COMBO_BACK = 0xA0081A24;
	private static final int COMBO_FILL = 0xFF4FC3F7;
	private static final int COMBO_TEXT = 0xFF8AD8FA;
	private static final int METER_WIDTH = 60;

	/** Per meter kind (BloomPayloads.Meter): stacks, bonus, ticks left, total ticks. */
	private static final int[] STACKS = new int[BloomPayloads.Meter.KINDS];
	private static final float[] BONUS = new float[BloomPayloads.Meter.KINDS];
	private static final int[] TICKS_LEFT = new int[BloomPayloads.Meter.KINDS];
	private static final int[] TOTAL_TICKS = {1, 1, 1};

	private BloomHud() {
	}

	public static void init(IEventBus modBus) {
		modBus.addListener((RegisterGuiOverlaysEvent event) -> {
			event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "charge_meter", overlay(BloomHud::chargeMeter));
			event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "bloodlust", overlay(BloomHud::bloodlust));
			event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "predator_combo", overlay(BloomHud::predatorAndCombo));
			event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "brace", overlay(BloomHud::brace));
		});
		// Straight Shot: its dot instead of the crosshair.
		MinecraftForge.EVENT_BUS.addListener((RenderGuiOverlayEvent.Pre event) -> {
			if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) && straightShotDot(BloomGraphics.of(event.getGuiGraphics()))) {
				event.setCanceled(true);
			}
		});

		ClientPackets.onMeter(payload -> {
			int kind = payload.kind();

			if (kind >= 0 && kind < BloomPayloads.Meter.KINDS) {
				STACKS[kind] = payload.stacks();
				BONUS[kind] = payload.bonus();
				TICKS_LEFT[kind] = payload.ticksLeft();
				TOTAL_TICKS[kind] = Math.max(1, payload.totalTicks());
			}
		});
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase != TickEvent.Phase.END) {
				return;
			}

			Minecraft client = Minecraft.getInstance();

			for (int kind = 0; kind < BloomPayloads.Meter.KINDS; kind++) {
				if (TICKS_LEFT[kind] > 0 && --TICKS_LEFT[kind] == 0) {
					STACKS[kind] = 0;
				}

				if (client.player == null) {
					STACKS[kind] = 0;
					TICKS_LEFT[kind] = 0;
				}
			}
		});
	}

	/** A HUD element as a Forge overlay; hidden with the rest of the HUD (F1). */
	private static IGuiOverlay overlay(java.util.function.Consumer<BloomGraphics> draw) {
		return (gui, graphics, partialTick, screenWidth, screenHeight) -> {
			if (!Minecraft.getInstance().options.hideGui) {
				draw.accept(BloomGraphics.of(graphics));
			}
		};
	}

	private static int width() {
		return Minecraft.getInstance().getWindow().getGuiScaledWidth();
	}

	private static int height() {
		return Minecraft.getInstance().getWindow().getGuiScaledHeight();
	}

	// ------------------------------------------------------------------ crosshair / Straight Shot

	/** Draws Straight Shot's dot instead of the crosshair. Returns false to draw the normal crosshair. */
	private static boolean straightShotDot(BloomGraphics g) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null || !ClientBloomState.ready() || !minecraft.options.getCameraType().isFirstPerson() || minecraft.options.hideGui) {
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

	private static void chargeMeter(BloomGraphics g) {
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

	// ------------------------------------------------------------------ Brace!

	/**
	 * Brace!'s icon (the vanilla shield until its art exists) right of the Bloodlust bar: lit in a gold
	 * box while braced; while it cools down, greyed with a shade that drains away and the seconds left. Hidden otherwise.
	 */
	private static void brace(BloomGraphics g) {
		Minecraft minecraft = Minecraft.getInstance();
		com.freshorangeslices.bloomlevelup.player.BloomProfile profile = ClientBloomState.profile();

		if (minecraft.player == null || minecraft.level == null || profile == null || !ClientBloomState.ready()
				|| !ClientBloomState.service().isMajorActive(profile, ArmorIds.BRACE)) {
			return;
		}

		boolean braced = profile.counter(ArmorIds.BRACED) != 0;
		long ticksLeft = profile.counter(ArmorIds.BRACE_READY_AT) - minecraft.level.getGameTime();

		if (!braced && ticksLeft <= 0) {
			return;
		}

		int x = width() / 2 + 91 + 34 + METER_WIDTH + 8;
		int y = height() - 24;
		g.fill(x - 2, y - 2, x + 18, y + 18, braced ? 0xFFF2C443 : 0xFF3A3128);
		g.fill(x - 1, y - 1, x + 17, y + 17, 0xE0141210);
		ResourceLocation icon = ClientBloomState.definitions().major(ArmorIds.BRACE).map(major -> IconTextures.resolve(major.icon()))
				.orElse(IconTextures.PLACEHOLDER);

		if (icon.equals(IconTextures.PLACEHOLDER)) {
			if (braceFallback == null) {
				braceFallback = new ItemStack(net.minecraft.world.item.Items.SHIELD); // made on first use: items aren't ready at startup
			}

			g.item(braceFallback, x, y);
		} else {
			g.blit(BloomGraphics.GUI_TEXTURED, icon, x, y, 0.0F, 0.0F, 16, 16, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE,
					IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, 0xFFFFFFFF);
		}

		if (!braced) {
			int total = Math.max(1, ClientBloomState.config() == null ? 200 : ClientBloomState.config().braceCooldownTicks);
			int shade = (int) Math.ceil(16.0 * Math.min(1.0, ticksLeft / (double) total));
			g.fill(x, y, x + 16, y + 16, 0x80000000);
			g.fill(x, y + 16 - shade, x + 16, y + 16, 0x90000000);
			Component seconds = Component.literal(Long.toString((ticksLeft + 19) / 20));
			g.text(minecraft.font, seconds, x + 17 - minecraft.font.width(seconds), y + 9, 0xFFFFFFFF);
		}
	}

	private static ItemStack braceFallback;

	// ------------------------------------------------------------------ Bloodlust

	private static void bloodlust(BloomGraphics g) {
		int kind = BloomPayloads.Meter.BLOODLUST;
		meter(g, kind, width() / 2 + 91 + 34, height() - 12,
				Component.translatable("bloom.hud.bloodlust", STACKS[kind], Math.round(BONUS[kind] * 100.0F)), RAGE_BACK, RAGE_FILL, RAGE_TEXT);
	}

	// ------------------------------------------------------------------ Predator / Combo (left of the hotbar)

	private static void predatorAndCombo(BloomGraphics g) {
		int x = width() / 2 - 91 - 34 - METER_WIDTH;
		int predator = BloomPayloads.Meter.PREDATOR;
		int combo = BloomPayloads.Meter.COMBO;
		meter(g, predator, x, height() - 36,
				Component.translatable("bloom.hud.predator", STACKS[predator], Math.round(BONUS[predator] * 100.0F)),
				PREDATOR_BACK, PREDATOR_FILL, PREDATOR_TEXT);
		meter(g, combo, x, height() - 12,
				Component.translatable("bloom.hud.combo", STACKS[combo], String.format(java.util.Locale.ROOT, "%.2f", BONUS[combo])),
				COMBO_BACK, COMBO_FILL, COMBO_TEXT);
	}

	/** A label over a bar that empties as the stacks run out. Hidden with no stacks. */
	private static void meter(BloomGraphics g, int kind, int x, int y, Component label, int back, int fill, int text) {
		Minecraft minecraft = Minecraft.getInstance();

		if (STACKS[kind] <= 0 || TICKS_LEFT[kind] <= 0 || minecraft.player == null) {
			return;
		}

		g.text(minecraft.font, label, x, y - 11, text);
		g.fill(x - 1, y - 1, x + METER_WIDTH + 1, y + 4, back);
		g.fill(x, y, x + Math.round(METER_WIDTH * (TICKS_LEFT[kind] / (float) TOTAL_TICKS[kind])), y + 3, fill);
	}
}
