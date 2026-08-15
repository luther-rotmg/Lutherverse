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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Buff clock &amp; stacking suite. The buff system is ~70 classes of shared clock plumbing
 * (Actor.spend/postpone/cooldown, attach/detach exclusions, bundled durations) with the
 * highest silent-regression risk after a save/load rework, so this suite pins:
 *
 *  - Burning's per-tick contract: damage inside its formula band, left decrementing by
 *    exactly TICK, expiry after DURATION (8) ticks (F1 + F3: acts run on a real
 *    HeadlessLevel because Burning.act() dereferences Dungeon.level.flamable/water).
 *  - Water extinguishing: Burning.act() detaches when the target stands in water and is
 *    not flying — and, deliberately, only AFTER that tick's damage has landed.
 *  - The Burning/Chill mutual exclusion, in both attach directions.
 *  - F2: a mid-duration Burning survives a real Bundle write/read with its remaining
 *    'left' intact (both the raw save-format keys and the resumed behaviour).
 *  - Buff.affect vs prolong vs append duration math on a FlavourBuff (Cripple):
 *    affect(cls, dur) ACCUMULATES via spend, prolong takes max(current, new) via
 *    postpone, append duplicates.
 *
 * All acts run at Dungeon.depth = 0, so Burning's roll Random.NormalIntRange(1, 3 +
 * scalingDepth()/4) is the band [1,3].
 */
class BuffClockTest {

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
		Dungeon.depth = 0;   // Burning's damage band becomes [1, 3]
		Dungeon.hero = null; // scalingDepth() and Burning.act() are hero-null safe for mobs
	}

	@AfterEach
	void tearDown() {
		HeadlessLevel.uninstall();
		Dungeon.depth = prevDepth;
		Dungeon.hero = prevHero;
	}

	/** A 100-HP rat placed on an open (EMPTY, non-flammable) cell of a fresh level. */
	private Rat placeRat(HeadlessLevel level, int cell) {
		Rat rat = new Rat();
		rat.HP = rat.HT = 100;
		return HeadlessLevel.at(rat, cell);
	}

	@Test
	void burningTickDamagesInBandDecrementsLeftAndSpendsTick() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		Rat rat = placeRat(level, 3 * level.width() + 3);

		Burning burning = Buff.affect(rat, Burning.class);
		burning.reignite(rat); // left = DURATION = 8
		assertEquals("8", burning.iconTextDisplay(), "reignite must set left to the full 8 turns");

		Random.pushGenerator(20260814L);
		try {
			burning.act();
		} finally {
			Random.popGenerator();
		}

		int dropped = 100 - rat.HP;
		assertTrue(dropped >= 1 && dropped <= 3,
				"one tick at depth 0 must deal NormalIntRange(1,3), dealt " + dropped);
		assertNotNull(rat.buff(Burning.class), "7 turns remain -> still burning");
		assertEquals("7", burning.iconTextDisplay(), "one act must decrement left by exactly TICK");
		assertEquals(1f, burning.cooldown(), 0.001f, "one act must spend exactly TICK on the buff clock");

		Random.pushGenerator(20260815L);
		try {
			burning.act();
			burning.act();
		} finally {
			Random.popGenerator();
		}
		assertEquals("5", burning.iconTextDisplay(), "three acts -> left = 5");
		assertEquals(3f, burning.cooldown(), 0.001f, "three acts -> 3 turns spent");
		int total = 100 - rat.HP;
		assertTrue(total >= 3 && total <= 9, "three ticks must total [3,9] damage, dealt " + total);
	}

	@Test
	void burningDamageSpansTheFullFormulaBand() {
		// Bounds + endpoint coverage: a mis-implementation that quietly narrows the roll
		// (wrong min/max argument, off-by-one in the depth scaling) passes a bounds-only
		// check but fails the coverage half.
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		Rat rat = placeRat(level, 3 * level.width() + 3);
		Burning burning = Buff.affect(rat, Burning.class);

		boolean sawMin = false, sawMax = false;
		Random.pushGenerator(424242L);
		try {
			for (int i = 0; i < 100; i++) {
				burning.reignite(rat); // keep left high so expiry never interferes
				rat.HP = 100;
				burning.act();
				int dmg = 100 - rat.HP;
				assertTrue(dmg >= 1 && dmg <= 3, "tick damage must stay in [1,3], dealt " + dmg);
				if (dmg == 1) sawMin = true;
				if (dmg == 3) sawMax = true;
			}
		} finally {
			Random.popGenerator();
		}
		assertTrue(sawMin, "100 seeded ticks must reach the band's minimum (1)");
		assertTrue(sawMax, "100 seeded ticks must reach the band's maximum (3)");
	}

	@Test
	void burningExpiresAfterExactlyEightTicks() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		Rat rat = placeRat(level, 3 * level.width() + 3);

		Burning burning = Buff.affect(rat, Burning.class);
		burning.reignite(rat); // left = 8

		Random.pushGenerator(77L);
		try {
			for (int i = 0; i < 7; i++) {
				burning.act();
			}
			assertNotNull(rat.buff(Burning.class), "after 7 of 8 turns the burn must persist");
			burning.act(); // left hits 0 -> detach
		} finally {
			Random.popGenerator();
		}
		assertNull(rat.buff(Burning.class), "the 8th tick must extinguish a full-duration burn");
	}

	@Test
	void waterExtinguishesBurningButOnlyAfterTheTicksDamage() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int cell = 3 * level.width() + 3;
		Rat rat = placeRat(level, cell);
		// water is a plain flag array (buildFlagMaps: water[i] = (flags & LIQUID) != 0);
		// setting the flag directly is exactly what a WATER tile would have produced.
		level.water[cell] = true;

		Burning burning = Buff.affect(rat, Burning.class);
		burning.reignite(rat);

		Random.pushGenerator(1234L);
		try {
			burning.act();
		} finally {
			Random.popGenerator();
		}

		assertNull(rat.buff(Burning.class), "standing in water must detach Burning on its act");
		// Burning.act() rolls damage BEFORE the water check: stepping into water does not
		// retroactively spare you the tick you are extinguished on. Pinned deliberately.
		assertTrue(rat.HP < 100, "the extinguishing tick still deals its damage first");
	}

	@Test
	void flyingTargetsBurnOverWater() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int cell = 3 * level.width() + 3;
		Rat rat = placeRat(level, cell);
		rat.flying = true;
		level.water[cell] = true;

		Burning burning = Buff.affect(rat, Burning.class);
		burning.reignite(rat);

		Random.pushGenerator(1234L);
		try {
			burning.act();
		} finally {
			Random.popGenerator();
		}

		assertNotNull(rat.buff(Burning.class),
				"a flying char above water must keep burning (water check requires !flying)");
		assertEquals("7", burning.iconTextDisplay(), "the burn keeps ticking down normally");
	}

	@Test
	void chillAndBurningDetachEachOtherOnAttachBothDirections() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		Rat rat = placeRat(level, 3 * level.width() + 3);

		// Direction 1: Chill.attachTo detaches an existing Burning.
		Burning burning = Buff.affect(rat, Burning.class);
		burning.reignite(rat);
		assertNotNull(rat.buff(Burning.class));

		Buff.affect(rat, Chill.class, 5f);
		assertNull(rat.buff(Burning.class), "chilling a burning target must extinguish the burn");
		assertNotNull(rat.buff(Chill.class), "and the chill itself must land");

		// Direction 2: Burning.attachTo detaches an existing Chill.
		Buff.affect(rat, Burning.class).reignite(rat);
		assertNull(rat.buff(Chill.class), "igniting a chilled target must melt the chill");
		assertNotNull(rat.buff(Burning.class), "and the burn itself must land");
	}

	@Test
	void burningSaveFormatKeysAreStable() throws Exception {
		// Pins the SERIALIZED CONTRACT: existing saves store Burning under these exact
		// keys, so a rename silently zeroes every mid-burn save. Deliberate format pin.
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		Rat rat = placeRat(level, 3 * level.width() + 3);
		Burning burning = Buff.affect(rat, Burning.class);
		burning.reignite(rat, 5.5f);

		Bundle out = new Bundle();
		burning.storeInBundle(out);
		Bundle in = SaveRoundtrip.writeRead(out);

		assertTrue(in.contains("left"), "Burning must store its remaining duration under 'left'");
		assertEquals(5.5f, in.getFloat("left"), 0.001f, "the partial duration must survive the write/read");
		assertTrue(in.contains("burnIncrement"), "the hero item-burn counter key must survive");
		assertEquals(0, in.getInt("burnIncrement"));
	}

	@Test
	void midDurationRoundtripResumesWithRemainingBurnTime() throws Exception {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		Rat rat = placeRat(level, 3 * level.width() + 3);
		Burning original = Buff.affect(rat, Burning.class);
		original.reignite(rat, 5.5f); // mid-duration: NOT the fresh 8

		Burning restored = SaveRoundtrip.of(original);

		assertEquals("5", restored.iconTextDisplay(), "(int)left of the restored buff must be 5");
		assertEquals((8f - 5.5f) / 8f, restored.iconFadePercent(), 0.001f,
				"fade percent must reflect the restored partial duration, not a fresh one");

		// Behavioural half: the restored buff resumes at 5.5 turns, so it must survive
		// exactly 5 more acts and detach on the 6th (a fresh burn would take 8).
		Rat rat2 = placeRat(level, 4 * level.width() + 4);
		restored.attachTo(rat2);
		Random.pushGenerator(99L);
		try {
			for (int i = 0; i < 5; i++) {
				restored.act();
			}
			assertNotNull(rat2.buff(Burning.class), "0.5 turns remain after 5 acts -> still burning");
			restored.act();
		} finally {
			Random.popGenerator();
		}
		assertNull(rat2.buff(Burning.class), "the 6th act after restore must extinguish (5.5 -> -0.5)");
	}

	@Test
	void affectAccumulatesProlongTakesMaxAndAppendDuplicates() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		Rat rat = placeRat(level, 3 * level.width() + 3);

		// affect(cls, dur) spends the duration onto the buff clock: repeated affects ADD.
		Cripple cripple = Buff.affect(rat, Cripple.class, 10f);
		assertEquals(10f, cripple.cooldown(), 0.001f, "fresh affect -> cooldown = duration");
		Cripple again = Buff.affect(rat, Cripple.class, 5f);
		assertSame(cripple, again, "affect must reuse the existing instance, never duplicate");
		assertEquals(15f, cripple.cooldown(), 0.001f, "a second affect ACCUMULATES (10 + 5)");

		// prolong postpones: time = max(current, now + duration). Shorter/equal -> no-op.
		Buff.prolong(rat, Cripple.class, 12f);
		assertEquals(15f, cripple.cooldown(), 0.001f, "prolong with less time remaining must not shorten");
		Buff.prolong(rat, Cripple.class, 20f);
		assertEquals(20f, cripple.cooldown(), 0.001f, "prolong with more time must raise to exactly that");

		// append is the explicit duplication path.
		Buff.append(rat, Cripple.class, 4f);
		assertEquals(2, rat.buffs(Cripple.class).size(), "append must attach a SECOND instance");
	}
}
