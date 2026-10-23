package com.qsr.customspd;

import com.qsr.customspd.test.HeadlessGdx;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;
import com.watabou.utils.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Dungeon.initialVersion is the version a run STARTED on (distinct from {@code version}, the
 * version that last wrote the save). Runs the real {@code saveGame} -> file -> {@code loadGame}
 * path: loadGame once read the current-version key into initialVersion, so every reload
 * silently rewrote a run's start version to whatever build loaded it.
 */
class DungeonInitialVersionTest {

	private static final int SLOT = 1;

	private int prevInitialVersion;
	private int prevVersion;
	private int prevVersionCode;
	private com.qsr.customspd.actors.hero.Hero prevHero;
	private String[] prevMods, prevVisited, prevPos, prevSou, prevAs;
	private String prevGhost, prevWandmaker, prevBlacksmith, prevImp, prevLevelName;
	private java.util.HashSet<Integer> prevChapters;
	private java.util.HashMap<String, java.util.ArrayList<com.qsr.customspd.items.Item>> prevDropped;

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@BeforeEach
	void saveStatics() {
		prevInitialVersion = Dungeon.initialVersion;
		prevVersion = Dungeon.version;
		prevVersionCode = Game.versionCode;
		prevHero = Dungeon.hero;
		prevMods = Dungeon.gameplayMods;
		prevVisited = Dungeon.visited;
		prevPos = Dungeon.posLevels;
		prevSou = Dungeon.souLevels;
		prevAs = Dungeon.asLevels;
		prevGhost = Dungeon.ghostLevel;
		prevWandmaker = Dungeon.wandmakerLevel;
		prevBlacksmith = Dungeon.blacksmithLevel;
		prevImp = Dungeon.impLevel;
		prevLevelName = Dungeon.levelName;
		prevChapters = Dungeon.chapters;
		prevDropped = Dungeon.droppedItems;

		// saveGame bundles these unconditionally; a fresh JVM has them null until Dungeon.init.
		Dungeon.gameplayMods = new String[0];
		Dungeon.visited = new String[0];
		Dungeon.posLevels = new String[0];
		Dungeon.souLevels = new String[0];
		Dungeon.asLevels = new String[0];
		Dungeon.ghostLevel = "";
		Dungeon.wandmakerLevel = "";
		Dungeon.blacksmithLevel = "";
		Dungeon.impLevel = "";
		Dungeon.levelName = "";
		Dungeon.chapters = new java.util.HashSet<>();
		Dungeon.droppedItems = new java.util.HashMap<>();
		Dungeon.hero = null;
		com.qsr.customspd.Statistics.reset();
		com.qsr.customspd.journal.Notes.reset();
	}

	@AfterEach
	void tearDown() {
		Dungeon.initialVersion = prevInitialVersion;
		Dungeon.version = prevVersion;
		Game.versionCode = prevVersionCode;
		Dungeon.hero = prevHero;
		Dungeon.gameplayMods = prevMods;
		Dungeon.visited = prevVisited;
		Dungeon.posLevels = prevPos;
		Dungeon.souLevels = prevSou;
		Dungeon.asLevels = prevAs;
		Dungeon.ghostLevel = prevGhost;
		Dungeon.wandmakerLevel = prevWandmaker;
		Dungeon.blacksmithLevel = prevBlacksmith;
		Dungeon.impLevel = prevImp;
		Dungeon.levelName = prevLevelName;
		Dungeon.chapters = prevChapters;
		Dungeon.droppedItems = prevDropped;
		FileUtils.deleteFile(GamesInProgress.gameFile(SLOT));
	}

	@Test
	void initialVersionSurvivesSaveLoadDistinctFromCurrentVersion() {
		Game.versionCode = 900;     // the build doing the save
		Dungeon.initialVersion = 700; // the build the run started on

		Dungeon.saveGame(SLOT);

		Dungeon.initialVersion = -1;
		Dungeon.version = -1;
		loadGameFromSlot();

		assertEquals(700, Dungeon.initialVersion,
				"initialVersion must be restored from what was stored, not the current version");
		assertEquals(900, Dungeon.version, "version is the build that last saved");
		assertNotEquals(Dungeon.version, Dungeon.initialVersion);
	}

	@Test
	void saveWithoutInitVerFallsBackToVersion() {
		Game.versionCode = 900;
		Dungeon.initialVersion = 700;
		Dungeon.saveGame(SLOT);

		// Emulate a pre-INIT_VER save: the key simply is not there.
		try {
			Bundle bundle = FileUtils.bundleFromFile(GamesInProgress.gameFile(SLOT));
			bundle.remove("init_ver");
			FileUtils.bundleToFile(GamesInProgress.gameFile(SLOT), bundle);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}

		Dungeon.initialVersion = -1;
		loadGameFromSlot();

		assertEquals(900, Dungeon.initialVersion,
				"an old save with no init_ver must treat its own version as the start version");
	}

	private static void loadGameFromSlot() {
		try {
			Dungeon.loadGame(SLOT, false);
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}
}
