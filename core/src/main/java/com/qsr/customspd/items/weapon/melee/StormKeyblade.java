/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2023 Evan Debenham
 *
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

package com.qsr.customspd.items.weapon.melee;

import com.qsr.customspd.Assets;
import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.Actor;
import com.qsr.customspd.actors.Char;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.hero.spheregrid.SphereGrid;
import com.qsr.customspd.assets.GeneralAsset;
import com.watabou.utils.PathFinder;

/**
 * Prototype STORM keyblade for the Keybearer — the third element, and the first whose
 * identity is spatial: shock ARCS. Where fire stacks damage-over-time and frost plays
 * for control, storm plays for crowds:
 *  - MIGHT nodes add flat melee damage (shared build axis)
 *  - STORM nodes add bonus shock damage on hit, and let the strike arc to an enemy
 *    adjacent to the target ({@link #arcTarget}) for half the storm damage
 *  - Static Discharge (signature ability): burst = stormLevel * abilityLevel — the
 *    hybrid storm+ability payoff, mirroring Flame Burst / Rime Shatter
 *  - Conduction (Keybearer): carrying another elemental keyblade while single-wielding
 *    grounds the circuit for bonus damage — the storm analog of Thermal Shock
 *
 * The arc picks its target by scanning the defender's neighbours in the fixed
 * PathFinder.NEIGHBOURS8 order (never an unordered collection) so seeded runs reproduce.
 */
public class StormKeyblade extends MeleeWeapon {

	{
		image = GeneralAsset.STORM_KEYBLADE; // original 16x16 keyblade sprite (storm variant)
		hitSound = Assets.Sounds.HIT_SLASH;
		hitSoundPitch = 1.25f;

		tier = 2;
	}

	@Override
	public int damageRoll(Char owner) {
		int damage = super.damageRoll(owner);
		SphereGrid grid = gridOf(owner);
		if (grid != null) {
			damage += grid.mightLevel(); // Might nodes: raw melee damage (shared axis)
		}
		return damage;
	}

	@Override
	public int proc(Char attacker, Char defender, int damage) {
		damage = super.proc(attacker, defender, damage);
		if (damage > 0 && defender.isAlive()) {
			SphereGrid grid = gridOf(attacker);
			int storm = grid != null ? grid.stormLevel() : 0;
			if (storm > 0) {
				// Storm nodes: bonus shock damage on the hit.
				defender.damage(storm, this);
			}
			// Static Discharge (signature ability): burst = stormLevel * abilityLevel — the
			// hybrid storm+ability payoff, nothing without investing in both.
			if (grid != null && defender.isAlive()) {
				int burst = storm * grid.abilityLevel();
				if (burst > 0) defender.damage(burst, this);
			}
			// The arc: storm damage jumps to one enemy adjacent to the target for half the
			// shock (rounded up). This is the element's identity — fire burns one, frost
			// slows one, storm punishes clustering.
			if (storm > 0) {
				Char arc = arcTarget(attacker, defender);
				if (arc != null) {
					arc.damage((storm + 1) / 2, this);
				}
			}
			// Conduction (Keybearer): another elemental keyblade in the pack grounds the
			// circuit while single-wielding — bonus damage, mirroring Thermal Shock's shape
			// (dual-wield's off-hand strike replaces this bonus, exactly like the others).
			if (defender.isAlive() && attacker instanceof Hero
					&& ((Hero) attacker).belongings.secondWep == null
					&& (((Hero) attacker).belongings.getItem(Keyblade.class) != null
						|| ((Hero) attacker).belongings.getItem(FrostKeyblade.class) != null)) {
				defender.damage(2, this);
			}
		}
		return damage;
	}

	/**
	 * The arc's victim: the first enemy of the attacker standing adjacent to the defender,
	 * scanning PathFinder.NEIGHBOURS8 in its fixed order (deterministic under seeded runs).
	 * Null when there is no level (headless), no neighbour, or no valid enemy.
	 */
	public static Char arcTarget(Char attacker, Char defender) {
		if (Dungeon.level == null) return null;
		for (int offset : PathFinder.NEIGHBOURS8) {
			Char ch = Actor.findChar(defender.pos + offset);
			if (ch != null && ch != attacker && ch != defender
					&& ch.alignment != attacker.alignment && ch.isAlive()) {
				return ch;
			}
		}
		return null;
	}

	private static SphereGrid gridOf(Char ch) {
		return (ch instanceof Hero) ? ((Hero) ch).sphereGrid : null;
	}
}
