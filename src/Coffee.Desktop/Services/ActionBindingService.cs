using System.IO;
using CoffeeDesktop.Models;

namespace CoffeeDesktop.Services;

public sealed class ActionBindingService(SettingsStore store)
{
    public void SaveBinding(AppSettings settings, string executable, bool enabled)
    {
        settings.BoundExecutable = Path.GetFullPath(executable);
        settings.LaunchOnClick = enabled;
        store.Save(settings);
    }
}
