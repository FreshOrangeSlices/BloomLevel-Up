package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.Despawning;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.band.BandKind;
import com.freshorangeslices.legionwarex.band.Bands;
import com.freshorangeslices.legionwarex.band.FollowLeaderGoal;
import com.freshorangeslices.legionwarex.band.PirateTuning;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** War bands and pirate bands, with Illager Invasion and Bosses'Rise loaded (the dev runs carry them). */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BandGameTests {
	private static final ResourceLocation CAPTAIN = new ResourceLocation("block_factorys_bosses", "pirate_captain");
	private static final ResourceLocation ROOK = new ResourceLocation("block_factorys_bosses", "pirate_rook");

	private static String id(Entity entity) {
		return String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
	}

	private static void clear(List<Mob> band) {
		band.forEach(Entity::discard);
	}

	@GameTest(template = "room")
	public static void aWarBandIsAPatrolLedByTheBanner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Mob> band = Bands.spawn(level, BandKind.WAR, helper.absolutePos(Rooms.on(helper, 4, 4)), RandomSource.create(7), 5, false);

		try {
			helper.assertTrue(band.size() >= 3, "Most of a band of 5 found room on flat ground: " + Bands.describe(BandKind.WAR, band));
			Mob leader = band.get(0);
			helper.assertTrue(leader instanceof PatrollingMonster patroller && patroller.isPatrolLeader(), "The first is the patrol's leader: " + id(leader));
			helper.assertTrue(leader.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof BannerItem, "The leader carries the ominous banner");
			helper.assertTrue(Bands.leaderOf(leader) == null, "The leader follows no one");

			for (Mob member : band) {
				helper.assertTrue(Bands.inBand(member), "Every member is tagged as in a band");
				// isPatrolling() is protected: its saved flag says the same.
				CompoundTag saved = new CompoundTag();
				member.saveWithoutId(saved);
				helper.assertTrue(member instanceof PatrollingMonster && saved.getBoolean("Patrolling"), id(member) + " patrols with the band");
				helper.assertFalse(member.isPersistenceRequired(), id(member) + " despawns like a patrol member (the game decides)");

				if (member != leader) {
					helper.assertTrue(leader.getUUID().equals(Bands.leaderOf(member)), id(member) + " knows its leader");
					helper.assertFalse(((PatrollingMonster) member).isPatrolLeader(), id(member) + " isn't a second leader");
				}
			}

			// Mixed: with Illager Invasion installed, its illagers join vanilla's.
			Set<String> seen = new HashSet<>();
			RandomSource random = RandomSource.create(1);

			for (int i = 0; i < 400; i++) {
				seen.add(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(BandKind.WAR.pickMember(random))));
			}

			for (String expected : List.of("minecraft:pillager", "minecraft:vindicator", "illagerinvasion:marauder", "illagerinvasion:provoker",
					"illagerinvasion:alchemist", "illagerinvasion:basher")) {
				helper.assertTrue(seen.contains(expected) || !ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(expected)),
						"War bands take " + expected + ": " + seen);
			}

			helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation("illagerinvasion", "marauder")),
					"Illager Invasion is loaded in the dev run");
		} finally {
			clear(band);
		}

		helper.succeed();
	}

	/**
	 * A closed stone room around the test (a floor at y 0, air from 1 to 4, a ceiling and walls): the game test server
	 * makes a normal world, so a test can sit in the sea or a hillside, and water flows into its cleared space. Tests that
	 * need a big one use the "room" template instead (12 by 12, stone all round), which also keeps the next test's
	 * blocks out of theirs.
	 */
	private static void room(GameTestHelper helper, int from, int to) {
		for (int x = from; x <= to; x++) {
			for (int z = from; z <= to; z++) {
				boolean wall = x == from || x == to || z == from || z == to;
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);

				for (int y = 1; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	@GameTest(template = "room", timeoutTicks = 200)
	public static void aPirateBandKeepsToItsRookAndKillingItSummonsNoBoss(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(CAPTAIN), "Bosses'Rise is loaded in the dev run");
		BlockPos at = helper.absolutePos(Rooms.on(helper, 4, 4));
		List<Mob> band = Bands.spawn(level, BandKind.PIRATE, at, RandomSource.create(3), 4, false);
		helper.assertTrue(band.size() >= 3, "Most of a pirate band of 4 found room: " + Bands.describe(BandKind.PIRATE, band));
		Mob leader = band.get(0);
		helper.assertTrue(id(leader).equals("block_factorys_bosses:pirate_rook"), "A rook leads: " + id(leader));
		RandomSource random = RandomSource.create(5);

		for (int i = 0; i < 200; i++) {
			helper.assertFalse(BandKind.PIRATE.pickLeader(random).equals(ForgeRegistries.ENTITY_TYPES.getValue(CAPTAIN))
					|| BandKind.PIRATE.pickMember(random).equals(ForgeRegistries.ENTITY_TYPES.getValue(CAPTAIN)), "No captain in a pirate band");
		}

		for (Mob pirate : band.subList(1, band.size())) {
			helper.assertTrue(id(pirate).equals("block_factorys_bosses:pirate_rook") || id(pirate).equals("block_factorys_bosses:crossbow_pirate"),
					"Rooks and crossbow pirates follow: " + id(pirate));
			helper.assertTrue(leader.getUUID().equals(Bands.leaderOf(pirate)), id(pirate) + " knows its leader");
			helper.assertTrue(pirate.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof FollowLeaderGoal),
					id(pirate) + " keeps near the leading rook");
		}

		for (Mob pirate : band) {
			double base = pirate.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
			helper.assertTrue(Math.abs(pirate.getAttributeValue(Attributes.MOVEMENT_SPEED) - base * (1.0 + PirateTuning.SPEED_BONUS)) < 1.0E-6,
					id(pirate) + " walks 40% faster: " + pirate.getAttributeValue(Attributes.MOVEMENT_SPEED) + " from " + base);
		}

		// Bosses'Rise keeps every pirate for good; these despawn like monsters (BandEvents), unless a player keeps them.
		helper.assertTrue(band.stream().allMatch(Mob::isPersistenceRequired), "Pirates are kept for good by their mod");
		helper.assertFalse(Despawning.keptByPlayer(leader), "Nothing keeps the leader");
		Mob loner = helper.spawn((EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(ROOK), Rooms.on(helper, 2, 2));
		helper.assertFalse(Bands.inBand(loner), "A pirate the add-on didn't send (the Kraken's crew) is left to its mod");
		helper.assertTrue(loner.getAttributeValue(Attributes.MOVEMENT_SPEED) == loner.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue(),
				"The Kraken's crew keeps its own speed");
		loner.discard();

		// The Kraken comes only when the pirates its own ship spawned are dead; these were never its.
		band.forEach(pirate -> pirate.kill());
		helper.runAfterDelay(60, () -> {
			AABB around = new AABB(at).inflate(96);
			List<Entity> bosses = level.getEntities((Entity) null, around,
					entity -> entity instanceof LivingEntity living && living.isAlive() && id(entity).startsWith("block_factorys_bosses:"));
			helper.assertTrue(bosses.isEmpty(), "Killing the band brought nothing: " + bosses);
			helper.succeed();
		});
	}

	/**
	 * A band's rook is ready to swing again after 25 ticks, the Kraken's crew after 40. Both stand still (no AI, so no
	 * goal moves them or drops their target) with a villager 4 blocks off: within 16 blocks the cooldown counts, outside
	 * 1.5 no swing starts. Bosses'Rise's own counting runs in the rook's base tick, which runs without AI too.
	 */
	@SuppressWarnings("unchecked")
	@GameTest(template = "room", timeoutTicks = 100)
	public static void aBandRookSwingsSooner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Mob> band = Bands.spawn(level, BandKind.PIRATE, helper.absolutePos(Rooms.on(helper, 1, 1)), RandomSource.create(3), 1, false);
		helper.assertTrue(band.size() == 1 && PirateTuning.hasSwingCounters(band.get(0)), "A band's rook, with Bosses'Rise's swing counters: "
				+ Bands.describe(BandKind.PIRATE, band));
		Mob quick = band.get(0);
		Mob crew = helper.spawn((EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(ROOK), Rooms.on(helper, 1, 7));
		Mob target = helper.spawn(EntityType.VILLAGER, Rooms.on(helper, 5, 4));

		for (Mob mob : List.of(quick, crew, target)) {
			mob.setNoAi(true);
		}

		quick.setTarget(target);
		crew.setTarget(target);
		int quickStart = PirateTuning.cooldown(quick);
		int crewStart = PirateTuning.cooldown(crew);
		helper.runAfterDelay(PirateTuning.COOLDOWN_TICKS + 1, () -> {
			int quickNow = PirateTuning.cooldown(quick);
			int crewNow = PirateTuning.cooldown(crew);

			try {
				helper.assertTrue(quickNow >= PirateTuning.COOLDOWN_STEPS - 1, "The band's rook is about ready to swing after "
						+ (PirateTuning.COOLDOWN_TICKS + 1) + " ticks: " + quickStart + " -> " + quickNow);
				helper.assertTrue(crewNow < PirateTuning.COOLDOWN_STEPS - 10, "The Kraken's crew counts one a tick: " + crewStart + " -> " + crewNow);
				helper.assertTrue(crewNow > crewStart, "The crew's own counter runs (a target within 16 blocks): " + crewStart + " -> " + crewNow);
			} finally {
				quick.discard();
				crew.discard();
				target.discard();
			}

			helper.succeed();
		});
	}

	@GameTest(template = "empty")
	public static void aBandMemberStandsOnTheGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		room(helper, 0, 6);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.WATER);
		helper.setBlock(new BlockPos(4, 1, 2), Blocks.STONE);
		// Room just above the floor; none inside it, in water, or on a block in mid-air's way (a lone stone, then air under 3 up).
		helper.assertTrue(Bands.canStand(level, helper.absolutePos(new BlockPos(2, 1, 2)), EntityType.PILLAGER), "On the floor");
		helper.assertFalse(Bands.canStand(level, helper.absolutePos(new BlockPos(2, 0, 2)), EntityType.PILLAGER), "Not inside the floor");
		helper.assertFalse(Bands.canStand(level, helper.absolutePos(new BlockPos(3, 1, 3)), EntityType.PILLAGER), "Not in water");
		helper.assertFalse(Bands.canStand(level, helper.absolutePos(new BlockPos(2, 3, 2)), EntityType.PILLAGER), "Not in mid-air");
		helper.assertTrue(Bands.canStand(level, helper.absolutePos(new BlockPos(4, 2, 2)), EntityType.PILLAGER), "On a block");
		helper.assertTrue(BandKind.byId("war") == BandKind.WAR && BandKind.byId("pirate") == BandKind.PIRATE && BandKind.byId("navy") == null,
				"Bands by name, as the command takes them");
		helper.succeed();
	}
}
