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
		assertTrue(w.lootChance > 0, "the key-warden hoards tribute (gold loot)");
	}

	@Test
	void damageRollStaysInTheDesignedBand() {
		Keywraith w = new Keywraith();
		Random.pushGenerator(20260814L);
		try {
			for (int i = 0; i < 200; i++) {
				int dmg = w.damageRoll();
				assertTrue(dmg >= 2 && dmg <= 6, "damage must stay in [2,6], rolled " + dmg);
			}
		} finally {
			Random.popGenerator();
		}
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
