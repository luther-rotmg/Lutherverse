package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.Actor;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.assets.Asset;
import com.qsr.customspd.assets.GeneralAsset;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.HeadlessLevel;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TumblerWisp — the blink-on-wound Sewers mote. Verifies the stat line, the damage band,
 * and the blink mechanic on a REAL level (HeadlessLevel fixture): a wounding hit must move
 * the wisp to a free adjacent passable cell, a boxed-in or unwounded wisp must stay put.
 */
class TumblerWispTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@BeforeEach
	void setUp() {
		// Dungeon.hero must not be the attacking hero, or Mob.defenseProc's surprise-attack
		// branch fires and touches sprites/audio that do not exist headless.
		Dungeon.hero = null;
	}

	@AfterEach
	void tearDown() {
		HeadlessLevel.uninstall();
		Dungeon.hero = null;
	}

	@Test
	void statLineIsTheDesignedDepthTwoBand() {
		TumblerWisp w = new TumblerWisp();
		assertEquals(8, w.HT, "paper HP — the blink is the defense, not the health bar");
		assertEquals(2, w.EXP);
		assertEquals(7, w.maxLvl, "a depth-2 Sewers mote must not out-scale later Sewers mobs");
		assertTrue(w.flying, "a wisp flies — water and grass never pin it down");
		assertTrue(w.lootChance > 0, "it carries a fleck of the locksmith's tribute (gold)");
	}

	@Test
	void damageRollStaysInTheDesignedBandAndSpansIt() {
		// Bounds + coverage: 200 seeded rolls must stay in [1,4] AND actually reach both
		// endpoints — a mis-implementation that quietly narrows the band passes a
		// bounds-only check but fails the coverage half.
		TumblerWisp w = new TumblerWisp();
		boolean sawMin = false, sawMax = false;
		Random.pushGenerator(20260814L);
		try {
			for (int i = 0; i < 200; i++) {
				int dmg = w.damageRoll();
				assertTrue(dmg >= 1 && dmg <= 4, "damage must stay in [1,4], rolled " + dmg);
				if (dmg == 1) sawMin = true;
				if (dmg == 4) sawMax = true;
			}
		} finally {
			Random.popGenerator();
		}
		assertTrue(sawMin, "200 rolls must reach the band's minimum (1)");
		assertTrue(sawMax, "200 rolls must reach the band's maximum (4)");
	}

	@Test
	void aWoundingHitBlinksTheWispToAFreeAdjacentCell() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int center = 3 * level.width() + 3;

		Hero hero = new Hero();
		TumblerWisp wisp = HeadlessLevel.at(new TumblerWisp(), center);

		Random.pushGenerator(20260814L);
		try {
			wisp.defenseProc(hero, 3);
		} finally {
			Random.popGenerator();
		}

		assertNotEquals(center, wisp.pos, "a wounding hit must tumble the wisp off its cell");
		assertTrue(level.adjacent(center, wisp.pos), "the blink is one tumble, not a teleport");
		assertTrue(level.passable[wisp.pos], "the wisp must land on a passable cell");
		assertSame(wisp, Actor.findChar(wisp.pos),
				"the actor system must see the wisp at its new cell");
	}

	@Test
	void aBoxedInWispCannotBlink() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int center = 3 * level.width() + 3;

		Hero hero = new Hero();
		TumblerWisp wisp = HeadlessLevel.at(new TumblerWisp(), center);
		for (int offset : PathFinder.NEIGHBOURS8) {
			HeadlessLevel.at(new Rat(), center + offset);
		}

		Random.pushGenerator(20260814L);
		try {
			wisp.defenseProc(hero, 3);
		} finally {
			Random.popGenerator();
		}

		assertEquals(center, wisp.pos, "with every neighbour occupied there is nowhere to tumble");
	}

	@Test
	void aFullyBlockedHitDoesNotBlink() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int center = 3 * level.width() + 3;

		Hero hero = new Hero();
		TumblerWisp wisp = HeadlessLevel.at(new TumblerWisp(), center);

		Random.pushGenerator(20260814L);
		try {
			wisp.defenseProc(hero, 0);
		} finally {
			Random.popGenerator();
		}

		assertEquals(center, wisp.pos, "a hit that deals no damage must not spring the tumble");
	}

	@Test
	void spriteAssetIsWiredAndOnDisk() {
		String path = Asset.getAssetFilePath(GeneralAsset.TUMBLER_WISP);
		assertNotNull(path, "GeneralAsset.TUMBLER_WISP must resolve to a path");
		assertTrue(com.badlogic.gdx.Gdx.files.internal(path).exists(),
				"the tumbler wisp sprite must exist at " + path);
	}
}
