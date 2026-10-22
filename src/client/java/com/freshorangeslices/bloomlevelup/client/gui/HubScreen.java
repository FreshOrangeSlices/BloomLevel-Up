package com.freshorangeslices.bloomlevelup.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.client.IconTextures;
import com.freshorangeslices.bloomlevelup.definition.CategoryDefinition;
import com.freshorangeslices.bloomlevelup.definition.MajorDefinition;
import com.freshorangeslices.bloomlevelup.definition.StatDefinition;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads.Action;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.CategoryProgress;
import com.freshorangeslices.bloomlevelup.player.OwnedMajor;
import com.freshorangeslices.bloomlevelup.player.PlayerXpWallet;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;

/**
 * The Skill Hub. Four category tabs; each page has levelable stat rows on the left and the category's Majors
 * on the right. Everything shown is read from the last server snapshot; every click is a request the server
 * validates, and the screen redraws when the next snapshot arrives.
 */
public final class HubScreen extends Screen {
	// Palette (ARGB). Text colors need full alpha.
	private static final int PANEL = 0xF0141210;
	private static final int PANEL_EDGE = 0xFF5A4A33;
	private static final int DIVIDER = 0xFF3A3128;
	private static final int TEXT = 0xFFEFE6D2;
	private static final int MUTED = 0xFFA99F8A;
	private static final int ACCENT = 0xFFE5933F;
	private static final int GOOD = 0xFF7FD27F;
	private static final int BAD = 0xFFE06C5A;
	private static final int XP_BACK = 0xFF2A241D;
	private static final int XP_FILL = 0xFF7CC242;

	private static final int ICON_TINT_OWNED_OFF = 0xFF7A7A7A;
	private static final int ICON_TINT_UNOWNED = 0xFF262626;
	private static final int ICON_TINT_LOCKED = 0xFF161616;

	private static final int MAJOR_CELL = 26;
	private static final int MAJOR_ICON = 22;
	private static final int STAT_ROW = 22;
	private static final long RESULT_SHOW_MS = 3500;

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int statsX;
	private int statsW;
	private int majorsX;
	private int majorsY;
	private int majorsW;
	private int majorsH;
	private int majorScroll;
	private boolean spendInvested;
	private int builtVersion = -1;
	/** What the last click in the Majors grid did (client game test diagnostics). */
	public static String lastClickForTests = "";

	public HubScreen() {
		super(Component.translatable("bloom.hub.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ layout & widgets

	@Override
	protected void init() {
		builtVersion = ClientBloomState.version();
		panelW = Math.min(this.width - 16, 420);
		panelH = Math.min(this.height - 16, 240);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		statsX = panelX + 8;
		statsW = (int) (panelW * 0.47);
		majorsX = statsX + statsW + 10;
		majorsW = panelX + panelW - 8 - majorsX;
		majorsY = panelY + 60;
		majorsH = panelY + panelH - 22 - majorsY;

		if (!ClientBloomState.ready()) {
			return;
		}

		addCategoryTabs();
		CategoryDefinition category = currentCategory();

		if (category != null) {
			addStatRows(category);
		}
	}

	private void addCategoryTabs() {
		List<CategoryDefinition> categories = new ArrayList<>(ClientBloomState.definitions().categories());

		if (categories.isEmpty()) {
			return;
		}

		int gap = 4;
		int tabW = (panelW - 16 - gap * (categories.size() - 1)) / categories.size();
		int x = panelX + 8;
		String selected = ClientBloomState.selectedCategory();

		for (CategoryDefinition category : categories) {
			Button tab = Button.builder(name(category.translationKey(), category.id()), b -> {
				ClientBloomState.selectCategory(category.id());
				majorScroll = 0;
				spendInvested = false;
				this.rebuildWidgets();
			}).bounds(x, panelY + 6, tabW, 18).build();
			tab.active = !category.id().equals(selected);
			this.addRenderableWidget(tab);
			x += tabW + gap;
		}
	}

	private void addStatRows(CategoryDefinition category) {
		ProgressionService service = ClientBloomState.service();
		BloomProfile profile = ClientBloomState.profile();
		CategoryProgress progress = profile.category(category.id());
		boolean investUnlocked = service.investmentUnlocked(profile, category.id());

		if (!investUnlocked) {
			spendInvested = false;
		}

		int y = majorsY + 12;
		int right = statsX + statsW;

		for (StatDefinition stat : category.stats()) {
			Action allocate = spendInvested ? Action.ALLOCATE_INVESTED : Action.ALLOCATE_NATURAL;
			int unspent = spendInvested ? progress.investedUnspent() : progress.naturalUnspent();
			int inPool = (spendInvested ? progress.invested() : progress.natural()).getOrDefault(stat.id(), 0);
			boolean showToggle = spendInvested || stat.toggleable();
			int buttonsX = right - (showToggle ? 16 : 0) - 32;

			StepButton minus = new StepButton(buttonsX, y + 3, 14, 14, Component.literal("-"),
					shift -> send(allocate, stat.id(), shift ? -Math.min(10, inPool) : -1));
			minus.active = inPool > 0 && ClientBloomState.config().allowStatRefund;
			minus.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("bloom.hub.stat.minus")));
			this.addRenderableWidget(minus);

			StepButton plus = new StepButton(buttonsX + 16, y + 3, 14, 14, Component.literal("+"),
					shift -> send(allocate, stat.id(), shift ? Math.min(10, unspent) : 1));
			plus.active = unspent > 0;
			plus.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("bloom.hub.stat.plus")));
			this.addRenderableWidget(plus);

			// On/off: a green dot when on, empty when off.
			if (spendInvested) {
				// XP Investment mode: this switch only covers the stat's invested points.
				boolean off = progress.disabledInvested().contains(stat.id());
				Component tip = Component.translatable(off ? "bloom.hub.toggle.off" : "bloom.hub.toggle.on").append("\n")
						.append(Component.translatable("bloom.hub.invested_toggle"));
				DotToggle toggle = new DotToggle(right - 14, y + 3, 14, !off, tip, () -> send(Action.TOGGLE_INVESTED, stat.id(), 0));
				toggle.active = inPool > 0 || off;
				this.addRenderableWidget(toggle);
			} else if (stat.toggleable()) {
				boolean off = progress.disabledStats().contains(stat.id());
				DotToggle toggle = new DotToggle(right - 14, y + 3, 14, !off,
						Component.translatable(off ? "bloom.hub.toggle.off" : "bloom.hub.toggle.on"), () -> send(Action.TOGGLE_STAT, stat.id(), 0));
				this.addRenderableWidget(toggle);
			}

			y += STAT_ROW;
		}

		int buttonY = Math.max(y + 4, panelY + panelH - 44);
		int cap = ClientBloomState.config().levelCap;

		if (investUnlocked) {
			int modeW = Math.max(60, statsW * 2 / 5);
			Button mode = Button.builder(Component.translatable(spendInvested ? "bloom.hub.pool.invested" : "bloom.hub.pool.natural")
					.withStyle(spendInvested ? ChatFormatting.GREEN : ChatFormatting.WHITE), b -> {
						spendInvested = !spendInvested;
						this.rebuildWidgets();
					}).bounds(statsX, buttonY, modeW, 16).build();
			mode.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("bloom.hub.pool.tooltip")));
			this.addRenderableWidget(mode);

			// Shown as a vanilla level (the level whose total XP covers the cost); the real cost is in XP.
			long cost = service.investmentCost(progress, 1);
			int levels = PlayerXpWallet.levelForPoints(cost);
			Button invest = Button.builder(Component.translatable("bloom.hub.invest", levels), b -> send(Action.INVEST, category.id(), 1))
					.bounds(statsX + modeW + 4, buttonY, statsW - modeW - 4, 16).build();
			invest.active = playerXpPoints() >= cost;
			invest.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("bloom.hub.invest.tooltip", cost, levels)));
			this.addRenderableWidget(invest);
		} else if (progress.level() < cap) {
			int needed = vanillaXpForNextLevel(service, progress);
			Button fund = Button.builder(Component.translatable("bloom.hub.fund", needed), b -> send(Action.FUND, category.id(), needed))
					.bounds(statsX, buttonY, statsW, 16).build();
			fund.active = playerXpPoints() > 0 && ClientBloomState.config().vanillaXpToSkillXp > 0;
			fund.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("bloom.hub.fund.tooltip")));
			this.addRenderableWidget(fund);
		}
	}

	private static int vanillaXpForNextLevel(ProgressionService service, CategoryProgress progress) {
		double skillXp = Math.max(1.0, service.xpToNext(progress.level()) - progress.xp());
		double rate = Math.max(1e-6, ClientBloomState.config().vanillaXpToSkillXp);
		return (int) Math.max(1, Math.ceil(skillXp / rate));
	}

	private int playerXpPoints() {
		if (this.minecraft.player == null) {
			return 0;
		}

		return PlayerXpWallet.pointsForLevel(this.minecraft.player.experienceLevel)
				+ Math.round(this.minecraft.player.experienceProgress * this.minecraft.player.getXpNeededForNextLevel());
	}

	@Override
	public void tick() {
		super.tick();

		if (builtVersion != ClientBloomState.version()) {
			this.rebuildWidgets();
		}
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		drawPanel(g);

		if (!ClientBloomState.ready()) {
			g.centeredText(this.font, Component.translatable("bloom.hub.not_ready"), this.width / 2, this.height / 2 - 4, MUTED);
			super.extractRenderState(g, mouseX, mouseY, delta);
			return;
		}

		CategoryDefinition category = currentCategory();

		if (category != null) {
			drawHeader(g, category);
			drawStats(g, category, mouseX, mouseY);
			drawMajors(g, category, mouseX, mouseY);
		}

		drawStatusLine(g);

		// Which build this is, so an old jar is easy to spot.
		Component build = Component.literal("v" + com.freshorangeslices.bloomlevelup.BloomLevelUp.version());
		g.text(this.font, build, panelX + panelW - 6 - this.font.width(build), panelY + panelH - 11, 0xFF5E5648);
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	private void drawPanel(GuiGraphicsExtractor g) {
		g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
		g.fill(panelX, panelY, panelX + panelW, panelY + 1, PANEL_EDGE);
		g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, PANEL_EDGE);
		g.fill(panelX, panelY, panelX + 1, panelY + panelH, PANEL_EDGE);
		g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, PANEL_EDGE);
	}

	private void drawHeader(GuiGraphicsExtractor g, CategoryDefinition category) {
		ProgressionService service = ClientBloomState.service();
		BloomProfile profile = ClientBloomState.profile();
		CategoryProgress progress = profile.category(category.id());
		int cap = ClientBloomState.config().levelCap;
		int y = panelY + 30;

		MutableComponent title = name(category.translationKey(), category.id()).copy().withStyle(ChatFormatting.BOLD);
		g.text(this.font, title, statsX, y, ACCENT);
		Component level = Component.translatable("bloom.hub.level", progress.level());
		g.text(this.font, level, statsX + this.font.width(title) + 6, y, TEXT);

		if (service.investmentUnlocked(profile, category.id())) {
			g.text(this.font, Component.literal("★"), statsX + this.font.width(title) + this.font.width(level) + 10, y, GOOD);
		}

		// XP bar
		int barY = y + 12;
		int barW = statsW;
		g.fill(statsX, barY, statsX + barW, barY + 5, XP_BACK);

		if (progress.level() >= cap) {
			g.fill(statsX, barY, statsX + barW, barY + 5, XP_FILL);
		} else {
			double fraction = Math.min(1.0, progress.xp() / Math.max(1.0, service.xpToNext(progress.level())));
			g.fill(statsX, barY, statsX + (int) (barW * fraction), barY + 5, XP_FILL);
		}

		Component xpText = progress.level() >= cap
				? Component.translatable("bloom.hub.xp.max")
				: Component.translatable("bloom.hub.xp", (int) progress.xp(), (int) service.xpToNext(progress.level()));
		g.text(this.font, xpText, statsX, barY + 7, MUTED);

		// Armor: the real total (the vanilla armor bar stops at 20) and what the overcap above 20 adds.
		if (category.id().equals("bloomlevelup:armor") && this.minecraft.player != null) {
			double armor = this.minecraft.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
			double overcap = com.freshorangeslices.bloomlevelup.stats.StatFormulas.armorOvercapReduction(armor,
					ClientBloomState.config().armorOvercapReductionCap);
			Component armorText = overcap > 0.0
					? Component.translatable("bloom.hub.armor_total.overcap", fmt(armor), fmt(overcap * 100.0))
					: Component.translatable("bloom.hub.armor_total", fmt(armor));
			g.text(this.font, armorText, statsX + statsW - this.font.width(armorText), barY + 7, TEXT);
		}

		// Global Major Level wallet (right side)
		Component wallet = Component.translatable("bloom.hub.major_levels", profile.availableMajorLevels(), profile.spentMajorLevels());
		g.text(this.font, wallet, panelX + panelW - 8 - this.font.width(wallet), y, TEXT);
		Component hint = Component.translatable("bloom.hub.major_hint");
		g.text(this.font, hint, panelX + panelW - 8 - this.font.width(hint), y + 12, MUTED);

		g.fill(statsX + statsW + 4, majorsY - 2, statsX + statsW + 5, panelY + panelH - 20, DIVIDER);
	}

	private void drawStats(GuiGraphicsExtractor g, CategoryDefinition category, int mouseX, int mouseY) {
		CategoryProgress progress = ClientBloomState.profile().category(category.id());
		Component points = spendInvested
				? Component.translatable("bloom.hub.points.invested", progress.investedUnspent())
				: Component.translatable("bloom.hub.points", progress.naturalUnspent());
		g.text(this.font, points, statsX, majorsY, spendInvested ? GOOD : progress.naturalUnspent() > 0 ? ACCENT : MUTED);

		// XP Investment mode is unmistakable: a green wash and frame around the stat rows.
		if (spendInvested) {
			int top = majorsY + 10;
			int bottom = top + category.stats().size() * STAT_ROW + 2;
			g.fill(statsX - 3, top, statsX + statsW + 1, bottom, 0x2232A852);
			frame(g, statsX - 3, top, statsX + statsW + 1, bottom, 0xFF3F8F4F);
		}

		int y = majorsY + 12;

		for (StatDefinition stat : category.stats()) {
			boolean off = progress.disabledStats().contains(stat.id());
			Identifier icon = IconTextures.resolve(stat.icon());
			g.blit(RenderPipelines.GUI_TEXTURED, icon, statsX, y + 2, 0.0F, 0.0F, 16, 16,
					IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE,
					off ? ICON_TINT_OWNED_OFF : 0xFFFFFFFF);

			int natural = progress.natural().getOrDefault(stat.id(), 0);
			int invested = progress.invested().getOrDefault(stat.id(), 0);
			boolean investedOff = progress.disabledInvested().contains(stat.id());
			g.text(this.font, name(stat.translationKey(), stat.id()), statsX + 20, y + 2, off ? MUTED : TEXT);
			Component naturalText = Component.literal(Integer.toString(natural));
			g.text(this.font, naturalText, statsX + 20, y + 11, off ? MUTED : ACCENT);

			if (invested > 0) {
				Component investedText = Component.translatable(investedOff ? "bloom.hub.stat.invested_off" : "bloom.hub.stat.invested", invested);
				g.text(this.font, investedText, statsX + 22 + this.font.width(naturalText), y + 11, off || investedOff ? MUTED : GOOD);
			}

			if (mouseX >= statsX && mouseX < statsX + 16 + 60 && mouseY >= y && mouseY < y + STAT_ROW) {
				List<FormattedCharSequence> lines = new ArrayList<>();
				lines.add(name(stat.translationKey(), stat.id()).copy().withStyle(ChatFormatting.BOLD).getVisualOrderText());
				lines.addAll(this.font.split(Component.translatableWithFallback(stat.translationKey() + ".desc", "").withStyle(ChatFormatting.GRAY), 200));

				if (off) {
					lines.add(Component.translatable("bloom.hub.stat.off").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
				}

				if (invested > 0) {
					lines.add(Component.translatable("bloom.hub.stat.pools", natural, invested).withStyle(ChatFormatting.GRAY).getVisualOrderText());
				}

				if (investedOff) {
					lines.add(Component.translatable("bloom.hub.stat.invested_off.tooltip").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
				}

				g.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			}

			y += STAT_ROW;
		}
	}

	private List<MajorDefinition> majors(CategoryDefinition category) {
		return ClientBloomState.definitions().majorsIn(category.id());
	}

	private int columns() {
		return Math.max(1, majorsW / MAJOR_CELL);
	}

	private int maxScroll(CategoryDefinition category) {
		int rows = (majors(category).size() + columns() - 1) / columns();
		return Math.max(0, rows * MAJOR_CELL - (majorsH - 12));
	}

	private void drawMajors(GuiGraphicsExtractor g, CategoryDefinition category, int mouseX, int mouseY) {
		g.text(this.font, Component.translatable("bloom.hub.majors"), majorsX, majorsY, MUTED);
		List<MajorDefinition> majors = majors(category);
		BloomProfile profile = ClientBloomState.profile();
		ProgressionService service = ClientBloomState.service();
		int cols = columns();
		int gridTop = majorsY + 12;
		long now = System.currentTimeMillis();
		MajorDefinition hovered = null;
		majorScroll = Math.min(majorScroll, maxScroll(category));

		g.enableScissor(majorsX - 3, gridTop - 3, majorsX + majorsW + 3, majorsY + majorsH + 3);

		for (int i = 0; i < majors.size(); i++) {
			MajorDefinition major = majors.get(i);
			int x = majorsX + (i % cols) * MAJOR_CELL + (MAJOR_CELL - MAJOR_ICON) / 2;
			int y = gridTop + (i / cols) * MAJOR_CELL - majorScroll;

			if (y + MAJOR_ICON < gridTop - 3 || y > majorsY + majorsH) {
				continue;
			}

			OwnedMajor owned = profile.majors().get(major.id());
			boolean active = service.isMajorActive(profile, major.id());
			boolean locked = owned == null && !service.prerequisitesMet(profile, major);
			int tint;

			if (owned != null && active) {
				drawGlow(g, x, y, now, i);
				tint = 0xFFFFFFFF;
			} else if (owned != null) {
				frame(g, x - 1, y - 1, MAJOR_ICON + 2, 0xFF9A5A44);
				tint = ICON_TINT_OWNED_OFF;
			} else if (locked) {
				tint = ICON_TINT_LOCKED;
			} else {
				boolean affordable = major.cost() <= profile.availableMajorLevels();
				frame(g, x - 1, y - 1, MAJOR_ICON + 2, affordable ? 0x60EFE6D2 : 0x30EFE6D2);
				tint = ICON_TINT_UNOWNED;
			}

			g.blit(RenderPipelines.GUI_TEXTURED, IconTextures.resolve(major.icon()), x, y, 0.0F, 0.0F, MAJOR_ICON, MAJOR_ICON,
					IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, tint);

			// Owned: a small dot in the corner, green when on, red when switched off (so off never looks refunded).
			if (owned != null) {
				dot(g, x + MAJOR_ICON - 7, y + 1, owned.enabled() ? 0xFF4FD34F : 0xFFE0453A, owned.enabled() ? 0xFF1E6B1E : 0xFF6B1A14);
			}

			// Cost badge for anything not yet owned.
			if (owned == null) {
				String cost = Integer.toString(major.cost());
				g.text(this.font, Component.literal(cost), x + MAJOR_ICON - this.font.width(cost), y + MAJOR_ICON - 7, locked ? MUTED : ACCENT);
			}

			boolean inside = mouseX >= x && mouseX < x + MAJOR_ICON && mouseY >= y && mouseY < y + MAJOR_ICON
					&& mouseY >= gridTop - 3 && mouseY <= majorsY + majorsH;

			if (inside) {
				frame(g, x - 2, y - 2, MAJOR_ICON + 4, 0xFFFFFFFF);
				hovered = major;
			}
		}

		g.disableScissor();

		if (maxScroll(category) > 0) {
			g.text(this.font, Component.translatable("bloom.hub.scroll"), majorsX + majorsW - this.font.width(Component.translatable("bloom.hub.scroll")),
					majorsY, MUTED);
		}

		if (hovered != null) {
			g.setTooltipForNextFrame(this.font, majorTooltip(hovered), mouseX, mouseY);
		}
	}

	/** Owned + on: a soft pulsing halo with a small mote orbiting the icon. */
	private void drawGlow(GuiGraphicsExtractor g, int x, int y, long now, int seed) {
		double t = (now % 2400L) / 2400.0;
		int pulse = (int) (90 + 70 * Math.sin(t * Math.PI * 2));
		int halo = (pulse << 24) | 0xE5933F;
		g.fill(x - 3, y - 3, x + MAJOR_ICON + 3, y + MAJOR_ICON + 3, (pulse / 3 << 24) | 0xE5933F);
		frame(g, x - 2, y - 2, MAJOR_ICON + 4, halo);

		double angle = ((now + seed * 337L) % 3000L) / 3000.0 * Math.PI * 2;
		int cx = x + MAJOR_ICON / 2;
		int cy = y + MAJOR_ICON / 2;
		int r = MAJOR_ICON / 2 + 3;
		int mx = cx + (int) Math.round(Math.cos(angle) * r);
		int my = cy + (int) Math.round(Math.sin(angle) * r);
		g.fill(mx - 1, my - 1, mx + 1, my + 1, 0xFFFFF1C9);
	}

	/** A 6x6 round-ish dot with a darker edge. */
	private static void dot(GuiGraphicsExtractor g, int x, int y, int color, int edge) {
		g.fill(x + 1, y, x + 5, y + 6, edge);
		g.fill(x, y + 1, x + 6, y + 5, edge);
		g.fill(x + 1, y + 1, x + 5, y + 5, color);
	}

	private static void frame(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		g.fill(x0, y0, x1, y0 + 1, color);
		g.fill(x0, y1 - 1, x1, y1, color);
		g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
		g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
	}

	private static String fmt(double value) {
		return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	private static void frame(GuiGraphicsExtractor g, int x, int y, int size, int color) {
		g.fill(x, y, x + size, y + 1, color);
		g.fill(x, y + size - 1, x + size, y + size, color);
		g.fill(x, y + 1, x + 1, y + size - 1, color);
		g.fill(x + size - 1, y + 1, x + size, y + size - 1, color);
	}

	private List<FormattedCharSequence> majorTooltip(MajorDefinition major) {
		BloomProfile profile = ClientBloomState.profile();
		ProgressionService service = ClientBloomState.service();
		OwnedMajor owned = profile.majors().get(major.id());
		List<FormattedCharSequence> lines = new ArrayList<>();

		lines.add(name(major.translationKey(), major.id()).copy().withStyle(owned != null ? ChatFormatting.GOLD : ChatFormatting.WHITE, ChatFormatting.BOLD)
				.getVisualOrderText());
		major.group().ifPresent(group -> {
			String tree = group.substring(group.lastIndexOf('/') + 1);
			lines.add(Component.translatable("bloom.hub.tree", capitalize(tree)).withStyle(ChatFormatting.DARK_AQUA).getVisualOrderText());
		});
		lines.addAll(this.font.split(Component.translatableWithFallback(major.translationKey() + ".desc", "").withStyle(ChatFormatting.GRAY), 220));

		if (owned != null) {
			boolean active = service.isMajorActive(profile, major.id());
			lines.add(Component.translatable(active ? "bloom.hub.major.on" : "bloom.hub.major.off")
					.withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY).getVisualOrderText());

			if (major.toggleable()) {
				lines.add(Component.translatable("bloom.hub.major.click_toggle").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
			}

			lines.add(Component.translatable("bloom.hub.major.click_refund", owned.paid()).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
			copycatLines(profile, service, major, owned, lines);
		} else {
			boolean affordable = major.cost() <= profile.availableMajorLevels();
			lines.add(Component.translatable("bloom.hub.major.cost", major.cost())
					.withStyle(affordable ? ChatFormatting.YELLOW : ChatFormatting.RED).getVisualOrderText());

			if (!service.prerequisitesMet(profile, major)) {
				lines.add(Component.translatable("bloom.result.prerequisite_required").withStyle(ChatFormatting.RED).getVisualOrderText());
			} else if (affordable) {
				lines.add(Component.translatable("bloom.hub.major.click_buy").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
			}
		}

		return lines;
	}

	private static final String COPYCAT = com.freshorangeslices.bloomlevelup.BloomLevelUp.key("combat/copycat");

	private void copycatLines(BloomProfile profile, ProgressionService service, MajorDefinition major, OwnedMajor owned, List<FormattedCharSequence> lines) {
		OwnedMajor copycat = profile.majors().get(COPYCAT);

		if (copycat == null) {
			return;
		}

		if (major.id().equals(COPYCAT)) {
			if (owned.option().isEmpty()) {
				lines.add(Component.translatable("bloom.hub.copycat.none").withStyle(ChatFormatting.YELLOW).getVisualOrderText());
			} else {
				Component source = ClientBloomState.definitions().major(owned.option())
						.map(definition -> name(definition.translationKey(), owned.option()))
						.orElse(Component.literal(owned.option()));
				boolean working = service.activeCopySource(profile, COPYCAT).isPresent();
				lines.add(Component.translatable(working ? "bloom.hub.copycat.copying" : "bloom.hub.copycat.paused", source)
						.withStyle(working ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY).getVisualOrderText());
			}

			lines.add(Component.translatable("bloom.hub.copycat.clear").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
		} else if (service.isCopyableSource(profile, major.id())) {
			boolean chosen = major.id().equals(copycat.option());
			lines.add(Component.translatable(chosen ? "bloom.hub.copycat.is_source" : "bloom.hub.copycat.pick")
					.withStyle(chosen ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY).getVisualOrderText());
		}
	}

	private void drawStatusLine(GuiGraphicsExtractor g) {
		BloomPayloads.Result result = ClientBloomState.lastResult();

		if (result == null || System.currentTimeMillis() - ClientBloomState.lastResultAt() > RESULT_SHOW_MS
				|| "SUCCESS".equals(result.outcome())) {
			return;
		}

		MutableComponent message = Component.translatable("bloom.result." + result.outcome().toLowerCase(Locale.ROOT));

		if (!result.involved().isEmpty() && !"CONFLICT".equals(result.outcome())) {
			message.append(" ").append(joinNames(result.involved()));
		}

		g.centeredText(this.font, message, panelX + panelW / 2, panelY + panelH - 14, BAD);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		CategoryDefinition category = currentCategory();
		MajorDefinition major = category == null ? null : majorAt(category, event.x(), event.y());
		lastClickForTests = String.format(Locale.ROOT, "button=%d x=%.1f y=%.1f major=%s", event.button(), event.x(), event.y(),
				major == null ? "-" : major.id());

		if (super.mouseClicked(event, doubleClick)) {
			lastClickForTests += " (taken by a widget)";
			return true;
		}

		if (major == null) {
			return false;
		}

		boolean owned = ClientBloomState.profile().owns(major.id());

		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (owned) {
				click();
				send(Action.REFUND_MAJOR, major.id(), 0);
			}

			return true;
		}

		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			click();

			// Copycat: shift-click an owned weapon Major to choose it (shift-click Copycat itself to clear).
			if (owned && event.hasShiftDown() && ClientBloomState.profile().owns(COPYCAT)) {
				if (major.id().equals(COPYCAT)) {
					send(Action.SET_COPY_SOURCE, "", 0);
					return true;
				}

				if (ClientBloomState.service().isCopyableSource(ClientBloomState.profile(), major.id())) {
					send(Action.SET_COPY_SOURCE, major.id(), 0);
					return true;
				}
			}

			if (owned) {
				if (major.toggleable()) {
					send(Action.TOGGLE_MAJOR, major.id(), 0);
				}
			} else {
				List<String> conflicts = ClientBloomState.service().ownedConflicts(ClientBloomState.profile(), major);

				if (conflicts.isEmpty()) {
					send(Action.BUY_MAJOR, major.id(), 0);
				} else {
					confirmSwap(major, conflicts);
				}
			}

			return true;
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		CategoryDefinition category = currentCategory();

		if (category != null && mouseX >= majorsX && mouseX < majorsX + majorsW && mouseY >= majorsY && mouseY < majorsY + majorsH) {
			majorScroll = Math.max(0, Math.min(maxScroll(category), majorScroll - (int) Math.signum(scrollY) * MAJOR_CELL));
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Center of a Major's icon in GUI coordinates, or null if it isn't on this page (client game test). */
	public int[] majorCenterForTests(String majorId) {
		CategoryDefinition category = currentCategory();

		if (category == null) {
			return null;
		}

		List<MajorDefinition> majors = majors(category);
		int cols = columns();

		for (int i = 0; i < majors.size(); i++) {
			if (majors.get(i).id().equals(majorId)) {
				int x = majorsX + (i % cols) * MAJOR_CELL + (MAJOR_CELL - MAJOR_ICON) / 2;
				int y = majorsY + 12 + (i / cols) * MAJOR_CELL - majorScroll;
				return new int[] {x + MAJOR_ICON / 2, y + MAJOR_ICON / 2};
			}
		}

		return null;
	}

	private MajorDefinition majorAt(CategoryDefinition category, double mouseX, double mouseY) {
		int gridTop = majorsY + 12;

		if (mouseX < majorsX || mouseX >= majorsX + majorsW || mouseY < gridTop - 3 || mouseY > majorsY + majorsH) {
			return null;
		}

		List<MajorDefinition> majors = majors(category);
		int cols = columns();

		for (int i = 0; i < majors.size(); i++) {
			int x = majorsX + (i % cols) * MAJOR_CELL + (MAJOR_CELL - MAJOR_ICON) / 2;
			int y = gridTop + (i / cols) * MAJOR_CELL - majorScroll;

			if (mouseX >= x && mouseX < x + MAJOR_ICON && mouseY >= y && mouseY < y + MAJOR_ICON) {
				return majors.get(i);
			}
		}

		return null;
	}

	private void confirmSwap(MajorDefinition major, List<String> conflicts) {
		Component title = Component.translatable("bloom.hub.swap.title");
		Component message = Component.translatable("bloom.hub.swap.message", name(major.translationKey(), major.id()), joinNames(conflicts));
		ClientScreens.open(new ConfirmScreen(yes -> {
			if (yes) {
				send(Action.BUY_MAJOR_SWAP, major.id(), 0);
			}

			ClientScreens.open(this);
		}, title, message));
	}

	private void click() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private static void send(Action action, String target, int amount) {
		if (amount == 0 && (action == Action.ALLOCATE_NATURAL || action == Action.ALLOCATE_INVESTED)) {
			return;
		}

		ClientPlayNetworking.send(new BloomPayloads.Request(action, target, amount));
	}

	// ------------------------------------------------------------------ helpers

	private CategoryDefinition currentCategory() {
		String id = ClientBloomState.selectedCategory();
		return id == null ? null : ClientBloomState.definitions().category(id).orElse(null);
	}

	private static Component name(String translationKey, String fallback) {
		return Component.translatableWithFallback(translationKey + ".name", fallback);
	}

	private static Component joinNames(List<String> majorIds) {
		MutableComponent out = Component.empty();

		for (int i = 0; i < majorIds.size(); i++) {
			String id = majorIds.get(i);
			Optional<MajorDefinition> def = ClientBloomState.definitions().major(id);
			out.append(def.map(d -> name(d.translationKey(), id)).orElse(Component.literal(id)));

			if (i < majorIds.size() - 1) {
				out.append(", ");
			}
		}

		return out;
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** +/- button that knows whether shift was held (shift = 10 at a time). */
	private static final class StepButton extends Button.Plain {
		private final java.util.function.Consumer<Boolean> action;
		private boolean shift;

		StepButton(int x, int y, int width, int height, Component label, java.util.function.Consumer<Boolean> action) {
			super(x, y, width, height, label, b -> { }, DEFAULT_NARRATION);
			this.action = action;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			shift = input.hasShiftDown();
			action.accept(shift);
		}
	}
}
