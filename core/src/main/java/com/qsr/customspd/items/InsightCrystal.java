/*
 * Lutherverse -- original content (scaffolded by content-scaffold, mechanic filled in).
 * GPLv3; see the project license.
 */
package com.qsr.customspd.items;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.hero.spheregrid.SphereGridProgress;
import com.qsr.customspd.assets.GeneralAsset;
import com.qsr.customspd.effects.Speck;
import com.qsr.customspd.items.stones.Runestone;
import com.qsr.customspd.utils.GLog;

/**
 * Insight Crystal — a runestone-pool item that feeds the Keybearer's PERSISTENT
 * meta-progression from the dungeon floor: shattering it (throw, like any runestone)
 * grants {@link SphereGridProgress} Insight that survives the run. Until now Insight
 * only trickled from XP; this makes exploration itself pay into the build-craft spine,
 * and it is the first ITEM shipped end-to-end through the content-scaffold pipeline.
 *
 * The grant goes through the persistent layer regardless of hero class — a non-Keybearer
 * finding one banks Insight for their future Keybearer runs (deliberate: meta-currency).
 */
public class InsightCrystal extends Runestone {

	/** Insight granted per shattered crystal. */
	public static final int INSIGHT_PER_CRYSTAL = 1;

	{
		image = GeneralAsset.INSIGHT_CRYSTAL;
	}

	@Override
	protected void activate(int cell) {
		SphereGridProgress.earnInsight(INSIGHT_PER_CRYSTAL);
		GLog.p(com.qsr.customspd.messages.Messages.get(InsightCrystal.class, "shatter", INSIGHT_PER_CRYSTAL));
		if (Dungeon.hero != null && Dungeon.hero.sprite != null && Dungeon.hero.sprite.parent != null) {
			com.qsr.customspd.effects.CellEmitter.center(cell).burst(Speck.factory(Speck.LIGHT), 6);
		}
	}

	@Override
	public int value() {
		return 30 * quantity; // meta-currency: pricier than a common runestone
	}
}
