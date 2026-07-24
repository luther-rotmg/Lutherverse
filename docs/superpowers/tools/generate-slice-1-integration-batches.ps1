#Requires -Version 5.1
param(
    [string]$OutputPath = ".beads/slice-1-integration-batches.tsv",
    [string]$RepoRoot = (git -C (Get-Location) rev-parse --show-toplevel),
    [string]$InventoryPath = "docs/superpowers/research/slice-1-upstream-inventory.tsv",
    [string]$ClassificationPath = "docs/superpowers/research/slice-1-classification.tsv",
    [string]$BaseRef = "6067d21dae60db860a2cbf79b2d5b8f1a6386c35",
    [string]$TargetRef = "f5531fd65542f69ed4c17a5bdde03299e06a83f6"
)

$ErrorActionPreference = "Stop"

function Load-TSV {
    param([string]$Path)
    $fullPath = Join-Path -Path $RepoRoot -ChildPath $Path
    if (-not (Test-Path -LiteralPath $fullPath)) {
        throw "File not found: $fullPath"
    }
    $lines = @(Get-Content -LiteralPath $fullPath -Encoding UTF8)
    if ($lines.Count -eq 0) {
        throw "Empty file: $fullPath"
    }
    $headers = $lines[0] -split "`t"
    $rows = @()
    for ($i = 1; $i -lt $lines.Count; $i++) {
        $fields = $lines[$i] -split "`t", $headers.Count
        $row = @{}
        for ($j = 0; $j -lt $headers.Count; $j++) {
            $row[$headers[$j]] = if ($j -lt $fields.Count) { $fields[$j] } else { "" }
        }
        $rows += $row
    }
    return $headers, $rows
}

$invHeaders, $inventory = Load-TSV -Path $InventoryPath
$classHeaders, $classRows = Load-TSV -Path $ClassificationPath

if ($invHeaders -notcontains "sha" -or $invHeaders -notcontains "touched_paths") {
    throw "Invalid inventory headers"
}
if ($classHeaders -notcontains "sha" -or $classHeaders -notcontains "scope" -or $classHeaders -notcontains "path_or_hunk" -or $classHeaders -notcontains "decision") {
    throw "Invalid classification headers"
}

$classifications = @{}
foreach ($row in $classRows) {
    $key = "$($row['sha'])::$($row['scope'])::$($row['path_or_hunk'])"
    $classifications[$key] = $row
}

$allPaths = @{}
$commitPaths = @{}

foreach ($inv in $inventory) {
    $sha = $inv['sha']
    $touchedPaths = $inv['touched_paths']
    
    if ([string]::IsNullOrWhiteSpace($touchedPaths)) {
        continue
    }
    
    $paths = @($touchedPaths -split ";")
    if (-not $commitPaths.ContainsKey($sha)) {
        $commitPaths[$sha] = @()
    }
    
    foreach ($path in $paths) {
        $path = $path.Trim()
        if ([string]::IsNullOrWhiteSpace($path)) { continue }
        
        $commitPaths[$sha] += $path
        
        if (-not $allPaths.ContainsKey($path)) {
            $allPaths[$path] = @{
                touches = @()
                decisions = @()
                targetBatches = @()
                isManualHunk = $false
                pathDecision = $null
                pathBatch = $null
            }
        }
        $allPaths[$path].touches += $sha
    }
}

foreach ($row in $classRows) {
    $sha = $row['sha']
    $scope = $row['scope']
    $pathOrHunk = $row['path_or_hunk']
    $decision = $row['decision']
    $targetBatch = $row['target_batch']
    
    if ($scope -eq "path") {
        if ($allPaths.ContainsKey($pathOrHunk)) {
            $allPaths[$pathOrHunk].pathDecision = $decision
            $allPaths[$pathOrHunk].pathBatch = $targetBatch
        }
    } elseif ($scope -eq "hunk") {
        if ($allPaths.ContainsKey($pathOrHunk)) {
            $allPaths[$pathOrHunk].isManualHunk = $true
        }
    }
}

$gitOutput = @{}
$nameStatusCmd = "git -C `"$RepoRoot`" diff --name-status --find-renames `"$BaseRef`" `"$TargetRef`""
$numstatCmd = "git -C `"$RepoRoot`" diff --numstat `"$BaseRef`" `"$TargetRef`""

$nameStatus = Invoke-Expression -Command $nameStatusCmd | Out-String
$numstat = Invoke-Expression -Command $numstatCmd | Out-String

$nameStatusLines = $nameStatus -split "`n" | Where-Object { $_ -and $_.Trim() }
$numstatLines = $numstat -split "`n" | Where-Object { $_ -and $_.Trim() }

foreach ($line in $nameStatusLines) {
    $parts = $line -split "`t"
    if ($parts.Count -ge 2) {
        $status = $parts[0]
        if ($status -eq "R" -and $parts.Count -ge 3) {
            $oldPath = $parts[1]
            $newPath = $parts[2]
            if (-not $gitOutput.ContainsKey($newPath)) {
                $gitOutput[$newPath] = @{ status = "R"; added = 0; deleted = 0; oldPath = $oldPath }
            }
        } else {
            $path = $parts[1]
            if (-not $gitOutput.ContainsKey($path)) {
                $gitOutput[$path] = @{ status = $status; added = 0; deleted = 0 }
            }
        }
    }
}

foreach ($line in $numstatLines) {
    $parts = $line -split "`t"
    if ($parts.Count -ge 3) {
        $added = $parts[0]
        $deleted = $parts[1]
        $path = $parts[2]
        
        if ($added -eq "-") { $added = -1 }
        if ($deleted -eq "-") { $deleted = -1 }
        
        if ($gitOutput.ContainsKey($path)) {
            $gitOutput[$path].added = [int]$added
            $gitOutput[$path].deleted = [int]$deleted
        }
    }
}

$ledger = @()

foreach ($path in ($allPaths.Keys | Sort-Object)) {
    $info = $allPaths[$path]
    $touchCount = $info.touches.Count
    $touchShas = $info.touches -join ";"
    
    $effectiveDecision = $null
    $effectiveBatch = $null
    
    if ($null -ne $info.pathDecision) {
        $effectiveDecision = $info.pathDecision
        $effectiveBatch = $info.pathBatch
    } else {
        $decisions = @()
        $batches = @()
        foreach ($sha in $info.touches) {
            $commClassKey = "$sha::commit::all"
            if ($classifications.ContainsKey($commClassKey)) {
                $decisions += $classifications[$commClassKey]['decision']
                $batches += $classifications[$commClassKey]['target_batch']
            }
        }
        if ($decisions.Count -gt 0) {
            $effectiveDecision = $decisions[0]
            $effectiveBatch = $batches[0]
        }
    }
    
    $mode = "-"
    $netStatus = "unchanged"
    $added = 0
    $deleted = 0
    
    if ($gitOutput.ContainsKey($path)) {
        $gitInfo = $gitOutput[$path]
        $mode = $gitInfo.status
        $added = $gitInfo.added
        $deleted = $gitInfo.deleted
        if ($mode -eq "A") { $netStatus = "added" }
        elseif ($mode -eq "D") { $netStatus = "deleted" }
        elseif ($mode -eq "M") { $netStatus = "modified" }
        elseif ($mode -eq "R") { $netStatus = "renamed" }
        else { $netStatus = "modified" }
    }
    
    $batchId = "-"
    $sizeClass = "-"
    $reason = "unknown"
    
    if ($info.isManualHunk) {
        $reason = "hunk-review-required"
    } elseif ($added -eq -1 -or $deleted -eq -1) {
        $reason = "binary"
    } elseif ($effectiveDecision -ne "slice1") {
        if ($effectiveDecision -eq "excluded") {
            $reason = "excluded-by-decision"
        } elseif ($effectiveDecision -eq "slice2") {
            $reason = "mixed-target-batches"
        } elseif ($effectiveDecision -eq "mixed") {
            $reason = "mixed-decisions"
        } else {
            $reason = "mixed-decisions"
        }
    } else {
        $reason = "all-history-slice1"
    }
    
    $ledger += @{
        batch_id = $batchId
        size_class = $sizeClass
        path = $path
        mode = $mode
        target_batch = $effectiveBatch
        path_area = "-"
        net_status = $netStatus
        added = $added.ToString()
        deleted = $deleted.ToString()
        touch_count = $touchCount.ToString()
        touch_shas = $touchShas
        reason = $reason
    }
}

$outputDir = Split-Path -Path $OutputPath -Parent
if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

$header = "batch_id`tsize_class`tpath`tmode`ttarget_batch`tpath_area`tnet_status`tadded`tdeleted`ttouch_count`ttouch_shas`treason"
$content = @($header)

foreach ($row in $ledger) {
    $line = "$($row.batch_id)`t$($row.size_class)`t$($row.path)`t$($row.mode)`t$($row.target_batch)`t$($row.path_area)`t$($row.net_status)`t$($row.added)`t$($row.deleted)`t$($row.touch_count)`t$($row.touch_shas)`t$($row.reason)"
    $content += $line
}

$tempPath = "$OutputPath.tmp"
$textContent = ($content -join "`n") + "`n"
[System.IO.File]::WriteAllText($tempPath, $textContent, [System.Text.Encoding]::UTF8)

if (Test-Path -LiteralPath $OutputPath) {
    Remove-Item -LiteralPath $OutputPath -Force
}
Move-Item -LiteralPath $tempPath -Destination $OutputPath -Force

exit 0
