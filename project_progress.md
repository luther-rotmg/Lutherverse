# Project Progress

Running log of what has been done, what is in flight, and what is next. Updated in the same
commit as any substantive work.

For the deeper "why" behind current state, see [PROJECT-STATUS.md](PROJECT-STATUS.md).
For the release-facing change list, see [CHANGELOG.md](CHANGELOG.md).

**Last updated:** 2026-08-14

---

## Now

| Item | Status | Notes |
|---|---|---|
| **Build-craft spine (Keybearer)** | 🟢 **on `main`** | Three-element sphere grid (Ember/Frost/Storm, tier-IIIs, Elemental Conflux keystone with multi-prereqs), three original keyblades + signatures + true dual-wield, Keyblade Nova (own icon, 3-way dominant element), persistent Insight layer, distinct hero model. 68+ core tests, all gates green. An adversarial review round caught and fixed 2 blocking defects (grid-UI overflow, Insight burn on gated keystones) before ship. |
| **Content via scaffold pipeline** | 🟢 **dogfooding** | Keywraith (mob) + Insight Crystal (meta-progression runestone) shipped end-to-end through `content-scaffold`; a 5-entity wave (TumblerWisp d2, HexwardMoth d4, WardstoneSentinel d6, GaolShade d7, WardensSigil stone) is scaffolded with mechanics/art in parallel implementation. Dogfooding exposed the Generator classes/probs desync in the tool (beaded); an every-category lockstep test now guards it. |
| **Test foundations** | 🟢 **F3 landed** | `HeadlessLevel` real-Level fixture: storm-arc targeting semantics and full Keyblade Nova activation now run headless. F1 (boot) + F2 (save roundtrip) unchanged. |
| **P0 — seeded runs** (`cpdu-yaa`) | 🟢 **fixed**, scope open | Layout reproduction holds; `Level.mobs` HashSet still leaves full-run determinism open. Content additions (bestiary/pools) legitimately shift seeded gen — noted per commit. |
| Sub-B Slice 1 | 🟡 **parked behind vision work** | 22 batches (~230 commits) still parked; content-audit registration heuristic (`EntityGraph`) being tuned on the AFK track. |
| CI | ✅ **green** | Gate set + audits verified locally on every `main` push through 2026-08-14. |

## Next

1. Continue the Slice 1 deferred-cluster burndown (Cached Rations, Provoked Anger, FloatingText damage icons — all three touch binary sprite assets, which CPDU drives from pack config, so each needs an asset-strategy decision before porting) (`cpdu-6lz`) — the design insight is that `services/tools/namespace-transform` is the missing link for comparing an upstream diff against its CPDU port.
3. Resume the Slice 1 batch burndown with Mergiraf in place.
4. `gdx-backend-headless` bootstrap spike — still needed for levelgen invariants (reachability, solvability), just no longer blocking the P0.
5. Sub-C modding API design — gated on a vision-decomposition pass that has not happened yet.

## Open defects

| ID | Pri | Summary |
|---|---|---|
| — | — | none open |

**Closed 2026-08-11:** `cpdu-6xp` (`depth/5` region math — fixed the 3 sites that actually threw/broke
past the vanilla 25-floor range: `TrapsRoom.levelTraps[depth/5]`, `SecretRoom.regionSecretsThisRun[region]`,
and `WeakFloorRoom`'s tile lookup, all now clamped to the last of the 5 vanilla regions. The other
`depth/5` call sites were already safe (`Generator` clamps via `GameMath.gate`; `Dungeon.enchStoneNeeded`/
`labRoomNeeded` don't index anything and degrade gracefully). Left open on purpose: whether a shared
`Dungeon.region()` should read `CustomLevelLayout.getRegion()` for gameplay scaling — an undecided
product/API question, not needed to fix the crash).

**Closed 2026-08-10:** `cpdu-q0v` + `cpdu-h6p` clusters (Barkskin multi-source, save-version precheck), `cpdu-q7t` epic + its 4 subtasks (regeneration-pause consolidation), `cpdu-yaa` (both determinism layers), `cpdu-48j` + `cpdu-ijc` (Slice 1 Hero clusters), `cpdu-6lz` (port-verify built and validated both directions), `cpdu-5p6` (DM201 — verified faithful port, upstream has the same dead
`canVent` override), `cpdu-jm4` (Noisemaker — faithful port; unresolvable classes are dropped
not crashed, and an alias would have been actively wrong), `cpdu-c4w` (RNG sweep — 2 defects
fixed, rest verified safe), `cpdu-bnp` (8 shrinks triaged; 1 was a real regression, fixed),
`cpdu-bqr` (Mergiraf + difftastic), and the 12 `DefaultLocale` violations (all fixed, core's
lint baseline removed entirely).

---

## Log

### 2026-08-10 — gate repair, toolbelt research, CI

Full detail in [PROJECT-STATUS.md](PROJECT-STATUS.md). Summary:

**Verification infrastructure.** Three gates that had never worked were replaced or repaired,
and every new gate ships with a negative control proving it can fail.

- `services/tools/deletion-audit` — finds removals api-diff structurally cannot see (private
  members, statements dropped from a body whose signature never changed). First run over the
  Slice 1 range: 1,021 files, 35 deletions + 8 shrinks, 27 triaged as legitimate, 16 tracked.
- `services/tools/desktop-smoke` — boots the real jar to 120 rendered frames. The first
  automated proof in this project's history that the game starts. Replaces the Android
  emulator smoke, which had never executed the APK.
- `services/tools/manifest-audit` — seven checks over the manifests that define Slice 1's
  entire scope.
- `BundleAliasRoundtripTest` — pins the `Bundle.addAlias` resolution Slice 1's package moves
  depend on. It works.

**Static analysis, from zero.**

- `options.release = 8` on the Java 8 modules. Caught `BundleBridge`'s `java.util.List.of`
  (Android API 34) on a minSdk 19 app with no desugaring.
- Android Lint wired as a ratcheted gate over `core` and `android`. Found 12 `DefaultLocale`
  violations, 8 in `Generator.java`.

**First tests in `:core`.** `core:test` was `NO-SOURCE` across 1019 Java + 49 Kotlin files.

**CI, from nothing.** Every step asserts it analysed something.

**Upstream-sync tooling.** `git rerere` enabled, Mergiraf + difftastic installed and validated.

**Spec defects found while porting.** THREE of the five deferred-cluster beads worked
so far specified the wrong thing, each caught only by checking upstream rather than trusting
the spec: `cpdu-48j` said "edit Hero.java
only" but the localization key it depends on does not exist in CPDU, `cpdu-q7t.1` put the new helper on the wrong class
with the wrong signature, and `cpdu-u6u`'s prohibition reads as forbidding upstream's own fix.
All three would have compiled and looked correct. **Check every remaining deferred-cluster spec
against upstream before implementing it** — and note an autonomous run following these specs
literally would reproduce the mistakes, since the gates catch regressions but not a faithfully
implemented wrong spec.

**Defects fixed.** `List.of` on Android; same-seed nondeterminism in `WandOfCorruption` and
`AlchemicalCatalyst`; Forbidden Runes (`Challenges.NO_SCROLLS` was defined but never consulted).

**Research.** A 12-agent sweep across 10 tooling domains produced 94 candidates, triaged into
four adoption groups — see
[docs/superpowers/specs/2026-08-10-toolbelt-research.md](docs/superpowers/specs/2026-08-10-toolbelt-research.md).
Its most useful finding was a gap: ten domain researchers proposed *zero* tooling for the
541-commit upstream backlog, the project's largest recurring cost.

### 2026-07-21 → 2026-07-26 — Sub-A and Sub-B Slice 1

Sub-A (fork infrastructure) shipped: 7 commits, attribution, both build paths verified after a
four-layer build-baseline hotfix. Sub-B Slice 0 shipped the tooling foundation. Slice 1 landed
Tasks 11–16 and part of 17–20 before the OpenRouter pipeline halted on exhausted credits.

A 2026-07-25 audit found 730 commits carrying unreviewed `provisional:` reasons, revealing
Slice 1 to be roughly nine times its planned size. That triage has since been completed —
31 provisional rows remain.
