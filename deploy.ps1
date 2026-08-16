<#
    Copyright (c) 2026 NoMercy Labs
    SPDX-License-Identifier: MIT

    Build, install and launch NoMercy Browser on one or more Android TV devices.

    .EXAMPLE
    ./deploy.ps1
    ./deploy.ps1 -Connect 192.168.2.80:5555 -Clean -Screenshot shot.png
    ./deploy.ps1 -Device 192.168.2.80:5555 -Logs
#>
[CmdletBinding()]
param(
    # Target these devices. Defaults to every connected device.
    [string[]] $Device = @(),

    # adb connect before deploying.
    [string[]] $Connect = @(),

    # Build release instead of debug.
    [switch] $Release,

    # Uninstall first, so the app starts with no stored state. Use when tab,
    # cookie or settings storage changes shape.
    [switch] $Clean,

    # Install without starting the app.
    [switch] $NoLaunch,

    # Follow the app's logcat after launch.
    [switch] $Logs,

    # Capture the screen after launch. With several devices the serial is
    # appended to the filename.
    [string] $Screenshot
)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$applicationId = 'com.nomercylabs.browser'
$activity = '.MainActivity'

if ($env:NM_TV_DEVICES) {
    $Connect += ($env:NM_TV_DEVICES -split '\s+' | Where-Object { $_ })
}

foreach ($target in $Connect) {
    Write-Host "connect  $target"
    & adb connect $target *> $null
}

if (-not $Device -or $Device.Count -eq 0) {
    $Device = & adb devices |
        Select-Object -Skip 1 |
        Where-Object { $_ -match '^\S+\s+device$' } |
        ForEach-Object { ($_ -split '\s+')[0] }
}

if (-not $Device -or $Device.Count -eq 0) {
    Write-Error 'No devices. Connect one, or pass -Connect host:port.'
}

# The debug build carries an applicationIdSuffix, so installing a debug build
# and then launching the release package silently starts the wrong app, or
# nothing at all.
if ($Release) {
    $package = $applicationId
    $gradleTask = 'assembleRelease'
    $apk = 'app/build/outputs/apk/release/app-release.apk'
} else {
    $package = "$applicationId.debug"
    $gradleTask = 'assembleDebug'
    $apk = 'app/build/outputs/apk/debug/app-debug.apk'
}

Write-Host "build    $gradleTask"
& ./gradlew.bat $gradleTask --console=plain -q
if ($LASTEXITCODE -ne 0) { Write-Error "Gradle task $gradleTask failed." }

if (-not (Test-Path $apk)) {
    Write-Error "No APK at $apk. The release build is unsigned unless NM_KEYSTORE_PATH is set."
}

$apkFull = (Resolve-Path $apk).Path

$Device | ForEach-Object -Parallel {
    $serial = $_
    $label = "[$serial]"

    if ($using:Clean) {
        & adb -s $serial uninstall $using:package *> $null
        Write-Host "$label uninstalled"
    }

    & adb -s $serial install -r -d $using:apkFull *> $null
    if ($LASTEXITCODE -ne 0) {
        # A signature change is the usual reason a reinstall fails, and it is
        # not recoverable without removing the old app.
        Write-Host "$label install failed, retrying after uninstall"
        & adb -s $serial uninstall $using:package *> $null
        & adb -s $serial install $using:apkFull *> $null
        if ($LASTEXITCODE -ne 0) { Write-Host "$label install FAILED"; return }
    }
    Write-Host "$label installed"

    if (-not $using:NoLaunch) {
        # Cleared so the Displayed line we wait for is this launch's, not a
        # previous one still sitting in the buffer.
        & adb -s $serial logcat -c *> $null
        & adb -s $serial shell am start -n "$($using:package)/$($using:applicationId)$($using:activity)" *> $null
        Write-Host "$label launched"
    }
} -ThrottleLimit 8

# Waits until the first frame is actually on screen.
#
# topResumedActivity is set before anything is drawn, so waiting on it captures
# whatever was previously on screen and looks exactly like a failed launch.
# ActivityTaskManager logs "Displayed <component>" when the first frame lands,
# which is the only signal that means what we need it to mean.
function Wait-ForDisplayed {
    param([string] $Serial, [string] $Package)
    for ($i = 0; $i -lt 60; $i++) {
        $lines = & adb -s $Serial logcat -d -s ActivityTaskManager:I 2>$null
        if ($lines -match "Displayed $([regex]::Escape($Package))/") { return $true }
        Start-Sleep -Milliseconds 500
    }
    Write-Warning "[$Serial] no Displayed line, falling back"
    Start-Sleep -Seconds 2
    return $false
}

if ($Screenshot -and -not $NoLaunch) {
    foreach ($serial in $Device) {
        [void] (Wait-ForDisplayed -Serial $serial -Package $package)
        $out = $Screenshot
        if ($Device.Count -gt 1) {
            $safe = $serial -replace '[:.]', '_'
            $out = [System.IO.Path]::ChangeExtension($Screenshot, $null).TrimEnd('.') +
                   "-$safe" + [System.IO.Path]::GetExtension($Screenshot)
        }
        & adb -s $serial exec-out screencap -p | Set-Content -Path $out -AsByteStream
        Write-Host "[$serial] screenshot $out"
    }
}

if ($Logs) {
    $serial = $Device[0]
    Write-Host "logs     $serial (ctrl-c to stop)"
    $processId = (& adb -s $serial shell pidof $package).Trim()
    if ($processId) {
        & adb -s $serial logcat --pid=$processId
    } else {
        & adb -s $serial logcat
    }
}
