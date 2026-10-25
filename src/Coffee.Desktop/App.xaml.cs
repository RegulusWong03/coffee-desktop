using CoffeeDesktop.Services;
using System.Windows;

namespace CoffeeDesktop;

public partial class App : System.Windows.Application
{
    public static StartupRegistration StartupRegistration { get; } = new();
    public static EventWaitHandle? ShutdownSignal { get; private set; }
    private static EventWaitHandle? _shutdownAck;
    private Mutex? _instanceMutex;
    private bool _ownsMutex;

    protected override void OnStartup(StartupEventArgs e)
    {
        if (Environment.GetCommandLineArgs().Any(a => a.Equals("--uninstall-cleanup", StringComparison.OrdinalIgnoreCase)))
        {
            StartupRegistration.Remove();
            try
            {
                using var signal = EventWaitHandle.OpenExisting(GetSignalName());
                signal.Set();
                try { using var ack = EventWaitHandle.OpenExisting(GetAckName()); ack.WaitOne(TimeSpan.FromSeconds(8)); }
                catch (WaitHandleCannotBeOpenedException) { }
            }
            catch (WaitHandleCannotBeOpenedException) { }
            base.OnStartup(e);
            Shutdown();
            return;
        }
        var userId = System.Security.Principal.WindowsIdentity.GetCurrent().User?.Value ?? Environment.UserName;
        _instanceMutex = new Mutex(initiallyOwned: true, $"Local\\CoffeeDesktop-{userId}", out var createdNew);
        if (!createdNew) { base.OnStartup(e); Shutdown(); return; }
        _ownsMutex = true;
        ShutdownSignal = new EventWaitHandle(false, EventResetMode.AutoReset, GetSignalName());
        _shutdownAck = new EventWaitHandle(false, EventResetMode.ManualReset, GetAckName());
        base.OnStartup(e);
        MainWindow = new MainWindow();
        MainWindow.Show();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        if (_ownsMutex) _instanceMutex?.ReleaseMutex();
        _instanceMutex?.Dispose();
        _shutdownAck?.Set(); _shutdownAck?.Dispose(); ShutdownSignal?.Dispose();
        base.OnExit(e);
    }

    private static string GetSignalName()
    {
        var userId = System.Security.Principal.WindowsIdentity.GetCurrent().User?.Value ?? Environment.UserName;
        return $"Local\\CoffeeDesktop-Exit-{userId}";
    }
    private static string GetAckName()
    {
        var userId = System.Security.Principal.WindowsIdentity.GetCurrent().User?.Value ?? Environment.UserName;
        return $"Local\\CoffeeDesktop-ExitAck-{userId}";
    }
}
