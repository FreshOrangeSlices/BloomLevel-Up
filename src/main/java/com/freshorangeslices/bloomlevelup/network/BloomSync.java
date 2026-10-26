package com.freshorangeslices.bloomlevelup.network;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.player.PlayerXpWallet;
import com.freshorangeslices.bloomlevelup.progression.ActionResult;
import com.freshorangeslices.bloomlevelup.progression.ProgressionService;
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
	private static final Map<UUID, BloomPayloads.NameTag> LAST_TAG = new HashMap<>();
	private static int tickCounter;

	private BloomSync() {
	}

	public static void init() {
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (!(event.getEntity() instanceof ServerPlayer player)) {
				return;
			}

			sendDefinitions(player);
			changed(player);

			// Nameplates: the newcomer learns everyone's tag (everyone learns theirs when their profile is sent).
			for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
				if (other != player) {
					sendNameTag(player, other);
				}
			}
		});
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> LAST_TAG.remove(event.getEntity().getUUID()));

		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
			if (!(event.getEntity() instanceof ServerPlayer newPlayer)) {
				return;
			}

			StatEffects.refresh(newPlayer);

			// A respawn after death (not the walk back from the End): start on full health, extra hearts included.
			if (!event.isEndConquered()) {
				newPlayer.setHealth(newPlayer.getMaxHealth());
			}

			changed(newPlayer);
		});

		// After /reload (no player: everyone); on login the PlayerLoggedInEvent above sends them.
		MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
			if (event.getPlayer() == null) {
				for (ServerPlayer player : event.getPlayerList().getPlayers()) {
					sendDefinitions(player);
					changed(player);
				}
			}
		});

		MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
			DIRTY.clear();
			LAST_TAG.clear();
		});
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				tick(event.getServer());
			}
		});
	}

	/** Sends the definitions and config to everyone again (after an admin changes the config in game). */
	public static void resendDefinitions(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendDefinitions(player);
		}
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
					broadcastNameTag(server, player);
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

	private static BloomPayloads.NameTag nameTag(ServerPlayer player) {
		BloomProfile profile = BloomAttachments.profile(player);
		return new BloomPayloads.NameTag(player.getUUID(), profile.spentMajorLevels(), profile.anyMastered());
	}

	private static void sendNameTag(ServerPlayer to, ServerPlayer about) {
		BloomNetwork.send(to, nameTag(about));
	}

	/** Tells everyone (the player included) about a changed nameplate tag; unchanged tags aren't re-sent. */
	private static void broadcastNameTag(MinecraftServer server, ServerPlayer player) {
		BloomPayloads.NameTag tag = nameTag(player);

		if (tag.equals(LAST_TAG.put(player.getUUID(), tag))) {
			return;
		}

		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			BloomNetwork.send(other, tag);
		}
	}

	private static void sendDefinitions(ServerPlayer player) {
		String json = SnapshotCodecs.encodeDefinitions(BloomServices.definitions(), BloomServices.config());
		BloomNetwork.send(player, new BloomPayloads.SyncDefinitions(json));
	}

	private static void sendProfile(ServerPlayer player) {
		String json = SnapshotCodecs.encodeProfile(BloomAttachments.profile(player));
		BloomNetwork.send(player, new BloomPayloads.SyncProfile(json));
	}

	static void handle(ServerPlayer player, BloomPayloads.Request request) {
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
				case TOGGLE_INVESTED -> service.toggleInvested(profile, target);
				case BUY_MAJOR -> service.purchaseMajor(profile, target);
				case BUY_MAJOR_SWAP -> service.purchaseMajorWithSwap(profile, target);
				case REFUND_MAJOR -> BloomServices.config().allowMajorRefund ? service.refundMajor(profile, target)
						: ActionResult.of(ActionResult.Outcome.REFUND_DISABLED);
				case TOGGLE_MAJOR -> service.toggleMajor(profile, target);
				case FUND -> service.fundWithVanillaXp(profile, target, amount, new PlayerXpWallet(player));
				case INVEST -> service.invest(profile, target, amount, new PlayerXpWallet(player));
				case SET_COPY_SOURCE -> service.selectCopySource(profile, CombatIds.COPYCAT, target);
				case REFUND_MAJOR_ALL -> BloomServices.config().allowMajorRefund ? service.refundMajorWithDependents(profile, target)
						: ActionResult.of(ActionResult.Outcome.REFUND_DISABLED);
				case RESET_MAJORS -> service.resetCategoryMajors(profile, target, player.level().getGameTime());
				case BLOOM_RESET -> {
					com.freshorangeslices.bloomlevelup.progression.BloomResetResult reset = service.bloomResetCategory(profile, target);
					player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("bloom.command.reset.done", reset.bloomCount(),
							reset.majorLevelsRefunded(), reset.totalPrestigeAwarded()).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
					yield ActionResult.success();
				}
				case SWITCH_BUILD -> service.switchBuild(profile, player.level().getGameTime());
			};
		} catch (RuntimeException e) {
			BloomLevelUp.LOGGER.error("Bloom request {} from {} failed", request, player.getName().getString(), e);
			return;
		}

		if (result.ok()) {
			changed(player);
		}

		BloomNetwork.send(player, new BloomPayloads.Result(request.action(), target, result.outcome().name(), result.involved()));
	}
}
