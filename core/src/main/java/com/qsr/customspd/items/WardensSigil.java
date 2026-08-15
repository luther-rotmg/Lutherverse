/*
 * Lutherverse -- original content (scaffolded by content-scaffold, mechanic filled in).
 * GPLv3; see the project license.
 */
package com.qsr.customspd.items;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.Actor;
import com.qsr.customspd.actors.Char;
import com.qsr.customspd.actors.buffs.Buff;
import com.qsr.customspd.actors.buffs.Cripple;
import com.qsr.customspd.assets.GeneralAsset;
import com.qsr.customspd.effects.Speck;
import com.qsr.customspd.items.stones.Runestone;
import com.watabou.utils.PathFinder;

/**
 * Warden's Sigil — a STONE-pool runestone: an aimed lock-stone. The gaol-warden's
 * binding ward flares where the stone lands: whatever stands on the struck cell is
 * locked hard ({@link Cripple}, {@link #DIRECT_DURATION}), and enemies beside it are
 * briefly held by the spill ({@link #SPLASH_DURATION}).
 *
 * Targeting rules, deliberately asymmetric:
 * <ul>
 *   <li>The DIRECT target may be ANY living char — the player aimed the throw, so a
 *       neutral (or even allied) char on the struck cell is fair game, exactly like
 *       aimed wands and thrown potions.</li>
 *   <li>The splash is strictly {@code Alignment.ENEMY} — a passive area effect must
 *       never hold neutral NPCs (shopkeeper, quest givers), matching
 *       {@code StormKeyblade.arcTarget}'s convention.</li>
 * </ul>
 * The splash scans {@link PathFinder#NEIGHBOURS8} in its fixed order (the determinism
 * contract: never an unordered collection), and {@code adjacent()} rejects row wrap at
 * level edges.
 */
public class WardensSigil extends Runestone {

	/** How long the ward locks the char on the struck cell. */
	public static final float DIRECT_DURATION = 5f;

	/** How long the spill holds enemies beside the struck cell. */
	public static final float SPLASH_DURATION = 2f;

	{
		image = GeneralAsset.WARDENS_SIGIL;
	}

	@Override
	protected void activate(int cell) {
		// The aimed target: any living char on the struck cell, alignment ignored.
		Char direct = Actor.findChar(cell);
		if (direct != null && direct.isAlive()) {
			Buff.prolong(direct, Cripple.class, DIRECT_DURATION);
		}

		// The spill: fixed NEIGHBOURS8 scan order, enemies only.
		for (int offset : PathFinder.NEIGHBOURS8) {
			int c = cell + offset;
			if (Dungeon.level != null && Dungeon.level.adjacent(cell, c)) {
				Char ch = Actor.findChar(c);
				if (ch != null && ch.isAlive() && ch.alignment == Char.Alignment.ENEMY) {
					Buff.prolong(ch, Cripple.class, SPLASH_DURATION);
				}
			}
		}

		if (Dungeon.hero != null && Dungeon.hero.sprite != null && Dungeon.hero.sprite.parent != null) {
			com.qsr.customspd.effects.CellEmitter.center(cell).burst(Speck.factory(Speck.LIGHT), 8);
		}
	}

	@Override
	public int value() {
		return 25 * quantity; // aimed hard-CC: pricier than a common runestone
	}
}
