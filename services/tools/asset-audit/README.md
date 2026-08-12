# asset-audit

Finds asset files that ship in the build but nothing can reference.

```
pwsh services/tools/asset-audit/asset-audit.ps1 [-MaxOrphanKb 9989] [-Canary]
```

Exit 0 within the ceiling, 1 above it, 2 if the scan itself is broken.

## Why

Catches the **assets-landed / code-deferred seam**: an asset batch lands the binary files
while the code batch that names them is deferred, so the files ride along in every APK and
JAR, unplayable and unlookable.

PROJECT-STATUS records this for the conditional-music cluster. A sweep on 2026-08-11
measured the actual cost for the first time.

## First run, 2026-08-11

2,159 assets, 20,474 KB total. **29 unreferenced, 9,989 KB.**

| Kind | Files | Size | Verdict |
|---|---|---|---|
| `.ogg` | 14 | **9,783 KB** | **Real waste.** ~54% of the game's music, in a 31 MB APK. |
| `.png` | 14 | 148 KB | Mostly intentional (see below). |
| `.ttf` | 1 | 58 KB | Unreviewed. |

**The audio is the finding.** Every orphan is a conditional-music track — `*_tense.ogg`,
`*_boss_finale.ogg`, `theme_finale.ogg`, and the `*_3.ogg` variants. The files landed with
the Task 9 music batches on 2026-07-23; the code that would name them is `cpdu-15l`, which is
deliberately blocked on a design decision about whether track selection belongs in pack config
or level code. So roughly **30% of the APK is audio nothing can currently play**, and it stays
that way until that decision is made.

**The PNGs are mostly not waste.** They are `REFERENCE_terrain_features.png`,
`REFERENCE_tiles_*.png` and similar — reference sheets shipped on purpose so pack authors can
see the tile layouts. Do not "clean these up" on this tool's say-so. Only `fireball.png` (5 KB)
looks like a genuine orphan.

## How it decides

An asset is referenced if its **file name appears anywhere** in core's Java/Kotlin sources or
in any JSON/properties file under `core/src/main`, `services` or `marketplace`.

That is deliberately loose. CPDU addresses many assets from pack config rather than from code,
so a checker that only looked at `Assets.java` would report a wall of false positives and get
switched off. The cost of the loose rule is that it cannot spot an asset referenced by a
constant that is itself never used — a second-order orphan.

## Two failure modes it guards against

- **Reading nothing.** If the source sweep collects under 10,000 characters the tool exits 2
  rather than declaring every asset an orphan. A checker that scanned nothing would otherwise
  report maximum waste and look authoritative.
- **Matching everything.** `-Canary` asks whether an impossible file name is referenced. If
  the matcher says yes, the tool fails rather than passing.

## The ceiling

`-MaxOrphanKb` is a ratchet, like the lint baselines and deletion-audit's `--max-findings`:
the known backlog is parked so anything **new** fails. The PASS message says *TRACKED, NOT
ACCEPTED* on purpose. Lower the ceiling when `cpdu-15l` is decided and the music is either
wired up or deleted.
