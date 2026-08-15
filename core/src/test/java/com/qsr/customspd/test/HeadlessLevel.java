package com.qsr.customspd.test;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.Actor;
import com.qsr.customspd.actors.Char;
import com.qsr.customspd.levels.Level;
import com.qsr.customspd.levels.Terrain;

import java.util.Arrays;

/**
 * F3 test fixture: a minimal REAL {@link Level} for headless integration tests — an open
 * walled room with real flag maps, so anything that consults {@code Dungeon.level}
 * (adjacency, passability, cell math, Actor.findChar-based targeting) runs against the
 * genuine level code instead of being untestable. No scene, no rendering, no generation
 * RNG: build() is a no-op because {@link #open(int, int)} lays the map out directly.
 *
 * Usage:
 *   HeadlessLevel.install(8, 8);        // Dungeon.level set, Actor state cleared
 *   ... place chars with at(ch, cell) ...
 *   HeadlessLevel.uninstall();          // in @AfterEach / finally
 */
public class HeadlessLevel extends Level {

	/** An open w x h room ringed by walls, with flag maps built. */
	public static HeadlessLevel open(int w, int h) {
		HeadlessLevel level = new HeadlessLevel();
		// These collections are normally seeded by Level.create()/restore; buildFlagMaps
		// iterates blobs, so the fixture seeds them itself.
		level.mobs = new java.util.LinkedHashSet<>();
		level.heaps = new com.watabou.utils.SparseArray<>();
		level.blobs = new java.util.HashMap<>();
		level.customTiles = new java.util.HashSet<>();
		level.customWalls = new java.util.HashSet<>();
		level.setSize(w, h);
		Arrays.fill(level.map, Terrain.EMPTY);
		for (int x = 0; x < w; x++) {
			level.map[x] = Terrain.WALL;
			level.map[(h - 1) * w + x] = Terrain.WALL;
		}
		for (int y = 0; y < h; y++) {
			level.map[y * w] = Terrain.WALL;
			level.map[y * w + w - 1] = Terrain.WALL;
		}
		level.buildFlagMaps();
		return level;
	}

	/** Builds an open room, installs it as Dungeon.level, and clears the Actor clock. */
	public static HeadlessLevel install(int w, int h) {
		Actor.clear();
		HeadlessLevel level = open(w, h);
		Dungeon.level = level;
		return level;
	}

	/** Undo {@link #install}: clears Dungeon.level and all actors. Call in a finally. */
	public static void uninstall() {
		Dungeon.level = null;
		Actor.clear();
	}

	/** Places a char at a cell and registers it with the actor system (findChar sees it). */
	public static <T extends Char> T at(T ch, int cell) {
		ch.pos = cell;
		Actor.add(ch);
		return ch;
	}

	@Override
	protected boolean build() {
		return true; // the map is laid out by open(); nothing to generate
	}

	@Override
	protected void createItems() {
		// no items in the fixture
	}
}
