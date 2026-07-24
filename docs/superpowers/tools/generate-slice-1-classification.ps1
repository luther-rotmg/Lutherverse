<#
.SYNOPSIS
    Generate a provisional Slice 1 classification manifest TSV from the inventory.
.DESCRIPTION
    Reads the immutable inventory TSV and generates classification rows.
    Output written atomically via sibling temp file.
.PARAMETER InventoryPath
    Path to the upstream inventory TSV.
.PARAMETER OutputPath
    Output path for the classification TSV.
#>
[CmdletBinding()]param([string]$InventoryPath="",[string]$OutputPath="")
$ScriptRoot=Split-Path -Parent $PSCommandPath
$ProjectRoot=Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $ScriptRoot))
if(-not $InventoryPath){$InventoryPath=Join-Path $ProjectRoot "docs\superpowers\research\slice-1-upstream-inventory.tsv"}
if(-not $OutputPath){$OutputPath=Join-Path $ProjectRoot "docs\superpowers\research\slice-1-classification.tsv"}
$TempPath="$OutputPath.tmp"

# Read inventory
$lines=Get-Content -Path $InventoryPath -Encoding utf8NoBOM
if($lines[0] -ne "sha`trelease_band`tsubject`ttouched_paths`tprovisional_category"){
    Write-Error "ERROR: unexpected inventory header";exit 1
}
$dataLines=$lines[1..($lines.Count-1)]
if($dataLines.Count -ne 1209){Write-Error "ERROR: $($dataLines.Count) rows, expected 1209.";exit 1}
$rows=@()
$shas=@{}
foreach($line in $dataLines){
    $c=$line.Split("`t")
    if($c.Count -ne 5 -or @($c|Where-Object{[string]::IsNullOrWhiteSpace($_)}).Count){
        Write-Error "ERROR: invalid inventory row";exit 1
    }
    if($shas.ContainsKey($c[0])){Write-Error "ERROR: duplicate SHA $($c[0])";exit 1}
    $shas[$c[0]]=$true
    $rows+=@{
        sha=$c[0];release_band=$c[1];subject=$c[2]
        touched_paths=$c[3];provisional_category=$c[4]
    }
}
if($shas.Keys.Count -ne 1209){Write-Error "ERROR: $($shas.Keys.Count) unique SHAs";exit 1}

# Slice 2 patterns (case-insensitive)
$s2sub='journal|bestiar|landmark|custom.?[Nn]otes|WndUpgrade|salt.?[Cc]ube|vial.?of.?[Bb]lood|shard.?of.?[Oo]blivion|chaotic.?[Cc]ens(e|er)|trinket|cursed.?[Ww]and'
$s2path='(?i)(^|\\|/)journal/|bestiar|landmark|custom.?[Nn]otes|WndUpgrade|salt.?[Cc]ube|vial.?of.?[Bb]lood|shard.?of.?[Oo]blivion|chaotic.?[Cc]ens(e|er)|trinket|cursed.?[Ww]and'
$iosPat='(?i)(^|\\|/)ios/'
$docPat='(?i)(^|\\|/)docs/|\.md$|ui/changelist/|WelcomeScene\.java$'
$buildPat='(?i)(^|\\|/)build\.gradle|\.gradle/|settings\.gradle|gradle\.properties'
$platPat='(?i)(^|\\|/)android/|(^|\\|/)desktop/'
$hotPats=@('actors/Actor\.java$','actors/Char\.java$','actors/hero/Hero\.java$','(^|\\|/)Dungeon\.java$','Level\.java$','Bundle\.java$','settings','serializ')
$assetPat='(?i)\.(png|jpg|jpeg|gif|svg|webp|ogg|mp3|wav|ttf|otf|woff|woff2)$'
$locPat='(?i)messages/.*\.properties$'

function Test-Slice2Path([string]$p){return $p -match $s2path}
function Test-Slice2Subject([string]$s){return $s -match "(?i)($s2sub)"}
function Is-IOS([string]$p){return $p -match $iosPat}
function Is-Doc([string]$p){return $p -match $docPat}
function Is-Build([string]$p){return $p -match $buildPat}
function Is-Platform([string]$p){return $p -match $platPat}
function Is-Asset([string]$p){return $p -match $assetPat}
function Is-Loc([string]$p){return $p -match $locPat}
function Get-HotBatch([string]$p){
    if($p -match 'actors/Actor\.java$'){return "hotspot-actor"}
    if($p -match 'actors/Char\.java$'){return "hotspot-char"}
    if($p -match 'actors/hero/Hero\.java$'){return "hotspot-hero"}
    if($p -match '(^|\\|/)Dungeon\.java$'){return "hotspot-dungeon"}
    if($p -match 'Level\.java$'){return "hotspot-level"}
    if($p -match 'Bundle\.java$'){return "hotspot-bundle"}
    if($p -match 'settings'){return "hotspot-settings-serialization"}
    if($p -match 'serializ'){return "hotspot-settings-serialization"}
    return $null
}

# Classify a single path
function Get-PathResult([string]$p){
    if(Is-IOS $p){return @{d="excluded";r="needs-frontier-review: iOS platform code";b="excluded-ios"}}
    if(Test-Slice2Path $p){return @{d="slice2";r="provisional: Slice 2 feature path";b="slice2-feature"}}
    if(Is-Doc $p){return @{d="excluded";r="needs-frontier-review: upstream docs/changelog path";b="excluded-upstream-docs"}}
    if(Is-Build $p){return @{d="slice1";r="needs-frontier-review: build configuration";b="build-review"}}
    if(Is-Platform $p){return @{d="slice1";r="needs-frontier-review: platform code";b="platform-review"}}
    if(Is-Asset $p -or (Is-Loc $p)){return @{d="slice1";r="provisional: assets/localization";b="assets-localization"}}
    if($p -match 'actors/Actor\.java$|actors/Char\.java$|actors/hero/Hero\.java$|(^|\\|/)Dungeon\.java$|Level\.java$|Bundle\.java$|settings|serializ'){
        return @{d="slice1";r="needs-frontier-review: "+(Get-HotBatch $p);b=Get-HotBatch $p}
    }
    return @{d="slice1";r="provisional: cold code (general engine/tuning)";b="cold-code"}
}

# Classify a whole commit
function Get-CommitResult([string]$s,[string[]]$pa,[string]$cat){
    $s2Paths=@($pa|Where-Object{Test-Slice2Path $_})
    $s2Subj=Test-Slice2Subject $s
    if($s2Subj -and $s2Paths.Count -eq 0){
        return @{d="slice2";r="needs-frontier-review: Slice 2 subject match without feature path";b="slice2-feature"}
    }
    $pathResults=@($pa|ForEach-Object{Get-PathResult $_})
    $pathDecisions=@($pathResults|ForEach-Object{$_.d}|Select-Object -Unique)
    if($pathDecisions.Count -gt 1){
        return @{d="mixed";r="needs-frontier-review: mixed commit";b="frontier-mixed"}
    }
    if($pathDecisions -eq "slice2" -or $s2Subj){
        return @{d="slice2";r="provisional: Slice 2 feature path(s) only";b="slice2-feature"}
    }
    if($pathDecisions -eq "excluded"){
        $batch=@($pathResults|ForEach-Object{$_.b}|Select-Object -Unique)
        if($batch.Count -ne 1){$batch="frontier-excluded"}
        return @{d="excluded";r="needs-frontier-review: excluded paths only";b=[string]$batch}
    }
    $batches=@($pathResults|ForEach-Object{$_.b}|Select-Object -Unique)
    if($batches -contains "build-review"){return @{d="slice1";r="needs-frontier-review: build configuration commit";b="build-review"}}
    if($batches -contains "platform-review"){return @{d="slice1";r="needs-frontier-review: platform commit";b="platform-review"}}
    $hot=@($batches|Where-Object{$_ -like "hotspot-*"})
    if($hot){return @{d="slice1";r="needs-frontier-review: hotspot commit";b=$hot -join ","}}
    if($batches.Count -eq 1 -and $batches[0] -eq "assets-localization"){
        return @{d="slice1";r="provisional: assets/localization commit";b="assets-localization"}
    }
    return @{d="slice1";r="provisional: cold code (general engine/tuning)";b="cold-code"}
}

# Generate output
$out=[System.Collections.Generic.List[string]]::new()
$out.Add("sha`tscope`tpath_or_hunk`tdecision`treason`ttarget_batch")
foreach($r in $rows){
    $pa=$r.touched_paths -split ';'
    $cr=Get-CommitResult -s $r.subject -pa $pa -cat $r.provisional_category
    $crRow="$($r.sha)`tcommit`tall`t$($cr.d)`t$($cr.r)`t$($cr.b)"
    $out.Add($crRow)
    if($cr.d -eq "mixed"){
        foreach($p in $pa){
            $pr=Get-PathResult $p
            $prRow="$($r.sha)`tpath`t$p`t$($pr.d)`t$($pr.r)`t$($pr.b)"
            $out.Add($prRow)
        }
    }
}

# Validate
$allowed=@('slice1','slice2','later','excluded','mixed')
$err=0
# Check decisions
$decisions=@()
for($i=1;$i -lt $out.Count;$i++){$decisions+=($out[$i] -split "`t")[3]}
$bad=$decisions|Where-Object{$_ -notin $allowed}|Select-Object -Unique
if($bad){Write-Error "ERROR: invalid decisions: $($bad -join ', ')";$err=1}
# Check 1209 commit rows
$commitRows=@()
for($i=1;$i -lt $out.Count;$i++){
    $c=$out[$i].Split("`t")
    if($c[1] -eq 'commit'){$commitRows+=$c[0]}
}
if($commitRows.Count -ne 1209){Write-Error "ERROR: $($commitRows.Count) commit rows, expected 1209";$err=1}
# Check every SHA has a commit summary
$shaMap=@{}
foreach($r in $rows){$shaMap[$r.sha]=$true}
$missing=@($shaMap.Keys|Where-Object{-not ($commitRows -contains $_)})
if($missing){Write-Error "ERROR: $($missing.Count) SHAs missing from commit summaries";$err=1}
# Check mixed commits are immediately followed by path rows matching the path set
for($i=1;$i -lt $out.Count;$i++){
    $c=$out[$i].Split("`t")
    if($c[1] -eq 'commit' -and $c[3] -eq 'mixed'){
        $msha=$c[0]
        $inv=$rows|Where-Object{$_.sha -eq $msha}
        $exp=@($inv.touched_paths -split ';'|ForEach-Object{$_.Trim()}|Sort-Object)
        $j=$i+1
        $found=@()
        while($j -lt $out.Count){
            $pc=$out[$j].Split("`t")
            if($pc[1] -eq 'path' -and $pc[0] -eq $msha){$found+=$pc[2];$j++}else{break}
        }
        $foundS=@($found|Sort-Object)
        $diff=Compare-Object $exp $foundS
        if($diff -or $found.Count -lt 2){
            Write-Error "ERROR: mixed commit $msha path mismatch";$err=1
        }
    }
}
# Check review-marker rules
for($i=1;$i -lt $out.Count;$i++){
    $c=$out[$i].Split("`t")
    $d=$c[3];$reason=$c[4]
    if($d -in @('mixed','excluded') -and $reason -notmatch '^needs-frontier-review:'){
        Write-Error "ERROR: $d row missing needs-frontier-review prefix: $reason";$err=1
    }
    if($d -in @('slice1','slice2') -and $reason -notmatch '^provisional:' -and $reason -notmatch '^needs-frontier-review:'){
        Write-Error "ERROR: $d row missing proper prefix: $reason";$err=1
    }
}
# Check 6 columns
for($i=1;$i -lt $out.Count;$i++){
    $c=$out[$i].Split("`t")
    if($c.Count -ne 6 -or @($c|Where-Object{[string]::IsNullOrWhiteSpace($_)}).Count){
        Write-Error "ERROR: invalid or empty field at row $i";$err=1
    }
}

if($err -ne 0){exit 1}

# Write atomically
$outFull=[System.IO.Path]::GetFullPath($OutputPath)
$tmpFull=[System.IO.Path]::GetFullPath($TempPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outFull))|Out-Null
$out|Out-File -FilePath $tmpFull -Encoding utf8NoBOM -Force
[System.IO.File]::Move($tmpFull,$outFull,$true)
Write-Host "Wrote $($out.Count-1) data rows to $OutputPath"
