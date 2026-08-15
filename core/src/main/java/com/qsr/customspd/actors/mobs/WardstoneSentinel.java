/*
 * Lutherverse -- original content (scaffolded by content-scaffold, mechanic filled in).
 * GPLv3; see the project license.
 */
package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.actors.Char;
import com.qsr.customspd.sprites.WardstoneSentinelSprite;
import com.watabou.utils.Random;

/**
 * Wardstone Sentinel — an animated lock-golem (original Lutherverse mob). A block of warded
 * lockstone carved into a crude keeper of doors that no longer exist: it grinds forward at
 * three-quarter speed, and its graven wards shrug off a slice of every blow.
 *
 * Depth-6 Prison stat line: the design lever is the armor band ({@link #drRoll()}), not a
 * proc — it is slow and hits no harder than its peers, but chip damage barely marks it.
 * The counterplay is armor-ignoring or large single hits, or simply walking away from it.
 */
public class WardstoneSentinel extends Mob {

	{
		spriteClass = WardstoneSentinelSprite.class;

		HP = HT = 30;
		defenseSkill = 4; // a stone slab does not dodge — the wards absorb instead

		EXP = 6;
		maxLvl = 13; // Prison band — stops paying out where Caves mobs take over

		baseSpeed = 0.75f; // it grinds, it does not walk

		// Animated stone: no blood, no mind, no flesh to poison.
		properties.add(Property.INORGANIC);
	}

	@Override
	public int damageRoll() {
		return Random.NormalIntRange(4, 8);
	}

	@Override
	public int attackSkill(Char target) {
		return 14;
	}

	@Override
	public int drRoll() {
		// The graven wards: a flat 2-6 armor band on top of whatever Char contributes
		// (Barkskin etc.), kept additive so future armor logic composes.
		return super.drRoll() + Random.NormalIntRange(2, 6);
	}
}
