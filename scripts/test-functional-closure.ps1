param([string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin', [int]$Port = 55462, [switch]$AllTests)
$ErrorActionPreference = 'Stop'
$previousEnvironment=@{}
foreach ($name in @('CORONYX_TEST_DB_URL','CORONYX_TEST_STORAGE','JAVA_HOME')) { $previousEnvironment[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
$repoRoot = Split-Path $PSScriptRoot -Parent
$testRoot = Join-Path $repoRoot ('target\closure-' + [guid]::NewGuid().ToString('N'))
$pgData = Join-Path $testRoot 'pg-data'
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
$started = $false
try {
    & (Join-Path $PostgresBin 'initdb.exe') -D $pgData -U postgres -A trust --encoding=UTF8 --locale=C *> (Join-Path $testRoot 'initdb.log')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base temporal. Revisar initdb.log.' }
    & (Join-Path $PostgresBin 'pg_ctl.exe') -D $pgData -l (Join-Path $testRoot 'postgres.log') -o "-h 127.0.0.1 -p $Port" -w start
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar la base temporal.' }
    $started = $true
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $Port -U postgres coronyx_closure
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo conectar a la base temporal.' }
    $env:CORONYX_TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$Port/coronyx_closure"
    $env:CORONYX_TEST_STORAGE = Join-Path $testRoot 'storage'
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
    $taskMaven = (Get-ChildItem (Join-Path $env:USERPROFILE '.m2\wrapper\dists\apache-maven-3.9.16\*\bin\mvn.cmd') | Select-Object -First 1).FullName
    if (!$taskMaven) { throw 'Primero inicialice Maven Wrapper del proyecto.' }
    $agent = (Get-ChildItem (Join-Path $env:USERPROFILE '.m2\repository\net\bytebuddy\byte-buddy-agent\*\byte-buddy-agent-*.jar') | Select-Object -Last 1).FullName
    Push-Location $repoRoot
    try {
        $arguments = @('-o', "-Dspring.datasource.url=$env:CORONYX_TEST_DB_URL", '-Dspring.datasource.username=postgres', '-Dspring.datasource.password=', "-Dstorage.local.directory=$env:CORONYX_TEST_STORAGE")
        if (!$AllTests) { $arguments += '-Dtest=FunctionalClosureTest,FunctionalClosureIntegrationTest,InventoryAndTeleconsultationTest' }
        if ($agent) { $arguments += "-DargLine=-javaagent:`"$agent`"" }
        $arguments += 'test'
        & $taskMaven @arguments *> (Join-Path $testRoot 'maven.log')
        $testExitCode=$LASTEXITCODE
        Get-Content (Join-Path $testRoot 'maven.log') -Tail 18
        if ($testExitCode -ne 0) { throw 'Fallaron las pruebas. Revisar target/surefire-reports.' }
    } finally { Pop-Location }
} finally {
    if ($started) { & (Join-Path $PostgresBin 'pg_ctl.exe') -D $pgData -m fast -w stop }
    Remove-Item Env:CORONYX_TEST_DB_URL -ErrorAction SilentlyContinue
    Remove-Item Env:CORONYX_TEST_STORAGE -ErrorAction SilentlyContinue
    Write-Host "Evidencia temporal: $testRoot"
    foreach ($name in $previousEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name,$previousEnvironment[$name],'Process') }
}
