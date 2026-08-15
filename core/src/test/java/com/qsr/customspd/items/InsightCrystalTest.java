package com.qsr.customspd.items;

import com.qsr.customspd.actors.hero.spheregrid.SphereGridProgress;
import com.qsr.customspd.items.stones.Runestone;
import com.qsr.customspd.test.HeadlessGdx;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Insight Crystal — the first ITEM shipped through the content-scaffold pipeline, and the
 * meta-progression faucet: shattering it must grant persistent Insight. Also guards the
 * Generator pool consistency the scaffold dogfooding broke (classes[] vs defaultProbs[]
 * desync means an item silently never drops).
 */
class InsightCrystalTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@BeforeEach
	void clean() {
		SphereGridProgress.reset();
	}

	@Test
	void shatteringGrantsPersistentInsight() throws Exception {
		assertEquals(0, SphereGridProgress.insight());

		InsightCrystal crystal = new InsightCrystal();
		// activate(cell) is the runestone shatter hook; invoke it directly (no scene).
		Method activate = InsightCrystal.class.getDeclaredMethod("activate", int.class);
		activate.setAccessible(true);
		activate.invoke(crystal, 0);

		assertEquals(InsightCrystal.INSIGHT_PER_CRYSTAL, SphereGridProgress.insight(),
				"a shattered crystal must bank persistent Insight");
	}

	@Test
	void isARunestonePoolCitizen() {
		InsightCrystal crystal = new InsightCrystal();
		assertTrue(crystal instanceof Runestone, "STONE pool items must be Runestones");
		assertTrue(crystal.stackable, "runestones stack");
		assertTrue(crystal.isIdentified(), "runestones need no identification");
		assertTrue(crystal.value() > 0, "shops must be able to price it");
	}

	@Test
	void everyGeneratorCategoryKeepsClassesAndProbsAligned() {
		// Regression guard for the content-scaffold desync bug (2026-08-14): inserting a
		// class without extending defaultProbs makes the tail item silently undroppable.
		// This sweeps EVERY deck-based category, so any future desync fails loudly.
		for (Generator.Category cat : Generator.Category.values()) {
			if (cat.defaultProbs != null) {
				assertNotNull(cat.classes, cat.name() + " has probs but no classes");
				assertEquals(cat.classes.length, cat.defaultProbs.length,
						cat.name() + ": classes[] and defaultProbs[] must stay in lockstep");
			}
		}
	}
}
