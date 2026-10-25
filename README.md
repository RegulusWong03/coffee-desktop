# 咖啡桌宠

一只可独立运行的 Windows 桌面猫咪。

**下载：**[最新版 Windows x64 安装包](https://github.com/RegulusWong03/coffee-desktop/releases/latest/download/CoffeeDesktop-Setup-win-x64.exe) · [全部版本](https://github.com/RegulusWong03/coffee-desktop/releases) · [校验文件](https://github.com/RegulusWong03/coffee-desktop/releases/latest/download/CoffeeDesktop-Setup-win-x64.exe.sha256)

## 使用

咖啡以透明桌面窗口显示，可从托盘或右键菜单设置、隐藏和退出；全屏时默认自动隐藏。默认不启动外部程序，可在设置中选择 exe 并开启单击启动。登录自启默认关闭。安装包自带运行环境，无需预装 .NET。

## 开发与发布

Windows x64 上运行 `./build.ps1 -BuildInstaller` 构建安装包。应用版本以 `src/Coffee.Desktop/Coffee.Desktop.csproj` 中的 `Version` 为准；提交版本变更后，推送对应的 `vMAJOR.MINOR.PATCH` 标签即可由 GitHub Actions 构建并发布安装包与 SHA-256 校验文件。

## 验证范围

已完成本地隔离检查；安装与卸载、真实拖动、混合 DPI 多屏和登录自启尚未在目标设备验证。全屏隐藏、窗口前台激活等行为也可能受 Windows 或具体应用影响。
