package com.freshorangeslices.bloomlevelup.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.network.BloomSync;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;

/**
 * Tracks "this player is eating/drinking right now" (mixin around {@code LivingEntity.completeUsingItem}), so effects caused by food
 * the player chose to eat can be told apart from effects inflicted on them, and counts Protein! meals.
 */
public final class ConsumeContext {
	/** A copy of what each player is consuming right now (the stack itself shrinks while it's eaten). */
	private static final Map<UUID, ItemStack> CONSUMING = new HashMap<>();
	private static final Map<UUID, Boolean> FROM_OFFHAND = new HashMap<>();

	private ConsumeContext() {
	}

	public static boolean isConsuming(ServerPlayer player) {
		return CONSUMING.containsKey(player.getUUID());
	}

	/** What the player is eating or drinking right now, or empty. */
	public static ItemStack consumed(ServerPlayer player) {
		return CONSUMING.getOrDefault(player.getUUID(), ItemStack.EMPTY);
	}

	public static void begin(ServerPlayer player, ItemStack stack, boolean fromOffhand) {
		CONSUMING.put(player.getUUID(), stack.copy());
		FROM_OFFHAND.put(player.getUUID(), fromOffhand);

		// Protein!: every qualifying meat item eaten after buying it counts toward permanent health.
		BloomProfile profile = BloomAttachments.profile(player);

		if (profile.owns(ArmorIds.PROTEIN) && stack.is(BloomTags.MEAT)) {
			profile.setCounter(ArmorIds.PROTEIN_MEALS, profile.counter(ArmorIds.PROTEIN_MEALS) + 1);
			BloomSync.changed(player);
		}
	}

	public static void end(ServerPlayer player) {
		ItemStack consumed = CONSUMING.remove(player.getUUID());
		boolean fromOffhand = Boolean.TRUE.equals(FROM_OFFHAND.remove(player.getUUID()));

		if (consumed != null) {
			MiscSkill.afterConsume(player, consumed, fromOffhand);
		}
	}
}
