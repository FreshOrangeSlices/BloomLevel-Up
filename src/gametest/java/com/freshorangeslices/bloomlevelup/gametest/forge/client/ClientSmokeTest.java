package com.freshorangeslices.bloomlevelup.gametest.forge.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.client.ClientBloomState;
import com.freshorangeslices.bloomlevelup.client.ClientPackets;
import com.freshorangeslices.bloomlevelup.client.gui.RuneScreen;
import com.freshorangeslices.bloomlevelup.client.gui.SideButton;
import com.freshorangeslices.bloomlevelup.entity.ThrownWeapon;
import com.freshorangeslices.bloomlevelup.network.BloomPayloads;
import com.freshorangeslices.bloomlevelup.client.gui.HubScreen;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.network.BloomSync;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.SkillXp;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscIds;
import com.freshorangeslices.bloomlevelup.skill.utility.UtilityIds;

/**
 * The client smoke test (CI only: runs when BLOOM_CLIENT_TEST is set): a real client makes a flat world, gets
 * levels and a few Majors, then opens every hub tab, the stat sheet, a Major's details window and the inventory,
 * taking a screenshot of each, and quits. Client mixins, the GUI and the sync all have to work for it to get
 * through; the result goes to {@code bloomtest-client.txt} and the log (BLOOMTEST lines).
 */
@Mod.EventBusSubscriber(modid = BloomLevelUp.MOD_ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
	private static final boolean ON = System.getenv("BLOOM_CLIENT_TEST") != null;
	/** Gives up after this many client ticks (5 minutes). */
	private static final int TIMEOUT = 20 * 60 * 5;

	private record Step(String name, Predicate<Minecraft> ready, int settle, Consumer<Minecraft> action) {
	}

	private static final List<Step> STEPS = new ArrayList<>();
	private static final List<String> FAILURES = new ArrayList<>();
	private static int index;
	private static int readyFor;
	private static int ticks;
	private static int shots;
	private static boolean done;

	static {
		step("create world", mc -> mc.screen instanceof TitleScreen, 20, ClientSmokeTest::createWorld);
		step("join", mc -> mc.player != null && mc.level != null && ClientBloomState.ready() && mc.getSingleplayerServer() != null, 100,
				ClientSmokeTest::grant);
		step("synced", mc -> ClientBloomState.profile() != null && ClientBloomState.profile().owns(CombatIds.SLASH), 20, mc -> {
		});
		tab("utility", SkillXp.UTILITY);
		tab("combat", SkillXp.COMBAT);
		tab("armor", SkillXp.ARMOR);
		tab("misc", SkillXp.MISC);
		step("sheet", always(), 10, mc -> {
			ClientBloomState.selectCategory(SkillXp.COMBAT);
			HubScreen hub = new HubScreen();
			mc.setScreen(hub);
			hub.openSheetForTests();
		});
		step("sheet shot", always(), 40, mc -> shot(mc, "sheet_combat"));
		step("sheet details", always(), 0, mc -> ((HubScreen) mc.screen).sheetForTests(true, null));
		step("sheet details shot", always(), 30, mc -> shot(mc, "sheet_combat_details"));
		step("details", always(), 0, mc -> {
			HubScreen hub = new HubScreen();
			mc.setScreen(hub);
			hub.openDetailsForTests(CombatIds.SLASH);
		});
		step("details shot", always(), 20, mc -> {
			if (!(mc.screen instanceof HubScreen hub) || !hub.detailsOpenForTests()) {
				FAILURES.add("Slash's details window didn't open");
			}

			shot(mc, "hub_major_details");
		});
		step("inventory", always(), 0, mc -> mc.setScreen(new InventoryScreen(mc.player)));
		step("inventory shot", always(), 20, mc -> shot(mc, "inventory"));
		// Rune Embedding: asked of the server like its button does; the server opens the menu, the client its screen.
		step("runes", always(), 0, mc -> {
			mc.setScreen(null);
			SideButton.openRunes();
		});
		step("runes shot", mc -> mc.screen instanceof RuneScreen, 10, mc -> shot(mc, "runes"));
		// The anvil screen (its label mixin only loads with it).
		step("anvil", always(), 0, mc -> mc.setScreen(new AnvilScreen(new AnvilMenu(0, mc.player.getInventory()), mc.player.getInventory(),
				Component.translatable("container.repair"))));
		step("anvil shot", always(), 10, mc -> shot(mc, "anvil"));
		// The HUD with Brace! on, Bloodlust and Predator bars, and a thrown sword hanging in front (its renderer).
		step("hud", always(), 0, ClientSmokeTest::hudScene);
		step("hud shot", always(), 15, mc -> shot(mc, "hud"));
		step("quit", always(), 10, ClientSmokeTest::finish);
	}

	private ClientSmokeTest() {
	}

	private static void step(String name, Predicate<Minecraft> ready, int settle, Consumer<Minecraft> action) {
		STEPS.add(new Step(name, ready, settle, action));
	}

	private static Predicate<Minecraft> always() {
		return mc -> mc.player != null;
	}

	private static void tab(String name, String category) {
		step("hub " + name, always(), 0, mc -> {
			ClientBloomState.selectCategory(category);
			mc.setScreen(new HubScreen());
		});
		step("hub " + name + " shot", always(), 20, mc -> shot(mc, "hub_" + name));
	}

	@SubscribeEvent
	public static void tick(TickEvent.ClientTickEvent event) {
		if (!ON || done || event.phase != TickEvent.Phase.END) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		if (++ticks > TIMEOUT) {
			FAILURES.add("timed out at step '" + STEPS.get(index).name() + "'");
			finish(mc);
			return;
		}

		Step step = STEPS.get(index);

		if (!step.ready().test(mc)) {
			readyFor = 0;
			return;
		}

		if (readyFor++ < step.settle()) {
			return;
		}

		readyFor = 0;
		BloomLevelUp.LOGGER.info("BLOOMTEST step {}", step.name());

		try {
			step.action().accept(mc);
		} catch (RuntimeException e) {
			BloomLevelUp.LOGGER.error("BLOOMTEST step {} failed", step.name(), e);
			FAILURES.add(step.name() + ": " + e);
		}

		if (!done) {
			index++;
		}
	}

	private static void createWorld(Minecraft mc) {
		mc.getWindow().setWindowed(1280, 720);
		LevelSettings settings = new LevelSettings("bloomtest", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(),
				WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel("bloomtest", settings, new WorldOptions(20261026L, false, false),
				registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
	}

	/** On the server thread: every tab at a high level, a few Majors bought, some points spent, weapons to look at. */
	private static void grant(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		UUID id = mc.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			ProgressionService service = BloomServices.progression();
			BloomProfile profile = BloomAttachments.profile(player);

			for (String category : List.of(SkillXp.UTILITY, SkillXp.COMBAT, SkillXp.ARMOR, SkillXp.MISC)) {
				service.addXp(profile, category, 1.0e9);
			}

			for (String major : List.of(CombatIds.SLASH, CombatIds.FULL_SWEEP, CombatIds.PREDATOR, CombatIds.FIRST_STRIKE, ArmorIds.BRACE,
					ArmorIds.IRON_SKIN, MiscIds.HOARDERS_POCKET, MiscIds.RUNE_EMBEDDING, UtilityIds.VEIN_MINING)) {
				ActionResult result = service.purchaseMajor(profile, major);
				BloomLevelUp.LOGGER.info("BLOOMTEST buy {}: {}", major, result.outcome());
			}

			service.allocate(profile, CombatIds.POWER, ProgressionService.Pool.NATURAL, 30);
			service.allocate(profile, CombatIds.CRITICAL, ProgressionService.Pool.NATURAL, 20);
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			sword.enchant(Enchantments.SHARPNESS, 3);
			sword.enchant(Enchantments.SMITE, 2);
			player.getInventory().setItem(0, sword);
			player.getInventory().setItem(1, new ItemStack(Items.BOW));
			player.getInventory().setItem(2, new ItemStack(Items.IRON_AXE));
			player.getInventory().setItem(3, new ItemStack(Items.IRON_PICKAXE));
			player.getInventory().selected = 0;
			StatEffects.refresh(player);
			BloomSync.changed(player);
		});
	}

	private static void hudScene(Minecraft mc) {
		mc.setScreen(null);
		ClientPackets.meter(new BloomPayloads.Meter(BloomPayloads.Meter.BLOODLUST, 3, 0.3F, 400, 400));
		ClientPackets.meter(new BloomPayloads.Meter(BloomPayloads.Meter.PREDATOR, 2, 0.2F, 400, 400));
		IntegratedServer server = mc.getSingleplayerServer();
		UUID id = mc.player.getUUID();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			BloomAttachments.profile(player).setCounter(ArmorIds.BRACED, 1);
			BloomSync.changed(player);
			ThrownWeapon thrown = ThrownWeapon.create(player.level(), player, new ItemStack(Items.IRON_AXE));
			Vec3 ahead = player.getEyePosition().add(player.getLookAngle().scale(2.5));
			thrown.setPos(ahead.x, ahead.y - 0.3, ahead.z);
			thrown.setNoGravity(true);
			thrown.setDeltaMovement(Vec3.ZERO);
			player.level().addFreshEntity(thrown);
		});
	}

	private static void shot(Minecraft mc, String name) {
		shots++;
		Screenshot.grab(mc.gameDirectory, "bloom_" + name + ".png", mc.getMainRenderTarget(),
				message -> BloomLevelUp.LOGGER.info("BLOOMTEST screenshot {}: {}", name, message.getString()));
	}

	private static void finish(Minecraft mc) {
		done = true;
		String result = (FAILURES.isEmpty() ? "PASS" : "FAIL") + " " + shots + " screenshots" + (FAILURES.isEmpty() ? "" : ": " + FAILURES);
		BloomLevelUp.LOGGER.info("BLOOMTEST client smoke test: {}", result);

		try {
			Files.writeString(Path.of("bloomtest-client.txt"), result + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			BloomLevelUp.LOGGER.error("BLOOMTEST couldn't write the result", e);
		}

		mc.stop();
	}
}
