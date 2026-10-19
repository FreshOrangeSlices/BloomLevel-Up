package com.freshorangeslices.bloomlevelup.definition;

import static com.freshorangeslices.bloomlevelup.TestDefinitions.category;
import static com.freshorangeslices.bloomlevelup.TestDefinitions.major;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class DefinitionSetTest {
	private static final String CAT = "test:cat";

	@Test
	void conflictsBecomeSymmetric() {
		DefinitionSet set = DefinitionSet.build(List.of(category(CAT, 0, false)), List.of(
				major("test:a", CAT, 1, true, List.of(), List.of("test:b")),
				major("test:b", CAT, 1, true)));
		assertEquals(List.of("test:a"), set.major("test:b").orElseThrow().conflicts());
	}

	@Test
	void badReferencesAreDroppedWithWarnings() {
		DefinitionSet set = DefinitionSet.build(List.of(category(CAT, 0, false)), List.of(
				major("test:a", CAT, 1, true, List.of("test:missing", "test:a"), List.of("test:a", "test:ghost")),
				major("test:orphan", "test:no_such_category", 1, true)));
		MajorDefinition a = set.major("test:a").orElseThrow();
		assertTrue(a.prerequisites().isEmpty());
		assertTrue(a.conflicts().isEmpty());
		assertFalse(set.major("test:orphan").isPresent());
		assertEquals(5, set.warnings().size());
	}

	@Test
	void prerequisiteCyclesAreRemoved() {
		DefinitionSet set = DefinitionSet.build(List.of(category(CAT, 0, false)), List.of(
				major("test:a", CAT, 1, true, List.of("test:b"), List.of()),
				major("test:b", CAT, 1, true, List.of("test:a"), List.of()),
				major("test:c", CAT, 1, true, List.of("test:a"), List.of()),
				major("test:d", CAT, 1, true)));
		assertFalse(set.major("test:a").isPresent());
		assertFalse(set.major("test:b").isPresent());
		assertTrue(set.major("test:c").orElseThrow().prerequisites().isEmpty());
		assertTrue(set.major("test:d").isPresent());
	}

	@Test
	void requiringAndConflictingWithSameMajorKeepsTheRequirement() {
		DefinitionSet set = DefinitionSet.build(List.of(category(CAT, 0, false)), List.of(
				major("test:a", CAT, 1, true, List.of("test:b"), List.of("test:b")),
				major("test:b", CAT, 1, true)));
		assertEquals(List.of("test:b"), set.major("test:a").orElseThrow().prerequisites());
		assertTrue(set.major("test:a").orElseThrow().conflicts().isEmpty());
	}

	@Test
	void treePrerequisiteToEmptyGroupIsDropped() {
		DefinitionSet set = DefinitionSet.build(List.of(category(CAT, 0, false)), List.of(
				major("test:a", CAT, 1, true, List.of("#test:nothing_here"), List.of())));
		assertTrue(set.major("test:a").orElseThrow().prerequisites().isEmpty());
		assertEquals(1, set.warnings().size());
	}

	@Test
	void categoriesAreSorted() {
		DefinitionSet set = DefinitionSet.build(List.of(category("test:z", 0, false), category("test:a", 5, false)), List.of());
		assertEquals("test:z", set.categories().iterator().next().id());
	}
}
