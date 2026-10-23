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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 1 (v2.5.4) Generator parity (cpdu-3vq): the catalog/tier-lookup entries upstream added in
 * 6a313b135 (Pickaxe in WEP_T2, Dart in MIS_T1, both at probability 0) and the category
 * sub-orderings from fbcfc5900 / later (minus Trinket, which CPDU has no subsystem for).
 *
 * What this pins and why:
 *  - The entries exist and are weight 0: they are for lookup only, never a drop.
 *  - A zero-weight tail entry must not change which nonzero entry any roll picks. The deck
 *    system consumes exactly one Float per draw whatever the array length, so the picks of the
 *    new arrays must equal the picks of the legacy (tail-less) arrays for the same seed --
 *    otherwise seeded levelgen would diverge for every existing seed.
 *  - Saves written before the tail entries existed carry shorter per-category probs arrays.
 *    restoreFromBundle must keep working (no crash, no misaligned deck) and fall back to a
 *    fresh default deck at the new length.
 *  - Item ordering within a category: sub-orderings must place potions/scrolls/missiles in
 *    upstream's order.
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
	void pickaxeAndDartAreLookupOnlyEntries() {
		int pick = indexOf(Generator.Category.WEP_T2, Pickaxe.class);
		assertTrue(pick >= 0, "Pickaxe must be listed in WEP_T2 (upstream 6a313b135)");
		assertEquals(0f, Generator.Category.WEP_T2.defaultProbs[pick], "Pickaxe must never drop");

		int dart = indexOf(Generator.Category.MIS_T1, Dart.class);
		assertTrue(dart >= 0, "Dart must be listed in MIS_T1 (upstream 6a313b135)");
		assertEquals(0f, Generator.Category.MIS_T1.defaultProbs[dart], "Dart must never drop");
	}

	@Test
	void catalogSeesPickaxe() {
		// CPDU's Catalog builds the weapons list from the WEP_T* class arrays
		assertTrue(Catalog.WEAPONS.items().contains(Pickaxe.class),
				"the weapons catalog follows WEP_T2.classes, so it must now include the Pickaxe");
	}

	// ------------------------------------------------------------------------- determinism

	/** The probs of a category as they were before the tail entry was added. */
	private static float[] legacy(Generator.Category cat, int tailIndex) {
		assertEquals(cat.defaultProbs.length - 1, tailIndex, "tail entry expected last");
		return Arrays.copyOf(cat.defaultProbs, tailIndex);
	}

	private static int[] drain(float[] defaults, long seed, int draws) {
		// mirrors Generator.random(): chances on the live deck, decrement, reset on exhaustion
		float[] deck = defaults.clone();
		int[] picks = new int[draws];
		Random.pushGenerator(seed);
		try {
			for (int n = 0; n < draws; n++) {
				int i = Random.chances(deck);
				if (i == -1) {
					deck = defaults.clone();
					i = Random.chances(deck);
				}
				deck[i]--;
				picks[n] = i;
			}
		} finally {
			Random.popGenerator();
		}
		return picks;
	}

	@Test
	void zeroWeightTailDoesNotChangeAnyPick() {
		Object[][] cases = {
				{ Generator.Category.WEP_T2, Pickaxe.class },
				{ Generator.Category.MIS_T1, Dart.class },
		};
		for (Object[] c : cases) {
			Generator.Category cat = (Generator.Category) c[0];
			int tail = indexOf(cat, (Class<?>) c[1]);
			float[] old = legacy(cat, tail);
			for (long seed = 1; seed <= 20; seed++) {
				int[] before = drain(old, seed, 200);
				int[] after = drain(cat.defaultProbs, seed, 200);
				assertArrayEquals(before, after,
						cat.name() + ": a zero-weight tail must not perturb seeded picks (seed " + seed + ")");
				for (int pick : after) {
					assertTrue(pick != tail, cat.name() + ": the zero-weight tail was picked");
				}
			}
		}
	}

	@Test
	void realGeneratorNeverDropsLookupOnlyEntries() {
		Random.pushGenerator(0xBEEFL);
		try {
			Generator.fullReset();
			for (int n = 0; n < 300; n++) {
				assertFalse(Generator.random(Generator.Category.WEP_T2) instanceof Pickaxe);
				assertFalse(Generator.randomUsingDefaults(Generator.Category.WEP_T2) instanceof Pickaxe);
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
		// A pre-port save: WEP_T2 had 6 weights, MIS_T1 had 3, partially drained.
		float[] oldWep = { 1, 0, 2, 0, 1, 2 };
		float[] oldMis = { 0, 3, 1 };
		Bundle old = new Bundle();
		Generator.storeInBundle(old);
		old.put("wep_t2_probs", oldWep);
		old.put("wep_t2_seed", 1234L);
		old.put("wep_t2_dropped", 7);
		old.put("mis_t1_probs", oldMis);
		old.put("mis_t1_seed", 4321L);
		old.put("mis_t1_dropped", 3);

		Random.pushGenerator(0xC0FFEEL);
		try {
			Generator.restoreFromBundle(old);
		} finally {
			Random.popGenerator();
		}

		Generator.Category wep = Generator.Category.WEP_T2;
		Generator.Category mis = Generator.Category.MIS_T1;
		// the mismatched arrays are dropped, so the deck is a full default deck at the new length
		assertArrayEquals(wep.defaultProbs, wep.probs, "WEP_T2 must fall back to the default deck");
		assertArrayEquals(mis.defaultProbs, mis.probs, "MIS_T1 must fall back to the default deck");
		assertEquals(wep.classes.length, wep.probs.length);
		assertEquals(mis.classes.length, mis.probs.length);
		// ...while the per-category seed and draw counter still come back
		assertEquals(Long.valueOf(1234L), wep.seed);
		assertEquals(7, wep.dropped);
		assertEquals(Long.valueOf(4321L), mis.seed);
		assertEquals(3, mis.dropped);

		// and the restored decks keep working
		Random.pushGenerator(99L);
		try {
			for (int n = 0; n < 50; n++) {
				Generator.random(wep);
				Generator.random(mis);
			}
		} finally {
			Random.popGenerator();
		}
	}

	@Test
	void currentLengthSavedDeckIsRestoredExactly() {
		float[] wepDeck = wepDeckWithTail(new float[]{ 1, 0, 2, 0, 1, 2 }, 0f);
		Bundle saved = new Bundle();
		Generator.storeInBundle(saved);
		saved.put("wep_t2_probs", wepDeck);

		Random.pushGenerator(0xC0FFEEL);
		try {
			Generator.restoreFromBundle(saved);
		} finally {
			Random.popGenerator();
		}
		assertArrayEquals(wepDeck, Generator.Category.WEP_T2.probs);
	}

	private static float[] wepDeckWithTail(float[] head, float tail) {
		float[] deck = Arrays.copyOf(head, head.length + 1);
		deck[head.length] = tail;
		return deck;
	}

	// --------------------------------------------------------------------------- sub-orderings

	@Test
	void subOrderingsArrangeItemsWithinTheirCategory() {
		// potions: Waterskin, Potion, ExoticPotion, Brew, Elixir, LiquidMetal
		int waterskin = Generator.Category.order(new Waterskin());
		int potion = Generator.Category.order(new PotionOfHealing());
		int exotic = Generator.Category.order(new PotionOfShielding());
		int brew = Generator.Category.order(new BlizzardBrew());
		int elixir = Generator.Category.order(new ElixirOfMight());
		int metal = Generator.Category.order(new LiquidMetal());
		assertTrue(waterskin < potion && potion < exotic && exotic < brew && brew < elixir && elixir < metal,
				"potion ordering: " + Arrays.asList(waterskin, potion, exotic, brew, elixir, metal));

		// scrolls: Scroll, ExoticScroll, Spell, ArcaneResin
		int scroll = Generator.Category.order(new ScrollOfIdentify());
		int exoticScroll = Generator.Category.order(new ScrollOfAntiMagic());
		int spell = Generator.Category.order(new Alchemize());
		int resin = Generator.Category.order(new ArcaneResin());
		assertTrue(scroll < exoticScroll && exoticScroll < spell && spell < resin,
				"scroll ordering: " + Arrays.asList(scroll, exoticScroll, spell, resin));

		// thrown weapons: MissileWeapon, then Bomb
		int stone = Generator.Category.order(new ThrowingStone());
		int bomb = Generator.Category.order(new Bomb());
		assertTrue(stone < bomb, "bombs sort after thrown weapons: " + stone + " vs " + bomb);

		// sub-orderings stay inside the category: every potion-ish item sorts before scrolls
		assertTrue(metal < scroll, "potion category must stay ahead of the scroll category");
		assertTrue(Generator.Category.order(new Dart()) < Generator.Category.order(new PotionOfHealing()),
				"missiles stay ahead of potions");
	}
}
