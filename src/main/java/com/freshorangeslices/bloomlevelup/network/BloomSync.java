package com.freshorangeslices.bloomlevelup.network;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.PlayerXpWallet;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatIds;

/**
 * Keeps clients in step with the server. Anything that changes a profile calls {@link #changed}; once per
 * tick each changed player gets their effects re-applied and one profile snapshot. Definitions go out on
 * join and after {@code /reload}. Requests from the hub are validated here through {@link ProgressionService}.
 */
public final class BloomSync {
	/** Effects that depend on worn equipment (Titan Hide) are re-checked this often, in ticks. */
	private static final int EFFECT_REFRESH_INTERVAL = 10;
	private static final int MAX_AMOUNT = 100_000;

	private static final Set<UUID> DIRTY = new LinkedHashSet<>();
	private static int tickCounter;

	private BloomSync() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(BloomPayloads.SyncDefinitions.TYPE, BloomPayloads.SyncDefinitions.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BloomPayloads.SyncProfile.TYPE, BloomPayloads.SyncProfile.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BloomPayloads.Result.TYPE, BloomPayloads.Result.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BloomPayloads.Request.TYPE, BloomPayloads.Request.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BloomPayloads.CombatInput.TYPE, BloomPayloads.CombatInput.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BloomPayloads.Request.TYPE, (payload, context) -> handle(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(BloomPayloads.CombatInput.TYPE,
				(payload, context) -> CombatAbilities.handle(context.player(), payload.action()));

		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			ServerPlayer player = listener.player;
			sendDefinitions(player);
			changed(player);
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			StatEffects.refresh(newPlayer);

			if (!alive) {
				newPlayer.setHealth(newPlayer.getMaxHealth());
			}

			changed(newPlayer);
		});

		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				sendDefinitions(player);
				changed(player);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DIRTY.clear());
		ServerTickEvents.END_SERVER_TICK.register(BloomSync::tick);
	}

	/** Marks a player's profile as changed: effects are re-applied and a snapshot is sent at the end of this tick. */
	public static void changed(ServerPlayer player) {
		DIRTY.add(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		if (!DIRTY.isEmpty()) {
			for (UUID id : DIRTY) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);

				if (player != null) {
					StatEffects.refresh(player);
					sendProfile(player);
				}
			}

			DIRTY.clear();
		}

		if (++tickCounter % EFFECT_REFRESH_INTERVAL == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				StatEffects.refresh(player);
			}
		}
	}

	private static void sendDefinitions(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, BloomPayloads.SyncDefinitions.TYPE)) {
			String json = SnapshotCodecs.encodeDefinitions(BloomServices.definitions(), BloomServices.config());
			ServerPlayNetworking.send(player, new BloomPayloads.SyncDefinitions(json));
		}
	}

	private static void sendProfile(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, BloomPayloads.SyncProfile.TYPE)) {
			String json = SnapshotCodecs.encodeProfile(BloomAttachments.profile(player));
			ServerPlayNetworking.send(player, new BloomPayloads.SyncProfile(json));
		}
	}

	private static void handle(ServerPlayer player, BloomPayloads.Request request) {
		ProgressionService service = BloomServices.progression();
		BloomProfile profile = BloomAttachments.profile(player);
		String target = request.target();
		int amount = Math.max(-MAX_AMOUNT, Math.min(MAX_AMOUNT, request.amount()));
		ActionResult result;

		try {
			result = switch (request.action()) {
				case ALLOCATE_NATURAL -> service.allocate(profile, target, ProgressionService.Pool.NATURAL, amount);
				case ALLOCATE_INVESTED -> service.allocate(profile, target, ProgressionService.Pool.INVESTED, amount);
				case TOGGLE_STAT -> service.toggleStat(profile, target);
				case BUY_MAJOR -> service.purchaseMajor(profile, target);
				case BUY_MAJOR_SWAP -> service.purchaseMajorWithSwap(profile, target);
				case REFUND_MAJOR -> service.refundMajor(profile, target);
				case TOGGLE_MAJOR -> service.toggleMajor(profile, target);
				case FUND -> service.fundWithVanillaXp(profile, target, amount, new PlayerXpWallet(player));
				case INVEST -> service.invest(profile, target, amount, new PlayerXpWallet(player));
				case SET_COPY_SOURCE -> service.selectCopySource(profile, CombatIds.COPYCAT, target);
			};
		} catch (RuntimeException e) {
			BloomLevelUp.LOGGER.error("Bloom request {} from {} failed", request, player.getName().getString(), e);
			return;
		}

		if (result.ok()) {
			changed(player);
		}

		if (ServerPlayNetworking.canSend(player, BloomPayloads.Result.TYPE)) {
			ServerPlayNetworking.send(player, new BloomPayloads.Result(request.action(), target, result.outcome().name(), result.involved()));
		}
	}
}
