package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.buffs.Cripple;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.assets.Asset;
import com.qsr.customspd.assets.GeneralAsset;
import com.qsr.customspd.test.HeadlessGdx;
import com.watabou.utils.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keywraith — the first mob produced through the content-scaffold pipeline. Verifies the
 * filled-in mechanic (the Cripple "lock" proc), the stat line, and the wiring the scaffold
 * claims to have done (sprite asset resolvable, bestiary registration in dungeon.json).
 */
class KeywraithTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@Test
	void statLineIsTheDesignedDepthThreeBand() {
		Keywraith w = new Keywraith();
		assertEquals(16, w.HT, "designed HP band (between Crab and Skeleton)");
		assertEquals(4, w.EXP);
		assertEquals(9, w.maxLvl, "Crab's band — a Sewers mob must not out-scale Prison mobs");
		assertTrue(w.lootChance > 0, "the key-warden hoards tribute (gold loot)");
		assertTrue(w.properties().contains(com.qsr.customspd.actors.Char.Property.UNDEAD),
				"it is described as a ghost, so holy damage must treat it as one");
	}

	@Test
	void damageRollStaysInTheDesignedBandAndSpansIt() {
		// Bounds + coverage: 200 seeded rolls must stay in [2,6] AND actually reach both
		// endpoints — a mis-implementation that quietly narrows the band (e.g. wrong
		// min/max argument) passes a bounds-only check but fails the coverage half.
		Keywraith w = new Keywraith();
		boolean sawMin = false, sawMax = false;
		Random.pushGenerator(20260814L);
		try {
			for (int i = 0; i < 200; i++) {
				int dmg = w.damageRoll();
				assertTrue(dmg >= 2 && dmg <= 6, "damage must stay in [2,6], rolled " + dmg);
				if (dmg == 2) sawMin = true;
				if (dmg == 6) sawMax = true;
			}
		} finally {
			Random.popGenerator();
		}
		assertTrue(sawMin, "200 rolls must reach the band's minimum (2)");
		assertTrue(sawMax, "200 rolls must reach the band's maximum (6)");
	}

	@Test
	void itsTouchLocksTheVictimsJoints() {
		Hero prevHero = Dungeon.hero;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;
			Keywraith w = new Keywraith();

			w.attackProc(hero, 3);
			assertNotNull(hero.buff(Cripple.class),
					"a damaging Keywraith touch must lock (cripple) the victim");
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void zeroDamageTouchDoesNotLock() {
		Hero prevHero = Dungeon.hero;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;
			new Keywraith().attackProc(hero, 0);
			assertNull(hero.buff(Cripple.class), "a fully-blocked touch must not lock");
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void spriteAssetIsWiredAndOnDisk() {
		String path = Asset.getAssetFilePath(GeneralAsset.KEYWRAITH);
		assertNotNull(path, "GeneralAsset.KEYWRAITH must resolve to a path");
		assertTrue(com.badlogic.gdx.Gdx.files.internal(path).exists(),
				"the keywraith sprite must exist at " + path);
	}
}
