package com.qsr.customspd.actors.buffs;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.mobs.Rat;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.HeadlessLevel;
import com.qsr.customspd.test.SaveRoundtrip;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ParalysisResist accumulates the damage a paralysed target has taken (Paralysis.processDamage)
 * and uses it to shake off later paralysis. Its store/restore bodies were once swapped, so the
 * counter was zeroed on save and never restored.
 */
class ParalysisResistTest {

	private int prevDepth;
	private Hero prevHero;

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@BeforeEach
	void saveStatics() {
		prevDepth = Dungeon.depth;
		prevHero = Dungeon.hero;
		Dungeon.depth = 0;
		Dungeon.hero = null;
	}

	@AfterEach
	void tearDown() {
		HeadlessLevel.uninstall();
		Dungeon.depth = prevDepth;
		Dungeon.hero = prevHero;
	}

	@Test
	void accumulatedDamageSurvivesBundleRoundtrip() throws Exception {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		Rat rat = new Rat();
		rat.HP = rat.HT = 100000; // the break-free roll must not fire and detach Paralysis
		HeadlessLevel.at(rat, 3 * level.width() + 3);

		Paralysis paralysis = Buff.affect(rat, Paralysis.class, Paralysis.DURATION);
		Random.pushGenerator(20260822L);
		try {
			paralysis.processDamage(7);
			paralysis.processDamage(5);
		} finally {
			Random.popGenerator();
		}
		assertNotNull(rat.buff(Paralysis.class), "test setup: paralysis must still be attached");
		Paralysis.ParalysisResist resist = rat.buff(Paralysis.ParalysisResist.class);
		assertNotNull(resist, "processDamage must attach a ParalysisResist");

		// Storing must write the counter and must not disturb it.
		Bundle stored = new Bundle();
		resist.storeInBundle(stored);
		assertEquals(12, SaveRoundtrip.writeRead(stored).getInt("damage"),
				"storeInBundle must write the accumulated damage (7 + 5)");

		// And a full write/read -> restore -> store cycle must carry it through.
		Paralysis.ParalysisResist restored = SaveRoundtrip.of(resist);
		Bundle again = new Bundle();
		restored.storeInBundle(again);
		assertEquals(12, SaveRoundtrip.writeRead(again).getInt("damage"),
				"restoreFromBundle must read the stored damage back");
	}
}
