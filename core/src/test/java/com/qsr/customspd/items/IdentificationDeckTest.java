package com.qsr.customspd.items;

import com.qsr.customspd.assets.Asset;
import com.qsr.customspd.assets.GeneralAsset;
import com.qsr.customspd.items.potions.Potion;
import com.qsr.customspd.items.potions.PotionOfFrost;
import com.qsr.customspd.items.potions.PotionOfHealing;
import com.qsr.customspd.items.potions.PotionOfLevitation;
import com.qsr.customspd.items.potions.PotionOfMindVision;
import com.qsr.customspd.items.potions.PotionOfPurity;
import com.qsr.customspd.items.potions.PotionOfStrength;
import com.qsr.customspd.items.potions.PotionOfToxicGas;
import com.qsr.customspd.test.HeadlessGdx;
import com.qsr.customspd.test.SaveRoundtrip;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F1+F2 save-correctness suite: item identification state ({@link ItemStatusHandler}) and the
 * {@link Generator} deck state, both of which live only in a save bundle between sessions.
 *
 * What this pins and why:
 *  - A potion's unidentified appearance (label/image) and its identified flag must survive the
 *    REAL byte-level save path (Bundle.write -> bytes -> Bundle.read). If a label silently
 *    re-rolls on load, players lose identification knowledge without any crash.
 *  - The handler's newly-added-item fallback (ItemStatusHandler.restore, the unlabelled branch):
 *    when a save predates an item, the item must be dealt a label from the LEFTOVER pool and
 *    every previously mapped item must keep its exact label. A broken fallback would either
 *    crash old saves on update or shuffle appearances mid-run.
 *  - The Generator's deck probs / per-category seed / dropped counter must survive a save such
 *    that the post-load draw sequence is IDENTICAL to a run that never saved — the deck system's
 *    whole reason for existing (consistent drops regardless of when they occur).
 *
 * Statics discipline: Generator.Category state (probs/seed/dropped, plus the private
 * category-probs maps via the bundle path) is snapshotted in @BeforeEach and restored in
 * @AfterEach; the Potion handler is snapshotted/restored through Potion.save/restore inside the
 * one test that replaces it. All RNG-consuming sections run under Random.pushGenerator.
 */
class IdentificationDeckTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	// ---------------------------------------------------------------- Generator static snapshot

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
		// The private categoryProbs/usingFirstDeck state only travels through the bundle path.
		savedGeneratorBundle = new Bundle();
		Generator.storeInBundle(savedGeneratorBundle);
	}

	@AfterEach
	void restoreGeneratorState() {
		// restoreFromBundle internally runs fullReset(), which consumes RNG — keep it seeded.
		Random.pushGenerator(0xC0FFEEL);
		try {
			Generator.restoreFromBundle(savedGeneratorBundle);
		} finally {
			Random.popGenerator();
		}
		// Then pin the public per-category fields back to the exact snapshot (restoreFromBundle
		// leaves fullReset() values for anything the bundle did not carry, e.g. null seeds).
		for (Generator.Category cat : Generator.Category.values()) {
			float[] probs = savedProbs.get(cat);
			cat.probs = probs == null ? null : probs.clone();
			cat.seed = savedSeeds.get(cat);
			cat.dropped = savedDropped.get(cat);
		}
	}

	// ------------------------------------------------------------------------------- helpers

	@SuppressWarnings("unchecked")
	private static Class<? extends Potion>[] potionClasses() {
		return (Class<? extends Potion>[]) Generator.Category.POTION.classes;
	}

	/** The same 12 label->asset shape Potion's private colors map has; distinct assets each. */
	private static LinkedHashMap<String, Asset> testLabels() {
		LinkedHashMap<String, Asset> labels = new LinkedHashMap<>();
		labels.put("crimson", GeneralAsset.POTION_CRIMSON);
		labels.put("amber", GeneralAsset.POTION_AMBER);
		labels.put("golden", GeneralAsset.POTION_GOLDEN);
		labels.put("jade", GeneralAsset.POTION_JADE);
		labels.put("turquoise", GeneralAsset.POTION_TURQUOISE);
		labels.put("azure", GeneralAsset.POTION_AZURE);
		labels.put("indigo", GeneralAsset.POTION_INDIGO);
		labels.put("magenta", GeneralAsset.POTION_MAGENTA);
		labels.put("bistre", GeneralAsset.POTION_BISTRE);
		labels.put("charcoal", GeneralAsset.POTION_CHARCOAL);
		labels.put("silver", GeneralAsset.POTION_SILVER);
		labels.put("ivory", GeneralAsset.POTION_IVORY);
		return labels;
	}

	private static ItemStatusHandler<Potion> seededHandler(long seed) {
		Random.pushGenerator(seed);
		try {
			return new ItemStatusHandler<>(potionClasses(), testLabels());
		} finally {
			Random.popGenerator();
		}
	}

	private static ItemStatusHandler<Potion> restoredHandler(Bundle bundle, long seed) {
		Random.pushGenerator(seed);
		try {
			return new ItemStatusHandler<>(potionClasses(), testLabels(), bundle);
		} finally {
			Random.popGenerator();
		}
	}

	private static List<Class<?>> drawPotions(int n) {
		// The deck draw replays the category's own stored seed internally; the outer push just
		// keeps any incidental RNG use off the unseeded global generator.
		List<Class<?>> drawn = new ArrayList<>();
		Random.pushGenerator(555L);
		try {
			for (int i = 0; i < n; i++) {
				Item item = Generator.random(Generator.Category.POTION);
				assertNotNull(item, "the potion deck must always deal an item");
				drawn.add(item.getClass());
			}
		} finally {
			Random.popGenerator();
		}
		return drawn;
	}

	// ------------------------------------------------------- 1) handler save/restore roundtrip

	@Test
	void handlerLabelsAndKnownFlagsSurviveASaveRoundtrip() throws IOException {
		ItemStatusHandler<Potion> original = seededHandler(11L);
		original.know(PotionOfHealing.class);
		original.know(PotionOfFrost.class);

		Bundle out = new Bundle();
		original.save(out);
		Bundle read = SaveRoundtrip.writeRead(out);

		// Rebuild under a DIFFERENT seed: if restore re-rolled assignments instead of reading
		// the bundle, the permutation would differ and the label assertions below would fail.
		ItemStatusHandler<Potion> restored = restoredHandler(read, 999L);

		Set<String> seenLabels = new LinkedHashSet<>();
		for (Class<? extends Potion> cls : potionClasses()) {
			assertEquals(original.label(cls), restored.label(cls),
					cls.getSimpleName() + " must keep its label across a save");
			assertEquals(original.image(cls), restored.image(cls),
					cls.getSimpleName() + " must keep its image across a save");
			assertEquals(original.isKnown(cls), restored.isKnown(cls),
					cls.getSimpleName() + " must keep its identified flag across a save");
			seenLabels.add(restored.label(cls));
		}
		assertEquals(potionClasses().length, seenLabels.size(),
				"the restored mapping must still be a bijection (no shared labels)");
		assertTrue(restored.isKnown(PotionOfHealing.class));
		assertTrue(restored.isKnown(PotionOfFrost.class));
		assertFalse(restored.isKnown(PotionOfMindVision.class),
				"an unidentified potion must stay unidentified after loading");
	}

	// ------------------------------------------------- 2) the real Potion static save path

	@Test
	@SuppressWarnings("unchecked")
	void potionAppearancesSurviveTheRealStaticSavePath() throws IOException {
		// Snapshot the live handler (booted by HeadlessGdx) so this test leaves it untouched.
		Bundle snapshot = new Bundle();
		Potion.save(snapshot);
		try {
			Map<Class<?>, Asset> imagesBefore = new LinkedHashMap<>();
			for (Class<?> cls : Generator.Category.POTION.classes) {
				Potion p = (Potion) Reflection.newInstance((Class<? extends Potion>) cls);
				assertNotNull(p, cls.getSimpleName() + " must be constructible");
				imagesBefore.put(cls, p.image());
			}
			boolean controlKnownBefore = Potion.getKnown().contains(PotionOfMindVision.class);

			Bundle out = new Bundle();
			Potion.save(out);
			Bundle read = SaveRoundtrip.writeRead(out);
			// Flip one identified flag in the save itself, as if the player had drunk it.
			read.put(PotionOfHealing.class.getSimpleName() + "_known", true);

			Random.pushGenerator(31337L); // fully-populated bundle: restore must not roll RNG
			try {
				Potion.restore(read);
			} finally {
				Random.popGenerator();
			}

			for (Class<?> cls : Generator.Category.POTION.classes) {
				Potion p = (Potion) Reflection.newInstance((Class<? extends Potion>) cls);
				assertEquals(imagesBefore.get(cls), p.image(),
						cls.getSimpleName() + " must present the same appearance after loading");
			}
			assertTrue(Potion.getKnown().contains(PotionOfHealing.class),
					"the identified flag written into the save must be honoured on load");
			assertEquals(controlKnownBefore, Potion.getKnown().contains(PotionOfMindVision.class),
					"a potion's identified state must not change across the roundtrip");
		} finally {
			Potion.restore(snapshot);
		}
	}

	// --------------------------------------------- 3-5) the newly-added-item restore fallback

	@Test
	void aMissingItemFallsBackToTheLeftoverLabel() throws IOException {
		ItemStatusHandler<Potion> original = seededHandler(7L);
		original.know(PotionOfLevitation.class);
		original.know(PotionOfHealing.class);
		String healingLabel = original.label(PotionOfHealing.class);

		Bundle out = new Bundle();
		original.save(out);
		Bundle read = SaveRoundtrip.writeRead(out);
		// Simulate a save written before PotionOfHealing existed.
		assertTrue(read.remove(PotionOfHealing.class.getSimpleName() + "_label"));
		assertTrue(read.remove(PotionOfHealing.class.getSimpleName() + "_known"));

		ItemStatusHandler<Potion> restored = restoredHandler(read, 1234L);

		for (Class<? extends Potion> cls : potionClasses()) {
			if (cls != PotionOfHealing.class) {
				assertEquals(original.label(cls), restored.label(cls),
						"previously mapped " + cls.getSimpleName() + " must keep its exact label");
			}
		}
		// 11 restored items reclaim their labels from the pool, so exactly one label is left —
		// the one the missing item used to have. A fallback that failed to prune the pool would
		// deal an already-used label here (breaking the bijection) instead.
		assertEquals(healingLabel, restored.label(PotionOfHealing.class),
				"the missing item must be dealt the single leftover label");
		assertFalse(restored.isKnown(PotionOfHealing.class),
				"with its known key gone from the save, the item comes back unidentified");
		assertTrue(restored.isKnown(PotionOfLevitation.class),
				"other identified flags are unaffected by the fallback");
	}

	@Test
	void aMissingLabelKeepsItsSurvivingKnownFlag() throws IOException {
		// Pins ItemStatusHandler.restore's unlabelled branch reading the known key when it IS
		// present: label lost, identification retained.
		ItemStatusHandler<Potion> original = seededHandler(21L);
		original.know(PotionOfToxicGas.class);

		Bundle out = new Bundle();
		original.save(out);
		Bundle read = SaveRoundtrip.writeRead(out);
		assertTrue(read.remove(PotionOfToxicGas.class.getSimpleName() + "_label"));
		// deliberately KEEP PotionOfToxicGas_known = true

		ItemStatusHandler<Potion> restored = restoredHandler(read, 4321L);

		assertEquals(original.label(PotionOfToxicGas.class), restored.label(PotionOfToxicGas.class),
				"the single leftover label is the one the item had before");
		assertTrue(restored.isKnown(PotionOfToxicGas.class),
				"a surviving known key must be honoured even when the label key was lost");
	}

	@Test
	void twoMissingItemsAbsorbTheLeftoverLabelPool() throws IOException {
		ItemStatusHandler<Potion> original = seededHandler(42L);

		Bundle out = new Bundle();
		original.save(out);
		Bundle read = SaveRoundtrip.writeRead(out);
		assertTrue(read.remove(PotionOfFrost.class.getSimpleName() + "_label"));
		assertTrue(read.remove(PotionOfFrost.class.getSimpleName() + "_known"));
		assertTrue(read.remove(PotionOfPurity.class.getSimpleName() + "_label"));
		assertTrue(read.remove(PotionOfPurity.class.getSimpleName() + "_known"));

		ItemStatusHandler<Potion> restored = restoredHandler(read, 31L);

		Set<String> leftoverLabels = new LinkedHashSet<>();
		leftoverLabels.add(original.label(PotionOfFrost.class));
		leftoverLabels.add(original.label(PotionOfPurity.class));

		Set<String> dealtLabels = new LinkedHashSet<>();
		dealtLabels.add(restored.label(PotionOfFrost.class));
		dealtLabels.add(restored.label(PotionOfPurity.class));

		assertEquals(2, dealtLabels.size(), "the two missing items must get distinct labels");
		assertEquals(leftoverLabels, dealtLabels,
				"the missing items must split exactly the leftover pool, no re-deal of used labels");
		for (Class<? extends Potion> cls : potionClasses()) {
			if (cls != PotionOfFrost.class && cls != PotionOfPurity.class) {
				assertEquals(original.label(cls), restored.label(cls),
						"previously mapped " + cls.getSimpleName() + " must keep its exact label");
			}
		}
	}

	// --------------------------------------------------------- 6-7) Generator deck save state

	@Test
	void generatorDeckStateAndDrawSequenceSurviveASaveRoundtrip() throws IOException {
		Random.pushGenerator(20260814L);
		try {
			Generator.fullReset(); // deals fresh decks and per-category seeds, deterministically

			float deckTotal = 0;
			for (float p : Generator.Category.POTION.defaultProbs) deckTotal += p;

			drawPotions(4);
			float remaining = 0;
			for (float p : Generator.Category.POTION.probs) remaining += p;
			assertEquals(deckTotal - 4, remaining, 0f,
					"each draw must decrement the deck by exactly one");
			assertEquals(4, Generator.Category.POTION.dropped);

			// Mid-run save through the real byte path.
			Bundle out = new Bundle();
			Generator.storeInBundle(out);
			Bundle read = SaveRoundtrip.writeRead(out);

			float[] probsAtSave = Generator.Category.POTION.probs.clone();
			Long seedAtSave = Generator.Category.POTION.seed;
			assertNotNull(seedAtSave, "fullReset must arm the deck seed");

			// Control: keep playing without ever saving.
			List<Class<?>> control = drawPotions(6);

			// Load the save and play the same six draws again.
			Generator.restoreFromBundle(read);
			assertArrayEquals(probsAtSave, Generator.Category.POTION.probs, 0f,
					"deck probs must come back exactly as saved");
			assertEquals(seedAtSave.longValue(), Generator.Category.POTION.seed.longValue(),
					"the deck seed must come back exactly as saved");
			assertEquals(4, Generator.Category.POTION.dropped,
					"the dropped counter must come back exactly as saved");

			List<Class<?>> replayed = drawPotions(6);
			assertEquals(control, replayed,
					"post-load draws must match a run that never saved — the deck guarantee");
		} finally {
			Random.popGenerator();
		}
	}

	@Test
	void generatorDeckDealsExactlyTheDefaultProbsOverOneCycle() {
		Random.pushGenerator(4242L);
		try {
			Generator.fullReset();

			Generator.Category cat = Generator.Category.POTION;
			int deckSize = 0;
			for (float p : cat.defaultProbs) deckSize += (int) p;

			Map<Class<?>, Integer> counts = new LinkedHashMap<>();
			for (Class<?> drawn : drawPotions(deckSize)) {
				Integer prev = counts.get(drawn);
				counts.put(drawn, prev == null ? 1 : prev + 1);
			}

			// One full cycle must deal every potion exactly its weight — the deck is a deal,
			// not an independent roll. Any seed satisfies this; a regression to pure weighted
			// rolls would fail it almost surely.
			for (int i = 0; i < cat.classes.length; i++) {
				Integer got = counts.get(cat.classes[i]);
				assertEquals((int) cat.defaultProbs[i], got == null ? 0 : got.intValue(),
						cat.classes[i].getSimpleName() + " must appear exactly its deck weight");
			}
			assertFalse(counts.containsKey(PotionOfStrength.class),
					"zero-weight entries (strength potions drop by chapter logic) never deal");

			// The deck exhausted: the next draw must transparently reshuffle and still deal.
			List<Class<?>> extra = drawPotions(1);
			assertEquals(1, extra.size());
			float remaining = 0;
			for (float p : cat.probs) remaining += p;
			assertEquals(deckSize - 1, remaining, 0f,
					"after exhaustion the deck resets to defaults minus the one dealt item");
		} finally {
			Random.popGenerator();
		}
	}
}
