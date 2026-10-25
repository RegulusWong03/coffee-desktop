using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using CoffeeDesktop.Models;

namespace CoffeeDesktop.Services;

public interface IActionExecutor { void Execute(AppSettings settings); }

public sealed class ProcessActionExecutor : IActionExecutor
{
    public void Execute(AppSettings settings)
    {
        var path = settings.BoundExecutable;
        if (!settings.LaunchOnClick || string.IsNullOrWhiteSpace(path) || !File.Exists(path)) return;
        var fullPath = Path.GetFullPath(path);
        var candidateWindow = IntPtr.Zero;
        foreach (var process in Process.GetProcesses())
        {
            try
            {
                if (TryGetImagePath(process, out var image) && string.Equals(Path.GetFullPath(image), fullPath, StringComparison.OrdinalIgnoreCase))
                {
                    var hwnd = process.MainWindowHandle;
                    if (hwnd != IntPtr.Zero && IsWindowVisible(hwnd) && candidateWindow == IntPtr.Zero) candidateWindow = hwnd;
                }
            }
            catch { }
            finally { process.Dispose(); }
        }
        if (candidateWindow != IntPtr.Zero)
        {
            if (IsIconic(candidateWindow)) ShowWindow(candidateWindow, 9);
            if (SetForegroundWindow(candidateWindow)) return;
        }

        var info = new ProcessStartInfo(fullPath) { UseShellExecute = true };
        if (!string.IsNullOrWhiteSpace(settings.BoundArguments)) info.Arguments = settings.BoundArguments;
        if (!string.IsNullOrWhiteSpace(settings.BoundWorkingDirectory)) info.WorkingDirectory = settings.BoundWorkingDirectory;
        else info.WorkingDirectory = Path.GetDirectoryName(fullPath)!;
        try { Process.Start(info); }
        catch (Exception ex) { System.Windows.MessageBox.Show($"无法启动绑定的程序。\n{ex.Message}", "咖啡", System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Warning); }
    }

    private static bool TryGetImagePath(Process process, out string path)
    {
        try { path = process.MainModule?.FileName ?? ""; return path.Length > 0; }
        catch { path = ""; return false; }
    }

    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern bool IsIconic(IntPtr hWnd);
    [DllImport("user32.dll")] private static extern bool ShowWindow(IntPtr hWnd, int command);
}
