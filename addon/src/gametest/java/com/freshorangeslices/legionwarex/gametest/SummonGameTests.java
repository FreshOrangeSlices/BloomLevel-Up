package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.companion.OwnerHits;
import com.freshorangeslices.legionwarex.summon.PirateSaber;
import com.freshorangeslices.legionwarex.summon.Summons;
import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bosses'Rise's Pirate Saber and the summon rules, with Bosses'Rise in the add-on's test run (FreshOrangeSlices,
 * 2026-11-22: its two pirates stayed for good, couldn't be hurt, and came at their owner). A stand-in player finishes
 * the saber's charge as the game does (the item's own finish, then Forge's event); each test has its own stand-in, so
 * tests running side by side never share an owner.
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SummonGameTests {
	private static Item saber(GameTestHelper helper) {
		Item item = ForgeRegistries.ITEMS.getValue(PirateSaber.ID);

		// The registry hands back air for an id it doesn't have.
		if (item == null || !PirateSaber.ID.equals(ForgeRegistries.ITEMS.getKey(item))) {
			throw new IllegalStateException("Bosses'Rise's Pirate Saber isn't in the test run");
		}

		return item;
	}

	private static FakePlayer stand(GameTestHelper helper, String name) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}

		FakePlayer stand = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("legionwarex:" + name).getBytes()), "[" + name + "]"));
		Vec3 at = helper.absoluteVec(new Vec3(4.0, 1.0, 4.0));
		stand.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
		return stand;
	}

	/** One charge, finished: the saber's own finish (the summons), then Forge's finish event (the add-on's cooldown). */
	private static List<Mob> charge(GameTestHelper helper, FakePlayer stand, Item saber) {
		ServerLevel level = helper.getLevel();
		Set<UUID> before = Summons.saberSummons(level, stand.getUUID()).stream().map(Entity::getUUID).collect(Collectors.toSet());
		ItemStack stack = new ItemStack(saber);
		stand.setItemInHand(InteractionHand.MAIN_HAND, stack);
		ItemStack used = stack.copy();
		ForgeEventFactory.onItemUseFinish(stand, used, 0, stack.finishUsingItem(level, stand));
		return Summons.saberSummons(level, stand.getUUID()).stream().filter(mob -> !before.contains(mob.getUUID())).toList();
	}

	private static String id(Entity entity) {
		return String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
	}

	@GameTest(template = "empty", timeoutTicks = 200)
	public static void theSabersPairLeavesAfterItsTimeAndDropsNothing(GameTestHelper helper) {
		Item saber = saber(helper);
		FakePlayer stand = stand(helper, "Saber test");
		List<Mob> pair = charge(helper, stand, saber);
		helper.assertTrue(pair.size() == 2, "A charge summons two: " + pair.stream().map(SummonGameTests::id).toList());
		helper.assertTrue(pair.stream().map(SummonGameTests::id).collect(Collectors.toSet())
				.equals(Set.of("block_factorys_bosses:pirate_rook", "block_factorys_bosses:crossbow_pirate")), "A Rook and a Crossbow Pirate");

		for (Mob mob : pair) {
			helper.assertTrue(stand.getUUID().equals(Summons.summoner(mob)), id(mob) + " is the stand-in's");
			long left = Summons.ticksLeft(mob);
			helper.assertTrue(left > AddonConfig.summonLifetime() - 20 && left <= AddonConfig.summonLifetime(), id(mob) + " has its time: " + left);
			ListTag chances = mob.saveWithoutId(new CompoundTag()).getList("HandDropChances", Tag.TAG_FLOAT);

			for (int i = 0; i < chances.size(); i++) {
				helper.assertTrue(chances.getFloat(i) == 0.0F, id(mob) + " drops nothing it holds: " + chances);
			}
		}

		// The saber's own wait is 80 ticks: 100 ticks on, the add-on's 200 still holds it.
		for (int i = 0; i < 100; i++) {
			stand.getCooldowns().tick();
		}

		helper.assertTrue(stand.getCooldowns().isOnCooldown(saber), "Still waiting 100 ticks after a summon (10 seconds in all)");

		// Their time up: gone within a second.
		long now = helper.getLevel().getGameTime();
		pair.forEach(mob -> mob.getPersistentData().getCompound(Summons.DATA).putLong("until", now));
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(pair.stream().allMatch(Entity::isRemoved), "Both left once their time was up");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aNewChargeSendsTheOldPairAway(GameTestHelper helper) {
		Item saber = saber(helper);
		FakePlayer stand = stand(helper, "Charges test");
		List<Mob> first = charge(helper, stand, saber);
		List<Mob> second = charge(helper, stand, saber);
		List<Mob> third = charge(helper, stand, saber);
		helper.assertTrue(first.size() == 2 && second.size() == 2 && third.size() == 2, "Each charge summoned two");
		helper.runAfterDelay(3, () -> {
			List<Mob> left = Summons.saberSummons(helper.getLevel(), stand.getUUID());
			Set<UUID> kept = left.stream().map(Entity::getUUID).collect(Collectors.toSet());
			Set<UUID> newest = new HashSet<>(third.stream().map(Entity::getUUID).toList());

			try {
				helper.assertTrue(kept.equals(newest), "Only the newest pair is left: " + left.stream().map(SummonGameTests::id).toList());
				helper.assertTrue(first.stream().allMatch(Entity::isRemoved) && second.stream().allMatch(Entity::isRemoved), "The older ones left");
			} finally {
				left.forEach(Entity::discard);
			}

			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void summonsNeverTurnOnPlayersOrTheirOwnersSide(GameTestHelper helper) {
		Item saber = saber(helper);
		FakePlayer stand = stand(helper, "Side test");
		FakePlayer friend = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes("legionwarex:friend".getBytes()), "[Friend]"));
		List<Mob> pair = charge(helper, stand, saber);
		Mob rook = pair.stream().filter(mob -> id(mob).endsWith("pirate_rook")).findFirst().orElseThrow();
		Mob shooter = pair.stream().filter(mob -> id(mob).endsWith("crossbow_pirate")).findFirst().orElseThrow();
		Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(2, 1, 2));
		wolf.setTame(true);
		wolf.setOwnerUUID(stand.getUUID());
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 1, 6));

		try {
			rook.setTarget(stand);
			helper.assertTrue(rook.getTarget() == null, "The Rook can't pick its owner");
			rook.setTarget(friend);
			helper.assertTrue(rook.getTarget() == null, "Nor another player");
			rook.setTarget(wolf);
			helper.assertTrue(rook.getTarget() == null, "Nor its owner's pet");
			wolf.setTarget(rook);
			helper.assertTrue(wolf.getTarget() == null, "The owner's pet can't pick the owner's summon");
			shooter.setTarget(rook);
			helper.assertTrue(shooter.getTarget() == null, "Nor one summon the other");
			rook.setTarget(zombie);
			helper.assertTrue(rook.getTarget() == zombie, "A zombie it may");

			helper.assertTrue("its owner".equals(OwnerHits.refusal(stand, rook)), "The Rook's hits never land on its owner");
			helper.assertTrue("a player".equals(OwnerHits.refusal(friend, shooter)), "Nor the Crossbow Pirate's on another player");
			helper.assertTrue("its own side".equals(OwnerHits.refusal(wolf, rook)), "Nor on the owner's pet");
			helper.assertTrue(OwnerHits.refusal(zombie, rook) == null, "On a zombie they do");
			// Through the game's own path: a player's hit is asked of Forge's attack event first.
			helper.assertFalse(ForgeHooks.onPlayerAttack(stand, helper.getLevel().damageSources().mobAttack(rook), 5.0F), "The Rook's swing at its owner is called off");
			helper.assertFalse(ForgeHooks.onPlayerAttack(friend, helper.getLevel().damageSources().mobAttack(shooter), 5.0F), "The Crossbow Pirate's at a friend too");
			helper.assertTrue(ForgeHooks.onPlayerAttack(stand, helper.getLevel().damageSources().mobAttack(zombie), 5.0F), "A zombie's lands");
			float health = wolf.getHealth();
			rook.doHurtTarget(wolf);
			helper.assertTrue(wolf.getHealth() == health, "The Rook's real swing at the owner's wolf did nothing");
		} finally {
			pair.forEach(Entity::discard);
			wolf.discard();
			zombie.discard();
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theSaberHitsForNine(GameTestHelper helper) {
		Item saber = saber(helper);
		double weapon = new ItemStack(saber).getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream()
				.filter(modifier -> modifier.getOperation() == AttributeModifier.Operation.ADDITION).mapToDouble(AttributeModifier::getAmount).sum();
		// The player's own 1 plus the weapon's.
		helper.assertTrue(Math.abs(1.0 + weapon - AddonConfig.pirateSaberDamage()) < 1.0E-6, "Shown as " + AddonConfig.pirateSaberDamage()
				+ ": the weapon adds " + weapon);
		helper.succeed();
	}
}
