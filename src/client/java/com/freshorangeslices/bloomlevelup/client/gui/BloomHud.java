package com.freshorangeslices.bloomlevelup.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
 *   <li>a row of skill icons in the bottom-right corner: Brace!, Lightning Summon's cooldown, and Combo, Predator and
 *   Bloodlust with their stacks ({@link #skillIcons}).</li>
 * </ul>
 * (Forge 1.20.1 build: Forge GUI overlays; icons instead of main's bars; no Stored Impact or Wind Impact, the mace is gone.)
 */
public final class BloomHud {
	private static final int CHARGE_BACK = 0xA0000000;
	private static final int CHARGE_FILL = 0xFFE8E8E8;
	private static final int CHARGE_READY = 0xFFFFB13B;
	private static final int DOT_DRAWING = 0xFFE04040;
	private static final int DOT_READY = 0xFF55E055;
	private static final int RAGE_FILL = 0xFFC0202A;
	private static final int RAGE_TEXT = 0xFFE05555;
	private static final int PREDATOR_FILL = 0xFFB070FF;
	private static final int PREDATOR_TEXT = 0xFFC79BFF;
	private static final int COMBO_FILL = 0xFF4FC3F7;
	private static final int COMBO_TEXT = 0xFF8AD8FA;
	private static final int COOLDOWN_RIM = 0xFF9FB4C8;
	private static final int COOLDOWN_TEXT = 0xFFC9D6E3;
	/** An icon's box (16 + a 1-pixel rim), the room between entries, and the distance from the screen's edges. */
	private static final int ICON_BOX = 18;
	private static final int ICON_GAP = 4;
	private static final int EDGE = 4;

	/** Per meter kind (BloomPayloads.Meter): stacks, bonus, ticks left, total ticks. */
	private static final int[] STACKS = new int[BloomPayloads.Meter.KINDS];
	private static final float[] BONUS = new float[BloomPayloads.Meter.KINDS];
	private static final int[] TICKS_LEFT = new int[BloomPayloads.Meter.KINDS];
	private static final int[] TOTAL_TICKS = java.util.stream.IntStream.generate(() -> 1).limit(BloomPayloads.Meter.KINDS).toArray();

	private BloomHud() {
	}

	public static void init(IEventBus modBus) {
		modBus.addListener((RegisterGuiOverlaysEvent event) -> {
			event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "charge_meter", overlay(BloomHud::chargeMeter));
			event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "skill_icons", overlay(BloomHud::skillIcons));
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

	// ------------------------------------------------------------------ the skill icons (bottom right)

	/**
	 * One icon in the row: the Major's art (or an item until it exists), its rim colour, the text beside it ("×3",
	 * "10s" or nothing), how much of it is shaded over from the top (a buff running out) and whether it's greyed out
	 * with a shade draining from the bottom (cooling down; {@code cooling} is the share still to wait).
	 */
	private record Icon(String majorId, ItemStack fallback, int rim, Component text, int textColor, double used, double cooling) {
	}

	/**
	 * Bloom's running effects as a row of icons in the bottom-right corner (FreshOrangeSlices' call: icons instead of
	 * bars, all together): Brace! (lit while braced, greyed while it cools down), Lightning Summon's cooldown, then
	 * Combo, Predator and Bloodlust with their stacks ("×3"), each shading over as it runs out. Only what's going
	 * shows, packed against the corner.
	 */
	private static void skillIcons(BloomGraphics g) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null || minecraft.level == null || !ClientBloomState.ready()) {
			return;
		}

		List<Icon> icons = new ArrayList<>();
		brace(minecraft, icons);
		cooldown(icons, BloomPayloads.Meter.LIGHTNING_SUMMON, CombatIds.LIGHTNING_SUMMON, Items.TRIDENT);
		stacks(icons, BloomPayloads.Meter.COMBO, CombatIds.COMBO, Items.IRON_INGOT, COMBO_FILL, COMBO_TEXT);
		stacks(icons, BloomPayloads.Meter.PREDATOR, CombatIds.PREDATOR, Items.BONE, PREDATOR_FILL, PREDATOR_TEXT);
		stacks(icons, BloomPayloads.Meter.BLOODLUST, CombatIds.BLOODLUST, Items.REDSTONE, RAGE_FILL, RAGE_TEXT);

		if (icons.isEmpty()) {
			return;
		}

		int width = 0;

		for (Icon icon : icons) {
			width += ICON_BOX + textWidth(minecraft, icon) + ICON_GAP;
		}

		int x = width() - EDGE - (width - ICON_GAP);
		int y = height() - EDGE - ICON_BOX;

		for (Icon icon : icons) {
			drawIcon(g, minecraft, icon, x, y);
			x += ICON_BOX + textWidth(minecraft, icon) + ICON_GAP;
		}
	}

	private static int textWidth(Minecraft minecraft, Icon icon) {
		return icon.text().getString().isEmpty() ? 0 : 2 + minecraft.font.width(icon.text());
	}

	private static void drawIcon(BloomGraphics g, Minecraft minecraft, Icon icon, int x, int y) {
		g.fill(x, y, x + ICON_BOX, y + ICON_BOX, icon.rim());
		g.fill(x + 1, y + 1, x + ICON_BOX - 1, y + ICON_BOX - 1, 0xE0141210);
		ResourceLocation art = ClientBloomState.definitions().major(icon.majorId()).map(major -> IconTextures.resolve(major.icon()))
				.orElse(IconTextures.PLACEHOLDER);
		boolean greyed = icon.cooling() > 0.0;

		if (art.equals(IconTextures.PLACEHOLDER)) {
			g.item(icon.fallback(), x + 1, y + 1);
		} else {
			g.blit(BloomGraphics.GUI_TEXTURED, art, x + 1, y + 1, 0.0F, 0.0F, 16, 16, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE,
					IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, greyed ? 0xFF8A8A8A : 0xFFFFFFFF);
		}

		if (icon.used() > 0.0) {
			g.fill(x + 1, y + 1, x + ICON_BOX - 1, y + 1 + (int) Math.round(16 * Math.min(1.0, icon.used())), 0xA0000000);
		}

		if (greyed) {
			int shade = (int) Math.ceil(16.0 * Math.min(1.0, icon.cooling()));
			g.fill(x + 1, y + 1, x + ICON_BOX - 1, y + ICON_BOX - 1, 0x60000000);
			g.fill(x + 1, y + ICON_BOX - 1 - shade, x + ICON_BOX - 1, y + ICON_BOX - 1, 0x80000000);
		}

		if (!icon.text().getString().isEmpty()) {
			g.text(minecraft.font, icon.text(), x + ICON_BOX + 2, y + (ICON_BOX - 8) / 2, icon.textColor());
		}
	}

	/** Brace!: lit in gold while braced; greyed with the seconds left while it cools down. Nothing otherwise. */
	private static void brace(Minecraft minecraft, List<Icon> icons) {
		com.freshorangeslices.bloomlevelup.player.BloomProfile profile = ClientBloomState.profile();

		if (profile == null || !ClientBloomState.service().isMajorActive(profile, ArmorIds.BRACE)) {
			return;
		}

		boolean braced = profile.counter(ArmorIds.BRACED) != 0;
		long ticksLeft = profile.counter(ArmorIds.BRACE_READY_AT) - minecraft.level.getGameTime();

		if (braced) {
			icons.add(new Icon(ArmorIds.BRACE, new ItemStack(Items.SHIELD), 0xFFF2C443, Component.empty(), 0, 0.0, 0.0));
		} else if (ticksLeft > 0) {
			int total = Math.max(1, ClientBloomState.config() == null ? 200 : ClientBloomState.config().braceCooldownTicks);
			icons.add(new Icon(ArmorIds.BRACE, new ItemStack(Items.SHIELD), 0xFF3A3128, seconds(ticksLeft), COOLDOWN_TEXT, 0.0,
					Math.max(0.01, ticksLeft / (double) total)));
		}
	}

	/** One of Bloom's own skill cooldowns (Lightning Summon): greyed, with the seconds left. */
	private static void cooldown(List<Icon> icons, int kind, String majorId, net.minecraft.world.item.Item fallback) {
		if (STACKS[kind] > 0 && TICKS_LEFT[kind] > 0) {
			icons.add(new Icon(majorId, new ItemStack(fallback), COOLDOWN_RIM, seconds(TICKS_LEFT[kind]), COOLDOWN_TEXT, 0.0,
					Math.max(0.01, TICKS_LEFT[kind] / (double) TOTAL_TICKS[kind])));
		}
	}

	/** A stacking effect (Combo, Predator, Bloodlust): "×N" beside it, shaded over from the top as it runs out. */
	private static void stacks(List<Icon> icons, int kind, String majorId, net.minecraft.world.item.Item fallback, int rim, int textColor) {
		if (STACKS[kind] > 0 && TICKS_LEFT[kind] > 0) {
			icons.add(new Icon(majorId, new ItemStack(fallback), rim, Component.literal("×" + STACKS[kind]), textColor,
					1.0 - TICKS_LEFT[kind] / (double) TOTAL_TICKS[kind], 0.0));
		}
	}

	/** Whole seconds left, rounded up: "10s". */
	private static Component seconds(long ticks) {
		return Component.literal((ticks + 19) / 20 + "s");
	}

	/** The row as it is now, "brace bloodlust×3" (client smoke test). */
	public static String iconsForTests() {
		List<String> out = new ArrayList<>();
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level != null && ClientBloomState.ready()) {
			List<Icon> icons = new ArrayList<>();
			brace(minecraft, icons);
			cooldown(icons, BloomPayloads.Meter.LIGHTNING_SUMMON, CombatIds.LIGHTNING_SUMMON, Items.TRIDENT);
			stacks(icons, BloomPayloads.Meter.COMBO, CombatIds.COMBO, Items.IRON_INGOT, COMBO_FILL, COMBO_TEXT);
			stacks(icons, BloomPayloads.Meter.PREDATOR, CombatIds.PREDATOR, Items.BONE, PREDATOR_FILL, PREDATOR_TEXT);
			stacks(icons, BloomPayloads.Meter.BLOODLUST, CombatIds.BLOODLUST, Items.REDSTONE, RAGE_FILL, RAGE_TEXT);
			icons.forEach(icon -> out.add(icon.majorId().substring(icon.majorId().lastIndexOf('/') + 1) + icon.text().getString()));
		}

		return String.join(" ", out);
	}
}
