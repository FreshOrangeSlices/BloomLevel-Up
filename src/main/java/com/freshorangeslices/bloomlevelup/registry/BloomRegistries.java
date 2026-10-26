package com.freshorangeslices.bloomlevelup.registry;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;

import com.freshorangeslices.bloomlevelup.client.BloomLevelUpClient;
import com.freshorangeslices.bloomlevelup.command.BloomCommands;
import com.freshorangeslices.bloomlevelup.config.ConfigLoader;
import com.freshorangeslices.bloomlevelup.compat.BetterCombat;
import com.freshorangeslices.bloomlevelup.data.BloomDataLoader;
import com.freshorangeslices.bloomlevelup.data.BloomLootModifier;
import com.freshorangeslices.bloomlevelup.data.BloomServices;
import com.freshorangeslices.bloomlevelup.effect.BloomEffects;
import com.freshorangeslices.bloomlevelup.effect.HeldItemWatcher;
import com.freshorangeslices.bloomlevelup.effect.StatEffects;
import com.freshorangeslices.bloomlevelup.entity.BloomEntities;
import com.freshorangeslices.bloomlevelup.network.BloomNetwork;
import com.freshorangeslices.bloomlevelup.network.BloomSync;
import com.freshorangeslices.bloomlevelup.player.BloomAttachments;
import com.freshorangeslices.bloomlevelup.skill.armor.ArmorSkill;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatAbilities;
import com.freshorangeslices.bloomlevelup.skill.combat.CombatSkill;
import com.freshorangeslices.bloomlevelup.skill.misc.MiscSkill;
import com.freshorangeslices.bloomlevelup.skill.utility.BlockXpLoader;
import com.freshorangeslices.bloomlevelup.skill.utility.UtilitySkill;

/**
 * Single place where the mod hooks into the game. Each later chunk (skills, Majors, effects) adds one line here
 * rather than scattering registration through gameplay code. {@code modBus} carries registration and setup events;
 * gameplay events go through Forge's main bus.
 */
public final class BloomRegistries {
	private BloomRegistries() {
	}

	public static void init(IEventBus modBus) {
		BloomServices.setConfig(ConfigLoader.load());
		BloomAttachments.init(modBus);
		BloomEffects.register(modBus);
		BloomEntities.register(modBus);
		BloomMenus.register(modBus);
		BloomLootModifier.register(modBus);
		BloomNetwork.init();
		MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
			event.addListener(new BloomDataLoader());
			event.addListener(new BlockXpLoader());
		});
		BloomCommands.init();
		BloomSync.init();
		StatEffects.init();
		ArmorSkill.init();
		UtilitySkill.init(modBus);
		CombatSkill.init();
		CombatAbilities.init();
		MiscSkill.init();
		HeldItemWatcher.init();
		BetterCombat.init();

		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BloomLevelUpClient.init(modBus));
	}
}
