<p align="center">
  <img src="docs/assets/lutherverse-title.svg" alt="Lutherverse title card" width="820">
</p>

<h1 align="center">Lutherverse</h1>

<p align="center">
  <em>A 200-floor roguelike descent that keeps changing shape on you.</em>
</p>

<p align="center">
  <a href="LICENSE.txt"><img alt="License: GPL v3" src="https://img.shields.io/badge/license-GPL--3.0-blueviolet.svg"></a>
  <img alt="Status: pre-alpha" src="https://img.shields.io/badge/status-pre--alpha-orange.svg">
  <img alt="Platforms: Android and Desktop" src="https://img.shields.io/badge/platforms-android%20%C2%B7%20desktop-lightgrey.svg">
  <img alt="Base: SPD v3.3.8" src="https://img.shields.io/badge/base-SPD%20v3.3.8-informational.svg">
  <img alt="Java: 17+" src="https://img.shields.io/badge/java-17%2B-red.svg">
  <a href="https://github.com/luther-rotmg/CustomPixelDungeonUltimate/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/luther-rotmg/CustomPixelDungeonUltimate?style=social"></a>
  <a href="https://github.com/luther-rotmg/CustomPixelDungeonUltimate/watchers"><img alt="GitHub watchers" src="https://img.shields.io/github/watchers/luther-rotmg/CustomPixelDungeonUltimate?style=social"></a>
</p>

---

## What is this

Lutherverse (repo name: `CustomPixelDungeonUltimate`) is a fork of [Custom Pixel Dungeon](https://github.com/QuasiStellar/custom-pixel-dungeon), which is itself a fork of [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon). It uses CPD's marketplace-mod framework and extends the game on top of it. I want it to be the kind of project a small group of people obsess over for a long time.

Shattered Pixel Dungeon is about 25 floors of tight, replayable dungeon crawling. Lutherverse stretches that into a 200-floor run that keeps shifting tone as it goes: a hyper-utopian future with something wrong underneath, a version of Zanarkand you weren't supposed to see, calm plains that stop feeling calm. There's a story running through the whole thing, cameos of characters you'll recognize, keyblades as their own weapon type, save zones between big fights, towns every ten floors, a sphere-grid progression system, and coop you can play with a friend on their phone.

The mood is cosmic horror. Junji Ito and HP Lovecraft on the aesthetic side. Bloodborne, Final Fantasy X, and Kingdom Hearts 2 on the game-design side. Written in Java on libGDX, all GPLv3.

---

## The vision

<details open>
  <summary><strong>Core gameplay</strong></summary>

- 200 floors, about eight times what Shattered Pixel Dungeon runs by default, with new content the whole way up.
- A sphere-grid skill system inspired by Final Fantasy X. Nodes unlock along paths and either replace or wrap Shattered's talent tree depending on how the refactor lands.
- Combat is toggleable per run: the classic real-time roguelike mode Shattered ships, or a Final Fantasy X style turn-based action queue.
- Save zones borrowed from Kingdom Hearts 2. Checkpoint rooms where you can rest. Two per-run toggles: whether they heal you, and whether they appear at all (that second one is the hardcore mode).
- A town every ten floors for buying, selling, trading, crafting, resting, and quests. One city recurs for about thirty floors at a time and evolves as the player's choices reach back into it: different quest steps, different inventories, different events.
- Keyblades as a real weapon type, not a subclass of swords. A dual-wield reward system feeds into a Keybearer hero class built around dual keyblades. Endgame variants are crafted from orichalcum.

</details>

<details>
  <summary><strong>Aesthetic direction</strong></summary>

- Cosmic horror: Junji Ito (Uzumaki spirals, Tomie's designs) and HP Lovecraft (existential dread, deep-ones, mythos creatures).
- Bloodborne: chalice-dungeon-style procedural mini-dungeons, a fountain-shaped shop, gothic ambient horror.
- Final Fantasy X: Zanarkand, the Calm Lands, Sin as a recurring apocalyptic threat, Aeons as summonable creatures, the sphere grid.
- Kingdom Hearts 2: world-hopping between tonally distinct settings, keyblades, the save-zone save/load pattern.
- Cameos: Sora (KH), the Doll (Bloodborne), Patches (FromSoft), Tidus and O'aka (FFX), plus lighter nods to GTA, Warframe, Naruto, DBZ, and Binding of Isaac.
- Each stretch of floors feels distinct. The dungeon changes shape as it deepens.

</details>

<details>
  <summary><strong>Multiplayer</strong></summary>

Two coop modes for two kinds of session:

- Real-time coop. Pick a nickname, make a room, no self-hosted servers. The lobby toggles which addons count as "cheats" and can be turned off (God-tier starter gear counts; Hard mode and Bonfire mode don't).
- Turn-based coop inspired by Nestalgia. A four-unit party split across the players (one player controls all four, two players get two each, four players get one). Meant for async play with a friend on their phone. Pairs with the turn-based combat toggle above.

</details>

<details>
  <summary><strong>Community goodies</strong></summary>

- Leaderboards.
- Labeled seed sharing: `seed COSMICNIGHTMARE-1` instead of `seed 12345`.
- A story running through all 200 floors, with cutscenes and dialogue.
- A modding-platform API so other people can add biomes, mobs, weapons, and story branches.

</details>

---

## Currently working on

Short bulletin. Full detail in [project_progress.md](project_progress.md).

- 🔴 **P0 — seeded runs aren't actually seeded.** Guaranteed item and quest-NPC floors are computed before the dungeon seed exists, using an unseeded RNG. Same seed, two runs, different dungeon composition. Blocks seed sharing, daily runs and coop.
- 🟡 **Seeded-determinism test harness** for `core`, which the P0 fix should land on top of rather than before.
- 🟡 **Sub-B Slice 1 remainder** — 22 batches (~230 commits) and 27 ready beads, now with syntax-aware merge tooling in place.
- 🟡 **CI to first green** — the workflow exists and runs; shaking out Windows→Linux differences.
- ⏳ **`port-verify`** — prove a ported upstream commit kept every hunk, which nothing currently checks.

---

## Completed milestones

<details>
  <summary><strong>Verification infrastructure — 2026-08-10</strong></summary>

The project's gates were passing without checking anything. All of these now ship with a
negative control proving they can fail.

- **`deletion-audit`** — catches removals the API-surface diff structurally cannot see: private members, and statements dropped from a method whose signature never changed. First run: 1,021 files, 43 findings, 27 triaged as legitimate.
- **`desktop-smoke`** — boots the real jar to 120 rendered frames. The first automated proof that the game starts. Replaces an Android emulator smoke that had never once executed the APK.
- **`manifest-audit`** — seven checks over the manifests defining Slice 1's entire scope.
- **Android Lint** wired as a ratcheted gate over `core` and `android`. It ships inside AGP and had never been run.
- **`options.release = 8`** — constrains the Java 8 modules to the Java 8 *API*, not just the language level.
- **First tests in `core`** — `core:test` was `NO-SOURCE` across 1019 Java and 49 Kotlin files.
- **CI**, where the repo previously had none. Every step asserts it analysed something.

</details>

<details>
  <summary><strong>Sub-B Slice 1 — engine and tuning catchup (partial)</strong></summary>

- Tasks 11–16 complete: Actor, Char, Hero, Dungeon, Level and generator, and mob/combat-engine tuning.
- The entrance/exit room cluster in full: package move with save-compat aliases, 6 new region standard rooms, 20 entrance/exit variants, and the `canMerge` signature migration across 14 definitions.
- Tasks 17–20 partially landed (11 of 34 batches).
- Manifest triage: 730 unreviewed rows resolved down to 31.

</details>

<details>
  <summary><strong>Sub-B Slice 0 — sync foundation, 2026-07-22</strong></summary>

`api-diff`, `pack-smoke` and `smoke-boot` tooling, the `BundleBridge` save-compat scaffolding,
and the slice template that later slices are cut from.

</details>

<details>
  <summary><strong>Sub-A — fork infrastructure, 2026-07-21</strong></summary>

Fork established with three upstream remotes, attribution and licensing docs, `margarita` → `main`
rename, and both build paths verified green. Required a four-layer build-baseline hotfix — an
expired snapshot dependency, a wrong documented Gradle task, the DEX 64k method limit, and AndroidX
enablement — each layer unblocking the next.

</details>

---

## Roadmap

Every substantive commit updates this section. It's the accurate current state, not a snapshot from a while ago.

**Sub-projects (v0.1 track):**

| Sub | Name | Status | Notes |
|---|---|---|---|
| A | Fork infrastructure | ✅ done | This repo. Attribution, docs, branch rename `margarita` to `main`. |
| B | Upstream sync (CPD to SPD v3.3.8) | 🟡 Slice 1 of 7 | Slice 0 shipped. Tasks 11–16 done, 17–20 partial. Engine, hero, dungeon, level generation and enemy behaviour integrated. Slice 1 turned out ~9x its original estimate. |
| C | Broad modding-platform API | ⏳ next | Java-hook API on top of CPD's JSON-manifest framework: cutscenes, dialogue, dual-wield, story flags, biome swapping, NPC insertion. |
| D | God Mode addon | ⏳ | Top-tier starter gear. Flagged as "cheat" in coop lobby. |
| E | Hard Mode addon | ⏳ | Balance tuning. Coop-fair. |
| F | Bonfire Mode addon | ⏳ | Permadeath modifiers plus rest-checkpoint economy. Coop-fair. |

**Post-v0.1 waves.** These need their own brainstorm passes. The list below is a direction, not a commitment schedule.

- Sphere Grid progression system
- Keyblade weapon type · Keybearer class · dual-wield reward system
- Turn-based combat toggle
- Save zones · towns · quest system · narrative-state module
- 200-floor generator + biome variety framework · cosmic-horror biome pack
- Character cameo framework + first cameos
- Real-time coop (Sub-K)
- Nestalgia-style turn-based coop
- Leaderboards + labeled seed sharing
- Story spine + cutscene/dialogue engine

---

## Alpha testers and watchers

There's nothing new to play yet. Sub-B, the upstream sync, is underway: the engine and tuning catchup toward Shattered Pixel Dungeon v2.5 is landing on `main` now, but it isn't a playable release and no alpha has shipped. If the roadmap sounds like something you want to see happen, starring the repo bookmarks it for later, and switching Watch to Custom then Releases will ping you when the first alpha ships.

Issues are welcome. Bugs, feature ideas, cameo suggestions, all fine. Pull requests aren't being accepted right now because the modding API isn't stable and every hook is likely to change. That loosens up when Sub-C ships.

---

## Attribution

Lutherverse is GPL-3.0. It carries the attribution chain forward:

- Base game: *Shattered Pixel Dungeon* by Evan Debenham. [00-Evan/shattered-pixel-dungeon](https://github.com/00-Evan/shattered-pixel-dungeon).
- Modding framework: *Custom Pixel Dungeon* by QuasiStellar. [QuasiStellar/custom-pixel-dungeon](https://github.com/QuasiStellar/custom-pixel-dungeon).
- Predecessor: *Pixel Dungeon* by Watabou.

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the GPL fork chain plus the currently-audited library notices (libGDX, RoboVM/MobiVM, SPD-classes). The remaining Apache/BSD/MIT dependency notices (FreeType, LWJGL, Kotlin, Ktor, SLF4J, org.json, gdx-controllers, androidx.multidex) get added before the first alpha binary ships; Sub-B tracks that.

Not affiliated with, endorsed by, or connected to any of the games or franchises Lutherverse takes creative inspiration from. Character cameos are non-commercial fan-project references.

---

## License

GPL-3.0. See [`LICENSE.txt`](LICENSE.txt).

If you use or modify this code, your derivative work has to also be GPL-3.0. Those are the terms Evan set for Shattered Pixel Dungeon and we keep them.

---

## Platform support

| Platform | Status |
|---|---|
| **Android** | Supported. See build instructions below. |
| **Desktop** (Windows / macOS / Linux) | Supported. See build instructions below. |
| **iOS** | Not supported. QSR removed the `ios/` directory from CPD in May 2023, and Slice 0 removed its stale `settings.gradle` entry. iOS remains deferred indefinitely. |

---

## Building

### Prerequisites

- JDK 17+
- Android SDK for the Android build (compile-SDK 33, build-tools 33.0.2)

### Android APK

```
./gradlew android:assembleDebug
```

The debug APK lands in `android/build/outputs/apk/debug/`.

### Desktop JAR

```
./gradlew desktop:release
```

The runnable JAR lands in `desktop/build/libs/`.

---

## Modding

The modding framework is whatever Custom Pixel Dungeon ships right now: JSON manifest per mod, resource overrides, hero JSON merge semantics. Refer to CPD's mod documentation until Sub-C adds the broader Java-hook API. Once it does, its reference docs will live at `docs/modding-api-v1.md` (nothing there yet).

---

## Development docs

- [project_progress.md](project_progress.md): running log of done / in flight / next, plus the open defect list.
- [CHANGELOG.md](CHANGELOG.md): every substantive commit.
- [PROJECT-STATUS.md](PROJECT-STATUS.md): deeper detail on current state, blockers, and the reasoning behind them.
- [Merge and diff tooling](services/tools/README-merge.md): Mergiraf and difftastic setup for upstream porting.
- [Design specs](docs/superpowers/specs/): sub-project design docs.
- [Implementation plans](docs/superpowers/plans/): task-by-task plans for each sub-project.
- [Assets](docs/assets/): images and design assets.

<p align="center">
  <sub>Lutherverse is a fan project by <a href="https://github.com/luther-rotmg">luther-rotmg</a>, built on top of Watabou, Evan, and QSR's work.</sub>
</p>
