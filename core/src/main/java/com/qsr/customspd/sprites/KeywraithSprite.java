/*
 * Lutherverse -- scaffolded content stub.
 * GPLv3; see the project license. Replace the TODOs with the real mechanic + art.
 */
package com.qsr.customspd.sprites;

import com.qsr.customspd.assets.Asset;
import com.qsr.customspd.assets.GeneralAsset;
import com.watabou.noosa.TextureFilm;

public class KeywraithSprite extends MobSprite {
	public KeywraithSprite() {
		super();
		texture(Asset.getAssetFilePath(GeneralAsset.KEYWRAITH));
		// TODO: real frames + art. The placeholder is a single 16x16 frame.
		TextureFilm frames = new TextureFilm(texture, 16, 16);
		idle = new Animation(1, true);
		idle.frames(frames, 0);
		run = new Animation(1, true);
		run.frames(frames, 0);
		attack = new Animation(1, false);
		attack.frames(frames, 0);
		die = new Animation(1, false);
		die.frames(frames, 0);
		play(idle);
	}
}
