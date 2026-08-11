# Project Progress

Running log of what has been done, what is in flight, and what is next. Updated in the same
commit as any substantive work.

For the deeper "why" behind current state, see [PROJECT-STATUS.md](PROJECT-STATUS.md).
For the release-facing change list, see [CHANGELOG.md](CHANGELOG.md).

**Last updated:** 2026-08-10

---

## Now

| Item | Status | Notes |
|---|---|---|
| **P0 — seeded runs** (`cpdu-yaa`) | 🟢 **fixed**, scope open | Both halves landed (right RNG + inside the pushed generator), with 8 tests and a negative control. *Same seed now reproduces the same dungeon layout.* Still open: `Level.mobs` is a `HashSet`, byte-identical to upstream, so *same seed reproduces the same run* does not yet hold — closing that means diverging from upstream and touching serialised state. |
| Sub-B Slice 1 remainder | 🟡 paused | 22 batches (~230 commits) parked; 27 ready beads |
| deletion-audit backlog | ✅ **zero** | 16 findings triaged against tag v2.5.4; 15 verified superseded, 1 was a real regression and is fixed. CI ceiling back to 0. |
| CI | ✅ **green** | Run 31446047082. Found and fixed 3 real issues on the way: gradlew exec bit, a Windows-only test assumption, and a malformed step. |

## Next

1. Decide the determinism guarantee: layout-only (done) vs whole-run (needs the `Level.mobs` call) (`cpdu-6lz`) — the design insight is that `services/tools/namespace-transform` is the missing link for comparing an upstream diff against its CPDU port.
3. Resume the Slice 1 batch burndown with Mergiraf in place.
4. `gdx-backend-headless` bootstrap spike — still needed for levelgen invariants (reachability, solvability), just no longer blocking the P0.
5. Sub-C modding API design — gated on a vision-decomposition pass that has not happened yet.

## Open defects

| ID | Pri | Summary |
|---|---|---|
| `cpdu-yaa` | P0→open | Layout determinism **fixed**; whole-run determinism still open pending the `Level.mobs` decision |

**Closed 2026-08-10:** `cpdu-6lz` (port-verify built and validated both directions), `cpdu-5p6` (DM201 — verified faithful port, upstream has the same dead
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
