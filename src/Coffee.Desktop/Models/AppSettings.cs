namespace CoffeeDesktop.Models;

public sealed class AppSettings
{
    // Physical virtual-screen pixels so saved placement is independent of monitor DPI.
    public double Left { get; set; } = 40;
    public double Top { get; set; } = 80;
    public double PetScale { get; set; } = 1.0;
    public bool AutoStart { get; set; }
    public bool HideWhenFullscreen { get; set; } = true;
    public bool LaunchOnClick { get; set; }
    public string? BoundExecutable { get; set; }
    public string? BoundArguments { get; set; }
    public string? BoundWorkingDirectory { get; set; }
}
