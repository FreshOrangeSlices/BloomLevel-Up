package com.freshorangeslices.bloomlevelup.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import com.freshorangeslices.bloomlevelup.network.BloomPayloads;

/**
 * Nameplates: {@code Name [lvl N]}, N = Major Levels the player has spent. The tag turns green for good once
 * the player has mastered any category. The server sends each player's tag; this only remembers and shows it.
 */
public final class NameTags {
	private static final Map<UUID, BloomPayloads.NameTag> TAGS = new HashMap<>();

	private NameTags() {
	}

	public static void accept(BloomPayloads.NameTag tag) {
		TAGS.put(tag.player(), tag);
	}

	/** The tag known for a player, or null (client tests). */
	public static BloomPayloads.NameTag tag(UUID player) {
		return TAGS.get(player);
	}

	public static void clear() {
		TAGS.clear();
	}

	public static Component decorate(Player player, Component name) {
		BloomPayloads.NameTag tag = TAGS.get(player.getUUID());

		if (tag == null || name == null) {
			return name;
		}

		return name.copy().append(Component.literal(" [lvl " + tag.spent() + "]").withStyle(tag.mastered() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
	}
}
