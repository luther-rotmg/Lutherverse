package com.qsr.customspd.levels;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the terrain ID table, which is serialized state: {@code Level.map} stores raw
 * terrain IDs in the save bundle, so an ID that changes meaning silently reinterprets
 * every existing save. Slice 1 already did this once on purpose — {@code CUSTOM_DECO}
 * took over id 23 from {@code SIGN} — and nothing checked it.
 *
 * <p>Reflection over the constants rather than a hand-maintained list, so a terrain added
 * tomorrow is covered without anyone remembering to update this file.
 *
 * <p>Needs no libGDX: {@link Terrain} is plain constants and a static int array.
 */
class TerrainIdTest {

	/**
	 * IDs that intentionally carry more than one name, with the reason. Anything NOT listed
	 * here that shares an ID is a mistake — most likely a new terrain pasted from a sibling
	 * constant. Ratchet, like the lint baselines: park what is deliberate, fail what is new.
	 */
	private static final Map<Integer, String> DELIBERATE_ALIASES = new LinkedHashMap<>();
	static {
		// SIGN is a deprecated alias kept so old code and old saves still resolve; the
		// declaration says so: "re-uses the old terrain ID for signs".
		DELIBERATE_ALIASES.put(23, "CUSTOM_DECO/SIGN");
	}

	/**
	 * Terrain.flags bit values, not terrain IDs. They live in the same class as public
	 * static final ints, so reflection picks them up and they collide numerically with real
	 * terrain IDs (PASSABLE 0x01 vs EMPTY 1, SOLID 0x10 vs SECRET_DOOR 16, ...). Excluded by
	 * name because there is nothing in the declarations to tell them apart.
	 */
	//NB Arrays.asList, not Set.of: Set.of is Java 9+ and :core compiles with
	//options.release = 8, which correctly rejected it.
	private static final Set<String> FLAG_BITS = new LinkedHashSet<>(Arrays.asList(
			"PASSABLE", "LOS_BLOCKING", "FLAMABLE", "SECRET", "SOLID", "AVOID", "LIQUID", "PIT"));

	private static Map<String, Integer> terrainConstants() {
		Map<String, Integer> constants = new LinkedHashMap<>();
		for (Field f : Terrain.class.getDeclaredFields()) {
			if (f.getType() != int.class) continue;
			int mods = f.getModifiers();
			if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || !Modifier.isFinal(mods)) continue;
			if (FLAG_BITS.contains(f.getName())) continue;
			try {
				constants.put(f.getName(), f.getInt(null));
			} catch (IllegalAccessException e) {
				throw new AssertionError("could not read Terrain." + f.getName(), e);
			}
		}
		return constants;
	}

	@Test
	void constantsAreActuallyBeingRead() {
		// A reflection-driven test that silently finds nothing would pass forever. Terrain
		// has ~43 constants; a sharp drop means the filter above stopped matching.
		assertTrue(terrainConstants().size() >= 30,
				"expected to reflect over the terrain constants, found "
						+ terrainConstants().size());
	}

	@Test
	void noUndeclaredIdSharing() {
		Map<Integer, List<String>> byId = new LinkedHashMap<>();
		terrainConstants().forEach((name, id) ->
				byId.computeIfAbsent(id, k -> new ArrayList<>()).add(name));

		List<String> undeclared = new ArrayList<>();
		byId.forEach((id, names) -> {
			if (names.size() > 1 && !DELIBERATE_ALIASES.containsKey(id)) {
				undeclared.add(id + " shared by " + names);
			}
		});

		assertTrue(undeclared.isEmpty(),
				"terrain IDs are serialized in Level.map, so two terrains sharing one id "
						+ "makes old saves reinterpret tiles. If a new alias is deliberate, add it "
						+ "to DELIBERATE_ALIASES with the reason. Undeclared: " + undeclared);
	}

	@Test
	void theKnownAliasIsStillTheKnownAlias() {
		// Pins the one deliberate case, so silently repointing SIGN elsewhere fails here
		// rather than in someone's save file.
		Map<String, Integer> constants = terrainConstants();
		assertEquals(23, constants.get("CUSTOM_DECO"),
				"CUSTOM_DECO must keep id 23; it was chosen to reuse the retired SIGN id");
		assertEquals(constants.get("CUSTOM_DECO"), constants.get("SIGN"),
				"SIGN is a deprecated alias of CUSTOM_DECO");
	}

	@Test
	void everyTerrainIdFitsTheFlagsTable() {
		// flags is indexed directly by terrain id. An id past the end is an
		// ArrayIndexOutOfBounds the moment that tile is placed.
		Set<String> tooLarge = new LinkedHashSet<>();
		terrainConstants().forEach((name, id) -> {
			if (id >= Terrain.flags.length || id < 0) {
				tooLarge.add(name + "=" + id);
			}
		});
		assertTrue(tooLarge.isEmpty(),
				"terrain ids must index into Terrain.flags (length " + Terrain.flags.length
						+ "): " + tooLarge);
	}
}
