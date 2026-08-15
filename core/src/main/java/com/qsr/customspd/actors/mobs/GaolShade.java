/*
 * Lutherverse -- original content (scaffolded by content-scaffold, mechanic filled in).
 * GPLv3; see the project license.
 */
package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.Char;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.items.Gold;
import com.qsr.customspd.sprites.GaolShadeSprite;
import com.qsr.customspd.utils.GLog;
import com.watabou.utils.Random;

/**
 * GaolShade — the Keywraith's crueler kin (original Lutherverse mob). Where the Keywraith
 * was the gaol's warden, this is its tax-collector: an undead clerk still exacting tribute,
 * and every hit that draws blood also SKIMS GOLD from the hero's purse. The design lever is
 * economic pressure, not raw damage — it never takes more than a small fixed cut per hit,
 * and never more gold than the hero actually carries.
 *
 * Depth-7 stat line (Prison band): sturdier and far more accurate than its Sewers kin,
 * with a matching richer gold hoard (higher lootChance) — it drops back what it collects.
 */
public class GaolShade extends Mob {

	/** The fixed cut it collects per damaging hit — never more than the hero carries. */
	public static final int TRIBUTE_PER_HIT = 10;

	{
		spriteClass = GaolShadeSprite.class;

		HP = HT = 22;
		defenseSkill = 12;

		EXP = 7;
		maxLvl = 14; // Prison band — a depth-7 mob, one tier above the Keywraith

		loot = Gold.class;
		lootChance = 0.50f; // it hoards what it collects, so it drops it back often

		// Like the Keywraith it IS the ghost it claims to be: holy damage, transfusion,
		// etc. must treat it as undead (matches Wraith).
		properties.add(Property.UNDEAD);
		properties.add(Property.INORGANIC);
	}

	@Override
	public int damageRoll() {
		return Random.NormalIntRange(3, 7);
	}

	@Override
	public int attackSkill(Char target) {
		return 16;
	}

	@Override
	public int attackProc(Char enemy, int damage) {
		damage = super.attackProc(enemy, damage);
		if (damage > 0 && enemy instanceof Hero && enemy.isAlive() && Dungeon.gold > 0) {
			// The tribute: a fixed cut per damaging hit, capped by what the hero carries.
			int stolen = Math.min(TRIBUTE_PER_HIT, Dungeon.gold);
			Dungeon.gold -= stolen;
			GLog.n("The gaol shade collects %d gold in tribute!", stolen);
		}
		return damage;
	}
}
