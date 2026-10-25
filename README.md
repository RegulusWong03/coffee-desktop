# 咖啡桌宠

独立运行的 Windows x64 桌面猫咪，使用原生 WPF 透明窗口和系统托盘。安装和运行均不依赖 Codex、账号或网络；安装包自包含 .NET，朋友无需预装运行时。

## 使用

先按下方说明运行 `.\build.ps1 -BuildInstaller` 生成安装器，再运行 `release/CoffeeDesktop-Setup-win-x64.exe`。安装到当前用户的 `%LOCALAPPDATA%\Programs\CoffeeDesktop`，不要求管理员权限，创建开始菜单启动与卸载快捷方式，并在安装后启动咖啡。尚未实际安装验证。

右键猫咪或托盘图标可打开设置、隐藏、显示或退出。猫咪右键菜单的“动作”子菜单可播放九种原图标准状态，播放一次后回到待机；其中“垂耳、举爪、理爪”等是猫咪动作，不对应 Codex 任务状态。普通单击默认招手；拖动只移动猫咪。

设置中可选择一个 exe，并明确勾选“单击咖啡时启动此程序”。咖啡按完整程序路径查找已经打开且有可见窗口的同一程序并尝试切换前台；Windows 可能拒绝前台激活，此时会启动该程序。未绑定或未启用时，不会启动外部程序。

开机登录启动默认为关闭；启用后仅为当前用户写入 HKCU\Software\Microsoft\Windows\CurrentVersion\Run。程序不会每次启动时重写该项，以尊重 Windows 对启动项的禁用。全屏自动隐藏默认为开启。手动隐藏需从托盘恢复；全屏结束后仅恢复此前可见的猫咪。位置使用物理屏幕像素记录在 %APPDATA%\CoffeeDesktop\settings.json，切换或断开显示器时会移回可见区域。

## 构建

需要 Windows x64、PowerShell、网络连接（只用于首次下载 SDK、运行时包或安装器编译器）。.NET 10 是 LTS，官方支持到 2028 年 11 月；本项目固定 SDK 10.0.401，并使用微软官方 SHA512 校验。运行时包从本机缓存还原；缓存不足时，构建脚本使用微软 dotnet-public feed。构建脚本优先使用工作区 .tools 内的工具，再使用 PATH 或系统 NSIS；缺少 NSIS 时会下载并校验官方 3.13 便携版，不做全局安装。

官方来源： [.NET 10 下载页](https://dotnet.microsoft.com/en-us/download/dotnet/10.0)、[微软 .NET 支持政策](https://learn.microsoft.com/en-us/dotnet/core/releases-and-support)、[NSIS 下载页](https://nsis.sourceforge.io/Download)。


仅构建应用可运行 `.\build.ps1`；要生成安装器则运行 `.\build.ps1 -BuildInstaller`。源程序输出在 `release/CoffeeDesktop-win-x64`，安装包输出在 `release/CoffeeDesktop-Setup-win-x64.exe`。SDK zip 与 NSIS zip 不进入安装包。

assets/spritesheet.png 是从本机咖啡宠物目录只读复制的 v2 资产：1536×2288 透明 PNG，8列×11行，每格192×208。本程序使用 row0 待机6帧、row1/2行走8帧、row3招手4帧、row4跳跃5帧、row5垂耳8帧、row6举爪6帧、row7转头6帧、row8理爪6帧，以及row9/10的16个静态看向方向帧。动画时序使用固定帧间隔；原图不修改、不重画。

## 发布

GitHub Actions 在推送 `vMAJOR.MINOR.PATCH` 语义版本标签时，使用 Windows runner 构建自包含 x64 安装包、生成 SHA-256 文件并发布同名 GitHub Release。应用版本只在 `src/Coffee.Desktop/Coffee.Desktop.csproj` 的 `Version` 属性维护；发布标签去掉 `v` 后必须与该版本完全一致，否则构建会失败。新版本先修改该属性并提交，再创建并推送匹配标签，例如：

~~~powershell
git add src/Coffee.Desktop/Coffee.Desktop.csproj
git commit -m "Release Coffee Desktop 1.0.1"
git push origin main
git tag v1.0.1
git push origin v1.0.1
~~~

Release 附件为 `CoffeeDesktop-Setup-win-x64.exe` 和 `CoffeeDesktop-Setup-win-x64.exe.sha256`。Actions run 可在仓库的 Actions 页面查看。工作流只处理 tag 发布，不包含客户端自动更新。

## 需求与实现

- 透明无边框桌宠、右键菜单与托盘；记忆位置和尺寸、DPI/屏幕坐标处理、显示器拓扑变化后的可见区域校正、单实例。
- 交互输入、动作绑定与动作执行分层。动作菜单保留全部九行标准状态；默认没有程序绑定，单击不会启动外部程序。
- 可选启动程序，按完整 exe 路径谨慎复用已打开窗口；无法聚焦时按用户启用的绑定启动。
- 自启动只由设置开关显式写入当前用户 Run 项；卸载只逐项删除本安装版本文件、自己的 Run 项、快捷方式和卸载注册项。卸载脚本不会递归清空安装目录中的额外文件。
- 不包含 AI、云服务、遥测、账号、养成玩法或插件平台；不模拟 Codex 任务状态，也不修改 Codex 宠物配置或云端状态。

## 验证记录

- 已在 Windows 使用 .NET 10 SDK 编译自包含 Release 和安装器；尚未实际安装或卸载。
- 本地隔离检查覆盖离屏坐标校正、全屏隐藏与恢复、程序绑定、自启动设置写入与清理，以及透明窗口、右键菜单和动作菜单。
- 拖动动作尚未完成实际验证；混合 DPI、多显示器硬件及真实登录启动也未验证。

前台聚焦限制意味着尝试激活已打开的目标窗口不保证成功。全屏判断依据 Windows 前台窗口与所在显示器边界；特殊无边框应用、独占全屏以及混合DPI多显示器行为仍需在目标设备复核。
