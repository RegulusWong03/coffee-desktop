using System.Runtime.InteropServices;
using System.Windows;
using Point = System.Windows.Point;

namespace CoffeeDesktop.Services;

public static class FullscreenMonitor
{
    public static bool IsForegroundFullscreen(Window pet)
    {
        var foreground = GetForegroundWindow();
        if (foreground == IntPtr.Zero || foreground == new System.Windows.Interop.WindowInteropHelper(pet).Handle) return false;
        var ownPid = Environment.ProcessId;
        GetWindowThreadProcessId(foreground, out var pid);
        if (pid == ownPid) return false;
        var cls = new System.Text.StringBuilder(256);
        GetClassName(foreground, cls, cls.Capacity);
        if (cls.ToString() is "Progman" or "WorkerW" or "Shell_TrayWnd" or "DV2ControlHost") return false;
        if (!GetWindowRect(foreground, out var rect)) return false;
        var center = pet.PointToScreen(new Point(pet.Width / 2, pet.Height / 2));
        var monitor = MonitorFromPoint(new PointNative((int)center.X, (int)center.Y), 2);
        var info = new MonitorInfo { Size = Marshal.SizeOf<MonitorInfo>() };
        if (!GetMonitorInfo(monitor, ref info)) return false;
        return Math.Abs(rect.Left - info.Monitor.Left) <= 3 && Math.Abs(rect.Top - info.Monitor.Top) <= 3 &&
               Math.Abs(rect.Right - info.Monitor.Right) <= 3 && Math.Abs(rect.Bottom - info.Monitor.Bottom) <= 3;
    }

    [StructLayout(LayoutKind.Sequential)] private struct PointNative(int x, int y) { public int X = x; public int Y = y; }
    [StructLayout(LayoutKind.Sequential)] private struct Rect { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)] private struct MonitorInfo
    { public int Size; public Rect Monitor; public Rect Work; public uint Flags; }
    [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hwnd, out int pid);
    [DllImport("user32.dll", CharSet = CharSet.Auto)] private static extern int GetClassName(IntPtr hwnd, System.Text.StringBuilder name, int max);
    [DllImport("user32.dll")] private static extern bool GetWindowRect(IntPtr hwnd, out Rect rect);
    [DllImport("user32.dll")] private static extern IntPtr MonitorFromPoint(PointNative point, uint flags);
    [DllImport("user32.dll", CharSet = CharSet.Auto)] private static extern bool GetMonitorInfo(IntPtr monitor, ref MonitorInfo info);
}
