param([switch]$PularTestes)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw "Instale o JDK 17 ou superior e configure o PATH." }
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) { throw "O jpackage não foi encontrado. Use um JDK completo, não apenas um JRE." }
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw "Instale o Apache Maven ou compile pelo painel Maven do IntelliJ." }
if ($PularTestes) { & mvn -B -DskipTests package } else { & mvn -B verify }
if ($LASTEXITCODE -ne 0) { throw "A compilação ou os testes falharam." }
$inputDir = Join-Path (Get-Location) "target\package-input"
New-Item -ItemType Directory -Force $inputDir | Out-Null
Copy-Item "target\materia-1.0.0.jar" (Join-Path $inputDir "materia.jar") -Force
$output = Join-Path (Get-Location) "dist"
New-Item -ItemType Directory -Force $output | Out-Null
if (Test-Path "$output\Materia") { Remove-Item "$output\Materia" -Recurse -Force }
& jpackage --type app-image --name Materia --input $inputDir --main-jar materia.jar --dest $output --icon "native\materia.ico" --app-version 1.0.0 --vendor Materia --java-options "-Dfile.encoding=UTF-8" --java-options "-Xmx512m"
if ($LASTEXITCODE -ne 0) { throw "O jpackage falhou. Execute no Windows com JDK 17 ou superior." }
Copy-Item "LEIA-ME-PRIMEIRO.txt", "THIRD-PARTY-NOTICES.md" "$output\Materia\"
Compress-Archive -Path "$output\Materia" -DestinationPath "$output\Materia-1.0.0-Windows-x64.zip" -Force
Write-Host "Pronto: dist\Materia\Materia.exe e dist\Materia-1.0.0-Windows-x64.zip"
