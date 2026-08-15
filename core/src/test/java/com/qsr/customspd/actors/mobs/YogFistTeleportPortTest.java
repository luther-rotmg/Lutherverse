package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.buffs.Blindness;
import com.qsr.customspd.actors.buffs.Buff;
import com.qsr.customspd.actors.buffs.Light;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.HeadlessLevel;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port regression for upstream 2d5b87292 (SPD v3.3.7): the Bright and Dark fists'
 * half-HP teleport check was strict ({@code HP < HT/2}), so a hit that landed the fist
 * at EXACTLY half HP skipped the whole teleport response (Blindness / Light-snuff,
 * HP clamp, relocation). The fix makes the comparison inclusive ({@code HP <= HT/2}).
 *
 * Headless caveat: the teleport branch ends in ScrollOfTeleportation.appear(), whose
 * first statement dereferences the fist's sprite — absent in this harness — so ENTERING
 * the branch throws an NPE here. The tests therefore assert on the branch's pre-teleport
 * side effects (Blindness prolonged on the hero for the Bright fist; the hero's Light
 * buff detached for the Dark fist; the HP clamp to HT/2), swallowing exactly that NPE.
 * On unfixed code the exactly-half hit never enters the branch, none of those side
 * effects happen, and the boundary tests fail — verified red before the fix landed.
 */
class YogFistTeleportPortTest {

	// 24x24 open room: far cells stay outside hero FOV even after Light attaches
	// (viewDistance 8), so the teleport destination loop always has candidates.
	private static final int SIZE = 24;
	private static final int HERO_CELL = 1 * SIZE + 1;   // (1,1) corner
	// (20,20): 20 Chebyshev from the fixture's implied yog position (exit()=0 + 3 rows),
	// so YogFist.isNearYog() stays false and the fist is not invulnerable.
	private static final int FIST_CELL = 20 * SIZE + 20;

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@AfterEach
	void tearDown() {
		HeadlessLevel.uninstall();
		Dungeon.hero = null;
	}

	private Hero installLevelAndHero() {
		HeadlessLevel.install(SIZE, SIZE);
		Hero hero = new Hero();
		Dungeon.hero = hero;
		HeadlessLevel.at(hero, HERO_CELL);
		return hero;
	}

	/**
	 * Deals damage, swallowing ONLY the sprite NPE that ScrollOfTeleportation.appear()
	 * throws headless when the teleport branch is entered. Every assertion runs on the
	 * branch's real, pre-teleport side effects afterwards.
	 */
	private static void damageSwallowingHeadlessTeleportNPE(YogFist fist, int dmg) {
		Random.pushGenerator(20260814L);
		try {
			fist.damage(dmg, new Object());
		} catch (NullPointerException headlessSpriteAccess) {
			// expected in this harness iff the teleport branch ran
		} finally {
			Random.popGenerator();
		}
	}

	@Test
	void brightFistTeleportsWhenHitToExactlyHalfHP() {
		Hero hero = installLevelAndHero();
		YogFist fist = HeadlessLevel.at(new YogFist.BrightFist(), FIST_CELL);

		// HT is 300; a 150 hit lands it at exactly HT/2 — the boundary the fix covers
		damageSwallowingHeadlessTeleportNPE(fist, fist.HT / 2);

		assertTrue(fist.isAlive(), "a half-HP hit must not kill the fist");
		assertEquals(fist.HT / 2, fist.HP, "the branch clamps HP to exactly HT/2");
		assertNotNull(hero.buff(Blindness.class),
				"an exactly-half-HP hit must trigger the teleport response (hero blinded)");
	}

	@Test
	void brightFistStaysPutJustAboveHalfHP() {
		Hero hero = installLevelAndHero();
		YogFist fist = HeadlessLevel.at(new YogFist.BrightFist(), FIST_CELL);

		// one point short of the threshold: no branch, no exception, no blindness
		fist.damage(fist.HT / 2 - 1, new Object());

		assertEquals(fist.HT / 2 + 1, fist.HP);
		assertNull(hero.buff(Blindness.class),
				"above half HP the teleport response must not fire");
	}

	@Test
	void brightFistTeleportStillFiresBelowHalfHP() {
		// canary for the detection mechanism itself: this case entered the branch both
		// before and after the fix, so it must always show the side effects
		Hero hero = installLevelAndHero();
		YogFist fist = HeadlessLevel.at(new YogFist.BrightFist(), FIST_CELL);

		damageSwallowingHeadlessTeleportNPE(fist, fist.HT / 2 + 50);

		assertEquals(fist.HT / 2, fist.HP,
				"the branch clamps an overshooting hit back up to HT/2");
		assertNotNull(hero.buff(Blindness.class));
	}

	@Test
	void darkFistSnuffsLightWhenHitToExactlyHalfHP() {
		Hero hero = installLevelAndHero();
		Buff.affect(hero, Light.class, Light.DURATION);
		assertNotNull(hero.buff(Light.class), "precondition: the hero carries a Light buff");
		YogFist fist = HeadlessLevel.at(new YogFist.DarkFist(), FIST_CELL);

		damageSwallowingHeadlessTeleportNPE(fist, fist.HT / 2);

		assertEquals(fist.HT / 2, fist.HP, "the branch clamps HP to exactly HT/2");
		assertNull(hero.buff(Light.class),
				"an exactly-half-HP hit must trigger the teleport response (Light detached)");
	}

	@Test
	void darkFistKeepsLightJustAboveHalfHP() {
		Hero hero = installLevelAndHero();
		Buff.affect(hero, Light.class, Light.DURATION);
		YogFist fist = HeadlessLevel.at(new YogFist.DarkFist(), FIST_CELL);

		fist.damage(fist.HT / 2 - 1, new Object());

		assertEquals(fist.HT / 2 + 1, fist.HP);
		assertNotNull(hero.buff(Light.class),
				"above half HP the teleport response must not fire");
	}
}
