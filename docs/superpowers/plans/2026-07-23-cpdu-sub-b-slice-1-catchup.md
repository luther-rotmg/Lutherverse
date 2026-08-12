# Sub-B Slice 1: v2.1 → v2.5 engine and tuning catchup

**Date:** 2026-07-23
**Status:** execution plan
**Design source:** `docs/superpowers/specs/2026-07-22-cpdu-sub-b-upstream-sync-design.md`
**Previous slice:** Slice 0 at `a4519786780a4853fb0f7e36d097bf83b5ce08bf`

## Objective

Integrate the engine fixes and tuning changes from Shattered Pixel Dungeon
`v2.1.0..v2.5.4` into CPDU while preserving CPD's marketplace/modding behavior and
deferring the v2.5 feature layer to Slice 2.

This is not a tag merge. The 1,209-commit upstream range contains mixed commits
whose hunks span both Slice 1 engine/tuning work and Slice 2 features. Integration
must be driven by a reviewed classification manifest and normalized package paths.

## Locked refs and boundaries

- CPDU Slice 0 baseline: `a4519786780a4853fb0f7e36d097bf83b5ce08bf`
- CPD fork base: `c97fb832853b394da0e32940f1cb6a3c55750f99`
- SPD v2.1.0: `6067d21dae60db860a2cbf79b2d5b8f1a6386c35`
- SPD v2.5.4: `f5531fd65542f69ed4c17a5bdde03299e06a83f6`
- CPD incorporated v2.1.0 through merge:
  `3a633947ce7614af7a8a607fa92629f5de398ce7`
- SPD target after all Sub-B slices: `v3.3.8` / current upstream master

Slice 1 includes engine fixes, crash fixes, platform-neutral framework updates,
balance/tuning changes, and supporting assets needed by those changes.

Slice 1 excludes:

- Journal, bestiary, landmarks, and custom-notes catalog UI
- `WndUpgrade` confirmation feature
- Salt Cube, Vial of Blood, Shard of Oblivion, and Chaotic Censer
- Trinket energizing/refund behavior
- Cursed-wand rare-effect feature rework
- Cleric or any later-version content
- DSL v2 additions, iOS work, or Lutherverse-original content

Mixed commits are classified at file or hunk level. No worker may decide a mixed
commit's boundary implicitly while resolving a conflict.

## Allowed implementation patterns

- Namespace normalization copies the deterministic, bidirectional transformation
  contract promised by the Slice 0 design: package declarations/imports and the
  source path segment are transformed together; all other bytes are preserved.
- Upstream inspection uses immutable refs and read-only Git commands.
- Integration copies reviewed upstream hunks into CPDU paths after normalization.
- CPD-only marketplace, services, and modding code remains in place unless a
  reviewed manifest entry explicitly requires an adaptation.
- Existing project tools under `services/tools/` remain isolated from game code.

Do not:

- Merge `v2.5.4` directly into `main`.
- Cherry-pick the complete 1,209-commit range.
- Create a second `com.shatteredpixel.shatteredpixeldungeon` game tree.
- Introduce new modding DSL shapes, fields, or methods.
- Treat a clean textual merge as proof of semantic correctness.
- Commit or push from a worker.

## Phase 0 — Recover the missing foundation

### Task 1: Pin and record the Slice 0 comparison ref

Create an annotated local tag `cpdu-sub-b-slice-0` at the locked baseline after
confirming the object is `a451978...`. Do not move or overwrite an existing tag.

Verification:

- `git rev-parse cpdu-sub-b-slice-0^{}` prints the locked baseline SHA.
- The worktree remains clean.

### Task 2: Implement the bidirectional namespace transformer

Add a focused tool under `services/tools/` that transforms:

- path `com/shatteredpixel/shatteredpixeldungeon` ↔ `com/qsr/customspd`
- Java/Kotlin package declarations and imports using the same mapping

The tool must accept an input tree and output tree, refuse in-place writes, sort
traversal deterministically, preserve binary files byte-for-byte, preserve text
outside mapped namespace tokens byte-for-byte, and fail on destination collisions.

Verification:

- Unit tests cover both directions, nested paths, Java and Kotlin text, binaries,
  collision refusal, and a forward-then-reverse roundtrip.
- The module's Gradle tests pass.
- No game, marketplace, or asset file changes.

### Task 3: Harden API-diff identity and failure handling

Correct `DiffReport` member identity so overloads cannot overwrite each other and
make repository/blob read failures fail closed rather than masquerading as absent
files. Add regression tests before changing implementation.

Verification:

- Tests prove removal or signature change of one overload is reported.
- Tests prove a genuine Git/blob read error exits non-zero.
- Existing API-diff tests remain green.

### Task 4: Record gate capability honestly

Update the Slice 1 evidence section and tool documentation to distinguish:

- structural 29-pack manifest smoke from runtime pack boot
- PID-alive Android smoke from title-screen validation
- synthetic Bundle routing tests from a real 10-turn save roundtrip

This task does not weaken the final Sub-B design requirements. It prevents Slice 1
from claiming evidence the current tools cannot produce.

## Phase 1 — Build and approve the classification manifest

### Task 5: Generate the immutable upstream inventory

Produce `docs/superpowers/research/slice-1-upstream-inventory.tsv` containing, in
reverse chronological independence order:

- commit SHA
- release band
- subject
- touched paths
- provisional category

Release bands:

- v2.1.0 → v2.1.4: 80 commits
- v2.1.4 → v2.2.1: 295 commits
- v2.2.1 → v2.3.2: 228 commits
- v2.3.2 → v2.4.2: 238 commits
- v2.4.2 → v2.5.4: 368 commits

The generated file must account for all 1,209 commits exactly once.

### Task 6: Create the reviewed classification manifest

Create `docs/superpowers/research/slice-1-classification.tsv` with one or more rows
per upstream commit and these fields:

`sha`, `scope`, `path_or_hunk`, `decision`, `reason`, `target_batch`

Allowed decisions are `slice1`, `slice2`, `later`, `excluded`, and `mixed`.
Every `mixed` row must be followed by path/hunk rows that fully partition the
relevant diff. Ambiguous entries remain `needs-frontier-review` and cannot enter a
worker batch.

### Task 7: Frontier review the feature boundary

Sol reviews:

- every `mixed` entry
- every entry touching `Actor`, `Char`, `Hero`, `Dungeon`, `Level`, `Bundle`,
  settings, serialization, or build configuration
- every exclusion
- a sample from each remaining target batch

Verification:

- All 1,209 commits are accounted for.
- No `needs-frontier-review` entries remain.
- Slice 2 headline features are absent from Slice 1 batches.
- The manifest checksum is recorded in the plan execution log.

### Phase 1 execution log

- Inventory: 1,209 commits in the locked five release bands; SHA-256
  `5E2C48162D39E6D1B2A76B1BD801E88910FC474AC0BB993FF41C793BFC65FF19`.
- Frontier ledger: 448 reviews (411 required and 37 samples); SHA-256
  `119B943254154E82AC2B4DF7D207197E65F553D290123C4C8C9A2921A065DC9F`.
- Provisional classification: 6,107 data rows; SHA-256
  `18817F122941D7EB0986488C9D1EA459F7C9211CD3827B1203254E92C7536B50`.
- Final classification: 6,083 data rows, 1,209 ordered commit summaries,
  90 mixed partitions, and 1,427 explicit override details; SHA-256
  `8A160B5DA743E3794BCC1DB65926754BFD7F7696A056360453447BEB17142F63`.
- Independent validation confirmed complete mixed partitions, exact override
  evidence, reviewed-summary prefixes, and zero `needs-frontier-review` rows.

## Phase 2 — Integrate cold and framework batches

### Task 8: Import namespace-normalized Watabou cold zones

Integrate reviewed engine/framework changes under `SPD-classes` and Watabou
framework paths, excluding `Bundle` and any manifest-classified later work.

### Task 9: Integrate build-neutral assets and localization support

Import only assets and string infrastructure required by approved Slice 1 hunks.
Do not import Slice 2 feature strings or UI assets.

### Task 10: Integrate platform-neutral services and utilities

Apply approved utility/service changes without replacing CPD-specific marketplace
or update-service behavior.

Each integration task must:

- cite manifest rows in its bead spec
- remain within the bead sizing ceiling
- pass its focused compile/test command
- make no unrelated edits
- make no git commit or push

## Phase 3 — Integrate load-bearing game surfaces

The following are separate review units and may be split further to remain within
the sizing rubric:

### Task 11: Actor conflict resolution
### Task 12: Char conflict resolution
### Task 13: Hero conflict resolution
### Task 14: Dungeon global-state conflict resolution
### Task 15: Level and generator conflict resolution
### Task 16: Mob and combat-engine tuning
### Task 17: Item and equipment tuning
### Task 18: Buff, talent, and progression tuning
### Task 19: Scene and window engine adaptations
### Task 20: Remaining approved low-risk batches

For Tasks 11–20:

- Start from CPDU behavior and copy only manifest-approved upstream hunks.
- Preserve CPD hooks and namespace.
- Add focused tests for corrected bugs where a stable oracle exists.
- Record intentional deviations from SPD in the manifest reason field.
- Read every deletion during frontier review before accepting a bead.

## Phase 4 — Verification and closure

### Task 21: Compile and focused tests

Run with the configured JDK 17 and Android SDK:

```powershell
.\gradlew.bat SPD-classes:test :services:tools:api-diff:test `
  :services:tools:pack-smoke:test --no-daemon
.\gradlew.bat android:assembleDebug desktop:release --no-daemon
```

### Task 22: API compatibility audit

Run API diff from `cpdu-sub-b-slice-0` to Slice 1 HEAD. Review every reported
removal or signature change. Undocumented removals block closure.

### Task 23: Marketplace structural regression

Run `.\gradlew.bat packSmoke --no-daemon`. The current honest baseline is 29 pack
manifests green; `marketplace/Summary` is metadata, not a pack. Any regression
blocks closure.

### Task 24: Runtime and semantic smoke

- Boot the debug APK with `services/tools/smoke-boot/smoke-boot.ps1`.
- Confirm the process survives the existing 10-second PID gate.
- Manually confirm the title screen and start a seeded Warrior run.
- Exercise representative combat/tuning behavior and save/reload.
- Record which checks are manual and which are automated.

Slice 1 is not expected to populate a save translator. If manifest-approved work
touches serialized state, add the real fixture/roundtrip work before closure rather
than marking the conditional gate N/A.

### Task 25: Documentation, final review, and re-estimation

- Update `CHANGELOG.md`, `PROJECT-STATUS.md`, and the README roadmap/status drift.
- Record exact gate results and known tool limitations.
- Re-estimate Slices 2–7 from actual files, conflicts, elapsed work, and bead sizes.
- Review worker OUTPUT `DELETIONS`, `git diff --stat`, every touched-file diff, and
  every removed line before committing.
- Commit with LO authorship only and no AI attribution.

## Mandatory acceptance block

Status as of 2026-07-26. Slice 1 is **not closed** — Tasks 17–20 are partially
complete and the pipeline is halted on exhausted OpenRouter credits. These marks
record what is currently evidenced, not a closure claim.

- [x] Namespace transformer roundtrips both directions and refuses collisions.
      `:services:tools:namespace-transform:test` passes on a forced rerun.
- [x] Classification manifest accounts for all 1,209 upstream commits.
      Verified: 1,209 unique SHAs in the manifest, 1,209 commits in
      `v2.1.0..v2.5.4`. **Caveat:** accounting for a commit is not the same as
      reviewing it. 730 rows carried unreviewed `provisional:` reasons until the
      2026-07-25 triage, and 60 quest-content commits had to be reclassified out
      of Slice 1 across Tasks 15, 16, 19 and 20.
- [x] Slice 2/later feature hunks are absent from Slice 1.
      Leakage scans over the Task 15, 16 and 17–20 diffs find zero added-line
      references to `MiningLevel`, `GnollSapper`, `GnollGeomancer`,
      `CrystalSpire`, `CrystalGuardian`, `CrystalWisp`, `DarkGold`, `Pickaxe`,
      `SupplyRation`, `WndBlacksmithOld` or `Blacksmith.Quest.Type`.
- [x] `Actor.java`, `Char.java`, and `Hero.java` were separate reviewed units.
      Separate beads across Tasks 11–13, each frontier-reviewed.
- [x] Android and desktop builds exit 0. APK 32 MB, JAR 54 MB.
- [x] Focused tool and bridge tests exit 0. SPD-classes, api-diff, pack-smoke and
      namespace-transform all pass on forced reruns.
- [x] Android smoke evidence is recorded without overstating PID coverage.
      Recorded as **BLOCKED**: the emulator never reaches `sys.boot_completed`
      here, failing before `adb install`, so the APK is never exercised. The
      check was only ever a PID-alive gate even when it did run.
- [x] Pack smoke reports no regression from the 29-pack Slice 0 baseline.
      29/29 GREEN. The 30th `marketplace/` entry is `Summary/`, a metadata and
      screenshot directory with no `mod_info.json`, correctly not a pack.
- [x] API diff reports no undocumented removals/signature changes.
      1,021 files scanned: 21 removed, 160 added, 13 signature-changed, every one
      accounted for (11 the entrance/exit package move, 6 the upstream Noisemaker
      refactor, 2 relocations, 2 removed upstream too; 12 changes the `canMerge`
      migration, 1 `ShadowCaster.castShadow`). **This gate only became real
      today** — the tool previously scanned 0 files and printed PASS. Its first
      working run caught the `CorpseDust.actions()` regression.
- [ ] **PARTIAL — Serialized-state roundtrip is proven for the generic
      mechanism, not yet for the specific registrations.** Slice 1 changed
      serialized state two ways: terrain id reuse (`CUSTOM_DECO` takes the old
      `SIGN` id 23) and `Bundle.addAlias` registrations for the moved
      `EntranceRoom`/`ExitRoom`. Terrain id reuse is covered:
      `core/.../levels/TerrainIdTest.java` pins id 23 as a declared alias and
      fails on any undeclared collision. The addAlias *mechanism* is covered:
      `SPD-classes/.../BundleAliasRoundtripTest.java` (commit `9cea08968`)
      proves write/read roundtrip and legacy-classname resolution generically
      — but that test is explicit that it cannot reference `EntranceRoom` or
      `ExitRoom` (SPD-classes has no dependency on core), so it does not prove
      the two real registrations in `ShatteredPixelDungeon`'s constructor
      actually resolve. 2026-08-12: attempted to close this with a core-module
      test exercising the real classes; blocked mid-session by a Write-tool
      permission that disallows creating new files in this repo during this
      run (Edits to existing files were allowed). Still open: a core test
      source set that bundles a real `EntranceRoom`/`ExitRoom`, round-trips
      it, and separately confirms the legacy pre-move class names resolve via
      the actual registration call. Tracked as bead `cpdu-6io`.
- [x] No DSL surface additions, iOS work, or original Lutherverse content.
      `core/.../modding/` untouched; no `ios/` paths; no original content.
- [x] No unrelated edits and no worker commits/pushes.
      Every commit authored `LO`; nothing pushed — `origin/main` is unchanged.
- [x] CHANGELOG, PROJECT-STATUS, and README accurately report current state,
      including the blocked gates and the credit halt.
- [~] Remaining slices are re-estimated from Slice 1 empirical velocity.
      Throughput is measured and recorded (~10 commits per bead, ~8–10 min per
      bead, plus a corrected sizing rule based on commit count rather than LOC).
      Numeric estimates for Slices 2–7 are **deliberately withheld**: they would
      draw scope from the manifest that just proved unreliable, so a number now
      would repeat Slice 1's estimation error rather than correct it.

Missing evidence is a failed gate.
