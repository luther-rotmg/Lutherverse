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

package com.qsr.customspd.actors.hero.abilities.keybearer;

import com.qsr.customspd.Assets;
import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.Actor;
import com.qsr.customspd.actors.Char;
import com.qsr.customspd.actors.buffs.Buff;
import com.qsr.customspd.actors.buffs.Burning;
import com.qsr.customspd.actors.buffs.Chill;
import com.qsr.customspd.actors.buffs.Invisibility;
import com.qsr.customspd.actors.buffs.Paralysis;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.hero.Talent;
import com.qsr.customspd.actors.hero.abilities.ArmorAbility;
import com.qsr.customspd.actors.hero.spheregrid.SphereGrid;
import com.qsr.customspd.assets.Asset;
import com.qsr.customspd.effects.MagicMissile;
import com.qsr.customspd.items.armor.ClassArmor;
import com.qsr.customspd.mechanics.Ballistica;
import com.qsr.customspd.mechanics.ConeAOE;
import com.qsr.customspd.ui.HeroIcon;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Random;

/**
 * Keyblade Nova — the Keybearer's class ability (build-craft spine). A self-centered elemental
 * burst: every nearby enemy takes damage scaled by your sphere-grid investment, then is
 * ignited, chilled, or shocked depending on which element you've specced (one dominant
 * element, so opposing statuses never cancel on a single target). The grid scaling ties the ability to the build — a
 * heavier grid means a stronger Nova.
 *
 * The damage scaling ({@link #gridBonus}) is a pure function so it can be unit-tested headless;
 * the AoE + VFX run in a live scene.
 */
public class KeybladeNova extends ArmorAbility {

	{
		baseChargeUse = 35f;
	}

	@Override
	public boolean useTargeting() {
		return false; // self-centered burst, no aiming
	}

	/** Grid-scaled bonus damage — rewards total sphere-grid investment. Pure/testable. */
	public static int gridBonus(SphereGrid grid) {
		if (grid == null) return 0;
		return grid.emberLevel() + grid.frostLevel() + grid.stormLevel()
				+ grid.mightLevel() + 2 * grid.abilityLevel();
	}

	/** The Nova's element. Ties resolve toward the earlier element (deterministic). */
	public enum Element { EMBER, FROST, STORM }

	/** Dominant element of a build — the heaviest element branch wins. Pure/testable. */
	public static Element dominantElement(SphereGrid grid) {
		if (grid == null) return Element.EMBER;
		int ember = grid.emberLevel(), frost = grid.frostLevel(), storm = grid.stormLevel();
		if (ember >= frost && ember >= storm) return Element.EMBER;
		if (frost >= storm) return Element.FROST;
		return Element.STORM;
	}

	@Override
	protected void activate(ClassArmor armor, Hero hero, Integer target) {
		SphereGrid grid = hero.sphereGrid;
		int bonus = gridBonus(grid);
		Element element = dominantElement(grid);

		Ballistica aim = new Ballistica(hero.pos, hero.pos + 1, Ballistica.WONT_STOP);
		ConeAOE aoe = new ConeAOE(aim, 4, 360, Ballistica.STOP_SOLID | Ballistica.STOP_TARGET);

		if (hero.sprite != null && hero.sprite.parent != null) {
			int vfx = element == Element.EMBER ? MagicMissile.FIRE_CONE
					: element == Element.FROST ? MagicMissile.FROST_CONE
					: MagicMissile.SPARK_CONE;
			for (Ballistica ray : aoe.outerRays) {
				((MagicMissile) hero.sprite.parent.recycle(MagicMissile.class)).reset(
						vfx, hero.sprite, ray.path.get(ray.dist), null);
			}
			hero.sprite.operate(hero.pos);
		}

		int scalingStr = hero.STR() - 10;
		for (int cell : aoe.cells) {
			Char ch = Actor.findChar(cell);
			if (ch != null && ch != hero && ch.alignment != hero.alignment) {
				int damage = Random.NormalIntRange(4 + scalingStr, 8 + 2 * scalingStr) + bonus;
				damage -= ch.drRoll();
				if (damage > 0) ch.damage(damage, this);
				if (ch.isAlive()) {
					switch (element) {
						case EMBER:
							Buff.affect(ch, Burning.class).reignite(ch);
							break;
						case FROST:
							Buff.affect(ch, Chill.class, 3f + (grid != null ? grid.frostLevel() : 0));
							break;
						case STORM:
							Buff.prolong(ch, Paralysis.class,
									1f + (grid != null ? grid.stormLevel() * 0.5f : 0));
							break;
					}
				}
			}
		}

		armor.charge -= chargeUse(hero);
		armor.updateQuickslot();
		Invisibility.dispel();
		hero.busy();
		hero.spendAndNext(Actor.TICK);
		Sample.INSTANCE.play(Assets.Sounds.BLAST, 1f, 0.8f);
	}

	@Override
	public Asset icon() {
		return HeroIcon.ELEMENTAL_BLAST; // placeholder icon until a keyblade-nova icon exists
	}

	@Override
	public Talent[] talents() {
		// The Keybearer progresses on the sphere grid, not talents; only the shared charge talent applies.
		return new Talent[]{ Talent.HEROIC_ENERGY };
	}
}
