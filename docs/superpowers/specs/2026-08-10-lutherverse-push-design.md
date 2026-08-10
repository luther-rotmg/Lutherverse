# Lutherverse 2026-08-10 push — design

**Status:** approved by LO 2026-08-10
**Scope:** one multi-track work session, executed on frontier Claude rather than the OpenRouter bead pipeline
**Supersedes:** nothing. Extends the Sub-B slice plan at [2026-07-23-cpdu-sub-b-slice-1-catchup.md](../plans/2026-07-23-cpdu-sub-b-slice-1-catchup.md).

---

## Why this exists

An audit on 2026-08-10 found the project in a shape the roadmap did not describe.

Sub-A is shipped. Sub-B is stalled mid-Slice-1 of seven, with Tasks 11–16 landed,
Tasks 17–20 at 11 of 34 batches, and 22 beads parked on exhausted OpenRouter credit
rather than on any defect in their specs. Subs C through F have not started. Every
feature in the README vision — 200 floors, sphere grid, keyblades, towns, coop, the
story spine — has zero code behind it.

Two facts reframe that status.

**The scope is not what the plan says.** A 2026-07-25 manifest audit found 730 commits
sitting in the `cold-code` batch carrying unreviewed `provisional:` reasons from Task 6.
Machine triage classified 541 of them as unintegrated Slice 1 candidates, roughly nine
times Task 15's entire scope. Slices 2 through 7 draw their scope from that same
unreviewed manifest, so every downstream estimate inherits the error. PROJECT-STATUS
declines to publish a new number before a hunk-level review, and that refusal is correct.

**The gates were not gating.** The api-diff tool ran `git ls-tree` with no working
directory, inherited Gradle's subproject CWD, scanned zero files, and printed PASS. The
bead `validate:` gate never fired through Task 14 because the dispatcher parses
`validate:` only from `acceptance_criteria`/`notes`, requires it to begin its own line,
and requires a bare allowlisted first token — `.\gradlew.bat` failed on both counts.
`core:test` is `NO-SOURCE`, so compilation is the only mechanical gate a core-only change
ever faced. No save-roundtrip harness exists, and Slice 1 touched serialized state. The
Android runtime smoke has never executed the APK, because the emulator never reaches
`sys.boot_completed` and the run dies before `adb install`.

The first honest api-diff run, immediately after the fix in `4b9c83f6b`, caught a real
regression: a dropped `CorpseDust.actions()` override, restored in `874e49851`. One real
defect surfaced the moment one gate started working. There are 541 more commits queued
behind gates in that condition.

This push exists to make finishing Sub-B possible and trustworthy, not to finish it.

---

## Decisions taken

**D1 — Sub-B gets finished properly, not capped.** No Slice 1b, no permanent partial
parity. The full hunk-level review of the remaining buckets happens. Upstream SPD stays
a live source of content and fixes.

**D2 — Execution moves from the OpenRouter bead pipeline to frontier Claude.** Beads
remain the work ledger and the unit of scoping. Only the executor changes. OpenRouter
credit is available but held in reserve. Work that is mechanical and well-specified may
return to the cheap pipeline later; nothing in this design depends on that.

**D3 — Defold is rejected.** Evaluated and declined. Defold is a Lua-scripted, 2D-first
engine with strong HTML5 and mobile export. It shares no substrate with a Java/libGDX
codebase carrying CPD's marketplace-mod framework, Watabou's bundle serialization, and an
in-flight 1,209-commit upstream sync. Adopting it means a from-scratch rewrite in another
language that discards Sub-A, Sub-B, and the mod framework, and permanently severs the
upstream relationship that supplies content for free. GPLv3 follows the game logic and
assets regardless of engine, so no licensing benefit offsets that.

The impulse behind the question is kept. Web playability and multiplayer are the likely
real wants, and both are solvable inside libGDX. Neither is engine-constrained. They are
routed to T3 as vision subsystems.

**D4 — Nothing in the code lane merges on the old gate set.** T1 completes before T2 or
T5 land a commit.

---

## Architecture: two lanes, five tracks

The four selected work items are not peers. They separate by whether they touch source
files.

**Code lane** — sequential, shares one working tree:

    T1 gate repair  →  T2 ready-bead burndown  →  T5 Sub-B completion grind

**Design lane** — concurrent with the code lane, writes only to `docs/superpowers/specs/`:

    T3 vision decomposition  →  T4 Sub-C modding API design

The lanes run at the same time because the design lane produces no source edits. There is
no merge risk between them.

Two orderings are load-bearing rather than preference.

**T1 precedes T2 and T5.** Any task landed while the gates lie is a task that must be
re-verified later. That bill has been paid once already, at 14 tasks. Landing 541 more
commits under the same conditions repeats the mistake at forty times the scale.

**T3 precedes T4.** Sub-C is the modding API that Subs D, E and F sit on, and that every
vision feature sits on. Designing it before knowing what the sphere grid, cutscene engine,
town and quest state, and biome swapper demand of it yields a plausible API that fits none
of them. T3 produces the consumer list that T4 designs against.

---

## T1 — Gate repair

Four defects were identified. Two are already closed and need no work.

**Closed: the retroactive compile gate.** `core:compileJava --rerun-tasks` passed forced
in the Phase 4 gate run, which covers every task that closed while `validate:` was silently
skipped. Compilation-level regressions in already-landed work are ruled out.

**Closed: API-surface drift.** api-diff, once repaired, passed over 1,021 files with 21
removed, 160 added and 13 changed symbols, all accounted for on review.

Three defects remain open.

### T1.1 — Silent-deletion audit (new tool)

api-diff compares API *surface*. A behavior deletion inside an unchanged method signature
is invisible to it. The `CorpseDust.actions()` regression was caught only because it
happened to be an override, which changes the surface. A deletion inside a method body
would have passed every gate the project has.

Build `services/tools/deletion-audit`, mirroring the api-diff module layout
(`build.gradle` with the `application` and `java` plugins, JavaParser for parsing, JUnit 5
for tests, a `*Cli` main class, registered in `settings.gradle`).

Behavior: walk a given commit range, collect every removed line under `core/`, and flag
any removal that does not correspond to a matching deletion in the upstream commit the
integration claims to be porting. Output a reviewable report, exit non-zero on unexplained
removals.

This is the highest-value item in the track. It retro-audits everything already landed and
guards every commit still to come.

### T1.2 — Save-roundtrip harness

Slice 1 touched serialized state in two places. The `EntranceRoom`/`ExitRoom` package move
registered `Bundle.addAlias` entries. Terrain ID reuse gave `CUSTOM_DECO` the id 23
formerly held by `SIGN`.

The second is a silent save-corruption hazard. An old save's signs load as decorations,
with a clean compile, no exception, and no test to notice.

Build the harness in `SPD-classes`, which is where tests actually live. Capture binary save
fixtures from a pre-Slice-1 build, check them in, and assert field-level equality on
reload. The harness becomes a standing gate, not a one-off check.

### T1.3 — A runtime smoke that runs

The Android emulator never reaches `sys.boot_completed`, so `adb install` never fires and
the APK has never been executed in this environment. The existing check was only ever a
PID-alive test even when it did run.

Stop fighting the emulator. Add a desktop boot smoke that launches the JAR, asserts it
reaches the title screen, and exits. It is cheaper, more reliable, and exercises the same
core module. Android reverts to a manual pre-release check, documented as such.

### Explicitly out of scope for T1

Adding a test source set to `core`. It is a real gap and `core:test` stays vacuous until it
is addressed, but writing the first core tests is a project of its own. T1.1 covers more
risk per hour. Filed as a follow-up bead, not done today.

---

## T2 — Ready-bead burndown

27 beads are ready. All of them are frontier-judgment work that the cheap pipeline could
not do by construction: the multi-source Barkskin rewrite and its consumer migration, the
four-part regeneration-pause refactor, the Hero v2.3/v2.4/v2.5 deferred dependency
clusters, Provoked Anger, and Cached Rations, which needs a new `SupplyRation` class before
its `RegularLevel` hunk can stand alone.

A meaningful share of the open epics will resolve as supersession calls — CPDU already
solved the problem differently, and the correct outcome is to close as no-op with the
reasoning recorded. Group 15A already resolved this way. Two Task 15 beads were verified
no-ops. That is a normal and expected result, not a failure.

Two items need specific handling.

**`cpdu-0a7` (Forbidden Runes) is half-landed and uncommitted.** The working tree carries
edits to `Dungeon.java` and `randomGenUtils.kt` that hoist the `challenges` and `version`
initialisation above the `souLevels` computation so `isChallenged(NO_SCROLLS)` resolves
during level generation, plus a `halveQuantities` helper. The change reads correct. It goes
through the newly repaired gates and becomes the first commit that proves they work.

**`cpdu-15l` (conditional music) stays blocked on LO.** CPDU drives music from pack config
through `CustomLevel.playLevelMusic`. Upstream hardcodes tense and finale track selection in
level code. Landing upstream's form hardcodes what CPDU deliberately made configurable, and
would not apply to configured dungeons at all. It touches the frozen DSL. This is a
product-direction question about how much of the game is pack-configurable, and it grows
considerably once towns and biomes exist. It is deferred into T3 rather than decided here.

The earlier claim that the TENSE and FINALE `.ogg` assets were missing was wrong. All nine
landed on 2026-07-23 with the Task 9 music batches. Only the `Assets.Music` constants are
absent. The gap is a pipeline seam: the asset bead landed the files and the code bead
referencing them was deferred. The other Task 9 asset batches are worth auditing for the
same seam, filed as a follow-up.

---

## T3 — Vision decomposition

T3 does not produce a spec. It produces a decomposition.

The README vision is eleven independent subsystems presented as one goal:

1. Sphere-grid progression
2. Keyblade weapon type, Keybearer class, dual-wield reward system
3. Turn-based combat toggle
4. Save zones
5. Towns, quest system, narrative state
6. 200-floor generator and biome variety
7. Cosmic-horror biome pack
8. Character-cameo framework
9. Nestalgia-style turn-based coop
10. Leaderboards and labeled seed sharing
11. Story spine, cutscene and dialogue engine

Plus two routed in from D3: web/HTML5 playability, and real-time coop.

Each needs its own spec, plan and build cycle. T3 delivers a dependency graph across all
thirteen, a recommendation for which is the correct first subsystem to build, and — the
part T4 consumes — an explicit list of what each one demands of the modding API.

The `cpdu-15l` pack-configurability question is answered here, as a general policy on what
belongs in pack config versus level code, rather than one-off for music.

T3 is a child brainstorm and gets its own pass.

---

## T4 — Sub-C modding API design

A Java-hook API layered on CPD's JSON-manifest framework, covering cutscenes, dialogue,
dual-wield, story flags, biome swapping and NPC insertion.

T4 designs against T3's demand list. It is a child brainstorm and gets its own pass, and it
does not start before T3 delivers.

---

## T5 — Sub-B completion grind

Per D1, Sub-B is finished properly. This track starts today and runs well past it.

Remaining known scope: the 22 credit-blocked batches (~230 commits), the 541 triaged Slice 1
candidates, and the buckets the manifest still lists as `slice1` — `cold-code` at 3,041 rows,
`frontier-mixed` at 81, `assets-localization` at 50, `build-review` at 40, `platform-review`
at 27, plus residual hotspot rows. Slices 2 through 7 remain unestimated by design.

The `cold-code` bucket is the largest unknown in the project. It has never had a hunk-level
review, and it is the bucket the 730-row surprise came out of. Today's contribution to T5 is
the **review methodology**, not the review: a repeatable, documented procedure for taking a
manifest bucket to a genuinely reviewed state, validated against a sample. Machine triage has
now demonstrably failed twice — 60 quest-content commits leaked into Slice 1 across four tasks
because the marker scan reads added lines only, and commits that reference quest state without
naming a marker class slip through. The methodology must not repeat that.

No completion date is asserted. What is known is measured throughput: roughly 10 commits per
bead, 8 to 10 minutes per bead, one dispatcher at a time. Bead sizing tracks commit count and
file spread, not net LOC — a 434-line, 23-commit bead timed out, and a 509-line, 11-commit bead
spread across many enemy files timed out, while 10 to 12 commits over a narrow file set passes
first try. Heterogeneous buckets need about 4 commits.

---

## Verification

No commit in the code lane merges without all of:

| Gate | Command |
|---|---|
| Silent-deletion audit *(new)* | `deletion-audit --base 7d9c139c8 --head HEAD` |
| Save roundtrip *(new)* | `gradlew SPD-classes:test` |
| Core compile | `gradlew core:compileJava --rerun-tasks` |
| Tool tests | `gradlew :services:tools:{api-diff,pack-smoke,namespace-transform,deletion-audit}:test` |
| Desktop build | `gradlew desktop:release` |
| Android build | `gradlew android:assembleDebug` |
| API compatibility | `api-diff --base 7d9c139c8 --head HEAD`, reviewed |
| Marketplace pack smoke | `PackSmokeCli --marketplace ./marketplace`, 29/29 GREEN |
| Desktop boot smoke *(new)* | reaches title screen, exits clean |

Diff review at commit time follows the C-1 through C-9 checklist in
`~/.claude/pipeline/rules.md`. Every `-` line in every touched file is read and matched
against either a declared deletion or an acceptance bullet before commit.

`core:test` remains `NO-SOURCE` and is not cited as a gate anywhere. Android runtime smoke
is documented as a manual pre-release check, not an automated gate.

---

## Risks

**T5 has no credible end date.** This is stated rather than solved. The 3,041-row
`cold-code` bucket could shrink to near-nothing under review or could hold another 500
integration candidates. The methodology deliverable exists so the answer arrives from
measurement rather than from another estimate.

**T1.1 may surface a large backlog.** If the deletion audit finds many unexplained removals
across already-landed Slice 1 work, that becomes urgent repair work ahead of T2. That is the
tool succeeding, not failing, and the schedule absorbs it.

**Save fixtures require a pre-Slice-1 build.** Capturing them means building at a historical
ref. If that build cannot be produced, T1.2 falls back to synthetic fixtures constructed
against the old bundle format, which is weaker evidence and must be labelled as such.
