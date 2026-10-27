package com.freshorangeslices.bloomlevelup.client;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

import com.freshorangeslices.bloomlevelup.config.PackDefaults;

/**
 * Applies the Bloom Pack's first-launch defaults as early as a Forge mod can: when Mixin loads Bloom's client mixin
 * config, before any game class (so before Minecraft or any mod reads its settings). {@code OptionsMixin} used to be
 * the only place, at the start of {@code Options.load}, but Oculus reads and saves its own settings from the same
 * spot and got there first, so it never saw the pack's shader and wrote over it. {@code OptionsMixin} stays as the
 * fallback (once applied, a second call does nothing). Touches nothing of Minecraft's: only FML's paths and the
 * plain-Java {@link PackDefaults}. Does nothing outside the pack (no defaults file) or on a server.
 */
public final class PackDefaultsEarly implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("bloomlevelup");
	/** 1.20.1's data version (what its options.txt carries), since the game's own can't be read this early. */
	private static final int DATA_VERSION = 3465;

	@Override
	public void onLoad(String mixinPackage) {
		try {
			if (FMLLoader.getDist().isClient()) {
				PackDefaults.apply(FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get().resolve("options.txt"), DATA_VERSION, LOGGER::info);
			}
		} catch (Exception e) {
			LOGGER.warn("Couldn't apply the Bloom Pack's defaults early (the options hook will try again)", e);
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
