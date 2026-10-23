package com.qsr.customspd.test;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.GamesInProgress;
import com.qsr.customspd.SPDSettings;
import com.qsr.customspd.actors.hero.HeroClass;
import com.qsr.customspd.actors.mobs.Mob;
import com.qsr.customspd.journal.Document;
import com.qsr.customspd.levels.Level;
import com.qsr.customspd.levels.features.LevelTransition;
import com.watabou.noosa.Game;
import com.watabou.utils.FileUtils;
import com.watabou.utils.GameSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * F4 test foundation: a REAL run, headless. {@link #start(long)} runs the real
 * {@code Dungeon.init()} against the shipped dungeon layout with a fixed seed, and the other
 * methods drive the same static-state assembly {@code InterlevelScene} performs between scenes
 * (newLevel / switchLevel / saveAll / loadGame / loadLevel) without any scene or rendering.
 *
 * Usage (always try-with-resources, so global state is restored even when an assertion fails):
 * <pre>
 *   try (HeadlessRun run = HeadlessRun.start(12345L)) {
 *       Level floor1 = run.enterFirstLevel();
 *       run.saveAll();
 *       run.loadGame();
 *   }
 * </pre>
 *
 * Isolation: start() snapshots the global state of EVERY game and engine class
 * ({@link GameClasses}, via {@link StaticSnapshot}) and close() restores it exactly, to the
 * IdentificationDeckTest standard but without a hand-kept list that new statics silently escape.
 * HeadlessRunHygieneTest fingerprints the whole codebase around a full run to prove it.
 * Settings come from an in-memory {@link MemoryPreferences} (seed, no challenges, tutorial
 * done), never the developer's real prefs; saves go to slot {@link #SLOT} under F1's temp dir
 * and are deleted on close.
 */
public final class HeadlessRun implements AutoCloseable {

	/** A slot the game's UI never offers (slots are 1..MAX_SLOTS), so no real save collides. */
	public static final int SLOT = GamesInProgress.MAX_SLOTS + 1;

	/** Launchers set Game.versionCode; headless it is 0, which Level.restoreFromBundle rejects as an "old save". */
	public static final int VERSION_CODE = 900;

	public final long seed;
	private final StaticSnapshot saved;
	private boolean closed = false;

	private HeadlessRun(long seed, StaticSnapshot saved) {
		this.seed = seed;
		this.saved = saved;
	}

	/**
	 * Snapshots global state, then starts a fresh run exactly as InterlevelScene.descend() does
	 * for a new game (Mob.clearHeldAllies + Dungeon.init), as a Warrior with the given seed.
	 * The seed travels the real path: SPDSettings.customSeed -> DungeonSeed.convertFromText.
	 */
	public static HeadlessRun start(long seed) {
		HeadlessGdx.boot();
		HeadlessRun run = new HeadlessRun(seed, StaticSnapshot.capture(GameClasses.all()));
		try {
			GameSettings.set(new MemoryPreferences());
			SPDSettings.customSeed(Long.toString(seed));
			SPDSettings.challenges(0);
			// A returning player's run: tutorial done and the searching guide page found, so
			// RegularPainter's tutorial rule does not hide the entrance doors on floors 1-2.
			SPDSettings.intro(false);
			Document.ADVENTURERS_GUIDE.findPage(Document.GUIDE_SEARCHING);

			Game.versionCode = VERSION_CODE;
			GamesInProgress.curSlot = SLOT;
			GamesInProgress.selectedClass = HeroClass.WARRIOR;
			Dungeon.daily = Dungeon.dailyReplay = false;
			deleteSlotFiles();

			Mob.clearHeldAllies();
			Dungeon.init();
			if (Dungeon.seed != seed) {
				throw new IllegalStateException("seed injection failed: asked " + seed + ", got " + Dungeon.seed);
			}
		} catch (RuntimeException | Error e) {
			run.close();
			throw e;
		}
		return run;
	}

	/** InterlevelScene.descend() for a new game: generate the start level and enter it (this saves). */
	public Level enterFirstLevel() {
		Level level = Dungeon.newLevel();
		addLevelToVisited();
		Dungeon.switchLevel(level, -1);
		return level;
	}

	/**
	 * The hero takes the current level's regular exit: Hero's transition hand-off, then
	 * InterlevelScene.descend()'s existing-hero branch (hold allies, saveAll, load-or-generate the
	 * destination, switchLevel onto the matching entrance).
	 */
	public Level descend() {
		LevelTransition exit = Dungeon.level.getTransition(LevelTransition.Type.REGULAR_EXIT);
		if (exit == null || exit.type != LevelTransition.Type.REGULAR_EXIT) {
			throw new IllegalStateException("level " + Dungeon.levelName + " has no regular exit");
		}
		Level.beforeTransition();
		Mob.holdAllies(Dungeon.level);
		saveAll();

		Dungeon.depth = Dungeon.layout.getDungeon().get(exit.destLevel).getDepth();
		Dungeon.levelName = exit.destLevel;
		Level level;
		if (Arrays.asList(Dungeon.visited).contains(Dungeon.levelName)) {
			level = loadLevel();
		} else {
			level = Dungeon.newLevel();
			addLevelToVisited();
		}
		LevelTransition dest = level.getTransition(exit.destType, exit.departLevel);
		Dungeon.switchLevel(level, dest.cell());
		return level;
	}

	/**
	 * Generates a layout level directly, as if the hero arrived there without the floors before it
	 * having been generated. Real Dungeon.newLevel() levelgen; no switchLevel, nothing saved.
	 */
	public Level generate(String levelName) {
		Dungeon.levelName = levelName;
		Dungeon.depth = Dungeon.layout.getDungeon().get(levelName).getDepth();
		return Dungeon.newLevel();
	}

	public void saveAll() {
		try {
			Dungeon.saveAll();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void saveGame() {
		Dungeon.saveGame(SLOT);
	}

	public void saveLevel() {
		try {
			Dungeon.saveLevel(SLOT);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void loadGame() {
		try {
			Dungeon.loadGame(SLOT);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Level loadLevel() {
		try {
			return Dungeon.loadLevel(SLOT);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** InterlevelScene.restore(): continue a saved game (loadGame, then load the level and enter it). */
	public Level restore() {
		Mob.clearHeldAllies();
		loadGame();
		Level level;
		if (Arrays.asList(Dungeon.visited).contains(Dungeon.levelName)) {
			level = loadLevel();
		} else {
			level = Dungeon.newLevel();
			addLevelToVisited();
		}
		Dungeon.switchLevel(level, Dungeon.hero.pos);
		return level;
	}

	/** Deletes this run's save files and restores every snapshotted global. Idempotent. */
	@Override
	public void close() {
		if (closed) return;
		closed = true;
		try {
			deleteSlotFiles();
		} finally {
			saved.restore();
		}
	}

	private static void addLevelToVisited() {
		List<String> visited = new ArrayList<>(Arrays.asList(Dungeon.visited));
		visited.add(Dungeon.levelName);
		Dungeon.visited = visited.toArray(new String[0]);
	}

	private static void deleteSlotFiles() {
		String folder = GamesInProgress.gameFolder(SLOT);
		if (FileUtils.dirExists(folder)) {
			FileUtils.deleteDir(folder);
		}
	}
}
