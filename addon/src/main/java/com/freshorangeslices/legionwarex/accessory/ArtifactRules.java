package com.freshorangeslices.legionwarex.accessory;

import com.freshorangeslices.legionwarex.LegionWareX;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

/**
 * Artifacts' strong items toned down for the pack (FreshOrangeSlices, 2026-11-25: "that sounds fine"): its numbers are
 * game rules, kept with each world, so the add-on sets them once per world (and again whenever this table changes,
 * {@link #VERSION}), after which an operator's own /gamerule stays. Names are Artifacts' own
 * ("artifacts.<item in lowerCamel>.<rule>", read in its jar).
 */
public final class ArtifactRules {
	/** Raised whenever the table changes, so worlds take the new numbers. */
	static final int VERSION = 1;
	public static final Map<String, Object> RULES = new LinkedHashMap<>();
	private static final String NAME = "legionwarex_artifact_rules";

	static {
		RULES.put("artifacts.powerGlove.attackDamageBonus", 2);          // +4 attack damage before
		RULES.put("artifacts.crystalHeart.healthBonus", 6);              // +10 health
		RULES.put("artifacts.vampiricGlove.absorptionRatio", 10);        // 20% of melee damage healed
		RULES.put("artifacts.vampiricGlove.maxHealingPerHit", 3);        // 6
		RULES.put("artifacts.crossNecklace.bonusInvincibilityTicks", 10); // 20 (1 s)
		RULES.put("artifacts.bunnyHoppers.doCancelFallDamage", false);   // no fall damage
	}

	private ArtifactRules() {
	}

	/** Which version of the table a world has taken. */
	static final class Taken extends SavedData {
		int version;

		static Taken of(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(tag -> {
				Taken taken = new Taken();
				taken.version = tag.getInt("version");
				return taken;
			}, Taken::new, NAME);
		}

		@Override
		public CompoundTag save(CompoundTag tag) {
			tag.putInt("version", version);
			return tag;
		}
	}

	/** Sets the rules on a server; returns what it set and what it couldn't find. */
	public static List<String> apply(MinecraftServer server, Map<String, Object> rules) {
		Map<String, GameRules.Key<?>> keys = new HashMap<>();
		GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
			@Override
			public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
				keys.put(key.getId(), key);
			}
		});
		List<String> said = new ArrayList<>();

		rules.forEach((name, value) -> {
			GameRules.Key<?> key = keys.get(name);

			if (key == null) {
				said.add(name + " not found");
				return;
			}

			GameRules.Value<?> rule = server.getGameRules().getRule(key);

			if (rule instanceof GameRules.IntegerValue integer && value instanceof Integer number) {
				integer.set(number, server);
				said.add(name + "=" + number);
			} else if (rule instanceof GameRules.BooleanValue bool && value instanceof Boolean flag) {
				bool.set(flag, server);
				said.add(name + "=" + flag);
			} else {
				said.add(name + " is a " + rule.getClass().getSimpleName());
			}
		});

		return said;
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		if (!ModList.get().isLoaded("artifacts")) {
			return;
		}

		MinecraftServer server = event.getServer();
		Taken taken = Taken.of(server);

		if (taken.version >= VERSION) {
			LegionWareX.LOGGER.info("LegionWareX artifacts: this world has the pack's numbers (version {})", taken.version);
			return;
		}

		List<String> said = apply(server, RULES);
		taken.version = VERSION;
		taken.setDirty();
		LegionWareX.LOGGER.info("LegionWareX artifacts: set {}", said);
	}
}
