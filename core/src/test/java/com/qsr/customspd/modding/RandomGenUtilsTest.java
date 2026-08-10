package com.qsr.customspd.modding;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first tests in :core. Deliberately chosen to need nothing from libGDX -- no Gdx.app,
 * no Gdx.files, no GL context -- so the module gets a working test source set before the
 * headless-backend bootstrap spike happens.
 *
 * <p>Covers {@code halveQuantities}, which implements the Forbidden Runes (NO_SCROLLS)
 * challenge by halving each region's guaranteed ScrollOfUpgrade count.
 */
class RandomGenUtilsTest {

	private static ItemDistribution dist(int quantity, String... levels) {
		return new ItemDistribution(quantity, Arrays.asList(levels));
	}

	@Test
	void halvesEachDistributionQuantity() {
		List<ItemDistribution> halved = RandomGenUtils.halveQuantities(
				Arrays.asList(dist(4, "sewers1", "sewers2"), dist(2, "caves1")));

		assertEquals(2, halved.get(0).getQuantity());
		assertEquals(1, halved.get(1).getQuantity());
	}

	@Test
	void leavesTheLevelPoolUntouched() {
		// Only the count of guaranteed drops changes; which levels are eligible must not,
		// or the challenge would quietly reshape the dungeon's layout as well.
		List<String> levels = Arrays.asList("sewers1", "sewers2", "sewers3");
		List<ItemDistribution> halved = RandomGenUtils.halveQuantities(
				Arrays.asList(new ItemDistribution(3, levels)));

		assertEquals(levels, halved.get(0).getLevels());
	}

	@Test
	void oddQuantitiesRoundDown() {
		// 1 -> 0 is intentional: a region with a single guaranteed scroll loses it under
		// Forbidden Runes. Integer division, matching "half the scrolls are removed".
		List<ItemDistribution> halved = RandomGenUtils.halveQuantities(
				Arrays.asList(dist(3, "a"), dist(1, "b")));

		assertEquals(1, halved.get(0).getQuantity());
		assertEquals(0, halved.get(1).getQuantity());
	}

	@Test
	void doesNotMutateTheInput() {
		// The distributions come from the loaded dungeon layout, which is shared state.
		// Mutating it would apply the challenge permanently, including to later runs.
		ItemDistribution original = dist(4, "sewers1");
		List<ItemDistribution> input = Arrays.asList(original);

		List<ItemDistribution> halved = RandomGenUtils.halveQuantities(input);

		assertEquals(4, original.getQuantity(), "the source distribution must be untouched");
		assertNotSame(original, halved.get(0));
	}

	@Test
	void emptyInputYieldsEmptyOutput() {
		assertTrue(RandomGenUtils.halveQuantities(java.util.Collections.emptyList()).isEmpty());
	}

	@Test
	void calculateLevelsHonoursTheRequestedQuantity() {
		// Guards the contract halveQuantities depends on: the number of guaranteed levels
		// returned equals the quantity, so halving the quantity halves the drops.
		String[] levels = RandomGenUtils.calculateLevels(
				Arrays.asList(dist(2, "a", "b", "c", "d")));

		assertEquals(2, levels.length);
	}

	@Test
	void calculateLevelsWithZeroQuantityReturnsNothing() {
		// The case Forbidden Runes creates when a single-scroll region is halved to 0.
		assertEquals(0, RandomGenUtils.calculateLevels(Arrays.asList(dist(0, "a", "b"))).length);
	}
}
