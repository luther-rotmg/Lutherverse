<#
.SYNOPSIS
    Apply frontier review overrides to Slice 1 classification manifest.
.DESCRIPTION
    Reads provisional classification and frontier review TSVs, applies overrides
    and validations, writes final classification atomically.
.PARAMETER ProvisionalPath
    Path to the provisional classification TSV.
.PARAMETER InventoryPath
    Path to the upstream inventory TSV.
.PARAMETER ReviewPath
    Path to the frontier review TSV.
.PARAMETER OutputPath
    Output path for the final classification TSV.
#>
[CmdletBinding()]param(
    [string]$ProvisionalPath="",
    [string]$InventoryPath="",
    [string]$ReviewPath="",
    [string]$OutputPath=""
)
$ScriptRoot=Split-Path -Parent $PSCommandPath
$ProjectRoot=Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $ScriptRoot))
if(-not $ProvisionalPath){$ProvisionalPath=Join-Path $ProjectRoot "docs\superpowers\research\slice-1-classification.provisional.tsv"}
if(-not $InventoryPath){$InventoryPath=Join-Path $ProjectRoot "docs\superpowers\research\slice-1-upstream-inventory.tsv"}
if(-not $ReviewPath){$ReviewPath=Join-Path $ProjectRoot "docs\superpowers\research\slice-1-frontier-review.tsv"}
if(-not $OutputPath){$OutputPath=Join-Path $ProjectRoot "docs\superpowers\research\slice-1-classification.tsv"}
$TempPath="$OutputPath.tmp"

# Read inventory
$invLines=Get-Content -Path $InventoryPath -Encoding utf8NoBOM
if($invLines[0] -ne "sha`trelease_band`tsubject`ttouched_paths`tprovisional_category"){
    Write-Error "ERROR: unexpected inventory header";exit 1
}
$invData=$invLines[1..($invLines.Count-1)]
if($invData.Count -ne 1209){Write-Error "ERROR: $($invData.Count) inventory rows, expected 1209.";exit 1}

# Build inventory map: sha -> touched_paths array
$invMap=@{}
foreach($line in $invData){
    $c=$line.Split("`t")
    if($c.Count -ne 5 -or @($c|Where-Object{[string]::IsNullOrWhiteSpace($_)}).Count){
        Write-Error "ERROR: invalid inventory row";exit 1
    }
    if($invMap.ContainsKey($c[0])){Write-Error "ERROR: duplicate inventory SHA $($c[0])";exit 1}
    $invMap[$c[0]]=@($c[3] -split ';'|ForEach-Object{$_.Trim()})
}

# Read provisional classification
$provLines=Get-Content -Path $ProvisionalPath -Encoding utf8NoBOM
if($provLines[0] -ne "sha`tscope`tpath_or_hunk`tdecision`treason`ttarget_batch"){
    Write-Error "ERROR: unexpected provisional header";exit 1
}

# Parse provisional: sha -> { commitRow, pathRows }
$provMap=@{}
$provCommitOrder=@()
for($i=1;$i -lt $provLines.Count;$i++){
    $line=$provLines[$i]
    $c=$line.Split("`t")
    if($c.Count -ne 6 -or @($c|Where-Object{[string]::IsNullOrWhiteSpace($_)}).Count){
        Write-Error "ERROR: invalid provisional row";exit 1
    }
    $sha=$c[0];$scope=$c[1]
    
    if($scope -eq 'commit'){
        if($provMap.ContainsKey($sha)){Write-Error "ERROR: duplicate commit summary $sha";exit 1}
        $provMap[$sha]=@{commit=$line; paths=@()}
        $provCommitOrder+=$sha
    }elseif($scope -eq 'path'){
        if(-not $provMap.ContainsKey($sha)){Write-Error "ERROR: orphan path row for $sha";exit 1}
        $provMap[$sha].paths+=$line
    }
}

# Validate 1209 commits
$provCommits=$provMap.Keys.Count
if($provCommits -ne 1209){Write-Error "ERROR: $provCommits commit summaries, expected 1209";exit 1}
if($invMap.Keys.Count -ne 1209){Write-Error "ERROR: $($invMap.Keys.Count) unique inventory SHAs";exit 1}

# Read frontier review
$revLines=Get-Content -Path $ReviewPath -Encoding utf8NoBOM
if($revLines[0] -ne "sha`treview_kind`tstatus`tdecision`ttarget_batch`tpath_overrides`treason"){
    Write-Error "ERROR: unexpected review header";exit 1
}

# Parse review rows
$reviewMap=@{}
for($i=1;$i -lt $revLines.Count;$i++){
    $line=$revLines[$i]
    $c=$line.Split("`t")
    if($c.Count -ne 7 -or @($c|Where-Object{[string]::IsNullOrWhiteSpace($_)}).Count){
        Write-Error "ERROR: invalid review row";exit 1
    }
    $sha=$c[0]
    if($reviewMap.ContainsKey($sha)){Write-Error "ERROR: duplicate review SHA $sha";exit 1}
    
    $reviewMap[$sha]=@{
        kind=$c[1]
        status=$c[2]
        decision=$c[3]
        batch=$c[4]
        overrides=$c[5]
        reason=$c[6]
    }
}

# Build output
$out=[System.Collections.Generic.List[string]]::new()
$out.Add("sha`tscope`tpath_or_hunk`tdecision`treason`ttarget_batch")

foreach($sha in $provCommitOrder){
    $provEntry=$provMap[$sha]
    $commLine=$provEntry.commit
    $pathLines=$provEntry.paths
    $review=$reviewMap[$sha]
    
    if($review){
        # Parse review decision/batch/reason
        $newDecision=$review.decision
        $newBatch=$review.batch
        $ledgerReason=$review.reason
        
        # Parse path_overrides: "path=decision;path=decision;..."
        $overrideMap=@{}
        if($review.overrides -ne '-'){
            $overrides=$review.overrides -split ';'
            foreach($ov in $overrides){
                if($ov -match '^(.+)=([^=]+)$'){
                    $path=$matches[1]
                    $decision=$matches[2]
                    $overrideMap[$path]=$decision
                }
            }
        }
        
        # Validate overrides touch paths from inventory
        $touched=$invMap[$sha]
        foreach($ovPath in $overrideMap.Keys){
            $basePath=$ovPath -split '@@' | Select-Object -First 1
            if($touched -notcontains $basePath){
                Write-Error "ERROR: override path '$basePath' not in commit $sha";exit 1
            }
        }
        
        # Output commit row with reviewed reason
        $outReason="reviewed: $ledgerReason"
        $outLine="$sha`tcommit`tall`t$newDecision`t$outReason`t$newBatch"
        $out.Add($outLine)
        
        # For mixed decisions, emit full path partition
        if($newDecision -eq 'mixed'){
            $touched=$invMap[$sha]
            foreach($tp in $touched){
                $ovDecision=$overrideMap[$tp]
                if($ovDecision -eq 'slice1'){$mapDecision='slice1'; $mapBatch=$newBatch}
                elseif($ovDecision -eq 'slice2'){$mapDecision='slice2'; $mapBatch='slice2-feature'}
                elseif($ovDecision -eq 'excluded'){$mapDecision='excluded'; $mapBatch='excluded-reviewed'}
                elseif($ovDecision -eq 'later'){$mapDecision='later'; $mapBatch='later-review'}
                else{
                    # No override for this path - use provisional default
                    $origPath=$pathLines | Where-Object {$_ -match "`t$tp`t"} | Select-Object -First 1
                    if($origPath){
                        $pc=$origPath.Split("`t")
                        $mapDecision=$pc[3]
                        $mapBatch=$pc[5]
                    }else{
                        $mapDecision='slice1'
                        $mapBatch=$newBatch
                    }
                }
                $outReason="reviewed: override -"
                $outLine="$sha`tpath`t$tp`t$mapDecision`t$outReason`t$mapBatch"
                $out.Add($outLine)
            }
        }else{
            # Non-mixed: keep only review overrides as path evidence
            foreach($tp in $overrideMap.Keys){
                $basePath=$tp -split '@@' | Select-Object -First 1
                $ovDecision=$overrideMap[$tp]
                if($ovDecision -eq 'slice1'){$mapDecision='slice1'; $mapBatch=$newBatch}
                elseif($ovDecision -eq 'slice2'){$mapDecision='slice2'; $mapBatch='slice2-feature'}
                elseif($ovDecision -eq 'excluded'){$mapDecision='excluded'; $mapBatch='excluded-reviewed'}
                elseif($ovDecision -eq 'later'){$mapDecision='later'; $mapBatch='later-review'}
                else{$mapDecision=$newDecision; $mapBatch=$newBatch}
                
                $outReason="reviewed: override -"
                $outLine="$sha`tpath`t$tp`t$mapDecision`t$outReason`t$mapBatch"
                $out.Add($outLine)
            }
        }
    }else{
        # No review: keep provisional as-is
        $out.Add($commLine)
        foreach($pl in $pathLines){
            $out.Add($pl)
        }
    }
}

# Validate output
$allowed=@('slice1','slice2','later','excluded','mixed')
$err=0

# Check header
if($out[0] -ne "sha`tscope`tpath_or_hunk`tdecision`treason`ttarget_batch"){
    Write-Error "ERROR: output header mismatch";$err=1
}

# Validate rows
$commitCount=0
$foundShas=@{}
for($i=1;$i -lt $out.Count;$i++){
    $c=$out[$i].Split("`t")
    if($c.Count -ne 6 -or @($c|Where-Object{[string]::IsNullOrWhiteSpace($_)}).Count){
        Write-Error "ERROR: invalid/empty field at row $i";$err=1
        continue
    }
    $sha=$c[0];$scope=$c[1];$decision=$c[3];$reason=$c[4]
    
    # Validate scope
    if($scope -notin @('commit','path','hunk')){
        Write-Error "ERROR: invalid scope '$scope' at row $i";$err=1
    }
    
    # Validate decision
    if($decision -notin $allowed){
        Write-Error "ERROR: invalid decision '$decision' at row $i";$err=1
    }
    
    # Validate reason - no needs-frontier-review in output
    if($reason -match 'needs-frontier-review'){
        Write-Error "ERROR: needs-frontier-review in output at row $i";$err=1
    }
    
    # Track commits and SHAs
    if($scope -eq 'commit'){
        $commitCount++
        $foundShas[$sha]=$true
    }
}

# Validate 1209 commits
if($commitCount -ne 1209){
    Write-Error "ERROR: $commitCount commit rows, expected 1209";$err=1
}

# Validate all 1209 inventory SHAs present
if($foundShas.Count -ne 1209){
    Write-Error "ERROR: $($foundShas.Count) unique SHAs in output, expected 1209";$err=1
}

if($err -ne 0){exit 1}

# Write atomically
$outFull=[System.IO.Path]::GetFullPath($OutputPath)
$tmpFull=[System.IO.Path]::GetFullPath($TempPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outFull))|Out-Null
$out|Out-File -FilePath $tmpFull -Encoding utf8NoBOM -Force
[System.IO.File]::Move($tmpFull,$outFull,$true)
Write-Host "Wrote $($out.Count-1) data rows to $OutputPath"
