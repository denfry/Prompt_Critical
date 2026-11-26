# Push the Minecraft client windows to the bottom of the z-order without activating them, so the game never covers
# the owner's work. Usage: powershell -File art/film/sink_window.ps1 [-Seconds N]  (keeps doing it for N seconds:
# the window appears and may raise itself while it loads)
param([int]$Seconds = 0)
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class Sink {
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
}
"@
$bottom = [IntPtr]1
$flags = 0x0001 -bor 0x0002 -bor 0x0010   # SWP_NOSIZE | SWP_NOMOVE | SWP_NOACTIVATE
$end = (Get-Date).AddSeconds($Seconds)
do {
    Get-Process java, javaw -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like 'Minecraft*' } |
        ForEach-Object { [void][Sink]::SetWindowPos($_.MainWindowHandle, $bottom, 0, 0, 0, 0, $flags) }
    if ($Seconds -gt 0) { Start-Sleep -Milliseconds 1500 }
} while ((Get-Date) -lt $end)
