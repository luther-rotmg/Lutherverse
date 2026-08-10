#requires -Version 7
<#
.SYNOPSIS
    Boot smoke for the desktop build: proves core actually starts and renders.
.DESCRIPTION
    Replaces the Android emulator smoke, which has never executed the APK because
    the emulator never reaches sys.boot_completed. Builds the desktop jar, runs it
    with -Dsmoke.frames, and asserts a clean exit plus a confirmed rendered frame.
.PARAMETER Frames
    How many rendered frames count as a successful boot.
.PARAMETER TimeoutSeconds
    Hard ceiling on the whole run.
#>
param(
    [int]$Frames = 120,
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$repoRoot = (git rev-parse --show-toplevel).Trim()
Set-Location $repoRoot

Write-Host "desktop-smoke: building the release jar"
& "$repoRoot\gradlew.bat" desktop:release --quiet
if ($LASTEXITCODE -ne 0) {
    Write-Host "desktop-smoke: FAIL (desktop:release failed)"
    exit 1
}

$jar = Get-ChildItem "$repoRoot\desktop\build\libs\*.jar" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) {
    Write-Host "desktop-smoke: FAIL (no jar produced under desktop/build/libs)"
    exit 1
}
Write-Host "desktop-smoke: launching $($jar.Name), target $Frames frames"

$stdout = New-TemporaryFile
$stderr = New-TemporaryFile
$process = Start-Process -FilePath 'java' `
    -ArgumentList @("-Dsmoke.frames=$Frames", '-jar', $jar.FullName) `
    -PassThru -NoNewWindow `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr

if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
    Write-Host "desktop-smoke: FAIL (timed out after ${TimeoutSeconds}s)"
    $process | Stop-Process -Force
    Get-Content $stderr | Select-Object -Last 40
    Remove-Item $stdout, $stderr -Force -ErrorAction SilentlyContinue
    exit 1
}

$out = Get-Content $stdout -Raw
$err = Get-Content $stderr -Raw
Remove-Item $stdout, $stderr -Force -ErrorAction SilentlyContinue

if ($process.ExitCode -ne 0) {
    Write-Host "desktop-smoke: FAIL (exit $($process.ExitCode))"
    Write-Host $err
    exit 1
}
# A zero exit without the marker means the game closed on its own rather than the
# watchdog confirming a frame. Treating that as PASS would recreate the PID-alive
# check this script exists to replace.
if ($out -notmatch 'SMOKE: reached frame') {
    Write-Host "desktop-smoke: FAIL (exited 0 but never confirmed a rendered frame)"
    Write-Host $out
    Write-Host $err
    exit 1
}

Write-Host "desktop-smoke: PASS"
exit 0
