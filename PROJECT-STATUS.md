# Project Status

*Snapshot of where Lutherverse (repo: `CustomPixelDungeonUltimate`) is right now. Updated in the same commit as any substantive work — treat as ground truth for near-term state.*

**Last update:** 2026-07-25
**Current tip:** `main` at the tip of this commit (run `git log -1 main` for the exact SHA; this file is updated in every substantive commit).

---

## Where we are

**Sub-A (Fork infrastructure)** is shipped. Seven commits landed on `main` and pushed to origin. Both build paths verified green (Android APK 22.8 MB, Desktop JAR 45.9 MB). Final whole-branch review completed with one blocker (a "Dark Souls Mode" leak in the public roadmap table) plus four documentation mediums plus a small tail of nits; all fixed in the same commit that added this line.

**Sub-B (Upstream sync to SPD v3.3.8)** Slice 0 is shipped; Slice 1 is in execution, Phase 3. Full design spec at [docs/superpowers/specs/2026-07-22-cpdu-sub-b-upstream-sync-design.md](docs/superpowers/specs/2026-07-22-cpdu-sub-b-upstream-sync-design.md). Slice 1 implementation plan at [docs/superpowers/plans/2026-07-23-cpdu-sub-b-slice-1-catchup.md](docs/superpowers/plans/2026-07-23-cpdu-sub-b-slice-1-catchup.md). Eight architectural decisions remain locked: Cleric first-class + Vault first-class + full marketplace green-gate + DSL freeze + save bridge + iOS deferred + SPD tilemap edits win + 14 slices with sub-splits.

Phase 3 has landed Tasks 11–16 (Actor, Char, Hero, Dungeon, Level and generator, and mob/combat-engine tuning), including the full entrance/exit room cluster. Tasks 17–20 are partially landed — 11 of 34 batches integrated before the pipeline stopped on exhausted OpenRouter credits (see below). Sixteen dependency-deferred clusters from Tasks 11–14 remain open as epics, tracked in beads.

> **⚠ Slice 1 is materially larger than this plan states.** A 2026-07-25 audit found 730 in-range commits sitting in the manifest's `cold-code` batch with unreviewed `provisional:` reasons from Task 6. Machine triage classified **541 of them as unintegrated Slice 1 candidates** — roughly nine times the size of Task 15's own 63-commit scope. They are area-bucketed (`triaged-item` 181, `triaged-mob` 123, `triaged-ui` 83, `triaged-level` 64, `triaged-misc` 55, `triaged-buff` 35) so Tasks 16–20 can consume them, but **that triage is not the reviewed manifest the plan's acceptance block requires.** "Slice 1 candidate" means the symbols resolve against CPDU, not that the hunks apply; the conditional-music cluster is the counterexample where every class existed and the change was still architecturally superseded. Expect the 541 to shrink under hunk review, and expect Tasks 16–20 to re-estimate upward regardless.

---

## Roadmap

| Sub | Name | Status | Blockers / Notes |
|---|---|---|---|
| A | Fork infrastructure | ✅ shipped | Seven commits on origin, both builds verified, final review clean after one fix commit. |
| B | Upstream sync (CPD → SPD v3.3.8) | 🔴 Slice 1, **blocked** | Tasks 11–16 done; 17–20 partial; Phase 4 gates run (21–23 pass, 24 blocked on emulator). **Pipeline halted: OpenRouter credits exhausted.** 23 beads parked `worker-failed`, specs believed sound. |
| C | Broad modding-platform API | ⚪ not started | Blocked on Sub-B ship |
| D | God Mode addon | ⚪ | Blocked on Sub-C |
| E | Hard Mode addon | ⚪ | Blocked on Sub-C |
| F | Bonfire Mode addon | ⚪ | Blocked on Sub-C |
| G | CI / GitHub Actions | ⚪ | Deferred |
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
- Note `core:test` is `NO-SOURCE` — core has no tests, so **compilation is the only mechanical gate** for core-only changes. `SPD-classes:test` is where tests actually live.
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
