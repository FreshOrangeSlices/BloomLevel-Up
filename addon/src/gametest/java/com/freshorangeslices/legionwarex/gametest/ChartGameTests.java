package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.band.BandKind;
import com.freshorangeslices.legionwarex.band.Bands;
import com.freshorangeslices.legionwarex.chart.BeatenBosses;
import com.freshorangeslices.legionwarex.chart.ChartEvents;
import com.freshorangeslices.legionwarex.chart.Charts;
import com.freshorangeslices.legionwarex.chart.LairSearch;
import com.freshorangeslices.legionwarex.chart.Lairs;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Pirate charts, with Bosses'Rise's lairs (the dev run carries it) in the game test server's world. */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChartGameTests {
	private static final ResourceLocation KRAKEN_SHIP = new ResourceLocation("block_factorys_bosses", "kraken_ship");
	private static final ResourceLocation KRAKEN = new ResourceLocation("block_factorys_bosses", "kraken");
	private static final int REACH = 6000;

	/** Bosses'Rise's lairs only: the other mods' entries are skipped (not installed), as in a pack without them. */
	private static List<Lairs.Lair> bossesRiseLairs() {
		return Lairs.all().stream().filter(lair -> lair.structure().getNamespace().equals("block_factorys_bosses")).toList();
	}

	@GameTest(template = "empty")
	public static void theLairListLoadsFromData(GameTestHelper helper) {
		helper.assertTrue(Lairs.all().size() >= 30, "The add-on's lair files load: " + Lairs.all().size());
		helper.assertTrue(Lairs.all().stream().noneMatch(lair -> lair.structure().getNamespace().equals("skarrier_mobs")), "No Tunnel Gore lair: it's everywhere");
		helper.assertTrue(Lairs.all().stream().anyMatch(lair -> lair.structure().equals(KRAKEN_SHIP) && lair.bosses().contains(KRAKEN)),
				"The Kraken's ship is a lair");
		helper.assertTrue(Lairs.bosses().contains(KRAKEN), "The Kraken counts as a lair boss");
		helper.assertTrue(bossesRiseLairs().size() == 5, "Bosses'Rise's five lairs: " + bossesRiseLairs());
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 1200)
	public static void aChartLeadsToTheNearestUnbeatenLairAsATreasureMap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BeatenBosses beaten = BeatenBosses.of(level.getServer());
		beaten.clear();
		BlockPos from = helper.absolutePos(BlockPos.ZERO);

		try {
			LairSearch search = new LairSearch(level, from, REACH, bossesRiseLairs(), beaten);
			helper.assertTrue(search.size() >= 3, "Bosses'Rise's Overworld lairs can be in this world: " + search.size());
			LairSearch.Found first = search.run();
			helper.assertTrue(first != null, "A Bosses'Rise lair within " + REACH + " blocks (" + search.searched() + " looked at, " + search.millis() + " ms)");
			helper.assertTrue(first.distance() <= REACH && !first.rematch(), "Within reach, and not a rematch: " + first);
			LegionWareX.LOGGER.info("LegionWareX chart test: {} at {} ({} blocks, {} lairs in {} ms)", first.boss(), first.pos().toShortString(),
					Math.round(first.distance()), search.searched(), search.millis());

			// It's a treasure map: centred on the lair, a red X on it, and where it leads kept on the item.
			ItemStack map = Charts.read(level, first);
			helper.assertTrue(map.is(Items.FILLED_MAP) && Charts.isReadChart(map), "A read chart is a filled map");
			CompoundTag chart = map.getTag().getCompound(Charts.TAG);
			helper.assertTrue(chart.getInt("x") == first.pos().getX() && chart.getString("boss").equals(first.boss().toString()), "It keeps where it leads: " + chart);
			// The X lives on the item, as on vanilla's explorer maps (the map's data takes it up once the map is carried).
			helper.assertTrue(MapItem.getSavedData(map, level) != null, "The map's data exists");
			ListTag decorations = map.getTag().getList("Decorations", Tag.TAG_COMPOUND);
			helper.assertTrue(decorations.size() == 1 && decorations.getCompound(0).getByte("type") == MapDecoration.Type.RED_X.getIcon()
					&& decorations.getCompound(0).getDouble("x") == first.pos().getX(), "The map has a red X on the lair: " + decorations);

			// Charted: the next chart leads to another lair (each one somewhere new, as vanilla's explorer maps).
			beaten.chart(first.chunk());
			LairSearch.Found next = new LairSearch(level, from, REACH, bossesRiseLairs(), beaten).run();
			helper.assertTrue(next != null && !next.pos().equals(first.pos()) && !next.rematch(), "A charted lair is skipped: " + next + " after " + first);
			beaten.chart(next.chunk());
			LairSearch.Found third = new LairSearch(level, from, REACH, bossesRiseLairs(), beaten).run();
			helper.assertTrue(third == null || (!third.pos().equals(first.pos()) && !third.pos().equals(next.pos())), "And the next one too: " + third);
			beaten.clearCharted();

			// Beaten: the next chart goes elsewhere; with every one beaten, the nearest comes back as a rematch.
			beaten.add(first.boss());
			LairSearch.Found second = new LairSearch(level, from, REACH, bossesRiseLairs(), beaten).run();
			helper.assertTrue(second == null || !second.boss().equals(first.boss()), "A beaten boss's lair is skipped: " + second);
			bossesRiseLairs().forEach(lair -> lair.bosses().forEach(beaten::add));
			LairSearch.Found rematch = new LairSearch(level, from, REACH, bossesRiseLairs(), beaten).run();
			helper.assertTrue(rematch != null && rematch.rematch() && rematch.pos().equals(first.pos()),
					"All beaten: the nearest lair again, as a rematch: " + rematch);
			beaten.chart(first.chunk());
			LairSearch.Found again = new LairSearch(level, from, REACH, bossesRiseLairs(), beaten).run();
			helper.assertTrue(again != null && again.rematch() && again.pos().equals(first.pos()), "A rematch doesn't mind charts: " + again);

			// Out of reach: nothing (a lair can't be nearer than the next placement cell).
			LairSearch.Found none = new LairSearch(level, from, 300, List.of(new Lairs.Lair(KRAKEN_SHIP, List.of(KRAKEN))), beaten).run();
			helper.assertTrue(none == null || none.distance() <= 300, "Nothing past the chart's reach: " + none);
		} finally {
			beaten.clear();
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aChartCrumblesOnArrival(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("lwx_chart".getBytes()), "lwx_chart"));
		BlockPos here = helper.absolutePos(new BlockPos(1, 1, 1));
		player.moveTo(here.getX() + 0.5, here.getY(), here.getZ() + 0.5);
		player.getInventory().clearContent();
		ItemStack far = Charts.read(level, new LairSearch.Found(here.offset(500, 0, 0), KRAKEN_SHIP, KRAKEN, 500, false));
		ItemStack near = Charts.read(level, new LairSearch.Found(here.offset(20, 0, 10), KRAKEN_SHIP, KRAKEN, 22, false));
		player.getInventory().setItem(3, far);
		player.getInventory().setItem(5, near);
		Charts.checkArrival(player);
		helper.assertTrue(player.getInventory().getItem(5).isEmpty(), "The chart whose X is 22 blocks off has crumbled");
		helper.assertTrue(Charts.isReadChart(player.getInventory().getItem(3)), "The chart whose X is 500 blocks off is still there");
		player.getInventory().clearContent();
		helper.succeed();
	}

	/** The drop, by its event: about 1 in 15 for a band pirate a player hit, never for one of the Kraken's crew. */
	@SuppressWarnings("unchecked")
	@GameTest(template = "room")
	public static void aBandPirateDropsAChartAbout1In15(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Mob> band = Bands.spawn(level, BandKind.PIRATE, helper.absolutePos(Rooms.on(helper, 1, 1)), RandomSource.create(2), 1, false);
		Mob crew = helper.spawn((EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("block_factorys_bosses", "pirate_rook")),
				Rooms.on(helper, 3, 3));

		try {
			helper.assertTrue(!band.isEmpty() && Bands.kindOf(band.get(0)) == BandKind.PIRATE, "A band pirate knows its band's kind");
			helper.assertTrue(Bands.kindOf(crew) == null, "The Kraken's crew isn't in a band");
			Mob pirate = band.get(0);
			DamageSource hit = level.damageSources().generic();
			int charts = 0;
			int crewCharts = 0;
			int notHit = 0;

			for (int i = 0; i < 600; i++) {
				charts += drops(new LivingDropsEvent(pirate, hit, new ArrayList<>(), 0, true));
				crewCharts += drops(new LivingDropsEvent(crew, hit, new ArrayList<>(), 0, true));
				notHit += drops(new LivingDropsEvent(pirate, hit, new ArrayList<>(), 0, false));
			}

			helper.assertTrue(charts >= 15 && charts <= 70, "About 1 in 15 of 600 band pirates (40): " + charts);
			helper.assertTrue(crewCharts == 0, "The Kraken's crew never drops one: " + crewCharts);
			helper.assertTrue(notHit == 0, "Only when a player hit it: " + notHit);
		} finally {
			band.forEach(Mob::discard);
			crew.discard();
		}

		helper.succeed();
	}

	private static int drops(LivingDropsEvent event) {
		ChartEvents.onDrops(event);
		return (int) event.getDrops().stream().filter(item -> item.getItem().is(LegionWareX.PIRATE_CHART.get())).count();
	}
}
