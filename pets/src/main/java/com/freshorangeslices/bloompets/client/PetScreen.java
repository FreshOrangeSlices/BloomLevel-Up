package com.freshorangeslices.bloompets.client;

import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.PetBonuses;
import com.freshorangeslices.bloompets.PetModes;
import com.freshorangeslices.bloompets.PetSkills;
import com.freshorangeslices.bloompets.PetTrees;
import com.freshorangeslices.bloompets.Pets;
import com.freshorangeslices.bloompets.network.PetsNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The pet screen (FreshOrangeSlices' sketch, docs/design/art/pets-screen-sketch.png), in the Skill Hub's look: a dark
 * panel in the hub's gold frame. The player's pets on the left, Bloom pets first (orange) then companions (red), the
 * party's marked pink; the chosen pet in the middle, standing on a piece of the hub's art (the combat cave for fighters
 * and companions, the meadow for the rest) and turning to follow the mouse, with its skill slots on either side (a
 * combat pet's nine skills and the level-100 perk, dimmed until its level opens them) and a switch between its own
 * traits and its Bloom weapon tree (One and the Same); its level and XP, health and where it is; Call, the party and
 * its storage at the bottom.
 */
public final class PetScreen extends Screen {
	private static final int W = 340;
	private static final int H = 232;
	/** The gold frame, drawn this far outside the panel (as the hub's). */
	private static final int FRAME = 6;
	/** The list panel's right edge, from the panel's left. */
	private static final int LIST_W = 110;
	private static final int ROW_H = 14;
	private static final int LIST_TOP = 42;
	/** The divider under the pet's name. */
	private static final int HEAD = 36;
	private static final int SLOT = 18;
	private static final int SLOT_GAP = 2;
	/** Skill slots a side: the nine skills and the level-100 perk. */
	private static final int SLOTS = 5;
	private static final float BIG = 1.5F;

	// The Skill Hub's palette (Bloom's HubScreen and its details window).
	static final int PANEL = 0xF0141210;
	static final int PANEL_EDGE = 0xFF5A4A33;
	static final int TEXT = 0xFFEFE6D2;
	static final int MUTED = 0xFFA99F8A;
	static final int ACCENT = 0xFFE5933F;
	static final int GOLD = 0xFFFFD86B;
	static final int FAINT = 0xFF6E6250;
	private static final int DIVIDER = 0xFF8A6A2A;
	private static final int BAR_BACK = 0xFF2A241D;
	private static final int ROW_ON = 0xFF3A3128;
	private static final int SCROLL_THUMB = 0xFFC98F1F;
	private static final int SCROLL_TRACK = 0xFF2E261C;
	private static final int BLOOM = ACCENT;
	/** Companions' red, light enough to read on the dark panel. */
	private static final int MODDED = 0xFFD9574A;
	private static final int PARTY = 0xFFEC6AAE;
	private static final int PARTY_EDGE = 0xFF7A2450;
	private static final int HEALTH = 0xFFD8453A;
	private static final int HEALTH_SHINE = 0xFFF0705F;
	/** The hub's XP green. */
	private static final int XP_FILL = 0xFF7CC242;
	private static final int XP_SHINE = 0xFF9AD65E;
	private static final int SKILL_EDGE = 0xFFC98F1F;
	private static final int SKILL_TEXT = 0xFFEFE6D2;
	private static final int MOOD_OK = 0xFFE0B040;
	private static final int FOOD = 0xFFD08A3A;
	private static final int FOOD_SHINE = 0xFFE8A65A;

	private static final ResourceLocation FRAME_TEXTURE = new ResourceLocation(BloomPets.MOD_ID, "textures/gui/frame.png");
	private static final int FRAME_SIZE = 28;
	private static final int FRAME_BORDER = 6;
	/** Pieces of the hub's backgrounds (256x176), drawn a picture pixel per GUI pixel like the hub's, centred. */
	private static final ResourceLocation BACKDROP_COMBAT = new ResourceLocation(BloomPets.MOD_ID, "textures/gui/backdrop_combat.png");
	private static final ResourceLocation BACKDROP_MEADOW = new ResourceLocation(BloomPets.MOD_ID, "textures/gui/backdrop_meadow.png");
	private static final int BACKDROP_W = 256;
	private static final int BACKDROP_H = 176;

	private enum Filter {
		ALL, PARTY, BLOOM, COMPANIONS
	}

	private PetsNetwork.PetList list;
	private long receivedAt;
	private final Map<UUID, LivingEntity> models = new HashMap<>();
	private UUID selected;
	private Filter filter = Filter.ALL;
	private boolean bloomView;
	private int scroll;
	private Component message;
	private long messageAt;
	private int left;
	private int top;
	private Button filterButton;
	private Button viewButton;
	private Button callButton;
	private Button partyButton;
	private Button storageButton;
	private Button postButton;
	/** In the pet's picture: what it does when it isn't working (Follow, Wander, Stay; click for the next), rename, release. */
	private Button modeButton;
	private Button renameButton;
	private Button releaseButton;
	/** The pet's new name, typed over its name while renaming (Enter keeps it, Escape doesn't). */
	private EditBox nameBox;
	private UUID renaming;
	/** A question waiting for yes or no (picking or resetting a tree), drawn over the pet, and what yes does. */
	private Component confirmText;
	private Runnable confirmYes;
	private Runnable confirmNo;
	/** Companions whose Spirit this screen has asked about already (it asks once a visit; the chat asked first). */
	private final java.util.Set<UUID> askedSpirit = new java.util.HashSet<>();
	private Button yesButton;
	private Button noButton;
	private int ticks;
	/** A tooltip for something drawn in the pet's panel (its mood or hunger), drawn last, over the buttons. */
	private List<FormattedCharSequence> panelTip;

	public PetScreen(PetsNetwork.PetList list) {
		super(Component.translatable("bloompets.screen.title"));
		accept(list);
		bloomView = list.bloomView() && list.oneAndTheSame();
	}

	private void accept(PetsNetwork.PetList list) {
		this.list = list;
		this.receivedAt = System.currentTimeMillis();
		this.models.clear();

		if (list.message() != null) {
			message = list.message();
			messageAt = receivedAt;
		}

		if (selected == null || list.pets().stream().noneMatch(e -> e.pet().equals(selected))) {
			selected = list.pets().stream().filter(PetsNetwork.Entry::party).map(PetsNetwork.Entry::pet).findFirst()
					.orElse(list.pets().isEmpty() ? null : list.pets().get(0).pet());
		}

		scroll = Mth.clamp(scroll, 0, Math.max(0, shown().size() - visibleRows()));
	}

	/** New data from the server (after an action, or a fresh list). */
	public void refresh(PetsNetwork.PetList list) {
		accept(list);
		updateButtons();
	}

	private List<PetsNetwork.Entry> shown() {
		List<PetsNetwork.Entry> out = new ArrayList<>();

		for (Pets.Kind kind : Pets.Kind.values()) {
			for (PetsNetwork.Entry e : list.pets()) {
				boolean wanted = switch (filter) {
					case ALL -> true;
					case PARTY -> e.party() || e.kind() == Pets.Kind.MODDED;
					case BLOOM -> e.kind() == Pets.Kind.BLOOM;
					case COMPANIONS -> e.kind() == Pets.Kind.MODDED;
				};

				if (e.kind() == kind && wanted) {
					out.add(e);
				}
			}
		}

		return out;
	}

	private PetsNetwork.Entry current() {
		return list.pets().stream().filter(e -> e.pet().equals(selected)).findFirst().orElse(null);
	}

	/** Ticks until the party can change again, counted down here since the list came. */
	private int partyWait() {
		return Math.max(0, list.partyWait() - (int) ((System.currentTimeMillis() - receivedAt) / 50));
	}

	// Layout: the list panel on the left, the chosen pet to its right.

	private int mainLeft() {
		return left + LIST_W + 8;
	}

	private int mainRight() {
		return left + W - 8;
	}

	private int rowLeft() {
		return left + 8;
	}

	private int rowRight(int shown) {
		return left + LIST_W - (shown > visibleRows() ? 7 : 2);
	}

	private int visibleRows() {
		return (H - 6 - 22 - LIST_TOP) / ROW_H;
	}

	private int slotY(int i) {
		return top + HEAD + 6 + i * (SLOT + SLOT_GAP);
	}

	private int canvasTop() {
		return slotY(0);
	}

	private int canvasBottom() {
		return slotY(SLOTS - 1) + SLOT;
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = (height - H) / 2;
		filterButton = addRenderableWidget(new PetButton(left + 10, top + 23, LIST_W - 14, 14, filterLabel(), b -> {
			filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
			scroll = 0;
			b.setMessage(filterLabel());
		}));
		viewButton = addRenderableWidget(new PetButton(mainRight() - 16, top + 8, 16, 16, Component.literal("⇄"), b -> {
			bloomView = !bloomView;
			updateButtons();
		}));
		int buttonsY = top + H - 26;
		// Call, the party, a post and storage along the bottom.
		callButton = addRenderableWidget(new PetButton(mainLeft(), buttonsY, 40, 18, Component.translatable("bloompets.screen.call"),
				b -> act(PetsNetwork.Action.CALL)));
		partyButton = addRenderableWidget(new PetButton(mainLeft() + 42, buttonsY, 84, 18, Component.empty(), b -> act(PetsNetwork.Action.PARTY)));
		postButton = addRenderableWidget(new PetButton(mainLeft() + 128, buttonsY, 40, 18, Component.empty(), b -> act(PetsNetwork.Action.POST)));
		storageButton = addRenderableWidget(new PetButton(mainRight() - 44, buttonsY, 44, 18, Component.translatable("bloompets.screen.storage"),
				b -> act(PetsNetwork.Action.STORAGE)));
		// Its mode, top left in its picture; rename and release, top right.
		int canvasLeft = mainLeft() + SLOT + 6;
		int canvasRight = mainRight() - SLOT - 6;
		modeButton = addRenderableWidget(new PetButton(canvasLeft + 3, canvasTop() + 3, 62, 14, Component.empty(), b -> {
			PetsNetwork.Entry pet = current();
			PetModes.Mode mode = pet == null ? null : PetModes.Mode.byId(pet.mode());

			if (mode != null) {
				PetsClient.act(PetsNetwork.Action.MODE, pet.pet(), mode.next().id());
			}
		}));
		renameButton = addRenderableWidget(new PetButton(canvasRight - 33, canvasTop() + 3, 14, 14, Component.literal("\u270e"), b -> startRename()));
		releaseButton = addRenderableWidget(new PetButton(canvasRight - 17, canvasTop() + 3, 14, 14,
				Component.literal("\u2715").withStyle(ChatFormatting.RED), b -> {
					PetsNetwork.Entry pet = current();

					if (pet != null) {
						confirm(Component.translatable(pet.kind() == Pets.Kind.MODDED ? "bloompets.screen.release.confirm.companion"
								: "bloompets.screen.release.confirm", pet.name()), () -> PetsClient.act(PetsNetwork.Action.RELEASE, pet.pet()));
						updateButtons();
					}
				}));
		nameBox = addRenderableWidget(new EditBox(font, mainLeft(), top + 7, 150, 16, Component.translatable("bloompets.screen.rename")));
		nameBox.setMaxLength(PetModes.NAME_MAX);
		nameBox.visible = false;
		renaming = null;
		int middle = (mainLeft() + mainRight()) / 2;
		yesButton = addRenderableWidget(new PetButton(middle - 44, canvasBottom() - 24, 40, 16, Component.translatable("bloompets.screen.yes"), b -> {
			Runnable yes = confirmYes;
			closeConfirm();

			if (yes != null) {
				yes.run();
			}
		}));
		noButton = addRenderableWidget(new PetButton(middle + 4, canvasBottom() - 24, 40, 16, Component.translatable("bloompets.screen.no"), b -> {
			Runnable no = confirmNo;
			closeConfirm();

			if (no != null) {
				no.run();
			}
		}));
		closeConfirm();
		updateButtons();
	}

	/** Rename: the name box over its name, with its name in it. */
	private void startRename() {
		PetsNetwork.Entry pet = current();

		if (pet == null) {
			return;
		}

		renaming = pet.pet();
		nameBox.setValue(pet.name());
		nameBox.visible = true;
		setFocused(nameBox);
		nameBox.setFocused(true);
		updateButtons();
	}

	private void stopRename(boolean keep) {
		if (renaming != null && keep) {
			PetsClient.act(PetsNetwork.Action.RENAME, renaming, nameBox.getValue());
		}

		renaming = null;
		nameBox.visible = false;
		nameBox.setFocused(false);
		setFocused(null);
		updateButtons();
	}

	private Component filterLabel() {
		return Component.translatable("bloompets.screen.filter." + filter.name().toLowerCase(Locale.ROOT));
	}

	private void act(PetsNetwork.Action action) {
		PetsNetwork.Entry pet = current();

		if (pet != null) {
			PetsClient.act(action, pet.pet());
		}
	}

	private void updateButtons() {
		if (callButton == null) {
			return;
		}

		PetsNetwork.Entry pet = current();
		boolean any = pet != null;
		callButton.visible = partyButton.visible = storageButton.visible = postButton.visible = any;
		// Over the pet's picture: not while a question's asked there, nor while it's being renamed.
		boolean picture = any && confirmText == null;
		modeButton.visible = picture;
		renameButton.visible = releaseButton.visible = picture && renaming == null;

		if (renaming != null && (pet == null || !renaming.equals(pet.pet()))) {
			stopRename(false);
			return;
		}

		// The Pet / Bloom switch comes with One and the Same.
		if (!list.oneAndTheSame()) {
			bloomView = false;
		}

		viewButton.visible = any && list.oneAndTheSame();
		viewButton.setTooltip(Tooltip.create(Component.translatable(bloomView ? "bloompets.screen.view.bloom" : "bloompets.screen.view.pet")));

		if (!any) {
			return;
		}

		callButton.setTooltip(Tooltip.create(Component.translatable("bloompets.screen.call.tip", pet.name())));
		updatePartyButton(pet);
		updateModeButton(pet);
		renameButton.active = pet.loaded();
		renameButton.setTooltip(Tooltip.create(Component.translatable(pet.loaded() ? "bloompets.screen.rename.tip" : "bloompets.screen.away.tip", pet.name())));
		releaseButton.setTooltip(Tooltip.create(Component.translatable("bloompets.screen.release.tip", pet.name())));
		boolean storage = pet.slots() > 0;
		storageButton.active = storage;
		// A companion's Storage is its food pouch (one with a backpack of its own eats from that instead).
		storageButton.setTooltip(Tooltip.create(storage ? Component.translatable(pet.kind() == Pets.Kind.MODDED ? "bloompets.screen.pouch.tip"
				: "bloompets.screen.storage.tip", pet.slots())
				: Component.translatable(pet.kind() == Pets.Kind.MODDED ? "bloompets.screen.storage.modded" : "bloompets.storage.none", pet.name())));
		// A post: Bloom pets out of the party; "Unpost" calls one off its post.
		postButton.active = pet.kind() == Pets.Kind.BLOOM && (pet.posted() || !pet.party());
		postButton.setMessage(Component.translatable(pet.posted() ? "bloompets.screen.unpost" : "bloompets.screen.post"));
		postButton.setTooltip(Tooltip.create(Component.translatable(pet.kind() != Pets.Kind.BLOOM ? "bloompets.screen.post.modded"
				: pet.posted() ? "bloompets.screen.unpost.tip" : pet.party() ? "bloompets.screen.post.party" : "bloompets.screen.post.tip")));
	}

	/** Following, Wandering or Staying; the tip says what it does and what a click makes it. */
	private void updateModeButton(PetsNetwork.Entry pet) {
		PetModes.Mode mode = PetModes.Mode.byId(pet.mode());

		if (mode == null) {
			mode = PetModes.Mode.WANDER;
		}

		modeButton.active = pet.loaded();
		modeButton.setMessage(Component.translatable("bloompets.screen.mode." + mode.id()));
		modeButton.setTooltip(Tooltip.create(!pet.loaded() ? Component.translatable("bloompets.screen.away.tip", pet.name())
				: Component.translatable("bloompets.screen.mode.tip." + mode.id(), pet.name()).append("\n")
						.append(Component.translatable("bloompets.screen.mode.click", Component.translatable("bloompets.screen.mode." + mode.next().id()))
								.withStyle(ChatFormatting.GRAY))));
	}

	/** Join or leave, with the time left while the party can't change yet. */
	private void updatePartyButton(PetsNetwork.Entry pet) {
		if (pet.kind() == Pets.Kind.MODDED) {
			partyButton.active = false;
			partyButton.setMessage(Component.translatable("bloompets.screen.always"));
			partyButton.setTooltip(Tooltip.create(Component.translatable("bloompets.party.modded", pet.name())));
			return;
		}

		int wait = partyWait();
		String key = pet.party() ? "bloompets.screen.leave" : "bloompets.screen.join";
		partyButton.active = wait <= 0;
		partyButton.setMessage(wait > 0 ? Component.translatable(key + ".wait", time(wait)) : Component.translatable(key));
		partyButton.setTooltip(Tooltip.create(wait > 0 ? Component.translatable("bloompets.party.wait", time(wait))
				: Component.translatable("bloompets.screen.party.tip")));
	}

	private static String hours(int ticks) {
		int seconds = (ticks + 19) / 20;
		return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
	}

	private static String time(int ticks) {
		int seconds = (ticks + 19) / 20;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	@Override
	public void tick() {
		// Levels, health and rest as they change.
		if (++ticks % 40 == 0) {
			PetsClient.refresh();
		}

		// The party cooldown counts down on its button; the button comes back on when it's over.
		PetsNetwork.Entry pet = current();

		if (partyButton != null && pet != null && pet.kind() == Pets.Kind.BLOOM && (partyWait() > 0 || !partyButton.active)) {
			updatePartyButton(pet);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Typing a name: Enter keeps it, Escape doesn't, and every other key is for the box (K too).
		if (renaming != null) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
				stopRename(true);
				return true;
			}

			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
				stopRename(false);
				return true;
			}

			return nameBox.keyPressed(keyCode, scanCode, modifiers) || nameBox.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
		}

		if (PetsClient.OPEN.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// A question on screen takes every click until it's answered.
		if (confirmText != null) {
			return yesButton.mouseClicked(mouseX, mouseY, button) || noButton.mouseClicked(mouseX, mouseY, button) || true;
		}

		// A click away from the name box keeps the new name.
		if (renaming != null && !nameBox.isMouseOver(mouseX, mouseY)) {
			stopRename(true);
		}

		PetsNetwork.Entry chosen = current();

		if (bloomView && chosen != null) {
			for (int i = 0; i < SLOTS * 2; i++) {
				if (overSlot(i < SLOTS ? mainLeft() : mainRight() - SLOT, slotY(i % SLOTS), (int) mouseX, (int) mouseY)) {
					bloomClick(chosen, i);
					return true;
				}
			}
		}

		List<PetsNetwork.Entry> shown = shown();
		int rowRight = rowRight(shown.size());

		for (int i = 0; i < visibleRows() && i + scroll < shown.size(); i++) {
			int y = top + LIST_TOP + i * ROW_H;

			if (mouseX >= rowLeft() && mouseX < rowRight && mouseY >= y && mouseY < y + ROW_H) {
				selected = shown.get(i + scroll).pet();
				updateButtons();
				return true;
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (mouseX < left + LIST_W) {
			int max = Math.max(0, shown().size() - visibleRows());
			scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, max);
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderBackground(g);
		g.fill(left, top, left + W, top + H, PANEL);
		nineSlice(g, FRAME_TEXTURE, left - FRAME, top - FRAME, W + 2 * FRAME, H + 2 * FRAME, FRAME_SIZE, FRAME_BORDER);
		renderList(g, mouseX, mouseY);
		PetsNetwork.Entry pet = current();

		if (pet == null) {
			renderEmpty(g);
		} else {
			renderPet(g, pet, mouseX, mouseY);
		}

		// The buttons over its picture stay out of a question's way (asked here, or by the Spirit's box).
		boolean picture = pet != null && confirmText == null;
		modeButton.visible = picture;
		renameButton.visible = releaseButton.visible = picture && renaming == null;
		super.render(g, mouseX, mouseY, partialTick);

		if (message != null && System.currentTimeMillis() - messageAt < 5000) {
			g.drawCenteredString(font, message, width / 2, top + H + FRAME + 4, TEXT);
		}

		if (pet != null) {
			slotTooltip(g, pet, mouseX, mouseY);

			if (panelTip != null) {
				g.renderTooltip(font, panelTip, mouseX, mouseY);
			}
		}
	}

	/** The list panel, as the hub's stats window: a darker box with a brown edge and orange corners. */
	private void renderList(GuiGraphics g, int mouseX, int mouseY) {
		int x0 = left + 6;
		int y0 = top + 6;
		int x1 = left + LIST_W;
		int y1 = top + H - 6;
		g.fill(x0, y0, x1, y1, 0xE8141210);
		frame(g, x0, y0, x1, y1, PANEL_EDGE);
		corners(g, x0, y0, x1, y1);
		Component heading = title.copy().withStyle(ChatFormatting.BOLD);
		g.drawString(font, heading, x0 + 4, y0 + 5, ACCENT);
		g.drawString(font, Integer.toString(list.pets().size()), x0 + 8 + font.width(heading), y0 + 5, TEXT);

		List<PetsNetwork.Entry> shown = shown();
		int rowLeft = rowLeft();
		int rowRight = rowRight(shown.size());

		for (int i = 0; i < visibleRows() && i + scroll < shown.size(); i++) {
			PetsNetwork.Entry e = shown.get(i + scroll);
			int y = top + LIST_TOP + i * ROW_H;
			int color = e.kind() == Pets.Kind.MODDED ? MODDED : BLOOM;
			boolean hover = mouseX >= rowLeft && mouseX < rowRight && mouseY >= y && mouseY < y + ROW_H;

			if (e.pet().equals(selected)) {
				g.fill(rowLeft, y, rowRight, y + ROW_H, ROW_ON);
				g.fill(rowLeft, y, rowLeft + 2, y + ROW_H, color);
			} else if (hover) {
				g.fill(rowLeft, y, rowRight, y + ROW_H, 0x22EFE6D2);
			}

			String level = "Lv " + e.level();
			int levelX = rowRight - 19 - font.width(level);
			g.drawString(font, font.plainSubstrByWidth(e.name(), levelX - rowLeft - 9), rowLeft + 5, y + 3, color);
			g.drawString(font, level, levelX, y + 3, e.rest() > 0 ? MODDED : FAINT);

			if (e.party() || e.kind() == Pets.Kind.MODDED) {
				// Pink: in the party (companions always are).
				g.fill(rowRight - 10, y + 4, rowRight - 4, y + 10, PARTY_EDGE);
				g.fill(rowRight - 9, y + 5, rowRight - 5, y + 9, PARTY);
			}

			if (!e.loaded()) {
				g.drawString(font, "·", rowRight - 16, y + 2, MUTED);
			}
		}

		if (shown.isEmpty()) {
			g.drawString(font, Component.translatable("bloompets.screen.none"), rowLeft + 5, top + LIST_TOP + 3, MUTED);
		}

		if (shown.size() > visibleRows()) {
			// The hub's scrollbar: a dark track, an amber thumb.
			int trackTop = top + LIST_TOP;
			int trackH = visibleRows() * ROW_H;
			int thumbH = Math.max(8, trackH * visibleRows() / shown.size());
			int thumbY = trackTop + (trackH - thumbH) * scroll / Math.max(1, shown.size() - visibleRows());
			g.fill(x1 - 5, trackTop, x1 - 3, trackTop + trackH, SCROLL_TRACK);
			g.fill(x1 - 5, thumbY, x1 - 3, thumbY + thumbH, SCROLL_THUMB);
		}

		g.fill(x0 + 4, y1 - 16, x1 - 4, y1 - 15, PANEL_EDGE);
		String follows = list.pets().stream().filter(PetsNetwork.Entry::party).map(PetsNetwork.Entry::name).findFirst().orElse(null);
		g.drawString(font, font.plainSubstrByWidth(Component.translatable("bloompets.screen.follows", follows == null ? "-" : follows).getString(), x1 - x0 - 8),
				x0 + 4, y1 - 11, follows != null ? PARTY : MUTED);
	}

	private void renderEmpty(GuiGraphics g) {
		int mainLeft = mainLeft();
		int mainRight = mainRight();
		big(g, Component.translatable("bloompets.screen.empty").withStyle(ChatFormatting.BOLD), mainLeft, top + 9, GOLD);
		g.fill(mainLeft, top + HEAD, mainRight, top + HEAD + 1, DIVIDER);
		canvas(g, null, mainLeft, canvasTop(), mainRight, canvasBottom(), 0, 0);
		int y = canvasBottom() + 7;

		for (var line : font.split(Component.translatable("bloompets.command.none"), mainRight - mainLeft)) {
			g.drawString(font, line, mainLeft, y, MUTED);
			y += 10;
		}
	}

	private void renderPet(GuiGraphics g, PetsNetwork.Entry pet, int mouseX, int mouseY) {
		askSpirit(pet);
		int mainLeft = mainLeft();
		int mainRight = mainRight();
		panelTip = null;

		// Its name, large and gold as the hub's headings; its kind under it; which view the slots show, by the switch.
		Component view = Component.translatable(bloomView ? "bloompets.screen.view.bloom.short" : "bloompets.screen.view.pet.short");
		int viewX = list.oneAndTheSame() ? mainRight - 20 - font.width(view) : mainRight;

		if (list.oneAndTheSame()) {
			g.drawString(font, view, viewX, top + 12, MUTED);
		}
		if (renaming == null) {
			big(g, bold(pet.name(), (int) ((viewX - 6 - mainLeft) / BIG)), mainLeft, top + 9, GOLD);
		}
		Component level = Component.translatable("bloompets.screen.level", pet.level());
		g.drawString(font, level, mainLeft, top + 25, pet.level() >= pet.topLevel() ? GOLD : ACCENT);
		int kindX = mainLeft + font.width(level);
		// A companion's Spirit where a Bloom pet shows its category.
		String kind = " · " + typeName(pet) + " · " + (pet.kind() == Pets.Kind.MODDED && !pet.skills().isEmpty()
				? Component.translatable("bloompets.spirit.name." + pet.skills()).getString()
				: Component.translatable("bloompets.category." + pet.category()).getString());
		g.drawString(font, font.plainSubstrByWidth(kind, mainRight - kindX), kindX, top + 25, MUTED);

		// Its bonus for you, under the mouse on that line.
		if (pet.kind() == Pets.Kind.BLOOM && mouseX >= mainLeft && mouseX < mainRight && mouseY >= top + 24 && mouseY < top + 34) {
			panelTip = bonusTip(pet);
		}
		g.fill(mainLeft, top + HEAD, mainRight, top + HEAD + 1, DIVIDER);

		// The skill slots, five a side, and the pet between them.
		for (int i = 0; i < SLOTS; i++) {
			slot(g, pet, i, mainLeft, slotY(i), mouseX, mouseY);
			slot(g, pet, SLOTS + i, mainRight - SLOT, slotY(i), mouseX, mouseY);
		}

		canvas(g, pet, mainLeft + SLOT + 6, canvasTop(), mainRight - SLOT - 6, canvasBottom(), mouseX, mouseY);

		// Health and XP, as the hub's level bar.
		int y = canvasBottom() + 7;
		Component health = Component.translatable("bloompets.screen.health");
		Component xp = Component.translatable("bloompets.screen.xp");
		Component mood = Component.translatable("bloompets.screen.mood");
		int barX0 = mainLeft + Math.max(Math.max(font.width(health), font.width(xp)), font.width(mood)) + 6;
		bar(g, health, String.format(Locale.ROOT, "%.0f/%.0f", pet.health(), pet.maxHealth()),
				pet.maxHealth() > 0 ? pet.health() / pet.maxHealth() : 0.0F, HEALTH, HEALTH_SHINE, TEXT, mainLeft, barX0, mainRight, y);
		boolean max = pet.xpNext() <= 0;
		bar(g, xp, max ? Component.translatable("bloompets.screen.max").getString()
				: String.format(Locale.ROOT, "%,d / %,d", (int) pet.xp(), (int) Math.ceil(pet.xpNext())),
				max ? 1.0F : pet.xp() / pet.xpNext(), max ? SKILL_EDGE : XP_FILL, max ? GOLD : XP_SHINE, max ? GOLD : TEXT, mainLeft, barX0, mainRight, y + 11);
		// How it's doing (and how far), then where it is: labels in orange, as the hub's numbers.
		moodRow(g, pet, mood, barX0, mainLeft, mainRight, y + 22, mouseX, mouseY);
		labelled(g, "bloompets.screen.status", state(pet), mainLeft, y + 33, mainRight);
		labelled(g, "bloompets.screen.where", where(pet), mainLeft, y + 44, mainRight);

		if (confirmText != null) {
			drawConfirm(g);
		}
	}

	/** Mood (hearts as a bar: green, gold, red when it sulks, pink when it loves you) and hunger, side by side. */
	private void moodRow(GuiGraphics g, PetsNetwork.Entry pet, Component mood, int barX0, int x0, int x1, int y, int mouseX, int mouseY) {
		if (pet.mood() < 0) {
			labelled(g, "bloompets.screen.mood", Component.translatable("bloompets.screen.mood.modded").getString(), x0, y, x1);
			return;
		}

		int middle = x0 + (x1 - x0) / 2;
		boolean sad = pet.mood() < 30.0F;
		int color = pet.loved() ? PARTY : sad ? HEALTH : pet.mood() >= 70.0F ? XP_FILL : MOOD_OK;
		bar(g, mood, "", pet.mood() / 100.0F, color, 0x30FFFFFF | color & 0x00FFFFFF, TEXT, x0, barX0, middle - 2, y);
		Component food = Component.translatable("bloompets.screen.hunger");
		int foodX = middle + 6;
		bar(g, food, "", pet.hunger() / 20.0F, pet.hunger() <= 6.0F ? HEALTH : FOOD, FOOD_SHINE, TEXT, foodX, foodX + font.width(food) + 6, x1, y);

		if (mouseY >= y - 1 && mouseY < y + 9 && mouseX >= x0 && mouseX < x1) {
			int hearts = Math.max(1, Math.min(10, (int) Math.ceil(pet.mood() / 10.0F - 1.0E-6F)));
			String word = pet.loved() ? "loved" : sad ? "sad" : hearts >= 10 ? "overjoyed" : hearts >= 8 ? "happy" : hearts >= 6 ? "content" : "okay";
			List<Component> tip = new ArrayList<>();

			if (mouseX < middle) {
				tip.add(Component.translatable("bloompets.screen.mood.tip", Component.translatable("bloompets.screen.mood." + word), hearts)
						.withStyle(st -> st.withColor(color & 0xFFFFFF)));

				String who = pet.kind() == Pets.Kind.MODDED ? ".companion" : "";

				if (sad) {
					tip.add(Component.translatable("bloompets.screen.mood.sulking" + who).withStyle(ChatFormatting.GRAY));
				}

				if (pet.tired()) {
					tip.add(Component.translatable("bloompets.screen.mood.tired" + who).withStyle(ChatFormatting.GRAY));
				}

				tip.add(Component.translatable("bloompets.screen.mood.how" + who).withStyle(ChatFormatting.DARK_GRAY));
			} else {
				tip.add(Component.translatable("bloompets.screen.hunger.tip", (int) Math.ceil(pet.hunger())).withStyle(st -> st.withColor(FOOD & 0xFFFFFF)));
				tip.add(Component.translatable(pet.kind() == Pets.Kind.MODDED ? "bloompets.screen.hunger.how.companion" : "bloompets.screen.hunger.how")
						.withStyle(ChatFormatting.DARK_GRAY));
			}

			// Wrapped, so a long line doesn't run off the screen (FreshOrangeSlices' playtest).
			panelTip = tip.stream().flatMap(line -> font.split(line, 220).stream()).toList();
		}
	}

	/** A label, a bar filled to {@code fraction}, and the amount at the right. */
	private void bar(GuiGraphics g, Component label, String amount, float fraction, int fillColor, int shine, int amountColor, int x,
			int barX0, int right, int y) {
		g.drawString(font, label, x, y, MUTED);
		int barX1 = right - font.width(amount) - 6;
		int fill = barX0 + Math.round((barX1 - barX0) * Mth.clamp(fraction, 0.0F, 1.0F));
		g.fill(barX0, y + 1, barX1, y + 7, BAR_BACK);

		if (fill > barX0) {
			g.fill(barX0, y + 1, fill, y + 7, fillColor);
			g.fill(barX0, y + 1, fill, y + 2, shine);
		}

		g.drawString(font, amount, right - font.width(amount), y, amountColor);
	}

	private void labelled(GuiGraphics g, String labelKey, String value, int x, int y, int right) {
		Component label = Component.translatable(labelKey);
		g.drawString(font, label, x, y, ACCENT);
		int valueX = x + font.width(label) + 5;
		g.drawString(font, font.plainSubstrByWidth(value, right - valueX), valueX, y, TEXT);
	}

	/** The pet on a piece of the hub's art, turning to follow the mouse; a soft shadow at the top and bottom. */
	private void canvas(GuiGraphics g, PetsNetwork.Entry pet, int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
		int w = x1 - x0;
		int h = y1 - y0;
		frame(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, PANEL_EDGE);
		corners(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1);
		g.enableScissor(x0, y0, x1, y1);
		g.fill(x0, y0, x1, y1, 0xFF120F0C);
		boolean fighter = pet != null && (pet.kind() == Pets.Kind.MODDED || "combat".equals(pet.category()));
		RenderSystem.enableBlend();
		g.blit(fighter ? BACKDROP_COMBAT : BACKDROP_MEADOW, x0, y0, Math.max(0, (BACKDROP_W - w) / 2), Math.max(0, (BACKDROP_H - h) / 2),
				Math.min(w, BACKDROP_W), Math.min(h, BACKDROP_H), BACKDROP_W, BACKDROP_H);
		g.fillGradient(x0, y0, x1, y0 + 14, 0x90000000, 0x00000000);
		g.fillGradient(x0, y1 - 18, x1, y1, 0x00000000, 0xA0000000);

		for (int i = 0; i < 4; i++) {
			int alpha = (0x60 - i * 0x18) << 24;
			g.fill(x0 + i * 2, y0, x0 + i * 2 + 2, y1, alpha);
			g.fill(x1 - i * 2 - 2, y0, x1 - i * 2, y1, alpha);
		}

		LivingEntity model = pet == null || confirmText != null ? null : model(pet);

		if (model != null) {
			float size = Math.max(model.getBbHeight(), model.getBbWidth() * 0.9F);
			int scale = (int) Mth.clamp(64.0F / Math.max(size, 0.3F), 10, 52);
			int cx = x0 + w / 2;
			int cy = y1 - 10;

			try {
				InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, cy, scale, cx - mouseX, cy - scale * model.getEyeHeight() - mouseY, model);
			} catch (RuntimeException e) {
				// A mod's mob that won't draw here; the rest of the screen still does.
			}
		}

		g.disableScissor();
	}

	private Component bold(String text, int width) {
		String s = text;

		while (!s.isEmpty() && font.width(Component.literal(s).withStyle(ChatFormatting.BOLD)) > width) {
			s = s.substring(0, s.length() - 1);
		}

		return Component.literal(s).withStyle(ChatFormatting.BOLD);
	}

	/** Text half as large again, as the hub's headings; its top-left corner at x, y. */
	private void big(GuiGraphics g, Component text, int x, int y, int color) {
		g.pose().pushPose();
		g.pose().translate(x, y, 0.0F);
		g.pose().scale(BIG, BIG, 1.0F);
		g.drawString(font, text, 0, 0, color);
		g.pose().popPose();
	}

	private String state(PetsNetwork.Entry pet) {
		Minecraft minecraft = Minecraft.getInstance();
		int rest = pet.rest() - (int) ((System.currentTimeMillis() - receivedAt) / 50);

		if (rest > 0) {
			return Component.translatable("bloompets.screen.state.fainted", time(rest)).getString();
		}

		String state = pet.kind() == Pets.Kind.MODDED ? Component.translatable("bloompets.screen.state.companion").getString()
				: pet.party() ? Component.translatable("bloompets.screen.state.party").getString()
				: pet.posted() ? Component.translatable("bloompets.screen.state.post").getString()
				: Component.translatable("bloompets.screen.state.home").getString();

		if (pet.mood() >= 0 && pet.mood() < 30.0F) {
			state += " · " + Component.translatable("bloompets.screen.state.sulking").getString();
		}

		if (pet.owed() > 0) {
			state += " · " + Component.translatable("bloompets.screen.state.owed").getString();
		}

		if (!pet.loaded()) {
			return state + " · " + Component.translatable("bloompets.screen.state.away").getString();
		}

		if (minecraft.player != null && minecraft.player.level().dimension().location().toString().equals(pet.dimension())) {
			return state + " · " + Component.translatable("bloompets.screen.state.distance",
					(int) Math.sqrt(minecraft.player.blockPosition().distSqr(pet.pos()))).getString();
		}

		return state;
	}

	private String where(PetsNetwork.Entry pet) {
		return dimensionName(pet.dimension()) + "  " + pet.pos().getX() + ", " + pet.pos().getY() + ", " + pet.pos().getZ();
	}

	private static String dimensionName(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dimension.substring(dimension.indexOf(':') + 1);
		};
	}

	private static String typeName(PetsNetwork.Entry pet) {
		return EntityType.byString(pet.type()).map(t -> t.getDescription().getString()).orElse(pet.type());
	}

	/** The pet as it looks: the live one when it's nearby, else one made from its look. */
	private LivingEntity model(PetsNetwork.Entry pet) {
		if (models.containsKey(pet.pet())) {
			return models.get(pet.pet());
		}

		Minecraft minecraft = Minecraft.getInstance();
		LivingEntity found = null;

		if (minecraft.level != null) {
			for (Entity entity : minecraft.level.entitiesForRendering()) {
				if (entity.getUUID().equals(pet.pet()) && entity instanceof LivingEntity living) {
					found = living;
					break;
				}
			}

			if (found == null) {
				EntityType<?> type = EntityType.byString(pet.type()).orElse(null);
				Entity made = type == null ? null : type.create(minecraft.level);

				if (made instanceof LivingEntity living) {
					try {
						CompoundTag look = pet.look().copy();
						living.load(look);
					} catch (RuntimeException e) {
						// Its default look, then.
					}

					found = living;
				}
			}
		}

		models.put(pet.pet(), found);
		return found;
	}

	private boolean overSlot(int x, int y, int mouseX, int mouseY) {
		return mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT;
	}

	// One and the Same: the Bloom view.

	private void confirm(Component text, Runnable yes) {
		choose(text, Component.translatable("bloompets.screen.yes"), yes, Component.translatable("bloompets.screen.no"), null);
	}

	/** A question with two answers of its own (a companion's Spirit: Keep or Reroll). */
	private void choose(Component text, Component yesLabel, Runnable yes, Component noLabel, Runnable no) {
		confirmText = text;
		confirmYes = yes;
		confirmNo = no;
		yesButton.setMessage(yesLabel);
		noButton.setMessage(noLabel);
		yesButton.visible = noButton.visible = true;
	}

	private void closeConfirm() {
		confirmText = null;
		confirmYes = null;
		confirmNo = null;

		if (yesButton != null) {
			yesButton.visible = noButton.visible = false;
		}
	}

	/** A companion whose Spirit isn't settled yet: Keep or Reroll, in the box over it (once a visit). */
	private void askSpirit(PetsNetwork.Entry pet) {
		if (!pet.spiritPending() || confirmText != null || bloomView || !askedSpirit.add(pet.pet())) {
			return;
		}

		Component spirit = Component.translatable("bloompets.spirit.name." + pet.skills());
		choose(Component.translatable("bloompets.screen.spirit.ask", pet.name(), spirit),
				Component.translatable("bloompets.screen.spirit.keep"), () -> PetsClient.act(PetsNetwork.Action.SPIRIT, pet.pet(), "keep"),
				Component.translatable("bloompets.screen.spirit.reroll"), () -> PetsClient.act(PetsNetwork.Action.SPIRIT, pet.pet(), "reroll"));
	}

	/** The question, in a box over the pet (its model isn't drawn meanwhile), with yes and no under it. */
	private void drawConfirm(GuiGraphics g) {
		int x0 = mainLeft() + SLOT + 6;
		int x1 = mainRight() - SLOT - 6;
		int y0 = canvasTop();
		int y1 = canvasBottom();
		g.fill(x0, y0, x1, y1, 0xF2141210);
		frame(g, x0, y0, x1, y1, ACCENT);
		corners(g, x0, y0, x1, y1);
		List<FormattedCharSequence> lines = font.split(confirmText, x1 - x0 - 12);
		int y = y0 + 10;

		for (FormattedCharSequence line : lines) {
			g.drawString(font, line, (x0 + x1 - font.width(line)) / 2, y, TEXT);
			y += 10;
		}
	}

	private static ResourceLocation effectIcon(PetTrees.Effect effect) {
		return new ResourceLocation("bloomlevelup", "textures/icon/major/combat/" + effect.icon + ".png");
	}

	/**
	 * What a slot holds in the Bloom view: a tree to pick, one of its tree's effects, the pet's stats (the left column's
	 * last slot), the tree itself, the reset, or nothing.
	 */
	private Object bloomAt(PetsNetwork.Entry pet, int index) {
		if (!pet.treeable()) {
			return null;
		}

		if (index == SLOTS - 1) {
			return "stats";
		}

		PetTrees.Tree tree = PetTrees.Tree.byId(pet.tree());

		if (tree == null) {
			return index < PetTrees.Tree.values().length ? PetTrees.Tree.values()[index] : null;
		}

		if (index < tree.effects().size()) {
			return tree.effects().get(index);
		}

		return index == SLOTS ? "tree" : index == SLOTS * 2 - 1 ? "reset" : null;
	}

	private boolean abilityOff(PetsNetwork.Entry pet, PetTrees.Effect effect) {
		return java.util.Arrays.asList(pet.abilitiesOff().split(",")).contains(effect.id());
	}

	private void bloomSlot(GuiGraphics g, PetsNetwork.Entry pet, int index, int x, int y, boolean hover) {
		Object what = bloomAt(pet, index);
		g.fill(x, y, x + SLOT, y + SLOT, 0xFF0E0C0A);

		if (what == null) {
			frame(g, x, y, x + SLOT, y + SLOT, hover ? ACCENT : PANEL_EDGE);
			g.drawCenteredString(font, "?", x + SLOT / 2, y + 5, FAINT);
			return;
		}

		if (what instanceof PetTrees.Tree tree) {
			frame(g, x, y, x + SLOT, y + SLOT, hover ? GOLD : SKILL_EDGE);
			g.renderItem(new ItemStack(tree.icon), x + 1, y + 1);

			if (pet.treeWait() > 0) {
				dim(g, x, y, "");
			}

			return;
		}

		if (what instanceof PetTrees.Effect effect) {
			boolean open = pet.level() >= effect.level;
			frame(g, x, y, x + SLOT, y + SLOT, hover ? GOLD : open ? SKILL_EDGE : PANEL_EDGE);
			RenderSystem.enableBlend();

			// Locked: its icon greyed, so what's coming shows, and the level it opens at small in the corner.
			if (!open) {
				g.setColor(0.38F, 0.38F, 0.38F, 1.0F);
			}

			g.blit(effectIcon(effect), x + 1, y + 1, 16, 16, 0.0F, 0.0F, 64, 64, 64, 64);

			if (!open) {
				g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
				cornerNumber(g, x, y, Integer.toString(effect.level));
			} else if (effect.cooldown && abilityOff(pet, effect)) {
				dim(g, x, y, "");
				g.pose().pushPose();
				g.pose().translate(0.0F, 0.0F, 200.0F);
				g.drawCenteredString(font, "✕", x + SLOT / 2, y + 5, HEALTH);
				g.pose().popPose();
			}

			return;
		}

		if ("stats".equals(what)) {
			frame(g, x, y, x + SLOT, y + SLOT, hover ? GOLD : SKILL_EDGE);
			g.renderItem(new ItemStack(Items.BOOK), x + 1, y + 1);
			return;
		}

		PetTrees.Tree tree = PetTrees.Tree.byId(pet.tree());
		boolean reset = "reset".equals(what);
		frame(g, x, y, x + SLOT, y + SLOT, hover ? (reset ? HEALTH : GOLD) : reset ? PANEL_EDGE : SKILL_EDGE);
		g.renderItem(new ItemStack(reset ? Items.BARRIER : tree.icon), x + 1, y + 1);
	}

	/** A small number in a slot's bottom-right corner, over its icon, on a dark backing. */
	private void cornerNumber(GuiGraphics g, int x, int y, String text) {
		float scale = 0.75F;
		int width = (int) Math.ceil(font.width(text) * scale);
		int x1 = x + SLOT - 1;
		int y1 = y + SLOT - 1;
		g.pose().pushPose();
		g.pose().translate(0.0F, 0.0F, 200.0F);
		g.fill(x1 - width - 2, y1 - 7, x1, y1, 0xD0100E0C);
		g.pose().translate(x1 - width - 1, y1 - 6, 0.0F);
		g.pose().scale(scale, scale, 1.0F);
		g.drawString(font, text, 0, 0, MUTED, false);
		g.pose().popPose();
	}

	/** A slot's dark veil (over its icon), with a word on it. */
	private void dim(GuiGraphics g, int x, int y, String text) {
		g.pose().pushPose();
		g.pose().translate(0.0F, 0.0F, 200.0F);
		g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xC8100E0C);

		if (!text.isEmpty()) {
			g.drawString(font, text, x + (SLOT - font.width(text)) / 2 + 1, y + 5, MUTED);
		}

		g.pose().popPose();
	}

	private void bloomTip(GuiGraphics g, PetsNetwork.Entry pet, int index, int mouseX, int mouseY) {
		Object what = bloomAt(pet, index);
		List<FormattedCharSequence> lines = new ArrayList<>();
		boolean companion = pet.kind() == Pets.Kind.MODDED;

		if (what == null) {
			lines.addAll(font.split(Component.translatable(pet.treeable() ? "bloompets.screen.slot.bloom" : "bloompets.screen.tree.none"), 200));
		} else if (what instanceof PetTrees.Tree tree) {
			lines.add(title(Component.translatable("bloompets.screen.tree.title", Component.translatable("bloompets.tree." + tree.id()))));

			for (PetTrees.Effect effect : tree.effects()) {
				lines.add(Component.translatable("bloompets.screen.tree.line", effect.level, Component.translatable("bloompets.effect." + effect.id()))
						.withStyle(st -> st.withColor(SKILL_TEXT & 0xFFFFFF)).getVisualOrderText());
			}

			lines.addAll(font.split(pet.treeWait() > 0 ? Component.translatable("bloompets.screen.tree.wait", hours(pet.treeWait()))
					.withStyle(ChatFormatting.RED) : Component.translatable(companion ? "bloompets.screen.tree.pick.companion"
					: "bloompets.screen.tree.pick").withStyle(ChatFormatting.DARK_GRAY), 200));
		} else if (what instanceof PetTrees.Effect effect) {
			boolean open = pet.level() >= effect.level;
			lines.add(title(Component.translatable("bloompets.effect." + effect.id())));
			lines.add(Component.translatable(open ? "bloompets.screen.skill.open" : "bloompets.screen.skill.locked", effect.level)
					.withStyle(st -> st.withColor((open ? XP_FILL : MUTED) & 0xFFFFFF)).getVisualOrderText());
			lines.addAll(font.split(Component.translatable("bloompets.effect." + effect.id() + ".desc")
					.withStyle(st -> st.withColor(SKILL_TEXT & 0xFFFFFF)), 200));

			if (effect.cooldown && open) {
				lines.addAll(font.split(Component.translatable(abilityOff(pet, effect) ? "bloompets.screen.ability.off" : "bloompets.screen.ability.on")
						.withStyle(ChatFormatting.DARK_GRAY), 200));
			}

			if (!companion && !pet.party() && !pet.posted()) {
				lines.addAll(font.split(Component.translatable("bloompets.screen.tree.resting").withStyle(ChatFormatting.GRAY), 200));
			}
		} else if ("stats".equals(what)) {
			lines.addAll(statsTip(pet));
		} else if ("tree".equals(what)) {
			lines.add(title(Component.translatable("bloompets.screen.tree.follows",
					Component.translatable("bloompets.tree." + pet.tree()))));
			lines.addAll(font.split(Component.translatable("bloompets.screen.tree.how").withStyle(ChatFormatting.GRAY), 200));
		} else {
			lines.add(title(Component.translatable("bloompets.screen.tree.reset")));
			lines.addAll(font.split(Component.translatable(companion ? "bloompets.screen.tree.reset.companion" : "bloompets.screen.tree.reset.pet")
					.withStyle(ChatFormatting.RED), 200));
		}

		g.renderTooltip(font, lines, mouseX, mouseY);
	}

	/**
	 * The pet's stats, in one place: its damage per hit (an Amigo's own attacks, with what its levels add), the extras
	 * every hit gets (Sharpened Claws and the like at 75, Brawler on the Unarmed tree at 10), its health and armor.
	 */
	private List<FormattedCharSequence> statsTip(PetsNetwork.Entry pet) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		lines.add(title(Component.translatable("bloompets.screen.stats.title", pet.name())));

		if (!pet.loaded() || pet.armor() < 0) {
			lines.addAll(font.split(Component.translatable("bloompets.screen.stats.away").withStyle(ChatFormatting.GRAY), 200));
			return lines;
		}

		int color = SKILL_TEXT & 0xFFFFFF;

		if (pet.amigoPower() >= 0) {
			lines.add(Component.translatable("bloompets.screen.stats.amigo", String.format(Locale.ROOT, "%.1f", pet.amigoPower()))
					.withStyle(st -> st.withColor(color)).getVisualOrderText());
		} else if (pet.damage() >= 0) {
			lines.add(Component.translatable("bloompets.screen.stats.damage", String.format(Locale.ROOT, "%.1f", pet.damage()))
					.withStyle(st -> st.withColor(color)).getVisualOrderText());
		}

		PetSkills.Kind kind = PetSkills.Kind.byId(pet.skills());

		if (kind != null && pet.level() >= PetSkills.Slot.CLAWS.level) {
			lines.add(Component.translatable("bloompets.screen.stats.extra", "+2", Component.translatable(PetSkills.nameKey(kind, PetSkills.Slot.CLAWS)))
					.withStyle(ChatFormatting.GRAY).getVisualOrderText());
		}

		if ("unarmed".equals(pet.tree()) && pet.level() >= PetTrees.Effect.BRAWLER.level) {
			lines.add(Component.translatable("bloompets.screen.stats.extra", "+3", Component.translatable("bloompets.effect.brawler"))
					.withStyle(ChatFormatting.GRAY).getVisualOrderText());
		}

		lines.add(Component.translatable("bloompets.screen.stats.body", String.format(Locale.ROOT, "%.0f", pet.maxHealth()),
				String.format(Locale.ROOT, "%.0f", pet.armor())).withStyle(st -> st.withColor(color)).getVisualOrderText());

		if (pet.kind() == Pets.Kind.BLOOM && !pet.party() && !pet.posted()) {
			lines.addAll(font.split(Component.translatable("bloompets.screen.stats.resting").withStyle(ChatFormatting.DARK_GRAY), 200));
		}

		return lines;
	}

	private FormattedCharSequence title(MutableComponent text) {
		return text.withStyle(st -> st.withColor(GOLD & 0xFFFFFF).withBold(true)).getVisualOrderText();
	}

	private void bloomClick(PetsNetwork.Entry pet, int index) {
		Object what = bloomAt(pet, index);
		boolean companion = pet.kind() == Pets.Kind.MODDED;

		if (what instanceof PetTrees.Tree tree && pet.treeWait() <= 0) {
			confirm(Component.translatable("bloompets.screen.tree.confirm", pet.name(), Component.translatable("bloompets.tree." + tree.id())),
					() -> PetsClient.act(PetsNetwork.Action.TREE, pet.pet(), tree.id()));
		} else if (what instanceof PetTrees.Effect effect && effect.cooldown && pet.level() >= effect.level) {
			PetsClient.act(PetsNetwork.Action.ABILITY, pet.pet(), effect.id());
		} else if ("reset".equals(what)) {
			confirm(Component.translatable(companion ? "bloompets.screen.tree.confirm.reset.companion" : "bloompets.screen.tree.confirm.reset",
					pet.name()), () -> PetsClient.act(PetsNetwork.Action.TREE_RESET, pet.pet()));
		}
	}

	/** A non-combat Bloom pet's first slot holds its bonus for you (it has no skill tree). */
	private boolean bonusSlot(PetsNetwork.Entry pet, int index) {
		return index == 0 && !bloomView && pet.kind() == Pets.Kind.BLOOM && PetSkills.Kind.byId(pet.skills()) == null;
	}

	/** What the pet's bonus does at its level, and when it gives it. */
	private List<FormattedCharSequence> bonusTip(PetsNetwork.Entry pet) {
		String kind = pet.type().substring(pet.type().indexOf(':') + 1);
		double p = PetBonuses.power(pet.level());
		Object[] args = switch (kind) {
			case "wolf", "horse" -> new Object[] {number(8.0 * p)};
			case "iron_golem" -> new Object[] {number(3.0 * p)};
			case "goat" -> new Object[] {number(Math.min(70.0, 40.0 * p))};
			case "donkey", "llama" -> new Object[] {number(2.0 * p)};
			case "mule" -> new Object[] {number(Math.min(60.0, 30.0 * p))};
			case "fox" -> new Object[] {number(Math.min(50.0, 25.0 * p))};
			case "cow" -> new Object[] {number(120.0 / p)};
			case "bee" -> new Object[] {number(p * 100.0 - 100.0)};
			default -> new Object[0];
		};
		List<FormattedCharSequence> tip = new ArrayList<>();
		tip.add(Component.translatable("bloompets.screen.bonus").withStyle(st -> st.withColor(GOLD & 0xFFFFFF).withBold(true)).getVisualOrderText());
		tip.addAll(font.split(Component.translatable("bloompets.bonus." + kind, args).withStyle(st -> st.withColor(SKILL_TEXT & 0xFFFFFF)), 200));
		tip.addAll(font.split(Component.translatable("combat".equals(pet.category()) ? "bloompets.screen.bonus.party" : "bloompets.screen.bonus.near")
				.withStyle(ChatFormatting.DARK_GRAY), 200));
		return tip;
	}

	/** The skill in slot {@code index} (0 to 9: the left column, then the right), or null for an empty slot. */
	private PetSkills.Slot skillAt(PetsNetwork.Entry pet, int index) {
		// A companion's slots hold its Spirit's skills (none until it has one).
		if (bloomView || pet.kind() != Pets.Kind.BLOOM && PetSkills.Kind.byId(pet.skills()) == null) {
			return null;
		}

		PetSkills.Slot slot = PetSkills.Slot.values()[index];
		return PetSkills.Kind.byId(pet.skills()) != null || slot == PetSkills.Slot.MAX ? slot : null;
	}

	private void slotTooltip(GuiGraphics g, PetsNetwork.Entry pet, int mouseX, int mouseY) {
		for (int i = 0; i < SLOTS * 2; i++) {
			int x = i < SLOTS ? mainLeft() : mainRight() - SLOT;

			if (!overSlot(x, slotY(i % SLOTS), mouseX, mouseY)) {
				continue;
			}

			if (bloomView) {
				bloomTip(g, pet, i, mouseX, mouseY);
				return;
			}

			PetSkills.Slot slot = skillAt(pet, i);

			if (bonusSlot(pet, i)) {
				g.renderTooltip(font, bonusTip(pet), mouseX, mouseY);
				return;
			}

			if (slot == null) {
				String key = bloomView ? "bloompets.screen.slot.bloom" : pet.kind() == Pets.Kind.MODDED ? "bloompets.screen.slot.modded"
						: "bloompets.screen.slot.passive";
				g.renderTooltip(font, font.split(Component.translatable(key), 200), mouseX, mouseY);
				return;
			}

			PetSkills.Kind kind = PetSkills.Kind.byId(pet.skills());
			int opens = opensAt(pet, slot);
			boolean open = pet.level() >= opens;
			List<FormattedCharSequence> lines = new ArrayList<>();
			lines.add(Component.translatable(PetSkills.nameKey(kind, slot)).withStyle(st -> st.withColor(GOLD & 0xFFFFFF).withBold(true))
					.getVisualOrderText());
			lines.add(Component.translatable(open ? "bloompets.screen.skill.open" : "bloompets.screen.skill.locked", opens)
					.withStyle(st -> st.withColor((open ? XP_FILL : MUTED) & 0xFFFFFF)).getVisualOrderText());
			lines.addAll(font.split(describe(kind, slot, open ? pet.level() : opens).withStyle(st -> st.withColor(SKILL_TEXT & 0xFFFFFF)), 200));
			g.renderTooltip(font, lines, mouseX, mouseY);
			return;
		}
	}

	/** The level a skill opens at: its own, but the top one (100) is an Amigo's 99. */
	private static int opensAt(PetsNetwork.Entry pet, PetSkills.Slot slot) {
		return slot == PetSkills.Slot.MAX ? pet.topLevel() : slot.level;
	}

	/** What the skill does, with its numbers at this level (a kind's own wording first, then the shared one). */
	private static MutableComponent describe(PetSkills.Kind kind, PetSkills.Slot slot, int level) {
		String own = kind == null ? null : "bloompets.skill." + kind.id() + "." + slot.id() + ".desc";
		String key = slot == PetSkills.Slot.MAX ? "bloompets.skill.max.desc" : own != null && I18n.exists(own) ? own : "bloompets.skill." + slot.id() + ".desc";
		Object[] args = switch (slot) {
			case GORE -> new Object[] {number(PetSkills.goreChance(level))};
			case FIRST -> kind == PetSkills.Kind.IRON_GOLEM ? new Object[] {number(PetSkills.colossusHealth(level))} : new Object[0];
			case SPECIAL -> kind == PetSkills.Kind.POLAR_BEAR ? new Object[] {PetSkills.crushHigh(level) ? "IV" : "II", PetSkills.crushHigh(level) ? "II" : "I"}
					: kind == PetSkills.Kind.PANDA ? new Object[] {number(PetSkills.soothChance(level)), PetSkills.soothHeal(level)} : new Object[0];
			default -> new Object[0];
		};
		return Component.translatable(key, args);
	}

	private static String number(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
	}

	/**
	 * A skill slot, as the hub's Major icons: its icon on near black with a gold edge once it's open; dimmed with the
	 * level it opens at until then; a "?" in an empty one (non-combat pets' and companions' slots, and the Bloom view).
	 */
	private void slot(GuiGraphics g, PetsNetwork.Entry pet, int index, int x, int y, int mouseX, int mouseY) {
		boolean hover = overSlot(x, y, mouseX, mouseY);
		if (bloomView) {
			bloomSlot(g, pet, index, x, y, hover);
			return;
		}

		PetSkills.Slot slot = skillAt(pet, index);
		g.fill(x, y, x + SLOT, y + SLOT, 0xFF0E0C0A);

		if (bonusSlot(pet, index)) {
			frame(g, x, y, x + SLOT, y + SLOT, hover ? GOLD : SKILL_EDGE);
			EntityType<?> type = EntityType.byString(pet.type()).orElse(null);
			SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
			g.renderItem(new ItemStack(egg == null ? Items.LEAD : egg), x + 1, y + 1);
			return;
		}

		if (slot == null) {
			frame(g, x, y, x + SLOT, y + SLOT, hover ? ACCENT : PANEL_EDGE);
			g.drawCenteredString(font, "?", x + SLOT / 2, y + 5, FAINT);
			return;
		}

		boolean open = pet.level() >= opensAt(pet, slot);
		frame(g, x, y, x + SLOT, y + SLOT, hover ? GOLD : open ? SKILL_EDGE : PANEL_EDGE);
		g.renderItem(new ItemStack(PetSkills.icon(PetSkills.Kind.byId(pet.skills()), slot)), x + 1, y + 1);

		if (!open) {
			// Over the item (drawn at depth 150).
			g.pose().pushPose();
			g.pose().translate(0.0F, 0.0F, 200.0F);
			g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xC8100E0C);
			String level = Integer.toString(opensAt(pet, slot));
			g.drawString(font, level, x + (SLOT - font.width(level)) / 2 + 1, y + 5, MUTED);
			g.pose().popPose();
		}
	}

	/** A one-pixel outline. */
	static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		g.fill(x0, y0, x1, y0 + 1, color);
		g.fill(x0, y1 - 1, x1, y1, color);
		g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
		g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
	}

	/** The hub's orange corner marks, top-left and bottom-right. */
	private static void corners(GuiGraphics g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x0 + 3, y0 + 1, ACCENT);
		g.fill(x0, y0, x0 + 1, y0 + 3, ACCENT);
		g.fill(x1 - 3, y1 - 1, x1, y1, ACCENT);
		g.fill(x1 - 1, y1 - 3, x1, y1, ACCENT);
	}

	/** A square nine-slice texture stretched to width x height: corners as they are, edges and middle stretched (Bloom's BloomGraphics). */
	private static void nineSlice(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height, int size, int border) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		int inner = size - 2 * border;
		int midW = Math.max(0, width - 2 * border);
		int midH = Math.max(0, height - 2 * border);
		int right = x + width - border;
		int bottom = y + height - border;
		// Corners.
		g.blit(texture, x, y, border, border, 0, 0, border, border, size, size);
		g.blit(texture, right, y, border, border, size - border, 0, border, border, size, size);
		g.blit(texture, x, bottom, border, border, 0, size - border, border, border, size, size);
		g.blit(texture, right, bottom, border, border, size - border, size - border, border, border, size, size);
		// Edges.
		g.blit(texture, x + border, y, midW, border, border, 0, inner, border, size, size);
		g.blit(texture, x + border, bottom, midW, border, border, size - border, inner, border, size, size);
		g.blit(texture, x, y + border, border, midH, 0, border, border, inner, size, size);
		g.blit(texture, right, y + border, border, midH, size - border, border, border, inner, size, size);
		// Middle: the frame's own middle is see-through, so the panel shows.
		g.blit(texture, x + border, y + border, midW, midH, border, border, inner, inner, size, size);
	}
}
