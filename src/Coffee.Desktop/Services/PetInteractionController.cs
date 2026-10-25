using System.Windows;
using System.Windows.Input;
using System.Runtime.InteropServices;
using Point = System.Windows.Point;

namespace CoffeeDesktop.Services;

public sealed class PetInteractionController
{
    private Point _down;
    private Point _downScreen;
    private Point _windowOrigin;
    private bool _dragged;
    private bool _tracking;
    public event Action? Clicked;
    public event Action? ContextRequested;
    public event Action<int, int>? DragMoved;
    public bool IsTracking => _tracking;

    public void PointerDown(Window window, UIElement captureTarget, Point position)
    {
        _down = position;
        _downScreen = CursorPosition();
        _windowOrigin = window.PointToScreen(new Point(0, 0));
        _dragged = false;
        _tracking = Mouse.Capture(captureTarget, CaptureMode.Element);
    }
    public bool PointerMove(Point position)
    {
        if (!_tracking || Mouse.LeftButton != MouseButtonState.Pressed) return false;
        var cursor = CursorPosition();
        var dx = cursor.X - _downScreen.X;
        var dy = cursor.Y - _downScreen.Y;
        if (!_dragged && Math.Abs(position.X - _down.X) < 5 && Math.Abs(position.Y - _down.Y) < 5) return false;
        _dragged = true;
        DragMoved?.Invoke((int)Math.Round(_windowOrigin.X + dx), (int)Math.Round(_windowOrigin.Y + dy));
        return true;
    }
    public void PointerUp(Point position)
    {
        if (!_dragged && Math.Abs(position.X - _down.X) < 5 && Math.Abs(position.Y - _down.Y) < 5) Clicked?.Invoke();
        _dragged = false; _tracking = false;
        if (Mouse.Captured is not null) Mouse.Capture(null);
    }
    public void RequestContext() => ContextRequested?.Invoke();

    private static Point CursorPosition()
    {
        GetCursorPos(out var point);
        return new Point(point.X, point.Y);
    }

    [StructLayout(LayoutKind.Sequential)] private struct NativePoint { public int X, Y; }
    [DllImport("user32.dll")] private static extern bool GetCursorPos(out NativePoint point);
}
