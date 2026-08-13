package com.qsr.customspd;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.hero.spheregrid.SphereGrid;
import com.qsr.customspd.actors.hero.spheregrid.SphereNode;
import com.qsr.customspd.items.weapon.melee.Keyblade;
import com.qsr.customspd.items.weapon.melee.MeleeWeapon;
import com.qsr.customspd.modding.HeroConfig;
import com.qsr.customspd.modding.JsonConfigRetriever;
import com.qsr.customspd.test.HeadlessGdx;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F1 integration test: exercises the real hero-config asset path (ModManager + Gdx.files) on the
 * headless harness to verify the Keybearer starting kit is wired end to end — the keybearer.json
 * asset exists, parses into a HeroConfig, and equips the Keyblade. This is runtime wiring a plain
 * unit test cannot reach (it needs Gdx.files), and it directly catches a real prototype risk: a
 * wrong asset path or weapon type in the kit.
 */
class KeybearerHeadlessTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@Test
	void keybearerKitLoadsAndEquipsTheKeyblade() {
		HeroConfig config = JsonConfigRetriever.INSTANCE.retrieveHeroConfig("keybearer");
		assertNotNull(config, "keybearer.json must load through the real asset path");
		assertNotNull(config.getWeapon(), "the Keybearer kit must define a starting weapon");
		assertEquals("weapon.melee.Keyblade", config.getWeapon().getType(),
				"the Keybearer must start with the Keyblade");
	}

	@Test
	void keybladeClassInstantiatesViaTheKitReflectionPath() {
		// HeroClass.setUp builds the kit with Reflection.newInstance(Reflection.forName("com.qsr.customspd.items." + type)).
		// This proves that path resolves to a real, constructible MeleeWeapon — a wrong/renamed
		// class or a throwing constructor would surface here, not just at runtime on class-select.
		Object weapon = Reflection.newInstance(
				Reflection.forName("com.qsr.customspd.items." + "weapon.melee.Keyblade"));
		assertNotNull(weapon, "the Keyblade class must exist and be constructible");
		assertTrue(weapon instanceof MeleeWeapon, "the Keyblade must be a MeleeWeapon");
	}

	@Test
	void mightNodesAddFlatKeybladeDamage() {
		// Two fresh, identical heroes; one specs 2 points of Might on its grid.
		Hero base = new Hero();
		base.sphereGrid = new SphereGrid();

		Hero might = new Hero();
		might.sphereGrid = new SphereGrid();
		might.sphereGrid.grantPoints(2);
		might.sphereGrid.activate(SphereNode.ATTUNEMENT); // MIGHT +1
		might.sphereGrid.activate(SphereNode.MIGHT_I);    // MIGHT +1  -> mightLevel 2
		assertEquals(2, might.sphereGrid.mightLevel());

		Keyblade kb = new Keyblade();

		// KindOfWeapon.isEquipped() consults the global Dungeon.hero; combat always has one set.
		Hero prevHero = Dungeon.hero;
		try {
			// Seed the RNG identically for both rolls so the only difference is the grid bonus.
			long seed = 20260813L;

			Dungeon.hero = base;
			Random.pushGenerator(seed);
			int baseDmg = kb.damageRoll(base);
			Random.popGenerator();

			Dungeon.hero = might;
			Random.pushGenerator(seed);
			int mightDmg = kb.damageRoll(might);
			Random.popGenerator();

			assertEquals(baseDmg + 2, mightDmg,
					"Might nodes must add exactly mightLevel flat damage to the keyblade");
		} finally {
			Dungeon.hero = prevHero;
		}
	}
}
