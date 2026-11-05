param([string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin', [int]$DatabasePort=55463, [int]$ApiPort=18081)
$ErrorActionPreference='Stop'
$previousEnvironment=@{}
foreach ($name in @('DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET','CORONYX_STORAGE_PATH')) { $previousEnvironment[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
$repoRoot=Split-Path $PSScriptRoot -Parent
$testRoot=Join-Path $repoRoot ('target\preview-'+[guid]::NewGuid().ToString('N'))
$pgData=Join-Path $testRoot 'pg-data'
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
$started=$false
$appProcess=$null
try {
    & (Join-Path $PostgresBin 'initdb.exe') -D $pgData -U postgres -A trust --encoding=UTF8 --locale=C *> (Join-Path $testRoot 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base temporal' }
    & (Join-Path $PostgresBin 'pg_ctl.exe') -D $pgData -l (Join-Path $testRoot 'postgres.log') -o "-h 127.0.0.1 -p $DatabasePort" -w start
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar PostgreSQL temporal' }
    $started=$true
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $DatabasePort -U postgres coronyx_preview
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base de vista previa' }
    $env:DB_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/coronyx_preview"
    $env:DB_USERNAME='postgres'; $env:DB_PASSWORD=''
    $env:JWT_SECRET='coronyx-preview-only-not-a-production-key-64-characters-for-local-testing'
    $env:CORONYX_STORAGE_PATH=Join-Path $testRoot 'storage'
    $appProcess=Start-Process -FilePath 'C:\Program Files\Java\jdk-21\bin\java.exe' -ArgumentList @('-jar','target/coronyx-backend-0.0.1-SNAPSHOT.jar',"--server.port=$ApiPort",'--server.address=127.0.0.1','--cors.allowed-origins=http://127.0.0.1:5177','--spring.datasource.password=') -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testRoot 'app.log') -RedirectStandardError (Join-Path $testRoot 'app-error.log')
    $ready=$false
    for ($i=0;$i -lt 60;$i++) {
        if ($appProcess.HasExited) { throw 'El backend no inició. Revisar app.log y app-error.log' }
        try { $null=Invoke-RestMethod "http://127.0.0.1:$ApiPort/actuator/health" -TimeoutSec 1; $ready=$true; break } catch { Start-Sleep -Seconds 1 }
    }
    if (!$ready) { throw 'El backend temporal no respondió' }
    & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $DatabasePort -U postgres -d coronyx_preview -v ON_ERROR_STOP=1 -f (Join-Path $PSScriptRoot 'preview-fixture.sql')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron crear las identidades ficticias' }
    Write-Host "Vista previa temporal lista. API: http://127.0.0.1:$ApiPort. Datos: $testRoot"
    Wait-Process -Id $appProcess.Id
} finally {
    if ($appProcess -and !$appProcess.HasExited) { Stop-Process -Id $appProcess.Id -ErrorAction SilentlyContinue }
    if ($started) { & (Join-Path $PostgresBin 'pg_ctl.exe') -D $pgData -m fast -w stop }
    foreach ($name in $previousEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name,$previousEnvironment[$name],'Process') }
}
