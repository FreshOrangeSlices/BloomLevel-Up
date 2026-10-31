package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.sentinel.HoldPostGoal;
import com.freshorangeslices.legionwarex.sentinel.SentinelTable;
import com.freshorangeslices.legionwarex.sentinel.Sentinels;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Daytime sentinels: their table, how they're placed and dressed, and holding their post. */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SentinelGameTests {
	private static final ResourceLocation ZOMBIE = new ResourceLocation("minecraft", "zombie");
	private static final ResourceLocation PILLAGER = new ResourceLocation("minecraft", "pillager");

	@GameTest(template = "empty")
	public static void theSentinelTableLoadsAndPicksByBiome(GameTestHelper helper) {
		List<SentinelTable.Entry> loaded = SentinelTable.all();
		helper.assertTrue(loaded.size() >= 10, "The add-on's sentinel files load: " + loaded.size());
		helper.assertTrue(loaded.stream().anyMatch(entry -> entry.entity().equals(PILLAGER) && entry.roams()), "Pillager scouts roam");

		ServerLevel level = helper.getLevel();
		Holder<Biome> plains = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
		Holder<Biome> snowy = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.SNOWY_PLAINS);
		Holder<Biome> desert = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
		SentinelTable.Entry plainsOnly = new SentinelTable.Entry(new ResourceLocation("minecraft", "vindicator"), 10, List.of("minecraft:plains"), false);
		SentinelTable.Entry snowOnly = new SentinelTable.Entry(new ResourceLocation("minecraft", "stray"), 10, List.of("#forge:is_snowy"), false);
		SentinelTable.Entry anywhere = new SentinelTable.Entry(ZOMBIE, 10, List.of(), false);
		SentinelTable.Entry scout = new SentinelTable.Entry(PILLAGER, 10, List.of("#forge:is_plains"), true);
		SentinelTable.Entry missing = new SentinelTable.Entry(new ResourceLocation("nosuchmod", "ghost"), 1000, List.of(), false);

		try {
			SentinelTable.set(List.of(plainsOnly, snowOnly, anywhere, scout, missing));
			helper.assertTrue(Sentinels.vanillaBiome(plains), "Plains are vanilla's");
			Set<ResourceLocation> onPlains = picks(plains, false);
			Set<ResourceLocation> inSnow = picks(snowy, false);
			helper.assertTrue(onPlains.equals(Set.of(plainsOnly.entity(), ZOMBIE)), "On plains: the plains one and the one for anywhere: " + onPlains);
			helper.assertTrue(inSnow.equals(Set.of(snowOnly.entity(), ZOMBIE)), "In snow: the snowy one and the one for anywhere: " + inSnow);
			helper.assertTrue(picks(plains, true).equals(Set.of(PILLAGER)), "Scouts are picked apart from sentinels");
			helper.assertTrue(picks(desert, true).isEmpty(), "No scout where none fits");
			helper.assertFalse(onPlains.contains(missing.entity()), "A mob whose mod isn't installed is never picked");
		} finally {
			SentinelTable.set(loaded);
		}

		helper.succeed();
	}

	private static Set<ResourceLocation> picks(Holder<Biome> biome, boolean roamers) {
		Set<ResourceLocation> seen = new HashSet<>();
		RandomSource random = RandomSource.create(4);

		for (int i = 0; i < 200; i++) {
			SentinelTable.Entry entry = SentinelTable.pick(biome, random, roamers);

			if (entry != null) {
				seen.add(entry.entity());
			}
		}

		return seen;
	}

	/**
	 * An undead sentinel: tagged, its post kept, a helmet that never wears out or drops, 16 blocks of sight, and its post
	 * goal; drawn 6 blocks off, it walks back.
	 */
	@GameTest(template = "room", timeoutTicks = 300)
	public static void aSentinelIsDressedAndHoldsItsPost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos post = helper.absolutePos(Rooms.on(helper, 3, 3));
		Mob zombie = Sentinels.spawn(level, new SentinelTable.Entry(ZOMBIE, 1, List.of(), false), post, false);
		helper.assertTrue(zombie != null, "A zombie sentinel was placed");

		// A grown zombie on its feet (a spawn can roll a baby or a chicken jockey).
		if (zombie.getVehicle() != null) {
			Entity vehicle = zombie.getVehicle();
			zombie.stopRiding();
			vehicle.discard();
		}

		if (zombie instanceof Zombie grown) {
			grown.setBaby(false);
		}

		try {
			helper.assertTrue(Sentinels.isSentinel(zombie) && post.equals(Sentinels.postOf(zombie)), "It's a sentinel and knows its post");
			ItemStack head = zombie.getItemBySlot(EquipmentSlot.HEAD);
			helper.assertFalse(head.isEmpty(), "An undead sentinel wears a helmet (the sun's rule spares it)");
			helper.assertFalse(head.isDamageableItem(), "The helmet never wears out in the sun: " + head.getTag());
			CompoundTag saved = new CompoundTag();
			zombie.saveWithoutId(saved);
			helper.assertTrue(saved.getList("ArmorDropChances", Tag.TAG_FLOAT).getFloat(3) <= 0.0F, "The helmet never drops: " + saved.get("ArmorDropChances"));
			helper.assertTrue(zombie.getAttributeValue(Attributes.FOLLOW_RANGE) <= Sentinels.SIGHT + 1.0E-6,
					"It notices players within 16 blocks: " + zombie.getAttributeValue(Attributes.FOLLOW_RANGE));
			helper.assertTrue(zombie.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof HoldPostGoal),
					"It holds its post");
		} catch (RuntimeException e) {
			zombie.discard();
			throw e;
		}

		zombie.teleportTo(post.getX() + 6.5, post.getY(), post.getZ() + 0.5);
		helper.runAfterDelay(200, () -> {
			double away = Math.sqrt(zombie.blockPosition().distSqr(post));
			zombie.discard();
			helper.assertTrue(away <= 3.5, "Drawn 6 blocks off, it walked back to its post: " + away);
			helper.succeed();
		});
	}

	/** A scout (a roamer) has no post; a sentinel that isn't undead gets no helmet. */
	@GameTest(template = "room")
	public static void aScoutRoamsAndOnlyTheUndeadGetHelmets(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Mob> placed = new ArrayList<>();

		try {
			Mob scout = Sentinels.spawn(level, new SentinelTable.Entry(PILLAGER, 1, List.of(), true), helper.absolutePos(Rooms.on(helper, 2, 2)), false);
			placed.add(scout);
			helper.assertTrue(scout != null && Sentinels.isSentinel(scout) && Sentinels.postOf(scout) == null, "A scout has no post");
			helper.assertTrue(scout.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof HoldPostGoal), "A scout wanders");
			helper.assertTrue(scout.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "A pillager isn't undead: no helmet");
		} finally {
			placed.stream().filter(mob -> mob != null).forEach(Mob::discard);
		}

		helper.succeed();
	}

	/**
	 * Migamigos' Enemigos as sentinels (a table entry's nbt and xp): the shipped table has each companion as one, a placed
	 * one comes hostile, and a player's kill pays its 25 experience on top of its own. Migamigos is in the dev run.
	 */
	@GameTest(template = "room", timeoutTicks = 100)
	public static void anEnemigoSentinelComesHostileAndPaysExtraExperience(GameTestHelper helper) {
		ResourceLocation claymore = new ResourceLocation("migamigos", "claymore");
		helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(claymore), "Migamigos is in the dev run");
		List<SentinelTable.Entry> enemigos = SentinelTable.all().stream().filter(entry -> entry.entity().getNamespace().equals("migamigos")).toList();
		helper.assertTrue(enemigos.size() == 5 && enemigos.stream().allMatch(entry -> entry.nbt() != null && entry.nbt().getBoolean("IsEnemigo") && entry.xp() == 25),
				"The table has the five companions as Enemigos, 25 experience each: " + enemigos);

		ServerLevel level = helper.getLevel();
		CompoundTag nbt = new CompoundTag();
		nbt.putBoolean("IsEnemigo", true);
		Mob enemigo = Sentinels.spawn(level, new SentinelTable.Entry(claymore, 1, List.of(), false, nbt, 25), helper.absolutePos(Rooms.on(helper, 3, 3)), false);
		helper.assertTrue(enemigo != null, "An Enemigo Claymore was placed");

		try {
			helper.assertTrue(enemigo.saveWithoutId(new CompoundTag()).getBoolean("IsEnemigo"), "It came as an Enemigo");
			helper.assertTrue(Sentinels.isSentinel(enemigo) && Sentinels.bonusXp(enemigo) == 25, "It's a sentinel worth 25 more experience");
		} catch (RuntimeException e) {
			enemigo.discard();
			throw e;
		}

		AABB around = new AABB(enemigo.blockPosition()).inflate(6.0);
		Player player = helper.makeMockPlayer();
		enemigo.hurt(level.damageSources().playerAttack(player), 10000.0F);
		helper.assertTrue(enemigo.isDeadOrDying(), "A player killed it");
		helper.runAfterDelay(25, () -> {
			List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, around);
			int xp = orbs.stream().mapToInt(ExperienceOrb::getValue).sum();
			orbs.forEach(Entity::discard);
			helper.assertTrue(xp >= 25, "It dropped its 25 extra experience: " + xp);
			helper.succeed();
		});
	}
}
