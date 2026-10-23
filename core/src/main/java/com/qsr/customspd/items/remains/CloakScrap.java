/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2023 Evan Debenham
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

package com.qsr.customspd.items.remains;

import com.qsr.customspd.Assets;
import com.qsr.customspd.actors.buffs.Buff;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.assets.GeneralAsset;
import com.qsr.customspd.items.artifacts.Artifact;
import com.qsr.customspd.items.scrolls.ScrollOfRecharging;
import com.watabou.noosa.audio.Sample;

public class CloakScrap extends RemainsItem {

	{
		image = GeneralAsset.CLOAK_SCRAP;
	}

	@Override
	protected void doEffect(Hero hero) {
		for (Buff b : hero.buffs()){
			if (b instanceof Artifact.ArtifactBuff){
				if (!((Artifact.ArtifactBuff) b).isCursed()) ((Artifact.ArtifactBuff) b).charge(hero, 4);
			}
		}
		ScrollOfRecharging.charge(hero);
		Sample.INSTANCE.play( Assets.Sounds.CHARGEUP );
	}
}
