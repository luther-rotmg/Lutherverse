# Lutherverse — working notes for Claude

A GPLv3 roguelike. Fork of Custom Pixel Dungeon, itself a fork of Shattered Pixel Dungeon.
Java 8 game code on libGDX, Gradle 8.5, AGP 7.4.2.

Read [project_progress.md](project_progress.md) for what is in flight, and
[PROJECT-STATUS.md](PROJECT-STATUS.md) for why the current state is what it is.

---

## Stage your work, or it is thrown away

`git add` everything you change, as you go.

The AFK supervisor commits **only what a task leaves staged** and logs `assumed` otherwise.
On 2026-08-11 a 140-minute run produced **1 commit across 69 tasks** because the sessions
edited files and never staged them. Staging is safe: the supervisor auto-unstages the user's
protected paths before committing, so `git add -A` cannot capture their in-progress work.

If you are not in an AFK run, commit normally — same result, same reason.

---

## The gate set

Nothing is "done" until these pass. Run them before you claim completion.

```bash
./gradlew.bat core:test SPD-classes:test :core:lint :android:lint core:compileJava --rerun-tasks desktop:release android:assembleDebug
```

Then, for anything touching `core/`:

```bash
./gradlew.bat :services:tools:deletion-audit:run --quiet --args="--base 7d9c139c8 --head HEAD --min-shrink 3 --allowlist services/tools/deletion-audit/reviewed-removals.txt"
./gradlew.bat :services:tools:content-audit:run --quiet --args="--allowlist services/tools/content-audit/reviewed-exceptions.txt --max-findings 189"
```

content-audit is currently **advisory, not blocking**: its M3/I3 registration checks over-report
because CPDU registers mobs data-driven/reflectively by name rather than by source token (a
follow-up bead tracks tuning the heuristic). Read its output, but do not treat a ceiling-exceed
as a merge blocker yet.

deletion-audit reads **git refs, not the working tree** — commit first or it audits stale
content. Same for `port-verify`.

Other tools, all under `services/tools/`, each with a README:

| Tool | Answers |
|---|---|
| `deletion-audit` | did anything quietly disappear between two refs |
| `content-audit` | is every core Mob/Item fully wired (sprite, localization, registration) |
| `port-verify` | did an upstream commit's content actually land |
| `manifest-audit` | can the Slice 1 scope manifests be trusted |
| `asset-audit` | do assets ship that nothing can reference |
| `desktop-smoke` | does the game actually boot (needs a display) |
| `api-diff`, `pack-smoke`, `namespace-transform` | surface diff, 29 mod packs, SPD↔CPD paths |

---

## Every gate must be provably able to fail

This repo's recurring failure is gates that pass without checking anything: api-diff scanned
zero files and printed PASS for weeks; a bead `validate:` gate never fired for ~14 tasks;
`core:test` was `NO-SOURCE`; Android Lint analysed 9 of 1077 files while looking clean.

So: when you add a gate, **prove it fails** on a deliberately broken input before trusting it,
and say so in the commit. Several tools ship a `-Canary` switch for exactly this. A green
result you have never seen go red is not evidence.

Corollary: prefer a **ratchet** to a baseline you stop reading. Park the known backlog behind
an explicit ceiling (`--max-findings`, `-MaxOrphanKb`, `DELIBERATE_ALIASES`) so anything *new*
fails, and keep the parked items visible in the output.

---

## Porting from upstream

Remotes: `upstream-spd` (Shattered), `upstream-cpd` (Custom PD). Slice 1's target is the tag
**`v2.5.4`** — compare against that, **not** `upstream-spd/master`, which is v3.3.8 and carries
later slices' work that reads as spurious divergence.

**Verify the bead spec against upstream before implementing it.** Three of six deferred-cluster
specs worked on 2026-08-10/11 were wrong — a localization key that does not exist in CPDU, a
helper specified on the wrong class with the wrong signature, and a prohibition that read as
forbidding upstream's own fix. All three would have compiled and passed every gate. The gates
catch regressions; they do not catch a faithfully implemented wrong spec.

**Port faithfully; do not improve.** If you spot a genuine upstream bug while porting, file a
bead rather than fixing it inline — an unrequested fix buried in a port is invisible divergence
the next porter has to untangle.

**Where CPDU legitimately diverges,** keep CPDU's form and say why in the commit:

- Buff icons are `Pair<Asset, Asset>`, not upstream's `int BuffIndicator`.
- Sprites, tiles and music come from **pack config**, not upstream's sheet indices.
- CPDU has no `Dungeon.branch`; its `levelName` layout graph generalizes depth+branch.
- CPDU keeps its own talent flavour where upstream renamed (e.g. `RESTORED_AGILITY`).

---

## Things that will bite you

**Java 8 in the game modules.** `core`, `SPD-classes`, `android`, `desktop` compile at Java 8
under `options.release = 8`. No `List.of`, `Set.of`, `var`, text blocks, switch expressions.
`services/tools/*` are Java 17 and may use all of it. The gate exists because `BundleBridge`
shipped `List.of` — Android API 34 — on a minSdk 19 app.

**Terrain IDs and `Bundle` class names are serialized state.** `Level.map` stores raw terrain
IDs; changing one reinterprets every existing save. Moving a `Bundlable` class needs
`Bundle.addAlias`. `TerrainIdTest` guards the ID table.

**Determinism.** Seeded runs must reproduce. Never feed an unordered collection to
`Random.chances`/`element` — `Class`, enum and object keys hash by identity and vary per JVM
run. Use `LinkedHashMap`/`LinkedHashSet`. Anything consuming RNG must run inside
`Dungeon.init`'s `Random.pushGenerator` block.

**`depth/5` region math** assumes upstream's linear 25 floors. Custom packs and the 200-floor
goal both exceed it; clamp array indices.

**Line endings** are pinned by `.gitattributes`. Do not fight it.

---

## Conventions

- Work is tracked in **beads** (`bd`), not TODO lists. `bd ready` to find work.
- No AI attribution in commit messages.
- Commit messages explain **why**, and state what was verified rather than asserting success.
- Dated docs under `docs/superpowers/` are historical records — do not rewrite them to match
  the present. `PROJECT-STATUS.md`'s header wins where an older entry contradicts it.
- `claude-afk-marathon` fires daily at **06:45** and will check this repo out onto its own
  branch. Do not fight it for the working tree.
