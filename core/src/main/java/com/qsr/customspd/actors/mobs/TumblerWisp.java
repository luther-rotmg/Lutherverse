/*
 * Lutherverse -- scaffolded content stub.
 * GPLv3; see the project license. Replace the TODOs with the real mechanic + art.
 */
package com.qsr.customspd.actors.mobs;

import com.qsr.customspd.actors.Char;
import com.qsr.customspd.sprites.TumblerWispSprite;
import com.watabou.utils.Random;

public class TumblerWisp extends Mob {

	{
		spriteClass = TumblerWispSprite.class;

		// TODO: real stats
		HP = HT = 10;
		defenseSkill = 5;
		maxLvl = 10;
	}

	@Override
	public int damageRoll() {
		// TODO: mechanic
		return Random.NormalIntRange(1, 4);
	}

	@Override
	public int attackSkill(Char target) {
		return 10;
	}

	@Override
	public int drRoll() {
		return super.drRoll();
	}
}
