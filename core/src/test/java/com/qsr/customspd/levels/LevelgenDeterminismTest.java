package com.qsr.customspd.levels;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.levels.features.LevelTransition;
import com.qsr.customspd.test.HeadlessRun;
import com.watabou.utils.PathFinder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F4 levelgen pins on the shipped dungeon layout, via {@link HeadlessRun}:
 *  - determinism: the same seed must rebuild the same floors cell for cell. Seeded runs are the
 *    contract behind seed sharing and daily runs, and Dungeon.init's RNG ordering has silently
 *    broken it before (see the comment block in Dungeon.init).
 *  - non-vacuity: a different seed must give a different map, or "equal" proves nothing.
 *  - connectivity: every generated floor's entrance must reach its regular exit.
 */
class LevelgenDeterminismTest {

	/** Floors 1-6 of the default layout: four sewer floors, the SewerBossLevel, the first prison floor (shop). */
	private static final int FLOORS = 6;

	@Test
	void theSameSeedRebuildsTheSameFloorsCellForCell() {
		List<int[]> first = descendThrough(31337L);
		List<int[]> second = descendThrough(31337L);
		assertEquals(FLOORS, first.size());
		for (int i = 0; i < FLOORS; i++) {
			assertTrue(Arrays.equals(first.get(i), second.get(i)),
					"floor " + (i + 1) + " must regenerate identically from the same seed");
		}
	}

	@Test
	void differentSeedsBuildDifferentFloors() {
		List<int[]> a = descendThrough(31337L);
		List<int[]> b = descendThrough(8675309L);
		for (int i = 0; i < FLOORS; i++) {
			assertFalse(Arrays.equals(a.get(i), b.get(i)),
					"floor " + (i + 1) + " came out identical for two different seeds");
		}
	}

	/** One sample per region of the default layout, all regular (non-boss) floors. */
	private static final String[] CONNECTIVITY_FLOORS = {"2", "4", "7", "12", "17", "22"};

	@Test
	void everyFloorsEntranceReachesItsExit() {
		int checked = 0;
		for (long seed = 1; seed <= 20; seed++) {
			try (HeadlessRun run = HeadlessRun.start(seed * 1000003L)) {
				for (String name : CONNECTIVITY_FLOORS) {
					Level level = run.generate(name);
					assertReachable(level, "seed " + run.seed + " floor " + name);
					checked++;
				}
			}
		}
		assertEquals(20 * CONNECTIVITY_FLOORS.length, checked);
	}

	// ------------------------------------------------------------------------------ helpers

	/** A new run on the seed, descending floor 1 -> FLOORS the way the hero would; returns each map. */
	private static List<int[]> descendThrough(long seed) {
		List<int[]> maps = new ArrayList<>();
		try (HeadlessRun run = HeadlessRun.start(seed)) {
			Level level = run.enterFirstLevel();
			while (true) {
				assertEquals(String.valueOf(maps.size() + 1), Dungeon.levelName, "the default layout's floor order");
				assertReachable(level, "seed " + seed + " floor " + Dungeon.levelName);
				maps.add(level.map.clone());
				if (maps.size() == FLOORS) break;
				level = run.descend();
			}
			assertEquals("SewerBossLevel", Dungeon.layout.getDungeon().get("5").getLayout(),
					"floor 5 is the boss layout this test means to cover");
		}
		return maps;
	}

	/**
	 * Flood fill from the entrance over passable-or-avoid cells (chasms, known traps); on a
	 * SECRETS-feeling floor hidden doors also count, since RegularPainter only guarantees that
	 * rooms there are not totally isolated.
	 */
	private static void assertReachable(Level level, String where) {
		LevelTransition exit = level.getTransition(LevelTransition.Type.REGULAR_EXIT);
		assertNotNull(exit, where + ": no exit transition");
		// getTransition falls back to an entrance when no exit exists: rule that out explicitly.
		assertEquals(LevelTransition.Type.REGULAR_EXIT, exit.type, where + ": no regular exit");
		int entrance = level.entrance();
		assertTrue(entrance != exit.cell(), where + ": entrance and exit share a cell");

		boolean[] walkable = new boolean[level.length()];
		for (int i = 0; i < walkable.length; i++) {
			walkable[i] = level.passable[i] || level.avoid[i]
					|| (level.feeling == Level.Feeling.SECRETS && level.secret[i]);
		}
		// The exit cell is the goal, not a step: a boss floor's LOCKED_EXIT is solid until the
		// boss dies, which is a gate, not a connectivity failure.
		walkable[exit.cell()] = true;
		PathFinder.setMapSize(level.width(), level.height());
		PathFinder.buildDistanceMap(entrance, walkable);
		if (PathFinder.distance[exit.cell()] == Integer.MAX_VALUE) {
			fail(where + ": exit cell " + exit.cell() + " is unreachable from entrance " + entrance
					+ " (feeling " + level.feeling + ")");
		}
	}
}
