# CustomPixelDungeonUltimate Sub-A — Fork Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the public GitHub fork, wire local + remote infrastructure, publish GPLv3-compliant attribution, import the design + this plan into the fork, and prove the base game builds unchanged on Android + Desktop.

**Architecture:** Pure infrastructure sub-project — zero source-code changes to `core/` (that's Sub-B). All work is git remote wiring, documentation authoring, gradle-build verification, and one annotated tag to pin the fork base. Each task is independently reviewable and produces a deliverable that can be inspected via `git log`, `gh repo view`, or a build artifact.

**Tech Stack:** GitHub (via `gh` CLI, authenticated as `luther-rotmg`), git, Gradle (uses the fork's bundled `gradlew`), pwsh 7 for all shell commands, no test framework needed (verification is command-output-driven).

## Global Constraints

- **Commit authorship:** `Author: LO`. NO AI attribution, NO Co-Authored-By trailers, NO tool names in commit messages. (Enforced across all luther-rotmg repos per ~/.claude/CLAUDE.md.)
- **Shell:** pwsh 7. Never `powershell` (5.1). Never `cmd.exe`.
- **Fork base commit is pinned:** `c97fb83` from QuasiStellar/custom-pixel-dungeon (2025-08-15). All work in Sub-A is at this HEAD. Chasing CPD's HEAD is a v0.2+ concern.
- **License:** GPLv3. Retain CPD's `LICENSE.txt` unchanged. All new files inherit GPLv3.
- **Public display name for Sub-F throughout this repo:** "Bonfire Mode". Never write "Dark Souls" in any file that ships (trademark exposure per spec R4). This plan itself may reference the internal name for developer clarity but published files use "Bonfire Mode".
- **iOS:** on paper only. Do NOT build, do NOT test, DO document as unmaintained. `:ios` gradle target stays in `settings.gradle` untouched.
- **CI:** deferred to Sub-G. This plan does not create `.github/workflows/`.
- **No changes to `core/`, `android/`, `desktop/`, `ios/`, `services/`, `SPD-classes/`, or `marketplace/`.** Sub-A only touches root-level docs + adds `docs/superpowers/`. Any code-adjacent change is out of scope; if a task appears to require one, stop and escalate.
- **Repo visibility:** public from creation. Do NOT push a private-then-flip-public workflow (per stored feedback-github-visibility.md: never change repo visibility without explicit confirmation).
- **Push discipline:** Never push to `upstream-cpd` or `upstream-spd` (read-only remotes). Only push to `origin`.

## File Structure

New files created in the fork's working tree during Sub-A:

| Path | Task | Purpose |
|---|---|---|
| `README.md` | 3 | Rewritten from CPD's version. Fork-of-fork intro, install instructions per platform, iOS unmaintained disclosure, attribution links, design-doc link. |
| `THIRD_PARTY_NOTICES.md` | 3 | GPLv3 attribution chain (Evan → QSR → CPDU), libGDX + RoboVM + SPD-classes notices. |
| `docs/superpowers/specs/2026-07-21-custompixeldungeonultimate-design.md` | 4 | Full design doc imported from frontier scratchpad. |
| `docs/superpowers/plans/2026-07-21-cpdu-sub-a-fork-infrastructure.md` | 4 | This plan document imported from frontier scratchpad. |

Files touched (existing, retained or modified):

| Path | Task | Action |
|---|---|---|
| `LICENSE.txt` | 3 | Read + verify — GPLv3, retained unchanged from CPD. |
| `.gitignore` | — | Retained unchanged from CPD. |
| `settings.gradle` | 6 | Read + verify — `:ios` module reference retained unchanged. |
| `build.gradle`, `gradle.properties` | 6 | Read + verify — untouched. |

Files NOT created / touched by Sub-A (all deferred to later sub-projects):
- `.github/workflows/**` (Sub-G)
- Any file under `core/`, `android/`, `desktop/`, `ios/`, `services/`, `SPD-classes/`, `marketplace/` (Sub-B onward)
- `docs/modding-api-v1.md` (Sub-C)
- `docs/game-modes.md`, `docs/playtest/**` (Sub-D/E/F)

---

## Task 1: Fork on GitHub + local clone + wire three remotes

**Files:**
- Create (via `gh` + `git clone`): `C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\` (whole working tree from CPD)

**Interfaces:**
- Consumes: `gh` CLI authenticated as `luther-rotmg` (already verified in the brainstorming session)
- Produces: local repo at the target path with 3 remotes wired: `origin` (luther-rotmg fork), `upstream-cpd` (QuasiStellar), `upstream-spd` (00-Evan). HEAD at CPD commit `c97fb83` on branch `main` (renamed from CPD's inherited `margarita` per LO decision during Task 1 execution — CPD's actual default is `margarita`, not `master` as originally stated; `git branch -m` + `gh repo edit --default-branch main` + delete old ref + `git remote set-head` all completed inline).

- [ ] **Step 1: Verify target directory doesn't exist yet**

Run: `pwsh -NoProfile -Command "Test-Path 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'"`
Expected output: `False`

If output is `True`, stop and escalate — a collision means either a previous Sub-A attempt or an unrelated project used the name; do not overwrite.

- [ ] **Step 2: Create the public fork on GitHub under luther-rotmg**

Run: `pwsh -NoProfile -Command "gh repo fork QuasiStellar/custom-pixel-dungeon --clone=false --fork-name=CustomPixelDungeonUltimate --default-branch-only"`
Expected: exit 0, output contains `luther-rotmg/CustomPixelDungeonUltimate` and confirms the fork was created.

Notes on flags:
- `--clone=false` — we'll clone into the specific target path in step 3, not gh's default.
- `--fork-name=CustomPixelDungeonUltimate` — overrides the default `custom-pixel-dungeon` fork name.
- `--default-branch-only` — only fork the default branch (which is CPD's `margarita`, later renamed to `main` on our fork), skip other branches CPD may have (keeps our fork clean for the merge-in-slices strategy in Sub-B).

If the command reports the fork already exists, stop and escalate — a previous attempt shipped partially; do NOT overwrite an existing GitHub fork silently.

- [ ] **Step 3: Clone the new fork to the target local path**

Run: `pwsh -NoProfile -Command "git clone https://github.com/luther-rotmg/CustomPixelDungeonUltimate.git C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate"`
Expected: exit 0, `Cloning into '...' ... done.` No warnings about detached HEAD.

- [ ] **Step 4: Add upstream-cpd remote**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git remote add upstream-cpd https://github.com/QuasiStellar/custom-pixel-dungeon.git; git fetch upstream-cpd --tags"`
Expected: exit 0, fetch output shows commits + tags being pulled.

- [ ] **Step 5: Add upstream-spd remote**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git remote add upstream-spd https://github.com/00-Evan/shattered-pixel-dungeon.git; git fetch upstream-spd --tags"`
Expected: exit 0, fetch output pulls Evan's history + version tags (v3.x.x).

- [ ] **Step 6: Verify all three remotes and HEAD position**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git remote -v; Write-Host '---'; git log -1 --format='%H %s'"`
Expected: three remote pairs (origin, upstream-cpd, upstream-spd) each with (fetch) and (push) URLs; HEAD commit hash begins with `c97fb83` with message `Mod updates`.

If HEAD is NOT `c97fb83...`, stop and escalate — CPD may have pushed new commits since the brainstorming session; the fork-base pin decision needs re-review.

- [x] **Step 7: Commit — none needed for this task**

No git commit for Task 1. The local repo is in its clean-clone state; the pinning tag comes in Task 2. Confirm working tree is clean:

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git status --short"`
Expected: no output (working tree clean).

---

## Task 2: Pin fork base with annotated tag

**Files:**
- Create (in git): annotated tag `cpd-sync-base-2025-08-15` at HEAD

**Interfaces:**
- Consumes: HEAD at `c97fb83` (verified in Task 1 Step 6)
- Produces: pushable tag on `origin` that documents the exact CPD commit our Sub-B merge sits on top of.

- [ ] **Step 1: Verify still at c97fb83**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git rev-parse HEAD"`
Expected: `c97fb83...` (full 40-char hash starting with those 7 chars).

- [ ] **Step 2: Create the annotated tag with a rationale message**

Run:
```pwsh
Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'
git tag -a cpd-sync-base-2025-08-15 -m @'
Fork-base pin for CustomPixelDungeonUltimate.

This tag marks the QuasiStellar/custom-pixel-dungeon commit that
CustomPixelDungeonUltimate was forked from. Sub-B (upstream sync) merges
00-Evan/shattered-pixel-dungeon commits ONTO this base via the merge-
in-slices strategy defined in docs/superpowers/specs/2026-07-21-
custompixeldungeonultimate-design.md section 5.

Upstream CPD commit: c97fb83 "Mod updates" (2025-08-15)
Upstream SPD sync target: v3.3.8 (2026-03-19)

Do NOT delete this tag. It anchors the audit trail for Sub-B and any
future baseline-rewrite escalation (spec risk R1).
'@
```
Expected: exit 0, no output.

- [ ] **Step 3: Verify the tag exists locally and points at c97fb83**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git tag -l cpd-sync-base-2025-08-15; git rev-list -n 1 cpd-sync-base-2025-08-15"`
Expected output: `cpd-sync-base-2025-08-15` on the first line; `c97fb83...` (full hash) on the second.

- [ ] **Step 4: Push the tag to origin**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git push origin cpd-sync-base-2025-08-15"`
Expected: exit 0, `* [new tag] cpd-sync-base-2025-08-15 -> cpd-sync-base-2025-08-15`.

- [ ] **Step 5: Verify the tag on GitHub**

Run: `pwsh -NoProfile -Command "gh api repos/luther-rotmg/CustomPixelDungeonUltimate/git/refs/tags/cpd-sync-base-2025-08-15 --jq '.object.sha'"`
Expected: `c97fb83...` (full 40-char SHA matching Task 1 Step 6).

---

## Task 3: Publish attribution documents — README, LICENSE, THIRD_PARTY_NOTICES

**Files:**
- Modify: `C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\README.md` (rewrite from CPD's version)
- Read + verify: `C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\LICENSE.txt` (retained unchanged from CPD)
- Create: `C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\THIRD_PARTY_NOTICES.md`

**Interfaces:**
- Consumes: fork base from Task 1, `LICENSE.txt` inherited from CPD (GPLv3)
- Produces: GPLv3-compliant attribution surface visible to anyone landing on the GitHub repo page.

- [ ] **Step 1: Verify LICENSE.txt is CPD's GPLv3 (do not modify)**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; Get-Content LICENSE.txt -TotalCount 3"`
Expected: first 3 lines match the standard GPLv3 preamble beginning with `GNU GENERAL PUBLIC LICENSE` and `Version 3, 29 June 2007`.

If mismatch, stop and escalate — CPD's LICENSE.txt may have drifted from GPLv3 unexpectedly; GPLv3 compliance depends on this being correct.

- [ ] **Step 2: Overwrite README.md with the CPDU version**

Write file `C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\README.md` with exactly this content:

````markdown
# CustomPixelDungeonUltimate

**CustomPixelDungeonUltimate** (CPDU) is a public GPLv3 fork of [QuasiStellar's Custom Pixel Dungeon](https://github.com/QuasiStellar/custom-pixel-dungeon), itself a fork of [Evan Debenham's Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon).

CPDU takes Custom Pixel Dungeon current with upstream Shattered Pixel Dungeon and extends its modding framework with a broader Java-hook API. Shipped gameplay-mode addons include **God Mode**, **Hard Mode**, and **Bonfire Mode**.

## Status

Under active development. v0.1 release pending — see [`docs/superpowers/specs/`](docs/superpowers/specs/) for the roadmap.

## Attribution

Under the terms of the GNU General Public License v3, CustomPixelDungeonUltimate carries forward the full attribution chain:

- Base game: **Shattered Pixel Dungeon**, © Evan Debenham. Sourced from https://github.com/00-Evan/shattered-pixel-dungeon
- Modding framework: **Custom Pixel Dungeon**, © QuasiStellar. Sourced from https://github.com/QuasiStellar/custom-pixel-dungeon
- Predecessor: **Pixel Dungeon**, © Watabou

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for the full attribution chain and library notices.

## License

GPLv3. See [`LICENSE.txt`](LICENSE.txt).

## Platform support

| Platform | Status |
|---|---|
| Android | Supported — see build instructions below |
| Desktop (Windows / macOS / Linux) | Supported — see build instructions below |
| iOS | **Unmaintained.** The `:ios` Gradle module is retained in `settings.gradle` for future reactivation but is not built, tested, or shipped. |

## Building

### Prerequisites

- JDK 17+
- Android SDK (for the Android build)

### Android APK

```
./gradlew android:assembleDebug
```

Debug APK will be produced under `android/build/outputs/apk/debug/`.

### Desktop JAR

```
./gradlew desktop:release
```

Runnable JAR will be produced under `desktop/build/libs/`.

## Modding

The modding framework is currently the one Custom Pixel Dungeon ships. The extended Java-hook API is under active development — reference documentation will land at `docs/modding-api-v1.md` when Sub-C ships.

## Contributing

CustomPixelDungeonUltimate does not currently accept unsolicited pull requests. Issue reports and feature discussion are welcome.
````

- [ ] **Step 3: Write THIRD_PARTY_NOTICES.md**

Write file `C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\THIRD_PARTY_NOTICES.md` with exactly this content:

````markdown
# Third-Party Notices

CustomPixelDungeonUltimate is distributed under the GNU General Public License v3 (see [`LICENSE.txt`](LICENSE.txt)) and incorporates the following upstream projects. Each is listed with its own copyright and license terms.

---

## Shattered Pixel Dungeon

Copyright © 2014-2026 Evan Debenham

Licensed under the GNU General Public License, version 3 (GPLv3).

Source: https://github.com/00-Evan/shattered-pixel-dungeon

CustomPixelDungeonUltimate takes its base game logic — dungeons, mobs, items, heroes, level generation, save format, art, music, sound — from Shattered Pixel Dungeon. Any modification we make to files originating in Shattered Pixel Dungeon is itself GPLv3.

---

## Custom Pixel Dungeon

Copyright © 2021-2025 QuasiStellar

Licensed under the GNU General Public License, version 3 (GPLv3).

Source: https://github.com/QuasiStellar/custom-pixel-dungeon

CustomPixelDungeonUltimate is a fork of Custom Pixel Dungeon and takes its modding framework — `mod_info.json` manifest schema, marketplace mod-loader, per-mod resource overrides, hero JSON merge semantics — from it. Any modification we make to that framework, and any extension we add on top of it, is itself GPLv3.

---

## Pixel Dungeon (predecessor)

Copyright © 2012-2015 Oleg Dolya (Watabou)

Licensed under the GNU General Public License, version 3 (GPLv3).

Source: https://github.com/watabou/pixel-dungeon (archival)

Shattered Pixel Dungeon derives from Watabou's original Pixel Dungeon, and this attribution flows through the fork chain into CustomPixelDungeonUltimate.

---

## libGDX

Copyright © 2011-2026 See libGDX AUTHORS at https://github.com/libgdx/libgdx/blob/master/AUTHORS

Licensed under the Apache License, Version 2.0.

Source: https://github.com/libgdx/libgdx

libGDX provides the cross-platform game framework (rendering, input, audio, asset pipeline) that Shattered Pixel Dungeon runs on. Its Apache 2.0 license is compatible with GPLv3.

---

## RoboVM

Copyright © 2013-2026 RoboVM AB (via the community-maintained MobiVM fork)

Licensed under the GNU General Public License, version 2 with the Classpath Exception.

Source: https://github.com/MobiVM/robovm

The `:ios` Gradle module targets RoboVM/MobiVM. CustomPixelDungeonUltimate ships no iOS build — this notice is retained for accuracy of the codebase inheritance.

---

## SPD-classes

The `SPD-classes` module is Evan Debenham's shared-utility layer for the Shattered Pixel Dungeon family of projects. Copyright and license as Shattered Pixel Dungeon above (GPLv3).
````

- [ ] **Step 4: Stage and commit the documentation triple**

Run:
```pwsh
Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'
git add README.md THIRD_PARTY_NOTICES.md
git status --short
```
Expected output from `git status --short`:
```
 M README.md
A  THIRD_PARTY_NOTICES.md
```
(exact staging notation may show as `M` for the modified README and `A` for the new THIRD_PARTY_NOTICES; both should appear in green if terminal color is on)

Then commit:
```pwsh
git commit -m "docs: rewrite README + add THIRD_PARTY_NOTICES for CPDU fork

Rewrites README.md from CPD's version to document CustomPixelDungeon-
Ultimate as a fork-of-fork of QuasiStellar/custom-pixel-dungeon and
00-Evan/shattered-pixel-dungeon. Adds THIRD_PARTY_NOTICES.md with the
GPLv3 attribution chain (Evan -> QSR -> CPDU), libGDX Apache 2.0
notice, RoboVM/MobiVM GPLv2-with-Classpath-Exception notice, and
SPD-classes attribution.

LICENSE.txt is retained unchanged from CPD (GPLv3).

Platform status: Android + Desktop supported; iOS deliberately
unmaintained (see :ios Gradle target retained but never built/tested)."
```
Expected: exit 0, `[main <hash>] docs: rewrite README + add THIRD_PARTY_NOTICES for CPDU fork` with `2 files changed`.

- [ ] **Step 5: Verify commit authorship (LO, no AI attribution)**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git log -1 --format='%an%n%ae%n%b' HEAD"`
Expected:
- First line: `LO` (or whatever `user.name` resolves to for luther-rotmg per global git config)
- Second line: `ryan.duke360@gmail.com` (or the account email)
- Body: NO `Co-Authored-By:` trailer, NO tool-name mentions

If the body contains any AI/tool attribution, stop, run `git commit --amend` to strip it, and re-verify.

---

## Task 4: Import design + plan into fork's docs/superpowers/ tree

**Files:**
- Create: `C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\docs\superpowers\specs\2026-07-21-custompixeldungeonultimate-design.md`
- Create: `C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\docs\superpowers\plans\2026-07-21-cpdu-sub-a-fork-infrastructure.md`

**Interfaces:**
- Consumes: the two authored documents living in the frontier scratchpad at `C:\Users\minec\AppData\Local\Temp\claude\c--Users-minec-Documents-Projects-VSCode\be0d765b-b8c0-43a5-b81e-d3c5a546d773\scratchpad\`
- Produces: `docs/superpowers/` directory structure populated with the design + Sub-A plan, ready for future sub-project specs/plans to land alongside.

- [ ] **Step 1: Create the docs/superpowers/specs/ + docs/superpowers/plans/ directories**

Run:
```pwsh
Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'
New-Item -ItemType Directory -Force -Path 'docs\superpowers\specs' | Out-Null
New-Item -ItemType Directory -Force -Path 'docs\superpowers\plans' | Out-Null
Test-Path 'docs\superpowers\specs'
Test-Path 'docs\superpowers\plans'
```
Expected: both `Test-Path` calls return `True`.

- [ ] **Step 2: Copy the design doc from scratchpad**

Run:
```pwsh
Copy-Item `
  'C:\Users\minec\AppData\Local\Temp\claude\c--Users-minec-Documents-Projects-VSCode\be0d765b-b8c0-43a5-b81e-d3c5a546d773\scratchpad\2026-07-21-custompixeldungeonultimate-design.md' `
  'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\docs\superpowers\specs\2026-07-21-custompixeldungeonultimate-design.md'
```
Expected: exit 0, no output. Verify:

```pwsh
Get-Item 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\docs\superpowers\specs\2026-07-21-custompixeldungeonultimate-design.md' | Select-Object Length
```
Expected: `Length` > 15000 bytes (the design doc is substantial).

- [ ] **Step 3: Copy the Sub-A plan (this file) from scratchpad**

Run:
```pwsh
Copy-Item `
  'C:\Users\minec\AppData\Local\Temp\claude\c--Users-minec-Documents-Projects-VSCode\be0d765b-b8c0-43a5-b81e-d3c5a546d773\scratchpad\2026-07-21-cpdu-sub-a-fork-infrastructure.md' `
  'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\docs\superpowers\plans\2026-07-21-cpdu-sub-a-fork-infrastructure.md'
```
Expected: exit 0. Verify:

```pwsh
Get-Item 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\docs\superpowers\plans\2026-07-21-cpdu-sub-a-fork-infrastructure.md' | Select-Object Length
```
Expected: `Length` > 8000 bytes.

- [ ] **Step 4: Stage and commit the imported docs**

Run:
```pwsh
Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'
git add docs/superpowers/specs/2026-07-21-custompixeldungeonultimate-design.md docs/superpowers/plans/2026-07-21-cpdu-sub-a-fork-infrastructure.md
git status --short
```
Expected: two `A` (added) entries under `docs/superpowers/`.

Then commit:
```pwsh
git commit -m "docs: import CPDU design spec + Sub-A plan into fork

Imports the frontier-authored design spec (docs/superpowers/specs/
2026-07-21-custompixeldungeonultimate-design.md) and this sub-project's
implementation plan (docs/superpowers/plans/2026-07-21-cpdu-sub-a-fork-
infrastructure.md) into the fork.

The design spec captures six locked scope decisions and the six-sub-
project decomposition (A: fork infra, B: upstream sync, C: broad
modding platform API, D-F: God/Hard/Bonfire mode addons) targeting a
v0.1 release. Sub-A is this sub-project.

Future sub-projects land their own specs + plans under the same
docs/superpowers/ tree."
```
Expected: exit 0, `[main <hash>] docs: import CPDU design spec + Sub-A plan into fork` with `2 files changed`.

---

## Task 5: Verify Gradle Android + Desktop builds pass unchanged

**Files:**
- Read-only inspection of `settings.gradle`, `build.gradle`, `gradle.properties`
- Produced (transient): `android/build/outputs/apk/debug/*.apk`, `desktop/build/libs/*.jar`

**Interfaces:**
- Consumes: gradlew (already checked in from CPD), JDK 17+, Android SDK
- Produces: proof that Sub-A's documentation-only changes did not accidentally break the base game build.

- [ ] **Step 1: Verify settings.gradle module list is untouched**

Run:
```pwsh
Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'
Get-Content settings.gradle
```
Expected output: identical to CPD's settings.gradle, which is:
```
//core game code modules
include ':SPD-classes'
include ':core'

//platform modules
include ':android'
include ':ios'
include ':desktop'

//service modules
include ':services'
    //updates
    include ':services:updates:debugUpdates'
    include ':services:updates:githubUpdates'
    //news
    include ':services:news:debugNews'
    include ':services:news:shatteredNews'
```

If any module is missing or added, stop and escalate — Sub-A must not touch the module list.

- [ ] **Step 2: Run Android debug assembly**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; ./gradlew android:assembleDebug --console=plain"`
Expected: exit 0, final line contains `BUILD SUCCESSFUL`.

Verify APK produced:
```pwsh
Get-ChildItem 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\android\build\outputs\apk\debug\' -Filter '*.apk'
```
Expected: at least one `.apk` file listed.

If BUILD FAILED, save the full gradle output to a scratchpad log and stop; do not commit anything, do not proceed to Task 6. Escalate with the error.

- [ ] **Step 3: Run Desktop dist**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; ./gradlew desktop:release --console=plain"`
Expected: exit 0, final line contains `BUILD SUCCESSFUL`.

Verify JAR produced:
```pwsh
Get-ChildItem 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\desktop\build\libs\' -Filter '*.jar'
```
Expected: at least one `.jar` file listed.

If BUILD FAILED, save the full gradle output and stop.

- [ ] **Step 4: Confirm iOS was NOT built (matches iOS-on-paper decision)**

Run: `pwsh -NoProfile -Command "Test-Path 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate\ios\build'"`
Expected: `False`.

If `True`, some earlier step accidentally triggered iOS work — escalate.

- [ ] **Step 5: No commit — verification only**

The build outputs are intentionally not committed (they're in `.gitignore`-covered `build/` directories inherited from CPD). Confirm no accidental staging:

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git status --short"`
Expected: no output.

---

## Task 6: Push commits + tag to origin + verify public GitHub state

**Files:**
- No local file changes
- Produced (on GitHub): commits from Tasks 3 + 4 visible on `luther-rotmg/CustomPixelDungeonUltimate` main; tag from Task 2 already pushed in Task 2 Step 4. Note: Task 1 already pushed `main` to origin as part of the margarita→main rename addendum — so this task's Step 3 push will only push the two new commits (Tasks 3, 4), not create the branch.

**Interfaces:**
- Consumes: local repo with 2 new commits (Task 3, Task 4) not yet pushed to origin; tag `cpd-sync-base-2025-08-15` already pushed in Task 2
- Produces: fully synchronized public repo. Anyone visiting https://github.com/luther-rotmg/CustomPixelDungeonUltimate sees the CPDU README + attribution + design doc.

- [ ] **Step 1: Confirm the two pending local commits are ahead of origin**

Run:
```pwsh
Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'
git status
```
Expected output includes: `Your branch is ahead of 'origin/main' by 2 commits.` (Task 3's docs commit + Task 4's design/plan import commit)

- [ ] **Step 2: Review the two commits about to be pushed**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git log origin/main..HEAD --format='%h %an  %s'"`
Expected: exactly two commits shown, both authored by `LO`, both with `docs:` prefix. If any commit shows a different author or an unexpected message prefix, stop and escalate.

- [ ] **Step 3: Push main to origin**

Run: `pwsh -NoProfile -Command "Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'; git push origin main"`
Expected: exit 0, output shows `To https://github.com/luther-rotmg/CustomPixelDungeonUltimate.git` with `main -> main`.

- [ ] **Step 4: Verify the public repo shows the CPDU README**

Run: `pwsh -NoProfile -Command "gh api repos/luther-rotmg/CustomPixelDungeonUltimate/readme --jq '.content' | ForEach-Object { [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($_)) } | Select-Object -First 3"`
Expected: first 3 lines of the CPDU README, beginning with `# CustomPixelDungeonUltimate`.

If the README fetched from GitHub is CPD's original (`# Custom Pixel Dungeon`), the push didn't land — investigate.

- [ ] **Step 5: Verify repo is public**

Run: `pwsh -NoProfile -Command "gh api repos/luther-rotmg/CustomPixelDungeonUltimate --jq '.visibility'"`
Expected: `public`.

If `private`, the fork was created with the wrong visibility; escalate (do NOT unilaterally flip visibility per stored feedback-github-visibility.md; ask LO).

- [ ] **Step 6: Verify tag is discoverable on GitHub**

Run: `pwsh -NoProfile -Command "gh api repos/luther-rotmg/CustomPixelDungeonUltimate/tags --jq '.[].name'"`
Expected output contains `cpd-sync-base-2025-08-15`.

- [ ] **Step 7: Print the final Sub-A completion summary**

Run:
```pwsh
Set-Location 'C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate'
Write-Host '=== Sub-A complete ==='
Write-Host ('Fork:     https://github.com/luther-rotmg/CustomPixelDungeonUltimate')
Write-Host ('Local:    C:\Users\minec\Documents\Projects\CustomPixelDungeonUltimate')
Write-Host ('Base:     ' + (git rev-parse HEAD~2) + ' (upstream CPD c97fb83, pinned as cpd-sync-base-2025-08-15)')
Write-Host ('HEAD:     ' + (git rev-parse HEAD))
Write-Host '---'
Write-Host 'Remotes:'
git remote -v
Write-Host '---'
Write-Host 'Local commits since fork:'
git log --format='  %h %an  %s' HEAD~2..HEAD
Write-Host '---'
Write-Host 'Next: Sub-B (upstream sync) plan authoring by frontier.'
```

Expected: the summary block prints. This is not a commit — it's the human-readable "Sub-A shipped" signal for review.

---

## Sub-A completion criteria

All checkboxes above ticked, and:

- `https://github.com/luther-rotmg/CustomPixelDungeonUltimate` exists and is public
- Repo README is the CPDU one (not CPD's original)
- Local repo has 3 remotes wired
- Tag `cpd-sync-base-2025-08-15` exists locally and on GitHub, points at `c97fb83`
- Android APK + Desktop JAR both build cleanly on the current fork base
- iOS was not built (no `ios/build/` directory)
- Two commits (docs triple + design/plan import) authored by LO with no AI attribution
- Design doc + Sub-A plan visible under the fork's `docs/superpowers/{specs,plans}/`

## Deliberate non-goals (do NOT do these in Sub-A)

- Do NOT create `.github/workflows/` — that's Sub-G, deferred to v0.2+
- Do NOT touch anything under `core/`, `SPD-classes/`, `android/`, `desktop/`, `ios/`, `services/`, `marketplace/`
- Do NOT `git merge upstream-spd/master` or `git merge upstream-cpd/margarita` — that's Sub-B
- Do NOT add a `docs/modding-api-v1.md` — that's Sub-C's output
- Do NOT test iOS build (matches iOS-on-paper spec decision)
- Do NOT invoke any beads / OpenRouter dispatch — Sub-A is frontier-inline infrastructure work; also the standing constraint is zero OpenRouter dispatch until LO refills the budget
