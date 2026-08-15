# Project Status

*Snapshot of where Lutherverse is right now. The repo was renamed from
`CustomPixelDungeonUltimate` to `Lutherverse` on 2026-08-10; GitHub redirects the old URLs,
and dated design docs, plans and CHANGELOG entries deliberately keep the original name as a
historical record. Updated in the same commit as any substantive work — treat as ground truth for near-term state.*

**Last update:** 2026-08-14
**Current tip:** `main`, pushed to origin (run `git log -1 main` for the exact SHA).

> The dated sections further down are a **historical log** and are deliberately not rewritten.
> Where an older entry contradicts this header, the header wins.

---

## Where we are

**The build-craft vision has a running spine (2026-08-13/14).** The Keybearer prototype
class shipped to `main` end-to-end: three-element sphere grid (Ember/Frost/Storm) with
tier-III nodes and the Elemental Conflux keystone, three original keyblades with signature
abilities and true dual-wield, the Keyblade Nova class ability (with its own icon), a
persistent cross-run Insight layer, and a distinct hero model (recolored spritesheet +
splash). The mod-platform pillar is being dogfooded: **Keywraith** (mob) and **Insight
Crystal** (meta-progression item) are the first content shipped through the
`content-scaffold` pipeline — the item path surfaced a real scaffold bug (Generator
classes/probs desync, beaded) now guarded by an every-category lockstep test. Test
foundations grew from F1/F2 to **F3 (`HeadlessLevel`)**: real-Level integration tests run
headless, including full class-ability activation. Core test count 44 → 68+, every commit
gate-verified (full gate set + deletion-audit + content-audit ratchet). A five-entity
content wave (TumblerWisp, HexwardMoth, WardstoneSentinel, GaolShade, WardensSigil) is
scaffolded on `main`'s lineage with mechanics in flight via parallel implementation.
Design record: [2026-08-13-build-craft-spine-design.md](docs/superpowers/specs/2026-08-13-build-craft-spine-design.md).

**Sub-A (Fork infrastructure)** is shipped. Seven commits landed on `main` and pushed to origin. Both build paths verified green (Android APK 22.8 MB, Desktop JAR 45.9 MB). Final whole-branch review completed with one blocker (a "Dark Souls Mode" leak in the public roadmap table) plus four documentation mediums plus a small tail of nits; all fixed in the same commit that added this line.

**Sub-B (Upstream sync to SPD v3.3.8)** Slice 0 shipped; Slice 1 is in execution and **no longer
blocked**. Design spec: [2026-07-22-cpdu-sub-b-upstream-sync-design.md](docs/superpowers/specs/2026-07-22-cpdu-sub-b-upstream-sync-design.md).
Slice 1 plan: [2026-07-23-cpdu-sub-b-slice-1-catchup.md](docs/superpowers/plans/2026-07-23-cpdu-sub-b-slice-1-catchup.md).
Eight architectural decisions remain locked.

Tasks 11–16 are done and 17–20 are partial. The OpenRouter credit halt that stopped the worker
pipeline no longer binds — the deferred dependency clusters are being worked directly instead.
**106 beads closed, 15 ready.**

**What changed on 2026-08-10/11.** The verification story was rebuilt, because the gates were
passing without checking anything:

- `deletion-audit`, `desktop-smoke`, `manifest-audit` and `port-verify` now exist, each with a
  negative control proving it can fail.
- `options.release = 8` constrains the Java 8 modules to the Java 8 *API*; Android Lint runs over
  `core` and `android` with **no baseline on `core`**.
- `:core` has a test source set for the first time.
- CI exists and is green, asserting non-vacuity at every step.

Real defects found and fixed in that pass: `List.of` on a minSdk 19 app with no desugaring;
region-tinted grass and ember tiles silently not rendering; and **seeded runs that were not
actually seeded** — guaranteed-item and quest-NPC floors were drawn before the seed existed, and
actor scheduling order varied per run, so turn resolution itself was nondeterministic. Same seed
now reproduces the same run.

**The queue's real blocker is no longer capacity — it is two decisions.**

0. **Conditional music now has a measured cost.** `asset-audit` (new) finds **9,783 KB of
   orphaned `.ogg` files — ~54% of the game's music, ~30% of a 31 MB APK** — every one a
   conditional-music track whose naming code is `cpdu-15l`, blocked on the pack-config
   question. That decision is no longer just a design preference; it is a third of the
   download.

1. **Asset strategy.** The remaining ready clusters (Cached Rations, FloatingText damage icons)
   port upstream commits that ship binary sprite sheets. CPDU drives sprites from pack config, so
   each needs a call on whether to take upstream's sheet, extend pack config, or defer. The parked
   conditional-music cluster (`cpdu-15l`) is the same question about audio.
2. **Vision decomposition.** Nothing in the queue advances the 200-floor vision, because it has
   never been decomposed into beads. That, not integration throughput, is what blocks vision work
   from starting.

**Spec quality warning.** Of six deferred-cluster specs worked on 2026-08-10/11, **three were
wrong** — a missing localization key, a helper specified on the wrong class with the wrong
signature, and a prohibition that read as forbidding upstream's own fix. All three would have
compiled and passed every gate. Verify each remaining spec against upstream before implementing it;
the gates catch regressions, not a faithfully implemented wrong spec.

---

## Roadmap

| Sub | Name | Status | Blockers / Notes |
|---|---|---|---|
| A | Fork infrastructure | ✅ shipped | Seven commits on origin, both builds verified, final review clean after one fix commit. |
| B | Upstream sync (CPD → SPD v3.3.8) | 🟡 Slice 1 of 7, unblocked | Tasks 11–16 done, 17–20 partial. The OpenRouter halt no longer binds: deferred clusters are being worked on frontier instead. 106 beads closed, 15 ready. Remaining ready work needs an asset-strategy decision (below). |
| C | Broad modding-platform API | ⚪ not started | Blocked on Sub-B ship |
| D | God Mode addon | ⚪ | Blocked on Sub-C |
| E | Hard Mode addon | ⚪ | Blocked on Sub-C |
| F | Bonfire Mode addon | ⚪ | Blocked on Sub-C |
| G | CI / GitHub Actions | ✅ shipped | `.github/workflows/ci.yml`, green. Every step asserts it analysed something. |
| J | iOS reactivation | ⚪ | On paper only; deferred indefinitely |
| K | Real-time coop | ⚪ | Post-v0.1 |

**Post-v0.1 north-star waves** (the "Lutherverse ultimate vision" — needs a re-brainstorm after Sub-B ships): sphere grid progression · keyblades + Keybearer class + dual-wield reward system · turn-based combat toggle · save zones + towns + quest system + narrative state · 200-floor generator + biome variety · cosmic-horror biome pack · character-cameo framework · Nestalgia-style turn-based coop · leaderboards + labeled seeds · story spine + cutscene/dialogue engine.

---

## Recent activity

**2026-07-25 (Sub-B Slice 1 Phase 3 execution):**
- **Task 15 (Level and generator)** — 6 beads run; boss-level fixes, Level v2.5 state/load fixes, and levelgen challenge RNG isolation integrated. Two beads were verified no-ops (CPDU had already superseded the behavior).
- **Entrance/exit room cluster complete** — 13 in-range commits across 4 reviewed waves: the `EntranceRoom`/`ExitRoom` package move with `Bundle.addAlias` save-compat, 6 new region standard rooms, 20 entrance/exit variants, the `canMerge(Level, Room, Point, int)` signature migration across 14 definitions, and 7 refinement commits.
- **Manifest audit** — 730 unreviewed `provisional:` rows triaged to zero (see the warning above). Separately, 30 mining/quest commits were reclassified to Slice 2, and 8 entrance/exit commits were rescued from `cold-code`.
- **Group 15A resolved as superseded** — CPDU has no `Dungeon.branch`; its `levelName` layout graph generalizes upstream's depth+branch model. `de6878cef` was already integrated and its manifest row was stale.
- **Conditional-music cluster deferred** (`cpdu-15l`) — on a design question only. CPDU drives music from pack config via `CustomLevel.playLevelMusic`, so upstream's hardcoded conditional track selection may belong in pack config rather than level code. That touches the frozen DSL and needs LO's call. *(An earlier version of this entry claimed the TENSE/FINALE `.ogg` assets were missing. They are not — all nine landed 2026-07-23 with the Task 9 music batches. Only the `Assets.Music` constants are absent, which is a handful of strings. A pipeline seam: the assets bead landed the files, the code bead referencing them was deferred. Worth auditing the other Task 9 asset batches for the same seam.)*
- **Bead validate gate was never running.** The dispatcher parses `validate:` from `acceptance_criteria`/`notes` (never `description`), requires it to start its own line, and requires a bare allowlisted first token — so `.\gradlew.bat` was rejected on both counts. Every bead through Task 14 closed on frontier diff review alone with the compile gate silently skipped. Fixed with `.beads/validate-core.ps1`; all Task 15E beads gated green.
- Note `core:test` was `NO-SOURCE` — core had no tests, so compilation was the only mechanical gate for core-only changes. **Superseded 2026-08-10:** `:core` now has a test source set and its lint gate runs with no baseline.
- **Task 15 closed out** — mob spawn and room sizing (`268b804ac` `sizeFactor`/`mobSpawnWeight`/`connectionWeight` migration, `cb18c1eee`, `d45cc9cae`), plus housekeeping resolved inline: SPD copyright extended to 2024 on the 30 `levels/` files that actually received v2.4/v2.5 content, the `BArray` move recorded as superseded (CPDU keeps it at `utils/BArray.java`), and upstream's codebase-wide import reorder skipped as churn.
- **`5576a7777` split out** (`cpdu-m7a`) — the cached-rations redesign needs a new `SupplyRation` class, so its `RegularLevel` hunk cannot stand alone. Routed to Task 17. Flagged there: `ItemSpriteSheet` is a positional index table, the same hazard class as `StandardRoom.chances[]`, where a bad reindex yields wrong icons at runtime with a clean compile and no tests.
- Pipeline: 15 beads dispatched, 15 succeeded on attempt 1, no failures, timeouts, or re-dispatches. Two further items were resolved inline as below the bead value line.
- **Task 16 (mob and combat-engine tuning) complete** — 101 Slice 1 commits across 8 beads: 47 files, 699 insertions, concentrated in `actors/mobs`. Batch-reviewed with a Slice 2 leakage scan (zero hits) and a forced full recompile.
- **Task 16 boundary correction** — 22 of the 123 `triaged-mob` commits were new blacksmith/crystal/gnoll quest content ("re-enabled crystal quest", "crystal and gnoll quests now have 50/50 spawn") and were reclassified to Slice 2 before dispatch. Four more were caught the same way in Tasks 19–20. Concrete proof that the machine triage is a first pass, not the reviewed manifest: its marker scan reads added lines only, so commits referencing quest *state* without naming a marker class slip through.
- **Bead sizing rubric is wrong for this workload.** Four Task 16 beads timed out on the primary model and recovered on the fallback. Timeouts track **commit count and file spread, not LOC** — one bead was only 434 lines but 23 commits; another died at 11 commits spread across many enemy files. The CLAUDE.md rubric is LOC-only. Working limit found: **≤12 commits with a narrow file set**; heterogeneous buckets need ~4.
- **Tasks 17–20 dispatched as 34 right-sized beads; 11 landed before the pipeline halted.** 50 files, 512 insertions across `windows/`, `items/`, `actors/`, `ui/`, `scenes/`. Leakage scan clean, compile and tests green.
- **⛔ Pipeline halted on exhausted OpenRouter credits.** 23 beads failed with `AI_APICallError: Insufficient credits`, not on their specs. Parked `worker-failed` and annotated; see Awaiting LO input.
- Session totals: 34 beads succeeded, 23 blocked on credits, 1 genuinely oversized bead split after two `ra=0` hard-timeouts.

**2026-07-23 (Sub-B Slice 1 planning):**
- Reconciled the approved design against Git history and found the exact upstream range: 1,209 commits from SPD v2.1.0 to v2.5.4.
- Authored the just-in-time Slice 1 plan with an explicit engine-vs-feature classification manifest and prerequisite tooling recovery.
- Audited Slice 0 gates: current pack smoke is a 29-pack structural manifest check, Android smoke is a PID-alive check, and the real save-roundtrip harness remains future work.

**2026-07-22 (Sub-B Slice 0 execution session):**
- Sub-B Slice 0 shipped: `services/tools/{api-diff,pack-smoke,smoke-boot}/` + `SPD-classes/src/main/java/com/watabou/utils/BundleBridge*` + SLICE-TEMPLATE.md + `:ios` cleanup.

**2026-07-21 (Sub-A execution session):**
- Sub-A brainstorm + design spec + implementation plan
- Task 1: fork + local clone + 3 remotes + margarita→main branch rename
- Task 2: pin fork base with annotated tag `cpd-sync-base-2025-08-15` (implementer prematurely pushed the tag — non-blocking)
- Task 3: attribution docs commit (`ad6be78d4`)
- Task 4: design + Sub-A plan import (`6e5d6447b`)
- Ad-hoc: Lutherverse rebrand commit (`6041725c8`)
- Env: installed JDK 17 (Microsoft OpenJDK 17.0.19.10) + Android SDK 33
- Sub-B pre-brainstorm research workflow: 7 agents, 3 phases, adversarial verify caught 50% task-estimate under
- CHANGELOG.md + PROJECT-STATUS.md (this file) added for LO's changelog-cadence discipline

**Sub-A Task 5 (build verification) status:** ✅ both build paths verified. Required a 4-layer build-baseline hotfix — each layer of upstream rot unblocked the next:

1. `gdxControllersVersion '2.2.4-SNAPSHOT'` → `'2.2.3'` (SNAPSHOT dep expired everywhere)
2. Fixed `desktop:dist` → `desktop:release` in all docs (CPD uses a custom `release` task, no `dist` task exists)
3. Enabled Android multidex (`multiDexEnabled true` + `androidx.multidex:2.0.1` dep + `MultiDexApplication` in manifest) to defeat DEX 64k method-reference limit at `minSdk=19`
4. Set `android.useAndroidX=true` in `gradle.properties` (required by AGP once any `androidx.*` dep is on the classpath)

**Final verification:** `android-debug.apk` 22.8 MB, `desktop-2.1.0-1.0.jar` 45.9 MB. See [CHANGELOG](CHANGELOG.md) "Fixed — Sub-A build-baseline hotfix" for full detail. Sub-B will naturally revisit gdx-controllers version (SPD v3.2+3.3 bump libGDX) and multidex (SPD v3.2 bumps minSdk to 21, at which point native multidex takes over).

**Ad-hoc additions this session:**
- Ad-hoc rebrand commit (Lutherverse README + placeholder title card SVG) — `6041725c8`
- CHANGELOG.md + PROJECT-STATUS.md added per changelog-cadence rule — `f37dfb1b2`
- CONTRIBUTING.md + `.github/ISSUE_TEMPLATE/` (bug / feature / cameo templates + config) — `75f58ce99`
- Sub-A build-baseline hotfix (gdx-controllers pin + multidex + useAndroidX + desktop:dist→release doc rename): `fa5a31750`
- README humanization pass (stripped AI-writing tells): `3eff58a15`
- Final whole-branch review fix commit (Dark Souls Mode leak, PROJECT-STATUS staleness, RoboVM copyright, SVG SPDX header, iOS README framing, notices-scope wording, Sub-B research imported into repo): this commit

---

## Slice 1 Phase 4 gate results (2026-07-26)

| Gate | Command | Result |
|---|---|---|
| Core compile | `gradlew core:compileJava --rerun-tasks` | **PASS** (forced, 4 tasks executed) |
| SPD-classes tests | `gradlew SPD-classes:test --rerun-tasks` | **PASS** |
| api-diff tests | `gradlew :services:tools:api-diff:test --rerun-tasks` | **PASS** |
| pack-smoke tests | `gradlew :services:tools:pack-smoke:test --rerun-tasks` | **PASS** |
| namespace-transform tests | `gradlew :services:tools:namespace-transform:test --rerun-tasks` | **PASS** |
| Desktop build | `gradlew desktop:release` | **PASS** — `desktop-2.1.0-1.0.jar`, 54 MB |
| Android build | `gradlew android:assembleDebug` | **PASS** — `android-debug.apk`, 32 MB |
| API compatibility | `api-diff --base 7d9c139c8 --head HEAD` | **PASS after review** — 1021 files, 21 removed / 160 added / 13 changed, all accounted for |
| Marketplace pack smoke | `PackSmokeCli --marketplace ./marketplace` | **PASS** — 29/29 GREEN, matches Slice 0 baseline |
| Android runtime smoke | `smoke-boot.ps1 -TimeoutSeconds 300` | **BLOCKED** — emulator never reaches `sys.boot_completed`; fails before `adb install`, so the APK is never exercised |
| Manual runtime checks | title screen, seeded Warrior run, save/reload | **NOT DONE** — requires a human at the machine |
| Serialized-state roundtrip | — | **NOT DONE** — see below |

## ⛔ P0 found 2026-08-10 — seeded runs are not actually seeded

`cpdu-yaa`. `Dungeon.init()` computes `posLevels`, `souLevels`, `asLevels` and all four
quest-NPC floors with an RNG that is neither the game's nor seeded. **Two stacked defects:**

1. `modding/randomGenUtils.kt` has no imports. `.shuffled()` is Kotlin stdlib, backed by an
   unseeded `java.util.Random`; `.random()` is `kotlin.random.Random.Default`. Neither is
   `com.watabou.utils.Random`.
2. Even with the right RNG it would still fail: those calls sit at roughly `Dungeon.java`
   lines 218–228, `seed` is not assigned until ~232, and `Random.pushGenerator(seed+1)` is
   at ~245. **The distributions are computed before the seed exists.**

Same seed, two runs → different guaranteed scroll/potion floors and different quest-NPC
floors. This defeats labeled seed sharing, **daily runs (already shipped)**, deterministic
lockstep coop, and any replay verifier.

Fix needs both halves, and should land *with* the seeded-determinism harness rather than
before it — sequence after the `:core` headless bootstrap spike. There is no correct current
behavior to preserve, since it is already nondeterministic.

## Group B started, Group D landed (2026-08-10)

- **`:core` has a test source set and tests for the first time.** `core:test` was `NO-SOURCE`
  and reported green forever across 1019 Java + 49 Kotlin files. Seven tests now cover
  `RandomGenUtils.halveQuantities`, chosen because they touch no libGDX statics and so did
  not have to wait on the headless bootstrap spike.
- **CI exists** (`.github/workflows/ci.yml`); the repo previously had none. Every step
  asserts it analysed something: it probes that `options.release = 8` is genuinely applied,
  requires each test suite to report a non-zero count, requires deletion-audit to scan >0
  files, runs manifest-audit in canary mode, requires pack smoke to report 29/29, and
  size-checks the jar and APK. `desktop-smoke` and the Android emulator are excluded with
  written reasons. **Not yet executed — needs a push.**
- **Forbidden Runes (`cpdu-0a7`) landed.** `Challenges.NO_SCROLLS` was defined but never
  consulted anywhere. Implemented inside CPDU's layout-driven model by halving each
  region's `ItemDistribution.quantity`, so custom layouts and the save round-trip are
  unaffected.

## Toolbelt Group C partially landed (2026-08-10)

Attacks the ~541-commit upstream backlog, the largest recurring cost in the project.

- **`git rerere` enabled** (`rerere.enabled` + `rerere.autoupdate`). It was off, so the same
  namespace-rename conflicts were being re-resolved by hand on every batch.
- **`services/tools/manifest-audit`** — the Slice 1 manifests define the whole slice's scope
  and every downstream estimate, are hand-maintained, and already lied once. Seven checks,
  canary-verified. Current state: **PASS** — 1209 inventory rows, 6083 classification rows,
  31 provisional (down from 761 pre-triage), 0 coverage gaps, 0 orphans, closed vocabulary.

  **Correction to the record:** an earlier reading of this session claimed the manifests were
  untracked and that the 730 provisional rows were never triaged. Both were wrong. The
  canonical manifests are git-tracked at `docs/superpowers/research/`, and the triage did
  happen — 730 → 31. The confusion came from `.beads/`, which holds **14 untracked working
  copies** with authoritative-sounding names (`final.sol3`, `final.validation2`) that are
  *pre-triage* snapshots showing 761 provisional rows, and whose five `final` files split into
  **two mutually contradictory versions**. `manifest-audit` check M7 now reports them so the
  next reader is not misled the same way.

- **Two determinism defects fixed.** `Random.chances(HashMap)` selects via
  `keySet().toArray()`, and `Class` does not override `hashCode()`, so a `HashMap` keyed by
  `Class` iterates in identity-hash order that varies between JVM runs.
  `WandOfCorruption`'s debuff maps and `AlchemicalCatalyst.potionChances` were both affected:
  the same seed produced a different debuff or a different brewed potion on different runs.
  That breaks labeled seed sharing, deterministic lockstep coop, and any replay verifier —
  three things on the roadmap. Fixed with `LinkedHashMap`, matching what
  `Generator.categoryProbs` already did correctly. There was no prior deterministic behavior
  to preserve. `float[]` call sites and keyed-lookup maps were checked and are unaffected.

**Still open in Group C:** `port-verify` (bead filed — the design insight is that
`services/tools/namespace-transform` is the missing link for comparing an upstream diff
against its CPDU port), plus Mergiraf and difftastic, which need LO to install the binaries.

## Toolbelt Group A landed (2026-08-10)

Research: [2026-08-10-toolbelt-research.md](docs/superpowers/specs/2026-08-10-toolbelt-research.md).

- **`options.release = 8`** on every module whose `sourceCompatibility` is 1.8. `-source/-target`
  restrict the *language* level only; javac 17 was resolving Java 17 APIs into Java 8 bytecode.
  It immediately caught `BundleBridge`'s `java.util.List.of` (Android API 34) on a minSdk 19 app
  with no core library desugaring — latent, but armed for the Slice 3a save path. Fixed.
  `services/tools/*` correctly keep Java 17; `:android` is excluded because `--release` conflicts
  with AGP's `android.jar` bootclasspath.
  *The check must run in `afterEvaluate`* — a bare `configureEach` reads `sourceCompatibility`
  before the subproject sets it and silently applies the flag to nothing. Verified by probing
  `options.release` rather than trusting a green build.
- **`.gitattributes`** added; the repo had none while `core.autocrlf=true`. Line endings were a
  property of each machine rather than the repo, which inflates conflicts on the ~541 commits
  still to port and would diverge on a Linux CI runner. A `--renormalize` dry run touched zero
  files and zero binaries, so this locks in correct behavior rather than rewriting anything.
- **Android Lint** wired as a ratcheted gate. It ships in AGP 7.4.2 and had never been run. Its
  default scope is misleading: `:android:lint` analysed 9 files and *zero* of core's 1068.
  `checkDependencies` alone does not fix that because `:core` is a plain `java-library`; applying
  AGP's standalone `com.android.lint` plugin to `:core` is what makes `checkDependencies` descend.
  That combination is the **only** configuration that runs NewApi against core's 49 Kotlin files
  with a real minSdk context, since `options.release` has no Kotlin equivalent.
  Found 12 `DefaultLocale` violations (8 in `Generator.java`) — implicit-locale case conversion on
  a game shipping 202 localization bundles. Parked in baselines; bead filed.
  **Ratchet verified by negative control:** an injected violation fails the build.

Both baselines are gates that can lie by construction — they hide whatever is recorded in them.
Review and shrink them on a schedule; do not let them grow.

## T1 gate repair (2026-08-10) — the three open gates are now closed

Plan: [2026-08-10-t1-gate-repair.md](docs/superpowers/plans/2026-08-10-t1-gate-repair.md).
Design: [2026-08-10-lutherverse-push-design.md](docs/superpowers/specs/2026-08-10-lutherverse-push-design.md).

**`services/tools/deletion-audit` now exists** and audits what api-diff structurally
cannot: private members, and statements removed from inside a body whose signature
never changed. First real run over `7d9c139c8..HEAD`, 1,021 files: **35 deleted, 8
shrunk**. Triage classified 27 as legitimate and allowlisted them with reasons in
`services/tools/deletion-audit/reviewed-removals.txt`; **16 remain open**, tracked in
beads. Two matter:

- **`Noisemaker.Trigger`** — the entire `Bundlable` inner class was removed along with
  `setTrigger`/`glowing`. If no save migration accompanied it, old saves carrying a
  Noisemaker Trigger fail to restore.
- **`DM201#act()`** — vent logic moved into a `Hunting` state class (upstream's
  refactor), but the old `canVent(enemy.pos)` and non-adjacency guards did not move
  with it. `DM201.canVent` is now overridden and never called. api-diff cannot see
  this, because `canVent` itself still exists.

**Known false-positive class:** deletion-audit has no rename detection, so a moved file
reports every callable as deleted. The `EntranceRoom`/`ExitRoom` package move produced
11 such entries. Bead filed to add `git diff -M` rename following.

**`Bundle.addAlias` resolution is now pinned by a test** (`BundleAliasRoundtripTest`)
and **it works**, with a negative control proving an unregistered vanished class does
not resolve by some other path. This retroactively validates the save-compat approach
Slice 1's package moves relied on. It covers the *mechanism* only — `core` depends on
`SPD-classes` and never the reverse, so no test there can reach the actual
registrations or the `CUSTOM_DECO`-takes-`SIGN`-id-23 terrain reuse. Bead filed for a
static checker.

**`services/tools/desktop-smoke` replaces the Android emulator smoke** and passes: the
real jar boots and renders 120 frames. This is the first automated proof in this
project's history that the game actually starts. **Android runtime smoke is hereby
downgraded to a documented manual pre-release check** and must not be cited as an
automated gate. desktop-smoke requires a display and cannot run headless.

`core:test` is still `NO-SOURCE` and remains vacuous; bead filed to add a test source
set. Until then, compilation plus deletion-audit are the mechanical gates for
core-only changes.

### Known tool limitations found while running these gates

- **`core:test` is `NO-SOURCE`.** The core module has no tests at all, so for
  core-only changes *compilation is the only mechanical gate*. Every acceptance
  bullet in this plan that cites `core:test` is vacuous.
- **The api-diff tool was inert until fixed.** It ran `git ls-tree` with no
  working directory, inheriting Gradle's subproject CWD, so it scanned 0 files
  and printed PASS. Fixing that exposed a second bug: `isPathNotFound` matched
  only git's `does not exist in` message and not `exists on disk, but not in`,
  which is what a newly ADDED file produces — so it crashed on the first added
  file. Both fixed in `4b9c83f6b`. Its first real run caught a genuine
  regression (`CorpseDust.actions()`, fixed in `874e49851`).
- **The bead validate gate never ran before 2026-07-25.** The dispatcher parses
  `validate:` from `acceptance_criteria`/`notes`, never `description`, requires
  it to start its own line, and requires a bare allowlisted first token.
  `.\gradlew.bat` failed on both counts, so every bead through Task 14 closed on
  frontier diff review alone.
- **Android smoke is a PID-alive check only**, and cannot run in this
  environment at all. It never verified gameplay even when it did run.
- **No save-roundtrip harness exists.** Slice 1 touched serialized state
  (`Bundle.addAlias` registrations for the moved `EntranceRoom`/`ExitRoom`, and
  terrain ID reuse where `CUSTOM_DECO` takes the old `SIGN` id 23). The plan says
  to add real fixture/roundtrip work rather than mark this N/A. It is **NOT
  done** and remains an open gate.

### Task 9 asset-batch audit (2026-08-11) — the follow-up filed at T1 Task 9 Step 1

Checked all nine Task 9 asset-import specs (`.beads/task9-music-assets-01..08-spec.md`,
`.beads/task9-sprite-assets-spec.md`) against `Assets.java` and the sprite-loading code,
following up on the TENSE/FINALE `assets-landed/code-deferred` finding in
[2026-08-10-lutherverse-push-design.md](docs/superpowers/specs/2026-08-10-lutherverse-push-design.md#L195-L199).

- **Batches 1-4 (16 files: theme/sewers/prison/caves/city depth-1/2/boss tracks) are
  fully wired.** `Assets.Music` (`Assets.java:57-80`) declares all of them, and
  `CavesLevel`/`CityLevel`/`PrisonLevel`/`HallsLevel`/`SewerLevel`'s `playLevelMusic()`
  consume them correctly.
- **Batches 5-8 (halls_boss, the five depth-3 tracks, and the nine tense/finale
  tracks) are not a separate forgotten code bead — they're the already-tracked
  `cpdu-15l` decision.** `Assets.Music` has no depth-3 or tense/finale constants, and
  none of the five built-in region level classes have any code path that would
  consume them: each hardcodes only a 1/2/2-weighted rotation of its two base tracks,
  the same simplified system CPDU forked with. Wiring these up requires building
  upstream's conditional/depth-3 music selection first — exactly the "should this be
  pack-config or level code" question already recorded under "Awaiting LO input"
  above. The design doc's framing ("the code bead referencing them was deferred") is
  slightly imprecise for this part: there's no missing constant-adding bead to file:
  it's blocked on the `cpdu-15l` product decision, which already exists. **The 16 base
  files were correctly split into their own batches (1-4) and got their code path
  as a matter of course, so no new bead is needed here.**
- **Sprite batch is a different failure mode, not this seam.**
  `core/src/main/assets/sprites/items.png` and `core/src/main/assets/interfaces/talent_icons.png`
  both landed on disk, but no code path loads either single-atlas file — CPDU renders
  items and talent icons from one PNG per item/talent instead (`ItemSprite.java`,
  `GeneralAsset.kt`, under `sprites/items/*.png` and `interfaces/talent_icons/*.png`).
  These two atlas files are unreferenced dead weight for this fork's per-file sprite
  convention, not code waiting to be written. Filed as a follow-up: confirm with LO
  whether they're wanted for a future atlas-based rendering path, else delete them.

Net result: the pipeline-seam pattern does **not** recur elsewhere in Task 9 — the one
real gap (sprite atlas files) is architectural leftover, not a dropped code bead, and
everything else either already works or is already tracked under `cpdu-15l`. No new
`worker` bead filed for the music side. (Note: this session's Bash access was denied,
so the `bd create`/`bd close` calls this finding would normally use could not be run —
if a bead titled "Audit Task 9 asset batches for the assets-landed/code-deferred
pipeline seam" exists and is open, close it with a note pointing here.)

---

## Slice 1 re-estimation from measured velocity (2026-07-26)

**Measured, not guessed.** 34 worker beads completed this session at ~10 commits
each, roughly 8–10 minutes per bead serialized through one dispatcher. Four needed
a fallback-model retry (~15 min wasted each) before the sizing was corrected.

**Bead sizing rule, corrected by evidence.** The rubric in CLAUDE.md sizes by net
LOC. That under-predicts this workload: timeouts track **commit count and file
spread**, not lines. A 434-line / 23-commit bead timed out; a 509-line / 11-commit
bead spread across many enemy files timed out; 10–12 commits over a narrow file set
passes first try. Heterogeneous buckets need ~4 commits. Use commit count as the
primary sizing input for integration sweeps.

**Slice 1 remaining.** Tasks 11–16 are done and 11 of Tasks 17–20's 34 batches
landed, leaving 22 batches (~230 commits) blocked only on OpenRouter credits — about
4 hours of pipeline time. Beyond that sit the buckets the manifest still lists as
`slice1`: `cold-code` 3041 (mostly genuinely cold Watabou zones, but this is the
bucket the 730-row audit came out of and it has never been fully reviewed),
`frontier-mixed` 81, `assets-localization` 50, `build-review` 40, `platform-review`
27, plus the residual hotspot rows. Sixteen dependency-deferred epics from Tasks
11–14 also remain, most of which are frontier supersession calls rather than worker
work.

**Implication for Slices 2–7.** Slice 1's original estimate assumed a reviewed
manifest. It was not reviewed: 730 rows carried unreviewed `provisional:` reasons,
and 60 quest-content commits had to be reclassified out of Slice 1 across four tasks
after the machine triage missed them. Any estimate for Slices 2–7 that draws scope
from the same manifest inherits that error. **Re-estimate after a real hunk-level
review of the remaining buckets, not before** — a number produced now would repeat
the mistake Slice 1 just demonstrated. What can be said with confidence is the
throughput figure above: ~10 commits per bead, ~8–10 minutes per bead, one
dispatcher at a time.

---

## Awaiting LO input

- **⛔ BLOCKING — OpenRouter credits exhausted.** The bead pipeline stopped mid-run on
  `AI_APICallError: Insufficient credits`. 23 beads across Tasks 17–20 failed for this
  reason alone; their specs were never actually attempted against a working provider and
  should NOT be re-specced. After topping up at <https://openrouter.ai/settings/credits>,
  re-arm them all with:
  ```
  for b in $(bd list --label worker-failed --json | jq -r '.[].id'); do bd update $b --remove-label worker-failed; done
  ```
  then run the dispatcher normally. Nothing else is needed — the work resumes where it left off.

- **Slice 1 scope decision.** The 541 triaged commits mean Slice 1 is roughly an order of magnitude larger than planned. Options: review and integrate them inside Slice 1, split them into a Slice 1b, or push the non-engine areas to a later slice. Task 25's re-estimation should not be the first time this is confronted.
- **Conditional music as pack config** (`cpdu-15l`). Should amulet-obtained / quest-active track selection become a pack-config surface rather than hardcoded level code? Landing upstream's form would hardcode what CPDU deliberately made configurable, and would not apply to configured dungeons at all. Touches the frozen DSL.
- Otherwise Slice 1 remains authorized for autonomous execution under the repository's beads-pipeline and review rules.
- **Ultimate vision re-brainstorm** — LO explicitly deferred to "after Sub-B ships". Vision wave 1+2 already captured in frontier memory.

---

## Contribution status

- ★ Star and 👁 Watch → Releases are welcome. See [README's alpha-tester section](README.md#alpha-testers-and-watchers).
- Issues welcome (bugs, feature ideas, cameo requests)
- **PRs currently not accepted** — modding API is not stable, every hook is subject to change; this loosens up when Sub-C ships
