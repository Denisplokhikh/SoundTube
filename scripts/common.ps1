<#
    Общие помощники для скриптов сборки.

    PowerShell 5.1 считает любую запись нативной команды в stderr ошибкой и при
    `$ErrorActionPreference = "Stop"` обрывает скрипт — даже если команда
    завершилась успешно. git, uv, cargo и npm пишут в stderr постоянно
    (прогресс, предупреждения), поэтому «просто запустить» сборку не получалось.

    [Invoke-Native] гасит это поведение на время вызова и решает по коду
    возврата: stderr показывается, но успешный шаг не считается падением.
#>

function Invoke-Native {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @(),
        # Вернуть stdout вместо того, чтобы печатать его в консоль.
        [switch]$Capture
    )

    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        if ($Capture) {
            # stderr нужен только для человека: в переменную он не попадает.
            $output = & $FilePath @Arguments 2>$null
            $code = $LASTEXITCODE
        } else {
            & $FilePath @Arguments
            $code = $LASTEXITCODE
        }
    } finally {
        $ErrorActionPreference = $previous
    }

    if ($code -ne 0) {
        $command = (@($FilePath) + $Arguments) -join " "
        throw "Команда завершилась с кодом $code`: $command"
    }

    if ($Capture) { return $output }
}
