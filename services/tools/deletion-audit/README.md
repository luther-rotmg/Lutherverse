# deletion-audit

Answers the question api-diff structurally cannot: **did any code quietly disappear?**

api-diff compares the public/protected *declaration surface* between two refs. It is
blind to private members, and blind to statements removed from inside a method whose
signature never changed. The `CorpseDust.actions()` regression was caught only because
it happened to be a visible override; the same deletion one line inside the body would
have passed every gate this project had.

## Usage

```
./gradlew :services:tools:deletion-audit:run --args="\
  --base <ref> --head <ref> \
  [--files 'core/src/main/java/**/*.java'] \
  [--min-shrink 3] \
  [--allowlist services/tools/deletion-audit/reviewed-removals.txt]"
```

Exits 0 when clean, 1 when unreviewed removals remain, 2 on bad arguments or a
missing allowlist file.

## Reading the output

- `DELETED` — a callable present at base and absent at head.
- `SHRUNK` — a callable that kept its signature but whose body lost `--min-shrink`
  or more statements.

Both are a **review queue, not a verdict**. Integration work legitimately removes
CPDU code. Triage each entry, then either restore the behavior or record the key in
`reviewed-removals.txt` with a reason.

Expect these legitimate patterns to show up as findings:

| Pattern | Appears as | Example from the first run |
|---|---|---|
| Signature migration | `DELETED` at the old arity | `canMerge(Level, Point, int)` → 4-arg form, 12 entries |
| Parameter added | `DELETED` | `ShadowCaster#castShadow` gained a width param |
| File moved | `DELETED` for every callable | `EntranceRoom`/`ExitRoom` package move, 11 entries |
| Extract method | `SHRUNK` | `Wraith#spawnAt(int, boolean)` 24 → 2, body moved to a 3-arg overload |

The moved-file case is a **tool limitation**: there is no rename detection yet, so a
moved file reports all of its callables as deleted. Tracked as a follow-up bead.

## First run, 2026-08-10

Base `7d9c139c8` (Slice 1 start) → HEAD, 1,021 files scanned: **35 deleted, 8 shrunk**.
Triage classified 27 as legitimate (allowlisted with reasons) and left **16 open review
items**, of which the two most significant are:

- **`Noisemaker.Trigger`** — the whole `Bundlable` inner class removed. If there is no
  save migration, old saves carrying one fail to restore.
- **`DM201#act()`** — vent logic moved to a `Hunting` state class, but the old
  `canVent(enemy.pos)` and non-adjacency guards did not move with it, leaving
  `DM201.canVent` overridden and never called. Invisible to api-diff, since `canVent`
  itself still exists.

## Gotchas

Two failure modes, both already hit once in this repo:

- **`Files scanned: 0` means the tool is lying, not that the code is clean.** git
  invocations must be anchored to the repository root via `GitCommands.repoRoot()`,
  or they inherit Gradle's subproject working directory and scan nothing. This is
  exactly how api-diff printed PASS for weeks.
- **A silently empty allowlist looks like a clean triage that found nothing.** The
  first real run hit this: `gradle run` sets the CWD to the subproject, so the
  repo-root-relative allowlist path did not exist and loaded empty. Relative paths
  now resolve against the repo root, and a missing allowlist exits 2 rather than
  permitting nothing.
