/*
 * Lutherverse -- original content (scaffolded by content-scaffold, mechanic filled in).
 * GPLv3; see the project license.
 */
package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.actors.Char;
import com.qsr.customspd.actors.buffs.Buff;
import com.qsr.customspd.actors.buffs.Vertigo;
import com.qsr.customspd.sprites.HexwardMothSprite;
import com.watabou.utils.Random;

/**
 * HexwardMoth — a plump moth shedding disorienting ward-dust (original Lutherverse mob,
 * produced through the content-scaffold pipeline). Its wings are speckled with pale
 * hex-runes; every landed hit shakes loose a puff of that dust and sends the victim
 * reeling ({@link Vertigo}).
 *
 * Depth-4 stat line: fragile and low-damage, but it flies and its touch scrambles your
 * footing — the design lever is the Vertigo proc, not the numbers.
 */
public class HexwardMoth extends Mob {

	{
		spriteClass = HexwardMothSprite.class;

		HP = HT = 14;
		defenseSkill = 8;

		EXP = 3;
		maxLvl = 10;

		// No loot: the moth carries nothing but its dust.

		flying = true;
	}

	@Override
	public int damageRoll() {
		return Random.NormalIntRange(2, 5);
	}

	@Override
	public int attackSkill(Char target) {
		return 12;
	}

	@Override
	public int attackProc(Char enemy, int damage) {
		damage = super.attackProc(enemy, damage);
		if (damage > 0 && enemy.isAlive()) {
			// The dust: a puff of hex-rune ward-dust briefly disorients the victim.
			Buff.prolong(enemy, Vertigo.class, 2f);
		}
		return damage;
	}
}
