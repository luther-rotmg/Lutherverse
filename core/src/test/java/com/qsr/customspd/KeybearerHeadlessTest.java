package com.qsr.customspd;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.buffs.Burning;
import com.qsr.customspd.actors.buffs.Chill;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.hero.HeroClass;
import com.qsr.customspd.actors.hero.spheregrid.SphereGrid;
import com.qsr.customspd.actors.hero.spheregrid.SphereNode;
import com.qsr.customspd.actors.mobs.Mob;
import com.qsr.customspd.actors.mobs.Rat;
import com.qsr.customspd.items.weapon.melee.FrostKeyblade;
import com.qsr.customspd.items.weapon.melee.Keyblade;
import com.qsr.customspd.items.weapon.melee.MeleeWeapon;
import com.qsr.customspd.modding.HeroConfig;
import com.qsr.customspd.modding.JsonConfigRetriever;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.SaveRoundtrip;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

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

	@Test
	void vigorNodesRaiseMaxHP() {
		Hero prevHero = Dungeon.hero;
		try {
			Hero base = new Hero();
			Dungeon.hero = base;
			base.updateHT(false);
			int baseHT = base.HT;

			Hero tanky = new Hero();
			tanky.sphereGrid = new SphereGrid();
			tanky.sphereGrid.grantPoints(2);
			tanky.sphereGrid.activate(SphereNode.ATTUNEMENT); // prerequisite for VIGOR_I
			tanky.sphereGrid.activate(SphereNode.VIGOR_I);    // VIGOR magnitude 3
			assertEquals(3, tanky.sphereGrid.vigorLevel());

			Dungeon.hero = tanky;
			tanky.updateHT(false);

			assertEquals(baseHT + tanky.sphereGrid.vigorLevel(), tanky.HT,
					"Vigor nodes must add vigorLevel to max HP");
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void emberNodesIgniteAndAddFireDamage() {
		Hero prevHero = Dungeon.hero;
		try {
			Keyblade kb = new Keyblade();

			// Control: a Keybearer with no Ember. The hit ignites but deals no immediate bonus.
			Hero plain = new Hero();
			plain.sphereGrid = new SphereGrid();
			Dungeon.hero = plain;
			Mob control = new Rat();
			control.HP = control.HT = 100;
			kb.proc(plain, control, 10);
			assertNotNull(control.buff(Burning.class), "a keyblade hit must ignite the target");
			assertEquals(100, control.HP,
					"without Ember, the hit ignites but deals no immediate bonus damage");

			// Ember specced: same hit ignites AND deals emberLevel bonus fire damage now.
			Hero fiery = new Hero();
			fiery.sphereGrid = new SphereGrid();
			fiery.sphereGrid.grantPoints(2);
			fiery.sphereGrid.activate(SphereNode.ATTUNEMENT);
			fiery.sphereGrid.activate(SphereNode.EMBER_I); // EMBER magnitude 1
			Dungeon.hero = fiery;
			Mob target = new Rat();
			target.HP = target.HT = 100;
			kb.proc(fiery, target, 10);
			assertNotNull(target.buff(Burning.class), "a keyblade hit must ignite the target");
			assertTrue(target.HP < 100, "Ember nodes must deal bonus fire damage on the hit");
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void frostKeybladeChillsAndFrostNodesLengthenIt() {
		Hero prevHero = Dungeon.hero;
		try {
			FrostKeyblade fkb = new FrostKeyblade();

			// Control: no Frost specced -> base chill duration.
			Hero plain = new Hero();
			plain.sphereGrid = new SphereGrid();
			Dungeon.hero = plain;
			Mob control = new Rat();
			control.HP = control.HT = 100;
			fkb.proc(plain, control, 10);
			Chill baseChill = control.buff(Chill.class);
			assertNotNull(baseChill, "a frost keyblade hit must chill the target");
			float baseTurns = baseChill.cooldown();

			// Frost specced -> a longer chill from the same hit.
			Hero cold = new Hero();
			cold.sphereGrid = new SphereGrid();
			cold.sphereGrid.grantPoints(2);
			cold.sphereGrid.activate(SphereNode.ATTUNEMENT);
			cold.sphereGrid.activate(SphereNode.FROST_I); // FROST magnitude 1
			Dungeon.hero = cold;
			Mob target = new Rat();
			target.HP = target.HT = 100;
			fkb.proc(cold, target, 10);
			Chill frostChill = target.buff(Chill.class);
			assertNotNull(frostChill, "a frost keyblade hit must chill the target");
			assertTrue(frostChill.cooldown() > baseTurns, "Frost nodes must lengthen the chill");
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void signatureAbilityBurstScalesWithElementTimesAbility() {
		Hero prevHero = Dungeon.hero;
		try {
			Keyblade kb = new Keyblade();

			// Fire+ability build: emberLevel 1, abilityLevel 1 -> Flame Burst = 1*1 = 1,
			// on top of the ember bonus (1). Two immediate damage total.
			Hero hybrid = new Hero();
			hybrid.sphereGrid = new SphereGrid();
			hybrid.sphereGrid.grantPoints(3);
			hybrid.sphereGrid.activate(SphereNode.ATTUNEMENT);
			hybrid.sphereGrid.activate(SphereNode.EMBER_I);   // ember 1
			hybrid.sphereGrid.activate(SphereNode.ABILITY_I); // ability 1
			assertEquals(1, hybrid.sphereGrid.emberLevel());
			assertEquals(1, hybrid.sphereGrid.abilityLevel());
			Dungeon.hero = hybrid;
			Mob t1 = new Rat();
			t1.HP = t1.HT = 100;
			kb.proc(hybrid, t1, 10);
			assertEquals(100 - (1 + 1), t1.HP, "ember bonus (1) + Flame Burst (ember*ability = 1)");

			// Ability WITHOUT an element: burst = 0 * ability = 0, and no ember bonus either.
			Hero abilityOnly = new Hero();
			abilityOnly.sphereGrid = new SphereGrid();
			abilityOnly.sphereGrid.grantPoints(2);
			abilityOnly.sphereGrid.activate(SphereNode.ATTUNEMENT);
			abilityOnly.sphereGrid.activate(SphereNode.ABILITY_I); // ability 1, ember 0
			Dungeon.hero = abilityOnly;
			Mob t2 = new Rat();
			t2.HP = t2.HT = 100;
			kb.proc(abilityOnly, t2, 10);
			assertEquals(100, t2.HP, "ability alone bursts for nothing without an element");
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void keybearerClassInitialisesEndToEnd() {
		Hero prevHero = Dungeon.hero;
		try {
			Hero hero = new Hero();
			Dungeon.hero = hero;

			HeroClass.KEYBEARER.initHero(hero);

			assertEquals(HeroClass.KEYBEARER, hero.heroClass);
			assertNotNull(hero.sphereGrid, "initHero must create the sphere grid for the Keybearer");
			assertTrue(hero.belongings.weapon instanceof Keyblade,
					"the Keybearer must start the run wielding a Keyblade");
		} finally {
			Dungeon.hero = prevHero;
		}
	}

	@Test
	void keybearerGridSurvivesAFullHeroSaveRoundtrip() throws IOException {
		Hero prevHero = Dungeon.hero;
		try {
			// A realistic, fully-initialised Keybearer, then a specced grid.
			Hero hero = new Hero();
			Dungeon.hero = hero;
			HeroClass.KEYBEARER.initHero(hero);
			hero.sphereGrid.grantPoints(3);
			hero.sphereGrid.activate(SphereNode.ATTUNEMENT);
			hero.sphereGrid.activate(SphereNode.MIGHT_I);

			// The real save path: storeInBundle -> Bundle.write -> read -> restoreFromBundle.
			Bundle out = new Bundle();
			hero.storeInBundle(out);
			Bundle in = SaveRoundtrip.writeRead(out);

			Hero restored = new Hero();
			restored.restoreFromBundle(in);

			assertEquals(HeroClass.KEYBEARER, restored.heroClass);
			assertNotNull(restored.sphereGrid, "the Keybearer grid must survive a full save");
			assertTrue(restored.sphereGrid.isActivated(SphereNode.MIGHT_I),
					"activated nodes must survive the save");
			assertEquals(1, restored.sphereGrid.unspentPoints(), "unspent points must survive (3 granted - 2 spent)");
			assertEquals(2, restored.sphereGrid.mightLevel());
		} finally {
			Dungeon.hero = prevHero;
		}
	}
}
