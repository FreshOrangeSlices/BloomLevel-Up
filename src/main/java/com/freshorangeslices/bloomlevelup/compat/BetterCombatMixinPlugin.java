package com.freshorangeslices.bloomlevelup.compat;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.LoadingModList;

/** Applies {@code bloomlevelup.forge.bettercombat.mixins.json} only when Better Combat is installed. */
public final class BetterCombatMixinPlugin implements IMixinConfigPlugin {
	private boolean installed;

	@Override
	public void onLoad(String mixinPackage) {
		LoadingModList mods = LoadingModList.get();
		installed = mods != null && mods.getModFileById("bettercombat") != null; // no Bloom class this early
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return installed;
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
