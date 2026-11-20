# Push the Minecraft window under all other windows without activating it, so a film run does not get in the way.
# FilmRecorder renders into its own framebuffer, so the window may be covered. Waits up to -Wait seconds for it.
#   pwsh art/film/window_bottom.ps1 [-Wait 180]
param([int]$Wait = 180)
Add-Type @'
using System; using System.Runtime.InteropServices; using System.Text;
public static class FilmWindow {
  public delegate bool EnumProc(IntPtr h, IntPtr p);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc f, IntPtr p);
  [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr a, int x, int y, int cx, int cy, uint f);
}
'@
$HWND_BOTTOM = [IntPtr]1
$FLAGS = 0x0013  # SWP_NOSIZE | SWP_NOMOVE | SWP_NOACTIVATE
$deadline = (Get-Date).AddSeconds($Wait)
do {
    $found = [System.Collections.Generic.List[IntPtr]]::new()
    [void][FilmWindow]::EnumWindows({
        param($h, $p)
        $sb = [Text.StringBuilder]::new(256)
        [void][FilmWindow]::GetWindowText($h, $sb, 256)
        if ([FilmWindow]::IsWindowVisible($h) -and $sb.ToString() -like 'Minecraft*') { $found.Add($h) }
        $true
    }, [IntPtr]::Zero)
    if ($found.Count) {
        foreach ($h in $found) { [void][FilmWindow]::SetWindowPos($h, $HWND_BOTTOM, 0, 0, 0, 0, $FLAGS) }
        "Minecraft window sent to the bottom"
        exit 0
    }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
"no Minecraft window within $Wait s"
exit 1
