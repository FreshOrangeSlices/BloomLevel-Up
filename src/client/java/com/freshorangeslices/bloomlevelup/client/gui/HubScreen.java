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
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.client.IconTextures;
import com.freshorangeslices.bloomlevelup.definition.CategoryDefinition;
import com.freshorangeslices.bloomlevelup.definition.MajorDefinition;
import com.freshorangeslices.bloomlevelup.definition.StatDefinition;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads.Action;
import com.freshorangeslices.bloomlevelup.network.SheetPayloads;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.CategoryProgress;
import com.freshorangeslices.bloomlevelup.player.OwnedMajor;
import com.freshorangeslices.bloomlevelup.player.PlayerXpWallet;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;

/**
 * The Skill Hub: a window with a tab per category. Each tab is a 416x320 page on its own background picture,
 * centred in the view; where the page is bigger than the view (small windows, big GUI scale) it can be
 * dragged, but only until its edges show:
 * <ul>
 *   <li>a category with weapon trees (Combat) is a tree: the waiver Major (Master of Arms) in the middle, a node
 *   per weapon around it with that weapon's Majors just outside it, the universal Majors that need a weapon
 *   (Heavy Hands, Catch!, ...) on the line between the middle and that weapon, and the rest of the universal
 *   Majors in a row along the bottom. Lines run from the middle to each weapon and from each weapon to its
 *   Majors, and turn red when a Major on them is active;</li>
 *   <li>other categories scatter bigger icons over the whole page.</li>
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
	/** Room kept around a node beyond its icon (its glow) when working out the tree's extent. */
	private static final int NODE_GLOW = 3;
	// docs/art/hub-templates/make_templates.py mirrors this layout: keep the two in step.

	/**
	 * Every tab's page, the size of its background picture (in GUI pixels), centred on the tab's Majors. The
	 * view moves only as far as the page's edges, so it doesn't move at all where the page fits.
	 */
	public static final int BOARD_W = 416;
	public static final int BOARD_H = 320;
	/** Major icons on tabs without weapon trees: bigger (half the 64-pixel art, so they stay sharp), scattered over the page. */
	private static final int BIG_ICON = 32;
	/** Space kept clear along the page's edges by the scattered icons. */
	private static final int SCATTER_MARGIN = 36;
	/** Shown until the art exists: the advancements screen's stone, darkened. */
	private static final Identifier FALLBACK_TILE = Identifier.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png");
	private static final String COPYCAT = BloomLevelUp.key("combat/copycat");
	/** The gold frame around the window: a nine-slice GUI sprite ({@code textures/gui/sprites/hub/frame.png}), this wide. */
	private static final Identifier FRAME_SPRITE = BloomLevelUp.id("hub/frame");
	private static final int FRAME = 6;
	/** The stats window's column left of the page while it's open (window, 4-pixel gaps either side). */
	private static final int STATS_COLUMN = STATS_W + 8;
	private static final int TABS_H = 26;
	/** The category tabs: small item plates, centred; the reset buttons sit in the corners either side. */
	private static final int TAB_W = 26;
	private static final int TAB_H = 20;
	private static final Identifier RESET_LEVELS_ICON = BloomLevelUp.id("textures/gui/hub/reset_levels.png");
	private static final Identifier RESET_MAJORS_ICON = BloomLevelUp.id("textures/gui/hub/reset_majors.png");
	private static final Identifier STAT_SHEET_ICON = BloomLevelUp.id("textures/gui/hub/stat_sheet.png");
	private static final int STRIP_H = 20;
	/** Where the strip's status text starts: right of the two build helmets in its left corner. */
	private static final int STRIP_TEXT_X = 4 + 2 * 20 + 4 + 6;

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
	private Button investButton;
	private long investCost;
	private Backdrop backdrop;
	/** The tab's "reset Majors" button and the minutes its tooltip shows (rebuilt when they change). */
	private HubTab resetMajorsButton;
	private long resetMajorsReadyAt;
	private long resetMinutesShown = -1;
	/** The build switch's wait, shown in its tooltip as seconds (rebuilt each second while it counts down). */
	private long buildReadyAt;
	private long buildSecondsShown;
	/** Whether the click being handled had shift held (Buy point: shift buys up to 10). */
	private boolean shiftClick;
	/** The details window (right-click an owned Major): which Major, its sections, how far it's scrolled. */
	private MajorDefinition detailMajor;
	/** The stat sheet window, while it's open (the Stats button at the top right). */
	private StatSheetWindow sheetWindow;
	private List<MajorDetails.Section> detailSections = List.of();
	private double detailScroll;
	private int detailContentH;
	private static final int DETAIL_HEADER = 46;

	/** The current tab's nodes and lines, in canvas coordinates. */
	private final List<Node> nodes = new ArrayList<>();
	private final List<Link> links = new ArrayList<>();
	/** Where the canvas origin (the middle of the page) is on screen. */
	private double panX;
	private double panY;
	private String panFor = "";
	private boolean panning;

	/** What the last click on the canvas did (client game test diagnostics). */
	public static String lastClickForTests = "";

	/** Opened by the stat sheet's keybind: the sheet shows at once, and closing it closes the hub too. */
	private boolean sheetOnly;
	/** The stat whose "one more point" preview was asked for last, and ticks since. */
	private String previewAsked;
	private int previewAge;
	/** This frame's + buttons by stat (client game test). */
	private final Map<String, MiniButton> plusButtons = new HashMap<>();

	public HubScreen() {
		super(Component.translatable("bloom.hub.title"));
	}

	/** The hub with the stat sheet already open for the tab last open (the "Open Stat Sheet" key). */
	public static HubScreen onStatSheet() {
		HubScreen hub = new HubScreen();
		hub.sheetOnly = true;
		return hub;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ layout & widgets

	@Override
	protected void init() {
		builtVersion = ClientBloomState.version();
		// The window hugs its page (and the stats window's column while it's open), framed in gold, with the
		// game showing around it; on a screen too small for that it fills the screen and the page drags.
		int column = statsCollapsed ? 0 : STATS_COLUMN;
		panelW = Math.min(this.width - 2 * (FRAME + 2), 8 + column + BOARD_W);
		panelH = Math.min(this.height - 2 * (FRAME + 2), TABS_H + BOARD_H + STRIP_H);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		canvasX = panelX + 4;
		canvasY = panelY + TABS_H;
		canvasW = panelW - 8;
		canvasH = panelH - TABS_H - STRIP_H;
		statsX = canvasX + 4;
		statsY = canvasY + 4;

		investButton = null;

		if (!ClientBloomState.ready()) {
			return;
		}

		addCategoryTabs();
		resetMajorsButton = null;
		CategoryDefinition category = currentCategory();

		if (category == null) {
			return;
		}

		addResetButtons(category);
		addBuildButtons();

		backdrop = findBackdrop(category);
		addStatsWindow(category);
		layout(category);
		String key = category.id() + "@" + this.width + "x" + this.height + (statsCollapsed ? "/folded" : "");

		if (!key.equals(panFor)) {
			panFor = key;
			panX = (freeLeftNow() + freeRight()) / 2.0;
			panY = canvasY + canvasH / 2.0;
		}

		clampPan();

		// The details window sits over everything: its Major's text is rebuilt with the profile, and the widgets under
		// it are hidden (they'd show through, tooltips and all).
		if (detailMajor != null && !ClientBloomState.profile().owns(detailMajor.id())) {
			detailMajor = null;
		}

		if (detailMajor != null) {
			detailSections = MajorDetails.sections(detailMajor);
		}

		// The stat sheet belongs to one tab; switching tabs closes it.
		if (sheetWindow != null && !sheetWindow.category().equals(category.id())) {
			sheetWindow = null;
		}

		if (sheetOnly && sheetWindow == null) {
			sheetWindow = newSheet(category);
		}

		if (overlayOpen()) {
			for (var child : this.children()) {
				if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
					widget.visible = false;
				}
			}
		}
	}

	private void addCategoryTabs() {
		List<CategoryDefinition> categories = new ArrayList<>(ClientBloomState.definitions().categories());

		if (categories.isEmpty()) {
			return;
		}

		int gap = 4;
		int total = categories.size() * TAB_W + (categories.size() - 1) * gap;
		int x = panelX + (panelW - total) / 2;
		String selected = ClientBloomState.selectedCategory();

		for (CategoryDefinition category : categories) {
			this.addRenderableWidget(HubTab.tab(x, panelY + 4, TAB_W, TAB_H, name(category.translationKey(), category.id()), tabItem(category),
					category.id().equals(selected), () -> {
						ClientBloomState.selectCategory(category.id());
						spendInvested = false;
						this.rebuildWidgets();
					}));
			x += TAB_W + gap;
		}
	}

	/**
	 * The two reset buttons in the corners of the tab row: Bloom+ on the left (every tab back to level 0 for bonus
	 * Major Levels), and this tab's Majors on the right (all refunded, then a 20-minute wait). Both ask first.
	 */
	private void addResetButtons(CategoryDefinition category) {
		ProgressionService service = ClientBloomState.service();
		BloomProfile profile = ClientBloomState.profile();
		var config = ClientBloomState.config();
		int y = panelY + 4;

		Component tabName = name(category.translationKey(), category.id());
		int gain = service.previewPrestige(profile, category.id());
		MutableComponent bloomTip = Component.translatable("bloom.hub.bloom.tooltip", tabName).withStyle(ChatFormatting.LIGHT_PURPLE);
		bloomTip.append("\n").append(Component.translatable("bloom.hub.bloom.tooltip.desc", config.prestigeEvery, config.prestigePerCategoryCap)
				.withStyle(ChatFormatting.GRAY));
		bloomTip.append("\n").append(bloomGainLine(gain));
		this.addRenderableWidget(HubTab.button(panelX + 4, y, TAB_H, bloomTip, RESET_LEVELS_ICON, true, () -> confirmBloom(category, gain)));

		long now = this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime();
		resetMajorsReadyAt = profile.counter(ProgressionService.majorResetKey(category.id()));
		resetMinutesShown = minutesUntil(resetMajorsReadyAt, now);
		List<String> owned = service.ownedMajorsIn(profile, category.id());
		MutableComponent resetTip = Component.translatable("bloom.hub.reset_majors.tooltip", name(category.translationKey(), category.id()))
				.withStyle(ChatFormatting.GOLD);
		resetTip.append("\n").append(Component.translatable("bloom.hub.reset_majors.tooltip.desc", cooldownMinutes()).withStyle(ChatFormatting.GRAY));

		if (resetMinutesShown > 0) {
			resetTip.append("\n").append(Component.translatable("bloom.hub.reset_majors.ready_in", resetMinutesShown).withStyle(ChatFormatting.RED));
		} else if (owned.isEmpty()) {
			resetTip.append("\n").append(Component.translatable("bloom.hub.reset_majors.none").withStyle(ChatFormatting.DARK_GRAY));
		}

		resetMajorsButton = HubTab.button(panelX + panelW - 4 - TAB_H, y, TAB_H, resetTip, RESET_MAJORS_ICON,
				resetMinutesShown <= 0 && !owned.isEmpty(), () -> confirmMajorReset(category, owned));
		this.addRenderableWidget(resetMajorsButton);

		// The stat sheet, just left of the reset button.
		MutableComponent sheetTip = Component.translatable("bloom.sheet.ui.button", tabName).withStyle(ChatFormatting.GOLD);
		sheetTip.append("\n").append(Component.translatable("bloom.sheet.ui.button.desc." + categoryKey(category.id())).withStyle(ChatFormatting.GRAY));
		this.addRenderableWidget(HubTab.button(panelX + panelW - 4 - 2 * TAB_H - 3, y, TAB_H, sheetTip, STAT_SHEET_ICON, true,
				() -> openSheet(category)));
	}

	/** Whole minutes (rounded up) from {@code now} until {@code readyAt}, in game ticks; 0 when it's ready. */
	private static long minutesUntil(long readyAt, long now) {
		return readyAt <= now ? 0L : (readyAt - now + 1199) / 1200;
	}

	private static long cooldownMinutes() {
		return Math.max(1, ClientBloomState.config().majorResetCooldownTicks / 1200);
	}

	/** What a Bloom+ reset of this tab would pay right now, as one line. */
	private static Component bloomGainLine(int gain) {
		return gain <= 0 ? Component.translatable("bloom.hub.bloom.no_gain").withStyle(ChatFormatting.DARK_GRAY)
				: Component.translatable("bloom.hub.bloom.gain", gain).withStyle(ChatFormatting.GREEN);
	}

	/** Bloom+ asks twice: what it does and pays, then "are you really sure?". */
	private void confirmBloom(CategoryDefinition category, int gain) {
		Component tab = name(category.translationKey(), category.id());
		MutableComponent message = Component.translatable("bloom.hub.bloom.message", tab);
		message.append("\n\n").append(bloomGainLine(gain));
		ClientScreens.open(new ConfirmScreen(yes -> {
			if (!yes) {
				ClientScreens.open(this);
				return;
			}

			ClientScreens.open(new ConfirmScreen(sure -> {
				if (sure) {
					send(Action.BLOOM_RESET, category.id(), 0);
				}

				ClientScreens.open(this);
			}, Component.translatable("bloom.hub.bloom.sure.title").withStyle(ChatFormatting.RED),
					Component.translatable("bloom.hub.bloom.sure.message", tab)));
		}, Component.translatable("bloom.hub.bloom.title", tab), message));
	}

	/**
	 * The two builds, in the left corner of the strip under the page: Build 1 (an iron helmet) and Build 2 (a golden
	 * helmet). The one in use is lit; clicking the other switches (once a tab has reached 100, then a 2-minute wait).
	 */
	private void addBuildButtons() {
		BloomProfile profile = ClientBloomState.profile();
		ProgressionService service = ClientBloomState.service();
		boolean unlocked = service.buildsUnlocked(profile);
		long now = this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime();
		buildReadyAt = ClientBloomState.config().waitsEnabled ? profile.counter(ProgressionService.BUILD_SWITCH_KEY) : 0L;
		buildSecondsShown = buildReadyAt > now ? (buildReadyAt - now + 19) / 20 : 0L;
		int gap = 4;
		int x = panelX + 4;
		int y = panelY + panelH - STRIP_H;

		for (int build = 0; build < 2; build++) {
			boolean inUse = profile.activeBuild() == build;
			MutableComponent tip = Component.translatable("bloom.hub.build", build + 1).withStyle(ChatFormatting.GOLD);

			if (inUse) {
				tip.append("\n").append(Component.translatable("bloom.hub.build.active").withStyle(ChatFormatting.GREEN));
			} else if (!unlocked) {
				tip.append("\n").append(Component.translatable("bloom.hub.build.locked").withStyle(ChatFormatting.DARK_GRAY));
			} else if (buildSecondsShown > 0) {
				tip.append("\n").append(Component.translatable("bloom.hub.build.wait", buildSecondsShown).withStyle(ChatFormatting.RED));
			} else {
				tip.append("\n").append(Component.translatable("bloom.hub.build.switch",
						Math.max(1, ClientBloomState.config().buildSwitchCooldownTicks / 1200)).withStyle(ChatFormatting.GRAY));
			}

			HubTab button = HubTab.tab(x + build * (TAB_H + gap), y, TAB_H, TAB_H, tip, buildItem(build), inUse, () -> {
				click();
				send(Action.SWITCH_BUILD, "", 0);
			});

			if (!inUse) {
				button.active = unlocked && buildSecondsShown == 0;
			}

			this.addRenderableWidget(button);
		}
	}

	private static final ItemStack[] BUILD_ITEMS = new ItemStack[2];

	/** Build 1 is an iron helmet, build 2 a golden one (made on first use, once items exist). */
	private static ItemStack buildItem(int build) {
		if (BUILD_ITEMS[build] == null) {
			BUILD_ITEMS[build] = new ItemStack(build == 0 ? Items.IRON_HELMET : Items.GOLDEN_HELMET);
		}

		return BUILD_ITEMS[build];
	}

	private void confirmMajorReset(CategoryDefinition category, List<String> owned) {
		BloomProfile profile = ClientBloomState.profile();
		List<String> also = ClientBloomState.service().refundCascade(profile, Set.copyOf(owned), null);
		int back = 0;

		for (String id : owned) {
			back += profile.majors().get(id).paid();
		}

		for (String id : also) {
			back += profile.majors().get(id).paid();
		}

		Component tab = name(category.translationKey(), category.id());
		MutableComponent message = Component.translatable("bloom.hub.reset_majors.message", joinNames(owned), back, cooldownMinutes());

		if (!also.isEmpty()) {
			message.append("\n\n").append(Component.translatable("bloom.hub.refund.also", joinNames(also)));
		}

		ClientScreens.open(new ConfirmScreen(yes -> {
			if (yes) {
				send(Action.RESET_MAJORS, category.id(), 0);
			}

			ClientScreens.open(this);
		}, Component.translatable("bloom.hub.reset_majors.title", tab), message));
	}

	private static final Map<String, ItemStack> TAB_ITEMS = new HashMap<>();

	/** A category tab's item: a pickaxe, a sword, a chestplate, a potion (made on first use, once items exist). */
	private static ItemStack tabItem(CategoryDefinition category) {
		Identifier id = Identifier.tryParse(category.id());
		String path = id == null ? "" : id.getPath();
		return TAB_ITEMS.computeIfAbsent(path, name -> switch (name) {
			case "utility" -> new ItemStack(Items.DIAMOND_PICKAXE);
			case "combat" -> new ItemStack(Items.DIAMOND_SWORD);
			case "armor" -> new ItemStack(Items.DIAMOND_CHESTPLATE);
			case "misc" -> PotionContents.createItemStack(Items.POTION, Potions.REGENERATION);
			default -> new ItemStack(Items.BOOK);
		});
	}

	/** The stats window's widgets; also works out where its text goes. */
	private void addStatsWindow(CategoryDefinition category) {
		plusButtons.clear();
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

			String statId = stat.id();
			MiniButton plus = new MiniButton(x + 4, y + 1, MiniButton.Glyph.PLUS, Component.translatable("bloom.hub.stat.plus"),
					shift -> send(allocate, statId, shift ? Math.min(10, unspent) : 1)).live(() -> plusTooltip(statId), () -> askPreview(statId));
			plus.active = unspent > 0;
			this.addRenderableWidget(plus);
			plusButtons.put(statId, plus);

			// Natural points can only be taken back out at level 100 (and not again after Bloom+); XP-bought ones always.
			if (spendInvested ? ClientBloomState.config().allowStatRefund : service.canRefundStats(profile, category.id())) {
				MiniButton minus = new MiniButton(x + 4, y + 12, MiniButton.Glyph.MINUS, Component.translatable("bloom.hub.stat.minus"),
						shift -> send(allocate, stat.id(), shift ? -Math.min(10, inPool) : -1));
				minus.active = inPool > 0;
				this.addRenderableWidget(minus);
			}

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
			Button invest = Button.builder(Component.translatable("bloom.hub.invest", levels), b -> {
				// Shift: as many as the XP pays for, up to 10.
				int count = 1;

				if (shiftClick) {
					long xp = playerXpPoints();

					for (int n = 10; n > 1; n--) {
						if (service.investmentCost(progress, n) <= xp) {
							count = n;
							break;
						}
					}
				}

				send(Action.INVEST, category.id(), count);
			}).bounds(x + 4, y, STATS_W - 8, 14).build();
			investButton = invest;
			investCost = cost;
			invest.active = playerXpPoints() >= cost;
			invest.setTooltip(Tooltip.create(Component.translatable("bloom.hub.invest.tooltip", cost, levels).append("\n")
					.append(Component.translatable("bloom.hub.invest.shift").withStyle(ChatFormatting.DARK_GRAY))));
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

	/** The player's vanilla XP as raw points (a long: huge levels overflow an int). */
	private long playerXpPoints() {
		if (this.minecraft.player == null) {
			return 0L;
		}

		return PlayerXpWallet.totalPoints(this.minecraft.player.experienceLevel, this.minecraft.player.experienceProgress);
	}

	@Override
	public void tick() {
		super.tick();

		if (sheetWindow != null) {
			sheetWindow.tick();
		}

		previewAge++;

		if (builtVersion != ClientBloomState.version()) {
			previewAsked = null; // the profile changed: ask again
			this.rebuildWidgets();
			return;
		}

		// XP can change while the hub is open (orbs, commands): keep Buy point in step with it.
		if (investButton != null) {
			investButton.active = playerXpPoints() >= investCost;
		}

		// The build switch's countdown, in seconds.
		if (buildSecondsShown > 0 && this.minecraft.level != null
				&& Math.max(0L, (buildReadyAt - this.minecraft.level.getGameTime() + 19) / 20) != buildSecondsShown) {
			this.rebuildWidgets();
			return;
		}

		// The Major reset's countdown: rebuild when the minutes it shows change (and when it's ready again).
		if (resetMajorsButton != null && resetMinutesShown > 0 && this.minecraft.level != null
				&& minutesUntil(resetMajorsReadyAt, this.minecraft.level.getGameTime()) != resetMinutesShown) {
			this.rebuildWidgets();
		}
	}

	/** Buy point's center and whether it can be clicked, or null when it isn't shown (client game test). */
	public int[] investButtonForTests() {
		if (investButton == null) {
			return null;
		}

		return new int[] {investButton.getX() + investButton.getWidth() / 2, investButton.getY() + investButton.getHeight() / 2,
				investButton.active ? 1 : 0};
	}

	/** What the hub thinks the player's XP and the next point's cost are (client game test diagnostics). */
	public String investStateForTests() {
		return "level=" + (this.minecraft.player == null ? -1 : this.minecraft.player.experienceLevel) + " points=" + playerXpPoints()
				+ " cost=" + investCost;
	}

	// ------------------------------------------------------------------ canvas layout

	/** A Major (or a weapon tree's node, {@code group} set) at a point on the canvas, drawn {@code size} pixels wide. */
	private record Node(MajorDefinition major, String group, int x, int y, boolean center, int size) {
		Node(MajorDefinition major, String group, int x, int y, boolean center) {
			this(major, group, x, y, center, MAJOR_ICON);
		}

		Node moved(int dx, int dy) {
			return new Node(major, group, x + dx, y + dy, center, size);
		}
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
			centerOnPage();
		} else {
			scatterLayout(majors);
		}
	}

	/** Moves the tree so its extent (icons and their glow) is centred on the page. */
	private void centerOnPage() {
		if (nodes.isEmpty()) {
			return;
		}

		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;

		for (Node node : nodes) {
			int half = node.size() / 2 + NODE_GLOW;
			minX = Math.min(minX, node.x() - half);
			maxX = Math.max(maxX, node.x() + half);
			minY = Math.min(minY, node.y() - half);
			maxY = Math.max(maxY, node.y() + half);
		}

		int dx = -((minX + maxX) / 2);
		int dy = -((minY + maxY) / 2);
		nodes.replaceAll(node -> node.moved(dx, dy));
		links.replaceAll(link -> new Link(link.x0() + dx, link.y0() + dy, link.x1() + dx, link.y1() + dy, link.majors()));
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

		// Universal Majors: rows along the bottom, the priciest (Copycat) in the middle, the rest around it in order.
		if (universal.size() > 2) {
			MajorDefinition priciest = universal.stream().max(java.util.Comparator.comparingInt(MajorDefinition::cost)).orElseThrow();
			universal.remove(priciest);
			universal.add(Math.min(UNIVERSALS_PER_ROW, universal.size() + 1) / 2, priciest);
		}

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

	/**
	 * Tabs without weapon trees: big icons over the whole page. A Major with a {@code hub_position} in its JSON
	 * sits exactly there (pixels of the tab's picture, from its top-left corner), so it can be placed to fit
	 * the art. The rest are scattered: the page is split into a grid with about as many cells as Majors (spare
	 * cells spread out as gaps), and each Major sits somewhere in its cell, nudged by an amount fixed by its id,
	 * so it looks loose but never overlaps and stays the same every time (the background templates show it).
	 */
	private void scatterLayout(List<MajorDefinition> majors) {
		int count = majors.size();

		if (count == 0) {
			return;
		}

		double areaW = BOARD_W - 2 * SCATTER_MARGIN;
		double areaH = BOARD_H - 2 * SCATTER_MARGIN;
		int columns = Math.max(1, (int) Math.ceil(Math.sqrt(count * areaW / areaH)));
		int rows = (int) Math.ceil(count / (double) columns);
		int cells = columns * rows;
		double cellW = areaW / columns;
		double cellH = areaH / rows;
		double nudgeX = Math.max(0.0, (cellW - BIG_ICON - 6) / 2.0);
		double nudgeY = Math.max(0.0, (cellH - BIG_ICON - 6) / 2.0);

		int shift = columns > 2 ? 2 : 1;

		for (int k = 0; k < count; k++) {
			// Spare cells fall every so often along the rows; shifting each row keeps them from lining up into an empty column.
			int cell = (int) Math.floor((k + 0.5) * cells / count);
			int row = cell / columns;
			int column = (cell % columns + row * shift) % columns;
			double x = -areaW / 2.0 + (column + 0.5) * cellW;
			double y = -areaH / 2.0 + (row + 0.5) * cellH;
			int hash = majors.get(k).id().hashCode();
			x += ((hash & 0xFFFF) / 65535.0 * 2.0 - 1.0) * nudgeX;
			y += (((hash >>> 16) & 0xFFFF) / 65535.0 * 2.0 - 1.0) * nudgeY;
			Optional<com.freshorangeslices.bloomlevelup.definition.HubPosition> placed = majors.get(k).hubPosition();

			if (placed.isPresent()) {
				x = placed.get().x() - BOARD_W / 2.0;
				y = placed.get().y() - BOARD_H / 2.0;
			}

			nodes.add(new Node(majors.get(k), null, (int) Math.round(x), (int) Math.round(y), false, BIG_ICON));
		}
	}

	/** Left edge of the canvas area the stats window doesn't cover. */
	private int freeLeft() {
		return canvasX + STATS_COLUMN;
	}

	private int freeRight() {
		return canvasX + canvasW;
	}

	/** Left edge of the view: right of the stats window, or the canvas edge while it's folded away (it then sits on the page). */
	private int freeLeftNow() {
		return statsCollapsed ? canvasX : freeLeft();
	}

	private int viewTop() {
		return canvasY;
	}

	private int viewBottom() {
		return canvasY + canvasH;
	}

	/** Whether the page is bigger than the view, so dragging shows more of it. */
	private boolean canPan() {
		return BOARD_W > freeRight() - freeLeftNow() || BOARD_H > viewBottom() - viewTop();
	}

	/** The page sits in the middle of the view; where it doesn't fit, it moves only until an edge reaches the view's edge. */
	private void clampPan() {
		panX = clampAxis(panX, freeLeftNow(), freeRight(), BOARD_W);
		panY = clampAxis(panY, viewTop(), viewBottom(), BOARD_H);
	}

	private static double clampAxis(double pan, int low, int high, int size) {
		if (size <= high - low) {
			return (low + high) / 2.0;
		}

		return Math.max(high - size / 2.0, Math.min(low + size / 2.0, pan));
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
	public void extractRenderState(GuiGraphicsExtractor g, int realMouseX, int realMouseY, float delta) {
		// While the details window is open, nothing under it reacts to the mouse.
		int mouseX = overlayOpen() ? -10000 : realMouseX;
		int mouseY = overlayOpen() ? -10000 : realMouseY;
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
			List<FormattedCharSequence> walletTooltip = drawWallet(g, mouseX, mouseY);

			if (walletTooltip != null && tooltip == null) {
				tooltip = walletTooltip;
			}
			List<FormattedCharSequence> statTooltip = drawStatsWindow(g, category, mouseX, mouseY);

			if (statTooltip != null) {
				tooltip = statTooltip;
			}
		}

		drawStatusLine(g);

		// Which build this is, so an old jar is easy to spot.
		Component build = Component.literal("v" + BloomLevelUp.version());
		g.text(this.font, build, panelX + panelW - 6 - this.font.width(build), panelY + panelH - 14, 0xFF5E5648);
		super.extractRenderState(g, mouseX, mouseY, delta);

		if (detailMajor != null) {
			drawDetails(g, realMouseX, realMouseY);
			return;
		}

		if (sheetWindow != null) {
			sheetWindow.render(g, realMouseX, realMouseY);
			return;
		}

		if (tooltip != null) {
			g.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
		}
	}

	// ------------------------------------------------------------------ details window

	private int detailW() {
		return Math.min(300, this.width - 40);
	}

	private int detailH() {
		return Math.min(232, this.height - 30);
	}

	private int detailX() {
		return (this.width - detailW()) / 2;
	}

	private int detailY() {
		return (this.height - detailH()) / 2;
	}

	private int detailBodyH() {
		return detailH() - DETAIL_HEADER - 14;
	}

	private void openDetails(MajorDefinition major) {
		detailMajor = major;
		detailScroll = 0.0;
		this.rebuildWidgets();
	}

	private void closeDetails() {
		detailMajor = null;
		this.rebuildWidgets();
	}

	private boolean onDetailClose(double x, double y) {
		int x0 = detailX() + detailW() - 15;
		int y0 = detailY() + 3;
		return x >= x0 && x < x0 + 12 && y >= y0 && y < y0 + 12;
	}

	private boolean inDetailWindow(double x, double y) {
		return x >= detailX() && x < detailX() + detailW() && y >= detailY() && y < detailY() + detailH();
	}

	/**
	 * A gold-framed window over the dimmed hub: the Major's icon, name, tab, tree and cost, then
	 * its sections, scrolled with the wheel. Esc, the × or a click outside closes it.
	 */
	private void drawDetails(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		MajorDefinition major = detailMajor;
		g.nextStratum();
		g.fill(0, 0, this.width, this.height, 0xB0000000);
		int x0 = detailX();
		int y0 = detailY();
		int w = detailW();
		int h = detailH();
		g.fill(x0, y0, x0 + w, y0 + h, 0xF6141210);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME_SPRITE, x0 - FRAME, y0 - FRAME, w + 2 * FRAME, h + 2 * FRAME);

		Identifier icon = IconTextures.resolve(major.icon());
		g.blit(RenderPipelines.GUI_TEXTURED, icon, x0 + 8, y0 + 7, 0.0F, 0.0F, 32, 32, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE,
				IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, 0xFFFFFFFF);
		MajorDetails.big(g, this.font, MajorDetails.heading(name(major.translationKey(), major.id())), x0 + 48, y0 + 7, 0xFFFFD86B);

		MutableComponent subtitle = Component.empty();
		ClientBloomState.definitions().category(major.categoryId())
				.ifPresent(category -> subtitle.append(name(category.translationKey(), category.id())));
		major.group().ifPresent(group -> subtitle.append(" · ").append(Component.translatable("bloom.hub.tree", capitalize(treeName(group)))));
		subtitle.append(" · ").append(Component.translatable("bloom.hub.major.cost", major.cost()));
		g.text(this.font, subtitle, x0 + 48, y0 + 25, MUTED);

		boolean overClose = onDetailClose(mouseX, mouseY);
		g.text(this.font, Component.literal("×"), x0 + w - 12, y0 + 5, overClose ? 0xFFFFFFFF : MUTED);

		// The stat sheet link: the Stats button's picture in a little box.
		boolean overLink = onDetailLink(mouseX, mouseY);
		int lx = detailLinkX();
		int ly = detailLinkY();
		g.fill(lx, ly, lx + 16, ly + 16, overLink ? 0xFFF2C443 : 0xFF8A7656);
		g.fill(lx + 1, ly + 1, lx + 15, ly + 15, overLink ? 0xFF6A5A44 : 0xFF3A3128);
		g.blit(RenderPipelines.GUI_TEXTURED, STAT_SHEET_ICON, lx + 2, ly + 2, 0.0F, 0.0F, 12, 12, 64, 64, 64, 64, 0xFFFFFFFF);
		g.fill(x0 + 8, y0 + DETAIL_HEADER - 4, x0 + w - 8, y0 + DETAIL_HEADER - 3, 0xFF8A6A2A);

		int bx = x0 + 10;
		int by = y0 + DETAIL_HEADER;
		int bw = w - 26;
		int bh = detailBodyH();
		g.enableScissor(bx - 2, by, bx + bw + 4, by + bh);
		int y = by - (int) Math.round(detailScroll);

		for (MajorDetails.Section section : detailSections) {
			MajorDetails.big(g, this.font, section.heading(), bx, y, 0xFFF2C443);
			y += 16;

			for (Component paragraph : section.paragraphs()) {
				Component shown = section.rows() ? row(paragraph) : paragraph;

				for (FormattedCharSequence line : this.font.split(shown, bw - 4)) {
					g.text(this.font, line, bx + 4, y, TEXT);
					y += 10;
				}

				y += 2;
			}

			y += 6;
		}

		g.disableScissor();
		detailContentH = y + (int) Math.round(detailScroll) - by;

		if (detailContentH > bh) {
			int track = bh;
			int thumb = Math.max(12, track * bh / detailContentH);
			int top = by + (int) Math.round((track - thumb) * (detailScroll / Math.max(1.0, detailContentH - bh)));
			g.fill(x0 + w - 9, by, x0 + w - 7, by + track, 0xFF2E261C);
			g.fill(x0 + w - 9, top, x0 + w - 7, top + thumb, 0xFFC98F1F);
		}

		g.centeredText(this.font, Component.translatable("bloom.hub.detail.close"), x0 + w / 2, y0 + h - 11, 0xFF6E6250);

		if (overLink) {
			MutableComponent tip = Component.translatable("bloom.hub.detail.sheet").withStyle(ChatFormatting.GOLD);
			String linked = StatSheetWindow.linkedSwitch(major.id());

			if (linked != null) {
				tip.append("\n").append(Component.translatable("bloom.hub.detail.sheet.switch", Component.translatable("bloom.sheet.toggle." + linked))
						.withStyle(ChatFormatting.GRAY));
			}

			g.setTooltipForNextFrame(this.font, this.font.split(tip, 200), mouseX, mouseY);
		}
	}

	/** A numbers row, "Label: value", with the label in gold. */
	private static Component row(Component paragraph) {
		String text = paragraph.getString();
		int colon = text.indexOf(':');

		if (colon <= 0) {
			return paragraph;
		}

		return Component.literal(text.substring(0, colon + 1)).withStyle(MajorDetails.labelStyle())
				.append(Component.literal(text.substring(colon + 1)).withStyle(ChatFormatting.WHITE));
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (detailMajor != null && event.isEscape()) {
			closeDetails();
			return true;
		}

		if (sheetWindow != null && event.isEscape()) {
			closeSheet();
			return true;
		}

		return super.keyPressed(event);
	}

	/** Asks the server what one more point in a stat would change: when the mouse moves to another +, then once a second. */
	private void askPreview(String statId) {
		if ((statId.equals(previewAsked) && previewAge < 20) || !ClientPlayNetworking.canSend(SheetPayloads.Preview.TYPE)) {
			return;
		}

		previewAsked = statId;
		previewAge = 0;
		ClientPlayNetworking.send(new SheetPayloads.Preview(statId));
	}

	/** The + button's tooltip, with what one more point would change (worked out by the server). */
	private static Component plusTooltip(String statId) {
		MutableComponent tip = Component.translatable("bloom.hub.stat.plus");

		if (!ClientPlayNetworking.canSend(SheetPayloads.Preview.TYPE)) {
			return tip;
		}

		List<Component> lines = ClientBloomState.preview(statId);
		tip.append("\n").append(Component.translatable("bloom.sheet.preview.header").withStyle(ChatFormatting.GOLD));

		if (lines == null) {
			tip.append("\n").append(Component.translatable("bloom.sheet.ui.loading").withStyle(ChatFormatting.DARK_GRAY));
		} else if (lines.isEmpty()) {
			tip.append("\n").append(Component.translatable("bloom.sheet.preview.none").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			for (Component line : lines) {
				tip.append("\n").append(line);
			}
		}

		return tip;
	}

	/** Whether the details window or the stat sheet covers the hub (nothing under it reacts then). */
	private boolean overlayOpen() {
		return detailMajor != null || sheetWindow != null;
	}

	private void openSheet(CategoryDefinition category) {
		sheetWindow = newSheet(category);
		this.rebuildWidgets();
	}

	/** The details window's stat sheet link: closes it and opens the sheet showing that Major at work. */
	private void showInSheet(MajorDefinition major) {
		Optional<CategoryDefinition> category = ClientBloomState.definitions().category(major.categoryId());

		if (category.isEmpty()) {
			return;
		}

		detailMajor = null;
		sheetWindow = newSheet(category.get());
		sheetWindow.focus(major);
		this.rebuildWidgets();
	}

	/** Where the details window's stat sheet link sits (left of the ×). */
	private int detailLinkX() {
		return detailX() + detailW() - 34;
	}

	private int detailLinkY() {
		return detailY() + 4;
	}

	private boolean onDetailLink(double x, double y) {
		return x >= detailLinkX() && x < detailLinkX() + 16 && y >= detailLinkY() && y < detailLinkY() + 16;
	}

	private static StatSheetWindow newSheet(CategoryDefinition category) {
		return new StatSheetWindow(category.id(), Component.translatable("bloom.sheet.ui.title", name(category.translationKey(), category.id())),
				FRAME_SPRITE, FRAME);
	}

	private void closeSheet() {
		sheetWindow = null;

		if (sheetOnly) {
			this.onClose();
			return;
		}

		this.rebuildWidgets();
	}

	/** "combat" for "bloomlevelup:combat" (the lang keys per tab). */
	private static String categoryKey(String id) {
		int colon = id.indexOf(':');
		return colon < 0 ? id : id.substring(colon + 1);
	}

	/** Opens the stat sheet of the current tab, as its button does (client game test). */
	public void openSheetForTests() {
		CategoryDefinition category = currentCategory();

		if (category != null) {
			openSheet(category);
		}
	}

	/** The open stat sheet: switches to the full breakdown, or turns a "show it at max" switch on (client game test). */
	public void sheetForTests(boolean details, String toggle) {
		sheetForTests(details, toggle, true);
	}

	/** As above, with the switch put on Max ({@code on}) or Off. */
	public void sheetForTests(boolean details, String toggle, boolean on) {
		if (sheetWindow != null) {
			sheetWindow.showDetailsForTests(details);

			if (toggle != null) {
				sheetWindow.setToggleForTests(toggle, on);
			}
		}
	}

	public boolean sheetOpenForTests() {
		return sheetWindow != null;
	}

	/** The details window's stat sheet link, as a click on it does (client game test). */
	public void showInSheetForTests() {
		if (detailMajor != null) {
			showInSheet(detailMajor);
		}
	}

	/** Pins a weapon in the open sheet, or picks one (client game test). */
	public void sheetSlotForTests(int pin, int pick) {
		if (sheetWindow != null) {
			sheetWindow.pinForTests(pin);
			sheetWindow.pickSlotForTests(pick);
		}
	}

	/** The open sheet's weapon choice and switches, "slot=3 predator=1" (client game test). */
	public String sheetStateForTests() {
		return sheetWindow == null ? "closed" : "slot=" + sheetWindow.slotForTests() + " " + new java.util.TreeMap<>(sheetWindow.modesForTests());
	}

	/** Whether a details window is open (client game test). */
	public boolean detailsOpenForTests() {
		return detailMajor != null;
	}

	/** Opens a Major's details window, as a right-click does (client game test). */
	public void openDetailsForTests(String majorId) {
		ClientBloomState.definitions().major(majorId).ifPresent(this::openDetails);
	}

	private void drawPanel(GuiGraphicsExtractor g) {
		g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME_SPRITE, panelX - FRAME, panelY - FRAME, panelW + 2 * FRAME, panelH + 2 * FRAME);
	}

	/**
	 * The tab's picture (416x320, though any size works) drawn pixel for pixel (one picture pixel per GUI pixel,
	 * like vanilla's GUI art), centred on the page and moving with it; plain dark around it. Until it exists:
	 * the advancements screen's stone, darkened, filling the page.
	 */
	private void drawBackground(GuiGraphicsExtractor g) {
		g.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
		g.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF120F0C);

		if (backdrop != null) {
			int w = backdrop.width();
			int h = backdrop.height();
			g.blit(RenderPipelines.GUI_TEXTURED, backdrop.texture(), screenX(0) - w / 2, screenY(0) - h / 2, 0.0F, 0.0F, w, h, w, h, w, h, 0xFFFFFFFF);
			g.disableScissor();
			return;
		}

		int left = screenX(-BOARD_W / 2);
		int top = screenY(-BOARD_H / 2);
		int x0 = Math.max(canvasX, left);
		int y0 = Math.max(canvasY, top);
		int x1 = Math.min(canvasX + canvasW, left + BOARD_W);
		int y1 = Math.min(canvasY + canvasH, top + BOARD_H);

		if (x1 > x0 && y1 > y0) {
			for (int x = left; x < left + BOARD_W; x += 16) {
				for (int y = top; y < top + BOARD_H; y += 16) {
					if (x + 16 > x0 && x < x1 && y + 16 > y0 && y < y1) {
						g.blit(RenderPipelines.GUI_TEXTURED, FALLBACK_TILE, x, y, 0.0F, 0.0F, 16, 16, 16, 16, 16, 16, 0xFFFFFFFF);
					}
				}
			}

			g.fill(x0, y0, x1, y1, 0xB80E0B09);
		}

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
		int size = node.size();
		int x = cx - size / 2;
		int y = cy - size / 2;
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
			drawGlow(g, x, y, size, now, seed);
			tint = 0xFFFFFFFF;
		} else if (owned != null) {
			frame(g, x - 1, y - 1, size + 2, 0xFF9A5A44);
			tint = ICON_TINT_OWNED_OFF;
		} else if (locked) {
			g.fill(x, y, x + size, y + size, 0xC0000000);
			tint = ICON_TINT_LOCKED;
		} else {
			boolean affordable = major.cost() <= profile.availableMajorLevels();
			g.fill(x, y, x + size, y + size, 0xA0000000);
			frame(g, x - 1, y - 1, size + 2, affordable ? 0x90EFE6D2 : 0x50EFE6D2);
			tint = ICON_TINT_UNOWNED;
		}

		g.blit(RenderPipelines.GUI_TEXTURED, IconTextures.resolve(major.icon()), x, y, 0.0F, 0.0F, size, size,
				IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, IconTextures.SOURCE_SIZE, tint);

		// Owned: a small dot in the corner, green when on, red when switched off (so off never looks refunded).
		if (owned != null) {
			dot(g, x + size - 7, y + 1, owned.enabled() ? 0xFF4FD34F : 0xFFE0453A, owned.enabled() ? 0xFF1E6B1E : 0xFF6B1A14);
		} else {
			// Cost badge for anything not yet owned.
			String cost = Integer.toString(major.cost());
			g.text(this.font, Component.literal(cost), x + size - this.font.width(cost), y + size - 7, locked ? MUTED : ACCENT);
		}

		boolean inside = pointing && mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;

		if (inside) {
			frame(g, x - 2, y - 2, size + 4, 0xFFFFFFFF);
		}

		return inside;
	}

	/** Owned + on: a soft pulsing halo with a small mote orbiting the icon. */
	private static void drawGlow(GuiGraphicsExtractor g, int x, int y, int size, long now, int seed) {
		double t = (now % 2400L) / 2400.0;
		int pulse = (int) (90 + 70 * Math.sin(t * Math.PI * 2));
		int halo = (pulse << 24) | 0xE5933F;
		g.fill(x - 3, y - 3, x + size + 3, y + size + 3, (pulse / 3 << 24) | 0xE5933F);
		frame(g, x - 2, y - 2, size + 4, halo);

		double angle = ((now + seed * 337L) % 3000L) / 3000.0 * Math.PI * 2;
		int cx = x + size / 2;
		int cy = y + size / 2;
		int r = size / 2 + 3;
		int mx = cx + (int) Math.round(Math.cos(angle) * r);
		int my = cy + (int) Math.round(Math.sin(angle) * r);
		g.fill(mx - 1, my - 1, mx + 1, my + 1, 0xFFFFF1C9);
	}

	/**
	 * The Major Level counter, in the strip under the page (left of the build number), so nothing on the page
	 * sits under it. Returns its tooltip when the mouse is on it.
	 */
	private List<FormattedCharSequence> drawWallet(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		BloomProfile profile = ClientBloomState.profile();
		Component wallet = Component.translatable("bloom.hub.major_levels", profile.availableMajorLevels(), profile.spentMajorLevels());
		Component build = Component.literal("v" + BloomLevelUp.version());
		int right = panelX + panelW - 6 - this.font.width(build) - 10;
		int left = right - this.font.width(wallet);
		int y = panelY + panelH - 14;
		g.text(this.font, wallet, left, y, TEXT);

		if (mouseX >= left && mouseX < right && mouseY >= y - 2 && mouseY < y + 10) {
			return List.of(Component.translatable("bloom.hub.major_hint").withStyle(ChatFormatting.GRAY).getVisualOrderText());
		}

		return null;
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

			lines.add(Component.translatable("bloom.hub.major.click_details").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
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
			// Nothing to report: say the page can be dragged, if it's bigger than the view.
			if (ClientBloomState.ready() && currentCategory() != null && canPan()) {
				g.text(this.font, Component.translatable("bloom.hub.drag"), panelX + STRIP_TEXT_X, panelY + panelH - 14, MUTED);
			}

			return;
		}

		MutableComponent message = Component.translatable("bloom.result." + result.outcome().toLowerCase(Locale.ROOT));

		if (!result.involved().isEmpty() && !"CONFLICT".equals(result.outcome())) {
			message.append(" ").append(joinNames(result.involved()));
		}

		g.text(this.font, message, panelX + STRIP_TEXT_X, panelY + panelH - 14, BAD);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (sheetWindow != null) {
			if (!sheetWindow.mouseClicked(event.x(), event.y(), event.button() == InputConstants.MOUSE_BUTTON_RIGHT)) {
				closeSheet();
			}

			return true;
		}

		if (detailMajor != null) {
			if (onDetailLink(event.x(), event.y())) {
				click();
				showInSheet(detailMajor);
			} else if (onDetailClose(event.x(), event.y()) || !inDetailWindow(event.x(), event.y())) {
				closeDetails();
			}

			return true;
		}

		shiftClick = event.hasShiftDown();
		MajorDefinition major = onOpenCanvas(event.x(), event.y()) ? majorAt(event.x(), event.y()) : null;
		lastClickForTests = String.format(Locale.ROOT, "button=%d x=%.1f y=%.1f major=%s", event.button(), event.x(), event.y(),
				major == null ? "-" : major.id());

		if (super.mouseClicked(event, doubleClick)) {
			lastClickForTests += " (taken by a widget)";
			return true;
		}

		if (major == null) {
			// Empty canvas: drag to see the rest of the page (only when it's bigger than the view).
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && onOpenCanvas(event.x(), event.y()) && canPan()) {
				panning = true;
				return true;
			}

			return false;
		}

		boolean owned = ClientBloomState.profile().owns(major.id());

		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			// Right-click an owned Major: its details window (single Majors aren't refunded any more).
			if (owned) {
				click();
				openDetails(major);
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
		if (overlayOpen()) {
			return true;
		}

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
		if (sheetWindow != null) {
			sheetWindow.mouseScrolled(scrollY);
			return true;
		}

		if (detailMajor != null) {
			detailScroll = Math.max(0.0, Math.min(Math.max(0, detailContentH - detailBodyH()), detailScroll - scrollY * 12.0));
			return true;
		}

		if (onOpenCanvas(mouseX, mouseY) && canPan()) {
			panX += scrollX * 20.0;
			panY += scrollY * 20.0;
			clampPan();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** What the white reset button sends once its confirm screen says yes (client game test). */
	public void resetTabMajorsForTests() {
		CategoryDefinition category = currentCategory();

		if (category != null) {
			send(Action.RESET_MAJORS, category.id(), 0);
		}
	}

	/** Folds the stats window away or brings it back, as its arrow does (client game test). */
	public static void foldStatsForTests(boolean folded) {
		statsCollapsed = folded;
	}

	/** Moves the page so a Major is in view, as far as the page's edges allow (client game test). */
	public void revealForTests(String majorId) {
		for (Node node : nodes) {
			if (node.major() != null && node.major().id().equals(majorId)) {
				panX = (freeLeftNow() + freeRight()) / 2.0 - node.x();
				panY = (viewTop() + viewBottom()) / 2.0 - node.y();
				clampPan();
				return;
			}
		}
	}

	/** Center of a Major's icon in GUI coordinates, or null if it isn't on this page or can't be clicked (client game test). */
	/** The centre of a stat's + button, in GUI coordinates, or null (client game test). */
	public int[] plusCenterForTests(String statId) {
		MiniButton plus = plusButtons.get(statId);
		return plus == null ? null : new int[] {plus.getX() + plus.getWidth() / 2, plus.getY() + plus.getHeight() / 2};
	}

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

			int x = screenX(node.x()) - node.size() / 2;
			int y = screenY(node.y()) - node.size() / 2;

			if (mouseX >= x && mouseX < x + node.size() && mouseY >= y && mouseY < y + node.size()) {
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
