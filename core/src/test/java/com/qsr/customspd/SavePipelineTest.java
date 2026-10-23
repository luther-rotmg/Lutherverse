package com.qsr.customspd;

import com.qsr.customspd.actors.blobs.Blob;
import com.qsr.customspd.actors.blobs.ToxicGas;
import com.qsr.customspd.actors.mobs.Mob;
import com.qsr.customspd.items.Heap;
import com.qsr.customspd.items.Item;
import com.qsr.customspd.items.potions.PotionOfHealing;
import com.qsr.customspd.items.weapon.melee.Dagger;
import com.qsr.customspd.levels.Level;
import com.qsr.customspd.test.HeadlessRun;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F4 save-pipeline pins: the REAL file path a run goes through between sessions —
 * Dungeon.saveAll/saveGame/saveLevel -> game.dat / level_*.dat in the save slot ->
 * Dungeon.loadGame/loadLevel — driven on a real seeded run by {@link HeadlessRun}. Nothing here
 * hand-builds a bundle: if any piece of run or level state stops being written or read back, a
 * "continue" silently hands the player a different game.
 *
 * Every saved value is first moved off its default (gold, hero HP, extra inventory, a gas cloud)
 * so a field that is dropped on save and re-defaulted on load cannot pass by coincidence.
 */
class SavePipelineTest {

	private static final long SEED = 20261023L;

	@Test
	void runStateSurvivesSaveAllThenLoadGame() {
		try (HeadlessRun run = HeadlessRun.start(SEED)) {
			run.enterFirstLevel();

			Dungeon.gold = 4321;
			Dungeon.hero.HP = Dungeon.hero.HT - 7;
			assertTrue(new PotionOfHealing().quantity(3).collect(), "the potion must enter the backpack");
			assertTrue(new Dagger().upgrade(2).collect(), "the dagger must enter the backpack");

			String levelName = Dungeon.levelName;
			int depth = Dungeon.depth;
			int hp = Dungeon.hero.HP;
			int ht = Dungeon.hero.HT;
			List<String> inventory = inventory();
			assertTrue(inventory.contains("PotionOfHealing x3 +0") && inventory.contains("Dagger x1 +2"),
					"precondition: the extra items are in the inventory snapshot: " + inventory);

			run.saveAll();

			// Clobber everything loadGame is meant to bring back.
			Dungeon.seed = 0;
			Dungeon.gold = 0;
			Dungeon.levelName = "clobbered";
			Dungeon.depth = -1;
			Dungeon.hero = null;

			run.loadGame();

			assertEquals(SEED, Dungeon.seed, "seed");
			assertEquals(levelName, Dungeon.levelName, "levelName");
			assertEquals(depth, Dungeon.depth, "depth");
			assertEquals(4321, Dungeon.gold, "gold");
			assertNotNull(Dungeon.hero, "the hero must be reconstructed from the save");
			assertEquals(hp, Dungeon.hero.HP, "hero HP");
			assertEquals(ht, Dungeon.hero.HT, "hero HT");
			assertEquals(inventory, inventory(), "inventory (class, quantity, level), equipped and backpack");
		}
	}

	@Test
	void levelStateSurvivesSaveLevelThenLoadLevel() {
		try (HeadlessRun run = HeadlessRun.start(SEED)) {
			Level saved = run.enterFirstLevel();

			int gasCell = firstOpenCell(saved);
			Blob gas = Blob.seed(gasCell, 120, ToxicGas.class, saved);

			List<String> mobs = mobs(saved);
			List<String> heaps = heaps(saved);
			Map<String, Integer> blobs = blobVolumes(saved);
			int[] map = saved.map.clone();
			assertFalse(mobs.isEmpty(), "precondition: floor 1 has mobs to round-trip");
			assertFalse(heaps.isEmpty(), "precondition: floor 1 has item heaps to round-trip");
			assertEquals(Integer.valueOf(120), blobs.get(ToxicGas.class.getSimpleName()), "precondition: seeded gas");

			run.saveLevel();
			Level loaded = run.loadLevel();

			assertNotNull(loaded);
			assertEquals(saved.getClass(), loaded.getClass(), "level class");
			assertEquals(saved.width(), loaded.width(), "width");
			assertEquals(saved.height(), loaded.height(), "height");
			assertArrayEquals(map, loaded.map, "Level.map, cell for cell");
			assertEquals(mobs, mobs(loaded), "mobs (type@pos)");
			assertEquals(heaps, heaps(loaded), "heaps (pos: item class x quantity)");
			assertEquals(blobs, blobVolumes(loaded), "blob volumes");
			assertEquals(gas.cur[gasCell], loaded.blobs.get(ToxicGas.class).cur[gasCell], "gas at the seeded cell");
			assertEquals(saved.transitions.size(), loaded.transitions.size(), "transitions");
			assertEquals(saved.entrance(), loaded.entrance(), "entrance cell");
			assertEquals(saved.exit(), loaded.exit(), "exit cell");
		}
	}

	// ------------------------------------------------------------------------------ helpers

	/** Every item the hero holds, in Belongings' (deterministic) iteration order. */
	private static List<String> inventory() {
		List<String> out = new ArrayList<>();
		for (Item item : Dungeon.hero.belongings) {
			out.add(item.getClass().getSimpleName() + " x" + item.quantity() + " +" + item.level());
		}
		return out;
	}

	/** Level.mobs is a HashSet: compare as a sorted multiset. */
	private static List<String> mobs(Level level) {
		List<String> out = new ArrayList<>();
		for (Mob m : level.mobs) {
			out.add(m.getClass().getName() + "@" + m.pos);
		}
		Collections.sort(out);
		return out;
	}

	private static List<String> heaps(Level level) {
		List<String> out = new ArrayList<>();
		for (Heap h : level.heaps.valueList()) {
			for (Item item : h.items) {
				out.add(h.pos + ": " + item.getClass().getName() + " x" + item.quantity());
			}
		}
		Collections.sort(out);
		return out;
	}

	private static Map<String, Integer> blobVolumes(Level level) {
		Map<String, Integer> out = new TreeMap<>();
		for (Blob b : level.blobs.values()) {
			out.put(b.getClass().getSimpleName(), b.volume);
		}
		return out;
	}

	private static int firstOpenCell(Level level) {
		for (int i = 0; i < level.length(); i++) {
			if (level.passable[i] && !level.solid[i] && level.heaps.get(i) == null && level.findMob(i) == null) {
				return i;
			}
		}
		throw new IllegalStateException("no open cell on the level");
	}
}
