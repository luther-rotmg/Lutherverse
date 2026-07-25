# Project Status

*Snapshot of where Lutherverse (repo: `CustomPixelDungeonUltimate`) is right now. Updated in the same commit as any substantive work — treat as ground truth for near-term state.*

**Last update:** 2026-07-25
**Current tip:** `main` at the tip of this commit (run `git log -1 main` for the exact SHA; this file is updated in every substantive commit).

---

## Where we are

**Sub-A (Fork infrastructure)** is shipped. Seven commits landed on `main` and pushed to origin. Both build paths verified green (Android APK 22.8 MB, Desktop JAR 45.9 MB). Final whole-branch review completed with one blocker (a "Dark Souls Mode" leak in the public roadmap table) plus four documentation mediums plus a small tail of nits; all fixed in the same commit that added this line.

**Sub-B (Upstream sync to SPD v3.3.8)** Slice 0 is shipped; Slice 1 is in execution, Phase 3. Full design spec at [docs/superpowers/specs/2026-07-22-cpdu-sub-b-upstream-sync-design.md](docs/superpowers/specs/2026-07-22-cpdu-sub-b-upstream-sync-design.md). Slice 1 implementation plan at [docs/superpowers/plans/2026-07-23-cpdu-sub-b-slice-1-catchup.md](docs/superpowers/plans/2026-07-23-cpdu-sub-b-slice-1-catchup.md). Eight architectural decisions remain locked: Cleric first-class + Vault first-class + full marketplace green-gate + DSL freeze + save bridge + iOS deferred + SPD tilemap edits win + 14 slices with sub-splits.

Phase 3 has landed Tasks 11–14 (Actor, Char, Hero, Dungeon) and most of Task 15 (Level and generator), including the full entrance/exit room cluster. Sixteen dependency-deferred clusters from Tasks 11–14 remain open as epics, tracked in beads.

> **⚠ Slice 1 is materially larger than this plan states.** A 2026-07-25 audit found 730 in-range commits sitting in the manifest's `cold-code` batch with unreviewed `provisional:` reasons from Task 6. Machine triage classified **541 of them as unintegrated Slice 1 candidates** — roughly nine times the size of Task 15's own 63-commit scope. They are area-bucketed (`triaged-item` 181, `triaged-mob` 123, `triaged-ui` 83, `triaged-level` 64, `triaged-misc` 55, `triaged-buff` 35) so Tasks 16–20 can consume them, but **that triage is not the reviewed manifest the plan's acceptance block requires.** "Slice 1 candidate" means the symbols resolve against CPDU, not that the hunks apply; the conditional-music cluster is the counterexample where every class existed and the change was still architecturally superseded. Expect the 541 to shrink under hunk review, and expect Tasks 16–20 to re-estimate upward regardless.

---

## Roadmap

| Sub | Name | Status | Blockers / Notes |
|---|---|---|---|
| A | Fork infrastructure | ✅ shipped | Seven commits on origin, both builds verified, final review clean after one fix commit. |
| B | Upstream sync (CPD → SPD v3.3.8) | 🟡 Slice 1 Phase 3 | Tasks 11–14 done, Task 15 mostly done. 541 unintegrated Slice 1 commits found hiding in `cold-code`; Slice 1 needs re-estimation. |
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
- **Conditional-music cluster deferred** (`cpdu-15l`) — blocked on absent TENSE/FINALE audio assets *and* an open design question: CPDU drives music from pack config via `CustomLevel.playLevelMusic`, so upstream's hardcoded conditional track selection may belong in pack config rather than level code. That touches the frozen DSL and needs LO's call.
- **Bead validate gate was never running.** The dispatcher parses `validate:` from `acceptance_criteria`/`notes` (never `description`), requires it to start its own line, and requires a bare allowlisted first token — so `.\gradlew.bat` was rejected on both counts. Every bead through Task 14 closed on frontier diff review alone with the compile gate silently skipped. Fixed with `.beads/validate-core.ps1`; all Task 15E beads gated green.
- Note `core:test` is `NO-SOURCE` — core has no tests, so **compilation is the only mechanical gate** for core-only changes. `SPD-classes:test` is where tests actually live.
- Pipeline: 14 beads dispatched, 14 succeeded on attempt 1, no failures, timeouts, or re-dispatches.

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

## Awaiting LO input

- **Slice 1 scope decision.** The 541 triaged commits mean Slice 1 is roughly an order of magnitude larger than planned. Options: review and integrate them inside Slice 1, split them into a Slice 1b, or push the non-engine areas to a later slice. Task 25's re-estimation should not be the first time this is confronted.
- **Conditional music as pack config** (`cpdu-15l`). Should amulet-obtained / quest-active track selection become a pack-config surface rather than hardcoded level code? Landing upstream's form would hardcode what CPDU deliberately made configurable, and would not apply to configured dungeons at all. Touches the frozen DSL.
- Otherwise Slice 1 remains authorized for autonomous execution under the repository's beads-pipeline and review rules.
- **Ultimate vision re-brainstorm** — LO explicitly deferred to "after Sub-B ships". Vision wave 1+2 already captured in frontier memory.

---

## Contribution status

- ★ Star and 👁 Watch → Releases are welcome. See [README's alpha-tester section](README.md#alpha-testers-and-watchers).
- Issues welcome (bugs, feature ideas, cameo requests)
- **PRs currently not accepted** — modding API is not stable, every hook is subject to change; this loosens up when Sub-C ships
