/*
 * Lutherverse -- original content (scaffolded by content-scaffold, mechanic filled in).
 * GPLv3; see the project license.
 */
package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.actors.Char;
import com.qsr.customspd.actors.buffs.Buff;
import com.qsr.customspd.actors.buffs.Cripple;
import com.qsr.customspd.items.Gold;
import com.qsr.customspd.sprites.KeywraithSprite;
import com.watabou.utils.Random;

/**
 * Keywraith — a spectral key-warden (original Lutherverse mob, the first content produced
 * through the content-scaffold pipeline). The ghost of a jailer still making its rounds:
 * its touch turns a phantom key in the victim's joints and LOCKS them ({@link Cripple}),
 * and it hoards the tribute of everyone it has ever locked in (gold loot).
 *
 * Depth-3 stat line (between Crab and Skeleton): fragile but accurate, and the lock proc
 * makes fleeing it expensive — the design lever is the Cripple, not the numbers.
 */
public class Keywraith extends Mob {

	{
		spriteClass = KeywraithSprite.class;

		HP = HT = 16;
		defenseSkill = 6;

		EXP = 4;
		maxLvl = 9; // Crab's band — it is a Sewers mob, and the description promises no more

		loot = Gold.class;
		lootChance = 0.25f;

		// It IS the ghost it claims to be: holy damage, transfusion, etc. must treat it
		// as undead (matches Wraith).
		properties.add(Property.UNDEAD);
		properties.add(Property.INORGANIC);
	}

	@Override
	public int damageRoll() {
		return Random.NormalIntRange(2, 6);
	}

	@Override
	public int attackSkill(Char target) {
		return 11;
	}

	@Override
	public int attackProc(Char enemy, int damage) {
		damage = super.attackProc(enemy, damage);
		if (damage > 0 && enemy.isAlive()) {
			// The lock: a phantom key turns in the victim's joints, crippling them briefly.
			Buff.prolong(enemy, Cripple.class, 2f);
		}
		return damage;
	}
}
