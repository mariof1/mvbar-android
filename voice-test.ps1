<#
.SYNOPSIS
    mvbar Voice Command Tester for Android Auto DHU
.DESCRIPTION
    Sends voice commands to the mvbar app via adb without needing a microphone.
    Works with both physical devices and the Desktop Head Unit emulator.
.EXAMPLE
    .\voice-test.ps1 play "bohemian rhapsody"
    .\voice-test.ps1 shuffle favorites
    .\voice-test.ps1 pause
    .\voice-test.ps1 next
    .\voice-test.ps1 nowplaying
#>

param(
    [Parameter(Position=0)]
    [ValidateSet("play","search","pause","resume","next","skip","previous","prev","shuffle","playlist","nowplaying","what","help")]
    [string]$Command = "help",

    [Parameter(Position=1, ValueFromRemainingArguments)]
    [string[]]$QueryWords
)

$pkg = "com.mvbar.android"
$svc = "$pkg/.player.PlaybackService"
$action = "$pkg.VOICE_COMMAND"

function Send-VoiceCommand {
    param([string]$Cmd, [string]$Query = "")
    
    $args_list = @("shell", "am", "start-foreground-service")
    $args_list += @("-a", $action)
    $args_list += @("--es", "command", $Cmd)
    if ($Query) {
        $args_list += @("--es", "query", $Query)
    }
    $args_list += @("-n", $svc)
    
    Write-Host "  > $Cmd$(if($Query){" '$Query'"})" -ForegroundColor Cyan
    $result = & adb @args_list 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  OK" -ForegroundColor Green
    } else {
        Write-Host "  FAILED: $result" -ForegroundColor Red
    }
}

function Show-Help {
    Write-Host ""
    Write-Host "mvbar Voice Command Tester" -ForegroundColor Yellow
    Write-Host "=========================" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Usage: .\voice-test.ps1 <command> [query]" -ForegroundColor White
    Write-Host ""
    Write-Host "Commands:" -ForegroundColor Cyan
    Write-Host "  play <query>          Search and play a song/artist/album"
    Write-Host "  search <query>        Same as play"
    Write-Host "  shuffle               Toggle shuffle on current queue"
    Write-Host "  shuffle favorites     Shuffle all favorite tracks"
    Write-Host "  shuffle <query>       Search and shuffle results"
    Write-Host "  playlist <name>       Play a playlist (regular or smart) by name"
    Write-Host "  pause                 Pause playback"
    Write-Host "  resume                Resume playback"
    Write-Host "  next / skip           Skip to next track"
    Write-Host "  previous / prev       Go to previous track"
    Write-Host "  nowplaying / what     Show current track (check adb logcat)"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Green
    Write-Host '  .\voice-test.ps1 play "bohemian rhapsody"'
    Write-Host '  .\voice-test.ps1 play metallica'
    Write-Host '  .\voice-test.ps1 shuffle favorites'
    Write-Host '  .\voice-test.ps1 playlist "chill vibes"'
    Write-Host '  .\voice-test.ps1 pause'
    Write-Host '  .\voice-test.ps1 next'
    Write-Host ""
    Write-Host "Tip: Check debug logs with:" -ForegroundColor DarkGray
    Write-Host '  adb shell run-as com.mvbar.android cat files/debug_log.txt | Select-String "Voice"' -ForegroundColor DarkGray
    Write-Host ""
}

if ($Command -eq "help") {
    Show-Help
    return
}

# Join remaining arguments as the query string
$query = if ($QueryWords) { $QueryWords -join " " } else { "" }

Write-Host ""
Write-Host "mvbar Voice Command" -ForegroundColor Yellow
Send-VoiceCommand -Cmd $Command -Query $query

# Show recent voice log entries
Start-Sleep -Milliseconds 1500
Write-Host ""
Write-Host "  Log:" -ForegroundColor DarkGray
$log = adb shell "run-as $pkg cat files/debug_log.txt 2>/dev/null" | Select-String "Voice" | Select-Object -Last 3
if ($log) {
    $log | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
} else {
    Write-Host "  (no voice log entries yet)" -ForegroundColor DarkGray
}
Write-Host ""
