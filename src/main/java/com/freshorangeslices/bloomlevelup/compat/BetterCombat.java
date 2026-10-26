package com.freshorangeslices.bloomlevelup.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

/**
 * Better Combat compatibility, Forge 1.20.1 build. The Fabric build's compat (crowd-penalty counter, Batter Up!
 * swings, the charged slam) is ported against Better Combat's 1.20.1 version in its own chunk; until then Bloom
 * behaves the same with or without Better Combat installed: every hit Better Combat makes still goes through
 * {@code Player.attack}, so it is a normal hit for Bloom.
 */
public final class BetterCombat {
	public static final String MOD_ID = "bettercombat";

	private BetterCombat() {
	}

	/** Whether Better Combat is present at all (for logs and the hub's notes). */
	public static boolean present() {
		return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
	}

	/** Whether Bloom's Better Combat compat is running. Not yet in this build (see the class notes). */
	public static boolean installed() {
		return false;
	}

	public static void init() {
	}

	/** Start of every {@code Player.attack} (the crowd-penalty counter, once the compat is ported). */
	public static void beforeAttack(ServerPlayer player) {
	}

	/** Whether Better Combat is in the middle of this player's swing. */
	public static boolean inSwing(Player player) {
		return false;
	}

	/** A charged swing's landing under Better Combat (once the compat is ported). */
	public static void slamShockwave(ServerLevel level, LivingEntity target) {
	}
}
