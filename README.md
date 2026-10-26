# 咖啡桌宠

一只可独立运行的 Windows 桌面猫咪。

**下载：**[最新版 Windows x64 安装包](https://github.com/RegulusWong03/coffee-desktop/releases/latest/download/CoffeeDesktop-Setup-win-x64.exe) · [全部版本](https://github.com/RegulusWong03/coffee-desktop/releases) · [校验文件](https://github.com/RegulusWong03/coffee-desktop/releases/latest/download/CoffeeDesktop-Setup-win-x64.exe.sha256)

## 使用

咖啡以透明桌面窗口显示，可从托盘或右键菜单设置、隐藏和退出；全屏时默认自动隐藏。默认不启动外部程序，可在设置中选择 exe 并开启单击启动。登录自启默认关闭。安装包自带运行环境，无需预装 .NET。

## Android（自用离线）

Android 版供本机离线使用，需求基准见[实施计划](docs/android-implementation-plan.md)。P0/P1/P2/P3 已全部通过目标平板验收，覆盖八动作、多实例、resize/删除、旋转与进程恢复、Code 2→Code 3 同签升级、真实离线、播放中锁屏、人工 PIN 解锁及真实重启。[P1/P2 结果](docs/android-p1-p2-results.md) · [P3 验收与交付状态](docs/android-p3-results.md) · [Release 指南](docs/android-release-guide.md)。

正式 APK `outputs/android-release/coffee-1.0.0.apk` 已完成验收并安装在目标平板；它是本地构建产物，未加入 Git。Code 3 / `1.0.0`，SHA-256 为 `fa659105ab0e706d927014e8b770b83ccb1af24299c3611852c5080ee8f19523`。目标平板保留一个默认小号 1×1 widget；升级和重启后设置仍为随机动作、170 ms、85%、动画开启、点击播放。设备内 base.apk 与交付 APK 摘要一致。

安装后打开“咖啡桌宠”设置，可选择随机/固定动作、速度、大小和点击行为；预览只在设置页播放。HyperOS 的系统添加路径为桌面空白处长按 → “小部件” → “安卓小部件” → 列表末尾“咖啡桌宠” → 选择小号或大号。设置、冷启动和播放已在断网下通过；20 秒短样本显示空闲无应用后台工作，USB 充电和 launcher 背景噪声使功耗数据仅作 CPU/后台相对观察，不承诺电池 mAh。

## 开发与发布

Windows x64 上运行 `./build.ps1 -BuildInstaller` 构建安装包。应用版本以 `src/Coffee.Desktop/Coffee.Desktop.csproj` 中的 `Version` 为准；提交版本变更后，推送对应的 `vMAJOR.MINOR.PATCH` 标签即可由 GitHub Actions 构建并发布安装包与 SHA-256 校验文件。

## 验证范围

已完成本地隔离检查；安装与卸载、真实拖动、混合 DPI 多屏和登录自启尚未在目标设备验证。全屏隐藏、窗口前台激活等行为也可能受 Windows 或具体应用影响。
