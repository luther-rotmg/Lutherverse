package com.qsr.customspd;

import com.qsr.customspd.test.HeadlessRun;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;
import com.watabou.utils.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Dungeon.initialVersion is the version a run STARTED on (distinct from {@code version}, the
 * version that last wrote the save). Runs the real {@code saveGame} -> file -> {@code loadGame}
 * path on a real {@link HeadlessRun}: loadGame once read the current-version key into
 * initialVersion, so every reload silently rewrote a run's start version to whatever build
 * loaded it. HeadlessRun owns all global-state setup and restore.
 */
class DungeonInitialVersionTest {

	private static final long SEED = 7007L;

	@Test
	void initialVersionSurvivesSaveLoadDistinctFromCurrentVersion() {
		try (HeadlessRun run = HeadlessRun.start(SEED)) {
			Game.versionCode = 900;     // the build doing the save
			Dungeon.initialVersion = 700; // the build the run started on

			run.saveGame();

			Dungeon.initialVersion = -1;
			Dungeon.version = -1;
			run.loadGame();

			assertEquals(700, Dungeon.initialVersion,
					"initialVersion must be restored from what was stored, not the current version");
			assertEquals(900, Dungeon.version, "version is the build that last saved");
			assertNotEquals(Dungeon.version, Dungeon.initialVersion);
		}
	}

	@Test
	void saveWithoutInitVerFallsBackToVersion() throws IOException {
		try (HeadlessRun run = HeadlessRun.start(SEED)) {
			Game.versionCode = 900;
			Dungeon.initialVersion = 700;
			run.saveGame();

			// Emulate a pre-INIT_VER save: the key simply is not there.
			Bundle bundle = FileUtils.bundleFromFile(GamesInProgress.gameFile(HeadlessRun.SLOT));
			bundle.remove("init_ver");
			FileUtils.bundleToFile(GamesInProgress.gameFile(HeadlessRun.SLOT), bundle);

			Dungeon.initialVersion = -1;
			run.loadGame();

			assertEquals(900, Dungeon.initialVersion,
					"an old save with no init_ver must treat its own version as the start version");
		}
	}

	@Test
	void initialVersionSurvivesASaveLoadSaveCycleAcrossBuilds() throws IOException {
		try (HeadlessRun run = HeadlessRun.start(SEED)) {
			int started = Dungeon.initialVersion;
			assertEquals(HeadlessRun.VERSION_CODE, started, "init() stamps the starting build");
			run.enterFirstLevel(); // switchLevel -> saveAll, written by the starting build

			// The player updates twice, continuing the run (load -> switchLevel saves) each time.
			Game.versionCode = started + 50;
			run.restore();
			Game.versionCode = started + 60;
			run.restore();

			assertEquals(started, Dungeon.initialVersion,
					"two continues on newer builds must not rewrite the run's start version");
			assertEquals(started + 60, Dungeon.version, "version tracks the last build that saved");
			Bundle onDisk = FileUtils.bundleFromFile(GamesInProgress.gameFile(HeadlessRun.SLOT));
			assertEquals(started, onDisk.getInt("init_ver"), "the re-saved file still carries the start version");
		}
	}
}
