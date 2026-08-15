package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.Char;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.assets.Asset;
import com.qsr.customspd.assets.GeneralAsset;
import com.qsr.customspd.test.HeadlessGdx;
import com.watabou.utils.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wardstone Sentinel — the animated lock-golem. Verifies the designed depth-6 Prison stat
 * line (slow, heavily armored), the damage band, and that the armor band ({@code drRoll})
 * is real: it must floor at the ward's minimum, not at zero.
 */
class WardstoneSentinelTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@Test
	void statLineIsTheDesignedDepthSixBand() {
		Hero prevHero = Dungeon.hero;
		try {
			// Mob.speed() consults AscensionChallenge via Dungeon.hero; give it a plain
			// hero so speed() reduces to baseSpeed (no buffs on either side).
			Dungeon.hero = new Hero();
			WardstoneSentinel w = new WardstoneSentinel();
			assertEquals(30, w.HT, "designed Prison-band HP");
			assertEquals(6, w.EXP);
			assertEquals(13, w.maxLvl, "Prison band — must stop paying out where Caves mobs take over");
			assertEquals(0.75f, w.speed(), 0.001f,
					"unbuffed speed() is baseSpeed — the golem grinds at three-quarter speed");
			assertTrue(w.properties().contains(Char.Property.INORGANIC),
					"animated stone — bleed/poison-class effects must treat it as inorganic");
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void damageRollStaysInTheDesignedBandAndSpansIt() {
		// Bounds + coverage: 200 seeded rolls must stay in [4,8] AND actually reach both
		// endpoints — a mis-implementation that quietly narrows the band (e.g. wrong
		// min/max argument) passes a bounds-only check but fails the coverage half.
		WardstoneSentinel w = new WardstoneSentinel();
		boolean sawMin = false, sawMax = false;
		Random.pushGenerator(20260814L);
		try {
			for (int i = 0; i < 200; i++) {
				int dmg = w.damageRoll();
				assertTrue(dmg >= 4 && dmg <= 8, "damage must stay in [4,8], rolled " + dmg);
				if (dmg == 4) sawMin = true;
				if (dmg == 8) sawMax = true;
			}
		} finally {
			Random.popGenerator();
		}
		assertTrue(sawMin, "200 rolls must reach the band's minimum (4)");
		assertTrue(sawMax, "200 rolls must reach the band's maximum (8)");
	}

	@Test
	void armorBandIsRealAndFloorsAboveZero() {
		// The mechanic IS the armor: with no buffs, super.drRoll() contributes 0, so the
		// ward band [2,6] is the whole roll. Every roll must clear the ward's floor (2) —
		// a zero would mean the armor silently vanished — and stay under a sanity ceiling
		// of 10 (band max 6 + generous headroom for any future super contribution).
		WardstoneSentinel w = new WardstoneSentinel();
		boolean sawPositive = false;
		Random.pushGenerator(20260814L);
		try {
			for (int i = 0; i < 200; i++) {
				int dr = w.drRoll();
				assertTrue(dr >= 2, "every ward roll must clear the band's floor (2), rolled " + dr);
				assertTrue(dr <= 10, "ward roll sanity ceiling exceeded, rolled " + dr);
				if (dr > 0) sawPositive = true;
			}
		} finally {
			Random.popGenerator();
		}
		assertTrue(sawPositive, "the armor must be real — some roll must exceed 0");
	}

	@Test
	void spriteAssetIsWiredAndOnDisk() {
		String path = Asset.getAssetFilePath(GeneralAsset.WARDSTONE_SENTINEL);
		assertNotNull(path, "GeneralAsset.WARDSTONE_SENTINEL must resolve to a path");
		assertTrue(com.badlogic.gdx.Gdx.files.internal(path).exists(),
				"the wardstone sentinel sprite must exist at " + path);
	}
}
