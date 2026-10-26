# 咖啡 Android P0 原型结果

> 更新：2026-10-26。状态：P0 目标机播放选型门通过；唯一选型为按 `appWidgetId` 调度的 expedited `JobService` + 短时逐帧 RemoteViews 资源引用更新。host `AdapterViewFlipper` 路线因 HyperOS 显示“加载窗口小部件时出现问题”失败。P1 正在由主代理派发；P2/P3 生命周期矩阵未完成。

> 证据说明：本文引用的 APK、构建报告、截图、录屏、UI dump 和日志均为本机验收证据，保留在本地且未推送。公开仓库只提交源码、资源与结果文档。

## 结果摘要

- 已建立独立 Kotlin Android 工程；Windows 工程与其素材源文件未改动。
- 桌面端 JVM 单测共 9 项通过，0 失败：7 项直接调用 production `PlaybackCoordinator`；2 项覆盖 production `PlaybackJobRunRegistry` 的同 jobId 替换事务安全。
- Android lint：0 errors、5 warnings。两项 SharedPreferences `commit()` 警告是为了在异步事务开始/模式更改时同步持久化 busy 和配置；target SDK 35 警告待 K Pad 行为验证；两组 `IconDuplicates` 指向源雪碧图中像素相同的格子，按原素材保留。
- registry 并发修正后的内部 debug APK 已安装至红米 K Pad 并完成新版本真机录制；包 `com.coffee.pet.android`，SHA-256 `988AD30CEDCB65B2718AFE0345316BE509661741EAA46F423C37FAC505B754CC`。设备上的两个实例为 widgetId 171/172。

| 产物/证据 | 位置 |
|---|---|
| 内部 debug APK（本机未推送） | `android/app/build/outputs/apk/debug/app-debug.apk` |
| SHA-256 sidecar（本机未推送） | `android/app/build/outputs/apk/debug/app-debug.apk.sha256` |
| JVM 测试报告（本机未推送） | `android/app/build/reports/tests/testDebugUnitTest/index.html` |
| 测试 XML | coordinator 与 `PlaybackJobRunRegistry` 两个 XML 报告位于 `android/app/build/test-results/testDebugUnitTest/` |
| Lint 报告（本机未推送） | `android/app/build/reports/lint-results-debug.html` · `android/app/build/reports/lint-results-debug.txt` |
| 原图裁帧映射与帧 SHA-256 | [`android/frame-map.csv`](../android/frame-map.csv) |
| 完整构建/检查命令日志（本机忽略目录） | `.tools/p0-final-acceptance.log` |

APK 身份：`applicationId=com.coffee.pet.android`、`versionName=0.1-p0`、`minSdk=26`、`targetSdk=35`、`compileSdk=36`。debug variant 使用 Gradle 本机生成的 debug 签名，不包含 release keystore；本轮不创建 release 密钥。

## 本地实现与测试证据

### 小部件与帧资源

- 两个 `AppWidgetProvider`（小号/大号），使用透明 `RemoteViews`；静态待机显示源行 0 第 0 帧。provider 的尺寸变化取消当前事务并提交静帧；删除实例写入轻量 generation tombstone，以防相同 appWidgetId 重用后迟到回调复活旧状态。
- 输入 `assets/spritesheet.png` 实测 1536×2288、RGBA；切为 8×11 个 192×208 网格，alpha 非空 73 格；各行动作帧数 `[6,8,8,4,5,8,6,6,6,8,8]`。裁切不重采样/重绘，输出和源行列、坐标及 SHA-256 在 `android/frame-map.csv`。
- P0 诊断页可选 host 推进与逐帧两条渲染候选，并设置 170/300 ms 和固定动作行。两条路径都由 receiver 同步准入后提交 expedited Job；不在广播生命周期持有整段动画。
- **host 路线实测失败：** `AdapterViewFlipper` 在动作过程中多次显示系统“加载窗口小部件时出现问题”错误占位，即使之后恢复。证据在 `p0-host-once.mp4` 时间序列，事务日志未记录异常；视觉失败足以淘汰该候选。
- **逐帧路线选定：** `PlaybackJobService` 每帧仅通过 `setImageViewResource` 更新预打包 drawable；末帧后提交 idle RemoteViews。未发送 Bitmap 或雪碧图。

  P0 诊断页在应用图标启动后显示模式选择、170 ms 默认速度和 300 ms 慢速边界候选；“应用”将设置用于所有现有原型 widget，并取消活动动作回到静止。此页仅控制原型和辅助观察，动画验收仍须在桌面 widget 上完成。

### 状态、并发和恢复

- `PlaybackCoordinator` 是 production receiver/provider 使用的同一实现；状态按 widgetId 保存 `transactionId`、`renderGeneration`、配置版本、速度与 elapsedRealtime 开始时间。
- receiver 在同步 `onReceive` 中通过 production coordinator 原子取得 busy 并尽快返回；按 widgetId 提交 expedited Job，启动上限 1.5 秒。调度失败或非 expedited/迟到启动会取消回 idle，不退化成普通排队任务。每实例 job 共享渲染锁和取消令牌。
- `PlaybackJobRunRegistry` 以 jobId 持有同一运行记录，并用 expected-record 条件移除；`onStopJob` 还校验 widgetId、transactionId、renderGeneration，旧完成或 stop 不能删除/取消替换事务。Job 停止不重试。
- busy 点击在新 build 真机上 0–1 ms 丢弃；另一实例可同时运行。每次播放成功时在 busy 事务中提交静帧，再释放 busy。五秒事务 deadline 仍为守卫；8 帧名义时长 170 ms 为 1.36 秒、300 ms 为 2.4 秒。

### 单测覆盖（9 项）

`PlaybackCoordinatorTest` 直接调用生产协调器，使用注入的仓库、renderer 和单调时钟：

- 同一 widget busy 点击立即拒绝，不同 widgetId 可并行播放。
- 当前动作使用启动快照，后续动作读取新动作/速度配置。
- host 序列提交接收 speed snapshot，300 ms 设置确实传到 renderer 契约。
- 取消/删除阻止迟到帧与完成回调；死实例不再接受点击。
- 并发取消和旧 frame 不能互相覆盖。
- 静态待机提交尚未完成时的新点击在 200 ms 内返回 `null`；释放 busy 后，只有之后新到达的点击才开始。
- 事务超时与 elapsedRealtime 重置后的进程恢复会先提交 idle，并拒绝旧回调。
- registry 测试覆盖旧 worker 完成和旧 stop 回调遇到相同 jobId 的新 transactionId/renderGeneration 时不得移除新记录。

### 静态检查

- Gradle：`testDebugUnitTest assembleDebug lintDebug` 成功。
- 单测 XML：coordinator `tests=7`、job run registry `tests=2`；合计 `tests=9, failures=0, errors=0, skipped=0`。
- Lint：`0 errors, 5 warnings`（上述两处同步持久化、target SDK 35、原图像素重复两组）。Kotlin 编译还对 API 26–30 的兼容 RemoteViewsService Intent 重载提示已弃用；该重载只作为旧 API fallback，API 31+ 使用集合 RemoteViews。
- AAPT `dump badging` 确认 applicationId、min/target/compile SDK 和 APK 标签。AAPT `dump permissions` 仅显示 package，没有声明任何权限；源码/产物没有 INTERNET、前台服务、悬浮窗、通知或闹钟权限。`updatePeriodMillis=0`。
- AAPT `dump xmltree` 确认 host 布局实际包含 `AdapterViewFlipper`、`autoStart=true`、`loopViews=false` 和默认 `flipInterval=170`；该间隔由 RemoteViews `setInt(..., "setFlipInterval", txn.intervalMs)` 覆盖为 170 或 300 ms。
- 本次设备验收 APK SHA-256：`988AD30CEDCB65B2718AFE0345316BE509661741EAA46F423C37FAC505B754CC`。

## RemoteViews/API 能力核查与候选比较

P0 使用的实际 SDK stub 为 `.tools/android-sdk/platforms/android-36/android.jar`。本机 `javap` 核验到 `RemoteViews.setInt(int,String,int)`、`setRemoteAdapter(int,RemoteCollectionItems)`、`RemoteCollectionItems.Builder.addItem/setHasStableIds/setViewTypeCount`，以及 `AdapterViewFlipper.setFlipInterval(int)`、`setAutoStart(boolean)`、`stopFlipping()`。Android 36 stub 中 `stopFlipping()` 是 Flipper 自身方法，不是 RemoteViews 暴露的无参数调用；本方案不尝试用 RemoteViews 直接调用它，而以末尾待机 child、`loopViews=false` 和末尾完整静态 RemoteViews 提交控制结束画面。

| 候选 | 原型状态 | P0 真机比较重点 |
|---|---|---|
| `AdapterViewFlipper` host 推进 | 已在 K Pad / HyperOS 实测；录屏中间帧出现系统“加载窗口小部件时出现问题”占位，后续恢复静帧 | **失败并淘汰**；证据 `p0-host-once.mp4`、接触表及日志 |
| receiver 长时间持有 `goAsync()` | 旧版日志显示同进程后续广播延迟到前一事务结束后才送达；已改为同步准入 + expedited JobService | 不再使用；新版本在十次批次中记录 busy drop 与双实例重叠 |
| 短时逐帧预打包资源引用更新 | 新版 job 路径已实测 170/300ms、最长 8 帧、随机十次、双实例与 reset 中断 | **选定**；画面完整播放、回待机，详情见本报告“P0 真机阶段门结果” |

官方 API 说明和限制：

- [RemoteViews API](https://developer.android.com/reference/android/widget/RemoteViews)：允许 `AdapterViewFlipper` 作远程集合视图；API 31+ 的 `RemoteCollectionItems` 是内联资源列表接口，支持 `AdapterViewAnimator`。本原型在 API 31+ 使用它；API 26–30 使用 RemoteViewsService adapter 兼容实现。
- [AdapterViewFlipper API](https://developer.android.com/reference/android/widget/AdapterViewFlipper)：定义自动推进、flip interval 和 host advance 通知能力；不会单凭能循环便视为一次播放验收通过。
- [RemoteViews widget 布局](https://developer.android.com/develop/ui/views/appwidgets/layouts) 与 [高级 App Widget 更新](https://developer.android.com/develop/ui/views/appwidgets/advanced)：RemoteViews 更新有成本；本实现让 receiver 快速同步返回，以 JobService 管理短事务，不依赖延长 receiver 生命周期。coordinator 五秒事务 deadline 用于拒绝过期事务。

API 能力核查是原型设计依据。目标机已完成 host 与逐帧对照；当前唯一选型、屏幕结果和生命周期待测边界以本报告“P0 真机阶段门结果”为准。

## 工具链来源与锁定

工具只装入仓库忽略的 `.tools/`；未改全局 PATH、系统配置或 Windows 工程。

| 项 | 实际版本/校验 |
|---|---|
| Microsoft OpenJDK | 17.0.21.0.2；187,236,595 字节，576 ZIP 条目；与 Microsoft 发布 SHA-256 `49154292866386b2f4f09a3ecf3be07ab80a419149c84ec1d4419b9eec9e4b64` 匹配 |
| Android command-line tools | `15859902`；SHA-256 `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a` 与 Android Developers 下载页匹配 |
| SDK Platform / Build Tools / Platform Tools | API 36 revision 2 (extension level 17) / Build Tools 36.0.0 / Platform Tools 37.0.1 |
| Gradle Wrapper | 8.13；ZIP SHA-256 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` 与 Gradle 官方 `.sha256` 匹配 |
| Android Gradle Plugin / Kotlin Gradle Plugin | 8.11.1 / 2.2.21；AGP 8.11 官方要求 Gradle 8.13、JDK 17、最高支持 API 36；Kotlin 2.2.21 官方兼容矩阵覆盖 AGP 8.11.1 与 Gradle 8.13 |

来源：[Android SDK Platform releases](https://developer.android.com/tools/releases/platforms)、[Android command-line tools 下载页及 SHA-256](https://developer.android.com/studio)、[AGP 8.11 兼容性](https://developer.android.com/build/releases/agp-8-11-0-release-notes)、[Kotlin Gradle Plugin 兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)、[Microsoft OpenJDK 下载与校验文件](https://learn.microsoft.com/en-us/java/openjdk/download)、[Gradle 8.13 官方发行与校验](https://services.gradle.org/distributions/gradle-8.13-bin.zip)。

Windows 工作区路径含中文，AGP 首次运行按其实际错误拒绝路径；在工程 `gradle.properties` 中使用 `android.overridePathCheck=true` 后构建通过。Gradle 单测 worker 对原始中文路径发生 `GradleWorkerMain` 启动失败；最终构建通过临时 ASCII `R:` drive alias 执行，该 alias 在命令结束后删除，不改文件路径或系统 PATH。

## P0 真机阶段门结果（2026-10-26）

### 设备与安装

| 项目 | 本次实测 |
|---|---|
| 设备 | 红米 K Pad（序列号不公开） |
| 系统 / HOME | Android 16 / API 36；HyperOS `OS3.0.310.0.WAOCNXM`；`com.miui.home` launcher `4.50.0.1182` |
| 安装 | registry 修正版 debug APK 安装成功，包 `com.coffee.pet.android` |
| Widget | 桌面实际添加小/大两个透明 widget，ID 171、172 |
| 验收版本 | APK SHA-256 `988AD30CEDCB65B2718AFE0345316BE509661741EAA46F423C37FAC505B754CC` |

### 技术选型与观测

**P0 唯一选型：同步 receiver 准入 + 每实例 expedited JobService + 短时逐帧 RemoteViews 资源引用更新。** receiver 立即返回；JobService 在事务期间管理播放、idle 提交与取消。不是 hold-open `goAsync()`。Job 调度/启动迟到或非 expedited 时取消回 idle，不转成普通排队工作。

| 验收项 | 屏幕与逻辑证据 | 结果 |
|---|---|---|
| Host advance 对照 | `p0-host-once.mp4`、`p0-host-once-contact.png`、`p0-host-once.log` | **失败/淘汰。** 录屏中多个时间点出现 HyperOS“加载窗口小部件时出现问题”占位；后续恢复坐姿。日志事务正常结束，屏幕错误仍判失败。 |
| 最长动作，300ms | `p0-perframe-300-row5.mp4`、`p0-perframe-300-row5-contact.png`、`p0-perframe-300-row5.log` | **通过。** 固定 row 5 共 8 帧，画面可见耳朵下垂/低头完整动作并回静态坐姿。帧提交间隔约 303–305ms；接收到 job-start 约 8ms；idle/end 为 2449ms。名义动作时长 8×300=2400ms，实际 elapsed 含调度与提交时间。 |
| 随机十次 + 双实例 / busy | `p0-perframe-random-10.mp4`、`p0-perframe-random-10-contact.png`、`p0-perframe-random-10-right.png`、`p0-perframe-random-10.log` | **通过。** 同一录制共 10 个已接受事务：171/172 首次动作重叠运行；两实例忙时追加点击各即时 drop（1ms）；之后完成 8 个随机动作。随机选中行 1、4、5、6、7。10 个事务均有帧、`idle committed=true` 与 `end`，画面复核左右 widget 动作和回坐姿。 |
| Reset 中断 / 恢复 | `p0-reset-interrupt.mp4`、`p0-reset-interrupt-contact.png`、`p0-reset-interrupt.log` | **通过。** txn54 row5 在播放到 col4 时按“重置全部原型小部件到待机”；Job 被停止且 worker 收到 interrupt，不重试、不再推进旧帧。录屏后续显示桌面待机坐姿。`onStopJob idle=false` 是因为 reset 路径先使 coordinator 事务失效并提交 idle，stop 回调随后作为旧令牌到达。 |

**时间口径：** log 的 `elapsed` 是 `SystemClock.elapsedRealtime()` 事务时间；图中 100/200ms 接触表间隔是抽样间隔；原有图片文件名里的 sleep 参数不作为测量值。逐帧脚本的接触表标签是视频时间点；屏幕行为以录屏画面与同期事务日志联合判断。

### 新版本设备日志结论

- 接收至 `job-start` 约 8–19ms；schedule 同步耗时约 6–15ms。
- 170ms 随机批次中，8 帧事务实际约 1.41–1.42s，4/5/6 帧事务约 0.90–1.07s；最后帧后提交 idle 成功。名义帧时长分别是帧数×170ms。
- 同实例忙时点击在 0–1ms drop；171、172 两个事务在不同 executor 线程上有真实重叠。
- 300ms row5 的 8 次逐帧更新间隔约 303–305ms，末帧后 idle 约 2.449s。
- 随机批次、300ms 和 reset 中断的 CoffeeP0 日志未见 Job 执行异常；host 候选的失败来自录屏中的 launcher 错误占位。
- registry 并发修正后的 JVM 验证：coordinator 7 项 + production job-run registry 2 项，共 9 项通过、0 失败。`testDebugUnitTest assembleDebug lintDebug` 已成功；lint 0 errors、5 warnings。无需据此重复运行构建。

### 阶段门范围

P0 的最小播放技术选型门**通过**，唯一选型及实测预算已交回主代理作为后续阶段安排依据。此结论不把未完成的生命周期矩阵纳入通过范围：widget resize/旋转、删除实例、普通系统进程回收与重启恢复、锁屏/桌面切页、离线冷启动、完整尺寸与背景可视性矩阵仍待 P2/P3。当前 reset 中断只覆盖一次显式“重置全部到待机”取消路径。P1 正在由主代理派发；P2/P3 生命周期矩阵未完成。

### 证据索引

| 证据 | 文件 |
|---|---|
| Host 错误占位录屏、接触表、事务日志 | `p0-host-once.mp4` · `p0-host-once-contact.png` · `p0-host-once.log` |
| 300ms 八帧逐帧动作 | `p0-perframe-300-row5.mp4` · `p0-perframe-300-row5-contact.png` · `p0-perframe-300-row5.log` |
| 随机十次、双实例及 busy drop | `p0-perframe-random-10.mp4` · `p0-perframe-random-10-contact.png` · `p0-perframe-random-10-right.png` · `p0-perframe-random-10.log` |
| Reset 中断恢复 | `p0-reset-interrupt.mp4` · `p0-reset-interrupt-contact.png` · `p0-reset-interrupt.log` |
| 当前设备偏好读回 | `p0-diagnostics.xml` values: `PER_FRAME`, `170`, random row `0` |
| 视频分析工具说明 | `video-analysis/README.md`；脚本 `android/tools/video_contact_sheet.py` |
