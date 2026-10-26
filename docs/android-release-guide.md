# Android 自用离线 Release 交付指南

## 构建门与版本

只有 P1、P2 真机验收通过后才运行 release 脚本。脚本要求显式传入 `-P2Accepted`、`-VersionCode` 和 `-VersionName`，并再次检查 APK 包名、版本、签名证书和复制前后的 SHA-256。建议先用 `2 / 1.0.0-rc.1` 做签名安装验收，再用 `3 / 1.0.0` 同签覆盖升级。

应用 ID 固定为 `com.coffee.pet.android`。当前签名材料在仓库外本机目录：

- Keystore：仓库外签名目录中的 `coffee-release.jks`
- Gradle 属性文件：同一仓库外目录中的 `signing.properties`
- Alias：`coffee-release`

不要复制 keystore 或属性文件到仓库、APK、日志、命令行或版本控制。安全保留备份；丢失私钥会阻止后续同签名就地升级。

## 签名配置

在 PowerShell 当前进程设置外部属性文件路径（环境变量仅存路径，属性文件包含的签名口令不进入进程参数）：

```powershell
$env:COFFEE_SIGNING_PROPERTIES = '<仓库外签名目录>\signing.properties'
```

`android/app/build.gradle.kts` 只在该环境变量存在时读取文件；debug 构建不需要签名环境。release 任务缺路径、字段、keystore 或版本参数时会明确失败，不会生成未签名 release APK。外部属性文件字段为 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`。

## P2 通过后的可复现命令

从仓库根目录在 PowerShell 执行；执行前确认 `R:` 未被占用。脚本临时把中文路径映射为 `R:`，将 `JAVA_HOME`、`ANDROID_HOME` 和 `GRADLE_USER_HOME` 指向仓库内已准备的 JDK 17、SDK 36 和 Gradle 缓存目录，退出时移除自己创建的映射。若另一构建代理正在用 `R:`，脚本停止并提示稍后重试。

```powershell
./android/build-release.ps1 -P2Accepted -VersionCode 2 -VersionName '1.0.0-rc.1'
./android/build-release.ps1 -P2Accepted -VersionCode 3 -VersionName '1.0.0'
```

脚本调用 Gradle 8.13 `:app:assembleRelease --no-daemon`，将交付副本写到 `E:\Shiori\ChatGPT\桌宠\outputs\android-release\coffee-<versionName>.apk`，并在该副本和 Gradle 原始 APK 旁分别写 `.sha256`。脚本检查副本与 Gradle 输出摘要一致。Release manifest 不包含 P0 `PrototypeActivity` 或淘汰路线 `HostFrameService`；Debug manifest 保留它们供诊断构建使用。

本机 signing properties 按非转义字面值读取键值，支持 Windows 绝对路径；口令值不会写入 Gradle 日志、交付 APK 或文档。

## 安装与升级

首次从 debug 原型切换到 release 证书时，Android 不允许异签原地覆盖。若测试设备装有原型，只卸载 `com.coffee.pet.android` 后安装 release APK；这只替换本应用测试版本，不动其它应用。后续 release 使用相同 applicationId、相同 keystore 并递增 `versionCode`，可覆盖升级。

安装验收后在本应用设置中恢复随机动作、170ms、85%、动画开启、点击播放。至少保留一个本应用 widget。验收/交付时记录包名、版本、SDK/构建工具、证书 SHA-256、APK 路径、APK SHA-256 和设备安装状态。

## 本机证书记录

- Application ID：`com.coffee.pet.android`
- Alias：`coffee-release`
- Keystore：JKS，RSA 3072 位，SHA256withRSA
- 有效期：2026-10-26 21:51:51 HKT 至 2054-03-13 21:51:51 HKT
- 证书 SHA-256：`25:63:E3:BC:4A:43:6C:79:A8:39:36:E7:51:1E:90:95:4E:C9:4D:AD:A0:0A:F4:90:C3:F4:81:78:DE:58:1A:49`
- 材料目录：仓库外的本机签名目录
- 工具链：JDK 17、Android SDK/Build Tools 36.0.0、AGP 8.11.1、Kotlin 2.2.21、Gradle 8.13

## 2026-10-26 构建与 2026-10-27 设备验收结果

- Code 2 RC：`outputs/android-release/coffee-1.0.0-rc.1.apk`
  - SHA-256：`727cba45399d9e9f644701aeb0856712f28aff111a336838f3577d7f2573b9e9`
- Code 3 正式包：`outputs/android-release/coffee-1.0.0.apk`
  - SHA-256：`fa659105ab0e706d927014e8b770b83ccb1af24299c3611852c5080ee8f19523`
  - 交付副本与 Gradle `app-release.apk` 跨路径摘要一致。
- 两个包的 package/version、v2 签名、指定证书 SHA-256、release manifest、入口、权限和包内敏感材料检查通过；`testDebugUnitTest` 14/14、`lintDebug`、`assembleDebug` 成功。
- 静态汇总证据见本机 `docs/android-p1-p2-evidence/release-final-static-verification.txt`，该目录本地留存且未推送。
- debug 异签已卸载。早期 HyperOS 安装曾返回 `INSTALL_FAILED_USER_RESTRICTED`；用户手动允许后 Code 2 RC 安装成功。随后在设备添加小号 1×1 widget 176，并把实例点击行为保存为非默认“打开设置”。
- Code 3 已通过 `adb install -r` 同签覆盖：`firstInstallTime` 保持 Code 2 首次安装时间，widget 176 与非默认点击偏好均保留。升级后已将最终偏好恢复为随机动作、170 ms、85%、动画开启、点击播放。
- 真实断网冷启/设置/播放、空闲与播放中锁屏、用户手动 PIN 解锁和真实重启均通过。重启前后 boot_id 变化、uptime 重新计时；设备内 base.apk 与交付 APK 的 SHA-256 完全一致。最终保留 widget 176、Code 3 和用户原网络/旋转设置。
- 20 秒短功耗观察记录在 `outputs/android-release-evidence-1027/equal-window-summary.txt`。该样本 USB 供电且 launcher 含其它动态组件噪声，不作为电池 mAh 结论。

设备侧完整矩阵以 [P3 结果](android-p3-results.md) 为准；当前 P3 已完成，无未决功能项。
