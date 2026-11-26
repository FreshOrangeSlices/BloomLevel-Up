package com.freshorangeslices.legionwarex.gametest;

import com.freshorangeslices.legionwarex.LegionWareX;
import com.freshorangeslices.legionwarex.accessory.ArtifactRules;
import com.freshorangeslices.legionwarex.accessory.SharedSlots;
import com.freshorangeslices.legionwarex.accessory.Trophies;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Accessories (accessory/). Curios and the mods with the pieces aren't in the add-on's test run, so what's tested is the
 * part that stands alone: game rules set by name (vanilla's stand in for Artifacts'), and the tables.
 */
@GameTestHolder(LegionWareX.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AccessoryGameTests {
	@GameTest(template = "empty")
	public static void gameRulesAreSetByTheirNames(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		GameRules rules = server.getGameRules();
		int speed = rules.getInt(GameRules.RULE_RANDOMTICKING);
		boolean insomnia = rules.getBoolean(GameRules.RULE_DOINSOMNIA);
		Map<String, Object> table = new LinkedHashMap<>();
		table.put("randomTickSpeed", speed + 1);
		table.put("doInsomnia", !insomnia);
		table.put("artifacts.noSuchItem.noSuchRule", 3);

		try {
			List<String> said = ArtifactRules.apply(server, table);
			helper.assertTrue(rules.getInt(GameRules.RULE_RANDOMTICKING) == speed + 1, "a number rule is set by its name: " + said);
			helper.assertTrue(rules.getBoolean(GameRules.RULE_DOINSOMNIA) == !insomnia, "... and a yes/no one");
			helper.assertTrue(said.contains("artifacts.noSuchItem.noSuchRule not found"), "a rule no mod has is named, not set: " + said);
		} finally {
			rules.getRule(GameRules.RULE_RANDOMTICKING).set(speed, server);
			rules.getRule(GameRules.RULE_DOINSOMNIA).set(insomnia, server);
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void theTablesNameWhatThePackAgreed(GameTestHelper helper) {
		helper.assertTrue(ArtifactRules.RULES.get("artifacts.powerGlove.attackDamageBonus").equals(2), "the Power Glove gives +2 (not 4)");
		helper.assertTrue(ArtifactRules.RULES.get("artifacts.bunnyHoppers.doCancelFallDamage").equals(false), "Bunny Hoppers no longer stop fall damage");
		helper.assertTrue(Trophies.SAYS.size() == 5, "five pieces in the first wave of trophies");
		helper.assertTrue(SharedSlots.ROWS.get("ring").equals("rings") && SharedSlots.ROWS.get("belt").equals("waist"),
				"Cataclysm's own rows fold into the shared ring and belt rows");
		helper.succeed();
	}
}
