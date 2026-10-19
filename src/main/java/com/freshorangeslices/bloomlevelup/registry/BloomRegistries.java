package com.freshorangeslices.bloomlevelup.registry;

import net.minecraft.server.packs.PackType;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;

import com.freshorangeslices.bloomlevelup.command.BloomCommands;
import com.freshorangeslices.bloomlevelup.config.ConfigLoader;
import com.freshorangeslices.bloomlevelup.data.BloomDataLoader;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.network.BloomSync;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorSkill;

/**
 * Single place where the mod hooks into the game. Each later chunk (networking, skills, Majors, effects)
 * adds one line here rather than scattering registration through gameplay code.
 */
public final class BloomRegistries {
	private BloomRegistries() {
	}

	public static void init() {
		BloomServices.setConfig(ConfigLoader.load());
		BloomAttachments.init();
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(BloomDataLoader.ID, new BloomDataLoader());
		BloomCommands.init();
		BloomSync.init();
		ArmorSkill.init();
	}
}
