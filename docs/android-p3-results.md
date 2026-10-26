# Android P3 集成验收与 Release 交付

状态：**已完成并通过，Code 3 已安装交付。** P0/P1/P2/P3 目标矩阵与用户协作的安装、升级、离线、功耗观察、播放中锁屏和真实重启均已验收。原有核心记录见 [P1/P2 结果](android-p1-p2-results.md)。

> 证据说明：本文引用的截图、UI dump、日志、录屏和 APK 均为本机验收材料，保留在本地且未推送。公开仓库只提交源码、资源与结果文档。

**P2 功能 gate 于 2026-10-26 23:11 通过：** 小号默认 1×1、大号默认 2×2（须退出旧选择器后重开以刷新包更新缓存），50%/100% 完整等比、真实宿主 resize 中断、删除一个/最后一个、旧回调失效及重添继承均通过。正式 provider label 已在目标机选择器显示“咖啡桌宠 / 小号咖啡猫 / 大号咖啡猫”。

## 已完成的 P3 准备与局部验收

- 小号/大号 `requestPinAppWidget` 入口、Activity 返回后的 widget ID 重扫、`singleTop` intent 重路由已实现。HyperOS pin 限制已完成有界定位，设置页显示手动系统添加路径及真实请求反馈；系统添加已走通。
- `testDebugUnitTest lintDebug assembleDebug` 成功；路由已有 `p3-route-global.xml`、`p3-route-instance.xml` 定向证据，由主代理复核图标无 EXTRA 返回全局。
- 设备为 Redmi K Pad / Android 16 API 36 / HyperOS 3.0.310.0。原 171/172 在删除生命周期验收中移除；早期阶段一度保留小号 175 与大号 174，最终交付只保留新增的小号 1×1 widget 176。其它应用部件（含 170）未操作。

## P3 完成结论

八动作与速度边界、播放中配置快照、动画关闭/恢复默认中断、真实方向变化、应用离开/返回、进程死亡/过期 busy 恢复及 release 构建静态门均已完成。2026-10-27 随后完成 HyperOS 安装确认、Code 2→Code 3 同签覆盖、widget/设置保留、真实离线冷启与播放、等长 CPU/后台观察、空闲和播放中锁屏、用户 PIN 后人工解锁及真实重启保留。最终设备保留一个本应用小号 1×1 widget，运行 Code 3；Code 2 和 Code 3 均安装成功，Code 3 `firstInstallTime` 保留 Code 2 首次安装时间。当前 launcher 只有 1 页，页面切换记为不适用。

## 前期设备限制（已解除）

早期 HyperOS 安装确认曾返回 `INSTALL_FAILED_USER_RESTRICTED`，随后用户在设备上允许安装，RC 与正式包均成功。所有锁屏、解锁和重启步骤均由用户明确授权；PIN 始终由用户手动输入，ADB 未尝试凭据或绕过系统限制。

## 2026-10-26 补充实机验收：删除生命周期

- **删除一个（171）通过。** 先在自己小号空白边缘长按打开系统“移除”菜单，再用应用 UID 的显式广播触发正式 PlaybackReceiver（`run-as ... am broadcast --user 0`），并点系统“移除”。该输入用于可重复地在菜单已打开时进入播放，播放仍走正式 PER_FRAME/JobService。171 于 22:56:39.924 开始第 0 帧，22:56:40.046 收到真实 provider-onDeleted，任务中断。172 同时播放 8 帧至 22:56:41.198 正常 idle，未受影响。
- **删除最后一个（172）通过。** 22:58:02.087 第 0 帧；22:58:02.188 真实 onDeleted；22:58:02.201 job-interrupted。屏幕只剩原有其它应用部件，两个咖啡均消失。
- 持久播放状态为 tombstone：171=`0,0,68,28,25,-1,190,0`，172=`0,0,42,28,33,-1,170,0`，前两项为 exists/busy；删除递增 transaction/generation 使旧回调失效。DataStore 原始 protobuf 仅剩 global_* 五项，已无 widget_171/172 canonical keys。
- 证据目录 `android-p1-p2-evidence/`：`p3-delete-small-menu.{xml,png}`、`p3-after-delete-small.{xml,png}`、`p3-delete-small-log.txt`、`p3-delete-small-state.txt`、`p3-delete-last-menu.{xml,png}`、`p3-after-delete-last.{xml,png}`、`p3-delete-last-log.txt`、`p3-delete-last-state.txt`、`p3-deleted-canonical.pb`。其它应用猫、时钟和 widget 170 未操作。
- 以下为早期检查记录；重添继承、resize 和旧实例不复活的最终结果见后文。旧实例横屏外框均 718×718（网格单元为 359×359，实际均 2×2），不能将其中设置 70%/85% 的视觉差别计作小/大占格通过。正在修正正式名称及 targetCell 尺寸元数据后重添实测。

## 补充实机验收：resize、重添与添加路径

- 删除后数分钟再经系统选择器添加，新 ID 为 173（小号）、174（大号）；旧 ID 171/172 未复活。`p3-readded-canonical.pb` 只含全局及新实例配置。新实例均继承当时 global 的 random/170ms/85%/播放动作/动画跟随；173 随后单独设置 100%，174 新增仍为 85%，见 `p3-large-added-normal.{xml,png}` 及 `p3-large-settings.xml`。
- **resize 与中断通过。** 173 先由 2×2 缩为 1×2，再在播放 row4 时拖底边缩为 1×1（外框 `[427,92][786,451]`，host `[463,128][750,415]`）。23:03:29.537 首帧，23:03:29.914 收到真实 options-changed，23:03:29.918 `wasBusy=true` 取消；29.921 job-interrupted。`p3-resize-playing-log.txt`、`p3-small-resize-explicit.{xml,png}`、`p3-small-one-cell-idle.{xml,png}` 为逻辑及最终静止屏幕证据。
- **50%/100% 比例与完整性通过。** 小号在实际 1×1、大号在实际 2×2 分别通过正式设置 UI 保存 50%/100%，屏幕猫耳、尾部和脚掌完整，透明边缘、等比缩放。证据 `p3-small-50-confirmed.{xml,png}`、`p3-small-100-confirmed.{xml,png}`、`p3-large-50-confirmed.{xml,png}`、`p3-large-100-confirmed.{xml,png}`。早期同名不带 confirmed 的截图是未保存草稿操作，不作边界通过证据。
- **系统添加路径通过，pin 存在 OEM 限制。** 正常桌面长按空白 → 小部件 → 安卓小部件 → 列表末尾“咖啡桌宠” → 点小/大卡片 → 完成，已真实添加 173/174。内置 pin 按钮在 HyperOS 启动 AddItemActivity 后立即退出，无确认页与新增 ID；不再重复点。设置页补充持久手动路径与“已请求系统添加，若未出现确认页……”真实反馈（`p3-pin-feedback.{xml,png}`）；不显示添加成功。
- **默认占格的早期检查记录。** `targetCellWidth/Height` 分别 1/2 已加入；小号 min 从 80dp 降为 40dp、minResize40dp，大号 min160dp/minResize80dp，均可 resize。首次 min40 安装时旧选择器仍显示两个 2×2，须退出后新开选择器排除缓存，未提前记为通过。provider 正式 label 需 manifest 协作方引用新增 small_widget_label / large_widget_label。
- 代码/资源修正的第一次完整 `testDebugUnitTest lintDebug assembleDebug` 成功（55s）；第二次小号 min40 资源调整 `assembleDebug` 成功（14s）。构建日志 `p3-build-log.txt`、`p3-small-spec-build.txt`，均使用约定 R: 工具链并释放映射，无签名环境下 debug 正常。

### 默认占格最终复核（已通过）

旧选择器缓存已定位：退出旧 Activity 后，从正常桌面重新完整进入系统添加路径，`p3-picker-fresh-spec40.{xml,png}` 实际列小号 **1×1**、大号 **2×2**。点小号新建 175，未做任何 resize，正常桌面外框 `[786,92][1145,451]` = 359×359；大号 174 外框 `[1145,92][1863,810]` = 718×718。见 `p3-default-small-large-cells.{xml,png}`、`p3-default-cells-appwidget.txt`。小号 min40dp/target1、大号 min160dp/target2 在目标 launcher 的默认占格已区分；上文缓存状态的 2×2 记录仅保留为诊断过程。

## 2026-10-26 补充实机验收：动作边界、运行配置与进程恢复

- **八个动作与 170/300ms 边界通过。** `p3-actions-summary.json` 覆盖 row1..8 的两种速度：全部预期帧列齐全，170ms 实测帧间隔 172–176ms，300ms 实测 302–307ms；每轮结束画面与静止基准像素一致。完整视频和逐动作静止帧保留在 `p3-actions-170-rows1-4*`、`p3-actions-170-rows5-8*`、`p3-actions-300-rows1-4*`、`p3-actions-300-rows5-8*`。
- **播放中动作/速度快照通过。** 最新 `p3-runtime-snapshot-log.txt` 中 txn30 以 row1、约 302ms/帧完整播放到 idle；保存事件发生在 txn30 仍 busy 时，记录新配置 action6/170，旧 txn 未被改写；下一次点击的 txn31 才使用 row6，帧间隔约 175ms。`p3-runtime-snapshot-first-idle.png`、`p3-runtime-snapshot-second-idle.png` 及对应视频/XML 证明旧事务结束与下一动作结束均为桌面静止画面；`p3-runtime-snapshot-crops.jpg` 为两张原图的猫区域缩略证据。早期 txn26/27 日志仅留作失败诊断，不计通过。
- **动画关闭与恢复默认通过。** `p3-animation-off-log.txt` 表明实例设置保存时 txn32 仍 busy，随后取消并中断 job；关闭后再次点击只产生 `drop ... animation_disabled`，无新事务。`p3-restore-defaults-log.txt` 表明 174/175 同时处于播放时恢复默认，174 busy 被取消，设置页恢复 random/170ms/85%，两实例回到静止。保存页 XML、桌面 XML、视频及 `p3-animation-restore-crops.jpg` 保留真实 UI 反馈。
- **进程死亡与过期 busy 恢复通过。** 最终样本由 PID 13695 接受 txn46 并写入 busy；frame0 后进程被 `kill -9`，`pid_after_kill` 为空时 `p3-process-death-busy.xml` 仍记录 `1,1,46,...`。等待超过 5 秒事务 deadline 后，桌面真实点击由新 PID 14059 恢复，过期状态递增事务并启动 txn48；8 帧完整播放至 idle，最终状态为 `1,0,48,13,63,1,300,0`。证据为 `p3-process-death-pids.txt`、`p3-process-death-accepted.txt`、`p3-process-death-busy.xml`、`p3-process-death-recovered.xml`、`p3-process-death-before-recovery.*`、`p3-process-death-recovered.*`、`p3-process-death-recovered-crop.jpg` 与 `p3-process-death-log.txt`。
- 进程测试的中间失败样本为 PID 29132、txn44：点击后、取 PID 前进程已自然死亡，且后续 `run-as am broadcast` 未进入 receiver；该样本只用于定位 Harness 路径问题，不计通过。最终报告的 PID 与 busy/txn 均以上一段同一日志中的 PID 13695 / txn46 为准。

## RC 构建与安装状态

- `build-release.ps1 -P2Accepted -VersionCode 2 -VersionName 1.0.0-rc.1` 已成功；构建期间修正了外部 signing properties 的 Windows 路径被 `Properties.load` 当作转义符解析的问题，签名材料本身未改写、未复制。
- APK 静态门通过：`com.coffee.pet.android`、versionCode 2、versionName `1.0.0-rc.1`、minSdk 26、targetSdk 35；v2 签名证书 SHA-256 为指定值；release manifest 无 `PrototypeActivity`/`HostFrameService`，无 `debuggable` 和 INTERNET 权限，唯一 launcher 入口为 `MainActivity`。APK 包内未发现 signing properties、`.jks` 或 keystore。
- 交付副本与 Gradle 原始 release APK 的 SHA-256 均为 `727cba45399d9e9f644701aeb0856712f28aff111a336838f3577d7f2573b9e9`，`.sha256` 内容一致。
- debug 异签已按授权卸载。早期 RC 安装曾返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`；用户允许安装后，Code 2 RC `install -r` 成功。随后真实添加小号 widget 176，并把该实例“点击桌面猫咪”改为“打开设置”并保存。
- Code 3 正式包 `coffee-1.0.0.apk` 已构建，versionCode 3、versionName `1.0.0`；静态门与 RC 相同并通过，副本和 Gradle 原始 APK 的 SHA-256 均为 `fa659105ab0e706d927014e8b770b83ccb1af24299c3611852c5080ee8f19523`。
- 当前代码 `testDebugUnitTest` 14/14、`lintDebug` 和 `assembleDebug` 成功，R: 映射已释放。Code 3 已通过 `adb install -r` 安装，设备内 base.apk 的 SHA-256 与交付副本一致。
- 汇总静态证据与测试计数见 `release-final-static-verification.txt`。

## 2026-10-27 安装、升级与离线验收

- **Code 3 同签升级通过。** RC 的 versionCode 2 / `1.0.0-rc.1` 首次安装于 00:05:10；Code 3 `install -r` 成功，versionCode 3 / `1.0.0`，`firstInstallTime` 保持 00:05:10。升级前将 widget 176 的点击行为存为非默认“打开设置”，升级后该值、widget ID 和 1×1 provider 均保留；随后恢复最终默认 random / 170 ms / 85% / 动画开启 / 点击播放动作。
- **真实离线冷启、设置和播放通过。** 关闭 Wi‑Fi 与移动数据后，系统默认网络为 `none`。强制停止应用后启动返回 `LaunchState: COLD`、209 ms；实例动画开关可离线关闭、保存、恢复并再次保存。断网点击 widget 176 后完整产生 8 帧并 `idle committed=true`，结束后无活动 service。随后恢复原 Wi‑Fi 与移动数据，设备重新连接 COFFEE。
- **证据：** `android-release-evidence-1027/` 下 `rc2-*`、`code3-*`、`offline-*` XML/日志与最终锁屏、重启记录。

## 2026-10-27 等长短功耗观察

- 在 Code 3、屏幕唤醒、同一 widget 176 与 launcher PID 下使用两组约 20 秒窗口，未 reset batterystats。
- **静态：** 真实时长 20.090 秒、0 次点击；app CPU 增量 0 tick，launcher 319 tick；app `pwi 0.367→0.367`。
- **点击：** 真实时长 20.809 秒，固定 6 次点击、约每 3.3 秒一次；app CPU 增量 10 tick（CLK_TCK=100，即 0.10 秒，约 0.48%），launcher 418 tick；app `pwi 0.367→0.370`。6 次均有 `job-start` 与 `idle`，结束后 0 running jobs、无活动 service。
- 设备观测时 USB 供电，电量 59%、温度 32.4°C；launcher 基线还包含其它动态组件噪声。数据仅作为短样本 CPU/后台相对对照，误差受充电与桌面噪声影响，不承诺 mAh。详细值见 `android-release-evidence-1027/equal-window-summary.txt`。

## 生命周期与最终设备项

- **真实方向变化通过。** `p3-lifecycle-portrait.xml` 根画布为 `3008×1880`，`p3-lifecycle-landscape.xml` 根画布为 `1880×3008`；日志在 23:42:54.094 收到 provider options-changed，随后以 `wasBusy=true` 取消 txn37 并递增 txn38。`p3-lifecycle-rotation-return-crops.jpg` 保留方向画面与返回后的猫咪静止缩略图。文件名沿用了测试过程命名，方向以 XML 根画布为准。
- **离开/返回通过。** txn39 正常播放结束后离开；返回后 txn40 启动，打开 Calendar 时 job-stopped 且 idle=true。`p3-lifecycle-drawer.xml` 实际包名为 `com.android.calendar`，`p3-lifecycle-drawer-thumb.jpg` 为只读缩略证据；返回桌面的 `p3-lifecycle-return-home.xml/png` 为 `com.miui.home`，猫咪静止。该既有测试不重复执行，也不改动 Calendar 内容。
- **launcher 页面切换未执行。** 设备当前 launcher 的 accessibility 信息为“第1页，共1页”，没有第二页可切；不能把应用切换证据计成页面切换。
- **空闲锁屏/人工 PIN 解锁通过。** 发送一次 `KEYCODE_SLEEP` 后实测 `Asleep`、keyguard `showing=true`、`INTERACTIVE_STATE_SLEEP`、屏幕关闭；用户手动输入 PIN 解锁，未使用 ADB 凭据输入。解锁后 Code 3、widget 176 与最终设置保留。
- **播放中锁屏通过。** txn15 已有 `job-start + 2 frames` 后 191 ms 发送一次睡眠键；事务在屏灭后继续完成第 5 帧，`idle committed=true` 并在 892 ms 正常结束。用户解锁后静态猫、设置保留；没有重播，只有原 1 条 `job-start`，0 running jobs、无活动 service。证据见 `android-release-evidence-1027/busy-lock-verification.txt`。
- **真实重启通过。** boot_id 由 `1b0ded09-a817-48b1-8e05-f560fe244734` 变为 `eef699cd-26c0-4fc0-9d6c-a303222358ca`，重启后 uptime 从 0 重新计时。设备上 base.apk SHA-256 与交付 APK 完全一致；Code 3、versionName `1.0.0`、签名 ID `30a871f2`、widget 176 和默认设置均保留。
- **重启后点击通过。** 一次点击 txn18 完整播放 8 帧，`idle committed=true` 并在 1410 ms 结束；之后仍只有 1 条 `job-start`，0 running jobs、无活动 service，无 busy 残留或重播。
- **离线与等长观察通过。** 真实断网、设置和播放结果见上文；功耗边界与 USB/噪声条件同时记录。

## 最终交付状态

- 正式包：[coffee-1.0.0.apk](../outputs/android-release/coffee-1.0.0.apk)，10,689,417 bytes，versionCode 3 / versionName `1.0.0`。
- APK SHA-256：`fa659105ab0e706d927014e8b770b83ccb1af24299c3611852c5080ee8f19523`。
- 证书 SHA-256：`2563e3bc4a436c79a83936e7511e90954ec94dada00af490c3f48178de581a49`。
- 设备为 Redmi K Pad / Android 16 / HyperOS 3.0.310.0，竖屏 1880×3008。最终 Wi‑Fi=1、移动数据=1、`user_rotation=0`、`accelerometer_rotation=1`，设备唤醒且已由用户解锁。
- 桌面保留一个本应用小号 1×1 widget（id 176）；最终实例设置为 random / 170 ms / 85% / 动画开启 / 点击播放动作。真实静态裁图见 `android-release-evidence-1027/post-reboot-widget.jpg`。
- P3 无未决功能项。launcher 页面切换因只有 1 页记为不适用。
