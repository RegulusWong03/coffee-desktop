using CoffeeDesktop.Models;
using CoffeeDesktop.Services;
using System.IO;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using Forms = System.Windows.Forms;
using DrawPoint = System.Drawing.Point;
using Point = System.Windows.Point;
using MouseEventArgs = System.Windows.Input.MouseEventArgs;

namespace CoffeeDesktop;

public partial class MainWindow : Window
{
    private const int FrameWidth = 192, FrameHeight = 208, Columns = 8;
    private readonly SettingsStore _store = new();
    private readonly AppSettings _settings;
    private readonly PetInteractionController _interaction = new();
    private readonly IActionExecutor _actions = new ProcessActionExecutor();
    private readonly DispatcherTimer _animation = new() { Interval = TimeSpan.FromMilliseconds(170) };
    private readonly DispatcherTimer _fullscreenPoll = new() { Interval = TimeSpan.FromMilliseconds(750) };
    private readonly DispatcherTimer _saveDelay = new() { Interval = TimeSpan.FromMilliseconds(500) };
    private readonly DispatcherTimer _shutdownPoll = new() { Interval = TimeSpan.FromMilliseconds(250) };
    private readonly Forms.NotifyIcon _tray = new();
    private readonly BitmapSource _sheet;
    private int _frame, _row = 0, _lookDirection, _idleTicks, _lookRemainingTicks;
    private bool _manualHidden, _fullscreenHidden, _closing;
    private Point _pointerDown;
    private bool _oneShotAction;
    private static readonly int[] FrameCounts = [6, 8, 8, 4, 5, 8, 6, 6, 6, 8, 8];
    private static readonly (string Label, int Row)[] PetActions =
    [
        ("待机", 0), ("向右跑", 1), ("向左跑", 2), ("打招呼", 3), ("跳跃", 4),
        ("垂耳", 5), ("举爪", 6), ("转头", 7), ("理爪", 8)
    ];

    public MainWindow()
    {
        InitializeComponent();
#if DEBUG
        ShowInTaskbar = true;
#endif
        _settings = _store.Load();
        var assetPath = Path.Combine(AppContext.BaseDirectory, "assets", "spritesheet.png");
        _sheet = BitmapFrame.Create(new Uri(assetPath), BitmapCreateOptions.PreservePixelFormat, BitmapCacheOption.OnLoad);
        _sheet.Freeze();
        Width = FrameWidth * Math.Clamp(_settings.PetScale, .5, 2.5);
        Height = FrameHeight * Math.Clamp(_settings.PetScale, .5, 2.5);
        Left = _settings.Left; Top = _settings.Top;
        SourceInitialized += (_, _) =>
        {
            SetPhysicalPosition((int)_settings.Left, (int)_settings.Top);
            Dispatcher.BeginInvoke(ClampToVisibleScreen, DispatcherPriority.Loaded);
        };
        _animation.Tick += (_, _) => AdvanceAnimation();
        _fullscreenPoll.Tick += (_, _) => CheckFullscreen();
        _saveDelay.Tick += (_, _) => { _saveDelay.Stop(); SaveLocation(); };
        _shutdownPoll.Tick += (_, _) => { if (App.ShutdownSignal?.WaitOne(0) == true) ShutdownPet(); };
        _interaction.Clicked += OnPetClicked;
        _interaction.ContextRequested += ShowPetMenu;
        _interaction.DragMoved += SetPhysicalPosition;
        SetupTray();
        SetFrame(0, 0);
        _animation.Start(); _fullscreenPoll.Start(); _shutdownPoll.Start();
        LocationChanged += (_, _) => { if (IsLoaded) { _saveDelay.Stop(); _saveDelay.Start(); } };
        SizeChanged += (_, _) => { if (IsLoaded) { _saveDelay.Stop(); _saveDelay.Start(); } };
        SystemEvents_DisplaySettingsChanged();
    }

    private void SetupTray()
    {
        var iconPath = Path.Combine(AppContext.BaseDirectory, "coffee.ico");
        _tray.Icon = File.Exists(iconPath) ? new Icon(iconPath) : SystemIcons.Application;
        _tray.Text = "咖啡";
        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("设置…", null, (_, _) => OpenSettings());
        menu.Items.Add("隐藏", null, (_, _) => HideManually());
        menu.Items.Add("显示", null, (_, _) => ShowManually());
        menu.Items.Add(new Forms.ToolStripSeparator());
        menu.Items.Add("退出", null, (_, _) => ShutdownPet());
        _tray.ContextMenuStrip = menu;
        _tray.DoubleClick += (_, _) => ShowManually();
        _tray.Visible = true;
    }

    private void Pet_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        _pointerDown = e.GetPosition(this);
        _interaction.PointerDown(this, PetSurface, _pointerDown);
        DebugTrace($"down p={_pointerDown} tracking={_interaction.IsTracking}");
        e.Handled = true;
    }

    private void Pet_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        _interaction.PointerUp(e.GetPosition(this));
        DebugTrace($"up dragged={_interaction.IsTracking}");
        e.Handled = true;
    }

    private void Pet_MouseRightButtonUp(object sender, MouseButtonEventArgs e)
    {
        _interaction.RequestContext(); e.Handled = true;
    }

    private void Pet_MouseMove(object sender, MouseEventArgs e)
    {
        var point = e.GetPosition(this);
        var moved = e.LeftButton == MouseButtonState.Pressed && _interaction.PointerMove(point);
        DebugTrace($"move button={e.LeftButton} p={point} tracking={_interaction.IsTracking} moved={moved}");
        if (moved)
        {
            e.Handled = true;
        }
    }

    private void ShowPetMenu()
    {
        var menu = new System.Windows.Controls.ContextMenu();
        var actionMenu = new System.Windows.Controls.MenuItem { Header = "动作" };
        foreach (var action in PetActions)
        {
            var item = new System.Windows.Controls.MenuItem { Header = action.Label };
            item.Click += (_, _) => PlayAction(action.Row);
            actionMenu.Items.Add(item);
        }
        menu.Items.Add(actionMenu);
        AddItem(menu, "设置…", OpenSettings);
        AddItem(menu, _manualHidden ? "显示" : "隐藏", () => { if (_manualHidden) ShowManually(); else HideManually(); });
        AddItem(menu, "退出", ShutdownPet);
        menu.PlacementTarget = PetImage;
        menu.IsOpen = true;
    }

    private static void AddItem(System.Windows.Controls.ContextMenu menu, string title, Action action)
    {
        var item = new System.Windows.Controls.MenuItem { Header = title };
        item.Click += (_, _) => action(); menu.Items.Add(item);
    }

    private void OnPetClicked()
    {
        if (_settings.LaunchOnClick) _actions.Execute(_settings);
        else PlayAction(3, 1);
    }

    private void PlayAction(int row, int firstFrame = 0)
    {
        _row = row; _frame = firstFrame; _oneShotAction = row != 0;
        if (row == 0) _oneShotAction = false;
        SetFrame(_row, _frame);
    }

    private void AdvanceAnimation()
    {
        if (_oneShotAction)
        {
            if (++_frame >= FrameCounts[_row]) { _oneShotAction = false; _row = 0; _frame = 0; }
            SetFrame(_row, _frame); return;
        }
        if (_row >= 9)
        {
            if (--_lookRemainingTicks <= 0) { _row = 0; _frame = 0; SetFrame(_row, _frame); }
            return;
        }
        if (_row == 0)
        {
            _frame = (_frame + 1) % 6;
            if (++_idleTicks >= 45) { _idleTicks = 0; _lookDirection = (_lookDirection + 1) % 16; _row = _lookDirection < 8 ? 9 : 10; _frame = _lookDirection % 8; _lookRemainingTicks = 8; }
        }
        else { _row = 0; _frame = 0; }
        SetFrame(_row, _frame);
    }

    private void SetFrame(int row, int col)
    {
        var crop = new CroppedBitmap(_sheet, new Int32Rect(col * FrameWidth, row * FrameHeight, FrameWidth, FrameHeight));
        crop.Freeze(); PetImage.Source = crop;
    }

    private void OpenSettings()
    {
        var window = new SettingsWindow(_settings, _store, App.StartupRegistration, new ActionBindingService(_store)) { Owner = this };
        window.ShowDialog();
        Width = FrameWidth * Math.Clamp(_settings.PetScale, .5, 2.5);
        Height = FrameHeight * Math.Clamp(_settings.PetScale, .5, 2.5);
        _fullscreenPoll.Interval = TimeSpan.FromMilliseconds(750);
        ClampToVisibleScreen();
    }

    private void HideManually()
    {
        _manualHidden = true; _fullscreenHidden = false; Hide();
    }

    private void ShowManually()
    {
        _manualHidden = false; _fullscreenHidden = false; ClampToVisibleScreen(); Show(); Activate();
    }

    private void CheckFullscreen()
    {
        if (_manualHidden || _closing) return;
        if (!_settings.HideWhenFullscreen)
        {
            if (_fullscreenHidden) { _fullscreenHidden = false; ClampToVisibleScreen(); Show(); }
            return;
        }
        var fullscreen = FullscreenMonitor.IsForegroundFullscreen(this);
        if (fullscreen && IsVisible) { _fullscreenHidden = true; Hide(); }
        else if (!fullscreen && _fullscreenHidden) { _fullscreenHidden = false; ClampToVisibleScreen(); Show(); }
    }

    private void ClampToVisibleScreen()
    {
        try
        {
            var origin = PointToScreen(new Point(0, 0));
            var far = PointToScreen(new Point(Width, Height));
            var widthPixels = Math.Max(1, (int)(far.X - origin.X));
            var heightPixels = Math.Max(1, (int)(far.Y - origin.Y));
            var screen = Forms.Screen.FromPoint(new DrawPoint((int)(origin.X + widthPixels / 2), (int)(origin.Y + heightPixels / 2)));
            var area = screen.WorkingArea;
            SetPhysicalPosition(Math.Clamp((int)origin.X, area.Left, Math.Max(area.Left, area.Right - widthPixels)),
                Math.Clamp((int)origin.Y, area.Top, Math.Max(area.Top, area.Bottom - heightPixels)));
        }
        catch { Left = Math.Max(0, Left); Top = Math.Max(0, Top); }
    }

    private void SetPhysicalPosition(int x, int y)
    {
        var hwnd = new System.Windows.Interop.WindowInteropHelper(this).Handle;
        if (hwnd != IntPtr.Zero) SetWindowPos(hwnd, IntPtr.Zero, x, y, 0, 0, 0x0001 | 0x0004 | 0x0010);
    }

    private void SaveLocation()
    {
        if (_closing || !IsLoaded) return;
        var screenPoint = PointToScreen(new Point(0, 0));
        _settings.Left = screenPoint.X; _settings.Top = screenPoint.Y; _store.Save(_settings);
    }

    private void SystemEvents_DisplaySettingsChanged()
    {
        Microsoft.Win32.SystemEvents.DisplaySettingsChanged += (_, _) => Dispatcher.BeginInvoke(ClampToVisibleScreen);
    }

    private void ShutdownPet()
    {
        _closing = true; _animation.Stop(); _fullscreenPoll.Stop(); _saveDelay.Stop(); _shutdownPoll.Stop(); _tray.Visible = false; _tray.Dispose();
        var screenPoint = PointToScreen(new Point(0, 0));
        _settings.Left = screenPoint.X; _settings.Top = screenPoint.Y; _store.Save(_settings); Close();
    }

    [System.Runtime.InteropServices.DllImport("user32.dll", SetLastError = true)]
    private static extern bool SetWindowPos(IntPtr hwnd, IntPtr insertAfter, int x, int y, int cx, int cy, uint flags);

    private static void DebugTrace(string line)
    {
#if DEBUG
        try { File.AppendAllText(Path.Combine(AppContext.BaseDirectory, "qa-input.log"), $"{DateTime.Now:HH:mm:ss.fff} {line}{Environment.NewLine}"); } catch { }
#endif
    }

    protected override void OnClosing(System.ComponentModel.CancelEventArgs e)
    {
        if (!_closing) { e.Cancel = true; HideManually(); return; }
        _tray.Dispose(); base.OnClosing(e);
    }
}
