<#
.SYNOPSIS
    Generate the immutable Slice 1 upstream inventory TSV (v2.1.0..v2.5.4).
.DESCRIPTION
    Categories: merge, docs, localization, platform, assets, build, hotspot
    (Actor/Char/Hero/Dungeon/Level/Bundle/settings/serialization),
    slice2-candidate (Journal/bestiary/landmarks/custom-notes/WndUpgrade/
    trinkets/cursed-wand), or general. Aborts non-zero on ref/count/column
    failures. Writes atomically via sibling temp file.
.PARAMETER OutputPath
    Output path. Default: docs/superpowers/research/slice-1-upstream-inventory.tsv.
.EXAMPLE
    .\generate-slice-1-upstream-inventory.ps1 -OutputPath .beads/test.tsv
#>

[CmdletBinding()]
param(
    [string]$OutputPath = ""
)

# ---- Configuration ----
$ProjectRoot = Split-Path -Parent $PSScriptRoot | Split-Path -Parent | Split-Path -Parent
if (-not $OutputPath) {
    $OutputPath = Join-Path $ProjectRoot "docs\superpowers\research\slice-1-upstream-inventory.tsv"
}
$TempPath = "$OutputPath.tmp"

# ---- Release bands ----
$Bands = @(
    @{ Label = "v2.1.0..v2.1.4";  From = "v2.1.0"; To = "v2.1.4";  Expected = 80 }
    @{ Label = "v2.1.4..v2.2.1";  From = "v2.1.4"; To = "v2.2.1";  Expected = 295 }
    @{ Label = "v2.2.1..v2.3.2";  From = "v2.2.1"; To = "v2.3.2";  Expected = 228 }
    @{ Label = "v2.3.2..v2.4.2";  From = "v2.3.2"; To = "v2.4.2";  Expected = 238 }
    @{ Label = "v2.4.2..v2.5.4";  From = "v2.4.2"; To = "v2.5.4";  Expected = 368 }
)

# ---- Resolve a ref or die ----
function Resolve-Ref {
    param([string]$Ref)
    $sha = git -C $ProjectRoot rev-parse -q --verify "$Ref^{commit}" 2>$null
    if (-not $sha) {
        Write-Error "ERROR: ref '$Ref' does not resolve to a commit."
        exit 1
    }
    return $sha.Trim()
}

# ---- Categorize a commit ----
function Get-ProvisionalCategory {
    param([string]$Subject, [string[]]$Paths, [bool]$IsMerge)

    # Merge commit
    if ($IsMerge) { return "merge" }

    # Slice-2 feature candidates
    $slice2 = 'journal|bestiar|landmark|custom.?notes|WndUpgrade|trinket|cursed.?wand'
    if ($Subject -match $slice2 -or ($Paths | Where-Object { $_ -match $slice2 })) {
        return "slice2-candidate"
    }

    # Docs / changelog
    if ($Subject -match 'changelog|readme' -or
        ($Paths | Where-Object { $_ -match '(^|/)docs/|\.md$' })) { return "docs" }

    # Localization
    $hasLoc = $false
    $hasNonLoc = $false
    foreach ($p in $Paths) {
        if ($p -match 'messages/.*\.properties$') { $hasLoc = $true }
        else { $hasNonLoc = $true }
    }
    if ($hasLoc -and -not $hasNonLoc) { return "localization" }

    # Platform-specific (Android, iOS, desktop)
    if ($Paths | Where-Object { $_ -match '(^|\/)android\/|ios\/|desktop\/|services\b' }) { return "platform" }

    # Assets (images, fonts, audio etc.)
    if ($Paths | Where-Object { $_ -match '\.(png|jpg|jpeg|gif|svg|webp|ogg|mp3|wav|ttf|otf|woff|woff2)$' }) { return "assets" }

    # Build configuration
    if ($Paths | Where-Object { $_ -match '(^|\/)build\.gradle|\.gradle\/|settings\.gradle|gradle\.properties|travis|github|gitlab|\.ci\b' }) { return "build" }

    # Hotspot: Actor, Char, Hero, Dungeon, Level, Bundle, settings, serialization
    $hotPaths = @('actors/Actor\.java$','actors/Char\.java$','actors/hero/Hero\.java$','actors/hero/HeroClass\.java$','Dungeon\.java$','actors/hero/HeroSubClass\.java$','Level\.java$','Bundle\.java$','settings','serializ')
    $hotMatch = $Paths | Where-Object { $matched = $false; foreach ($pat in $hotPaths) { if ($_ -match $pat) { $matched = $true; break } }; $matched }
    if ($hotMatch) { return "hotspot" }

    return "general"
}

# ---- Validate refs ----
Write-Host "Validating boundary refs..."
foreach ($band in $Bands) {
    Resolve-Ref $band.From | Out-Null
    Resolve-Ref $band.To   | Out-Null
}
Write-Host "All refs resolve."

# ---- Collect commits per band (newest band first) ----
Write-Host "Collecting commits..."
$allRows = [System.Collections.Generic.List[string]]::new()
$seenShas = [System.Collections.Generic.HashSet[string]]::new()

# Process bands newest-first so the output is reverse-chronological globally
for ($i = $Bands.Count - 1; $i -ge 0; $i--) {
    $band = $Bands[$i]
    $rangeShas = git -C $ProjectRoot rev-list --topo-order "$($band.From)..$($band.To)"
    $commitCount = ($rangeShas | Measure-Object -Line).Lines
    if ($commitCount -ne $band.Expected) {
        Write-Error "ERROR: Band $($band.Label) has $commitCount commits, expected $($band.Expected)."
        exit 1
    }

    foreach ($sha in $rangeShas) {
        $sha = $sha.Trim()
        if (-not $seenShas.Add($sha)) {
            Write-Error "ERROR: Duplicate SHA $sha found in band $($band.Label)."
            exit 1
        }

        # Get parents and subject together; parent count identifies merges.
        $metadata = git -C $ProjectRoot log --format="%P`t%s" -1 $sha
        $metadataParts = $metadata -split "`t", 2
        $parents = ([string]$metadataParts[0]).Split(
            ' ', [System.StringSplitOptions]::RemoveEmptyEntries)
        $isMerge = $parents.Count -gt 1
        $subject = $metadataParts[1]
        $subject = $subject -replace '\t', ' ' -replace '\r?\n', ' '

        # Get touched paths
        $paths = @(git -C $ProjectRoot diff-tree --root -m --no-commit-id -r --name-only $sha |
            Sort-Object -Unique)
        $joinedPaths = ($paths -join ';') -replace '\t', ' ' -replace '\r?\n', ' '

        $category = Get-ProvisionalCategory -Subject $subject -Paths $paths -IsMerge $isMerge

        $row = "$sha	$($band.Label)	$subject	$joinedPaths	$category"
        $allRows.Add($row)
    }
}

# ---- Validate union ----
Write-Host "Validating union..."
$totalExpected = ($Bands | ForEach-Object { $_.Expected }) | Measure-Object -Sum | Select-Object -ExpandProperty Sum
if ($allRows.Count -ne $totalExpected) {
    Write-Error "ERROR: Union has $($allRows.Count) commits, expected $totalExpected."
    exit 1
}
if ($seenShas.Count -ne $totalExpected) {
    Write-Error "ERROR: Unique SHA count $($seenShas.Count) != total $totalExpected — duplicates found."
    exit 1
}

# ---- Write TSV atomically ----
Write-Host "Writing to $TempPath ..."
$header = "sha	release_band	subject	touched_paths	provisional_category"
$lines = @($header) + $allRows

# Validate every row has exactly 5 columns
foreach ($line in $lines) {
    $cols = $line -split '\t'
    if ($cols.Count -ne 5) {
        Write-Error "ERROR: Row with $($cols.Count) columns (expected 5): $($line.Substring(0, [Math]::Min(120, $line.Length)))"
        exit 1
    }
}

$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
$tempFullPath = [System.IO.Path]::GetFullPath($TempPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outputFullPath)) | Out-Null
$lines | Out-File -FilePath $tempFullPath -Encoding utf8NoBOM -Force

# Move atomically
[System.IO.File]::Move($tempFullPath, $outputFullPath, $true)

Write-Host "Wrote $($allRows.Count) rows to $OutputPath"
Write-Host "Done."
