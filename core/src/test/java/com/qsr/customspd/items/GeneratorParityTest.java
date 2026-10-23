package com.qsr.customspd.items;

import com.qsr.customspd.items.bombs.Bomb;
import com.qsr.customspd.items.potions.PotionOfHealing;
import com.qsr.customspd.items.potions.brews.BlizzardBrew;
import com.qsr.customspd.items.potions.elixirs.ElixirOfMight;
import com.qsr.customspd.items.potions.exotic.PotionOfShielding;
import com.qsr.customspd.items.quest.Pickaxe;
import com.qsr.customspd.items.scrolls.ScrollOfIdentify;
import com.qsr.customspd.items.scrolls.exotic.ScrollOfAntiMagic;
import com.qsr.customspd.items.spells.Alchemize;
import com.qsr.customspd.items.weapon.missiles.ThrowingStone;
import com.qsr.customspd.items.weapon.missiles.darts.Dart;
import com.qsr.customspd.journal.Catalog;
import com.qsr.customspd.test.HeadlessGdx;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 1 (v2.5.4) Generator parity (cpdu-3vq): the lookup-only entry upstream added in 6a313b135
 * (Dart in MIS_T1 at probability 0) and the category sub-orderings from fbcfc5900 (minus Trinket,
 * which CPDU has no subsystem for). 6a313b135's matching Pickaxe entry in WEP_T2 is deliberately held
 * back: CPDU's old Catalog.WEAPONS is built from WEP_T*.classes, so it would change what the
 * weapon-identification badges require (see Generator's WEP_T2 comment).
 *
 * What this pins and why:
 *  - Dart is listed at weight 0: it is for lookup only, never a drop.
 *  - A zero-weight entry must not change which item any roll picks. The deck system consumes
 *    exactly one Float per draw whatever the array length, so the picked classes must equal the
 *    picks of the array without that entry for the same seed -- otherwise seeded levelgen would
 *    diverge for every existing seed. Compared by class, so entries appended after it later
 *    (content-scaffold appends at the end) do not break the comparison.
 *  - Saves written before the entry existed carry a shorter MIS_T1 probs array. restoreFromBundle
 *    must keep working (no crash, no misaligned deck) and fall back to a fresh default deck.
 *  - Item ordering within a category: sub-orderings must place potions/scrolls/missiles in
 *    upstream's order, inside their own category.
 *
 * Statics discipline: Generator.Category probs/seed/dropped and the private deck maps are
 * snapshotted in @BeforeEach and restored in @AfterEach (same pattern as IdentificationDeckTest).
 */
class GeneratorParityTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	private Map<Generator.Category, float[]> savedProbs;
	private Map<Generator.Category, Long> savedSeeds;
	private Map<Generator.Category, Integer> savedDropped;
	private Bundle savedGeneratorBundle;

	@BeforeEach
	void snapshotGeneratorState() {
		savedProbs = new LinkedHashMap<>();
		savedSeeds = new LinkedHashMap<>();
		savedDropped = new LinkedHashMap<>();
		for (Generator.Category cat : Generator.Category.values()) {
			savedProbs.put(cat, cat.probs == null ? null : cat.probs.clone());
			savedSeeds.put(cat, cat.seed);
			savedDropped.put(cat, cat.dropped);
		}
		savedGeneratorBundle = new Bundle();
		Generator.storeInBundle(savedGeneratorBundle);
	}

	@AfterEach
	void restoreGeneratorState() {
		Random.pushGenerator(0xC0FFEEL);
		try {
			Generator.restoreFromBundle(savedGeneratorBundle);
		} finally {
			Random.popGenerator();
		}
		for (Generator.Category cat : Generator.Category.values()) {
			float[] probs = savedProbs.get(cat);
			cat.probs = probs == null ? null : probs.clone();
			cat.seed = savedSeeds.get(cat);
			cat.dropped = savedDropped.get(cat);
		}
	}

	private static int indexOf(Generator.Category cat, Class<?> cls) {
		return Arrays.asList(cat.classes).indexOf(cls);
	}

	// ------------------------------------------------------------------ catalog / tier lookup

	@Test
	void dartIsALookupOnlyEntry() {
		int dart = indexOf(Generator.Category.MIS_T1, Dart.class);
		assertTrue(dart >= 0, "Dart must be listed in MIS_T1 (upstream 6a313b135)");
		assertEquals(0f, Generator.Category.MIS_T1.defaultProbs[dart], "Dart must never drop");
	}

	@Test
	void weaponBadgesDoNotRequireThePickaxeYet() {
		// Catalog.WEAPONS feeds ALL_WEAPONS_IDENTIFIED (and so ALL_ITEMS_IDENTIFIED). Listing the
		// Pickaxe in WEP_T2 without upstream's b003d1129 badge redesign would make those badges need
		// a quest item that packs without a Blacksmith cannot provide.
		assertTrue(indexOf(Generator.Category.WEP_T2, Pickaxe.class) < 0,
				"Pickaxe belongs in WEP_T2 only together with the journal catalog + badge redesign port");
		assertFalse(Catalog.WEAPONS.items().contains(Pickaxe.class),
				"the weapons catalog must not require the Pickaxe before the badge redesign is ported");
	}

	// ------------------------------------------------------------------------- determinism

	private static List<Class<?>> drain(Class<?>[] classes, float[] defaults, long seed, int draws) {
		// mirrors Generator.random(): chances on the live deck, decrement, reset on exhaustion
		float[] deck = defaults.clone();
		List<Class<?>> picks = new ArrayList<>();
		Random.pushGenerator(seed);
		try {
			for (int n = 0; n < draws; n++) {
				int i = Random.chances(deck);
				if (i == -1) {
					deck = defaults.clone();
					i = Random.chances(deck);
				}
				deck[i]--;
				picks.add(classes[i]);
			}
		} finally {
			Random.popGenerator();
		}
		return picks;
	}

	private static Class<?>[] without(Class<?>[] classes, int index) {
		List<Class<?>> out = new ArrayList<>(Arrays.asList(classes));
		out.remove(index);
		return out.toArray(new Class<?>[0]);
	}

	private static float[] without(float[] probs, int index) {
		float[] out = new float[probs.length - 1];
		for (int i = 0, j = 0; i < probs.length; i++) {
			if (i != index) out[j++] = probs[i];
		}
		return out;
	}

	@Test
	void zeroWeightEntryDoesNotChangeAnyPick() {
		Generator.Category cat = Generator.Category.MIS_T1;
		int dart = indexOf(cat, Dart.class);
		Class<?>[] oldClasses = without(cat.classes, dart);
		float[] oldProbs = without(cat.defaultProbs, dart);
		for (long seed = 1; seed <= 20; seed++) {
			List<Class<?>> before = drain(oldClasses, oldProbs, seed, 200);
			List<Class<?>> after = drain(cat.classes, cat.defaultProbs, seed, 200);
			assertEquals(before, after,
					"a zero-weight entry must not perturb seeded picks (seed " + seed + ")");
			assertFalse(after.contains(Dart.class), "the zero-weight Dart entry was picked");
		}
	}

	@Test
	void realGeneratorNeverDropsLookupOnlyEntries() {
		Random.pushGenerator(0xBEEFL);
		try {
			Generator.fullReset();
			for (int n = 0; n < 300; n++) {
				Item missile = Generator.random(Generator.Category.MIS_T1);
				assertFalse(missile instanceof Dart, "got " + missile.getClass());
				assertFalse(Generator.randomUsingDefaults(Generator.Category.MIS_T1) instanceof Dart);
			}
		} finally {
			Random.popGenerator();
		}
	}

	// ----------------------------------------------------------------------- save compatibility

	@Test
	void oldLengthSavedDeckIsDiscardedWithoutCrashOrMisalignment() {
		// A pre-port save: MIS_T1 had 3 weights, partially drained.
		float[] oldMis = { 0, 3, 1 };
		Bundle old = new Bundle();
		Generator.storeInBundle(old);
		old.put("mis_t1_probs", oldMis);
		old.put("mis_t1_seed", 4321L);
		old.put("mis_t1_dropped", 3);

		Random.pushGenerator(0xC0FFEEL);
		try {
			Generator.restoreFromBundle(old);
		} finally {
			Random.popGenerator();
		}

		Generator.Category mis = Generator.Category.MIS_T1;
		// the mismatched array is dropped, so the deck is a full default deck at the new length
		assertArrayEquals(mis.defaultProbs, mis.probs, "MIS_T1 must fall back to the default deck");
		assertEquals(mis.classes.length, mis.probs.length);
		// ...while the per-category seed and draw counter still come back
		assertEquals(Long.valueOf(4321L), mis.seed);
		assertEquals(3, mis.dropped);

		// and the restored deck keeps working
		Random.pushGenerator(99L);
		try {
			for (int n = 0; n < 50; n++) {
				Generator.random(mis);
			}
		} finally {
			Random.popGenerator();
		}
	}

	@Test
	void currentLengthSavedDeckIsRestoredExactly() {
		Generator.Category mis = Generator.Category.MIS_T1;
		float[] deck = new float[mis.classes.length];
		deck[0] = 1;
		deck[1] = 2;
		Bundle saved = new Bundle();
		Generator.storeInBundle(saved);
		saved.put("mis_t1_probs", deck);

		Random.pushGenerator(0xC0FFEEL);
		try {
			Generator.restoreFromBundle(saved);
		} finally {
			Random.popGenerator();
		}
		assertArrayEquals(deck, mis.probs);
	}

	// --------------------------------------------------------------------------- sub-orderings

	@Test
	void subOrderingsArrangeItemsWithinTheirCategory() {
		// potions: Potion, ExoticPotion, Brew, Elixir, LiquidMetal
		int potion = Generator.Category.order(new PotionOfHealing());
		int exotic = Generator.Category.order(new PotionOfShielding());
		int brew = Generator.Category.order(new BlizzardBrew());
		int elixir = Generator.Category.order(new ElixirOfMight());
		int metal = Generator.Category.order(new LiquidMetal());
		assertTrue(potion < exotic && exotic < brew && brew < elixir && elixir < metal,
				"potion ordering: " + Arrays.asList(potion, exotic, brew, elixir, metal));

		// scrolls: Scroll, ExoticScroll, Spell, ArcaneResin
		int scroll = Generator.Category.order(new ScrollOfIdentify());
		int exoticScroll = Generator.Category.order(new ScrollOfAntiMagic());
		int spell = Generator.Category.order(new Alchemize());
		int resin = Generator.Category.order(new ArcaneResin());
		assertTrue(scroll < exoticScroll && exoticScroll < spell && spell < resin,
				"scroll ordering: " + Arrays.asList(scroll, exoticScroll, spell, resin));

		// thrown weapons: MissileWeapon, then Bomb -- inside the thrown-weapon category. Bomb is not a
		// MissileWeapon, so only the sub-ordering entry can put it in that category's bucket
		// (order() returns category*100 + sub-index; unordered items fall back past Short.MAX_VALUE).
		int stone = Generator.Category.order(new ThrowingStone());
		int bomb = Generator.Category.order(new Bomb());
		assertEquals(stone / 100, bomb / 100, "bombs must sort inside the thrown-weapon category: "
				+ stone + " vs " + bomb);
		assertTrue(stone < bomb, "bombs sort after thrown weapons: " + stone + " vs " + bomb);

		// sub-orderings stay inside the category: every potion-ish item sorts before scrolls
		assertTrue(metal < scroll, "potion category must stay ahead of the scroll category");
		assertTrue(Generator.Category.order(new Dart()) < Generator.Category.order(new PotionOfHealing()),
				"missiles stay ahead of potions");
	}
}
