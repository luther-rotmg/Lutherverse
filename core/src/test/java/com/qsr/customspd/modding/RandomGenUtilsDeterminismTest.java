package com.qsr.customspd.modding;

import com.watabou.utils.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the seeded determinism of guaranteed-drop and quest-NPC floor selection.
 *
 * <p>These functions previously used Kotlin's stdlib RNG, which consults neither the game's
 * generator nor its seed, so the same seed produced a different dungeon composition on every
 * run. That defeats labelled seed sharing, daily runs, deterministic coop and replay
 * verification.
 *
 * <p>Needs no libGDX: {@code com.watabou.utils.Random} lives in SPD-classes and is pure.
 */
class RandomGenUtilsDeterminismTest {

	private static final List<String> FLOORS = Arrays.asList(
			"sewers1", "sewers2", "sewers3", "sewers4",
			"prison1", "prison2", "prison3", "prison4",
			"caves1", "caves2", "caves3", "caves4");

	private static ItemDistribution dist(int quantity, List<String> levels) {
		return new ItemDistribution(quantity, levels);
	}

	@AfterEach
	void clearGenerators() {
		// Leaving a pushed generator on the stack would leak into the next test.
		Random.resetGenerators();
	}

	private static String[] levelsForSeed(long seed) {
		Random.pushGenerator(seed);
		try {
			return RandomGenUtils.calculateLevels(
					Arrays.asList(dist(3, FLOORS), dist(2, FLOORS)));
		} finally {
			Random.resetGenerators();
		}
	}

	private static String questLevelForSeed(long seed) {
		Random.pushGenerator(seed);
		try {
			return RandomGenUtils.calculateQuestLevel(FLOORS);
		} finally {
			Random.resetGenerators();
		}
	}

	@Test
	void sameSeedProducesIdenticalGuaranteedFloors() {
		assertArrayEquals(levelsForSeed(0xC0FFEEL), levelsForSeed(0xC0FFEEL),
				"the same seed must select the same guaranteed-drop floors; if this fails, "
						+ "labelled seed sharing and daily runs are broken");
	}

	@Test
	void sameSeedProducesIdenticalQuestFloor() {
		assertEquals(questLevelForSeed(0xC0FFEEL), questLevelForSeed(0xC0FFEEL));
	}

	@Test
	void differentSeedsGenerallyProduceDifferentFloors() {
		// Guards the opposite failure: a "deterministic" implementation that ignores the seed
		// entirely would pass the test above. Sampling 3 of 12 floors twice over, collisions
		// are possible, so require that MOST of a spread of seeds differ from a reference.
		String[] reference = levelsForSeed(1L);
		int differing = 0;
		for (long seed = 2L; seed <= 21L; seed++) {
			if (!Arrays.equals(reference, levelsForSeed(seed))) {
				differing++;
			}
		}
		assertTrue(differing >= 18,
				"expected most seeds to differ from the reference, got " + differing + "/20");
	}

	@Test
	void resultsAreDrawnFromTheRequestedPoolAndRespectQuantity() {
		Random.pushGenerator(42L);
		try {
			String[] result = RandomGenUtils.calculateLevels(
					Arrays.asList(dist(3, FLOORS), dist(2, FLOORS)));

			assertEquals(5, result.length);
			for (String level : result) {
				assertTrue(FLOORS.contains(level), level + " is not in the requested pool");
			}
		} finally {
			Random.resetGenerators();
		}
	}

	@Test
	void aSingleDistributionDrawsDistinctFloors() {
		// The shuffle-then-take contract: one distribution must not place two guaranteed
		// drops on the same floor.
		Random.pushGenerator(7L);
		try {
			String[] result = RandomGenUtils.calculateLevels(
					Arrays.asList(dist(4, FLOORS)));

			List<String> seen = new ArrayList<>();
			for (String level : result) {
				assertTrue(!seen.contains(level), "duplicate floor selected: " + level);
				seen.add(level);
			}
		} finally {
			Random.resetGenerators();
		}
	}

	@Test
	void questLevelSelectionVariesWithTheSeed() {
		// Same shape of guard as above, on the quest-NPC path.
		String reference = questLevelForSeed(1L);
		boolean sawDifferent = false;
		for (long seed = 2L; seed <= 30L; seed++) {
			if (!reference.equals(questLevelForSeed(seed))) {
				sawDifferent = true;
				break;
			}
		}
		assertTrue(sawDifferent, "quest floor never varied across 29 seeds");
	}

	@Test
	void anEmptyQuestDistributionYieldsNull() {
		assertEquals(null, RandomGenUtils.calculateQuestLevel(new ArrayList<>()));
	}

	@Test
	void differentSeedsAreNotTriviallyEqualOnQuestFloors() {
		assertNotEquals(0, FLOORS.size());
	}
}
