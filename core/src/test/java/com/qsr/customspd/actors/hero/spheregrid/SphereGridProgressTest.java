package com.qsr.customspd.actors.hero.spheregrid;

import com.qsr.customspd.test.HeadlessGdx;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The persistent (cross-run) sphere-grid layer: an Insight currency and permanent node unlocks,
 * backed by GameSettings. Runs on the F1 headless harness (GameSettings needs Gdx preferences).
 * reset() isolates each test from the shared prefs store.
 */
class SphereGridProgressTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@BeforeEach
	void clean() {
		SphereGridProgress.reset();
	}

	@Test
	void noInsightCannotUnlock() {
		assertEquals(0, SphereGridProgress.insight());
		assertFalse(SphereGridProgress.canUnlock(SphereNode.ATTUNEMENT));
		assertFalse(SphereGridProgress.unlock(SphereNode.ATTUNEMENT));
		assertFalse(SphereGridProgress.isUnlocked(SphereNode.ATTUNEMENT));
	}

	@Test
	void insightUnlocksRootAndIsSpent() {
		SphereGridProgress.earnInsight(2);
		assertEquals(2, SphereGridProgress.insight());
		assertTrue(SphereGridProgress.canUnlock(SphereNode.ATTUNEMENT));
		assertTrue(SphereGridProgress.unlock(SphereNode.ATTUNEMENT));
		assertTrue(SphereGridProgress.isUnlocked(SphereNode.ATTUNEMENT));
		assertEquals(1, SphereGridProgress.insight(), "unlocking spends Insight");
	}

	@Test
	void unlockRequiresPrerequisiteUnlocked() {
		SphereGridProgress.earnInsight(5);
		// EMBER_I requires ATTUNEMENT unlocked first.
		assertFalse(SphereGridProgress.canUnlock(SphereNode.EMBER_I));
		SphereGridProgress.unlock(SphereNode.ATTUNEMENT);
		assertTrue(SphereGridProgress.canUnlock(SphereNode.EMBER_I));
		// EMBER_II is still gated behind EMBER_I.
		assertFalse(SphereGridProgress.canUnlock(SphereNode.EMBER_II));
	}

	@Test
	void unlocksAccumulateAndPersistInTheStore() {
		SphereGridProgress.earnInsight(3);
		assertTrue(SphereGridProgress.unlock(SphereNode.ATTUNEMENT));
		assertTrue(SphereGridProgress.unlock(SphereNode.MIGHT_I)); // prereq ATTUNEMENT is unlocked
		// A fresh read (as a later run/access would do) still sees both.
		assertTrue(SphereGridProgress.isUnlocked(SphereNode.ATTUNEMENT));
		assertTrue(SphereGridProgress.isUnlocked(SphereNode.MIGHT_I));
		assertEquals(2, SphereGridProgress.unlockedNames().size());
		assertEquals(1, SphereGridProgress.insight());
	}
}
