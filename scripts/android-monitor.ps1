<#
.SYNOPSIS
    «Диспетчер задач» для приложения на подключённом по USB телефоне.

.DESCRIPTION
    Раз в N секунд печатает строку: резидентная память, число потоков, загрузка CPU
    и скорость сети (суммарно по устройству — yt-dlp качает нативным процессом,
    поэтому его трафик виден только здесь, а не в Network Inspector студии).

    Данные читаются из /proc:<pid>/stat|status и /proc/net/dev, отдельных dumpsys
    не требуется, поэтому накладные расходы минимальны.

.EXAMPLE
    .\scripts\android-monitor.ps1

.EXAMPLE
    .\scripts\android-monitor.ps1 -Interval 1 -Count 30
#>
param(
    [string]$Package = "com.example.localplayer",
    [string]$Adb = "",
    [double]$Interval = 2,
    [int]$Count = 0
)

$ErrorActionPreference = "Stop"

function Resolve-Adb([string]$explicit) {
    if ($explicit) { return $explicit }

    $roots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")
    foreach ($root in $roots) {
        if (-not $root) { continue }
        $candidate = Join-Path $root "platform-tools\adb.exe"
        if (Test-Path $candidate) { return $candidate }
    }

    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "adb не найден. Укажите путь: -Adb C:\path\to\adb.exe"
}

function Invoke-AdbShell([string]$adbPath, [string]$script) {
    $lines = & $adbPath shell $script 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($lines -join " ") }
    return @($lines | ForEach-Object { $_.TrimEnd("`r") })
}

function Get-Pid([string]$adbPath) {
    $out = Invoke-AdbShell $adbPath "pidof $Package"
    $text = ($out | Select-Object -First 1).Trim()
    if (-not $text) { return $null }
    # pidof может вернуть несколько pid через пробел — берём первый.
    return [int]($text -split '\s+')[0]
}

# Один замер: поля /proc/<pid>/stat, /proc/<pid>/status, /proc/net/dev, /proc/uptime.
function Get-Sample([string]$adbPath, [int]$appPid) {
    $raw = Invoke-AdbShell $adbPath (
        "cat /proc/$appPid/stat; echo '#SPLIT#'; grep -E '^(VmRSS|Threads)' /proc/$appPid/status; " +
        "echo '#SPLIT#'; cat /proc/net/dev; echo '#SPLIT#'; cat /proc/uptime"
    )
    $text = $raw -join "`n"
    $parts = $text -split "#SPLIT#"
    if ($parts.Count -lt 4) { return $null }

    # /proc/<pid>/stat: имя процесса в скобках и может содержать пробелы,
    # поэтому поля считаем от закрывающей скобки. utime/stime — 14 и 15.
    $stat = $parts[0]
    $tail = $stat.Substring($stat.LastIndexOf(')') + 1).Trim() -split '\s+'
    $cpuTicks = [double]$tail[11] + [double]$tail[12]

    $rssKb = 0; $threads = 0
    foreach ($line in ($parts[1] -split "`n")) {
        if ($line -match '^VmRSS:\s+(\d+)') { $rssKb = [int]$Matches[1] }
        if ($line -match '^Threads:\s+(\d+)') { $threads = [int]$Matches[1] }
    }

    $rx = [double]0; $tx = [double]0
    foreach ($line in ($parts[2] -split "`n")) {
        if ($line -notmatch '^\s*([^\s:]+):\s*(.+)$') { continue }
        if ($Matches[1] -eq 'lo') { continue }
        $fields = $Matches[2].Trim() -split '\s+'
        if ($fields.Count -lt 9) { continue }
        $rx += [double]$fields[0]
        $tx += [double]$fields[8]
    }

    $uptime = [double](($parts[3] -split '\s+')[0])

    return [pscustomobject]@{
        CpuTicks = $cpuTicks
        Uptime   = $uptime
        RssMb    = [math]::Round($rssKb / 1024, 1)
        Threads  = $threads
        Rx       = $rx
        Tx       = $tx
    }
}

function Format-Rate([double]$bytesPerSecond) {
    if ($bytesPerSecond -ge 1MB) { return "{0,6:0.0} МБ/с" -f ($bytesPerSecond / 1MB) }
    if ($bytesPerSecond -ge 1KB) { return "{0,6:0} КБ/с" -f ($bytesPerSecond / 1KB) }
    return "{0,6:0} Б/с " -f $bytesPerSecond
}

$adbPath = Resolve-Adb $Adb

$devices = (& $adbPath devices) | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' }
if (-not $devices) { throw "Нет подключённого устройства. Включите отладку по USB и подтвердите запрос на телефоне." }

Write-Host "Пакет: $Package (Ctrl+C — выход)" -ForegroundColor DarkGray
Write-Host ("{0}  {1,9}  {2,7}  {3,6}  {4,11}  {5,11}" -f "время", "память", "потоки", "CPU", "приём", "отдача") -ForegroundColor DarkGray

$prev = $null
$ticks = 0

while ($Count -le 0 -or $ticks -lt $Count) {
    $ticks++

    $appPid = Get-Pid $adbPath
    if (-not $appPid) {
        Write-Host ("{0}  приложение не запущено" -f (Get-Date -Format "HH:mm:ss")) -ForegroundColor DarkYellow
        $prev = $null
        if ($Count -le 0 -or $ticks -lt $Count) { Start-Sleep -Seconds $Interval }
        continue
    }

    $sample = Get-Sample $adbPath $appPid
    if (-not $sample) {
        Start-Sleep -Seconds $Interval
        continue
    }

    $cpuPercent = [double]0
    $rxRate = [double]0
    $txRate = [double]0
    if ($prev) {
        $elapsed = $sample.Uptime - $prev.Uptime
        if ($elapsed -gt 0) {
            # 100 тиков в секунду (USER_HZ) — так же считает top.
            $cpuPercent = (($sample.CpuTicks - $prev.CpuTicks) / 100.0) / $elapsed * 100.0
            $rxRate = ($sample.Rx - $prev.Rx) / $elapsed
            $txRate = ($sample.Tx - $prev.Tx) / $elapsed
        }
    }

    $color = if ($cpuPercent -ge 80) { "Red" } elseif ($cpuPercent -ge 40) { "Yellow" } else { "Gray" }
    Write-Host ("{0}  {1,7} МБ  {2,7}  {3,5:0.0}%  {4}  {5}" -f `
        (Get-Date -Format "HH:mm:ss"), $sample.RssMb, $sample.Threads, $cpuPercent, `
        (Format-Rate $rxRate), (Format-Rate $txRate)) -ForegroundColor $color

    $prev = $sample
    if ($Count -le 0 -or $ticks -lt $Count) { Start-Sleep -Milliseconds ([int]($Interval * 1000)) }
}
