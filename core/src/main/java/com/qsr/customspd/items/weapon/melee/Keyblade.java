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
import com.qsr.customspd.assets.GeneralAsset;

/**
 * Prototype keyblade for the Keybearer (build-craft ARPG spine).
 *
 * First increment implements the FIRE element axis only: every damaging strike
 * reignites the target, so the weapon plays visibly differently from a plain blade.
 * The signature-ability axis and the sphere-grid amplification hook onto this later;
 * for now the burn IS the keyblade's identity. Art reuses the sword sprite as a
 * placeholder until a real keyblade PNG exists.
 */
public class Keyblade extends MeleeWeapon {

	{
		image = GeneralAsset.SWORD; // placeholder art until a keyblade sprite exists
		hitSound = Assets.Sounds.HIT_SLASH;
		hitSoundPitch = 1.1f;

		tier = 2;
	}

	@Override
	public int proc(Char attacker, Char defender, int damage) {
		damage = super.proc(attacker, defender, damage);
		// Fire element: a damaging strike sets the target alight.
		if (damage > 0 && defender.isAlive()) {
			Buff.affect(defender, Burning.class).reignite(defender);
		}
		return damage;
	}
}
