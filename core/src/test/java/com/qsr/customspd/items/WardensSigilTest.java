package com.qsr.customspd.items;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.Char;
import com.qsr.customspd.actors.buffs.Cripple;
import com.qsr.customspd.actors.mobs.Mob;
import com.qsr.customspd.actors.mobs.Rat;
import com.qsr.customspd.items.stones.Runestone;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.HeadlessLevel;
import com.watabou.utils.PathFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Warden's Sigil — the STONE-pool lock-stone. Verifies the pool contract (Runestone,
 * stackable, identified, priceable) and the aimed-vs-splash asymmetry on a REAL level
 * (HeadlessLevel fixture): the direct target may be ANY char because the player aimed
 * the throw, while the passive splash is strictly ENEMY-only so neutral NPCs standing
 * beside the impact are never held.
 */
class WardensSigilTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@AfterEach
	void tearDown() {
		HeadlessLevel.uninstall();
		Dungeon.hero = null;
	}

	/** activate(cell) is the runestone shatter hook; invoke it directly (no scene). */
	private static void activate(WardensSigil stone, int cell) throws Exception {
		Method activate = WardensSigil.class.getDeclaredMethod("activate", int.class);
		activate.setAccessible(true);
		activate.invoke(stone, cell);
	}

	@Test
	void isARunestonePoolCitizen() {
		WardensSigil sigil = new WardensSigil();
		assertTrue(sigil instanceof Runestone, "STONE pool items must be Runestones");
		assertTrue(sigil.stackable, "runestones stack");
		assertTrue(sigil.isIdentified(), "runestones need no identification");
		assertTrue(sigil.value() > 0, "shops must be able to price it");
	}

	@Test
	void locksTheDirectTarget() throws Exception {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int cell = 3 * level.width() + 3;
		Mob rat = HeadlessLevel.at(new Rat(), cell);

		activate(new WardensSigil(), cell);

		assertNotNull(rat.buff(Cripple.class),
				"the char on the struck cell must be locked");
	}

	@Test
	void splashHoldsAdjacentEnemiesOnly() throws Exception {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int cell = 3 * level.width() + 3;
		Mob enemy = HeadlessLevel.at(new Rat(), cell + PathFinder.NEIGHBOURS8[0]);
		Mob neutral = HeadlessLevel.at(new Rat(), cell + PathFinder.NEIGHBOURS8[7]);
		neutral.alignment = Char.Alignment.NEUTRAL;

		activate(new WardensSigil(), cell);

		assertNotNull(enemy.buff(Cripple.class),
				"an ENEMY beside the impact must be held by the spill");
		assertNull(neutral.buff(Cripple.class),
				"the passive splash must never hold a neutral bystander");
	}

	@Test
	void directHitMayLockAnyChar() throws Exception {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int cell = 3 * level.width() + 3;
		Mob neutral = HeadlessLevel.at(new Rat(), cell);
		neutral.alignment = Char.Alignment.NEUTRAL;

		activate(new WardensSigil(), cell);

		assertNotNull(neutral.buff(Cripple.class),
				"the player aimed at it — a neutral char ON the cell is fair game");
	}

	@Test
	void emptyCellNoCrash() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int cell = 3 * level.width() + 3;

		assertDoesNotThrow(() -> activate(new WardensSigil(), cell),
				"shattering on an empty cell with no neighbours must be a no-op");
	}
}
