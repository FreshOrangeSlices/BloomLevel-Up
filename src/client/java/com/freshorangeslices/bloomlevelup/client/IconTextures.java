package com.freshorangeslices.bloomlevelup.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import com.freshorangeslices.bloomlevelup.BloomLevelUp;

/**
 * Maps an icon id from the definitions (e.g. {@code bloomlevelup:icon/major/combat/execute}) to its texture
 * ({@code assets/bloomlevelup/textures/icon/major/combat/execute.png}). Missing art falls back to the shared
 * placeholder, so a Major works and shows something before its icon exists.
 */
public final class IconTextures {
	public static final Identifier PLACEHOLDER = BloomLevelUp.id("textures/gui/placeholder.png");
	/** Icon art is authored at 64x64 and scaled down to its slot. */
	public static final int SOURCE_SIZE = 64;

	private static final Map<String, Identifier> CACHE = new HashMap<>();

	private IconTextures() {
	}

	public static Identifier resolve(Optional<String> icon) {
		if (icon.isEmpty()) {
			return PLACEHOLDER;
		}

		return CACHE.computeIfAbsent(icon.get(), IconTextures::lookup);
	}

	private static Identifier lookup(String icon) {
		Identifier id = Identifier.tryParse(icon);

		if (id == null) {
			return PLACEHOLDER;
		}

		Identifier texture = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png");
		boolean exists = Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
		return exists ? texture : PLACEHOLDER;
	}

	static void clearCache() {
		CACHE.clear();
	}
}
