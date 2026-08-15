package com.qsr.customspd.modding;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.qsr.customspd.actors.mobs.Elemental;
import com.qsr.customspd.actors.mobs.Mob;
import com.qsr.customspd.actors.mobs.Shaman;
import com.qsr.customspd.items.Item;
import com.qsr.customspd.levels.Level;
import com.qsr.customspd.test.HeadlessGdx;
import com.watabou.noosa.Game;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F1 modding/JSON registration sweep. CPDU registers content data-driven: dungeon.json names
 * mobs and level layouts as bare strings that the game resolves at runtime via reflection
 * (Bestiary.standardMobRotation, Dungeon's level factory), and heroes/*.json names starting-kit
 * items the same way (HeroClass.setUp). The static content-audit tool's M3/I3 heuristics cannot
 * see these registrations (they scan source tokens, not JSON) — this suite closes that gap
 * honestly by proving every shipped token actually resolves through the SAME runtime paths the
 * game uses. A token typo'd in JSON, or a renamed/deleted class still referenced by JSON, fails
 * here with the token and location named.
 *
 * Resolution semantics mirrored exactly:
 *  - Bestiary tokens (Bestiary.kt standardMobRotation): customMobExists(token) -> CustomMob;
 *    "Elemental" -> Elemental.random(); "Shaman" -> Shaman.random();
 *    else Reflection.forName("com.qsr.customspd.actors.mobs." + token).
 *  - rare_mob tokens (Bestiary.kt addRareMobs): reflection ONLY — no custom-mob or
 *    Elemental/Shaman special-casing. A rare_mob of "Elemental" would crash at runtime,
 *    so the sweep must not paper over it.
 *  - REGULAR level layouts (Dungeon.newLevel):
 *    Reflection.newInstance(Reflection.forName("com.qsr.customspd.levels." + layout)).
 *  - Hero kit items (HeroClass.setUp):
 *    Reflection.forName("com.qsr.customspd.items." + type), with weapon.missiles.darts.TippedDart
 *    special-cased (TippedDart is abstract; setUp builds it via TippedDart.randomTipped instead).
 */
class RegistrationSweepTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
		// Every real launcher (DesktopLauncher/AndroidLauncher) sets Game.version before any
		// game class loads; headless it is null, and RegularLevel's static init transitively
		// reaches Document -> DeviceCompat.isDebug() -> Game.version.contains(...), which NPEs.
		// Mirror the launcher with a non-INDEV version (Document inits in release mode). Left
		// set afterwards on purpose: class-init side effects are sticky JVM-wide anyway, and no
		// existing test reads Game.version (it was null-crash territory before this).
		if (Game.version == null) {
			Game.version = "0.0.0-headless-test";
		}
	}

	/**
	 * Mirrors the token-to-class mapping of Bestiary.standardMobRotation. Returns null when the
	 * token resolves to a spawnable mob, or a human-readable failure reason otherwise.
	 * Caller must have a seeded RNG pushed: Elemental.random()/Shaman.random() consume Random.
	 */
	private static String mobResolutionFailure(String token) {
		if (JsonConfigRetriever.INSTANCE.customMobExists(token)) {
			return null; // the game maps this token to CustomMob backed by mobs/<token>.json
		}
		Class<?> cls;
		if (token.equals("Elemental")) {
			cls = Elemental.random();
		} else if (token.equals("Shaman")) {
			cls = Shaman.random();
		} else {
			cls = Reflection.forName("com.qsr.customspd.actors.mobs." + token);
		}
		if (cls == null) {
			return "no class com.qsr.customspd.actors.mobs." + token + " and no custom mob json";
		}
		if (!Mob.class.isAssignableFrom(cls)) {
			return "resolves to " + cls.getName() + ", which is not a Mob";
		}
		if (Reflection.newInstance(cls) == null) {
			return cls.getName() + " is not constructible (Reflection.newInstance returned null)";
		}
		return null;
	}

	@Test
	void everyBestiaryTokenInTheShippedDungeonResolvesToASpawnableMob() {
		DungeonLayout layout = JsonConfigRetriever.INSTANCE.retrieveDungeonLayout();
		assertFalse(layout.getDungeon().isEmpty(), "the shipped dungeon.json must define levels");

		Set<String> seen = new TreeSet<>();
		Random.pushGenerator(20260814L);
		try {
			for (Map.Entry<String, LevelScheme> e : layout.getDungeon().entrySet()) {
				for (String token : e.getValue().getBestiary()) {
					seen.add(token);
					String failure = mobResolutionFailure(token);
					assertNull(failure, "bestiary token '" + token + "' on level '" + e.getKey()
							+ "' (depth " + e.getValue().getDepth() + "): " + failure);
				}
			}
		} finally {
			Random.popGenerator();
		}

		// Anti-vacuity guard: the CPDU mobs registered ONLY through dungeon.json (invisible to
		// the content-audit source heuristic) must actually have been visited by this sweep.
		// If they vanish from the shipped layout, the sweep silently stops covering them —
		// this fails instead.
		for (String dataDriven : Arrays.asList(
				"Keywraith", "TumblerWisp", "HexwardMoth", "WardstoneSentinel", "GaolShade")) {
			assertTrue(seen.contains(dataDriven),
					"the shipped dungeon.json no longer registers '" + dataDriven
							+ "' in any bestiary — the sweep would go vacuous for it");
		}
	}

	@Test
	void everyRareMobTokenInTheShippedDungeonResolvesViaReflectionAlone() {
		DungeonLayout layout = JsonConfigRetriever.INSTANCE.retrieveDungeonLayout();

		int rareMobs = 0;
		Random.pushGenerator(20260814L);
		try {
			for (Map.Entry<String, LevelScheme> e : layout.getDungeon().entrySet()) {
				String token = e.getValue().getRareMob();
				if (token == null) continue;
				rareMobs++;
				// Bestiary.addRareMobs resolves rare_mob by reflection only — no custom-mob
				// lookup, no Elemental/Shaman special cases — so the sweep must be as strict.
				Class<?> cls = Reflection.forName("com.qsr.customspd.actors.mobs." + token);
				assertNotNull(cls, "rare_mob token '" + token + "' on level '" + e.getKey()
						+ "' (depth " + e.getValue().getDepth() + ") does not resolve to a class");
				assertTrue(Mob.class.isAssignableFrom(cls),
						"rare_mob token '" + token + "' on level '" + e.getKey()
								+ "' resolves to " + cls.getName() + ", which is not a Mob");
				assertNotNull(Reflection.newInstance(cls),
						"rare_mob token '" + token + "' on level '" + e.getKey()
								+ "' is not constructible");
			}
		} finally {
			Random.popGenerator();
		}
		assertTrue(rareMobs > 0,
				"the shipped dungeon.json defines rare mobs; a sweep that visits none is vacuous");
	}

	@Test
	void everyShippedLevelEntryResolvesToAConstructibleLevel() {
		DungeonLayout layout = JsonConfigRetriever.INSTANCE.retrieveDungeonLayout();

		// Dungeon.init dereferences the start entry immediately; a dangling start key NPEs
		// before the first level even generates.
		assertTrue(layout.getDungeon().containsKey(layout.getStart()),
				"dungeon.json 'start' (\"" + layout.getStart() + "\") must name a defined level");

		int regulars = 0;
		Random.pushGenerator(20260814L);
		try {
			for (Map.Entry<String, LevelScheme> e : layout.getDungeon().entrySet()) {
				LevelScheme scheme = e.getValue();
				if (scheme.getType() == LevelType.REGULAR) {
					regulars++;
					// Dungeon.newLevel: Reflection.newInstance(forName("com.qsr.customspd.levels." + layout))
					assertNotNull(scheme.getLayout(),
							"REGULAR level '" + e.getKey() + "' must declare a layout token");
					Class<?> cls = Reflection.forName("com.qsr.customspd.levels." + scheme.getLayout());
					assertNotNull(cls, "layout token '" + scheme.getLayout() + "' on level '"
							+ e.getKey() + "' (depth " + scheme.getDepth()
							+ ") does not resolve under com.qsr.customspd.levels");
					assertTrue(Level.class.isAssignableFrom(cls),
							"layout token '" + scheme.getLayout() + "' on level '" + e.getKey()
									+ "' resolves to " + cls.getName() + ", which is not a Level");
					assertNotNull(Reflection.newInstance(cls),
							"layout token '" + scheme.getLayout() + "' on level '" + e.getKey()
									+ "' is not constructible headless");
				} else {
					// Dungeon.newLevel feeds custom_layout straight into new CustomLevel(...).
					assertNotNull(scheme.getCustomLayout(),
							"CUSTOM level '" + e.getKey() + "' must embed a custom_layout");
				}
			}
		} finally {
			Random.popGenerator();
		}
		assertTrue(regulars > 0, "the shipped dungeon.json is all-regular; visiting none is vacuous");
	}

	@Test
	void everyShippedHeroKitItemTokenResolvesToAConstructibleItem() {
		FileHandle heroesDir = Gdx.files.internal("heroes");
		assertTrue(heroesDir.exists() && heroesDir.isDirectory(),
				"the shipped heroes/ asset directory must be listable headless");
		FileHandle[] files = heroesDir.list(".json");

		// Anti-vacuity guard: the listing must actually see the shipped roster (a wrong CWD or
		// asset path would list nothing and the loop below would pass while checking nothing).
		Set<String> names = new LinkedHashSet<>();
		for (FileHandle f : files) {
			names.add(f.nameWithoutExtension());
		}
		for (String required : Arrays.asList(
				"any", "warrior", "mage", "rogue", "huntress", "duelist", "keybearer")) {
			assertTrue(names.contains(required),
					"heroes/ listing must include the shipped config '" + required + ".json'");
		}

		Random.pushGenerator(20260814L);
		try {
			for (String hero : names) {
				HeroConfig config = JsonConfigRetriever.INSTANCE.retrieveHeroConfig(hero);
				assertNotNull(config, "heroes/" + hero + ".json must parse into a HeroConfig");

				List<String> tokens = new ArrayList<>();
				collectItemTokens(config.getWeapon(), tokens);
				collectItemTokens(config.getArmor(), tokens);
				collectItemTokens(config.getArtifact(), tokens);
				collectItemTokens(config.getMisc(), tokens);
				collectItemTokens(config.getRing(), tokens);
				for (ItemDescription desc : config.getItems()) {
					collectItemTokens(desc, tokens);
				}
				tokens.addAll(config.getIdentified());
				assertFalse(tokens.isEmpty(),
						"heroes/" + hero + ".json defines no item tokens at all — sweep is vacuous for it");

				for (String token : tokens) {
					if (token.equals("weapon.missiles.darts.TippedDart")) {
						// HeroClass.setUp special-cases this token (TippedDart is abstract and
						// built via TippedDart.randomTipped, never reflected), so the reflection
						// check below would be dishonest for it. No shipped config uses it today.
						continue;
					}
					Class<?> cls = Reflection.forName("com.qsr.customspd.items." + token);
					assertNotNull(cls, "hero '" + hero + "' item token '" + token
							+ "' does not resolve under com.qsr.customspd.items");
					assertTrue(Item.class.isAssignableFrom(cls),
							"hero '" + hero + "' item token '" + token + "' resolves to "
									+ cls.getName() + ", which is not an Item");
					assertNotNull(Reflection.newInstance(cls),
							"hero '" + hero + "' item token '" + token + "' is not constructible");
				}
			}
		} finally {
			Random.popGenerator();
		}
	}

	private static void collectItemTokens(ItemDescription desc, List<String> into) {
		if (desc == null) return;
		into.add(desc.getType());
		// core_wand resolves through the same "com.qsr.customspd.items." prefix in setUp.
		if (desc.getCoreWand() != null) into.add(desc.getCoreWand());
	}

	@Test
	void bogusTokensDoNotResolve_theSweepCanFail() {
		// Negative canary: every resolution predicate the sweeps above rely on must be able to
		// say NO. If any of these started resolving, the whole suite would be meaningless.
		assertFalse(JsonConfigRetriever.INSTANCE.customMobExists("DefinitelyNotARealMob"),
				"a bogus token must not read as a defined custom mob");
		assertNull(Reflection.forName("com.qsr.customspd.actors.mobs.DefinitelyNotARealMob"),
				"a bogus mob token must not resolve to a class");
		assertNull(Reflection.forName("com.qsr.customspd.levels.DefinitelyNotARealLevel"),
				"a bogus layout token must not resolve to a class");
		assertNull(Reflection.forName("com.qsr.customspd.items.DefinitelyNotARealItem"),
				"a bogus item token must not resolve to a class");

		Random.pushGenerator(20260814L);
		try {
			assertNotNull(mobResolutionFailure("DefinitelyNotARealMob"),
					"the bestiary sweep's own helper must reject a bogus token");
		} finally {
			Random.popGenerator();
		}
	}
}
