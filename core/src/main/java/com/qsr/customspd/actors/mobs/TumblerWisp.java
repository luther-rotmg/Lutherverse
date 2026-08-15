/*
 * Lutherverse -- original content (scaffolded by content-scaffold, mechanic filled in).
 * GPLv3; see the project license.
 */
package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.Actor;
import com.qsr.customspd.actors.Char;
import com.qsr.customspd.items.Gold;
import com.qsr.customspd.sprites.TumblerWispSprite;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayList;

/**
 * TumblerWisp — a flittering mote of locksmith's magic (original Lutherverse mob), escaped
 * from some long-broken mechanism. The moment it is struck it BLINKS: it tumbles to a random
 * free adjacent cell, like a lock pin springing out from under the pick.
 *
 * Depth-2 stat line: paper HP but a high dodge for the Sewers, and the blink means even a
 * landed hit rarely earns a second one in the same spot — the design lever is the
 * reposition, not the numbers. It flies, so water and grass never pin it down.
 */
public class TumblerWisp extends Mob {

	{
		spriteClass = TumblerWispSprite.class;

		HP = HT = 8;
		defenseSkill = 9;

		EXP = 2;
		maxLvl = 7; // a depth-2 Sewers mote must stop paying XP before Prison mobs do

		loot = Gold.class;
		lootChance = 0.20f; // it carries a fleck of the locksmith's tribute

		flying = true;
	}

	@Override
	public int damageRoll() {
		return Random.NormalIntRange(1, 4);
	}

	@Override
	public int attackSkill(Char target) {
		return 10;
	}

	@Override
	public int defenseProc(Char enemy, int damage) {
		damage = super.defenseProc(enemy, damage);

		// The tumble: a wounding hit springs the wisp to a random free adjacent cell.
		// Candidates are gathered in PathFinder.NEIGHBOURS8's fixed order and picked with
		// Random.Int, so seeded runs reproduce (determinism contract).
		if (damage > 0 && isAlive() && Dungeon.level != null) {
			ArrayList<Integer> candidates = new ArrayList<>();
			for (int offset : PathFinder.NEIGHBOURS8) {
				int cell = pos + offset;
				if (Dungeon.level.adjacent(pos, cell)
						&& Dungeon.level.passable[cell]
						&& Actor.findChar(cell) == null) {
					candidates.add(cell);
				}
			}
			if (!candidates.isEmpty()) {
				pos = candidates.get(Random.Int(candidates.size()));
				if (sprite != null) {
					sprite.place(pos);
				}
			}
		}

		return damage;
	}
}
