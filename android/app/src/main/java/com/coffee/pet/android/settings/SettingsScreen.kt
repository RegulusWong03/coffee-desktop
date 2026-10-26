package com.coffee.pet.android.settings

import android.appwidget.AppWidgetManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.coffee.pet.android.R
import com.coffee.pet.android.playback.FrameCatalog
import com.coffee.pet.android.playback.PlaybackJobScheduler
import com.coffee.pet.android.playback.PlaybackRuntime
import com.coffee.pet.android.widget.LargePetWidgetProvider
import com.coffee.pet.android.widget.SmallPetWidgetProvider
import com.coffee.pet.android.widget.WidgetRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(initialWidgetId: Int?, refreshKey: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { AppWidgetManager.getInstance(context) }
    val widgetChoices = remember(refreshKey) {
        buildList {
            manager.getAppWidgetIds(ComponentName(context, SmallPetWidgetProvider::class.java)).sorted().forEachIndexed { index, wid -> add(wid to "小号咖啡 ${index + 1}") }
            manager.getAppWidgetIds(ComponentName(context, LargePetWidgetProvider::class.java)).sorted().forEachIndexed { index, wid -> add(wid to "大号咖啡 ${index + 1}") }
        }
    }
    val widgetIds = widgetChoices.map { it.first }
    var selectedId by rememberSaveable { mutableIntStateOf(initialWidgetId ?: -1) }
    LaunchedEffect(initialWidgetId) {
        selectedId = initialWidgetId ?: -1
    }
    val id = selectedId.takeIf { it in widgetIds }
    var draft by remember { mutableStateOf(PetSettings()) }
    var saved by remember { mutableStateOf(PetSettings()) }
    var loaded by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var previewPlaying by remember { mutableStateOf(false) }
    var previewRow by remember { mutableIntStateOf(3) }
    var frame by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf("") }
    val bg = Color(0xFFFFF8F0)

    LaunchedEffect(id) {
        loaded = false
        val value = SettingsRepository.read(context, id)
        draft = value; saved = value; loaded = true
    }
    LaunchedEffect(draft) {
        if (draft != saved) notice = ""
    }
    LaunchedEffect(preview, previewPlaying, draft.intervalMs, previewRow) {
        while (preview && previewPlaying) {
            delay(draft.intervalMs.toLong())
            if (frame + 1 < FrameCatalog.frameCount(previewRow)) frame++ else {
                frame = 0
                previewPlaying = false
            }
        }
    }

    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = bg) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp)) {
            Text("咖啡桌宠", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color(0xFF533B31))
            Text("设置保存在本机", color = Color(0xFF79665D), modifier = Modifier.padding(bottom = 16.dp))
            if (manager.isRequestPinAppWidgetSupported) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        notice = if (requestPinWidget(context, SmallPetWidgetProvider::class.java, 1)) "已请求系统添加。若未出现确认页，请按下方桌面路径添加。" else "系统未接受添加请求，请按下方桌面路径添加。"
                    }) { Text("添加小号猫咪") }
                    OutlinedButton(onClick = {
                        notice = if (requestPinWidget(context, LargePetWidgetProvider::class.java, 2)) "已请求系统添加。若未出现确认页，请按下方桌面路径添加。" else "系统未接受添加请求，请按下方桌面路径添加。"
                    }) { Text("添加大号猫咪") }
                }
                Text("由系统确认添加到桌面；返回后会刷新实例列表。", fontSize = 12.sp, color = Color.Gray)
            }
            Text("桌面手动添加：长按空白处 → 小部件 → 安卓小部件 → 咖啡桌宠，选择小号或大号。也可在小部件页搜索“咖啡”。", fontSize = 12.sp, color = Color.Gray)
            if (notice.contains("添加")) Text(notice, color = Color(0xFF557448))
            if (widgetIds.isNotEmpty()) {
                Text("设置对象", fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(onClick = { expanded = "target" }) { Text(if (id == null) "全局默认（新实例）" else widgetChoices.firstOrNull { it.first == id }?.second ?: "桌面部件") }
                    DropdownMenu(expanded == "target", { expanded = "" }) {
                        DropdownMenuItem(text = { Text("全局默认（新实例）") }, onClick = { selectedId = -1; expanded = "" })
                        widgetChoices.forEach { (wid, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { selectedId = wid; expanded = "" }) }
                    }
                }
            } else Text("尚无桌面实例。以下设置作为全局默认，新添加的实例会沿用。", color = Color(0xFF79665D))
            Text(if (id == null) "全局默认 · 新实例沿用" else "${widgetChoices.firstOrNull { it.first == id }?.second ?: "桌面部件"} · 仅动画可跟随全局，其它设置独立保存", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 6.dp))
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("动画", fontWeight = FontWeight.SemiBold)
                    Text(if (id == null) "跟随全局的部件立即生效" else if (draft.animationOverride == null) "当前跟随全局；可单独覆写" else "仅本实例立即生效", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(draft.animationOverride ?: draft.animationEnabled, onCheckedChange = { draft = draft.copy(animationEnabled = it, animationOverride = if (id == null) null else it) })
            }
            if (id != null && draft.animationOverride != null) TextButton(onClick = { draft = draft.copy(animationEnabled = saved.animationEnabled, animationOverride = null) }) { Text("改为跟随全局") }
            Text("动作", fontWeight = FontWeight.SemiBold)
            Box {
                OutlinedButton(onClick = { expanded = "action" }) { Text(actionName(draft.actionRow)) }
                DropdownMenu(expanded == "action", { expanded = "" }) {
                    (0..8).forEach { row -> DropdownMenuItem(text = { Text(actionName(row)) }, onClick = { draft = draft.copy(actionRow = row); expanded = "" }) }
                }
            }
            Text("下次点击时生效，不会中断当前动作。", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(10.dp))
            Text("速度 · ${draft.intervalMs} 毫秒/帧", fontWeight = FontWeight.SemiBold)
            Slider(draft.intervalMs.toFloat(), { draft = draft.copy(intervalMs = (it.toInt() / 10 * 10).coerceIn(170, 300)) }, valueRange = 170f..300f, steps = 12)
            Text("仅影响下一次播放，范围 170–300 毫秒；数值越大越慢。", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text("猫咪显示大小 · ${draft.sizePercent}%", fontWeight = FontWeight.SemiBold)
            Slider(draft.sizePercent.toFloat(), { draft = draft.copy(sizePercent = (it.toInt() / 5 * 5).coerceIn(50, 100)) }, valueRange = 50f..100f, steps = 9)
            Text("保存后立即停止当前动作并刷新，猫保持比例。", fontSize = 12.sp, color = Color.Gray)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("点击桌面猫咪", fontWeight = FontWeight.SemiBold); Text("保存后立即生效", fontSize = 12.sp, color = Color.Gray) }
                Box {
                    OutlinedButton(onClick = { expanded = "click" }) { Text(if (draft.clickOpensSettings) "打开设置" else "播放动作") }
                    DropdownMenu(expanded == "click", { expanded = "" }) {
                        DropdownMenuItem(text = { Text("播放动作") }, onClick = { draft = draft.copy(clickOpensSettings = false); expanded = "" })
                        DropdownMenuItem(text = { Text("打开设置") }, onClick = { draft = draft.copy(clickOpensSettings = true); expanded = "" })
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = loaded && draft != saved, onClick = {
                    scope.launch {
                        val animationBefore = if (id == null) saved.animationEnabled
                            else (saved.animationOverride ?: SettingsRepository.readGlobal(context).animationEnabled)
                        val effectiveAnimationAfter = if (id == null) draft.animationEnabled
                            else (draft.animationOverride ?: SettingsRepository.readGlobal(context).animationEnabled)
                        SettingsRepository.save(context, id, draft)
                        if (id == null) {
                            val animationChanged = draft.animationEnabled != saved.animationEnabled
                            val sizeChanged = draft.sizePercent != saved.sizePercent
                            if (animationChanged || sizeChanged) widgetIds.forEach { wid ->
                                val current = SettingsRepository.readFast(context, wid)
                                val effectiveAnimationChanged = animationChanged && current.animationOverride == null
                                val effectiveSizeChanged = sizeChanged && !SettingsRepository.hasSizeOverride(context, wid)
                                if (effectiveAnimationChanged || effectiveSizeChanged) {
                                    PlaybackJobScheduler.cancel(context, wid); PlaybackRuntime.get(context).cancel(wid, "global_setting_changed")
                                }
                            }
                        }
                        else {
                            val animationEffectiveChanged = animationBefore != effectiveAnimationAfter
                            if (draft.sizePercent != saved.sizePercent || animationEffectiveChanged) {
                                if (draft.sizePercent != saved.sizePercent || animationEffectiveChanged) {
                                    PlaybackJobScheduler.cancel(context, id); PlaybackRuntime.get(context).cancel(id, "instance_setting_changed")
                                }
                            }
                            PlaybackRuntime.get(context).saveNextConfig(id, if (draft.actionRow == 0) -1 else draft.actionRow, draft.intervalMs)
                            if (draft.clickOpensSettings != saved.clickOpensSettings) WidgetRenderer.refreshClickBehavior(context, id, draft.clickOpensSettings)
                        }
                        saved = draft; notice = "已保存"
                    }
                }) { Text("保存设置") }
                OutlinedButton(onClick = {
                    frame = 0
                    previewRow = draft.actionRow.takeIf { it in 1..8 } ?: (1..8).random()
                    previewPlaying = true
                    preview = true
                }) { Text("预览动作") }
                TextButton(onClick = {
                    scope.launch {
                        SettingsRepository.resetAll(context, widgetIds)
                        widgetIds.forEach { wid ->
                            PlaybackJobScheduler.cancel(context, wid); PlaybackRuntime.get(context).cancel(wid, "restore_defaults")
                        }
                        draft = PetSettings(); saved = draft; notice = "已恢复全部默认设置"
                    }
                }) { Text("恢复全部默认") }
            }
            if (notice.isNotEmpty()) Text(notice, color = Color(0xFF557448), modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (preview) Dialog(onDismissRequest = { preview = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.88f), shape = MaterialTheme.shapes.large, color = bg) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                Text("动作预览 · 不影响桌面猫咪", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                val row = draft.actionRow.takeIf { it in 1..8 } ?: 3
                Image(painterResource(if (previewPlaying) FrameCatalog.frame(previewRow, frame.coerceIn(0, FrameCatalog.frameCount(previewRow) - 1)) else FrameCatalog.frame(0, 0)), null, Modifier.size(220.dp))
                Text(if (previewPlaying) "临时播放一次" else "动作结束 · 已回待机", fontSize = 12.sp, color = Color.Gray)
                TextButton(onClick = { previewPlaying = false; preview = false }) { Text("结束预览") }
            }
        }
    }
}

private fun requestPinWidget(context: android.content.Context, provider: Class<*>, requestCode: Int): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    val callbackIntent = Intent(context, com.coffee.pet.android.MainActivity::class.java)
        .setAction("com.coffee.pet.android.REFRESH_WIDGETS")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val callback = PendingIntent.getActivity(
        context,
        20260 + requestCode,
        callbackIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return manager.requestPinAppWidget(ComponentName(context, provider), null, callback)
}

private fun actionName(row: Int) = when (row) {
    0 -> "随机动作"
    1, 2 -> "跑动 $row"
    3 -> "打招呼"
    4 -> "舔爪"
    5 -> "垂耳"
    6 -> "举爪"
    7 -> "低头"
    8 -> "歪头"
    else -> "随机动作"
}
