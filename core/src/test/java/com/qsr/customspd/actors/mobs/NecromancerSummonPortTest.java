package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.Actor;
import com.qsr.customspd.levels.Terrain;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.HeadlessLevel;
import com.watabou.utils.PathFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port regression for upstream b692329a7 (v3.3.8): a necromancer must not finish a summon
 * onto a tile that became impassable mid-summon (e.g. terrain changed while the zap was in
 * flight). Pre-fix, {@code summonMinion()} only checked for a blocking char, so an empty
 * wall/chasm tile received the skeleton anyway.
 *
 * Only the "wait a turn" branch is exercised: it is the one summon path that returns before
 * touching {@code sprite.finishSummoning()} (an NPE headless), and it is where both ported
 * guards live — the impassable check on the outer condition and the null-guard on the
 * blocker-damage branch (the blocker cell can now legitimately be empty).
 */
class NecromancerSummonPortTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@BeforeEach
	void setUp() {
		Dungeon.hero = null;
	}

	@AfterEach
	void tearDown() {
		HeadlessLevel.uninstall();
		Dungeon.hero = null;
	}

	@Test
	void summonOntoNewlyImpassableTileWaitsInsteadOfFinishing() {
		HeadlessLevel level = HeadlessLevel.install(8, 8);

		// A 3x3 wall block in the interior: the summon target and every one of its
		// neighbours is impassable, so no relocation cell exists either.
		int target = 4 * level.width() + 4;
		level.map[target] = Terrain.WALL;
		level.passable[target] = false;
		level.solid[target] = true;
		for (int offset : PathFinder.NEIGHBOURS8) {
			level.map[target + offset] = Terrain.WALL;
			level.passable[target + offset] = false;
			level.solid[target + offset] = true;
		}

		Necromancer necro = HeadlessLevel.at(new Necromancer(), level.width() + 1);
		necro.summoning = true;
		necro.summoningPos = target;

		// Pre-fix this call summoned the skeleton onto the wall tile (and the null-blocker
		// branch, once the outer condition was fixed alone, threw an NPE).
		necro.summonMinion();

		assertTrue(level.mobs.isEmpty(), "no skeleton may be summoned onto an impassable tile");
		assertNull(Actor.findChar(target), "the impassable tile must stay empty");
		assertTrue(necro.summoning, "the summon must stay pending so it can retry next turn");
		assertEquals(1f, necro.cooldown(), 0.001f, "the necromancer must wait a turn instead");
	}
}
