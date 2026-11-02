$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not (Test-Path "target\materia-1.0.0.jar")) {
    & mvn -B package
    if ($LASTEXITCODE -ne 0) { throw "A compilação falhou." }
}
& java "-Dfile.encoding=UTF-8" -jar "target\materia-1.0.0.jar"
