/*
 * Lutherverse -- scaffolded content stub.
 * GPLv3; see the project license. Replace the TODOs with the real mechanic + art.
 */
package com.qsr.customspd.items;

import com.qsr.customspd.assets.GeneralAsset;

public class WardensSigil extends Item {

	{
		image = GeneralAsset.WARDENS_SIGIL;
		// TODO: stackable / defaultAction / mechanic
	}

	@Override
	public boolean isUpgradable() {
		return false;
	}

	@Override
	public boolean isIdentified() {
		return true;
	}
}
