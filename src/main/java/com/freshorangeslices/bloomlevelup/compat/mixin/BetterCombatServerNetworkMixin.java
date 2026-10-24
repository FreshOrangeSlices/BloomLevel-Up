package com.freshorangeslices.bloomlevelup.compat.mixin;

import net.bettercombat.network.Packets;
import net.bettercombat.network.ServerNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.freshorangeslices.bloomlevelup.compat.BetterCombat;

/**
 * Better Combat's server handler for a swing (it runs the whole swing at once, on the server thread): marks the
 * player as mid-swing and notes how many mobs the swing hits before any of them is attacked, so Full Sweep and
 * Batter Up! can cancel its crowd penalty; clears it all at the end. Only applied when Better Combat is installed.
 */
@Mixin(value = ServerNetwork.class, remap = false)
abstract class BetterCombatServerNetworkMixin {
	@Inject(method = "handleAttackRequest", at = @At("HEAD"))
	private static void bloomlevelup$noteTargets(Packets.C2S_AttackRequest request, MinecraftServer server, ServerPlayer player,
			ServerGamePacketListenerImpl handler, CallbackInfo ci) {
		if (request != null && request.entityIds() != null) {
			BetterCombat.request(player, request.entityIds().length);
		}
	}

	@Inject(method = "handleAttackRequest", at = @At("RETURN"))
	private static void bloomlevelup$swingOver(Packets.C2S_AttackRequest request, MinecraftServer server, ServerPlayer player,
			ServerGamePacketListenerImpl handler, CallbackInfo ci) {
		BetterCombat.endRequest(player);
	}
}
