package com.qsr.customspd;

import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.hero.HeroClass;
import com.qsr.customspd.items.Item;
import com.qsr.customspd.items.potions.PotionOfHealing;
import com.qsr.customspd.items.remains.BrokenStaff;
import com.qsr.customspd.items.remains.CloakScrap;
import com.qsr.customspd.modding.DungeonLayout;
import com.qsr.customspd.modding.JsonConfigRetriever;
import com.qsr.customspd.test.HeadlessGdx;
import com.watabou.utils.Bundle;
import com.watabou.utils.FileUtils;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bones after the v2.3.0 remains rewrite (upstream f6054cc89 + its v2.3.0 fixes 21b05f3cf /
 * 9515ad729). bones.dat outlives the run that wrote it, so this pins the real file path
 * (FileUtils -> the HeadlessGdx temp dir) rather than an in-memory bundle:
 *  - a bones file written by pre-rewrite CPDU ({level: "<levelName>", item}) must still load:
 *    its item comes back and, having no hero_class key, it yields NO class remains item (what
 *    upstream does for a pre-v2.3.0 file - Bundle.getEnum on a missing key would otherwise
 *    report an exception and return WARRIOR, inventing a seal shard);
 *  - leave() -> get() across a simulated restart must hand back the dead hero's class remains
 *    item, first, alongside any picked item;
 *  - a stackable item left in remains is capped at 3.
 *
 * Statics discipline: every Dungeon/Statistics field Bones reads, and Bones' own private
 * level/item/heroClass (via reflection - a fresh process starts at "surface"), are snapshotted
 * in @BeforeEach and restored in @AfterEach; bones.dat is deleted after each test. All
 * RNG-consuming calls run under Random.pushGenerator.
 */
class BonesRemainsTest {

	private static final String BONES_FILE = "bones.dat";
	private static final String LEVEL_NAME = "3";

	private Hero prevHero;
	private int prevDepth;
	private int prevChallenges;
	private String prevLevelName;
	private String prevSeedText;
	private DungeonLayout prevLayout;
	private boolean prevAmulet;
	private int prevDeepest;
	private Object prevBonesLevel, prevBonesItem, prevBonesClass;
	private QuickSlot prevQuickslot;

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@BeforeEach
	void saveStatics() throws Exception {
		prevHero = Dungeon.hero;
		prevDepth = Dungeon.depth;
		prevChallenges = Dungeon.challenges;
		prevLevelName = Dungeon.levelName;
		prevSeedText = Dungeon.customSeedText;
		prevLayout = Dungeon.layout;
		prevAmulet = Statistics.amuletObtained;
		prevDeepest = Statistics.deepestFloor;
		prevBonesLevel = bonesField("level").get(null);
		prevBonesItem = bonesField("item").get(null);
		prevBonesClass = bonesField("heroClass").get(null);
		// Bones.pickItem reads quickslot.randomNonePlaceholder(); other suites leave starter items in it
		prevQuickslot = Dungeon.quickslot;
		Dungeon.quickslot = new QuickSlot();

		Dungeon.depth = 3;
		Dungeon.challenges = 0;
		Dungeon.levelName = LEVEL_NAME;
		Dungeon.customSeedText = "";
		Dungeon.layout = JsonConfigRetriever.INSTANCE.retrieveDungeonLayout();
		assertTrue(Dungeon.layout.getBones(), "test setup: the shipped layout must allow bones");
		Statistics.amuletObtained = false;
		Statistics.deepestFloor = 3;
		freshProcess();
	}

	@AfterEach
	void tearDown() throws Exception {
		Dungeon.hero = prevHero;
		Dungeon.depth = prevDepth;
		Dungeon.challenges = prevChallenges;
		Dungeon.levelName = prevLevelName;
		Dungeon.customSeedText = prevSeedText;
		Dungeon.layout = prevLayout;
		Statistics.amuletObtained = prevAmulet;
		Statistics.deepestFloor = prevDeepest;
		bonesField("level").set(null, prevBonesLevel);
		bonesField("item").set(null, prevBonesItem);
		bonesField("heroClass").set(null, prevBonesClass);
		Dungeon.quickslot = prevQuickslot;
		FileUtils.deleteFile(BONES_FILE);
	}

	// ------------------------------------------------------------------------------- helpers

	private static Field bonesField(String name) throws NoSuchFieldException {
		Field f = Bones.class.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	/** Bones' statics as a newly started process has them: nothing read from disk yet. */
	private static void freshProcess() throws Exception {
		bonesField("level").set(null, "surface");
		bonesField("item").set(null, null);
		bonesField("heroClass").set(null, null);
	}

	private static Hero hero(HeroClass cls) {
		Hero hero = new Hero();
		hero.heroClass = cls;
		Dungeon.hero = hero;
		return hero;
	}

	private static void leave(long seed) {
		Random.pushGenerator(seed);
		try {
			Bones.leave();
		} finally {
			Random.popGenerator();
		}
	}

	private static ArrayList<Item> get(long seed) {
		Random.pushGenerator(seed);
		try {
			return Bones.get();
		} finally {
			Random.popGenerator();
		}
	}

	// ------------------------------------------------------------------------------- tests

	@Test
	void legacyBonesFileLoadsItsItemWithoutInventingARemainsItem() throws Exception {
		// Exactly what pre-rewrite CPDU Bones.leave() wrote: a string level name and an item,
		// no hero_class key (that key did not exist).
		PotionOfHealing legacyItem = new PotionOfHealing();
		legacyItem.quantity(2);
		Bundle legacy = new Bundle();
		legacy.put("level", LEVEL_NAME);
		legacy.put("item", legacyItem);
		FileUtils.bundleToFile(BONES_FILE, legacy);

		ArrayList<Item> found = get(1L);

		assertNotNull(found, "a legacy bones file for this level must still produce remains");
		assertEquals(1, found.size(), "a legacy file has no hero_class: just its item, got " + found);
		assertTrue(found.get(0) instanceof PotionOfHealing, "the legacy item must come back, got " + found.get(0));
		assertEquals(2, found.get(0).quantity());

		// The file is consumed exactly as before: rewritten as the empty marker, never re-dropped.
		assertEquals("0", FileUtils.bundleFromFile(BONES_FILE).getString("level"));
		freshProcess();
		assertNull(get(2L), "a consumed bones file must not drop again after a restart");
	}

	@Test
	void legacyBonesFileForAnotherLevelStaysOnDisk() throws Exception {
		Bundle legacy = new Bundle();
		legacy.put("level", "7");
		legacy.put("item", new PotionOfHealing());
		FileUtils.bundleToFile(BONES_FILE, legacy);

		assertNull(get(1L), "bones belong to the level they were left on");
		assertEquals("7", FileUtils.bundleFromFile(BONES_FILE).getString("level"),
				"unclaimed bones must stay on disk for the matching level");
	}

	@Test
	void leaveThenGetRoundtripsTheHeroClassRemainsItem() throws Exception {
		hero(HeroClass.MAGE); // nothing equipped or carried: pickItem can only leave nothing
		leave(20261023L);

		Bundle onDisk = FileUtils.bundleFromFile(BONES_FILE);
		assertEquals(LEVEL_NAME, onDisk.getString("level"));
		assertEquals("MAGE", onDisk.getString("hero_class"), "leave() must persist the dead hero's class");
		assertFalse(onDisk.contains("item"), "an empty-handed hero leaves no item key");

		freshProcess(); // a later run, in a new process, reaches the same level
		Dungeon.hero = new Hero();
		ArrayList<Item> found = get(3L);

		assertNotNull(found, "a hero-class-only bones file must still produce remains");
		assertEquals(1, found.size(), "no item was left, so only the class remains item: " + found);
		assertTrue(found.get(0) instanceof BrokenStaff, "a mage's remains item, got " + found.get(0));
	}

	@Test
	void remainsItemComesFirstAlongsideAPickedItem() throws Exception {
		int withItem = 0;
		for (long seed = 1; seed <= 40; seed++) {
			Hero hero = hero(HeroClass.ROGUE);
			hero.belongings.backpack.items.add(new PotionOfHealing());
			leave(seed);

			freshProcess();
			ArrayList<Item> found = get(seed);
			assertNotNull(found, "seed " + seed);
			assertTrue(found.get(0) instanceof CloakScrap, "seed " + seed + ": class item first, got " + found);
			if (found.size() == 2) {
				withItem++;
				assertTrue(found.get(1) instanceof PotionOfHealing, "seed " + seed + ": " + found);
			} else {
				assertEquals(1, found.size(), "seed " + seed + ": " + found);
			}
		}
		assertTrue(withItem > 0, "test setup: some seed must have left the carried potion");
	}

	@Test
	void stackableRemainsAreCappedAtThree() throws Exception {
		int withItem = 0;
		int atCap = 0;
		for (long seed = 1; seed <= 40; seed++) {
			Hero hero = hero(HeroClass.WARRIOR);
			PotionOfHealing stack = new PotionOfHealing();
			stack.quantity(40);
			hero.belongings.backpack.items.add(stack);
			leave(seed);

			Bundle onDisk = FileUtils.bundleFromFile(BONES_FILE);
			if (onDisk.contains("item")) {
				withItem++;
				int left = ((Item) onDisk.get("item")).quantity();
				assertTrue(left >= 1 && left <= 3, "seed " + seed + ": remains left " + left + " of a 40-stack");
				if (left == 3) atCap++;
			}
		}
		assertTrue(withItem > 0, "test setup: some seed must have left the carried stack");
		// NormalIntRange(1, 20) is almost never below 3, so the cap must actually be doing the work.
		assertTrue(atCap > 0, "the cap at 3 must be reached for a 40-stack");
	}
}
