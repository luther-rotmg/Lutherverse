package com.qsr.customspd.items;

import com.qsr.customspd.actors.hero.spheregrid.SphereGridProgress;
import com.qsr.customspd.items.stones.Runestone;
import com.qsr.customspd.test.HeadlessGdx;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Insight Crystal — the first ITEM shipped through the content-scaffold pipeline, and the
 * meta-progression faucet: shattering it must grant persistent Insight. Also guards the
 * Generator pool consistency the scaffold dogfooding broke (classes[] vs defaultProbs[]
 * desync means an item silently never drops).
 */
class InsightCrystalTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@BeforeEach
	void clean() {
		SphereGridProgress.reset();
	}

	@Test
	void shatteringGrantsPersistentInsight() throws Exception {
		assertEquals(0, SphereGridProgress.insight());

		InsightCrystal crystal = new InsightCrystal();
		// activate(cell) is the runestone shatter hook; invoke it directly (no scene).
		Method activate = InsightCrystal.class.getDeclaredMethod("activate", int.class);
		activate.setAccessible(true);
		activate.invoke(crystal, 0);

		assertEquals(InsightCrystal.INSIGHT_PER_CRYSTAL, SphereGridProgress.insight(),
				"a shattered crystal must bank persistent Insight");
	}

	@Test
	void isARunestonePoolCitizen() {
		InsightCrystal crystal = new InsightCrystal();
		assertTrue(crystal instanceof Runestone, "STONE pool items must be Runestones");
		assertTrue(crystal.stackable, "runestones stack");
		assertTrue(crystal.isIdentified(), "runestones need no identification");
		assertTrue(crystal.value() > 0, "shops must be able to price it");
	}

	@Test
	void everyGeneratorCategoryKeepsClassesAndProbsAligned() {
		// Regression guard for the content-scaffold desync bug (2026-08-14): inserting a
		// class without extending defaultProbs makes the tail item silently undroppable.
		// This sweeps EVERY deck-based category, so any future desync fails loudly.
		for (Generator.Category cat : Generator.Category.values()) {
			if (cat.defaultProbs != null) {
				assertNotNull(cat.classes, cat.name() + " has probs but no classes");
				assertEquals(cat.classes.length, cat.defaultProbs.length,
						cat.name() + ": classes[] and defaultProbs[] must stay in lockstep");
			}
			// the live deck, too: reset()/restoreFromBundle() must never leave it a different size
			assertNotNull(cat.probs, cat.name() + " has no probs");
			assertEquals(cat.classes.length, cat.probs.length,
					cat.name() + ": classes[] and the live probs[] must stay in lockstep");
		}
	}

	@Test
	void staticInitializerLeavesProbsAlignedWithClasses() throws Exception {
		// Generator.fullReset() re-derives every deck's probs from defaultProbs, so once any
		// earlier test (or Dungeon.init) has run it, a mis-sized static initializer is invisible
		// in the live Generator. This reads what the initializer itself leaves behind by loading
		// a private copy of Generator (child-first for Generator* only; every item class still
		// comes from the normal loader) -- the state any pre-reset reader sees. That is how
		// WEP_T3.probs = WEP_T1.defaultProbs.clone() (cpdu-lr8) went unnoticed: 5 weights for
		// 6 classes, until upstream b1d82dee5.
		java.net.URL here = Generator.class.getProtectionDomain().getCodeSource().getLocation();
		try (FreshGeneratorLoader loader = new FreshGeneratorLoader(
				new java.net.URL[]{here}, Generator.class.getClassLoader())) {
			Class<?> category = Class.forName(Generator.class.getName() + "$Category", true, loader);
			assertTrue(category.getClassLoader() == loader, "must be the private copy, not the shared Category");

			Object[] cats = category.getEnumConstants();
			assertTrue(cats.length > 0, "Category enum constants not found");
			for (Object cat : cats) {
				String name = ((Enum<?>) cat).name();
				Object[] classes = (Object[]) category.getField("classes").get(cat);
				assertNotNull(classes, name + " has no classes");

				float[] probs = (float[]) category.getField("probs").get(cat);
				assertNotNull(probs, name + " has no probs after static init");
				assertEquals(classes.length, probs.length,
						name + ": static-initializer probs[] must match classes[]");

				float[] defaults = (float[]) category.getField("defaultProbs").get(cat);
				if (defaults != null) {
					assertEquals(classes.length, defaults.length,
							name + ": static-initializer defaultProbs[] must match classes[]");
				}

				// the two-deck variant (not ported yet, upstream v2.3.0): guard it the day it lands
				try {
					float[] defaults2 = (float[]) category.getField("defaultProbs2").get(cat);
					if (defaults2 != null) {
						assertEquals(classes.length, defaults2.length,
								name + ": defaultProbs2[] must match classes[]");
					}
				} catch (NoSuchFieldException notPortedYet) {
					// fine
				}
			}
		}
	}

	/** Loads Generator and its nested types from the same classpath entry, ahead of the parent. */
	private static final class FreshGeneratorLoader extends java.net.URLClassLoader {
		FreshGeneratorLoader(java.net.URL[] urls, ClassLoader parent) {
			super(urls, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (name.equals(Generator.class.getName()) || name.startsWith(Generator.class.getName() + "$")) {
				synchronized (getClassLoadingLock(name)) {
					Class<?> c = findLoadedClass(name);
					if (c == null) c = findClass(name);
					if (resolve) resolveClass(c);
					return c;
				}
			}
			return super.loadClass(name, resolve);
		}
	}
}
