package com.freshorangeslices.bloomlevelup.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
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
 * The Skill Hub: a window with a tab per category. Each tab is a canvas the player drags around (like the
 * advancements screen) over the tab's own background picture (any size, centred on its Majors):
 * <ul>
 *   <li>a category with weapon trees (Combat) is a tree: the waiver Major (Master of Arms) in the middle, a node
 *   per weapon around it with that weapon's Majors just outside it, the universal Majors that need a weapon
 *   (Heavy Hands, Catch!, ...) on the line between the middle and that weapon, and the rest of the universal
 *   Majors in a row along the bottom. Lines run from the middle to each weapon and from each weapon to its
 *   Majors, and turn red when a Major on them is active;</li>
 *   <li>other categories lay their Majors out as a grid.</li>
 * </ul>
 * A compact stats window sits in the top-left corner and folds away to give the Majors more room.
 *
 * <p>Everything shown is read from the last server snapshot; every click is a request the server validates,
 * and the screen redraws when the next snapshot arrives.
 */
public final class HubScreen extends Screen {
	// Palette (ARGB). Text colors need full alpha.
	private static final int PANEL = 0xF0141210;
	private static final int PANEL_EDGE = 0xFF5A4A33;
	private static final int TEXT = 0xFFEFE6D2;
	private static final int MUTED = 0xFFA99F8A;
	private static final int ACCENT = 0xFFE5933F;
	private static final int GOOD = 0xFF7FD27F;
	private static final int BAD = 0xFFE06C5A;
	private static final int XP_BACK = 0xFF2A241D;
	private static final int XP_FILL = 0xFF7CC242;
	private static final int LINE_ON = 0xFFE0453A;
	private static final int LINE_OFF = 0x90A99F8A;

	private static final int ICON_TINT_OWNED_OFF = 0xFF7A7A7A;
	private static final int ICON_TINT_UNOWNED = 0xFF262626;
	private static final int ICON_TINT_LOCKED = 0xFF161616;

	private static final int MAJOR_ICON = 22;
	private static final int CELL = 28;
	private static final int STATS_W = 128;
	private static final int STAT_ROW = 22;
	private static final long RESULT_SHOW_MS = 3500;

	/** Tree layout, in canvas coordinates with the middle (Master of Arms) at 0,0. */
	private static final double WEAPON_RX = 110.0;
	private static final double WEAPON_RY = 72.0;
	private static final double MAJOR_RX = 178.0;
	private static final double MAJOR_RY = 104.0;
	private static final int UNIVERSALS_GAP = 22;
	private static final int UNIVERSALS_PER_ROW = 13;
	/** How far out a weapon's universal Majors sit on the line from the middle (0) to that weapon (1). */
	private static final double SPOKE_FRACTION = 0.55;
	/** Room kept around a node (its glow) when working out the tab's extent. */
	private static final int NODE_MARGIN = 14;
	// docs/art/hub-templates/make_templates.py mirrors this layout: keep the two in step.

	/** Majors per row on tabs without weapon trees (fixed, so background art can be painted around them). */
	private static final int GRID_COLUMNS = 8;
	/** Shown until the art exists: the advancements screen's stone, darkened. */
	private static final Identifier FALLBACK_TILE = Identifier.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png");
	private static final String COPYCAT = BloomLevelUp.key("combat/copycat");

	/** Whether the stats window is folded away (kept while the game runs). */
	private static boolean statsCollapsed;

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int canvasX;
	private int canvasY;
	private int canvasW;
	private int canvasH;
	private int statsX;
	private int statsY;
	private int statsH;
	private int statsXpY;
	private int statsArmorY = -1;
	private int statsPointsY;
	private int statsRowsY;
	private boolean spendInvested;
	private int builtVersion = -1;
	private Backdrop backdrop;

	/** The current tab's nodes and lines, in canvas coordinates. */
	private final List<Node> nodes = new ArrayList<>();
	private final List<Link> links = new ArrayList<>();
	private int minX;
	private int minY;
	private int maxX;
	private int maxY;
	/** Where the canvas origin is on screen. */
	private double panX;
	private double panY;
	private String panFor = "";
	private boolean panning;

	/** What the last click on the canvas did (client game test diagnostics). */
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
		panelW = Math.min(this.width - 12, 600);
		panelH = Math.min(this.height - 12, 340);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		canvasX = panelX + 4;
		canvasY = panelY + 26;
		canvasW = panelW - 8;
		canvasH = panelH - 26 - 14;
		statsX = canvasX + 4;
		statsY = canvasY + 4;

		if (!ClientBloomState.ready()) {
			return;
		}

		addCategoryTabs();
		CategoryDefinition category = currentCategory();

		if (category == null) {
			return;
		}

		backdrop = findBackdrop(category);
		addStatsWindow(category);
		layout(category);
		String key = category.id() + "@" + this.width + "x" + this.height + (statsCollapsed ? "/folded" : "");

		if (!key.equals(panFor)) {
			panFor = key;
			centerContent();
		}

		clampPan();
	}

	private void addCategoryTabs() {
		List<CategoryDefinition> categories = new ArrayList<>(ClientBloomState.definitions().categories());

		if (categories.isEmpty()) {
			return;
		}

		int gap = 4;
		int tabW = (panelW - 8 - gap * (categories.size() - 1)) / categories.size();
		int x = panelX + 4;
		String selected = ClientBloomState.selectedCategory();

		for (CategoryDefinition category : categories) {
			Button tab = Button.builder(name(category.translationKey(), category.id()), b -> {
				ClientBloomState.selectCategory(category.id());
				spendInvested = false;
				this.rebuildWidgets();
			}).bounds(x, panelY + 4, tabW, 18).build();
			tab.active = !category.id().equals(selected);
			this.addRenderableWidget(tab);
			x += tabW + gap;
		}
	}

	/** The stats window's widgets; also works out where its text goes. */
	private void addStatsWindow(CategoryDefinition category) {
		ProgressionService service = ClientBloomState.service();
		BloomProfile profile = ClientBloomState.profile();
		CategoryProgress progress = profile.category(category.id());
		boolean investUnlocked = service.investmentUnlocked(profile, category.id());

		if (!investUnlocked) {
			spendInvested = false;
		}

		int x = statsX;
		int y = statsY + 4; // header line
		MiniButton fold = new MiniButton(x + STATS_W - 13, y - 1, statsCollapsed ? MiniButton.Glyph.UNFOLD : MiniButton.Glyph.FOLD,
				Component.translatable(statsCollapsed ? "bloom.hub.stats.expand" : "bloom.hub.stats.collapse"), shift -> {
					statsCollapsed = !statsCollapsed;
					this.rebuildWidgets();
				});
		this.addRenderableWidget(fold);

		y += 12;
		statsXpY = y; // bar, then the XP text under it
		y += 16;

		if (statsCollapsed) {
			statsH = y - statsY;
			statsArmorY = -1;
			return;
		}

		statsArmorY = -1;

		if (category.id().equals("bloomlevelup:armor")) {
			statsArmorY = y;
			y += 10;
		}

		statsPointsY = y;
		y += 11;
		statsRowsY = y;

		for (StatDefinition stat : category.stats()) {
			Action allocate = spendInvested ? Action.ALLOCATE_INVESTED : Action.ALLOCATE_NATURAL;
			int unspent = spendInvested ? progress.investedUnspent() : progress.naturalUnspent();
			int inPool = (spendInvested ? progress.invested() : progress.natural()).getOrDefault(stat.id(), 0);

			MiniButton plus = new MiniButton(x + 4, y + 1, MiniButton.Glyph.PLUS, Component.translatable("bloom.hub.stat.plus"),
					shift -> send(allocate, stat.id(), shift ? Math.min(10, unspent) : 1));
			plus.active = unspent > 0;
			this.addRenderableWidget(plus);

			MiniButton minus = new MiniButton(x + 4, y + 12, MiniButton.Glyph.MINUS, Component.translatable("bloom.hub.stat.minus"),
					shift -> send(allocate, stat.id(), shift ? -Math.min(10, inPool) : -1));
			minus.active = inPool > 0 && ClientBloomState.config().allowStatRefund;
			this.addRenderableWidget(minus);

			// The icon is the on/off switch: a corner dot, green when on and red when off (like the Majors).
			boolean naturalOff = progress.disabledStats().contains(stat.id());
			Identifier icon = IconTextures.resolve(stat.icon());

			if (spendInvested) {
				boolean off = progress.disabledInvested().contains(stat.id());
				this.addRenderableWidget(new StatIcon(x + 15, y + 2, icon, naturalOff, true, !off, inPool > 0 || off,
						() -> send(Action.TOGGLE_INVESTED, stat.id(), 0)));
			} else {
				this.addRenderableWidget(new StatIcon(x + 15, y + 2, icon, naturalOff, stat.toggleable(), !naturalOff, stat.toggleable(),
						() -> send(Action.TOGGLE_STAT, stat.id(), 0)));
			}

			y += STAT_ROW;
		}

		y += 2;
		int cap = ClientBloomState.config().levelCap;

		if (investUnlocked) {
			Button mode = Button.builder(Component.translatable(spendInvested ? "bloom.hub.pool.invested" : "bloom.hub.pool.natural")
					.withStyle(spendInvested ? ChatFormatting.GREEN : ChatFormatting.WHITE), b -> {
						spendInvested = !spendInvested;
						this.rebuildWidgets();
					}).bounds(x + 4, y, STATS_W - 8, 14).build();
			mode.setTooltip(Tooltip.create(Component.translatable("bloom.hub.pool.tooltip")));
			this.addRenderableWidget(mode);
			y += 16;

			// Shown as a vanilla level (the level whose total XP covers the cost); the real cost is in XP.
			long cost = service.investmentCost(progress, 1);
			int levels = PlayerXpWallet.levelForPoints(cost);
			Button invest = Button.builder(Component.translatable("bloom.hub.invest", levels), b -> send(Action.INVEST, category.id(), 1))
					.bounds(x + 4, y, STATS_W - 8, 14).build();
			invest.active = playerXpPoints() >= cost;
			invest.setTooltip(Tooltip.create(Component.translatable("bloom.hub.invest.tooltip", cost, levels)));
			this.addRenderableWidget(invest);
			y += 16;
		} else if (progress.level() < cap) {
			int needed = vanillaXpForNextLevel(service, progress);
			Button fund = Button.builder(Component.translatable("bloom.hub.fund.short", needed), b -> send(Action.FUND, category.id(), needed))
					.bounds(x + 4, y, STATS_W - 8, 14).build();
			fund.active = playerXpPoints() > 0 && ClientBloomState.config().vanillaXpToSkillXp > 0;
			fund.setTooltip(Tooltip.create(Component.translatable("bloom.hub.fund.tooltip")));
			this.addRenderableWidget(fund);
			y += 16;
		}

		statsH = y - statsY + 2;
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

	// ------------------------------------------------------------------ canvas layout

	/** A Major (or a weapon tree's node, {@code group} set) at a point on the canvas. */
	private record Node(MajorDefinition major, String group, int x, int y, boolean center) {
	}

	/** A line between two canvas points; red while any of {@code majors} is active. */
	private record Link(int x0, int y0, int x1, int y1, List<String> majors) {
	}

	private void layout(CategoryDefinition category) {
		nodes.clear();
		links.clear();
		List<MajorDefinition> majors = ClientBloomState.definitions().majorsIn(category.id());

		if (majors.stream().anyMatch(major -> major.group().isPresent())) {
			treeLayout(majors);
		} else {
			gridLayout(majors);
		}

		minX = minY = Integer.MAX_VALUE;
		maxX = maxY = Integer.MIN_VALUE;

		for (Node node : nodes) {
			minX = Math.min(minX, node.x() - NODE_MARGIN);
			maxX = Math.max(maxX, node.x() + NODE_MARGIN);
			minY = Math.min(minY, node.y() - NODE_MARGIN);
			maxY = Math.max(maxY, node.y() + NODE_MARGIN);
		}

		if (nodes.isEmpty()) {
			minX = minY = maxX = maxY = 0;
		}
	}

	private void treeLayout(List<MajorDefinition> majors) {
		Map<String, List<MajorDefinition>> trees = new LinkedHashMap<>();
		Map<String, List<MajorDefinition>> nearTree = new HashMap<>();
		List<MajorDefinition> middle = new ArrayList<>();
		List<MajorDefinition> universal = new ArrayList<>();

		for (MajorDefinition major : majors) {
			major.group().ifPresent(group -> trees.computeIfAbsent(group, k -> new ArrayList<>()).add(major));
		}

		for (MajorDefinition major : majors) {
			if (major.group().isPresent()) {
				continue;
			}

			String tree = weaponTreeNeeded(major);

			if (!major.waives().isEmpty()) {
				middle.add(major);
			} else if (tree != null && trees.containsKey(tree)) {
				nearTree.computeIfAbsent(tree, k -> new ArrayList<>()).add(major);
			} else {
				universal.add(major);
			}
		}

		// The middle: Master of Arms (any waiver Major), side by side if there were ever more than one.
		for (int i = 0; i < middle.size(); i++) {
			nodes.add(new Node(middle.get(i), null, (int) Math.round((i - (middle.size() - 1) / 2.0) * CELL), 0, true));
		}

		// The weapons on an inner ring, clockwise from the top; each weapon's Majors in a short row just outside it.
		int count = trees.size();
		int index = 0;

		for (Map.Entry<String, List<MajorDefinition>> tree : trees.entrySet()) {
			double angle = -Math.PI / 2.0 + 2.0 * Math.PI * index / count;
			int wx = (int) Math.round(Math.cos(angle) * WEAPON_RX);
			int wy = (int) Math.round(Math.sin(angle) * WEAPON_RY);
			List<MajorDefinition> near = nearTree.getOrDefault(tree.getKey(), List.of());
			List<String> ids = new ArrayList<>(tree.getValue().stream().map(MajorDefinition::id).toList());
			near.forEach(major -> ids.add(major.id()));
			nodes.add(new Node(null, tree.getKey(), wx, wy, false));
			links.add(new Link(0, 0, wx, wy, ids));

			// The universal Majors that need this weapon: on the line between the middle and the weapon, side by side.
			double sx = Math.cos(angle) * WEAPON_RX * SPOKE_FRACTION;
			double sy = Math.sin(angle) * WEAPON_RY * SPOKE_FRACTION;

			for (int j = 0; j < near.size(); j++) {
				double offset = (j - (near.size() - 1) / 2.0) * CELL;
				nodes.add(new Node(near.get(j), null, (int) Math.round(sx - Math.sin(angle) * offset), (int) Math.round(sy + Math.cos(angle) * offset),
						false));
			}

			double ax = Math.cos(angle) * MAJOR_RX;
			double ay = Math.sin(angle) * MAJOR_RY;
			double tx = -Math.sin(angle) * MAJOR_RX;
			double ty = Math.cos(angle) * MAJOR_RY;
			double length = Math.max(1.0e-6, Math.hypot(tx, ty));
			tx /= length;
			ty /= length;
			int size = tree.getValue().size();

			for (int j = 0; j < size; j++) {
				double offset = (j - (size - 1) / 2.0) * CELL;
				int mx = (int) Math.round(ax + tx * offset);
				int my = (int) Math.round(ay + ty * offset);
				MajorDefinition major = tree.getValue().get(j);
				nodes.add(new Node(major, null, mx, my, false));
				links.add(new Link(wx, wy, mx, my, List.of(major.id())));
			}

			index++;
		}

		// Universal Majors: rows along the bottom.
		int top = (int) MAJOR_RY + 14 + UNIVERSALS_GAP;

		for (int k = 0; k < universal.size(); k++) {
			int row = k / UNIVERSALS_PER_ROW;
			int inRow = Math.min(UNIVERSALS_PER_ROW, universal.size() - row * UNIVERSALS_PER_ROW);
			int col = k % UNIVERSALS_PER_ROW;
			nodes.add(new Node(universal.get(k), null, (int) Math.round((col - (inRow - 1) / 2.0) * CELL), top + row * CELL, false));
		}
	}

	/** The weapon tree a universal Major needs (its first "#tree" prerequisite), or null. */
	private static String weaponTreeNeeded(MajorDefinition major) {
		for (String prerequisite : major.prerequisites()) {
			if (MajorDefinition.isGroupRef(prerequisite)) {
				return MajorDefinition.groupOf(prerequisite);
			}
		}

		return null;
	}

	private void gridLayout(List<MajorDefinition> majors) {
		for (int k = 0; k < majors.size(); k++) {
			nodes.add(new Node(majors.get(k), null, (k % GRID_COLUMNS) * CELL, (k / GRID_COLUMNS) * CELL, false));
		}
	}

	/** The middle of the tab's Majors, where its background picture is centred. */
	private int anchorX() {
		return (minX + maxX) / 2;
	}

	private int anchorY() {
		return (minY + maxY) / 2;
	}

	/** Left edge of the canvas area the stats window doesn't cover. */
	private int freeLeft() {
		return statsX + STATS_W + 8;
	}

	private int freeRight() {
		return canvasX + canvasW - 6;
	}

	/** Puts the content in the middle of the free area (or its top when it's taller than that). */
	private void centerContent() {
		int left = statsCollapsed ? canvasX + 6 : freeLeft();
		int right = freeRight();
		int top = canvasY + 22; // under the Major Level counter
		int bottom = canvasY + canvasH - 4;
		panX = (left + right) / 2.0 - (minX + maxX) / 2.0;
		panY = maxY - minY <= bottom - top ? (top + bottom) / 2.0 - (minY + maxY) / 2.0 : top - minY;
	}

	/** Keeps at least a little of the content on screen. */
	private void clampPan() {
		int keep = 40;
		panX = Math.max(canvasX + keep - maxX, Math.min(canvasX + canvasW - keep - minX, panX));
		panY = Math.max(canvasY + keep - maxY, Math.min(canvasY + canvasH - keep - minY, panY));
	}

	private int screenX(int canvasPointX) {
		return (int) Math.round(panX) + canvasPointX;
	}

	private int screenY(int canvasPointY) {
		return (int) Math.round(panY) + canvasPointY;
	}

	private boolean inCanvas(double x, double y) {
		return x >= canvasX && x < canvasX + canvasW && y >= canvasY && y < canvasY + canvasH;
	}

	private boolean inStatsWindow(double x, double y) {
		return x >= statsX && x < statsX + STATS_W && y >= statsY && y < statsY + statsH;
	}

	/** Majors and weapon nodes can be pointed at only where the canvas shows and the stats window doesn't cover. */
	private boolean onOpenCanvas(double x, double y) {
		return inCanvas(x, y) && !inStatsWindow(x, y);
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
		List<FormattedCharSequence> tooltip = null;

		if (category != null) {
			drawBackground(g);
			g.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
			tooltip = drawCanvas(g, mouseX, mouseY);
			g.disableScissor();
			drawWallet(g);
			List<FormattedCharSequence> statTooltip = drawStatsWindow(g, category, mouseX, mouseY);

			if (statTooltip != null) {
				tooltip = statTooltip;
			}
		}

		drawStatusLine(g);

		// Which build this is, so an old jar is easy to spot.
		Component build = Component.literal("v" + BloomLevelUp.version());
		g.text(this.font, build, panelX + panelW - 6 - this.font.width(build), panelY + panelH - 11, 0xFF5E5648);
		super.extractRenderState(g, mouseX, mouseY, delta);

		if (tooltip != null) {
			g.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
		}
	}

	private void drawPanel(GuiGraphicsExtractor g) {
		g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
		frame(g, panelX, panelY, panelX + panelW, panelY + panelH, PANEL_EDGE);
	}

	/**
	 * The tab's picture, any size, drawn pixel for pixel (one picture pixel per GUI pixel, like vanilla's GUI art),
	 * centred on the tab's Majors and scrolling with them; plain dark around it. Darkened stone until it exists.
	 */
	private void drawBackground(GuiGraphicsExtractor g) {
		g.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);

		if (backdrop != null) {
			g.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF120F0C);
			int w = backdrop.width();
			int h = backdrop.height();
			g.blit(RenderPipelines.GUI_TEXTURED, backdrop.texture(), screenX(anchorX()) - w / 2, screenY(anchorY()) - h / 2, 0.0F, 0.0F,
					w, h, w, h, w, h, 0xFFFFFFFF);
			g.disableScissor();
			return;
		}

		int offsetX = Math.floorMod((int) Math.round(panX), 16) - 16;
		int offsetY = Math.floorMod((int) Math.round(panY), 16) - 16;

		for (int x = canvasX + offsetX; x < canvasX + canvasW; x += 16) {
			for (int y = canvasY + offsetY; y < canvasY + canvasH; y += 16) {
				g.blit(RenderPipelines.GUI_TEXTURED, FALLBACK_TILE, x, y, 0.0F, 0.0F, 16, 16, 16, 16, 16, 16, 0xFFFFFFFF);
			}
		}

		g.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xB80E0B09);
		g.disableScissor();
	}

	/** A tab's background picture and its size in pixels. */
	private record Backdrop(Identifier texture, int width, int height) {
	}

	/** {@code textures/gui/hub/background_<tab>.png}, else {@code background.png}, else none. */
	private Backdrop findBackdrop(CategoryDefinition category) {
		Identifier categoryId = Identifier.tryParse(category.id());
		String path = categoryId == null ? "" : categoryId.getPath();

		for (String name : List.of("background_" + path, "background")) {
			Identifier texture = BloomLevelUp.id("textures/gui/hub/" + name + ".png");
			Optional<net.minecraft.server.packs.resources.Resource> resource = this.minecraft.getResourceManager().getResource(texture);
			int[] size = resource.map(HubScreen::pngSize).orElse(null);

			if (size != null) {
				return new Backdrop(texture, size[0], size[1]);
			}
		}

		return null;
	}

	/** Width and height from a PNG's header, or null if it can't be read. */
	private static int[] pngSize(net.minecraft.server.packs.resources.Resource resource) {
		try (java.io.InputStream in = resource.open()) {
			byte[] head = in.readNBytes(24);

			if (head.length < 24 || head[1] != 'P' || head[2] != 'N' || head[3] != 'G') {
				return null;
			}

			java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(head);
			int width = header.getInt(16);
			int height = header.getInt(20);
			return width > 0 && height > 0 && width <= 4096 && height <= 4096 ? new int[] {width, height} : null;
		} catch (java.io.IOException e) {
			return null;
		}
	}

	/** Lines, weapon nodes and Majors. Returns the tooltip for whatever the mouse is on, if anything. */
	private List<FormattedCharSequence> drawCanvas(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		BloomProfile profile = ClientBloomState.profile();
		ProgressionService service = ClientBloomState.service();
		boolean pointing = onOpenCanvas(mouseX, mouseY) && !panning;
		long now = System.currentTimeMillis();
		List<FormattedCharSequence> tooltip = null;

		// Lines first, so the nodes sit on top of their ends. Inactive lines underneath the red ones.
		for (int pass = 0; pass < 2; pass++) {
			for (Link link : links) {
				boolean lit = link.majors().stream().anyMatch(id -> service.isMajorActive(profile, id));

				if (lit == (pass == 1)) {
					line(g, screenX(link.x0()), screenY(link.y0()), screenX(link.x1()), screenY(link.y1()), lit ? LINE_ON : LINE_OFF);
				}
			}
		}

		for (int i = 0; i < nodes.size(); i++) {
			Node node = nodes.get(i);
			int cx = screenX(node.x());
			int cy = screenY(node.y());

			if (cx < canvasX - 30 || cx > canvasX + canvasW + 30 || cy < canvasY - 30 || cy > canvasY + canvasH + 40) {
				continue;
			}

			if (node.group() != null) {
				if (drawWeaponNode(g, node, cx, cy, mouseX, mouseY, pointing)) {
					tooltip = weaponTooltip(node.group());
				}
			} else if (drawMajor(g, node, cx, cy, i, now, mouseX, mouseY, pointing)) {
				tooltip = majorTooltip(node.major());
			}
		}

		return tooltip;
	}

	/** A weapon tree's node: the tree's art (or the weapon itself) in an advancement-style frame, named underneath. */
	private boolean drawWeaponNode(GuiGraphicsExtractor g, Node node, int cx, int cy, int mouseX, int mouseY, boolean pointing) {
		BloomProfile profile = ClientBloomState.profile();
		boolean anyOwned = ClientBloomState.definitions().groupMembers(node.group()).stream().anyMatch(m -> profile.owns(m.id()));
		Identifier frame = Identifier.withDefaultNamespace(anyOwned ? "advancements/goal_frame_obtained" : "advancements/goal_frame_unobtained");
		g.blitSprite(RenderPipelines.GUI_TEXTURED, frame, cx - 13, cy - 13, 26, 26);
		Identifier art = treeArt(node.group());

		if (art != IconTextures.PLACEHOLDER) {
			g.blit(RenderPipelines.GUI_TEXTURED, art, cx - 9, cy - 9, 0.0F, 0.0F, 18, 18, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE,
					IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, 0xFFFFFFFF);
		} else {
			g.item(treeItem(node.group()), cx - 8, cy - 8);
		}

		// The tree's name, small, on a dark backing so it reads over lines and art; on the side toward the middle
		// (under the weapons in the top half, over those in the bottom half), clear of the weapon's own Majors.
		Component label = Component.literal(capitalize(treeName(node.group())));
		int half = (int) Math.ceil(this.font.width(label) * 0.75 / 2.0) + 1;
		int labelY = node.y() > 0 ? cy - 21 : cy + 14;
		g.fill(cx - half, labelY, cx + half, labelY + 7, 0xA0000000);
		g.pose().pushMatrix();
		g.pose().translate(cx, labelY + 1);
		g.pose().scale(0.75F);
		g.centeredText(this.font, label, 0, 0, anyOwned ? TEXT : MUTED);
		g.pose().popMatrix();

		return pointing && Math.abs(mouseX - cx) <= 13 && Math.abs(mouseY - cy) <= 13;
	}

	/** One Major's icon and state. Returns whether the mouse is on it. */
	private boolean drawMajor(GuiGraphicsExtractor g, Node node, int cx, int cy, int seed, long now, int mouseX, int mouseY, boolean pointing) {
		MajorDefinition major = node.major();
		BloomProfile profile = ClientBloomState.profile();
		ProgressionService service = ClientBloomState.service();
		int x = cx - MAJOR_ICON / 2;
		int y = cy - MAJOR_ICON / 2;
		OwnedMajor owned = profile.majors().get(major.id());
		boolean active = service.isMajorActive(profile, major.id());
		boolean locked = owned == null && !service.prerequisitesMet(profile, major);
		int tint;

		if (node.center()) {
			Identifier frame = Identifier.withDefaultNamespace(owned != null ? "advancements/challenge_frame_obtained"
					: "advancements/challenge_frame_unobtained");
			g.blitSprite(RenderPipelines.GUI_TEXTURED, frame, cx - 13, cy - 13, 26, 26);
		}

		if (owned != null && active) {
			drawGlow(g, x, y, now, seed);
			tint = 0xFFFFFFFF;
		} else if (owned != null) {
			frame(g, x - 1, y - 1, MAJOR_ICON + 2, 0xFF9A5A44);
			tint = ICON_TINT_OWNED_OFF;
		} else if (locked) {
			g.fill(x, y, x + MAJOR_ICON, y + MAJOR_ICON, 0xC0000000);
			tint = ICON_TINT_LOCKED;
		} else {
			boolean affordable = major.cost() <= profile.availableMajorLevels();
			g.fill(x, y, x + MAJOR_ICON, y + MAJOR_ICON, 0xA0000000);
			frame(g, x - 1, y - 1, MAJOR_ICON + 2, affordable ? 0x90EFE6D2 : 0x50EFE6D2);
			tint = ICON_TINT_UNOWNED;
		}

		g.blit(RenderPipelines.GUI_TEXTURED, IconTextures.resolve(major.icon()), x, y, 0.0F, 0.0F, MAJOR_ICON, MAJOR_ICON,
				IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, tint);

		// Owned: a small dot in the corner, green when on, red when switched off (so off never looks refunded).
		if (owned != null) {
			dot(g, x + MAJOR_ICON - 7, y + 1, owned.enabled() ? 0xFF4FD34F : 0xFFE0453A, owned.enabled() ? 0xFF1E6B1E : 0xFF6B1A14);
		} else {
			// Cost badge for anything not yet owned.
			String cost = Integer.toString(major.cost());
			g.text(this.font, Component.literal(cost), x + MAJOR_ICON - this.font.width(cost), y + MAJOR_ICON - 7, locked ? MUTED : ACCENT);
		}

		boolean inside = pointing && mouseX >= x && mouseX < x + MAJOR_ICON && mouseY >= y && mouseY < y + MAJOR_ICON;

		if (inside) {
			frame(g, x - 2, y - 2, MAJOR_ICON + 4, 0xFFFFFFFF);
		}

		return inside;
	}

	/** Owned + on: a soft pulsing halo with a small mote orbiting the icon. */
	private static void drawGlow(GuiGraphicsExtractor g, int x, int y, long now, int seed) {
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

	/** The Major Level counter, top right of the canvas. */
	private void drawWallet(GuiGraphicsExtractor g) {
		BloomProfile profile = ClientBloomState.profile();
		Component wallet = Component.translatable("bloom.hub.major_levels", profile.availableMajorLevels(), profile.spentMajorLevels());
		Component hint = Component.translatable("bloom.hub.major_hint");
		int width = Math.max(this.font.width(wallet), this.font.width(hint));
		int right = canvasX + canvasW - 4;
		g.fill(right - width - 6, canvasY + 2, right + 2, canvasY + 24, 0xA0000000);
		g.text(this.font, wallet, right - this.font.width(wallet), canvasY + 4, TEXT);
		g.text(this.font, hint, right - this.font.width(hint), canvasY + 14, MUTED);
	}

	/** The stats window's frame and text (its buttons draw themselves). Returns a tooltip for the row under the mouse. */
	private List<FormattedCharSequence> drawStatsWindow(GuiGraphicsExtractor g, CategoryDefinition category, int mouseX, int mouseY) {
		ProgressionService service = ClientBloomState.service();
		BloomProfile profile = ClientBloomState.profile();
		CategoryProgress progress = profile.category(category.id());
		int cap = ClientBloomState.config().levelCap;
		int x = statsX;
		int right = statsX + STATS_W;

		g.fill(x, statsY, right, statsY + statsH, 0xE8141210);
		frame(g, x, statsY, right, statsY + statsH, PANEL_EDGE);
		g.fill(x, statsY, x + 3, statsY + 1, ACCENT);
		g.fill(x, statsY, x + 1, statsY + 3, ACCENT);
		g.fill(right - 3, statsY + statsH - 1, right, statsY + statsH, ACCENT);
		g.fill(right - 1, statsY + statsH - 3, right, statsY + statsH, ACCENT);

		// Header: name, level, mastery star.
		int y = statsY + 4;
		MutableComponent title = name(category.translationKey(), category.id()).copy().withStyle(ChatFormatting.BOLD);
		g.text(this.font, title, x + 4, y, ACCENT);
		Component level = Component.translatable("bloom.hub.level", progress.level());
		g.text(this.font, level, x + 8 + this.font.width(title), y, TEXT);

		if (service.investmentUnlocked(profile, category.id())) {
			g.text(this.font, Component.literal("★"), x + 12 + this.font.width(title) + this.font.width(level), y, GOOD);
		}

		// XP bar and text.
		int barY = statsXpY;
		g.fill(x + 4, barY, right - 4, barY + 4, XP_BACK);
		double fraction = progress.level() >= cap ? 1.0 : Math.min(1.0, progress.xp() / Math.max(1.0, service.xpToNext(progress.level())));
		g.fill(x + 4, barY, x + 4 + (int) ((STATS_W - 8) * fraction), barY + 4, XP_FILL);
		Component xpText = progress.level() >= cap
				? Component.translatable("bloom.hub.xp.max")
				: Component.translatable("bloom.hub.xp", (int) progress.xp(), (int) service.xpToNext(progress.level()));
		g.text(this.font, xpText, x + 4, barY + 6, MUTED);

		if (statsCollapsed) {
			return null;
		}

		// Armor: the real total (the vanilla armor bar stops at 20) and what the overcap above 20 adds.
		if (statsArmorY >= 0 && this.minecraft.player != null) {
			double armor = this.minecraft.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
			double overcap = com.freshorangeslices.bloomlevelup.stats.StatFormulas.armorOvercapReduction(armor,
					ClientBloomState.config().armorOvercapReductionCap);
			Component armorText = overcap > 0.0
					? Component.translatable("bloom.hub.armor_total.overcap", fmt(armor), fmt(overcap * 100.0))
					: Component.translatable("bloom.hub.armor_total", fmt(armor));
			g.text(this.font, armorText, x + 4, statsArmorY, TEXT);
		}

		Component points = spendInvested
				? Component.translatable("bloom.hub.points.invested", progress.investedUnspent())
				: Component.translatable("bloom.hub.points", progress.naturalUnspent());
		g.text(this.font, points, x + 4, statsPointsY, spendInvested ? GOOD : progress.naturalUnspent() > 0 ? ACCENT : MUTED);

		// XP Investment mode is unmistakable: a green wash and frame around the stat rows.
		int rowsBottom = statsRowsY + category.stats().size() * STAT_ROW;

		if (spendInvested) {
			g.fill(x + 2, statsRowsY - 1, right - 2, rowsBottom, 0x2232A852);
			frame(g, x + 2, statsRowsY - 1, right - 2, rowsBottom, 0xFF3F8F4F);
		}

		List<FormattedCharSequence> tooltip = null;
		y = statsRowsY;

		for (StatDefinition stat : category.stats()) {
			boolean off = progress.disabledStats().contains(stat.id());
			int natural = progress.natural().getOrDefault(stat.id(), 0);
			int invested = progress.invested().getOrDefault(stat.id(), 0);
			boolean investedOff = progress.disabledInvested().contains(stat.id());
			int textX = x + 37;
			g.text(this.font, name(stat.translationKey(), stat.id()), textX, y + 2, off ? MUTED : TEXT);
			Component naturalText = Component.literal(Integer.toString(natural));
			g.text(this.font, naturalText, textX, y + 12, off ? MUTED : ACCENT);

			if (invested > 0) {
				Component investedText = Component.translatable(investedOff ? "bloom.hub.stat.invested_off" : "bloom.hub.stat.invested", invested);
				g.text(this.font, investedText, textX + 2 + this.font.width(naturalText), y + 12, off || investedOff ? MUTED : GOOD);
			}

			if (mouseX >= x + 15 && mouseX < right && mouseY >= y && mouseY < y + STAT_ROW) {
				tooltip = statTooltip(stat, off, natural, invested, investedOff);
			}

			y += STAT_ROW;
		}

		return tooltip;
	}

	private List<FormattedCharSequence> statTooltip(StatDefinition stat, boolean off, int natural, int invested, boolean investedOff) {
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

		if (spendInvested) {
			lines.add(Component.translatable("bloom.hub.invested_toggle").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
		} else if (stat.toggleable()) {
			lines.add(Component.translatable(off ? "bloom.hub.toggle.icon_off" : "bloom.hub.toggle.icon_on")
					.withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
		}

		return lines;
	}

	/** A 6x6 round-ish dot with a darker edge. */
	static void dot(GuiGraphicsExtractor g, int x, int y, int color, int edge) {
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

	private static void frame(GuiGraphicsExtractor g, int x, int y, int size, int color) {
		frame(g, x, y, x + size, y + size, color);
	}

	/** A 2-pixel line, drawn as runs of rectangles. */
	private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		int dx = x1 - x0;
		int dy = y1 - y0;

		if (Math.abs(dx) >= Math.abs(dy)) {
			if (dx < 0) {
				line(g, x1, y1, x0, y0, color);
				return;
			}

			int x = x0;

			while (x <= x1) {
				int y = dx == 0 ? y0 : y0 + (int) Math.round((double) (x - x0) * dy / dx);
				int run = 1;

				while (x + run <= x1 && (y0 + (int) Math.round((double) (x + run - x0) * dy / dx)) == y) {
					run++;
				}

				g.fill(x, y - 1, x + run, y + 1, color);
				x += run;
			}
		} else {
			if (dy < 0) {
				line(g, x1, y1, x0, y0, color);
				return;
			}

			int y = y0;

			while (y <= y1) {
				int x = x0 + (int) Math.round((double) (y - y0) * dx / dy);
				int run = 1;

				while (y + run <= y1 && (x0 + (int) Math.round((double) (y + run - y0) * dx / dy)) == x) {
					run++;
				}

				g.fill(x - 1, y, x + 1, y + run, color);
				y += run;
			}
		}
	}

	private static String fmt(double value) {
		return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	// ------------------------------------------------------------------ tooltips

	private List<FormattedCharSequence> weaponTooltip(String group) {
		BloomProfile profile = ClientBloomState.profile();
		List<MajorDefinition> members = ClientBloomState.definitions().groupMembers(group);
		long owned = members.stream().filter(m -> profile.owns(m.id())).count();
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.add(Component.translatable("bloom.hub.tree", capitalize(treeName(group))).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
				.getVisualOrderText());
		lines.add(Component.translatable("bloom.hub.tree.owned", owned, members.size()).withStyle(ChatFormatting.GRAY).getVisualOrderText());
		return lines;
	}

	private List<FormattedCharSequence> majorTooltip(MajorDefinition major) {
		BloomProfile profile = ClientBloomState.profile();
		ProgressionService service = ClientBloomState.service();
		OwnedMajor owned = profile.majors().get(major.id());
		List<FormattedCharSequence> lines = new ArrayList<>();

		lines.add(name(major.translationKey(), major.id()).copy().withStyle(owned != null ? ChatFormatting.GOLD : ChatFormatting.WHITE, ChatFormatting.BOLD)
				.getVisualOrderText());
		major.group().ifPresent(group -> lines.add(Component.translatable("bloom.hub.tree", capitalize(treeName(group)))
				.withStyle(ChatFormatting.DARK_AQUA).getVisualOrderText()));
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

		g.text(this.font, message, panelX + 6, panelY + panelH - 11, BAD);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		MajorDefinition major = onOpenCanvas(event.x(), event.y()) ? majorAt(event.x(), event.y()) : null;
		lastClickForTests = String.format(Locale.ROOT, "button=%d x=%.1f y=%.1f major=%s", event.button(), event.x(), event.y(),
				major == null ? "-" : major.id());

		if (super.mouseClicked(event, doubleClick)) {
			lastClickForTests += " (taken by a widget)";
			return true;
		}

		if (major == null) {
			// Empty canvas: drag to look around.
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && onOpenCanvas(event.x(), event.y())) {
				panning = true;
				return true;
			}

			return false;
		}

		boolean owned = ClientBloomState.profile().owns(major.id());

		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (owned) {
				click();
				List<String> dependents = ClientBloomState.service().refundCascade(ClientBloomState.profile(), Set.of(major.id()), null);

				if (dependents.isEmpty()) {
					send(Action.REFUND_MAJOR, major.id(), 0);
				} else {
					confirmRefund(major, dependents);
				}
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
					List<String> also = ClientBloomState.service().refundCascade(ClientBloomState.profile(), Set.copyOf(conflicts), major.id());
					confirmSwap(major, conflicts, also);
				}
			}

			return true;
		}

		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (panning) {
			panX += dragX;
			panY += dragY;
			clampPan();
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (panning && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			panning = false;
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (onOpenCanvas(mouseX, mouseY)) {
			panX += scrollX * 20.0;
			panY += scrollY * 20.0;
			clampPan();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Center of a Major's icon in GUI coordinates, or null if it isn't on this page or can't be clicked (client game test). */
	public int[] majorCenterForTests(String majorId) {
		for (Node node : nodes) {
			if (node.major() != null && node.major().id().equals(majorId)) {
				int x = screenX(node.x());
				int y = screenY(node.y());
				return onOpenCanvas(x, y) ? new int[] {x, y} : null;
			}
		}

		return null;
	}

	private MajorDefinition majorAt(double mouseX, double mouseY) {
		for (Node node : nodes) {
			if (node.major() == null) {
				continue;
			}

			int x = screenX(node.x()) - MAJOR_ICON / 2;
			int y = screenY(node.y()) - MAJOR_ICON / 2;

			if (mouseX >= x && mouseX < x + MAJOR_ICON && mouseY >= y && mouseY < y + MAJOR_ICON) {
				return node.major();
			}
		}

		return null;
	}

	private void confirmSwap(MajorDefinition major, List<String> conflicts, List<String> also) {
		Component title = Component.translatable("bloom.hub.swap.title");
		MutableComponent message = Component.translatable("bloom.hub.swap.message", name(major.translationKey(), major.id()), joinNames(conflicts));

		if (!also.isEmpty()) {
			message.append("\n\n").append(Component.translatable("bloom.hub.refund.also", joinNames(also)));
		}

		ClientScreens.open(new ConfirmScreen(yes -> {
			if (yes) {
				send(Action.BUY_MAJOR_SWAP, major.id(), 0);
			}

			ClientScreens.open(this);
		}, title, message));
	}

	/** Refunding this takes other Majors with it (they need it, or Master of Arms held them together): ask first. */
	private void confirmRefund(MajorDefinition major, List<String> dependents) {
		Component title = Component.translatable("bloom.hub.refund.title", name(major.translationKey(), major.id()));
		Component message = Component.translatable("bloom.hub.refund.also", joinNames(dependents));
		ClientScreens.open(new ConfirmScreen(yes -> {
			if (yes) {
				send(Action.REFUND_MAJOR_ALL, major.id(), 0);
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

	/** {@code bloomlevelup:combat/sword} → {@code sword}. */
	private static String treeName(String group) {
		return group.substring(group.lastIndexOf('/') + 1);
	}

	/** A tree's own art, {@code textures/icon/tree/<name>.png} (64x64), or the placeholder if it doesn't exist yet. */
	private static Identifier treeArt(String group) {
		Identifier id = Identifier.tryParse(group);
		String namespace = id == null ? BloomLevelUp.MOD_ID : id.getNamespace();
		return IconTextures.resolve(Optional.of(namespace + ":icon/tree/" + treeName(group)));
	}

	private static final Map<String, ItemStack> TREE_ITEMS = new HashMap<>();

	/** Until the tree art exists: the weapon itself. */
	private static ItemStack treeItem(String group) {
		return TREE_ITEMS.computeIfAbsent(treeName(group), name -> {
			Item item = switch (name) {
				case "sword" -> Items.IRON_SWORD;
				case "axe" -> Items.IRON_AXE;
				case "bow" -> Items.BOW;
				case "crossbow" -> Items.CROSSBOW;
				case "trident" -> Items.TRIDENT;
				case "mace" -> Items.MACE;
				case "spear" -> Items.IRON_SPEAR;
				case "unarmed" -> Items.PLAYER_HEAD;
				default -> Items.STICK;
			};
			return new ItemStack(item);
		});
	}
}
