using Microsoft.Win32;

namespace CoffeeDesktop.Services;

public sealed class StartupRegistration
{
    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string ValueName = "CoffeeDesktop";
    private readonly string _runKey;
    private readonly string _valueName;

    public StartupRegistration() : this(RunKey, ValueName) { }
    public StartupRegistration(string runKey, string valueName)
    {
        _runKey = runKey;
        _valueName = valueName;
    }

    public static string FormatCommand(string executablePath) => $"\"{executablePath}\"";

    public void SetEnabled(bool enabled)
    {
        using var key = Registry.CurrentUser.OpenSubKey(_runKey, writable: true) ?? Registry.CurrentUser.CreateSubKey(_runKey, writable: true);
        if (enabled)
        {
            var path = Environment.ProcessPath ?? System.Reflection.Assembly.GetExecutingAssembly().Location;
            key.SetValue(_valueName, FormatCommand(path));
        }
        else key.DeleteValue(_valueName, throwOnMissingValue: false);
    }

    public void Remove() => SetEnabled(false);
}
