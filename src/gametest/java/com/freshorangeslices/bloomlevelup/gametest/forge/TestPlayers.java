package com.freshorangeslices.bloomlevelup.gametest.forge;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;

/**
 * Fake players for the game tests: survival-mode server players that never tick. Forge's own fake player is immune
 * to all damage, which the Armor and Combat tests need to go through, so this one isn't.
 */
public final class TestPlayers {
	private TestPlayers() {
	}

	/** A fake player that takes damage like a real one. */
	public static final class Vulnerable extends FakePlayer {
		Vulnerable(ServerLevel level, GameProfile profile) {
			super(level, profile);
		}

		@Override
		public boolean isInvulnerableTo(DamageSource source) {
			return false;
		}

		@Override
		public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) {
			return true;
		}
	}

	/** A new player standing at (4, 1, 4) of the test, holding {@code mainHand}. */
	public static ServerPlayer player(GameTestHelper helper, ItemStack mainHand) {
		ServerPlayer player = new Vulnerable(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bloom-test"));
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
		return player;
	}

	/** Max out the given categories and buy Majors. */
	public static void grant(ServerPlayer player, List<String> categories, String... majors) {
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);

		for (String category : categories) {
			service.addXp(profile, category, 1.0e9);
		}

		for (String major : majors) {
			ActionResult result = service.purchaseMajor(profile, major);

			if (!result.ok()) {
				throw new IllegalStateException("Couldn't buy " + major + ": " + result.outcome());
			}
		}
	}

	public static void points(ServerPlayer player, String stat, int amount) {
		ActionResult result = BloomServices.progression().allocate(BloomAttachments.profile(player), stat, ProgressionService.Pool.NATURAL, amount);

		if (!result.ok()) {
			throw new IllegalStateException("Couldn't allocate " + stat + ": " + result.outcome());
		}
	}
}
