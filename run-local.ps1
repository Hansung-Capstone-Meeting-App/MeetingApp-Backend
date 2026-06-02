$source = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = "C:\dev\MeetingApp-Backend"

Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object {
        Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
    }

Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*$target*" } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }

if (Test-Path $target) {
    Remove-Item -Recurse -Force $target -ErrorAction SilentlyContinue
}

New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
robocopy $source $target /E /XD .gradle build .git /NFL /NDL /NJH /NJS /NP | Out-Null

Set-Location $target

.\gradlew.bat bootRun --args="--server.port=8080 --spring.devtools.restart.enabled=false --spring.devtools.livereload.enabled=false"
