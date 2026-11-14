# Publishes a new version of the app to GitHub Releases.
# The app checks the latest release on start and offers to update.
#
# Usage:  publicar.bat                (asks what changed)
#         publicar.bat -Notas "texto" (no prompt)
param([string]$Notas = "")

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$dev = Join-Path $env:USERPROFILE 'AndroidDev'
$env:JAVA_HOME = Join-Path $dev 'jdk17'
$gh = Join-Path $dev 'gh\bin\gh.exe'
$versao = "Vers$([char]0xE3)o"

$repoMatch = Select-String -Path (Join-Path $root 'gradle.properties') -Pattern '^aguaUpdateRepo=(.+)$'
if (-not $repoMatch) { throw "aguaUpdateRepo nao configurado em gradle.properties" }
$repo = $repoMatch.Matches[0].Groups[1].Value.Trim()

if (-not $Notas) { $Notas = Read-Host "O que mudou nesta versao? (Enter para pular)" }

# Bump versionCode
$vfile = Join-Path $root 'version.properties'
$old = [int]((Get-Content $vfile | Where-Object { $_ -match '^versionCode=' }) -replace 'versionCode=', '')
$new = $old + 1
Set-Content $vfile "versionCode=$new" -Encoding ascii
Write-Host "Compilando $versao 1.$new ..."

& (Join-Path $root 'gradlew.bat') -p $root assembleDebug --console=plain -q
if ($LASTEXITCODE -ne 0) {
    Set-Content $vfile "versionCode=$old" -Encoding ascii
    throw "Build falhou - versao nao publicada"
}

$outDir = Join-Path $root 'app\build\outputs\apk\debug'
$apk = Join-Path $outDir "agua-$new.apk"
Copy-Item (Join-Path $outDir 'app-debug.apk') $apk -Force
Copy-Item $apk (Join-Path $root 'Agua.apk') -Force

if (-not $Notas) { $Notas = "$versao 1.$new" }
$notesFile = Join-Path $env:TEMP "agua-notes-$new.txt"
[IO.File]::WriteAllText($notesFile, $Notas, (New-Object System.Text.UTF8Encoding($false)))

& $gh release create "v$new" $apk --repo $repo --title "$versao 1.$new" --notes-file $notesFile
if ($LASTEXITCODE -ne 0) { throw "Falha ao publicar no GitHub" }
Remove-Item $notesFile -ErrorAction SilentlyContinue

# If the code itself lives in the repo, push it too
if (Test-Path (Join-Path $root '.git')) {
    git -C $root add -A
    git -C $root commit -q -m "$versao 1.$new"
    git -C $root push -q
}

Write-Host ""
Write-Host "Publicado: $versao 1.$new. Abra o app no celular para atualizar."
