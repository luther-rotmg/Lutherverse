package com.qsr.customspd.items;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.items.weapon.melee.Crossbow;
import com.qsr.customspd.items.weapon.missiles.darts.Dart;
import com.qsr.customspd.test.HeadlessGdx;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Port regression test for upstream SPD 68955901f (v3.0.1): "fixed an upgrade duplication
 * exploit on curse infused xbow".
 *
 * Dart.info() used to hide an unidentified equipped crossbow's level by reading
 * {@code bow.level()}, calling {@code bow.level(0)}, and restoring with the read value. But
 * {@link com.qsr.customspd.items.weapon.Weapon#level()} adds the curse-infusion bonus on read
 * while {@code level(int)} writes the raw level — so every info() call on a dart permanently
 * baked the bonus into the crossbow's true level (a free upgrade per inspection). The fix swaps
 * in a temporary Crossbow for the IDing render instead of mutating the real one.
 */
class CrossbowDupPortTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@Test
	void dartInfoDoesNotUpgradeACurseInfusedCrossbow() {
		Hero prevHero = Dungeon.hero;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;

			Crossbow bow = new Crossbow();
			bow.level(1);
			bow.curseInfusionBonus = true;
			hero.belongings.weapon = bow;

			// the exploit only fired on an unidentified bow (the IDing branch of Dart.info())
			assertFalse(bow.isIdentified(), "a fresh crossbow must be unidentified");
			assertEquals(1, bow.trueLevel(), "raw level before info()");
			assertEquals(2, bow.level(), "curse infusion shows +1 on read");

			Dart dart = new Dart();
			// pre-fix, EACH call permanently raised trueLevel by the infusion bonus (1 -> 2 -> 3)
			assertNotNull(dart.info());
			assertNotNull(dart.info());

			assertEquals(1, bow.trueLevel(),
					"info() must not bake the curse-infusion bonus into the crossbow's raw level");
			assertEquals(2, bow.level(),
					"displayed level must still be raw + infusion bonus, not compounded");
		} finally {
			Dungeon.hero = prevHero;
		}
	}
}
