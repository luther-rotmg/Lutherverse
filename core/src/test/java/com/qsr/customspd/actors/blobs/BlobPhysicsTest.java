package com.qsr.customspd.actors.blobs;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.levels.Terrain;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.HeadlessLevel;
import com.qsr.customspd.test.SaveRoundtrip;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F3+F2 integration tests for blob physics on the real {@link com.qsr.customspd.levels.Level}
 * fixture: Fire's spread/decay/terrain-destruction cycle, the Fire-vs-Freezing cancellation
 * rule, and the {@link Blob#storeInBundle} start/end trim through a real save roundtrip.
 *
 * None of the exercised paths consume RNG (Blob.act/Fire.evolve/Freezing.clear roll nothing,
 * and burn() finds no chars/heaps/plants on the empty fixture), so every assertion here is an
 * exact deterministic value — no seeded bands needed.
 */
class BlobPhysicsTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@AfterEach
	void tearDown() {
		HeadlessLevel.uninstall();
		Dungeon.hero = null;
	}

	@Test
	void fireSpreadsToTheFlammableNeighbourAndBurnsItToEmbers() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int w = level.width();
		int src = 3 * w + 3;        // fire seeded here, on plain EMPTY floor
		int grass = 3 * w + 4;      // flammable neighbour

		level.map[grass] = Terrain.HIGH_GRASS; // FLAMABLE per Terrain.flags
		level.buildFlagMaps();
		assertTrue(level.flamable[grass], "fixture sanity: HIGH_GRASS must be flammable");

		// Fire.evolve calls Dungeon.observe() after destroying terrain, which dereferences
		// Dungeon.hero — park a live hero on the level (not Actor-registered, so burn()
		// cannot find and ignite it).
		Hero hero = new Hero();
		hero.pos = w + 1;
		Dungeon.hero = hero;

		Fire fire = Blob.seed(src, 4, Fire.class, level);
		fire.act();

		// One evolve: the source decays 4 -> 3; the adjacent flammable cell ignites at
		// Fire.evolve's fixed ignition strength of 4.
		assertEquals(3, fire.cur[src], "seeded fire decays by exactly 1 per evolve");
		assertEquals(4, fire.cur[grass], "a flammable neighbour must ignite at strength 4");
		assertEquals(7, fire.volume, "volume = decayed source + fresh ignition");

		// Burn out completely. src: 4,3,2,1,0 and grass: (ignites),4,3,2,1,0 -> the grass
		// cell's fire dies on act 5, which is also the act that destroys the terrain.
		int acts = 1;
		while (fire.volume > 0 && acts < 12) {
			fire.act();
			acts++;
		}
		assertEquals(0, fire.volume, "the fire must burn out within the act cap");
		assertEquals(5, acts, "the whole fire must burn out on act 5 exactly");

		assertEquals(Terrain.EMBERS, level.map[grass],
				"burning a flammable tile to 0 must destroy it to EMBERS");
		assertFalse(level.flamable[grass], "Level.set must clear the flammable flag with the tile");
		assertEquals(Terrain.EMPTY, level.map[src],
				"fire dying on non-flammable floor must not touch the terrain");
	}

	@Test
	void fireAndFreezingOnOneCellAnnihilateBothCellsEntirely() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int cell = 3 * level.width() + 3;

		// Deliberately unequal volumes: Fire.evolve's cancellation (freeze.clear + zero the
		// fire cell) is all-or-nothing per cell, NOT proportional — 2 fire wipes 10 freeze.
		Fire fire = Blob.seed(cell, 2, Fire.class, level);
		Freezing freeze = Blob.seed(cell, 10, Freezing.class, level);
		assertEquals(2, fire.volume);
		assertEquals(10, freeze.volume);

		fire.act();

		assertEquals(0, fire.cur[cell], "the burning cell must be snuffed by the freeze");
		assertEquals(0, fire.volume, "no other fire existed, so total fire volume hits 0");
		assertEquals(0, freeze.cur[cell], "the freeze cell is fully cleared, not reduced");
		assertEquals(0, freeze.volume, "freeze.clear removes the cell's whole 10 volume");
	}

	@Test
	void trimRoundtripPreservesASpanReachingTheLastArrayCell() throws IOException {
		HeadlessLevel level = HeadlessLevel.install(8, 8); // length 64
		int last = level.length() - 1;

		Fire fire = new Fire();
		fire.seed(level, 5, 7);
		fire.seed(level, last, 3);

		// storeInBundle trims cur to [start..end]; end sitting on the final array index is
		// the off-by-one hot spot (trim(start, end + 1) must still fit the array).
		Fire out = SaveRoundtrip.of(fire);

		assertEquals(10, out.volume, "restored volume is recomputed from the trimmed data");
		assertEquals(level.length(), out.cur.length, "LENGTH must restore the full-size array");
		assertEquals(7, out.cur[5], "the trim start cell must survive exactly");
		assertEquals(3, out.cur[last], "the trim end cell (last array index) must survive exactly");
		assertEquals(0, out.cur[4], "just before the span: empty");
		assertEquals(0, out.cur[6], "inside the span but unseeded: empty");
		assertEquals(0, out.cur[last - 1], "just before the end cell: empty");
	}

	@Test
	void trimRoundtripSurvivesASingleCellAtTheVeryEndOfTheArray() throws IOException {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int last = level.length() - 1;

		// start == end == length-1: storeInBundle's end-scan loop never iterates
		// (end > start is false immediately), so this pins the degenerate trim window.
		Fire fire = new Fire();
		fire.seed(level, last, 6);

		Fire out = SaveRoundtrip.of(fire);

		assertEquals(6, out.volume);
		assertEquals(6, out.cur[last], "a lone blob on the last cell must roundtrip losslessly");
		assertEquals(0, out.cur[last - 1]);
	}

	@Test
	void unfedFireDiesInExactlySeedAmountActs() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int cell = 3 * level.width() + 3; // plain EMPTY floor, no flammables anywhere

		Fire fire = Blob.seed(cell, 5, Fire.class, level);

		// Fire.evolve decrements each burning cell by exactly 1 per act and EMPTY floor
		// never ignites, so a seed of 5 must survive 4 acts and die on the 5th.
		for (int i = 0; i < 4; i++) {
			fire.act();
		}
		assertEquals(1, fire.volume, "after seed-1 acts exactly one unit of fire remains");
		assertEquals(1, fire.cur[cell]);

		fire.act();
		assertEquals(0, fire.volume, "act number seed-amount extinguishes the fire");
		assertEquals(0, fire.cur[cell]);
		assertEquals(Terrain.EMPTY, level.map[cell],
				"dying on non-flammable floor leaves the terrain alone");
	}
}
