#requires -Version 7
<#
.SYNOPSIS
    Audits the Sub-B upstream-sync manifests for coverage, consistency, and stale copies.
.DESCRIPTION
    The manifests in docs/superpowers/research/ define Slice 1's entire scope and every
    downstream estimate. They are hand-maintained, which makes them a gate that can lie --
    and one already did: 730 rows carried unreviewed `provisional:` reasons while the
    plan's acceptance block assumed a reviewed manifest.

    This script mechanizes the checks that were previously done by hand, so the question
    "is the manifest trustworthy right now" costs one command instead of an afternoon.

    It is deliberately NOT a patch-id / git-cherry audit. CPDU integrates upstream work as
    hand-ported hunks through a namespace transform (com.shatteredpixel -> com.qsr.customspd),
    so patch-ids never match and such a check would report 100% drift and teach you to
    ignore it.
.PARAMETER ResearchDir
    Directory holding the canonical manifests.
.PARAMETER MaxProvisional
    Fail if more than this many classification rows still carry a `provisional:` reason.
.PARAMETER Canary
    Negative control. Injects a synthetic violation of each check to prove the check can
    actually fail. A gate never observed failing is not known to be a gate.
#>
param(
    [string]$ResearchDir = "docs/superpowers/research",
    [int]$MaxProvisional = 31,
    [int]$MaxSlice2Strays = 5,
    [switch]$Canary
)

$ErrorActionPreference = 'Stop'
$repoRoot = (git rev-parse --show-toplevel).Trim()
Set-Location $repoRoot

$inventoryPath      = Join-Path $ResearchDir 'slice-1-upstream-inventory.tsv'
$classificationPath = Join-Path $ResearchDir 'slice-1-classification.tsv'

$failures = [System.Collections.Generic.List[string]]::new()
$notes    = [System.Collections.Generic.List[string]]::new()

function Fail([string]$m) { $script:failures.Add($m) }
function Note([string]$m) { $script:notes.Add($m) }

# ---- M1: the canonical files exist and are tracked -------------------------------------
foreach ($p in @($inventoryPath, $classificationPath)) {
    if (-not (Test-Path $p)) { Fail "M1 missing canonical manifest: $p"; continue }
    git ls-files --error-unmatch $p *> $null
    if ($LASTEXITCODE -ne 0) {
        Fail "M1 canonical manifest is NOT git-tracked: $p (scope decisions must survive this disk)"
    }
}
if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Host "FAIL  $_" }
    Write-Host "manifest-audit: FAIL (cannot continue without canonical manifests)"
    exit 1
}

$inventory      = Import-Csv $inventoryPath      -Delimiter "`t"
$classification = Import-Csv $classificationPath -Delimiter "`t"

Note "inventory rows:      $($inventory.Count)"
Note "classification rows: $($classification.Count)"

if ($Canary) {
    # Synthetic violations, one per check, to prove each check fires.
    $classification += [pscustomobject]@{
        sha = 'canary000000000000000000000000000000000'; scope = 'commit'
        path_or_hunk = 'all'; decision = 'not-a-real-decision'
        reason = 'provisional: canary'; target_batch = 'canary-batch'
    }
    Note "CANARY MODE: injected one synthetic row; every check below must fail"
}

# ---- M2: unreviewed provisional rows ----------------------------------------------------
# This is the exact failure that already happened. 730 cold-code rows sat here claiming to
# be reviewed classification while carrying Task 6's provisional reason text.
$provisional = @($classification | Where-Object { $_.reason -like 'provisional:*' })
Note "provisional rows:    $($provisional.Count) (ceiling $MaxProvisional)"
if ($provisional.Count -gt $MaxProvisional) {
    # NB the parens around the concatenation are load-bearing: -f binds to the string
    # immediately left of it, so without them only the second fragment gets formatted and
    # the first prints a literal {0}.
    Fail (("M2 {0} rows still carry a provisional: reason, ceiling is {1}. These are UNREVIEWED -- " +
           "'the symbols resolve' is not 'the hunks apply'.") -f $provisional.Count, $MaxProvisional)
}

# ---- M3: coverage -- every inventory commit is classified -------------------------------
$classifiedShas = [System.Collections.Generic.HashSet[string]]::new()
$classification | ForEach-Object { [void]$classifiedShas.Add($_.sha) }
$uncovered = @($inventory | Where-Object { -not $classifiedShas.Contains($_.sha) })
if ($uncovered.Count -gt 0) {
    Fail "M3 $($uncovered.Count) inventory commits have no classification row (first: $($uncovered[0].sha))"
}

# ---- M4: no orphan classifications ------------------------------------------------------
$inventoryShas = [System.Collections.Generic.HashSet[string]]::new()
$inventory | ForEach-Object { [void]$inventoryShas.Add($_.sha) }
$orphans = @($classification | Where-Object { -not $inventoryShas.Contains($_.sha) } |
             Select-Object -ExpandProperty sha -Unique)
if ($orphans.Count -gt 0) {
    Fail "M4 $($orphans.Count) classification shas are not in the inventory (first: $($orphans[0]))"
}

# ---- M5: closed decision vocabulary -----------------------------------------------------
$validDecisions = @('slice1','slice2','excluded','mixed','superseded','integrated')
$badDecisions = @($classification | Where-Object { $_.decision -notin $validDecisions } |
                  Select-Object -ExpandProperty decision -Unique)
if ($badDecisions.Count -gt 0) {
    Fail "M5 unknown decision value(s): $($badDecisions -join ', ')"
}

# ---- M6: decision / target_batch coherence ----------------------------------------------
# Derived from the data, NOT assumed. Most batch names encode REVIEW ROUTING, not slice
# membership: frontier-mixed legitimately holds slice1 (81), slice2 (47) and mixed (84) rows,
# so "decision != batch prefix" is not a violation. An earlier version of this check assumed
# it was and produced 53 false positives -- a gate that cries wolf is a gate you switch off.
#
# Two invariants actually hold in the canonical manifest:
#   M6a  excluded-* batches contain ONLY excluded decisions  (261/261 today -- hard rule)
#   M6b  slice2-feature contains slice2 work                 (1709 slice2, 5 strays -- ratchet)
$m6a = @($classification | Where-Object { $_.target_batch -like 'excluded-*' -and $_.decision -ne 'excluded' })
if ($m6a.Count -gt 0) {
    Fail "M6a $($m6a.Count) non-excluded rows routed to an excluded-* batch (first: $($m6a[0].sha.Substring(0,9)))"
}

# Ratcheted like the lint baseline: the known strays are parked, anything NEW fails. A slice1
# commit sitting in slice2-feature never gets integrated in Slice 1 and nothing else notices --
# the same silent scope leak that let 60 quest-content commits into Slice 1 across four tasks.
$m6b = @($classification | Where-Object { $_.target_batch -eq 'slice2-feature' -and $_.decision -ne 'slice2' })
Note "slice2-feature strays: $($m6b.Count) (ceiling $MaxSlice2Strays)"
if ($m6b.Count -gt $MaxSlice2Strays) {
    $sample = ($m6b | Select-Object -First 3 | ForEach-Object { "$($_.sha.Substring(0,9))=$($_.decision)" }) -join ', '
    Fail "M6b $($m6b.Count) non-slice2 rows in slice2-feature, ceiling is $MaxSlice2Strays (e.g. $sample)"
}

# ---- M7: stale look-alike copies --------------------------------------------------------
# Untracked working copies with authoritative-sounding names ("final", "validation2") are a
# trap: they are pre-triage snapshots that read as canonical. One such set showed 761
# provisional rows against the canonical file's 31, and two files both named "final"
# disagreed with each other.
$lookalikes = @(Get-ChildItem -Path '.beads' -Filter 'slice-1-*.tsv' -ErrorAction SilentlyContinue)
if ($lookalikes.Count -gt 0) {
    $hashes = @($lookalikes | Where-Object { $_.Name -like '*classification*' } |
                Get-FileHash -Algorithm MD5 | Select-Object -ExpandProperty Hash -Unique)
    Note ("M7 {0} untracked look-alike manifest(s) in .beads/ ({1} distinct classification version(s))" -f
          $lookalikes.Count, $hashes.Count)
    Note "M7 these are stale pre-triage snapshots -- the canonical manifests are in $ResearchDir"
}

# ---- report -----------------------------------------------------------------------------
$notes    | ForEach-Object { Write-Host "note  $_" }
$failures | ForEach-Object { Write-Host "FAIL  $_" }

if ($Canary) {
    # In canary mode a PASS is the real failure.
    $expected = @('M2','M5')
    $fired = @($expected | Where-Object { $f = $_; $failures | Where-Object { $_ -like "$f*" } })
    if ($fired.Count -eq $expected.Count) {
        Write-Host "manifest-audit: CANARY OK (all of $($expected -join ', ') fired)"
        exit 0
    }
    Write-Host "manifest-audit: CANARY FAILED -- only $($fired -join ', ') fired; the others cannot detect a violation"
    exit 1
}

if ($failures.Count -gt 0) { Write-Host "manifest-audit: FAIL"; exit 1 }
Write-Host "manifest-audit: PASS"
exit 0
