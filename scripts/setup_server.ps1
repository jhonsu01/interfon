# ============================================================
# Interfon - preparacion del servidor (PowerShell 5.1 compatible)
# Uso:
#   .\setup_server.ps1             -> venv + dependencias + .env
#   .\setup_server.ps1 -Firewall   -> ademas abre el puerto 8765 (requiere admin)
# ============================================================
param([switch]$Firewall)
$ErrorActionPreference = "Stop"

$root     = Split-Path -Parent $PSScriptRoot
$serverDir = Join-Path $root "server"
$venv     = Join-Path $serverDir ".venv"
$py       = Join-Path $venv "Scripts\python.exe"

Write-Host "== Interfon: preparando servidor =="

if (-not (Test-Path $venv)) {
    Write-Host "Creando entorno virtual..."
    python -m venv $venv
    if ($LASTEXITCODE -ne 0) { throw "No se pudo crear el venv. Revisa que 'python' este en PATH." }
}

Write-Host "Instalando dependencias..."
& $py -m pip install --upgrade pip --quiet
& $py -m pip install -r (Join-Path $serverDir "requirements.txt") --quiet
if ($LASTEXITCODE -ne 0) { throw "Fallo pip install." }

$envFile = Join-Path $serverDir ".env"
if (-not (Test-Path $envFile)) {
    Copy-Item (Join-Path $serverDir ".env.example") $envFile
    Write-Host "Creado server\.env con valores por defecto."
}

# Voz del agente (Piper, es_AR daniela) desde HuggingFace
$voice = Join-Path $serverDir "voices\es_AR-daniela-high.onnx"
if (-not (Test-Path $voice)) {
    Write-Host "Descargando voz argentina daniela (HuggingFace, ~114 MB)..."
    & $py (Join-Path $PSScriptRoot "download_voice.py") daniela
    if ($LASTEXITCODE -ne 0) { Write-Host "AVISO: no se pudo bajar la voz; se usara la voz SAPI de Windows." -ForegroundColor Yellow }
}

if ($Firewall) {
    $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Write-Host "AVISO: -Firewall necesita admin. Ejecuta la consola como administrador o abre el puerto a mano:" -ForegroundColor Yellow
        Write-Host '  netsh advfirewall firewall add rule name="Interfon 8765" dir=in action=allow protocol=TCP localport=8765'
    } else {
        netsh advfirewall firewall delete rule name="Interfon 8765" | Out-Null
        netsh advfirewall firewall add rule name="Interfon 8765" dir=in action=allow protocol=TCP localport=8765 | Out-Null
        Write-Host "Puerto 8765 abierto en el firewall."
    }
}

$ip = (Get-NetIPAddress -AddressFamily IPv4 |
       Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254*" } |
       Select-Object -First 1).IPAddress

Write-Host ""
Write-Host "== Listo. Para arrancar el servidor: =="
Write-Host "  cd server"
Write-Host "  .\.venv\Scripts\python.exe serve.py"
Write-Host ""
Write-Host ("En la app Android, usa esta direccion del servidor:  http://" + $ip + ":8765")
