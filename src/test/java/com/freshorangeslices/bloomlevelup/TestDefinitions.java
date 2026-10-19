package com.freshorangeslices.bloomlevelup;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.freshorangeslices.bloomlevelup.definition.CategoryDefinition;
import com.freshorangeslices.bloomlevelup.definition.DefinitionSet;
import com.freshorangeslices.bloomlevelup.definition.MajorDefinition;
import com.freshorangeslices.bloomlevelup.definition.StatDefinition;

/** Small fixture mirroring the real data layout, so tests read like the design documents. */
public final class TestDefinitions {
	public static final String UTILITY = "bloomlevelup:utility";
	public static final String ARMOR = "bloomlevelup:armor";
	public static final String MISC = "bloomlevelup:misc";
	public static final String EFFICIENCY = UTILITY + "/efficiency";
	public static final String YIELD = UTILITY + "/yield";
	public static final String VITALITY = ARMOR + "/vitality";

	public static final String VEIN_MINING = "bloomlevelup:utility/vein_mining";
	public static final String IRON_SKIN = "bloomlevelup:armor/iron_skin";
	public static final String LAST_BREATH = "bloomlevelup:armor/last_breath";
	public static final String CASTLE_OF_STONE = "bloomlevelup:armor/castle_of_stone";
	public static final String HOLD_TO_CHARGE = "bloomlevelup:armor/hold_to_charge";
	public static final String HOLD_TO_THROW = "bloomlevelup:armor/hold_to_throw";
	public static final String PASSIVE = "bloomlevelup:misc/passive";
	public static final String COMBAT = "bloomlevelup:combat";
	public static final String SWORD_TREE = "bloomlevelup:combat/sword";
	public static final String FULL_SWEEP = "bloomlevelup:combat/full_sweep";
	public static final String EXECUTE = "bloomlevelup:combat/execute";
	public static final String BATTER_UP = "bloomlevelup:combat/batter_up";
	public static final String COPYCAT = "bloomlevelup:combat/copycat";
	public static final String ASSAULT_RIFLE = "bloomlevelup:combat/assault_rifle";

	private TestDefinitions() {
	}

	public static DefinitionSet standard() {
		return DefinitionSet.build(
				List.of(
						category(UTILITY, 0, true, stat(EFFICIENCY, UTILITY, true), stat(YIELD, UTILITY, true)),
						category(ARMOR, 1, true, stat(VITALITY, ARMOR, false)),
						category(MISC, 2, false),
						category(COMBAT, 3, true)),
				List.of(
						major(VEIN_MINING, UTILITY, 1, true),
						major(IRON_SKIN, ARMOR, 7, true),
						major(LAST_BREATH, ARMOR, 2, true),
						major(CASTLE_OF_STONE, ARMOR, 2, true, List.of(LAST_BREATH), List.of()),
						major(HOLD_TO_CHARGE, ARMOR, 1, true, List.of(), List.of(HOLD_TO_THROW)),
						major(HOLD_TO_THROW, ARMOR, 1, true),
						major(PASSIVE, MISC, 1, false),
						inTree(FULL_SWEEP, COMBAT, SWORD_TREE),
						inTree(EXECUTE, COMBAT, SWORD_TREE),
						major(BATTER_UP, COMBAT, 2, true, List.of("#" + SWORD_TREE), List.of()),
						new MajorDefinition(COPYCAT, COMBAT, 5, true, List.of(), List.of(), Optional.empty(), 0, Optional.empty(),
								Optional.empty(), Set.of("universal", "copies_weapon_major")),
						new MajorDefinition(ASSAULT_RIFLE, COMBAT, 1, true, List.of(), List.of(), Optional.of("bloomlevelup:combat/crossbow"), 0,
								Optional.empty(), Optional.empty(), Set.of("weapon", "copycat_ineligible"))));
	}

	public static CategoryDefinition category(String id, int sort, boolean investment, StatDefinition... stats) {
		return new CategoryDefinition(id, sort, investment, Optional.empty(), List.of(stats));
	}

	public static StatDefinition stat(String id, String category, boolean toggleable) {
		return new StatDefinition(id, category, toggleable, 0, Optional.empty());
	}

	/** A 1-cost weapon-tree Major. */
	public static MajorDefinition inTree(String id, String category, String group) {
		return new MajorDefinition(id, category, 1, true, List.of(), List.of(),
				Optional.of(group), 0, Optional.empty(), Optional.empty(), Set.of("weapon"));
	}

	public static MajorDefinition major(String id, String category, int cost, boolean toggleable) {
		return major(id, category, cost, toggleable, List.of(), List.of());
	}

	public static MajorDefinition major(String id, String category, int cost, boolean toggleable,
			List<String> prerequisites, List<String> conflicts) {
		return new MajorDefinition(id, category, cost, toggleable, prerequisites, conflicts,
				Optional.empty(), 0, Optional.empty(), Optional.empty(), Set.of());
	}
}
