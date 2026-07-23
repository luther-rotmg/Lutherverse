# Project Status

*Snapshot of where Lutherverse (repo: `CustomPixelDungeonUltimate`) is right now. Updated in the same commit as any substantive work — treat as ground truth for near-term state.*

**Last update:** 2026-07-23
**Current tip:** `main` at the tip of this commit (run `git log -1 main` for the exact SHA; this file is updated in every substantive commit).

---

## Where we are

**Sub-A (Fork infrastructure)** is shipped. Seven commits landed on `main` and pushed to origin. Both build paths verified green (Android APK 22.8 MB, Desktop JAR 45.9 MB). Final whole-branch review completed with one blocker (a "Dark Souls Mode" leak in the public roadmap table) plus four documentation mediums plus a small tail of nits; all fixed in the same commit that added this line.

**Sub-B (Upstream sync to SPD v3.3.8)** Slice 0 is shipped; Slice 1 planning and prerequisite recovery are active. Full design spec at [docs/superpowers/specs/2026-07-22-cpdu-sub-b-upstream-sync-design.md](docs/superpowers/specs/2026-07-22-cpdu-sub-b-upstream-sync-design.md). Slice 1 implementation plan at [docs/superpowers/plans/2026-07-23-cpdu-sub-b-slice-1-catchup.md](docs/superpowers/plans/2026-07-23-cpdu-sub-b-slice-1-catchup.md). Reconciliation found that the package-namespace transformer promised for Slice 0 is absent and that the Slice 1/Slice 2 boundary requires hunk-level classification across 1,209 upstream commits. Those are now explicit Slice 1 prerequisites. Eight architectural decisions remain locked: Cleric first-class + Vault first-class + full marketplace green-gate + DSL freeze + save bridge + iOS deferred + SPD tilemap edits win + 14 slices with sub-splits.

---

## Roadmap

| Sub | Name | Status | Blockers / Notes |
|---|---|---|---|
| A | Fork infrastructure | ✅ shipped | Seven commits on origin, both builds verified, final review clean after one fix commit. |
| B | Upstream sync (CPD → SPD v3.3.8) | 🟡 Slice 1 active | Plan drafted; namespace transformer + classification manifest are first gates. |
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

- **No immediate input required.** Slice 1 is authorized for autonomous execution under the repository's beads-pipeline and review rules.
- **Ultimate vision re-brainstorm** — LO explicitly deferred to "after Sub-B ships". Vision wave 1+2 already captured in frontier memory.

---

## Contribution status

- ★ Star and 👁 Watch → Releases are welcome. See [README's alpha-tester section](README.md#alpha-testers-and-watchers).
- Issues welcome (bugs, feature ideas, cameo requests)
- **PRs currently not accepted** — modding API is not stable, every hook is subject to change; this loosens up when Sub-C ships
