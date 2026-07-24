<#
.SYNOPSIS
    Apply frontier review overrides to Slice 1 classification.
.DESCRIPTION
    Reads provisional classification and frontier review TSVs, applies
    validated overrides, writes final classification atomically.
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
    [string]$ProvisionalPath="",[string]$InventoryPath="",
    [string]$ReviewPath="",[string]$OutputPath=""
)
function die([string]$m){Write-Error "ERROR: $m";exit 1}
$ScriptRoot=Split-Path -Parent $PSCommandPath
$ProjectRoot=Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $ScriptRoot))
if(-not $ProvisionalPath){$ProvisionalPath=Join-Path $ProjectRoot "docs\superpowers\research\slice-1-classification.provisional.tsv"}
if(-not $InventoryPath){$InventoryPath=Join-Path $ProjectRoot "docs\superpowers\research\slice-1-upstream-inventory.tsv"}
if(-not $ReviewPath){$ReviewPath=Join-Path $ProjectRoot "docs\superpowers\research\slice-1-frontier-review.tsv"}
if(-not $OutputPath){$OutputPath=Join-Path $ProjectRoot "docs\superpowers\research\slice-1-classification.tsv"}
$TempPath="$OutputPath.tmp"
$VS=@('commit','path','hunk'); $VD=@('slice1','slice2','later','excluded','mixed'); $OD=@('slice1','slice2','later','excluded')

# decision-map helper: outputs (decision, target_batch) tuple
function dm($d,$b){
    if($d -eq 'slice2'){return @($d,'slice2-feature')}
    if($d -eq 'excluded'){return @($d,'excluded-reviewed')}
    if($d -eq 'later'){return @($d,'later-review')}
    return @($d,$b)
}

# Read inventory
$invLines=Get-Content -Path $InventoryPath -Encoding utf8NoBOM
if($invLines[0] -ne "sha`trelease_band`tsubject`ttouched_paths`tprovisional_category"){die "unexpected inventory header"}
$invData=$invLines[1..($invLines.Count-1)]
if($invData.Count -ne 1209){die "$($invData.Count) inventory rows, expected 1209."}
$invMap=@{};$invOrder=@()
foreach($line in $invData){
    $c=$line.Split("`t")
    if($c.Count -ne 5 -or @($c|Where-Object{[string]::IsNullOrWhiteSpace($_)}).Count){die "invalid inventory row"}
    if($invMap.ContainsKey($c[0])){die "duplicate inventory SHA $($c[0])"}
    $invMap[$c[0]]=@($c[3] -split ';'|ForEach-Object{$_.Trim()});$invOrder+=$c[0]
}

# Read provisional
$provLines=Get-Content -Path $ProvisionalPath -Encoding utf8NoBOM
if($provLines[0] -ne "sha`tscope`tpath_or_hunk`tdecision`treason`ttarget_batch"){die "unexpected provisional header"}
$provMap=@{};$provCommitOrder=@()
for($i=1;$i -lt $provLines.Count;$i++){
    $line=$provLines[$i];$c=$line.Split("`t")
    if($c.Count -ne 6 -or @($c|Where-Object{[string]::IsNullOrWhiteSpace($_)}).Count){die "invalid provisional row $i"}
    $sha=$c[0];$scope=$c[1];$decision=$c[3]
    if($scope -notin $VS){die "invalid scope '$scope' at provisional row $i"}
    if($decision -notin $VD){die "invalid decision '$decision' at provisional row $i"}
    if($scope -eq 'commit'){
        if($provMap.ContainsKey($sha)){die "duplicate commit summary $sha"}
        $provMap[$sha]=@{commit=$line;paths=@();hunks=@()};$provCommitOrder+=$sha
    }elseif($scope -eq 'path'){if(-not $provMap.ContainsKey($sha)){die "orphan path row for $sha"};$provMap[$sha].paths+=$line
    }elseif($scope -eq 'hunk'){if(-not $provMap.ContainsKey($sha)){die "orphan hunk row for $sha"};$provMap[$sha].hunks+=$line}
}
if($provMap.Count -ne 1209){die "$($provMap.Count) commit summaries, expected 1209"}
if($invMap.Count -ne 1209){die "$($invMap.Count) unique inventory SHAs"}
for($idx=0;$idx -lt 1209;$idx++){if($provCommitOrder[$idx] -ne $invOrder[$idx]){die "provisional commit at index $idx mismatch inventory"}}

# Read frontier review
$revLines=Get-Content -Path $ReviewPath -Encoding utf8NoBOM
if($revLines[0] -ne "sha`treview_kind`tstatus`tdecision`ttarget_batch`tpath_overrides`treason"){die "unexpected review header"}
$reviewMap=@{};$revOrder=@()
for($i=1;$i -lt $revLines.Count;$i++){
    $line=$revLines[$i];$c=$line.Split("`t")
    if($c.Count -ne 7 -or @($c|Where-Object{[string]::IsNullOrWhiteSpace($_)}).Count){die "invalid review row $i"}
    $sha=$c[0];$kind=$c[1];$status=$c[2];$decision=$c[3];$batch=$c[4]
    if($kind -notin @('frontier-required','sample')){die "unknown review_kind '$kind' at row $i"}
    if($status -notin @('approved','override')){die "unknown status '$status' at row $i"}
    if($decision -notin $VD){die "unknown decision '$decision' at row $i"}
    if(-not $invMap.ContainsKey($sha)){die "review SHA $sha not in inventory"}
    if($reviewMap.ContainsKey($sha)){die "duplicate review SHA $sha"}
    $reviewMap[$sha]=@{kind=$kind;status=$status;decision=$decision;batch=$batch;overrides=$c[5];reason=$c[6]};$revOrder+=$sha
}

# Derive required-review set from provisional
$needsFrontier=@()
foreach($sha in $provCommitOrder){
    $pc=$provMap[$sha].commit.Split("`t");$pReason=$pc[4];$pDecision=$pc[3]
    if($pReason -match "^needs-frontier-review:" -or $pDecision -in @('mixed','excluded')){$needsFrontier+=$sha}
}
if($needsFrontier.Count -ne 411){die "$($needsFrontier.Count) commits need frontier review, expected 411"}
$reqKinds=@{};$sampleCount=0
foreach($sha in $revOrder){
    $r=$reviewMap[$sha]
    if($r.kind -eq 'frontier-required'){
        if($needsFrontier -notcontains $sha){die "frontier-required row for $sha not in needs-frontier set"};$reqKinds[$sha]=$true
    }elseif($r.kind -eq 'sample'){
        if($needsFrontier -contains $sha){die "sample row for $sha is in needs-frontier set"};$sampleCount++
    }
}
foreach($sha in $needsFrontier){if(-not $reqKinds.ContainsKey($sha)){die "missing frontier-required row for $sha"}}
if($sampleCount -ne 37){die "$sampleCount sample rows, expected 37"}

# Build output
$out=[System.Collections.Generic.List[string]]::new()
$out.Add("sha`tscope`tpath_or_hunk`tdecision`treason`ttarget_batch")
$err=0

foreach($sha in $provCommitOrder){
    $entry=$provMap[$sha];$comm=$entry.commit;$origCommit=$comm.Split("`t");$paths=$entry.paths;$hunks=$entry.hunks;$review=$reviewMap[$sha]
    if(-not $review){$out.Add($comm);foreach($l in $paths){$out.Add($l)};foreach($l in $hunks){$out.Add($l)};continue}

    $rDecision=$review.decision;$rBatch=$review.batch;$rReason=$review.reason

    # Parse overrides at last equals sign
    $om=@{}
    if($review.overrides -ne '-'){
        foreach($ov in ($review.overrides -split ';')){
            $ei=$ov.LastIndexOf('=');if($ei -le 0){die "malformed override '$ov' for $sha"}
            $k=$ov.Substring(0,$ei);$v=$ov.Substring($ei+1)
            if($k -match '@@$'){die "empty hunk anchor in override '$ov' for $sha"}
            if($om.ContainsKey($k)){die "duplicate override key '$k' for $sha"}
            if($v -notin $OD){die "override decision '$v' for '$k' not valid"};$om[$k]=$v
        }
    }

    # Validate override targets exist in inventory
    $touched=$invMap[$sha]
    foreach($k in $om.Keys){$b=$k -split '@@'|Select-Object -First 1;if($touched -notcontains $b){die "override path '$b' not in commit $sha"}}

    # Commit row
    $outReason="reviewed: $rReason"
    $out.Add("$sha`tcommit`tall`t$rDecision`t$outReason`t$rBatch")

    if($rDecision -eq 'mixed'){
        # Emit all touched paths + adjacent hunk overrides
        $ep=@{}
        foreach($tp in $touched){
            $ov=$om[$tp]
            if($ov){$md,$mb=dm $ov $rBatch}else{
                $op=$paths|Where-Object{$_.Split("`t")[2] -eq $tp}|Select-Object -First 1
                if($op){$mc=$op.Split("`t");$md=$mc[3];$mb=$mc[5]
                }elseif($origCommit[3] -ne 'mixed'){$md=$origCommit[3];$mb=$origCommit[5]
                }else{die "missing provisional path partition for '$tp' in mixed commit $sha"}
            }
            $outReason="reviewed: override -"
            $out.Add("$sha`tpath`t$tp`t$md`t$outReason`t$mb");$ep[$tp]=$true
            foreach($hk in ($om.Keys|Sort-Object|Where-Object{$_ -match "^$([regex]::Escape($tp))@@"})){
                $hv=$om[$hk];$hd,$hb=dm $hv $rBatch
                $hn=$hk -replace "^$([regex]::Escape($tp))@@",""
                $out.Add("$sha`thunk`t$tp@@$hn`t$hd`t$outReason`t$hb")
            }
        }
        # Validate >=2 distinct decisions
        $mds=@();for($j=$out.Count-1;$j -ge 0 -and ($c2=$out[$j].Split("`t")) -and $c2[0] -eq $sha -and $c2[1] -ne 'commit';$j--){if($c2[1] -in @('path','hunk')){$mds+=$c2[3]}}
        if(($mds|Sort-Object -Unique).Count -lt 2){die "mixed $sha has fewer than 2 distinct path/hunk decisions"}
    }else{
        # Emit only overridden paths and hunks
        foreach($k in ($om.Keys|Sort-Object)){
            $ov=$om[$k];$md,$mb=dm $ov $rBatch
            $outReason="reviewed: override -"
            if($k -match '@@'){$out.Add("$sha`thunk`t$k`t$md`t$outReason`t$mb")}else{$out.Add("$sha`tpath`t$k`t$md`t$outReason`t$mb")}
        }
    }
}

# Validate output
for($i=1;$i -lt $out.Count;$i++){
    $c=$out[$i].Split("`t")
    if($c.Count -ne 6 -or @($c|Where-Object{[string]::IsNullOrWhiteSpace($_)}).Count){die "invalid output row $i";$err=1;continue}
    $scope=$c[1];$decision=$c[3];$reason=$c[4]
    if($scope -notin $VS){die "invalid scope '$scope' at row $i";$err=1}
    if($decision -notin $VD){die "invalid decision '$decision' at row $i";$err=1}
    if($reason -match 'needs-frontier-review'){die "needs-frontier-review in output at row $i";$err=1}
}
$oc=0;$os=@()
for($i=1;$i -lt $out.Count;$i++){
    if($out[$i].Split("`t")[1] -eq 'commit'){$oc++;$os+=$out[$i].Split("`t")[0]}
}
if($oc -ne 1209){die "$oc commit rows, expected 1209";$err=1}
$ou=@($os|Select-Object -Unique);if($ou.Count -ne 1209){die "$($ou.Count) unique SHAs, expected 1209";$err=1}
for($idx=0;$idx -lt 1209;$idx++){if($os[$idx] -ne $invOrder[$idx]){die "output commit at index $idx mismatch inventory order";$err=1}}
for($i=1;$i -lt $out.Count;$i++){
    $c=$out[$i].Split("`t")
    if($c[1] -eq 'commit' -and $reviewMap.ContainsKey($c[0]) -and $c[4] -notmatch "^reviewed:"){die "reviewed commit $($c[0]) reason does not start with reviewed:";$err=1}
}
if($err -ne 0){exit 1}

# Write atomically
$outFull=[System.IO.Path]::GetFullPath($OutputPath)
$tmpFull=[System.IO.Path]::GetFullPath($TempPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outFull))|Out-Null
$out|Out-File -FilePath $tmpFull -Encoding utf8NoBOM -Force
[System.IO.File]::Move($tmpFull,$outFull,$true)
Write-Host "Wrote $($out.Count-1) data rows to $OutputPath"
