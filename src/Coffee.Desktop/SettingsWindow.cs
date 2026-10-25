using CoffeeDesktop.Models;
using CoffeeDesktop.Services;
using Microsoft.Win32;
using System.Windows;
using System.Windows.Controls;
using System.IO;
using TextBox = System.Windows.Controls.TextBox;
using CheckBox = System.Windows.Controls.CheckBox;
using Button = System.Windows.Controls.Button;
using Orientation = System.Windows.Controls.Orientation;
using OpenFileDialog = Microsoft.Win32.OpenFileDialog;
using HorizontalAlignment = System.Windows.HorizontalAlignment;

namespace CoffeeDesktop;

public sealed class SettingsWindow : Window
{
    private readonly AppSettings _settings;
    private readonly SettingsStore _store;
    private readonly StartupRegistration _startup;
    private readonly ActionBindingService _binding;
    private readonly bool _initialAutoStart;
    private readonly TextBox _path = new() { IsReadOnly = true, MinWidth = 300 };
    private readonly CheckBox _launchOnClick = new() { Content = "单击咖啡时启动此程序", Margin = new Thickness(0, 5, 0, 0) };
    private readonly CheckBox _autoStart = new() { Content = "登录 Windows 后自动启动咖啡" };
    private readonly CheckBox _hideFullscreen = new() { Content = "其他程序全屏时自动隐藏咖啡" };
    private readonly Slider _scale = new() { Minimum = .65, Maximum = 1.75, TickFrequency = .05, IsSnapToTickEnabled = true, Width = 230 };
    private readonly TextBlock _scaleText = new() { VerticalAlignment = VerticalAlignment.Center };

    public SettingsWindow(AppSettings settings, SettingsStore store, StartupRegistration startup, ActionBindingService binding)
    {
        _settings = settings; _store = store; _startup = startup; _binding = binding;
        _initialAutoStart = settings.AutoStart;
        Title = "咖啡设置"; Width = 440; Height = 390; ResizeMode = ResizeMode.NoResize;
        WindowStartupLocation = WindowStartupLocation.CenterOwner; ShowInTaskbar = false;
        _path.Text = settings.BoundExecutable ?? "尚未选择程序";
        _launchOnClick.IsChecked = settings.LaunchOnClick;
        _autoStart.IsChecked = settings.AutoStart;
        _hideFullscreen.IsChecked = settings.HideWhenFullscreen;
        _scale.Value = Math.Clamp(settings.PetScale, .65, 1.75);
        _scale.ValueChanged += (_, _) => _scaleText.Text = $"{_scale.Value:P0}";
        _scaleText.Text = $"{_scale.Value:P0}";

        var root = new StackPanel { Margin = new Thickness(24) };
        root.Children.Add(new TextBlock { Text = "咖啡", FontSize = 22, FontWeight = FontWeights.SemiBold });
        root.Children.Add(new TextBlock { Text = "互动", FontSize = 15, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 16, 0, 4) });
        root.Children.Add(new TextBlock { Text = "选择一个程序，单击猫咪时会先尝试切换到它已打开的窗口。", TextWrapping = TextWrapping.Wrap });
        var picker = new Button { Content = "选择程序…", HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 8, 0, 3), Padding = new Thickness(10, 4, 10, 4) };
        picker.Click += (_, _) => PickProgram(); root.Children.Add(picker);
        root.Children.Add(_path); root.Children.Add(_launchOnClick);
        root.Children.Add(new TextBlock { Text = "外观与启动", FontSize = 15, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 15, 0, 4) });
        var scaleLine = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
        scaleLine.Children.Add(new TextBlock { Text = "猫咪大小", Width = 78, VerticalAlignment = VerticalAlignment.Center });
        scaleLine.Children.Add(_scale); scaleLine.Children.Add(_scaleText); root.Children.Add(scaleLine);
        root.Children.Add(_autoStart); root.Children.Add(_hideFullscreen);
        root.Children.Add(new TextBlock { Text = "右键猫咪或系统托盘图标可打开菜单、隐藏、显示或退出。", TextWrapping = TextWrapping.Wrap, Foreground = System.Windows.Media.Brushes.DimGray, Margin = new Thickness(0, 10, 0, 0) });
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 17, 0, 0) };
        var save = new Button { Content = "保存", IsDefault = true, MinWidth = 88, Padding = new Thickness(10, 5, 10, 5) };
        save.Click += (_, _) => SaveAndClose();
        var cancel = new Button { Content = "取消", IsCancel = true, MinWidth = 88, Margin = new Thickness(8, 0, 0, 0), Padding = new Thickness(10, 5, 10, 5) };
        buttons.Children.Add(save); buttons.Children.Add(cancel); root.Children.Add(buttons);
        Content = root;
    }

    private void PickProgram()
    {
        var picker = new OpenFileDialog { Title = "选择要绑定的 Windows 程序", Filter = "程序 (*.exe)|*.exe|所有文件 (*.*)|*.*", CheckFileExists = true };
        if (picker.ShowDialog(this) == true) { _path.Text = picker.FileName; _launchOnClick.IsChecked = true; }
    }

    private void SaveAndClose()
    {
        _settings.PetScale = _scale.Value;
        _settings.AutoStart = _autoStart.IsChecked == true;
        _settings.HideWhenFullscreen = _hideFullscreen.IsChecked == true;
        if (File.Exists(_path.Text)) _binding.SaveBinding(_settings, _path.Text, _launchOnClick.IsChecked == true);
        else if (_launchOnClick.IsChecked != true) _settings.LaunchOnClick = false;
        if (_settings.AutoStart != _initialAutoStart) _startup.SetEnabled(_settings.AutoStart);
        _store.Save(_settings);
        DialogResult = true;
    }
}
