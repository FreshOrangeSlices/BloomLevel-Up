package com.freshorangeslices.bloomlevelup.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.client.IconTextures;
import com.freshorangeslices.bloomlevelup.client.combat.CombatControls;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
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
 *   <li>the running skills as their Major icons in a row at the bottom right of the screen, each with its "×N"
 *   beside it: Predator, Combo, Bloodlust and Stored Impact darken from the top as they run out; Brace! is lit
 *   while braced; Brace!, Wind Impact and Lightning Summon cooling down are greyed with the seconds left.</li>
 * </ul>
 */
public final class BloomHud {
	private static final int CHARGE_BACK = 0xA0000000;
	private static final int CHARGE_FILL = 0xFFE8E8E8;
	private static final int CHARGE_READY = 0xFFFFB13B;
	private static final int DOT_DRAWING = 0xFFE04040;
	private static final int DOT_READY = 0xFF55E055;
	/** Frame colours: Bloodlust red, Stored Impact orange (gold once full), Predator purple, Combo blue. */
	private static final int RAGE = 0xFFC0202A;
	private static final int RAGE_TEXT = 0xFFE05555;
	private static final int IMPACT = 0xFFFF9A2E;
	private static final int IMPACT_FULL = 0xFFFFC94A;
	private static final int IMPACT_TEXT = 0xFFFFB04A;
	/** Stored Impact: every full 2 damage stored (200 hundredths) is one "×N". */
	private static final int IMPACT_PER_STACK = 200;
	private static final int PREDATOR = 0xFFB070FF;
	private static final int PREDATOR_TEXT = 0xFFC79BFF;
	private static final int COMBO = 0xFF4FC3F7;
	private static final int COMBO_TEXT = 0xFF8AD8FA;
	private static final int BRACED = 0xFFF2C443;
	private static final int COOLING = 0xFF3A3128;
	/** An icon's box: the 16-pixel icon, a dark inside and a 1-pixel frame (20 across), and the gaps around it. */
	private static final int BOX = 20;
	private static final int MARGIN = 4;
	private static final int GAP = 4;
	/** The row stays clear of the hotbar and what sits right of it (the attack indicator, Combat Roll's meter). */
	private static final int CLEAR_OF_HOTBAR = 91 + 40;

	/** Per meter kind (BloomPayloads.Meter): stacks, bonus, ticks left, total ticks. */
	private static final int[] STACKS = new int[BloomPayloads.Meter.KINDS];
	private static final float[] BONUS = new float[BloomPayloads.Meter.KINDS];
	private static final int[] TICKS_LEFT = new int[BloomPayloads.Meter.KINDS];
	private static final int[] TOTAL_TICKS = java.util.stream.IntStream.generate(() -> 1).limit(BloomPayloads.Meter.KINDS).toArray();

	private BloomHud() {
	}

	public static void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, BloomHud::crosshair);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, BloomLevelUp.id("charge_meter"), BloomHud::chargeMeter);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, BloomLevelUp.id("skill_icons"), BloomHud::skillIcons);

		ClientPlayNetworking.registerGlobalReceiver(BloomPayloads.Meter.TYPE, (payload, context) -> accept(payload));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
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

	/** A meter update from the server (or a client game test). */
	public static void accept(BloomPayloads.Meter payload) {
		int kind = payload.kind();

		if (kind >= 0 && kind < BloomPayloads.Meter.KINDS) {
			STACKS[kind] = payload.stacks();
			BONUS[kind] = payload.bonus();
			TICKS_LEFT[kind] = payload.ticksLeft();
			TOTAL_TICKS[kind] = Math.max(1, payload.totalTicks());
		}
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

	// ------------------------------------------------------------------ skill icons (bottom right)

	/**
	 * One icon in the row: the Major whose art it shows, its frame colour, the "×N" beside it (empty for none) and
	 * its colour, how much of it has run out (darkened from the top), and, while it cools down, the share of the
	 * cooldown left (greyed, a shade draining from the bottom) and the seconds on it.
	 */
	private record Icon(String majorId, int frame, String count, int countColor, float spent, float cooling, String seconds) {
		static Icon buff(String majorId, int frame, int stacks, int countColor, float spent) {
			return new Icon(majorId, frame, stacks > 0 ? "×" + stacks : "", countColor, spent, 0.0F, "");
		}

		static Icon cooldown(String majorId, long ticksLeft, long totalTicks) {
			return new Icon(majorId, COOLING, "", 0, 0.0F, Math.min(1.0F, ticksLeft / (float) Math.max(1L, totalTicks)),
					Long.toString((ticksLeft + 19) / 20));
		}
	}

	private static void skillIcons(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null || minecraft.level == null || !ClientBloomState.ready() || ClientBloomState.profile() == null) {
			return;
		}

		List<Icon> icons = icons(minecraft);

		if (icons.isEmpty()) {
			return;
		}

		// Laid out from the right edge leftwards, the last icon in the corner; a row that would reach the hotbar
		// carries on in a row above.
		int right = width() - MARGIN;
		int top = height() - MARGIN - BOX;
		int leftLimit = width() / 2 + CLEAR_OF_HOTBAR;

		for (int i = icons.size() - 1; i >= 0; i--) {
			Icon icon = icons.get(i);
			int wide = BOX + (icon.count().isEmpty() ? 0 : 2 + minecraft.font.width(icon.count()));
			int left = right - wide;

			if (left < leftLimit && right < width() - MARGIN) {
				right = width() - MARGIN;
				top -= BOX + GAP;
				left = right - wide;
			}

			draw(g, minecraft, icon, left, top);
			right = left - GAP;
		}
	}

	/** What's running: the buffs first (Predator, Combo, Bloodlust, Stored Impact), then Brace! and the cooldowns. */
	private static List<Icon> icons(Minecraft minecraft) {
		BloomProfile profile = ClientBloomState.profile();
		long now = minecraft.level.getGameTime();
		List<Icon> icons = new ArrayList<>();
		buff(icons, CombatIds.PREDATOR, BloomPayloads.Meter.PREDATOR, PREDATOR, PREDATOR_TEXT);
		buff(icons, CombatIds.COMBO, BloomPayloads.Meter.COMBO, COMBO, COMBO_TEXT);
		buff(icons, CombatIds.BLOODLUST, BloomPayloads.Meter.BLOODLUST, RAGE, RAGE_TEXT);

		long stored = profile.counter(CombatIds.STORED_IMPACT_HUNDREDTHS);
		long impactLeft = profile.counter(CombatIds.STORED_IMPACT_EXPIRES_AT) - now;

		if (stored > 0 && impactLeft > 0) {
			long cap = ClientBloomState.config() == null ? 2000 : Math.round(ClientBloomState.config().storedImpactCap * 100.0);
			int window = ClientBloomState.config() == null ? 6000 : ClientBloomState.config().storedImpactWindowTicks;
			icons.add(Icon.buff(CombatIds.STORED_IMPACT, stored >= cap ? IMPACT_FULL : IMPACT, (int) (stored / IMPACT_PER_STACK), IMPACT_TEXT,
					1.0F - Math.min(1.0F, impactLeft / (float) Math.max(1, window))));
		}

		if (ClientBloomState.service().isMajorActive(profile, ArmorIds.BRACE)) {
			long braceLeft = profile.counter(ArmorIds.BRACE_READY_AT) - now;

			if (profile.counter(ArmorIds.BRACED) != 0) {
				icons.add(new Icon(ArmorIds.BRACE, BRACED, "", 0, 0.0F, 0.0F, ""));
			} else if (braceLeft > 0) {
				icons.add(Icon.cooldown(ArmorIds.BRACE, braceLeft, ClientBloomState.config() == null ? 200 : ClientBloomState.config().braceCooldownTicks));
			}
		}

		cooldown(icons, CombatIds.WIND_IMPACT, BloomPayloads.Meter.WIND_IMPACT);
		cooldown(icons, CombatIds.LIGHTNING_SUMMON, BloomPayloads.Meter.LIGHTNING_SUMMON);
		return icons;
	}

	/** A stacking buff from the server's meter (Predator, Combo, Bloodlust): hidden with no stacks. */
	private static void buff(List<Icon> icons, String majorId, int kind, int frame, int countColor) {
		if (STACKS[kind] > 0 && TICKS_LEFT[kind] > 0) {
			icons.add(Icon.buff(majorId, frame, STACKS[kind], countColor, 1.0F - Math.min(1.0F, TICKS_LEFT[kind] / (float) TOTAL_TICKS[kind])));
		}
	}

	/** A skill's own cooldown from the server's meter (Wind Impact, Lightning Summon). */
	private static void cooldown(List<Icon> icons, String majorId, int kind) {
		if (STACKS[kind] > 0 && TICKS_LEFT[kind] > 0) {
			icons.add(Icon.cooldown(majorId, TICKS_LEFT[kind], TOTAL_TICKS[kind]));
		}
	}

	private static void draw(GuiGraphicsExtractor g, Minecraft minecraft, Icon icon, int left, int top) {
		int x = left + 2;
		int y = top + 2;
		g.fill(left, top, left + BOX, top + BOX, icon.frame());
		g.fill(left + 1, top + 1, left + BOX - 1, top + BOX - 1, 0xE0141210);
		Identifier texture = ClientBloomState.definitions().major(icon.majorId()).map(major -> IconTextures.resolve(major.icon()))
				.orElse(IconTextures.PLACEHOLDER);

		if (texture.equals(IconTextures.PLACEHOLDER) && icon.majorId().equals(ArmorIds.BRACE)) {
			if (braceFallback == null) {
				braceFallback = new ItemStack(net.minecraft.world.item.Items.SHIELD); // made on first use: items aren't ready at startup
			}

			g.item(braceFallback, x, y);
		} else {
			g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, 16, 16, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE,
					IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, 0xFFFFFFFF);
		}

		if (icon.spent() > 0.0F) {
			int shade = Math.round(16.0F * Math.min(1.0F, icon.spent()));
			g.fill(x, y, x + 16, y + shade, 0xA0000000);
		}

		if (!icon.seconds().isEmpty()) {
			int shade = (int) Math.ceil(16.0 * icon.cooling());
			g.fill(x, y, x + 16, y + 16, 0x80000000);
			g.fill(x, y + 16 - shade, x + 16, y + 16, 0x90000000);
			Component seconds = Component.literal(icon.seconds());
			g.text(minecraft.font, seconds, x + 17 - minecraft.font.width(seconds), y + 9, 0xFFFFFFFF);
		}

		if (!icon.count().isEmpty()) {
			g.text(minecraft.font, Component.literal(icon.count()), left + BOX + 2, top + 6, icon.countColor());
		}
	}

	private static ItemStack braceFallback;
}
