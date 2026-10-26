# 咖啡 Android P1/P2 阶段结果

> 证据说明：截图、UI dump、日志和 APK 均为本地验收材料，保留在本机且未推送；公开仓库只提交源码、资源与结果文档。

## 验收摘要

- **P1 正式设置核心通过。** 原生中文设置页、全局默认与实例选择、固定/随机动作、速度、显示大小、点击行为、动画覆写/跟随、预览、恢复默认及本地持久化已实现。当前验证覆盖实例保存后重开读取、直接从桌面进入对应实例设置，以及预览结束回待机且不改变桌面实例。
- **P2 多实例核心行为已实现并完成定向验证。** 两个桌面实例各自读取设置并独立播放；实例动画覆写与全局动画恢复路径已在真机验证。正常连点期间忙碌事务被立即拒绝，另一实例仍能同时开始播放。旧 P0 偏好注入样本中紧邻 force-stop 的两次点击不计作忙碌验收；稳定状态下的验收记录见下方。
- **尚待下一单元验收：** widget resize、删除后的偏好/状态清理，以及完整 provider/job 生命周期矩阵。此文档不将这些项目记为已通过。

## 构建产物

- Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`
- 大小：13,701,801 bytes
- SHA-256：`C86A97F23F9341AF2FA42EDE2AF396A86CBCF79EE0D192D2970C7187599F3982`
- 构建验证：`testDebugUnitTest lintDebug assembleDebug` 成功；14 项单元测试通过（coordinator 9、run registry 2、设置规则 3），lint 无错误。随后仅有设置页提示状态的小幅 UI 修正，并再次成功 assembleDebug。

## 真机证据

证据目录：`android-p1-p2-evidence/`（本地留存，未推送）

- 设置布局与安全区：`settings-landscape-final.png`、`p1-settings-safearea.png`
- 实例配置保存与重开：`instance-171-saved.png`、`instance-171-persisted.xml`
- 桌面点击进入目标实例设置：`widget-opens-instance-settings.png`、`widget-opens-instance-settings.xml`
- 单次预览完成后回待机：`preview-ended.png`、`preview-ended.xml`
- 双实例独立播放与忙碌点击即时拒绝：`final-multinstance-busy.txt`、`final-home-idle.png`
- 实例动画关闭、跟随全局及恢复后播放：`animation-off.png`、`animation-off-click.txt`、`global-animation-off-clicks.txt`、`global-animation-restored.txt`
- Provider 稳定启动记录：`provider-stable-start.txt`

设备为 Redmi KPad（Android 16 / HyperOS 3.0.310.0）。本阶段没有验证上述待接续的 resize、删除和完整生命周期场景，也没有进行 P3 release 签名或发布构建。
