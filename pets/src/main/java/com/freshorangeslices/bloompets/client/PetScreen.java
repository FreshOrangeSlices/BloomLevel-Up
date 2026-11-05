package com.freshorangeslices.bloompets.client;

import com.freshorangeslices.bloompets.Pets;
import com.freshorangeslices.bloompets.network.PetsNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * The pet screen (FreshOrangeSlices' sketch, docs/design/art/pets-screen-sketch.png): the player's pets on the left,
 * Bloom pets first (orange) then companions (dark red), the party's marked pink; the chosen pet in the middle, turning
 * to follow the mouse, with its skill slots on either side (filled from Phase 2: pet levels) and a switch between its
 * own traits and its Bloom weapon tree (One and the Same); its health and where it is under it; Call, the party and
 * its storage at the bottom.
 */
public final class PetScreen extends Screen {
	private static final int W = 320;
	private static final int H = 196;
	private static final int LIST_W = 98;
	private static final int ROW_H = 15;
	private static final int LIST_TOP = 40;
	private static final int SLOT = 22;
	private static final int TEXT = 0xFF303030;
	private static final int SOFT = 0xFF606060;
	private static final int BLOOM = 0xFFB85C00;
	private static final int MODDED = 0xFF8B1A1A;
	private static final int PARTY = 0xFFE0569A;

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

	public PetScreen(PetsNetwork.PetList list) {
		super(Component.translatable("bloompets.screen.title"));
		accept(list);
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

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = (height - H) / 2;
		filterButton = addRenderableWidget(Button.builder(filterLabel(), b -> {
			filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
			scroll = 0;
			b.setMessage(filterLabel());
		}).bounds(left + 6, top + 18, LIST_W - 12, 16).build());
		viewButton = addRenderableWidget(Button.builder(Component.literal("⇄"), b -> {
			bloomView = !bloomView;
			updateButtons();
		}).bounds(left + W - 24, top + 5, 18, 18).build());
		int mainLeft = left + LIST_W + 4;
		callButton = addRenderableWidget(Button.builder(Component.translatable("bloompets.screen.call"), b -> act(PetsNetwork.Action.CALL))
				.bounds(mainLeft + 4, top + H - 24, 58, 18).build());
		partyButton = addRenderableWidget(Button.builder(Component.empty(), b -> act(PetsNetwork.Action.PARTY))
				.bounds(mainLeft + 66, top + H - 24, 84, 18).build());
		storageButton = addRenderableWidget(Button.builder(Component.translatable("bloompets.screen.storage"), b -> act(PetsNetwork.Action.STORAGE))
				.bounds(left + W - 64, top + H - 24, 58, 18).build());
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
		callButton.visible = partyButton.visible = storageButton.visible = any;
		viewButton.visible = any;
		viewButton.setTooltip(Tooltip.create(Component.translatable(bloomView ? "bloompets.screen.view.bloom" : "bloompets.screen.view.pet")));

		if (!any) {
			return;
		}

		callButton.setTooltip(Tooltip.create(Component.translatable("bloompets.screen.call.tip", pet.name())));

		if (pet.kind() == Pets.Kind.MODDED) {
			partyButton.active = false;
			partyButton.setMessage(Component.translatable("bloompets.screen.always"));
			partyButton.setTooltip(Tooltip.create(Component.translatable("bloompets.party.modded", pet.name())));
		} else {
			int wait = partyWait();
			partyButton.active = wait <= 0;
			partyButton.setMessage(Component.translatable(pet.party() ? "bloompets.screen.leave" : "bloompets.screen.join"));
			partyButton.setTooltip(Tooltip.create(wait > 0 ? Component.translatable("bloompets.party.wait", time(wait))
					: Component.translatable("bloompets.screen.party.tip")));
		}

		boolean storage = pet.kind() == Pets.Kind.BLOOM && pet.slots() > 0;
		storageButton.active = storage;
		storageButton.setTooltip(Tooltip.create(storage ? Component.translatable("bloompets.screen.storage.tip", pet.slots())
				: Component.translatable(pet.kind() == Pets.Kind.MODDED ? "bloompets.screen.storage.modded" : "bloompets.storage.none", pet.name())));
	}

	private static String time(int ticks) {
		int seconds = (ticks + 19) / 20;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	@Override
	public void tick() {
		// The party cooldown ticks down here; the button comes back on when it's over.
		if (partyButton != null && current() != null && current().kind() == Pets.Kind.BLOOM && partyButton.active == (partyWait() > 0)) {
			updateButtons();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (PetsClient.OPEN.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		List<PetsNetwork.Entry> shown = shown();

		for (int i = 0; i < visibleRows() && i + scroll < shown.size(); i++) {
			int y = top + LIST_TOP + i * ROW_H;

			if (mouseX >= left + 5 && mouseX < left + LIST_W - 5 && mouseY >= y && mouseY < y + ROW_H) {
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

	private int visibleRows() {
		return (H - LIST_TOP - 22) / ROW_H;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderBackground(g);
		panel(g, left, top, W, H);
		g.drawString(font, title, left + 8, top + 7, TEXT, false);
		renderList(g, mouseX, mouseY);
		PetsNetwork.Entry pet = current();

		if (pet == null) {
			int cx = left + LIST_W + (W - LIST_W) / 2;
			g.drawCenteredString(font, Component.translatable("bloompets.screen.empty"), cx, top + 70, 0xFFFFFFFF);
			drawWrapped(g, Component.translatable("bloompets.command.none"), left + LIST_W + 14, top + 90, W - LIST_W - 28, SOFT);
		} else {
			renderPet(g, pet, mouseX, mouseY);
		}

		super.render(g, mouseX, mouseY, partialTick);

		if (message != null && System.currentTimeMillis() - messageAt < 5000) {
			g.drawCenteredString(font, message, width / 2, top + H + 6, 0xFFFFFFFF);
		}

		if (pet != null) {
			slotTooltip(g, pet, mouseX, mouseY);
		}
	}

	private void renderList(GuiGraphics g, int mouseX, int mouseY) {
		int listBottom = top + LIST_TOP + visibleRows() * ROW_H;
		inset(g, left + 4, top + LIST_TOP - 2, LIST_W - 8, listBottom - (top + LIST_TOP) + 4, 0xFFE4E4E4);
		List<PetsNetwork.Entry> shown = shown();

		for (int i = 0; i < visibleRows() && i + scroll < shown.size(); i++) {
			PetsNetwork.Entry e = shown.get(i + scroll);
			int y = top + LIST_TOP + i * ROW_H;
			boolean hover = mouseX >= left + 5 && mouseX < left + LIST_W - 5 && mouseY >= y && mouseY < y + ROW_H;

			if (e.pet().equals(selected)) {
				g.fill(left + 5, y, left + LIST_W - 5, y + ROW_H, 0xFFFFFFFF);
				g.fill(left + 5, y, left + 7, y + ROW_H, e.kind() == Pets.Kind.MODDED ? MODDED : BLOOM);
			} else if (hover) {
				g.fill(left + 5, y, left + LIST_W - 5, y + ROW_H, 0xFFF2F2F2);
			}

			int color = e.kind() == Pets.Kind.MODDED ? MODDED : BLOOM;
			String name = font.plainSubstrByWidth(e.name(), LIST_W - 30);
			g.drawString(font, name, left + 10, y + 4, color, false);

			if (e.party() || e.kind() == Pets.Kind.MODDED) {
				// Pink: in the party (companions always are).
				g.fill(left + LIST_W - 14, y + 5, left + LIST_W - 9, y + 10, PARTY);
			}

			if (!e.loaded()) {
				g.drawString(font, "·", left + LIST_W - 20, y + 3, SOFT, false);
			}
		}

		if (shown.isEmpty()) {
			g.drawString(font, Component.translatable("bloompets.screen.none"), left + 10, top + LIST_TOP + 4, SOFT, false);
		}

		long party = list.pets().stream().filter(PetsNetwork.Entry::party).count();
		String follows = list.pets().stream().filter(PetsNetwork.Entry::party).map(PetsNetwork.Entry::name).findFirst().orElse("-");
		g.drawString(font, font.plainSubstrByWidth(Component.translatable("bloompets.screen.follows", follows).getString(), LIST_W - 10),
				left + 6, top + H - 14, party > 0 ? PARTY : SOFT, false);
	}

	private void renderPet(GuiGraphics g, PetsNetwork.Entry pet, int mouseX, int mouseY) {
		int mainLeft = left + LIST_W + 4;
		g.drawString(font, font.plainSubstrByWidth(pet.name(), W - LIST_W - 70), mainLeft + 4, top + 8, TEXT, false);
		String kind = typeName(pet) + " · " + Component.translatable("bloompets.category." + pet.category()).getString();
		g.drawString(font, font.plainSubstrByWidth(kind, W - LIST_W - 40), mainLeft + 4, top + 20, SOFT, false);
		g.drawString(font, Component.translatable(bloomView ? "bloompets.screen.view.bloom.short" : "bloompets.screen.view.pet.short"),
				left + W - 24 - font.width(Component.translatable(bloomView ? "bloompets.screen.view.bloom.short" : "bloompets.screen.view.pet.short")) - 4,
				top + 10, SOFT, false);

		// The skill slots, four a side (filled in Phase 2).
		for (int i = 0; i < 4; i++) {
			slot(g, mainLeft + 4, top + 34 + i * (SLOT + 2));
			slot(g, left + W - SLOT - 6, top + 34 + i * (SLOT + 2));
		}

		// The model, turning to follow the mouse.
		int boxX = mainLeft + SLOT + 10;
		int boxY = top + 34;
		int boxW = (left + W - SLOT - 10) - boxX;
		int boxH = 4 * (SLOT + 2) - 2;
		g.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFF373737);
		g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF101010);
		LivingEntity model = model(pet);

		if (model != null) {
			float size = Math.max(model.getBbHeight(), model.getBbWidth() * 0.9F);
			int scale = (int) Mth.clamp(64.0F / Math.max(size, 0.3F), 10, 52);
			int cx = boxX + boxW / 2;
			int cy = boxY + boxH - 8;
			g.enableScissor(boxX, boxY, boxX + boxW, boxY + boxH);

			try {
				InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, cy, scale, cx - mouseX, cy - scale * model.getEyeHeight() - mouseY, model);
			} catch (RuntimeException e) {
				// A mod's mob that won't draw here; the rest of the screen still does.
			}

			g.disableScissor();
		}

		// Health, then where it is.
		int infoY = boxY + boxH + 6;
		g.drawString(font, Component.translatable("bloompets.screen.health"), boxX, infoY, SOFT, false);
		int barX = boxX + 40;
		int barW = boxW - 80;
		float fraction = pet.maxHealth() > 0 ? Mth.clamp(pet.health() / pet.maxHealth(), 0.0F, 1.0F) : 0.0F;
		g.fill(barX, infoY, barX + barW, infoY + 7, 0xFF3A1010);
		g.fill(barX, infoY, barX + (int) (barW * fraction), infoY + 7, 0xFFD23C3C);
		g.drawString(font, String.format(Locale.ROOT, "%.0f/%.0f", pet.health(), pet.maxHealth()), barX + barW + 4, infoY, SOFT, false);
		g.drawString(font, font.plainSubstrByWidth(status(pet), W - LIST_W - 16), mainLeft + 4, infoY + 12, SOFT, false);
	}

	private String status(PetsNetwork.Entry pet) {
		Minecraft minecraft = Minecraft.getInstance();
		String where = dimensionName(pet.dimension()) + " " + pet.pos().getX() + " " + pet.pos().getY() + " " + pet.pos().getZ();

		if (minecraft.player != null && minecraft.player.level().dimension().location().toString().equals(pet.dimension())) {
			where += String.format(Locale.ROOT, " (%d m)", (int) Math.sqrt(minecraft.player.blockPosition().distSqr(pet.pos())));
		}

		String state = pet.kind() == Pets.Kind.MODDED ? Component.translatable("bloompets.screen.state.companion").getString()
				: pet.party() ? Component.translatable("bloompets.screen.state.party").getString()
				: Component.translatable("bloompets.screen.state.home").getString();
		return state + " · " + where + (pet.loaded() ? "" : " · " + Component.translatable("bloompets.screen.state.away").getString());
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

	private void slotTooltip(GuiGraphics g, PetsNetwork.Entry pet, int mouseX, int mouseY) {
		int mainLeft = left + LIST_W + 4;

		for (int i = 0; i < 4; i++) {
			for (int x : new int[] {mainLeft + 4, left + W - SLOT - 6}) {
				int y = top + 34 + i * (SLOT + 2);

				if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
					g.renderTooltip(font, Component.translatable(bloomView ? "bloompets.screen.slot.bloom" : "bloompets.screen.slot.pet"), mouseX, mouseY);
					return;
				}
			}
		}
	}

	private void slot(GuiGraphics g, int x, int y) {
		inset(g, x, y, SLOT, SLOT, 0xFF8B8B8B);
		g.drawCenteredString(font, "?", x + SLOT / 2, y + 7, 0xFFB0B0B0);
	}

	private void drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int color) {
		for (var line : font.split(text, width)) {
			g.drawString(font, line, x, y, color, false);
			y += 10;
		}
	}

	/** Minecraft's own panel: light grey, a white edge top-left and a dark one bottom-right, a black outline. */
	private static void panel(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, 0xFF000000);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
		g.fill(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF);
		g.fill(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF);
		g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
		g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, 0xFF555555);
		g.fill(x + LIST_W, y + 4, x + LIST_W + 1, y + h - 4, 0xFF8B8B8B);
	}

	/** A sunken area: dark edge top-left, white bottom-right, as Minecraft's slots. */
	private static void inset(GuiGraphics g, int x, int y, int w, int h, int fill) {
		g.fill(x, y, x + w, y + h, 0xFF373737);
		g.fill(x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
	}
}
