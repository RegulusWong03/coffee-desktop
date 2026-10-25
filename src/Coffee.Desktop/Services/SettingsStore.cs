using System.IO;
using System.Text.Json;
using CoffeeDesktop.Models;

namespace CoffeeDesktop.Services;

public sealed class SettingsStore
{
    private readonly string _path = Path.Combine(
        Environment.GetEnvironmentVariable("COFFEE_PROFILE_DIR") ?? Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "CoffeeDesktop", "settings.json");
    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public AppSettings Load()
    {
        try { return File.Exists(_path) ? JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(_path)) ?? new() : new(); }
        catch { return new(); }
    }

    public void Save(AppSettings settings)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_path)!);
        File.WriteAllText(_path, JsonSerializer.Serialize(settings, JsonOptions));
    }
}
