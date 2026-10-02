# Abre el puerto 8765 del firewall de Windows para Interfon (requiere admin).
# Uso manual: clic derecho "Ejecutar con PowerShell" como administrador, o via UAC.
netsh advfirewall firewall delete rule name="Interfon 8765" | Out-Null
$r = netsh advfirewall firewall add rule name="Interfon 8765" dir=in action=allow protocol=TCP localport=8765 profile=any
Write-Host ($r -join " ")
Write-Host ""
Write-Host "Listo. El telefono ya deberia poder conectar a este PC por el puerto 8765."
Write-Host "Puedes cerrar esta ventana."
Start-Sleep -Seconds 5
