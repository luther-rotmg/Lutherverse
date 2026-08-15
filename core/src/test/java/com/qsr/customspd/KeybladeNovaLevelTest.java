package com.qsr.customspd;

import com.qsr.customspd.actors.buffs.Burning;
import com.qsr.customspd.actors.buffs.Paralysis;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.hero.abilities.keybearer.KeybladeNova;
import com.qsr.customspd.actors.hero.spheregrid.SphereGrid;
import com.qsr.customspd.actors.hero.spheregrid.SphereNode;
import com.qsr.customspd.actors.mobs.Mob;
import com.qsr.customspd.actors.mobs.Rat;
import com.qsr.customspd.items.armor.WarriorArmor;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.HeadlessLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Keybearer's CLASS ABILITY under integration test — Keyblade Nova activated for real
 * (through the public ArmorAbility.use entry) on the F3 HeadlessLevel: real ConeAOE over
 * real flag maps, real damage, real dominant-element status, real charge spend. Until the
 * fixture existed only the Nova's pure helpers were testable.
 */
class KeybladeNovaLevelTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@AfterEach
	void tearDown() {
		HeadlessLevel.uninstall();
		Dungeon.hero = null;
	}

	@Test
	void novaBurnsEverythingNearbyAndSpendsCharge() {
		HeadlessLevel level = HeadlessLevel.install(12, 12);
		int center = 5 * level.width() + 5;

		Hero hero = new Hero();
		hero.sphereGrid = new SphereGrid();
		hero.sphereGrid.grantPoints(2);
		hero.sphereGrid.activate(SphereNode.ATTUNEMENT);
		hero.sphereGrid.activate(SphereNode.EMBER_I); // ember 1 -> fire dominant
		Dungeon.hero = hero;
		HeadlessLevel.at(hero, center);

		Mob near = HeadlessLevel.at(new Rat(), center + 1);
		near.HP = near.HT = 100;
		Mob diag = HeadlessLevel.at(new Rat(), center - level.width() - 1);
		diag.HP = diag.HT = 100;
		Mob outOfRange = HeadlessLevel.at(new Rat(), center + 5); // distance 5 > radius 4
		outOfRange.HP = outOfRange.HT = 100;

		WarriorArmor armor = new WarriorArmor();
		armor.charge = 100;

		new KeybladeNova().use(armor, hero); // useTargeting() == false -> activates directly

		assertTrue(near.HP < 100, "an adjacent enemy must take Nova damage");
		assertTrue(diag.HP < 100, "the 360-degree burst must reach diagonals");
		assertNotNull(near.buff(Burning.class), "fire-dominant Nova ignites survivors");
		assertEquals(100, outOfRange.HP, "enemies beyond the radius must be untouched");
		assertNull(outOfRange.buff(Burning.class));
		assertEquals(65f, armor.charge, 0.01f, "the Nova must spend its 35 base charge");
	}

	@Test
	void stormDominantNovaParalyzesInstead() {
		HeadlessLevel level = HeadlessLevel.install(12, 12);
		int center = 5 * level.width() + 5;

		Hero hero = new Hero();
		hero.sphereGrid = new SphereGrid();
		hero.sphereGrid.grantPoints(3);
		hero.sphereGrid.activate(SphereNode.ATTUNEMENT);
		hero.sphereGrid.activate(SphereNode.STORM_I);
		hero.sphereGrid.activate(SphereNode.STORM_II); // storm 3 -> storm dominant
		Dungeon.hero = hero;
		HeadlessLevel.at(hero, center);

		Mob near = HeadlessLevel.at(new Rat(), center + 1);
		near.HP = near.HT = 100;

		WarriorArmor armor = new WarriorArmor();
		armor.charge = 100;

		new KeybladeNova().use(armor, hero);

		assertTrue(near.HP < 100, "storm Nova still deals its damage");
		assertNotNull(near.buff(Paralysis.class), "storm-dominant Nova shocks survivors still");
		assertNull(near.buff(Burning.class), "one dominant element only — no burn from a storm build");
	}
}
