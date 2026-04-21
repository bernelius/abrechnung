# PowerShell script to create a Windows shortcut (.lnk) for abrechnung
# with embedded icon
# Usage: Run from the target directory where the shortcut should be created

$buildDir = Get-Location

$WshShell = New-Object -ComObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut("$buildDir\Abrechnung.lnk")
$Shortcut.TargetPath = "$buildDir\launch.bat"
$Shortcut.WorkingDirectory = "$buildDir"
$Shortcut.IconLocation = "$buildDir\abrechnung_dllar_logo.ico"
$Shortcut.Save()

Write-Host "Shortcut created: $buildDir\Abrechnung.lnk"
