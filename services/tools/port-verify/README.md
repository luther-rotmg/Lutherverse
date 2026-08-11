# port-verify

Answers the one question nothing else in this repo could: **did we port all of it?**

```
pwsh services/tools/port-verify/port-verify.ps1 -Upstream <sha> [-Head HEAD] \
    [-MinCoverage 90] [-ShowMissing] [-Canary]
```

Exit 0 on pass, 1 below the coverage floor, 2 on a bad commit or a parse that found nothing.

## Why

Three tools already exist and none of them can answer this:

| Tool | Answers | Blind to |
|---|---|---|
| api-diff | did the API surface change | private members, method bodies |
| deletion-audit | did anything vanish between two CPDU refs | whether upstream content ever arrived |
| `git range-diff` | did a rebased range drift | cannot pair the commits — namespaces differ |

The gap has already cost: `874e49851` "restore CorpseDust.actions() override dropped during
Slice 1" was an upstream hunk that vanished during porting, and nothing caught it until a
human noticed.

## Method

1. Take the lines the upstream commit **added**.
2. Map each upstream path to its CPDU counterpart
   (`com/shatteredpixel/shatteredpixeldungeon` → `com/qsr/customspd`).
3. Normalise both namespaces to a canonical token and collapse whitespace, so a correctly
   ported line compares equal despite the rename and any reindentation.
4. Report which added lines are absent from the CPDU file at `-Head`.

Noise lines — blank, brace-only, lone comment markers, javadoc continuations — are excluded
so the output is worth reading.

## This is a review aid, not a verdict

CPDU legitimately adapts upstream code: renamed symbols, layout-driven rewrites, dropped
depth/chapter bookkeeping. **A missing line is a question, not a defect.** Use `-MinCoverage`
to fail a batch that looks obviously under-ported, and read the unaccounted lines either way.

Two things it deliberately does not do:

- **No rename following.** A CPDU file that moved reports as a missing file. The output says
  so explicitly rather than implying the content was dropped.
- **No hunk or position matching.** A line found anywhere in the destination file counts as
  present. That trades precision for a signal that survives reordering.

## Validation performed

| Case | Expected | Actual |
|---|---|---|
| Positive control — a CPDU commit's own added lines vs HEAD | ~100% | **100%** (20/20) |
| Upstream commit touching `CrystalSpire.java`, which CPDU has never integrated | 0%, flagged as a missing file | **0%, correctly listed under MISSING FILES** |
| `-Canary` — matcher asked about a line that cannot exist | rejects it | **CANARY OK** |

A checker that always reports full coverage is worthless, which is what `-Canary` exists to
disprove. Run it whenever you are about to trust a green result.

## Reading a low score

Low coverage is not automatically bad. Check, in order:

1. **Is the commit even in scope?** A `decision=slice1` row in the manifest means "belongs in
   Slice 1", **not** "already integrated" — 22 batches are still unported, and those correctly
   score 0%.
2. **Was part of it deliberately deferred?** e.g. `f5531fd65` is classified `mixed`, with its
   translation bundles routed to Slice 2, so ~2% is the right answer.
3. **Did the file move in CPDU?** See the rename caveat above.
4. Only then treat it as a possible dropped hunk.
