# manifest-audit

Checks whether the Sub-B upstream-sync manifests can be trusted right now.

```
pwsh services/tools/manifest-audit/manifest-audit.ps1
pwsh services/tools/manifest-audit/manifest-audit.ps1 -Canary   # prove the checks can fail
```

Exit 0 clean, 1 on failure.

## Why

`docs/superpowers/research/slice-1-classification.tsv` and `slice-1-upstream-inventory.tsv`
define Slice 1's entire scope, and every Slice 2–7 estimate inherits from them. They are
hand-maintained, which makes them a gate that can lie — and one already did: 730 rows carried
unreviewed `provisional:` reasons from Task 6 while the plan's acceptance block assumed a
reviewed manifest.

This turns "is the manifest trustworthy" into one command.

## Checks

| ID | Check | State on 2026-08-10 |
|---|---|---|
| M1 | Canonical manifests exist and are git-tracked | PASS |
| M2 | Rows still carrying a `provisional:` reason ≤ ceiling | 31 (was 761 pre-triage) |
| M3 | Every inventory commit has a classification row | PASS, 0 uncovered |
| M4 | No classification sha missing from the inventory | PASS, 0 orphans |
| M5 | `decision` drawn from a closed vocabulary | PASS |
| M6a | `excluded-*` batches hold only `excluded` decisions | PASS, 261/261 |
| M6b | `slice2-feature` holds slice2 work | 5 strays, ratcheted |
| M7 | Reports stale look-alike manifests (advisory) | 14 found in `.beads/` |

M2 and M6b are **ratchets**, like the lint baselines: the known backlog is parked by a ceiling
so anything new fails. Lower the ceilings as the backlog is worked down.

## Two traps this encodes

**Stale look-alikes (M7).** `.beads/` holds 14 untracked `slice-1-*.tsv` working copies with
authoritative-sounding names — `final.sol3`, `final.validation2`. They are *pre-triage*
snapshots showing 761 provisional rows against the canonical file's 31, and the five files
named `final` split into **two mutually contradictory versions**. They read as canonical and
are not. The canonical manifests are the git-tracked ones in `docs/superpowers/research/`.

**Batch names encode routing, not slice membership.** `frontier-mixed` legitimately holds
slice1, slice2 and mixed rows. An earlier version of M6 assumed batch prefix implied decision
and produced 53 false positives. A gate that cries wolf gets switched off, which is worse than
no gate — every rule here was derived from the data, not assumed.

## What this deliberately does not do

No `git patch-id` / `git cherry` drift audit. CPDU integrates upstream work as hand-ported
hunks through a namespace transform (`com.shatteredpixel` → `com.qsr.customspd`), so patch-ids
never match. Such a check would report 100% drift on correctly-integrated work and train you
to ignore it.

Verifying that a *specific* port kept every upstream hunk is a different job, better served by
`git range-diff` against the upstream commit — that is what would have caught the dropped
`CorpseDust.actions()` override.
