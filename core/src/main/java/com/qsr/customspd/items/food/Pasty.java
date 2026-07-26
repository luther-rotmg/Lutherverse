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

package com.qsr.customspd.items.food;

import com.qsr.customspd.actors.buffs.Buff;
import com.qsr.customspd.actors.buffs.Hunger;
import com.qsr.customspd.actors.buffs.Recharging;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.effects.Speck;
import com.qsr.customspd.items.scrolls.ScrollOfRecharging;
import com.qsr.customspd.messages.Messages;
import com.qsr.customspd.assets.GeneralAsset;
import com.qsr.customspd.utils.Holiday;

public class Pasty extends Food {

	{
		reset();

		energy = Hunger.STARVING;

		bones = true;
	}
	
	@Override
	public void reset() {
		super.reset();
		switch(Holiday.getCurrentHoliday()){
			case NONE:
				image = GeneralAsset.PASTY;
				break;
			case HALLOWEEN:
				image = GeneralAsset.PUMPKIN_PIE;
				break;
			case WINTER_HOLIDAYS:
				image = GeneralAsset.CANDY_CANE;
				break;
		}
	}
	
	@Override
	protected void satisfy(Hero hero) {
		super.satisfy(hero);
		
		switch(Holiday.getCurrentHoliday()){
			default:
				break; //do nothing extra
			case HALLOWEEN:
				//heals for 10% max hp
				hero.HP = Math.min(hero.HP + hero.HT/10, hero.HT);
				hero.sprite.emitter().burst( Speck.factory( Speck.HEALING ), 1 );
				break;
			case WINTER_HOLIDAYS:
				Buff.affect( hero, Recharging.class, 2f ); //half of a charge
				ScrollOfRecharging.charge( hero );
				break;
		}
	}

	@Override
	public String name() {
		switch(Holiday.getCurrentHoliday()){
			case NONE: default:
				return Messages.get(this, "pasty");
			case HALLOWEEN:
				return Messages.get(this, "pie");
			case WINTER_HOLIDAYS:
				return Messages.get(this, "cane");
		}
	}

	@Override
	public String info() {
		switch(Holiday.getCurrentHoliday()){
			case NONE: default:
				return Messages.get(this, "pasty_desc");
			case HALLOWEEN:
				return Messages.get(this, "pie_desc");
			case WINTER_HOLIDAYS:
				return Messages.get(this, "cane_desc");
		}
	}
	
	@Override
	public int value() {
		return 20 * quantity;
	}
}
