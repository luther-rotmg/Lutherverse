package com.qsr.customspd.test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.qsr.customspd.Badges;
import com.qsr.customspd.items.potions.Potion;
import com.qsr.customspd.items.rings.Ring;
import com.qsr.customspd.items.scrolls.Scroll;
import com.watabou.utils.FileUtils;
import com.watabou.utils.Random;

import java.io.File;

/**
 * F1 headless test harness. Boots a libGDX HeadlessApplication once per JVM so tests can run
 * real game code (asset loading now; hero/combat next) without a display.
 *
 * Scope + limits:
 *  - {@code Gdx.gl} is null and there is no GL context — anything touching textures/rendering
 *    will NPE. This harness covers file / data / logic, NOT rendering.
 *  - Base assets resolve via {@code Gdx.files.internal(...)}, which HeadlessFiles reads relative
 *    to the process working directory; the core:test task sets that to {@code src/main/assets}.
 *  - External/local file ops (ModManager's enabled-mods scan) are pointed at a throwaway temp
 *    dir via {@link FileUtils#setDefaultFileProperties}.
 */
public final class HeadlessGdx {

	private static boolean booted = false;

	public static synchronized void boot() {
		if (booted) return;

		HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
		// The application listener does nothing; we only need Gdx.app/files initialised.
		new HeadlessApplication(new ApplicationAdapter() {}, config);
		if (Gdx.files == null) {
			throw new IllegalStateException("HeadlessApplication did not initialise Gdx.files");
		}

		try {
			File tmp = java.nio.file.Files.createTempDirectory("cpdu-headless-test").toFile();
			tmp.deleteOnExit();
			FileUtils.setDefaultFileProperties(Files.FileType.Absolute, tmp.getAbsolutePath() + File.separator);
		} catch (Exception e) {
			throw new RuntimeException("could not set up temp storage for the headless test harness", e);
		}

		// General game state many runtime paths touch (e.g. item.identify() -> Catalog -> Badges).
		// With no save file in the temp dir, this initialises an empty global badge set.
		Badges.loadGlobal();

		// Item appearance handlers (Scroll/Potion/Ring) that item.identify() consults. The game
		// initialises these at run start inside a seeded RNG block; do the same for determinism.
		Random.pushGenerator(1L);
		try {
			Scroll.initLabels();
			Potion.initColors();
			Ring.initGems();
		} finally {
			Random.popGenerator();
		}

		booted = true;
	}

	private HeadlessGdx() {}
}
