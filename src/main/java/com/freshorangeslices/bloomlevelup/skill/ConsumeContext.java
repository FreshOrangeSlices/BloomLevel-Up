package com.freshorangeslices.bloomlevelup.skill;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.freshorangeslices.bloomlevelup.network.BloomSync;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.player.BloomProfile;
import com.freshorangeslices.bloomlevelup.registry.BloomTags;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorIds;

/**
 * Tracks "this player is eating/drinking right now" (from the Consumable mixin), so effects caused by food
 * the player chose to eat can be told apart from effects inflicted on them, and counts Protein! meals.
 */
public final class ConsumeContext {
	private static final Set<UUID> CONSUMING = new HashSet<>();

	private ConsumeContext() {
	}

	public static boolean isConsuming(ServerPlayer player) {
		return CONSUMING.contains(player.getUUID());
	}

	public static void begin(ServerPlayer player, ItemStack stack) {
		CONSUMING.add(player.getUUID());

		// Protein!: every qualifying meat item eaten after buying it counts toward permanent health.
		BloomProfile profile = BloomAttachments.profile(player);

		if (profile.owns(ArmorIds.PROTEIN) && stack.is(BloomTags.MEAT)) {
			profile.setCounter(ArmorIds.PROTEIN_MEALS, profile.counter(ArmorIds.PROTEIN_MEALS) + 1);
			BloomSync.changed(player);
		}
	}

	public static void end(ServerPlayer player) {
		CONSUMING.remove(player.getUUID());
	}
}
