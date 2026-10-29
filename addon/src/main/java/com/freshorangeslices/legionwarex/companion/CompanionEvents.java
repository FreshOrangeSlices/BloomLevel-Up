package com.freshorangeslices.legionwarex.companion;

import com.freshorangeslices.legionwarex.AddonConfig;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** A befriended companion's death leaves its owner a keepsake; wild companions despawn like monsters. */
public final class CompanionEvents {
	/** Vanilla monsters' despawn distances, in blocks: at once past the first, now and then past the second. */
	public static final int FAR = 128;
	public static final int NEAR = 32;

	private CompanionEvents() {
	}

	// Lowest, so a totem or another mod that cancels the death has had its say.
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();

		if (event.isCanceled() || !(entity.level() instanceof ServerLevel level) || !Companions.isCompanion(entity)) {
			return;
		}

		UUID owner = Companions.owner(entity);

		if (owner != null) {
			ItemStack keepsake = Keepsakes.of(entity, owner, level.getGameTime() + AddonConfig.reviveWait());
			deliver(level.getServer(), owner, keepsake);
		}
	}

	/** To the owner now, or when they next join. */
	public static void deliver(MinecraftServer server, UUID owner, ItemStack keepsake) {
		ServerPlayer player = server.getPlayerList().getPlayer(owner);

		if (player == null) {
			PendingKeepsakes.get(server).add(owner, keepsake);
		} else {
			give(player, keepsake);
		}
	}

	private static void give(ServerPlayer player, ItemStack keepsake) {
		Component name = Keepsakes.name(keepsake);
		String wait = Keepsakes.time(Keepsakes.ticksLeft(player.level(), keepsake));

		if (!player.getInventory().add(keepsake)) {
			// A full inventory: at their feet, and it never despawns.
			ItemEntity dropped = player.drop(keepsake, false);

			if (dropped != null) {
				dropped.setUnlimitedLifetime();
				dropped.setNoPickUpDelay();
			}
		}

		player.sendSystemMessage(Component.translatable("message.legionwarex.keepsake_given", name, wait).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			PendingKeepsakes.get(player.server).take(player.getUUID()).forEach(stack -> give(player, stack));
		}
	}

	@SubscribeEvent
	public static void onTick(LivingEvent.LivingTickEvent event) {
		if (!(event.getEntity() instanceof Mob mob) || mob.tickCount % 20 != 0 || mob.level().isClientSide
				|| !AddonConfig.wildDespawn() || !Companions.isWild(mob)) {
			return;
		}

		Player nearest = mob.level().getNearestPlayer(mob, -1.0);

		if (nearest == null) {
			return;
		}

		double distance = nearest.distanceToSqr(mob);

		if (despawns(distance, mob.getNoActionTime(), mob.getRandom())) {
			mob.discard();
		} else if (distance < NEAR * NEAR) {
			mob.setNoActionTime(0);
		}
	}

	/**
	 * Vanilla's rule for a monster (Mob.checkDespawn), checked once a second instead of every tick: gone at once with
	 * no player within 128 blocks; past 32, after 30 seconds without a player close, a 1 in 40 chance a second (1 in
	 * 800 a tick).
	 */
	public static boolean despawns(double distanceSqr, int noActionTicks, RandomSource random) {
		if (distanceSqr > FAR * FAR) {
			return true;
		}

		return distanceSqr > NEAR * NEAR && noActionTicks > 600 && random.nextInt(40) == 0;
	}
}
