package com.freshorangeslices.bloompets.gametest;

import com.freshorangeslices.bloompets.BloomPets;
import com.freshorangeslices.bloompets.PetBonuses;
import com.freshorangeslices.bloompets.PetEvents;
import com.freshorangeslices.bloompets.PetLevels;
import com.freshorangeslices.bloompets.PetMood;
import com.freshorangeslices.bloompets.PetRegistry;
import com.freshorangeslices.bloompets.Pets;
import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Bloom Pets, Phase 3b: who gives their owner a bonus, and some of the bonuses. */
@GameTestHolder(BloomPets.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PetBonusGameTests {
	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pets-test"));
		BlockPos abs = helper.absolutePos(at);
		player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static <T extends Mob> T pet(GameTestHelper helper, EntityType<T> type, ServerPlayer owner, BlockPos at, int level, boolean party) {
		T mob = helper.spawn(type, at);
		mob.setNoAi(true);
		Pets.claim(mob, owner);
		PetRegistry.Record record = PetEvents.observe(mob);

		if (party) {
			record.party = true;
			Pets.setParty(mob, true);
		}

		PetLevels.setLevel(mob, level);
		return mob;
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0E-3;
	}

	private static long now(GameTestHelper helper) {
		return helper.getLevel().getServer().overworld().getGameTime();
	}

	@GameTest(template = "empty")
	public static void theBonusGrowsWithLevel(GameTestHelper helper) {
		helper.assertTrue(near(PetBonuses.power(1), 1.0) && near(PetBonuses.power(100), 1.45), "1 at level 1, 1.45 at 100");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void thePartyWolfGivesMeleeDamageWhileItsThere(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		Wolf wolf = pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 1), 1, true);
		double base = owner.getAttributeValue(Attributes.ATTACK_DAMAGE);
		PetBonuses.apply(owner, PetBonuses.gather(owner), now(helper));
		helper.assertTrue(near(owner.getAttributeValue(Attributes.ATTACK_DAMAGE), base * 1.08), "+8% melee damage");
		Pets.setParty(wolf, false);
		PetBonuses.apply(owner, PetBonuses.gather(owner), now(helper));
		helper.assertTrue(near(owner.getAttributeValue(Attributes.ATTACK_DAMAGE), base), "Gone once it leaves the party (a combat pet)");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void threeNearbySupportPetsWorkOutOfTheParty(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(1, 1, 1));
		pet(helper, EntityType.COW, owner, new BlockPos(2, 2, 1), 1, false);
		pet(helper, EntityType.RABBIT, owner, new BlockPos(3, 2, 1), 1, false);
		pet(helper, EntityType.CHICKEN, owner, new BlockPos(4, 2, 1), 1, false);
		pet(helper, EntityType.BAT, owner, new BlockPos(6, 2, 1), 1, false);
		pet(helper, EntityType.WOLF, owner, new BlockPos(1, 2, 3), 1, false);
		Set<String> kinds = PetBonuses.gather(owner).keySet();
		helper.assertTrue(kinds.equals(Set.of("cow", "rabbit", "chicken")), "The 3 nearest non-combat pets (not the 4th, not a wolf at home): " + kinds);
		owner.setPos(owner.getX() + 40, owner.getY(), owner.getZ());
		helper.assertTrue(PetBonuses.gather(owner).isEmpty(), "None from 40 blocks away");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void oneOfEachKindCountsAndASadPetGivesNothing(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(1, 1, 1));
		pet(helper, EntityType.DONKEY, owner, new BlockPos(2, 2, 1), 1, false);
		pet(helper, EntityType.DONKEY, owner, new BlockPos(4, 2, 1), 50, false);
		Map<String, PetBonuses.Bonus> bonuses = PetBonuses.gather(owner);
		helper.assertTrue(bonuses.size() == 1 && bonuses.get("donkey").level() == 50, "Two donkeys: the stronger one's bonus, once");
		double base = owner.getMaxHealth();
		PetBonuses.apply(owner, bonuses, now(helper));
		helper.assertTrue(near(owner.getMaxHealth(), base + 4.0 * PetBonuses.power(50)), "+2 hearts, grown with its level: " + owner.getMaxHealth());
		PetMood.setMood(bonuses.get("donkey").pet(), 10.0);
		helper.assertTrue(PetBonuses.gather(owner).get("donkey").level() == 1, "A sulking donkey gives nothing (the other one's left)");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theGoatSoftensFalls(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(4, 1, 4));
		pet(helper, EntityType.GOAT, owner, new BlockPos(1, 2, 1), 1, true);
		PetBonuses.apply(owner, PetBonuses.gather(owner), now(helper));
		LivingHurtEvent fall = new LivingHurtEvent(owner, helper.getLevel().damageSources().fall(), 10.0F);
		PetBonuses.onHurt(fall);
		helper.assertTrue(near(fall.getAmount(), 6.0), "40% less fall damage: " + fall.getAmount());
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theBeeAndPigTurnOnBloomsGrowthAuraAndTimber(GameTestHelper helper) {
		ServerPlayer owner = player(helper, new BlockPos(1, 1, 1));
		pet(helper, EntityType.BEE, owner, new BlockPos(2, 2, 1), 100, false);
		pet(helper, EntityType.PIG, owner, new BlockPos(3, 2, 1), 1, false);
		PetBonuses.apply(owner, PetBonuses.gather(owner), now(helper));
		CompoundTag kept = owner.getPersistentData().getCompound("bloompets");
		helper.assertTrue(near(kept.getDouble("growth"), 1.45) && kept.getLong("growthUntil") > now(helper), "The Bee's Growth Aura, at its strength");
		helper.assertTrue(kept.getLong("timberUntil") > now(helper), "The Pig's Timber");
		helper.succeed();
	}
}
