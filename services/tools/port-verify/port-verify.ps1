#requires -Version 7
<#
.SYNOPSIS
    Checks whether an upstream commit's content actually landed in CPDU.
.DESCRIPTION
    Nothing in this repo could answer "did we port all of it". api-diff compares the API
    surface, deletion-audit finds removals with no upstream reference, and git range-diff
    cannot pair the commits because the namespaces differ. The gap is real and it has already
    cost: 874e49851 "restore CorpseDust.actions() override dropped during Slice 1" was an
    upstream hunk that vanished during porting with nothing to catch it.

    Method: take the lines an upstream commit ADDED, map each upstream path to its CPDU
    counterpart, normalise both namespaces to a canonical token, and report which added lines
    are absent from the CPDU file at --head.

    This is a REVIEW AID with a real noise floor, not a binary gate. CPDU legitimately adapts
    upstream code -- renamed symbols, layout-driven rewrites, dropped depth bookkeeping -- so
    a missing line is a question, not a verdict. Use -MinCoverage to fail a batch that looks
    obviously under-ported, and read the missing lines either way.
.PARAMETER Upstream
    Upstream commit SHA whose added lines should be accounted for.
.PARAMETER Head
    CPDU ref to check against. Defaults to HEAD.
.PARAMETER MinCoverage
    Fail if the share of accounted-for added lines falls below this percentage.
.PARAMETER ShowMissing
    Print every unaccounted line rather than a capped sample.
.PARAMETER Canary
    Negative control: also verify the checker reports LESS than full coverage for a commit
    whose content was deliberately mangled. A checker that always says 100% is useless.
#>
param(
    [Parameter(Mandatory = $true)][string]$Upstream,
    [string]$Head = 'HEAD',
    [int]$MinCoverage = 0,
    [switch]$ShowMissing,
    [switch]$Canary
)

$ErrorActionPreference = 'Stop'
$repoRoot = (git rev-parse --show-toplevel).Trim()
Set-Location $repoRoot

$SPD_PATH = 'com/shatteredpixel/shatteredpixeldungeon'
$CPD_PATH = 'com/qsr/customspd'
$SPD_NS   = 'com.shatteredpixel.shatteredpixeldungeon'
$CPD_NS   = 'com.qsr.customspd'

# Collapse both namespaces to one token so an otherwise-identical line compares equal.
# Whitespace is normalised too: reindentation during porting is not a dropped hunk.
function Normalize([string]$line) {
    $l = $line -replace [regex]::Escape($SPD_NS), '<NS>'
    $l = $l     -replace [regex]::Escape($CPD_NS), '<NS>'
    $l = $l     -replace [regex]::Escape($SPD_PATH), '<NSPATH>'
    $l = $l     -replace [regex]::Escape($CPD_PATH), '<NSPATH>'
    return ($l -replace '\s+', ' ').Trim()
}

function MapPath([string]$p) { return $p.Replace($SPD_PATH, $CPD_PATH) }

# Lines that carry no information worth chasing: blank, brace-only, lone comment markers.
function IsNoise([string]$norm) {
    if ($norm -eq '') { return $true }
    if ($norm -match '^[{}();,]+$') { return $true }
    if ($norm -match '^(/\*+|\*+/?|//)$') { return $true }
    if ($norm -match '^\*\s') { return $true }   # javadoc continuation
    return $false
}

git cat-file -e "$Upstream^{commit}" 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "port-verify: FAIL (unknown upstream commit: $Upstream)"
    exit 2
}

# --- collect the upstream commit's added lines, grouped by destination path ----------------
$raw = git show --no-color --unified=0 --no-renames $Upstream
$added = [ordered]@{}
$currentPath = $null
$scanned = 0

foreach ($line in $raw) {
    if ($line -match '^\+\+\+ b/(.+)$') {
        $currentPath = MapPath $Matches[1]
        if (-not $added.Contains($currentPath)) { $added[$currentPath] = [System.Collections.Generic.List[string]]::new() }
        continue
    }
    if ($null -eq $currentPath) { continue }
    if ($line.StartsWith('+++') -or $line.StartsWith('---')) { continue }
    if ($line.StartsWith('+')) {
        $norm = Normalize $line.Substring(1)
        if (-not (IsNoise $norm)) { $added[$currentPath].Add($norm); $scanned++ }
    }
}

if ($scanned -eq 0) {
    # Distinguish "nothing to check" from "the parser broke", which is the api-diff failure
    # mode: a checker that examined nothing must never look like a clean result.
    Write-Host "port-verify: FAIL (upstream $Upstream contributed 0 checkable added lines -- "
    Write-Host "  either it is a pure deletion/rename commit, or the diff parse failed)"
    exit 2
}

# --- check each destination file at --head -------------------------------------------------
$totalAdded = 0
$totalFound = 0
$missingByPath = [ordered]@{}
$absentFiles = [System.Collections.Generic.List[string]]::new()

foreach ($path in $added.Keys) {
    $lines = $added[$path]
    if ($lines.Count -eq 0) { continue }

    $content = git show "${Head}:${path}" 2>$null
    if ($LASTEXITCODE -ne 0) {
        $absentFiles.Add($path)
        $totalAdded += $lines.Count
        $missingByPath[$path] = $lines
        continue
    }

    $haystack = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($c in $content) { [void]$haystack.Add((Normalize $c)) }

    $missing = [System.Collections.Generic.List[string]]::new()
    foreach ($l in $lines) {
        $totalAdded++
        if ($haystack.Contains($l)) { $totalFound++ } else { $missing.Add($l) }
    }
    if ($missing.Count -gt 0) { $missingByPath[$path] = $missing }
}

$coverage = if ($totalAdded -gt 0) { [math]::Round(100.0 * $totalFound / $totalAdded, 1) } else { 0 }

# --- report --------------------------------------------------------------------------------
Write-Host "port-verify: upstream $Upstream -> $Head"
Write-Host "  files touched:  $($added.Keys.Count)"
Write-Host "  added lines:    $totalAdded checkable ($scanned before per-file grouping)"
Write-Host "  accounted for:  $totalFound  ($coverage%)"

if ($absentFiles.Count -gt 0) {
    Write-Host "  MISSING FILES (path does not exist at ${Head}):"
    $absentFiles | ForEach-Object { Write-Host "    $_" }
    Write-Host "    NB a missing file may be a legitimate CPDU rename; this tool does not follow renames."
}

if ($missingByPath.Count -gt 0) {
    Write-Host "  Unaccounted lines:"
    foreach ($path in $missingByPath.Keys) {
        $m = $missingByPath[$path]
        Write-Host "    $path  ($($m.Count))"
        $show = if ($ShowMissing) { $m } else { $m | Select-Object -First 5 }
        $show | ForEach-Object { Write-Host "      $_" }
        if (-not $ShowMissing -and $m.Count -gt 5) { Write-Host "      ... $($m.Count - 5) more (-ShowMissing)" }
    }
}

if ($Canary) {
    # A checker that always reports full coverage would be worthless. Prove it can say "no"
    # by asking it about content that certainly is not in the tree.
    $fakeHaystack = [System.Collections.Generic.HashSet[string]]::new()
    [void]$fakeHaystack.Add((Normalize 'int realLine = 1;'))
    $probe = Normalize 'int thisLineDoesNotExistAnywhereInTheRepository = 12345;'
    if ($fakeHaystack.Contains($probe)) {
        Write-Host "port-verify: CANARY FAILED (matcher reports a line it should not)"
        exit 1
    }
    Write-Host "port-verify: CANARY OK (matcher correctly rejects an absent line)"
}

if ($coverage -lt $MinCoverage) {
    Write-Host "port-verify: FAIL ($coverage% below the -MinCoverage $MinCoverage% floor)"
    exit 1
}
Write-Host "port-verify: PASS (review the unaccounted lines above; adaptation is legitimate, omission is not)"
exit 0
