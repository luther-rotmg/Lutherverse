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

import com.watabou.utils.Bundlable;
import com.watabou.utils.Bundle;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Prototype run-scoped sphere grid for the Keybearer (build-craft spine).
 *
 * Run-scoped only: unspent points and node activations live and die with the run
 * (serialized in the hero bundle). The persistent "Insight" unlock backbone is a
 * later increment. Activating a node spends a point (if ALL its prerequisites are met)
 * and contributes to the aggregate build modifiers combat and the hero read via the
 * {@code emberLevel()/frostLevel()/stormLevel()/mightLevel()/vigorLevel()/abilityLevel()}
 * accessors (the ELEMENTAL_CONFLUX keystone adds +1 inside each element accessor).
 *
 * A LinkedHashSet keeps activation order deterministic (seeded-run discipline).
 */
public class SphereGrid implements Bundlable {

	private final Set<SphereNode> activated = new LinkedHashSet<>();
	private int unspentPoints = 0;

	public int unspentPoints() {
		return unspentPoints;
	}

	public void grantPoints(int n) {
		if (n > 0) unspentPoints += n;
	}

	public boolean isActivated(SphereNode node) {
		return activated.contains(node);
	}

	public boolean prerequisiteMet(SphereNode node) {
		for (SphereNode req : node.requiredNodes()) {
			if (req == null || !activated.contains(req)) return false;
		}
		return true;
	}

	public boolean canActivate(SphereNode node) {
		return node != null
				&& !activated.contains(node)
				&& unspentPoints >= node.cost
				&& prerequisiteMet(node);
	}

	public boolean activate(SphereNode node) {
		if (!canActivate(node)) return false;
		unspentPoints -= node.cost;
		activated.add(node);
		return true;
	}

	// --- aggregate build modifiers read by combat / the hero ---

	public int emberLevel() {
		return sum(SphereNode.Effect.EMBER) + confluxBonus();
	}

	public int frostLevel() {
		return sum(SphereNode.Effect.FROST) + confluxBonus();
	}

	public int stormLevel() {
		return sum(SphereNode.Effect.STORM) + confluxBonus();
	}

	public int abilityLevel() {
		return sum(SphereNode.Effect.ABILITY);
	}

	/** Elemental Conflux keystone: +1 to EVERY element level while active. */
	private int confluxBonus() {
		return isActivated(SphereNode.ELEMENTAL_CONFLUX) ? 1 : 0;
	}

	public int mightLevel() {
		return sum(SphereNode.Effect.MIGHT);
	}

	public int vigorLevel() {
		return sum(SphereNode.Effect.VIGOR);
	}

	private int sum(SphereNode.Effect effect) {
		int total = 0;
		for (SphereNode n : activated) {
			if (n.effect == effect) total += n.magnitude;
		}
		return total;
	}

	// --- serialization (run-scoped: lives in the hero bundle) ---

	private static final String ACTIVATED = "activated";
	private static final String POINTS = "unspent_points";

	@Override
	public void storeInBundle(Bundle bundle) {
		String[] names = new String[activated.size()];
		int i = 0;
		for (SphereNode n : activated) {
			names[i++] = n.name();
		}
		bundle.put(ACTIVATED, names);
		bundle.put(POINTS, unspentPoints);
	}

	@Override
	public void restoreFromBundle(Bundle bundle) {
		activated.clear();
		for (String name : bundle.getStringArray(ACTIVATED)) {
			SphereNode n = SphereNode.byName(name);
			if (n != null) activated.add(n);
		}
		unspentPoints = bundle.getInt(POINTS);
	}
}
