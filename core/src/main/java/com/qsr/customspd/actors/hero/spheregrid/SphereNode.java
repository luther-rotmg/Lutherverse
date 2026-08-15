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

/**
 * Prototype fixed node set for the Keybearer sphere grid (build-craft spine).
 *
 * Three clusters expressed as effects — EMBER (fire, amplifies the keyblade burn),
 * MIGHT (raw melee damage), VIGOR (survivability) — connected in a tiny branching web
 * via {@link #requires} (a node's prerequisite by name, or null for a root). Magnitudes
 * are deliberately coarse; this exists to prove the loop, not to balance it.
 *
 * requires is stored as a String name (not a SphereNode reference) to avoid enum
 * self-reference during initialization.
 */
public enum SphereNode {

	// Root
	ATTUNEMENT(Effect.MIGHT, 1, 1, null),

	// Fire branch — amplifies the keyblade's burn
	EMBER_I(Effect.EMBER, 1, 1, "ATTUNEMENT"),
	EMBER_II(Effect.EMBER, 1, 1, "EMBER_I"),
	CONFLAGRATION(Effect.EMBER, 2, 1, "EMBER_II"),

	// Might branch — raw melee damage
	MIGHT_I(Effect.MIGHT, 1, 1, "ATTUNEMENT"),
	MIGHT_II(Effect.MIGHT, 2, 1, "MIGHT_I"),

	// Vigor branch — survivability
	VIGOR_I(Effect.VIGOR, 3, 1, "ATTUNEMENT"),
	VIGOR_II(Effect.VIGOR, 3, 1, "VIGOR_I"),

	// Frost branch — the second element (chill/slow); amplifies the frost keyblade
	FROST_I(Effect.FROST, 1, 1, "ATTUNEMENT"),
	FROST_II(Effect.FROST, 2, 1, "FROST_I"),
	PERMAFROST(Effect.FROST, 2, 2, "FROST_II"),

	// Storm branch — the third element (arcing shock); amplifies the storm keyblade
	STORM_I(Effect.STORM, 1, 1, "ATTUNEMENT"),
	STORM_II(Effect.STORM, 2, 1, "STORM_I"),
	TEMPEST(Effect.STORM, 2, 2, "STORM_II"),

	// Might/Vigor deep tiers — pricier capstones for the raw-stat branches
	MIGHT_III(Effect.MIGHT, 2, 2, "MIGHT_II"),
	VIGOR_III(Effect.VIGOR, 4, 2, "VIGOR_II"),

	// Ability branch — empowers each keyblade's signature ability (burst = elementLevel * abilityLevel),
	// so it pays off only alongside an element: the design's hybrid element+ability quadrant.
	ABILITY_I(Effect.ABILITY, 1, 1, "ATTUNEMENT"),
	ABILITY_II(Effect.ABILITY, 2, 1, "ABILITY_I"),
	ABILITY_III(Effect.ABILITY, 2, 2, "ABILITY_II"),

	// Keystone — requires deep investment in ALL THREE elements; grants +1 to every
	// element level (implemented in SphereGrid's accessors, magnitude 0 here so the
	// plain ABILITY sum does not double-count it).
	ELEMENTAL_CONFLUX(Effect.ABILITY, 0, 2, "EMBER_II,FROST_II,STORM_II");

	public enum Effect { EMBER, FROST, STORM, MIGHT, VIGOR, ABILITY }

	public final Effect effect;
	public final int magnitude;
	public final int cost;
	public final String requires;

	SphereNode(Effect effect, int magnitude, int cost, String requires) {
		this.effect = effect;
		this.magnitude = magnitude;
		this.cost = cost;
		this.requires = requires;
	}

	/** The node's first (or only) prerequisite — kept for single-prereq callers/UI. */
	public SphereNode requiredNode() {
		SphereNode[] all = requiredNodes();
		return all.length == 0 ? null : all[0];
	}

	/**
	 * All prerequisites (comma-separated in {@link #requires}); empty for a root.
	 * Keystones use this to demand several branches at once.
	 */
	public SphereNode[] requiredNodes() {
		if (requires == null) return new SphereNode[0];
		String[] names = requires.split(",");
		SphereNode[] nodes = new SphereNode[names.length];
		for (int i = 0; i < names.length; i++) {
			nodes[i] = byName(names[i].trim());
		}
		return nodes;
	}

	public static SphereNode byName(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}
}
