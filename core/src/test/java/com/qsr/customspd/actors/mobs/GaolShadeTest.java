package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.Dungeon;
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
 * GaolShade — the Keywraith's crueler kin. Verifies the filled-in mechanic (the gold-tribute
 * proc: a fixed cut per damaging hit, capped by what the hero carries, never below zero),
 * the depth-7 stat line, and the wiring the scaffold claims to have done (sprite asset
 * resolvable on disk).
 */
class GaolShadeTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@Test
	void statLineIsTheDesignedDepthSevenBand() {
		GaolShade s = new GaolShade();
		assertEquals(22, s.HT, "designed HP band (Prison tier, above the Keywraith)");
		assertEquals(7, s.EXP);
		assertEquals(14, s.maxLvl, "Prison band — a depth-7 mob, one tier above the Keywraith");
		assertTrue(s.lootChance > 0, "the tax-collector hoards tribute (gold loot)");
		assertTrue(s.properties().contains(com.qsr.customspd.actors.Char.Property.UNDEAD),
				"it is described as undead, so holy damage must treat it as one");
		assertTrue(s.properties().contains(com.qsr.customspd.actors.Char.Property.INORGANIC),
				"a spectre has no flesh — bleed/poison-style effects must not apply");
	}

	@Test
	void damageRollStaysInTheDesignedBandAndSpansIt() {
		// Bounds + coverage: 200 seeded rolls must stay in [3,7] AND actually reach both
		// endpoints — a mis-implementation that quietly narrows the band (e.g. wrong
		// min/max argument) passes a bounds-only check but fails the coverage half.
		GaolShade s = new GaolShade();
		boolean sawMin = false, sawMax = false;
		Random.pushGenerator(20260814L);
		try {
			for (int i = 0; i < 200; i++) {
				int dmg = s.damageRoll();
				assertTrue(dmg >= 3 && dmg <= 7, "damage must stay in [3,7], rolled " + dmg);
				if (dmg == 3) sawMin = true;
				if (dmg == 7) sawMax = true;
			}
		} finally {
			Random.popGenerator();
		}
		assertTrue(sawMin, "200 rolls must reach the band's minimum (3)");
		assertTrue(sawMax, "200 rolls must reach the band's maximum (7)");
	}

	@Test
	void stealsTenWhenRich() {
		Hero prevHero = Dungeon.hero;
		int prevGold = Dungeon.gold;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;
			Dungeon.gold = 100;

			new GaolShade().attackProc(hero, 3);
			assertEquals(90, Dungeon.gold, "a damaging hit must collect exactly the fixed 10-gold cut");
		} finally {
			Dungeon.hero = prevHero;
			Dungeon.gold = prevGold;
		}
	}

	@Test
	void stealsOnlyWhatExists() {
		Hero prevHero = Dungeon.hero;
		int prevGold = Dungeon.gold;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;
			Dungeon.gold = 3;

			new GaolShade().attackProc(hero, 3);
			assertEquals(0, Dungeon.gold, "the cut is capped by what the hero carries — never negative");
		} finally {
			Dungeon.hero = prevHero;
			Dungeon.gold = prevGold;
		}
	}

	@Test
	void noGoldNoTheft() {
		Hero prevHero = Dungeon.hero;
		int prevGold = Dungeon.gold;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;
			Dungeon.gold = 0;

			new GaolShade().attackProc(hero, 3);
			assertEquals(0, Dungeon.gold, "an empty purse must stay at zero, without any exception");
		} finally {
			Dungeon.hero = prevHero;
			Dungeon.gold = prevGold;
		}
	}

	@Test
	void zeroDamageNoTheft() {
		Hero prevHero = Dungeon.hero;
		int prevGold = Dungeon.gold;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;
			Dungeon.gold = 100;

			new GaolShade().attackProc(hero, 0);
			assertEquals(100, Dungeon.gold, "a fully-blocked hit must not collect tribute");
		} finally {
			Dungeon.hero = prevHero;
			Dungeon.gold = prevGold;
		}
	}

	@Test
	void spriteAssetIsWiredAndOnDisk() {
		String path = Asset.getAssetFilePath(GeneralAsset.GAOL_SHADE);
		assertNotNull(path, "GeneralAsset.GAOL_SHADE must resolve to a path");
		assertTrue(com.badlogic.gdx.Gdx.files.internal(path).exists(),
				"the gaol shade sprite must exist at " + path);
	}
}
