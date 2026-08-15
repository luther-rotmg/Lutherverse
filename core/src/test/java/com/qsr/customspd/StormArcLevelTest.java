package com.qsr.customspd;

import com.qsr.customspd.actors.Actor;
import com.qsr.customspd.actors.Char;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.hero.spheregrid.SphereGrid;
import com.qsr.customspd.actors.hero.spheregrid.SphereNode;
import com.qsr.customspd.actors.mobs.Mob;
import com.qsr.customspd.actors.mobs.Rat;
import com.qsr.customspd.items.weapon.melee.StormKeyblade;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.HeadlessLevel;
import com.watabou.utils.PathFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * F3 integration tests: the storm keyblade's arc on a REAL level (HeadlessLevel fixture).
 * The headless combat tests prove the arc is level-null safe; these prove the arc's actual
 * targeting semantics — fixed NEIGHBOURS8 scan order (determinism), strict ENEMY alignment
 * (bystander safety), edge behaviour (no row wrap), and the end-to-end proc damage.
 */
class StormArcLevelTest {

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
	void arcPicksTheFirstAdjacentEnemyInNeighbours8Order() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int center = 3 * level.width() + 3;

		Hero hero = new Hero();
		Dungeon.hero = hero;
		Mob defender = HeadlessLevel.at(new Rat(), center);
		// Two candidate enemies adjacent to the defender; NEIGHBOURS8's fixed order must
		// decide, independent of placement order (the determinism contract).
		Mob late = HeadlessLevel.at(new Rat(), center + PathFinder.NEIGHBOURS8[5]);
		Mob early = HeadlessLevel.at(new Rat(), center + PathFinder.NEIGHBOURS8[2]);

		assertSame(early, StormKeyblade.arcTarget(hero, defender),
				"the arc must follow NEIGHBOURS8 scan order, not actor insertion order");
	}

	@Test
	void arcNeverStrikesNeutralBystanders() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int center = 3 * level.width() + 3;

		Hero hero = new Hero();
		Dungeon.hero = hero;
		Mob defender = HeadlessLevel.at(new Rat(), center);
		// A neutral NPC-stand-in adjacent in an EARLIER scan slot than the real enemy.
		Mob neutral = HeadlessLevel.at(new Rat(), center + PathFinder.NEIGHBOURS8[0]);
		neutral.alignment = Char.Alignment.NEUTRAL;
		Mob enemy = HeadlessLevel.at(new Rat(), center + PathFinder.NEIGHBOURS8[6]);

		assertSame(enemy, StormKeyblade.arcTarget(hero, defender),
				"a neutral bystander must be skipped even when it scans first");

		// Only the neutral adjacent -> no arc at all.
		Actor.remove(enemy);
		assertNull(StormKeyblade.arcTarget(hero, defender),
				"no ENEMY adjacent -> the arc grounds out");
	}

	@Test
	void arcRejectsRowWrapAtTheLevelEdge() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int w = level.width();
		// Defender on column 0 of row 3; a char on the LAST column of row 2 is at
		// defender.pos - 1 numerically but is NOT adjacent (row wrap).
		int edge = 3 * w;

		Hero hero = new Hero();
		Dungeon.hero = hero;
		Mob defender = HeadlessLevel.at(new Rat(), edge);
		HeadlessLevel.at(new Rat(), edge - 1); // previous row's far side

		assertNull(StormKeyblade.arcTarget(hero, defender),
				"pos-1 across a row boundary must not count as adjacent");
	}

	@Test
	void stormProcArcsRealDamageToTheAdjacentEnemy() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int center = 3 * level.width() + 3;

		Hero hero = new Hero();
		hero.sphereGrid = new SphereGrid();
		hero.sphereGrid.grantPoints(3);
		hero.sphereGrid.activate(SphereNode.ATTUNEMENT);
		hero.sphereGrid.activate(SphereNode.STORM_I);
		hero.sphereGrid.activate(SphereNode.STORM_II); // storm 3
		Dungeon.hero = hero;

		Mob defender = HeadlessLevel.at(new Rat(), center);
		defender.HP = defender.HT = 100;
		Mob bystander = HeadlessLevel.at(new Rat(), center + PathFinder.NEIGHBOURS8[0]);
		bystander.HP = bystander.HT = 100;

		new StormKeyblade().proc(hero, defender, 10);

		// storm 3: defender takes 3 shock (no ability nodes -> no discharge);
		// the arc carries (3+1)/2 = 2 to the adjacent enemy.
		assertEquals(100 - 3, defender.HP, "shock bonus = stormLevel");
		assertEquals(100 - 2, bystander.HP, "arc = (storm+1)/2 to the adjacent enemy");
	}
}
