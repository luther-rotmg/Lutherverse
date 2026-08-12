#requires -Version 7
<#
.SYNOPSIS
    Finds asset files that ship in the build but nothing can reference.
.DESCRIPTION
    Catches the "assets landed, code deferred" seam: an asset batch lands the binary files
    while the code batch that names them is deferred, so the files ride along in every APK
    and JAR unplayable and unlookable.

    This is not hypothetical. PROJECT-STATUS records it for the conditional-music cluster,
    and a 2026-08-11 sweep found 29 orphaned assets totalling ~9.99 MB, of which 14 .ogg files are ~9.8 MB -- 54% of the
    game's music, in a 31 MB APK -- all blocked behind the cpdu-15l design decision.

    An asset counts as referenced if its file name appears anywhere in core's Java/Kotlin
    sources or in any JSON/properties file. That is deliberately loose: CPDU addresses some
    assets from pack config rather than code, and a checker that ignored pack config would
    report a wall of false positives and get switched off.
.PARAMETER MaxOrphanKb
    Fail if unreferenced assets exceed this many kilobytes. Ratchet: park the known backlog,
    fail anything new.
.PARAMETER Canary
    Negative control: verifies the matcher rejects a file name that cannot be referenced.
#>
param(
    [int]$MaxOrphanKb = 9989,
    [switch]$Canary
)

$ErrorActionPreference = 'Stop'
$repoRoot = (git rev-parse --show-toplevel).Trim()
Set-Location $repoRoot

$assetRoot = 'core/src/main/assets'
if (-not (Test-Path $assetRoot)) { Write-Host "asset-audit: FAIL (no $assetRoot)"; exit 2 }

# One pass over every source and config file, so this is not O(assets x files).
$haystack = [System.Text.StringBuilder]::new()
foreach ($f in Get-ChildItem -Path 'core/src/main','services','marketplace' -Recurse -File `
        -Include *.java,*.kt,*.json,*.properties -ErrorAction SilentlyContinue) {
    [void]$haystack.Append([IO.File]::ReadAllText($f.FullName))
}
$text = $haystack.ToString()
if ($text.Length -lt 10000) {
    # A checker that read almost nothing would call every asset an orphan.
    Write-Host "asset-audit: FAIL (only $($text.Length) chars of sources read - the scan is broken)"
    exit 2
}

$assets = @(Get-ChildItem -Path $assetRoot -Recurse -File |
            Where-Object { $_.Extension -in '.ogg','.mp3','.wav','.png','.jpg','.ttf' })
if ($assets.Count -eq 0) { Write-Host "asset-audit: FAIL (no assets found)"; exit 2 }

$orphans = [System.Collections.Generic.List[object]]::new()
foreach ($a in $assets) {
    if (-not $text.Contains($a.Name)) { $orphans.Add($a) }
}

$orphanKb = [int](($orphans | Measure-Object -Property Length -Sum).Sum / 1KB)
$totalKb  = [int](($assets  | Measure-Object -Property Length -Sum).Sum / 1KB)

Write-Host "asset-audit: $($assets.Count) assets, $totalKb KB total"
Write-Host "  unreferenced: $($orphans.Count) files, $orphanKb KB (ceiling $MaxOrphanKb KB)"

if ($orphans.Count -gt 0) {
    $orphans | Group-Object Extension | Sort-Object Name | ForEach-Object {
        $kb = [int](($_.Group | Measure-Object -Property Length -Sum).Sum / 1KB)
        Write-Host "    $($_.Name): $($_.Count) files, $kb KB"
    }
    $orphans | Sort-Object Length -Descending | Select-Object -First 10 | ForEach-Object {
        Write-Host ("      {0,7} KB  {1}" -f [int]($_.Length / 1KB), $_.Name)
    }
}

if ($Canary) {
    $probe = 'this_asset_name_cannot_possibly_exist_12345.ogg'
    if ($text.Contains($probe)) {
        Write-Host "asset-audit: CANARY FAILED (matcher claims an impossible name is referenced)"
        exit 1
    }
    Write-Host "asset-audit: CANARY OK (matcher rejects an unreferenced name)"
}

if ($orphanKb -gt $MaxOrphanKb) {
    Write-Host "asset-audit: FAIL ($orphanKb KB exceeds the $MaxOrphanKb KB ceiling)"
    exit 1
}
Write-Host "asset-audit: PASS (orphans within the known ceiling - TRACKED, NOT ACCEPTED)"
exit 0
