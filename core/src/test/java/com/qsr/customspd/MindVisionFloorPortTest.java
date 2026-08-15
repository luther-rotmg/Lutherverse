package com.qsr.customspd;

import com.qsr.customspd.actors.buffs.Awareness;
import com.qsr.customspd.actors.buffs.Buff;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.levels.Level;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.HeadlessLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Port regression test for upstream SPD 51168138c ("fixed awareness persisting between
 * floors with high speed"): {@link Level#beforeTransition()} must detach the hero's
 * {@link Awareness} buff, exactly like the other non-persisting transition buffs it
 * already clears. Runs on the F3 HeadlessLevel fixture so the detach path's
 * Dungeon.observe() call executes against a real level.
 */
class MindVisionFloorPortTest {

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
	void awarenessDoesNotPersistAcrossFloorTransitions() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);

		Hero hero = new Hero();
		Dungeon.hero = hero;
		HeadlessLevel.at(hero, 3 * level.width() + 3);

		Buff.affect(hero, Awareness.class, Awareness.DURATION);
		assertNotNull(hero.buff(Awareness.class), "precondition: awareness attached to the hero");

		Level.beforeTransition();

		assertNull(hero.buff(Awareness.class),
				"upstream 51168138c: awareness must be detached before a floor transition");
	}
}
