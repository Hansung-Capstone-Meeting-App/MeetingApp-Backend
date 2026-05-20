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

.\gradlew.bat bootRun --args="--server.port=8080 --spring.devtools.restart.enabled=false --spring.devtools.livereload.enabled=false --spring.datasource.url=jdbc:h2:mem:meetingapp;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_LOWER=TRUE --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --jwt.secret=abcdefghijklmnopqrstuvwxyz123456 --cloud.aws.credentials.access-key=dummy --cloud.aws.credentials.secret-key=dummy --ai.assemblyai.api-key=dummy --ai.gemini.api-key=dummy"
