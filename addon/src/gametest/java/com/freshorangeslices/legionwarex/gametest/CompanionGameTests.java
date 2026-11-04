package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.companion.Amigos;
import com.freshorangeslices.legionwarex.companion.Companions;
import com.freshorangeslices.legionwarex.companion.RaidEnemigos;
import com.freshorangeslices.legionwarex.companion.StayPutGoal;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

/** Companions with Migamigos loaded: owners can't hurt their own, "Cannot Wander" keeps a staying one put, raid Enemigos. */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompanionGameTests {
	private static final ResourceLocation CLAYMORE = new ResourceLocation("migamigos", "claymore");

	@SuppressWarnings("unchecked")
	private static Mob claymore(GameTestHelper helper, BlockPos pos, UUID owner, CompoundTag extra) {
		helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(CLAYMORE), "Migamigos is loaded in the dev run");
		Mob mob = helper.spawn((EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(CLAYMORE), pos);
		CompoundTag data = mob.saveWithoutId(new CompoundTag());

		if (owner != null) {
			data.putUUID("FollowingPlayerUUID", owner);
		}

		data.merge(extra);
		mob.load(data);
		return mob;
	}

	@GameTest(template = "empty")
	public static void ownersCantHurtTheirOwnCompanionsOrPets(GameTestHelper helper) {
		Player owner = helper.makeMockPlayer();
		Player stranger = helper.makeMockPlayer();
		Mob amigo = claymore(helper, new BlockPos(1, 2, 1), owner.getUUID(), new CompoundTag());
		amigo.setNoAi(true);
		float full = amigo.getHealth();

		helper.assertFalse(amigo.hurt(helper.getLevel().damageSources().playerAttack(owner), 4.0F), "The owner's hit doesn't land");
		helper.assertTrue(amigo.getHealth() == full, "Not hurt by its owner");
		helper.assertTrue(amigo.getLastHurtByMob() == null, "It doesn't count its owner as having hurt it");

		Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(4, 2, 4));
		wolf.setNoAi(true);
		wolf.tame(owner);
		float wolfFull = wolf.getHealth();
		helper.assertFalse(wolf.hurt(helper.getLevel().damageSources().playerAttack(owner), 4.0F), "Nor on the owner's pet");
		helper.assertFalse(amigo.hurt(helper.getLevel().damageSources().mobAttack(wolf), 4.0F), "Nor the owner's pet's bite on its companion");
		helper.assertTrue(wolf.getHealth() == wolfFull && amigo.getHealth() == full, "Neither was hurt");

		helper.assertTrue(amigo.hurt(helper.getLevel().damageSources().playerAttack(stranger), 4.0F), "Someone else's hit lands");
		helper.assertTrue(amigo.getHealth() < full, "Hurt by a stranger");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 260)
	public static void aStayingCompanionWithWanderingOffStaysPut(GameTestHelper helper) {
		BlockPos at = new BlockPos(3, 0, 3);
		Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(at));
		CompoundTag stay = new CompoundTag();
		stay.putBoolean("Following", false);
		stay.putBoolean("AllowWandering", false);
		stay.putFloat("PostPosX", (float) spot.x);
		stay.putFloat("PostPosY", (float) spot.y);
		stay.putFloat("PostPosZ", (float) spot.z);
		Mob amigo = claymore(helper, at, UUID.randomUUID(), stay);

		helper.assertFalse(Amigos.following(amigo), "Migamigos reads it as staying");
		helper.assertFalse(Amigos.canWander(amigo), "and not allowed to wander");
		Vector3f post = Amigos.post(amigo);
		helper.assertTrue(post != null && Math.abs(post.x - spot.x) < 0.01, "with its spot set: " + post);
		helper.assertTrue(amigo.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof StayPutGoal),
				"It has the add-on's stay-put goal");
		helper.assertTrue(amigo.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof RaidEnemigos.MarchGoal),
				"and the raid march goal (idle unless it's a wild Enemigo near a raid)");

		// Migamigos alone would walk it about its spot (up to 10 blocks out); it should stay within a step or two.
		for (int tick = 40; tick <= 240; tick += 40) {
			helper.runAtTickTime(tick, () -> {
				double off = Math.sqrt(Math.pow(amigo.getX() - spot.x, 2) + Math.pow(amigo.getZ() - spot.z, 2));
				helper.assertTrue(off <= 2.0, "It stays at its spot, but was " + String.format("%.1f", off) + " blocks off");
			});
		}

		helper.runAtTickTime(250, helper::succeed);
	}

	@GameTest(template = "empty")
	public static void aWildEnemigoWithNoRaidNearIsAStrayAsBefore(GameTestHelper helper) {
		CompoundTag enemigo = new CompoundTag();
		enemigo.putBoolean("IsEnemigo", true);
		Mob amigo = claymore(helper, new BlockPos(2, 2, 2), null, enemigo);
		helper.assertTrue(Companions.isEnemigo(amigo), "An Enemigo");
		helper.assertTrue(Companions.isWild(amigo), "with no owner: wild");
		helper.assertTrue(RaidEnemigos.raid(amigo) == null, "No raid near, so it despawns like any stray");
		helper.succeed();
	}
}
