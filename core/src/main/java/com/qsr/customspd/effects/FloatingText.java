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

package com.qsr.customspd.effects;

import com.qsr.customspd.assets.Asset;
import com.qsr.customspd.assets.GeneralAsset;
import com.qsr.customspd.scenes.GameScene;
import com.qsr.customspd.scenes.PixelScene;
import com.qsr.customspd.tiles.DungeonTilemap;
import com.qsr.customspd.ui.RenderedTextBlock;
import com.watabou.noosa.Camera;
import com.watabou.noosa.Game;
import com.watabou.noosa.Image;
import com.watabou.noosa.RenderedText;
import com.watabou.utils.Callback;
import com.watabou.utils.SparseArray;

import java.util.ArrayList;

public class FloatingText extends RenderedTextBlock {

	private static final float LIFESPAN	= 1f;
	private static final float DISTANCE	= DungeonTilemap.SIZE;

	public static final int ICON_SIZE = 7;

	public static int NO_ICON   = -1;

	//combat damage icons
	public static int PHYS_DMG          = 0;
	public static int PHYS_DMG_NO_BLOCK = 1;
	public static int MAGIC_DMG         = 2;
	public static int PICK_DMG          = 3;

	//debuff/dot damage icons
	public static int HUNGER    = 5;
	public static int BURNING   = 6;
	public static int SHOCKING  = 7;
	public static int FROST     = 8;
	public static int WATER     = 9;
	public static int BLEEDING  = 10;
	public static int TOXIC     = 11;
	public static int CORROSION = 12;
	public static int POISON    = 13;
	public static int OOZE      = 14;
	public static int DEFERRED  = 15;
	public static int CORRUPTION= 16;
	public static int AMULET    = 17;

	//positive icons
	public static int HEALING   = 18;
	public static int SHIELDING = 19;
	public static int EXPERIENCE= 20;
	public static int STRENGTH  = 21;

	//currency icons
	public static int GOLD      = 23;
	public static int ENERGY    = 24;

	//CPDU divergence: upstream slices one text_icons.png sheet at runtime with a TextureFilm
	//indexed by the constants above. CPDU ships one PNG per icon (effects/text_icons/), so the
	//indices map to GeneralAsset entries instead (cpdu-cxr).
	private static final SparseArray<GeneralAsset> ICON_ASSETS = new SparseArray<>();
	static {
		ICON_ASSETS.put(PHYS_DMG,          GeneralAsset.TEXT_ICON_PHYS_DMG);
		ICON_ASSETS.put(PHYS_DMG_NO_BLOCK, GeneralAsset.TEXT_ICON_PHYS_DMG_NO_BLOCK);
		ICON_ASSETS.put(MAGIC_DMG,         GeneralAsset.TEXT_ICON_MAGIC_DMG);
		ICON_ASSETS.put(PICK_DMG,          GeneralAsset.TEXT_ICON_PICK_DMG);

		ICON_ASSETS.put(HUNGER,            GeneralAsset.TEXT_ICON_HUNGER);
		ICON_ASSETS.put(BURNING,           GeneralAsset.TEXT_ICON_BURNING);
		ICON_ASSETS.put(SHOCKING,          GeneralAsset.TEXT_ICON_SHOCKING);
		ICON_ASSETS.put(FROST,             GeneralAsset.TEXT_ICON_FROST);
		ICON_ASSETS.put(WATER,             GeneralAsset.TEXT_ICON_WATER);
		ICON_ASSETS.put(BLEEDING,          GeneralAsset.TEXT_ICON_BLEEDING);
		ICON_ASSETS.put(TOXIC,             GeneralAsset.TEXT_ICON_TOXIC);
		ICON_ASSETS.put(CORROSION,         GeneralAsset.TEXT_ICON_CORROSION);
		ICON_ASSETS.put(POISON,            GeneralAsset.TEXT_ICON_POISON);
		ICON_ASSETS.put(OOZE,              GeneralAsset.TEXT_ICON_OOZE);
		ICON_ASSETS.put(DEFERRED,          GeneralAsset.TEXT_ICON_DEFERRED);
		ICON_ASSETS.put(CORRUPTION,        GeneralAsset.TEXT_ICON_CORRUPTION);
		ICON_ASSETS.put(AMULET,            GeneralAsset.TEXT_ICON_AMULET);

		ICON_ASSETS.put(HEALING,           GeneralAsset.TEXT_ICON_HEALING);
		ICON_ASSETS.put(SHIELDING,         GeneralAsset.TEXT_ICON_SHIELDING);
		ICON_ASSETS.put(EXPERIENCE,        GeneralAsset.TEXT_ICON_EXPERIENCE);
		ICON_ASSETS.put(STRENGTH,          GeneralAsset.TEXT_ICON_STRENGTH);

		ICON_ASSETS.put(GOLD,              GeneralAsset.TEXT_ICON_GOLD);
		ICON_ASSETS.put(ENERGY,            GeneralAsset.TEXT_ICON_ENERGY);
	}

	public static GeneralAsset iconAsset( int iconIdx ) {
		return ICON_ASSETS.get(iconIdx);
	}

	private Image icon;
	private boolean iconLeft;

	private float timeLeft;
	
	private int key = -1;

	private static final SparseArray<ArrayList<FloatingText>> stacks = new SparseArray<>();
	
	public FloatingText() {
		super(9*PixelScene.defaultZoom);
		setHightlighting(false);
	}
	
	@Override
	public void update() {
		super.update();
		
		if (timeLeft >= 0) {
			if ((timeLeft -= Game.elapsed) <= 0) {
				kill();
			} else {
				float p = timeLeft / LIFESPAN;
				alpha( p > 0.5f ? 1 : p * 2 );
				
				float yMove = (DISTANCE / LIFESPAN) * Game.elapsed;
				y -= yMove;
				for (RenderedText t : words){
					t.y -= yMove;
				}

				if (icon != null){
					icon.alpha(p > 0.5f ? 1 : p * 2);
					icon.y -= yMove;
				}
			}
		}
	}

	@Override
	protected synchronized void layout() {
		super.layout();
		if (icon != null){
			if (iconLeft){
				icon.x = left();
			} else {
				icon.x = left() + width() - icon.width();
			}
			icon.y = top();
			PixelScene.align(icon);
		}
	}

	@Override
	public float width() {
		float width = super.width();
		if (icon != null){
			width += icon.width()-0.5f;
		}
		return width;
	}

	@Override
	public void kill() {
		if (key != -1) {
			synchronized (stacks) {
				stacks.get(key).remove(this);
			}
			key = -1;
		}
		super.kill();
	}
	
	@Override
	public void destroy() {
		kill();
		super.destroy();
	}
	
	public void reset( float x, float y, String text, int color, int iconIdx, boolean left ) {
		
		revive();
		
		zoom( 1 / (float)PixelScene.defaultZoom );

		text( text );
		hardlight( color );

		if (iconIdx != NO_ICON){
			icon = new Image( Asset.getAssetFilePath(iconAsset(iconIdx)) );
			add(icon);
			iconLeft = left;
			if (iconLeft){
				align(RIGHT_ALIGN);
			}
		} else {
			icon = null;
		}

		setPos(
			PixelScene.align( Camera.main, x - width() / 2),
			PixelScene.align( Camera.main, y - height())
		);
		
		timeLeft = LIFESPAN;
	}
	
	/* STATIC METHODS */

	public static void show( float x, float y, String text, int color) {
		show(x, y, -1, text, color, -1, false);
	}
	
	public static void show( float x, float y, int key, String text, int color) {
		show(x, y, key, text, color, -1, false);
	}
	
	public static void show( float x, float y, int key, String text, int color, int iconIdx, boolean left ) {
		Game.runOnRenderThread(new Callback() {
			@Override
			public void call() {
				FloatingText txt = GameScene.status();
				if (txt != null){
					txt.reset(x, y, text, color, iconIdx, left);
					if (key != -1) push(txt, key);
				}
			}
		});
	}
	
	private static void push( FloatingText txt, int key ) {
		
		synchronized (stacks) {
			txt.key = key;
			
			ArrayList<FloatingText> stack = stacks.get(key);
			if (stack == null) {
				stack = new ArrayList<>();
				stacks.put(key, stack);
			}
			
			if (stack.size() > 0) {
				FloatingText below = txt;
				int aboveIndex = stack.size() - 1;
				int numBelow = 0;
				while (aboveIndex >= 0) {
					numBelow++;
					FloatingText above = stack.get(aboveIndex);
					if (above.bottom() + 4 > below.top()) {
						above.setPos(above.left(), below.top() - above.height() - 4);

						//reduce remaining time on texts being nudged up, to prevent spam
						above.timeLeft = Math.min(above.timeLeft, LIFESPAN-(numBelow/5f));
						above.timeLeft = Math.max(above.timeLeft, 0);

						below = above;
						aboveIndex--;
					} else {
						break;
					}
				}
			}
			
			stack.add(txt);
		}
	}
}
