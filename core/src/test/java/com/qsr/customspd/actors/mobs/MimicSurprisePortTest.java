package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.Char;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.HeadlessLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port regression for upstream f8bd2233b ("fixed mimics not counting as having seen hero
 * when surprising"): when a hidden (NEUTRAL) mimic's surprise attack completes, the wake-up
 * block in {@link Mimic#onAttackComplete()} must also set {@code enemySeen}, so the hero's
 * counterattack against the now-woken mimic does not count as a sneak attack.
 *
 * The callback is invoked directly — it is the exact method the game fires when the
 * surprise-attack animation lands. In the fixture the mob's {@code enemy} field is left
 * null, so {@code Mob.onAttackComplete()}'s {@code attack(enemy)} no-ops and only the
 * wake-up block (the code the upstream fix changed) runs.
 */
class MimicSurprisePortTest {

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
	void wakingBySurpriseAttackCountsAsHavingSeenHero() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int center = 3 * level.width() + 3;

		Hero hero = new Hero();
		Dungeon.hero = hero;
		HeadlessLevel.at(hero, center);

		Mimic mimic = HeadlessLevel.at(new Mimic(), center + 1);
		mimic.setLevel(0);
		// A hidden mimic's act() loop clears enemySeen while it lies in wait (no enemy in
		// FOV while PASSIVE); reproduce that state directly — the bug needs it as input.
		mimic.enemySeen = false;
		assertSame(Char.Alignment.NEUTRAL, mimic.alignment, "fixture sanity: mimics hide as NEUTRAL");

		mimic.onAttackComplete();

		assertSame(Char.Alignment.ENEMY, mimic.alignment, "the surprising mimic must wake to ENEMY");
		assertTrue(mimic.enemySeen,
				"upstream f8bd2233b: a mimic that just surprised the hero has, by definition, seen the hero");
		assertFalse(mimic.surprisedBy(hero),
				"the hero's counterattack against the woken mimic must not count as a sneak attack");
	}

	@Test
	void alreadyWokenMimicIsUntouchedByTheCallback() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);
		int center = 3 * level.width() + 3;

		Hero hero = new Hero();
		Dungeon.hero = hero;
		HeadlessLevel.at(hero, center);

		Mimic mimic = HeadlessLevel.at(new Mimic(), center + 1);
		mimic.setLevel(0);
		mimic.alignment = Char.Alignment.ENEMY;
		mimic.state = mimic.HUNTING;
		mimic.enemySeen = false;
		float heroCooldown = hero.cooldown();

		mimic.onAttackComplete();

		assertFalse(mimic.enemySeen, "the wake-up block must only fire for a NEUTRAL (hidden) mimic");
		assertEquals(heroCooldown, hero.cooldown(), 0.001f,
				"an ordinary attack completing must not spend the hero's turn");
	}
}
