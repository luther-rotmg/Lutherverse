package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.buffs.Vertigo;
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
 * HexwardMoth — a depth-4 flying moth whose landed hits shed disorienting ward-dust.
 * Verifies the filled-in mechanic (the Vertigo dust proc), the stat line, and that the
 * scaffold's sprite wiring resolves to real art on disk.
 */
class HexwardMothTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@Test
	void statLineIsTheDesignedDepthFourBand() {
		HexwardMoth m = new HexwardMoth();
		assertEquals(14, m.HT, "designed depth-4 HP band");
		assertEquals(3, m.EXP);
		assertEquals(10, m.maxLvl, "a Prison-band mob must stop paying EXP past level 10");
		assertTrue(m.flying, "it is a moth — it flies over chasms and traps");
	}

	@Test
	void damageRollStaysInTheDesignedBandAndSpansIt() {
		// Bounds + coverage: 200 seeded rolls must stay in [2,5] AND actually reach both
		// endpoints — a mis-implementation that quietly narrows the band (e.g. wrong
		// min/max argument) passes a bounds-only check but fails the coverage half.
		HexwardMoth m = new HexwardMoth();
		boolean sawMin = false, sawMax = false;
		Random.pushGenerator(20260814L);
		try {
			for (int i = 0; i < 200; i++) {
				int dmg = m.damageRoll();
				assertTrue(dmg >= 2 && dmg <= 5, "damage must stay in [2,5], rolled " + dmg);
				if (dmg == 2) sawMin = true;
				if (dmg == 5) sawMax = true;
			}
		} finally {
			Random.popGenerator();
		}
		assertTrue(sawMin, "200 rolls must reach the band's minimum (2)");
		assertTrue(sawMax, "200 rolls must reach the band's maximum (5)");
	}

	@Test
	void itsTouchDizziesTheVictim() {
		Hero prevHero = Dungeon.hero;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;
			HexwardMoth m = new HexwardMoth();

			m.attackProc(hero, 3);
			assertNotNull(hero.buff(Vertigo.class),
					"a damaging moth touch must shed ward-dust (Vertigo) on the victim");
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void zeroDamageTouchDoesNotDizzy() {
		Hero prevHero = Dungeon.hero;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;
			new HexwardMoth().attackProc(hero, 0);
			assertNull(hero.buff(Vertigo.class), "a fully-blocked touch must not shed dust");
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void spriteAssetIsWiredAndOnDisk() {
		String path = Asset.getAssetFilePath(GeneralAsset.HEXWARD_MOTH);
		assertNotNull(path, "GeneralAsset.HEXWARD_MOTH must resolve to a path");
		assertTrue(com.badlogic.gdx.Gdx.files.internal(path).exists(),
				"the hexward moth sprite must exist at " + path);
	}
}
