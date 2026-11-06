package com.freshorangeslices.legionwarex.armor;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Hidden armor (FreshOrangeSlices' playtest, 2026-11-05): a player's armor pieces, each on its own, and a companion's
 * armor, drawn as if they weren't worn, for everyone, while they still protect. A mask kept on the entity
 * ({@value #KEY} in its persistent data: bit 0 boots, 1 leggings, 2 chestplate, 3 helmet, as {@link EquipmentSlot}'s
 * armor index), sent to everyone who can see it ({@link ArmorNetwork}); each client leaves the hidden pieces out as it
 * draws the entity ({@code armor.client.ArmorClient}). A player clicks the dot in each armor slot of their inventory
 * (or {@code /hidearmor}); Bloom Pets' pet screen hides a companion's ({@link #toggleAll}, called by name, so neither
 * mod needs the other). A player's mask stays with them through death and dimensions.
 */
public final class HiddenArmor {
	public static final String KEY = "legionwarex.hiddenArmor";
	public static final int ALL = 0b1111;

	private HiddenArmor() {
	}

	public static int mask(Entity entity) {
		return entity.getPersistentData().getInt(KEY) & ALL;
	}

	/** Hides these pieces (and shows the rest), and tells everyone who can see it. */
	public static void set(LivingEntity entity, int mask) {
		mask &= ALL;

		if (mask == 0) {
			entity.getPersistentData().remove(KEY);
		} else {
			entity.getPersistentData().putInt(KEY, mask);
		}

		if (!entity.level().isClientSide()) {
			ArmorNetwork.sendMask(entity, mask);
		}
	}

	/** One piece (an armor index, 0 boots to 3 helmet) hidden or shown again. */
	public static void toggle(LivingEntity entity, int index) {
		if (index >= 0 && index < 4) {
			set(entity, mask(entity) ^ 1 << index);
		}
	}

	/**
	 * All its armor hidden, or (if it all was) shown again; returns whether it's hidden now. Bloom Pets' pet screen calls
	 * this by name for a companion.
	 */
	public static boolean toggleAll(LivingEntity entity) {
		boolean hide = mask(entity) != ALL;
		set(entity, hide ? ALL : 0);
		return hide;
	}

	@SubscribeEvent
	public static void onStartTracking(PlayerEvent.StartTracking event) {
		if (event.getTarget() instanceof LivingEntity living && event.getEntity() instanceof ServerPlayer player && mask(living) != 0) {
			ArmorNetwork.sendTo(player, living, mask(living));
		}
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		self(event.getEntity());
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		self(event.getEntity());
	}

	@SubscribeEvent
	public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		self(event.getEntity());
	}

	/** A player's mask stays with them when they respawn (persistent data doesn't by itself). */
	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		int mask = mask(event.getOriginal());

		if (mask != 0) {
			event.getEntity().getPersistentData().putInt(KEY, mask);
		}
	}

	private static void self(Entity entity) {
		if (entity instanceof ServerPlayer player && mask(player) != 0) {
			ArmorNetwork.sendTo(player, player, mask(player));
		}
	}

	private static final String[] PIECES = {"feet", "legs", "chest", "head"};

	/** {@code /hidearmor} (anyone): what's hidden; {@code /hidearmor head|chest|legs|feet} switches one, {@code all} or {@code none}. */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("hidearmor").executes(HiddenArmor::status);

		for (int i = 0; i < PIECES.length; i++) {
			int index = i;
			root.then(Commands.literal(PIECES[i]).executes(c -> {
				toggle(c.getSource().getPlayerOrException(), index);
				return status(c);
			}));
		}

		return root.then(Commands.literal("all").executes(c -> {
			set(c.getSource().getPlayerOrException(), ALL);
			return status(c);
		})).then(Commands.literal("none").executes(c -> {
			set(c.getSource().getPlayerOrException(), 0);
			return status(c);
		}));
	}

	private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		int mask = mask(player);
		StringBuilder hidden = new StringBuilder();

		for (int i = PIECES.length - 1; i >= 0; i--) {
			if ((mask & 1 << i) != 0) {
				hidden.append(hidden.length() == 0 ? "" : ", ").append(PIECES[i].toUpperCase(Locale.ROOT).charAt(0)).append(PIECES[i].substring(1));
			}
		}

		context.getSource().sendSuccess(() -> mask == 0 ? Component.translatable("message.legionwarex.armor_all_shown")
				: Component.translatable("message.legionwarex.armor_hidden", hidden.toString()), false);
		return 1;
	}
}
