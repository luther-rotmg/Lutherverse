/*
 * Lutherverse
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.qsr.customspd.actors.hero.spheregrid;

import com.watabou.utils.Bundle;
import com.watabou.utils.FileUtils;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persistent (cross-run) half of the hybrid Keybearer sphere grid: an "Insight" currency plus the
 * set of grid nodes it has permanently UNLOCKED. Persisted to its own bundle file via
 * {@link FileUtils} (the same mechanism {@code Badges} uses), so it survives runs and deaths —
 * independent of the run-scoped {@link SphereGrid}, which handles per-run activation of nodes you
 * have already unlocked.
 *
 * The two layers combine at the activation site (the grid screen): a node must be unlocked HERE
 * (spending Insight, once, forever) before {@code SphereGrid} can activate it THIS run (spending a
 * run-scoped point). Insight is earned as the Keybearer levels. Kept out of {@code SphereGrid}
 * itself so that class stays pure/libGDX-free and unit-testable without a headless boot.
 */
public final class SphereGridProgress {

	private static final String FILE     = "keybearer_progress.dat";
	private static final String INSIGHT  = "insight";
	private static final String UNLOCKED = "unlocked";
	private static final int    UNLOCK_COST = 1; // Insight per node (prototype)

	private static boolean loaded = false;
	private static int insight = 0;
	private static final Set<String> unlocked = new LinkedHashSet<>();

	private static void ensureLoaded() {
		if (loaded) return;
		insight = 0;
		unlocked.clear();
		try {
			if (FileUtils.getFileHandle(FILE).exists()) {
				Bundle b = FileUtils.bundleFromFile(FILE);
				insight = b.getInt(INSIGHT);
				for (String n : b.getStringArray(UNLOCKED)) {
					if (n != null && !n.isEmpty()) unlocked.add(n);
				}
			}
		} catch (IOException e) {
			// missing/corrupt -> start fresh
		}
		loaded = true;
	}

	private static void save() {
		Bundle b = new Bundle();
		b.put(INSIGHT, insight);
		b.put(UNLOCKED, unlocked.toArray(new String[0]));
		try {
			FileUtils.bundleToFile(FILE, b);
		} catch (IOException e) {
			// best effort; a persistence hiccup must not crash gameplay
		}
	}

	public static int insight() {
		ensureLoaded();
		return insight;
	}

	public static void earnInsight(int n) {
		if (n <= 0) return;
		ensureLoaded();
		insight += n;
		save();
	}

	public static Set<String> unlockedNames() {
		ensureLoaded();
		return new LinkedHashSet<>(unlocked);
	}

	public static boolean isUnlocked(SphereNode node) {
		ensureLoaded();
		return node != null && unlocked.contains(node.name());
	}

	public static boolean prerequisiteUnlocked(SphereNode node) {
		SphereNode req = node.requiredNode();
		return req == null || isUnlocked(req);
	}

	public static int unlockCost(SphereNode node) {
		return UNLOCK_COST;
	}

	public static boolean canUnlock(SphereNode node) {
		ensureLoaded();
		return node != null
				&& !unlocked.contains(node.name())
				&& insight >= unlockCost(node)
				&& prerequisiteUnlocked(node);
	}

	public static boolean unlock(SphereNode node) {
		if (!canUnlock(node)) return false;
		insight -= unlockCost(node);
		unlocked.add(node.name());
		save();
		return true;
	}

	/** Wipe persistent progress (tests, or a future 'new profile'). */
	public static void reset() {
		ensureLoaded();
		insight = 0;
		unlocked.clear();
		save();
	}

	private SphereGridProgress() {}
}
