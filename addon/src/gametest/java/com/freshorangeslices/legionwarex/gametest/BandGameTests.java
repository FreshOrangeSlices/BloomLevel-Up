package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.Despawning;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.band.BandKind;
import com.freshorangeslices.legionwarex.band.Bands;
import com.freshorangeslices.legionwarex.band.FollowLeaderGoal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** War bands and pirate bands, with Illager Invasion and Bosses'Rise loaded (the dev runs carry them). */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BandGameTests {
	private static final ResourceLocation CAPTAIN = new ResourceLocation("block_factorys_bosses", "pirate_captain");

	private static String id(Entity entity) {
		return String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
	}

	private static void clear(List<Mob> band) {
		band.forEach(Entity::discard);
	}

	@GameTest(template = "empty")
	public static void aWarBandIsAPatrolLedByTheBanner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Mob> band = Bands.spawn(level, BandKind.WAR, helper.absolutePos(new BlockPos(1, 1, 1)), RandomSource.create(7), 5);

		try {
			helper.assertTrue(band.size() >= 3, "Most of a band of 5 found room on flat ground: " + Bands.describe(BandKind.WAR, band));
			Mob leader = band.get(0);
			helper.assertTrue(leader instanceof PatrollingMonster patroller && patroller.isPatrolLeader(), "The first is the patrol's leader: " + id(leader));
			helper.assertTrue(leader.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof BannerItem, "The leader carries the ominous banner");
			helper.assertTrue(Bands.leaderOf(leader) == null, "The leader follows no one");

			for (Mob member : band) {
				helper.assertTrue(Bands.inBand(member), "Every member is tagged as in a band");
				helper.assertTrue(member instanceof PatrollingMonster patroller && patroller.isPatrolling(), id(member) + " patrols with the band");
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

	@SuppressWarnings("unchecked")
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void aPirateBandKeepsToItsCaptainAndKillingItSummonsNoBoss(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(CAPTAIN), "Bosses'Rise is loaded in the dev run");
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		List<Mob> band = Bands.spawn(level, BandKind.PIRATE, at, RandomSource.create(3), 4);
		helper.assertTrue(band.size() >= 3, "Most of a pirate band of 4 found room: " + Bands.describe(BandKind.PIRATE, band));
		Mob captain = band.get(0);
		helper.assertTrue(captain.getType() == ForgeRegistries.ENTITY_TYPES.getValue(CAPTAIN), "A captain leads: " + id(captain));

		for (Mob pirate : band.subList(1, band.size())) {
			helper.assertTrue(id(pirate).equals("block_factorys_bosses:pirate_rook") || id(pirate).equals("block_factorys_bosses:crossbow_pirate"),
					"Rooks and crossbow pirates follow: " + id(pirate));
			helper.assertTrue(captain.getUUID().equals(Bands.leaderOf(pirate)), id(pirate) + " knows its captain");
			helper.assertTrue(pirate.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof FollowLeaderGoal),
					id(pirate) + " keeps near the captain");
		}

		// Bosses'Rise keeps every pirate for good; these despawn like monsters (BandEvents), unless a player keeps them.
		helper.assertTrue(band.stream().allMatch(Mob::isPersistenceRequired), "Pirates are kept for good by their mod");
		helper.assertFalse(Despawning.keptByPlayer(captain), "Nothing keeps the captain");
		Mob loner = helper.spawn((EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("block_factorys_bosses", "pirate_rook")),
				new BlockPos(3, 2, 3));
		helper.assertFalse(Bands.inBand(loner), "A pirate the add-on didn't send (the Kraken's crew) is left to its mod");
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

	@GameTest(template = "empty")
	public static void aBandMemberStandsOnTheGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// The template is air on the test floor: room just above the floor, none inside it, no ground in mid-air.
		helper.assertTrue(Bands.canStand(level, helper.absolutePos(new BlockPos(1, 0, 1)), EntityType.PILLAGER), "On the floor");
		helper.assertFalse(Bands.canStand(level, helper.absolutePos(new BlockPos(1, -1, 1)), EntityType.PILLAGER), "Not inside the floor");
		helper.assertFalse(Bands.canStand(level, helper.absolutePos(new BlockPos(1, 5, 1)), EntityType.PILLAGER), "Not in mid-air");
		helper.assertTrue(BandKind.byId("war") == BandKind.WAR && BandKind.byId("pirate") == BandKind.PIRATE && BandKind.byId("navy") == null,
				"Bands by name, as the command takes them");
		helper.succeed();
	}
}
