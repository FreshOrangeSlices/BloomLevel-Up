package com.freshorangeslices.legionwarex.world;

import com.freshorangeslices.legionwarex.AddonConfig;
import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Milk clears Bad Omen again (FreshOrangeSlices, 2026-11-20: "milk didn't work"; something in the pack stops it, and
 * neither the add-on, Bloom nor Bloom Pets touch either). When anyone finishes drinking milk, a Bad Omen still on them
 * comes off; if a mod refuses the removal, it's taken off directly and the player's game told. At server start a
 * stand-in player tries it, logged as "LegionWareX milk:" (whether plain milk worked, whether milk is still listed as Bad
 * Omen's cure, whether a mod refused a plain removal, and whether the add-on's cure worked), and the first few times a
 * real player's Bad Omen outlasts their milk are logged too; the pack test reads both.
 * Config {@code milkCuresBadOmen}.
 */
public final class MilkCures {
	private MilkCures() {
	}

	/** How many times the add-on has said it had to cure, this run (the first few are logged). */
	private static final AtomicInteger CURED = new AtomicInteger();

	/** Runs last, after the milk's own cure and every other mod: a Bad Omen still on here is one plain milk didn't take. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDrink(LivingEntityUseItemEvent.Finish event) {
		LivingEntity entity = event.getEntity();

		if (AddonConfig.milkCuresBadOmen() && !entity.level().isClientSide && event.getItem().is(Items.MILK_BUCKET) && entity.hasEffect(MobEffects.BAD_OMEN)) {
			boolean refused = !entity.removeEffect(MobEffects.BAD_OMEN);

			if (refused) {
				force(entity);
			}

			if (entity instanceof ServerPlayer && !(entity instanceof FakePlayer) && CURED.getAndIncrement() < 5) {
				LegionWareX.LOGGER.info("LegionWareX milk: {} drank milk and Bad Omen was still on after it: the add-on took it off{}",
						entity.getName().getString(), refused ? " (a mod refused the removal)" : "");
			}
		}
	}

	/** A refused removal (MobEffectEvent.Remove cancelled): off the list directly, and the player's screen told, as
	 * ServerPlayer.onEffectRemoved would (Bad Omen has no attribute modifiers to take back). */
	private static void force(LivingEntity entity) {
		MobEffectInstance gone = entity.removeEffectNoUpdate(MobEffects.BAD_OMEN);

		if (gone != null && entity instanceof ServerPlayer player && player.connection != null) {
			player.connection.send(new ClientboundRemoveMobEffectPacket(player.getId(), MobEffects.BAD_OMEN));
		}
	}

	/** The stand-in's try, logged for the pack test. */
	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		try {
			ServerLevel level = event.getServer().overworld();
			FakePlayer stand = FakePlayerFactory.getMinecraft(level);
			boolean listed = new MobEffectInstance(MobEffects.BAD_OMEN, 600).getCurativeItems().stream().anyMatch(stack -> stack.is(Items.MILK_BUCKET));

			if (!omen(stand)) {
				LegionWareX.LOGGER.info("LegionWareX milk: the stand-in couldn't be given Bad Omen (milk listed as its cure: {})", yes(listed));
				return;
			}

			// Plain milk, as the game drinks it (the bucket cures in finishUsingItem, before the finish event).
			new ItemStack(Items.MILK_BUCKET).finishUsingItem(level, stand);
			boolean plain = !stand.hasEffect(MobEffects.BAD_OMEN);
			// If it stayed: is a plain removal refused too (a mod cancelling the removal), or only milk's cure?
			boolean refused = !plain && !stand.removeEffect(MobEffects.BAD_OMEN) && stand.hasEffect(MobEffects.BAD_OMEN);

			// Milk again, with the add-on's cure after it (called directly: firing the finish event would wake every
			// mod that listens to drinking, for a player that isn't one).
			boolean fixed = false;

			if (omen(stand)) {
				ItemStack milk = new ItemStack(Items.MILK_BUCKET);
				ItemStack drunk = milk.copy();
				ItemStack left = milk.finishUsingItem(level, stand);
				onDrink(new LivingEntityUseItemEvent.Finish(stand, drunk, 0, left));
				fixed = !stand.hasEffect(MobEffects.BAD_OMEN);
			}

			stand.removeEffectNoUpdate(MobEffects.BAD_OMEN);
			LegionWareX.LOGGER.info("LegionWareX milk: plain milk cures Bad Omen: {} (milk listed as its cure: {}; a mod refused its removal: {}); with the add-on{}: {}",
					yes(plain), yes(listed), yes(refused), AddonConfig.milkCuresBadOmen() ? "" : " (off in the config)", yes(fixed));
		} catch (Throwable e) {
			LegionWareX.LOGGER.warn("LegionWareX milk: the stand-in's try failed", e);
		}
	}

	/** Bad Omen on the stand-in, fresh. Whether it took. */
	private static boolean omen(FakePlayer stand) {
		stand.removeEffectNoUpdate(MobEffects.BAD_OMEN);
		stand.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 600));
		return stand.hasEffect(MobEffects.BAD_OMEN);
	}

	private static String yes(boolean value) {
		return value ? "yes" : "no";
	}
}
