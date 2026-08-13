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
import com.qsr.customspd.actors.buffs.Burning;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.hero.spheregrid.SphereGrid;
import com.qsr.customspd.assets.GeneralAsset;

/**
 * Prototype keyblade for the Keybearer (build-craft ARPG spine).
 *
 * FIRE element: every damaging strike reignites the target. The sphere grid amplifies
 * this build:
 *  - MIGHT nodes add flat melee damage ({@link #damageRoll}).
 *  - EMBER nodes add bonus fire damage on the burning hit ({@link #proc}).
 * So "spec fire" and "spec might" visibly change how the keyblade plays. The signature
 * ability axis, real art, and Vigor's effect land in later increments.
 */
public class Keyblade extends MeleeWeapon {

	{
		image = GeneralAsset.SWORD; // placeholder art until a keyblade sprite exists
		hitSound = Assets.Sounds.HIT_SLASH;
		hitSoundPitch = 1.1f;

		tier = 2;
	}

	@Override
	public int damageRoll(Char owner) {
		int damage = super.damageRoll(owner);
		SphereGrid grid = gridOf(owner);
		if (grid != null) {
			damage += grid.mightLevel(); // Might nodes: raw melee damage
		}
		return damage;
	}

	@Override
	public int proc(Char attacker, Char defender, int damage) {
		damage = super.proc(attacker, defender, damage);
		if (damage > 0 && defender.isAlive()) {
			// Fire element: a damaging strike sets the target alight.
			Buff.affect(defender, Burning.class).reignite(defender);

			SphereGrid grid = gridOf(attacker);
			if (grid != null && grid.emberLevel() > 0) {
				// Ember nodes: bonus fire damage on top of the burn.
				defender.damage(grid.emberLevel(), this);
			}
		}
		return damage;
	}

	private static SphereGrid gridOf(Char ch) {
		return (ch instanceof Hero) ? ((Hero) ch).sphereGrid : null;
	}
}
