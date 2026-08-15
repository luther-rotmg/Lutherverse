package com.qsr.customspd.actors.hero;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.hero.spheregrid.SphereGridProgress;
import com.qsr.customspd.test.HeadlessGdx;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * F1: hero levelling economy. Pins the exp curve, the HT growth per level, and — critically —
 * the sphere-grid faucet inside {@link Hero#earnExp}'s level-up loop: exactly ONE run-scoped
 * grid point and ONE persistent Insight per level gained, even when a single earnExp call
 * crosses several level boundaries. A double-grant there would be invisible in play (numbers
 * only ever go up) and would quietly inflate the whole meta-progression economy.
 */
class HeroProgressionTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	// Insight persists in a real file store shared across the test JVM; keep it zeroed
	// on both sides so this suite neither inherits nor leaks meta-progression state.
	@BeforeEach
	void cleanPersistentInsightBefore() {
		SphereGridProgress.reset();
	}

	@AfterEach
	void cleanPersistentInsightAfter() {
		SphereGridProgress.reset();
	}

	@Test
	void multiLevelEarnExpGrantsExactlyOnePointAndOneInsightPerLevel() {
		Hero prevHero = Dungeon.hero;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;
			Random.pushGenerator(20260814L);
			try {
				HeroClass.KEYBEARER.initHero(hero);

				assertEquals(1, hero.lvl);
				assertEquals(0, hero.sphereGrid.unspentPoints(), "fresh grid starts with no points");
				assertEquals(0, SphereGridProgress.insight());

				// 2.5 levels of exp in ONE call, computed from the maxExp curve:
				// level 1->2 costs maxExp(1)=10, 2->3 costs maxExp(2)=15, plus half of
				// maxExp(3)=20 left over -> 10 + 15 + 10 = 35.
				int grant = Hero.maxExp(1) + Hero.maxExp(2) + Hero.maxExp(3) / 2;
				assertEquals(35, grant);
				hero.earnExp(grant, null);

				assertEquals(3, hero.lvl, "35 exp from level 1 must land exactly 2 level-ups");
				assertEquals(10, hero.exp, "residual exp after the loop: 35 - 10 - 15 = 10");
				// The faucet fires once per loop iteration. 2 iterations -> exactly 2 of each;
				// a second grant site (e.g. in the post-loop levelUp block) would read 4 here.
				assertEquals(2, hero.sphereGrid.unspentPoints(),
						"exactly one run-scoped grid point per level gained");
				assertEquals(2, SphereGridProgress.insight(),
						"exactly one persistent Insight per level gained — the single-grant pin");
			} finally {
				Random.popGenerator();
			}
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void exactMaxExpBoundaryLevelsOnceWithASingleFaucetGrant() {
		Hero prevHero = Dungeon.hero;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;
			Random.pushGenerator(20260814L);
			try {
				HeroClass.KEYBEARER.initHero(hero);

				// Exactly maxExp(1): the while loop's >= boundary must level once and stop clean.
				hero.earnExp(Hero.maxExp(1), null);

				assertEquals(2, hero.lvl);
				assertEquals(0, hero.exp, "an exact-cost grant leaves zero residual exp");
				assertEquals(1, hero.sphereGrid.unspentPoints());
				assertEquals(1, SphereGridProgress.insight());
			} finally {
				Random.popGenerator();
			}
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void nonKeybearerLevelUpGrantsNoGridPointsAndNoInsight() {
		Hero prevHero = Dungeon.hero;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;
			Random.pushGenerator(20260814L);
			try {
				HeroClass.WARRIOR.initHero(hero);
				assertNull(hero.sphereGrid, "only the Keybearer gets a sphere grid");
				assertEquals(0, SphereGridProgress.insight());

				hero.earnExp(Hero.maxExp(1), null);

				assertEquals(2, hero.lvl, "the warrior still levels normally");
				assertNull(hero.sphereGrid, "levelling must not conjure a grid for other classes");
				assertEquals(0, SphereGridProgress.insight(),
						"a non-Keybearer level-up must not feed the persistent Insight faucet");
			} finally {
				Random.popGenerator();
			}
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void maxExpCurveIsFivePlusFiveTimesLevel() {
		// Design-contract pin, stated deliberately: the meta-economy math (Insight per floor,
		// Insight Crystal pricing) is derived from this curve. Hero.maxExp(lvl) is 5 + 5*lvl;
		// if the curve is ever rebalanced this must be updated IN THE SAME COMMIT as the
		// economy numbers that depend on it.
		for (int lvl = 1; lvl <= Hero.MAX_LEVEL; lvl++) {
			assertEquals(5 + 5 * lvl, Hero.maxExp(lvl), "maxExp(" + lvl + ")");
		}

		// The instance form must delegate to the same curve at the hero's current level.
		Hero hero = new Hero();
		hero.lvl = 7;
		assertEquals(Hero.maxExp(7), hero.maxExp());
	}

	@Test
	void levelUpRaisesHTByExactlyFiveAndBoostsCurrentHP() {
		Hero prevHero = Dungeon.hero;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;

			// A fresh hero: HT formula is 20 + 5*(lvl-1) (+boosts, all zero here).
			hero.updateHT(false);
			assertEquals(20, hero.HT);
			assertEquals(20, hero.HP);

			hero.earnExp(Hero.maxExp(1), null);

			assertEquals(2, hero.lvl);
			assertEquals(25, hero.HT, "one level-up must raise HT by exactly 5");
			assertEquals(25, hero.HP, "earnExp's updateHT(true) grants the new HT as current HP too");
		} finally {
			Dungeon.hero = prevHero;
		}
	}
}
