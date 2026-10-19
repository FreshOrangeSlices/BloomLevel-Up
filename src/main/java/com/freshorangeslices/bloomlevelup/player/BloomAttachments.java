package com.freshorangeslices.bloomlevelup.player;

import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * Registers the player profile as a persistent Fabric data attachment that survives death and is saved
 * with the player. Client sync is handled separately by our own packets (sent as deltas, not every tick).
 */
public final class BloomAttachments {
	public static final AttachmentType<BloomProfile> PROFILE = AttachmentRegistry.create(BloomLevelUp.id("profile"), builder -> builder
			.persistent(ProfileCodecs.PROFILE)
			.initializer(BloomProfile::new)
			.copyOnDeath());

	private BloomAttachments() {
	}

	/** Loads the class so the attachment registers during mod init. */
	public static void init() {
	}

	/** The live, mutable profile for a player (created on first access). Server-side truth only. */
	public static BloomProfile profile(Player player) {
		return player.getAttachedOrCreate(PROFILE);
	}
}
