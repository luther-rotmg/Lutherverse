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
import com.qsr.customspd.actors.Char;
import com.qsr.customspd.actors.buffs.Buff;
import com.qsr.customspd.actors.buffs.Chill;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.hero.spheregrid.SphereGrid;
import com.qsr.customspd.assets.GeneralAsset;

/**
 * Prototype FROST keyblade for the Keybearer — the second element, proving the build axis
 * actually diverges. Where the fire {@link Keyblade} deals damage-over-time, this one plays
 * for control: every damaging strike chills (slows) the target. The sphere grid amplifies it:
 *  - MIGHT nodes add flat melee damage (shared build axis with the fire keyblade)
 *  - FROST nodes lengthen the chill ({@link #proc})
 * So "spec frost" turns the keyblade into a slow-lock weapon, a different build from fire's
 * burn stacking. Art reuses the sword sprite as a placeholder for now.
 */
public class FrostKeyblade extends MeleeWeapon {

	private static final float BASE_CHILL = 2f;

	{
		image = GeneralAsset.SWORD; // placeholder art until a frost keyblade sprite exists
		hitSound = Assets.Sounds.HIT_SLASH;
		hitSoundPitch = 0.9f;

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
			float turns = BASE_CHILL;
			SphereGrid grid = gridOf(attacker);
			if (grid != null) {
				turns += grid.frostLevel(); // Frost nodes: a longer chill
			}
			Buff.affect(defender, Chill.class, turns);
			// Rime Shatter (signature ability): burst = frostLevel * abilityLevel — the hybrid
			// frost+ability payoff.
			if (grid != null && defender.isAlive()) {
				int burst = grid.frostLevel() * grid.abilityLevel();
				if (burst > 0) defender.damage(burst, this);
			}
			// Dual Attunement (Keybearer): carrying the fire keyblade too clashes the opposing
			// elements for a Thermal Shock burst — bonus damage, not a stacked burn (applying the
			// opposite status would just cancel this keyblade's own chill; see Keyblade).
			if (defender.isAlive() && attacker instanceof Hero
					&& ((Hero) attacker).belongings.secondWep == null // not dual-wielding (else the off-hand strike replaces this)
					&& ((Hero) attacker).belongings.getItem(Keyblade.class) != null) {
				defender.damage(2, this);
			}
		}
		return damage;
	}

	private static SphereGrid gridOf(Char ch) {
		return (ch instanceof Hero) ? ((Hero) ch).sphereGrid : null;
	}
}
