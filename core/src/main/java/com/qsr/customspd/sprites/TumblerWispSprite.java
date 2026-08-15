/*
 * Lutherverse -- original content (scaffolded, then filled in).
 * GPLv3; see the project license.
 */
package com.qsr.customspd.sprites;

import com.qsr.customspd.assets.Asset;
import com.qsr.customspd.assets.GeneralAsset;
import com.qsr.customspd.modding.SpriteSizeConfig;
import com.watabou.noosa.TextureFilm;

import java.util.List;

/**
 * TumblerWisp sprite. Frame sizes come from pack config (SpriteSizeConfig), matching every
 * other mob sprite — a mod pack can reskin it with a different frame size. The stock art
 * is a single original 16x16 frame; multi-frame animation is a later increment, so every
 * animation plays frame 0.
 */
public class TumblerWispSprite extends MobSprite {
	public TumblerWispSprite() {
		super();
		texture(Asset.getAssetFilePath(GeneralAsset.TUMBLER_WISP));

		List<Integer> frameSizes = SpriteSizeConfig.getSizes(GeneralAsset.TUMBLER_WISP);
		int frameWidth = frameSizes.get(0);
		int frameHeight = frameSizes.get(1);

		TextureFilm frames = new TextureFilm(texture, frameWidth, frameHeight);
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
