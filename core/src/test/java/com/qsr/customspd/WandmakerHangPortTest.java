package com.qsr.customspd;

import com.qsr.customspd.actors.mobs.Mob;
import com.qsr.customspd.actors.mobs.npcs.Wandmaker;
import com.qsr.customspd.levels.Terrain;
import com.qsr.customspd.levels.rooms.Room;
import com.qsr.customspd.levels.rooms.standard.EmptyRoom;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.HeadlessLevel;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Port regression test for upstream SPD 94bd1370d ("fixed very rare hangs in wandmaker
 * spawning logic"). Pre-fix, {@code Quest.spawnWandmaker} picked candidate cells only at
 * margin 2 for rooms bigger than 6x6; if every margin-2 cell was invalid (e.g. all
 * EMPTY_SP quest-room terrain) the do-while spun forever. The fix retries at ever smaller
 * margins (2 -> 1 -> 0, 30 tries each), so placement falls back to the still-valid
 * margin-1 ring instead of hanging. Verified to fail (preemptive timeout) with the fix
 * reverted.
 */
class WandmakerHangPortTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@AfterEach
	void tearDown() {
		HeadlessLevel.uninstall();
		Wandmaker.Quest.reset();
	}

	@Test
	void spawnRelaxesMarginInsteadOfHangingWhenAllInnerCellsAreInvalid() {
		final HeadlessLevel level = HeadlessLevel.install(12, 12);

		// A room bigger than 6x6, so the pre-fix code sampled ONLY margin-2 cells.
		final Room room = new EmptyRoom();
		room.set(1, 1, 8, 8);

		// Every margin-2 cell (x,y in [3..6]) becomes terrain the spawn loop rejects;
		// the margin-1 ring (x or y in {2,7}) stays plain valid floor.
		for (int y = 3; y <= 6; y++) {
			for (int x = 3; x <= 6; x++) {
				level.map[y * level.width() + x] = Terrain.EMPTY_SP;
			}
		}
		level.buildFlagMaps();

		assertTimeoutPreemptively(Duration.ofSeconds(10), new org.junit.jupiter.api.function.Executable() {
			@Override
			public void execute() {
				// Everything RNG-consuming runs seeded, per the repo determinism rule.
				Random.pushGenerator(2026L);
				try {
					Wandmaker.Quest.reset();
					Wandmaker.Quest.spawnRoom(new ArrayList<Room>()); // arms questRoomSpawned
					Wandmaker.Quest.spawnWandmaker(level, room);
				} finally {
					Random.popGenerator();
				}
			}
		}, "spawnWandmaker must relax its placement margin rather than loop forever");

		Wandmaker npc = null;
		for (Mob m : level.mobs) {
			if (m instanceof Wandmaker) {
				npc = (Wandmaker) m;
			}
		}
		assertNotNull(npc, "the wandmaker must actually spawn");
		assertNotEquals(Terrain.EMPTY_SP, level.map[npc.pos],
				"bad terrain must still be rejected after the margin fallback");
		assertTrue(level.passable[npc.pos], "the wandmaker must stand on passable ground");
		int x = npc.pos % level.width();
		int y = npc.pos / level.width();
		assertTrue(x >= 2 && x <= 7 && y >= 2 && y <= 7,
				"the fallback placement must stay inside the room (margin-1 ring)");
	}
}
