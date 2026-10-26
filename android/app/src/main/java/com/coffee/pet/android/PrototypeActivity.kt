package com.coffee.pet.android

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.coffee.pet.android.playback.P0PlaybackMode
import com.coffee.pet.android.playback.P0PlaybackModeStore
import com.coffee.pet.android.widget.WidgetRenderer

/** Diagnostic launcher only. This screen does not animate and is not the widget acceptance surface. */
class PrototypeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(28, 28, 28, 28) }
        root.addView(TextView(this).apply {
            text = getString(R.string.prototype_instructions)
            textSize = 18f
        })
        root.addView(TextView(this).apply { text = getString(R.string.mode_section_title); textSize = 18f })
        val modes = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val perFrame = RadioButton(this).apply { id = View.generateViewId(); tag = P0PlaybackMode.PER_FRAME; text = getString(R.string.mode_per_frame) }
        val hostAdvance = RadioButton(this).apply { id = View.generateViewId(); tag = P0PlaybackMode.HOST_ADVANCE; text = getString(R.string.mode_host_advance) }
        modes.addView(perFrame); modes.addView(hostAdvance)
        modes.check(if (P0PlaybackModeStore.get(this) == P0PlaybackMode.HOST_ADVANCE) hostAdvance.id else perFrame.id)
        root.addView(modes)
        root.addView(TextView(this).apply { text = getString(R.string.speed_section_title); textSize = 18f })
        val speeds = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val defaultSpeed = RadioButton(this).apply { id = View.generateViewId(); tag = 170; text = getString(R.string.speed_default) }
        val slowSpeed = RadioButton(this).apply { id = View.generateViewId(); tag = 300; text = getString(R.string.speed_slow) }
        speeds.addView(defaultSpeed); speeds.addView(slowSpeed)
        speeds.check(if (P0PlaybackModeStore.getInterval(this) == 300) slowSpeed.id else defaultSpeed.id)
        root.addView(speeds)
        root.addView(TextView(this).apply { text = "诊断动作行（0 随机；1–8 固定）"; textSize = 18f })
        val actions = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val actionButtons = (0..8).map { row ->
            RadioButton(this).apply {
                id = View.generateViewId()
                tag = row
                text = when (row) {
                    0 -> "随机"
                    1, 2 -> "第 $row 行：跑动"
                    3 -> "第 3 行：打招呼"
                    4 -> "第 4 行：跳跃"
                    else -> "第 $row 行：动作"
                }
                actions.addView(this)
            }
        }
        actions.check(actionButtons.first { it.tag == P0PlaybackModeStore.getDebugActionRow(this) }.id)
        root.addView(actions)
        val modeStatus = TextView(this).apply { text = getString(R.string.mode_applied) }
        root.addView(modeStatus)
        root.addView(Button(this).apply { text = "重置全部原型小部件到待机"; setOnClickListener { WidgetRenderer.cancelAll(this@PrototypeActivity) } })
        root.addView(Button(this).apply {
            text = getString(R.string.mode_apply)
            setOnClickListener {
                val selected = modes.findViewById<RadioButton>(modes.checkedRadioButtonId)?.tag as? P0PlaybackMode ?: P0PlaybackMode.PER_FRAME
                val interval = speeds.findViewById<RadioButton>(speeds.checkedRadioButtonId)?.tag as? Int ?: P0PlaybackModeStore.getInterval(this@PrototypeActivity)
                val actionRow = actionButtons.firstOrNull { it.id == actions.checkedRadioButtonId }?.tag as? Int ?: 0
                P0PlaybackModeStore.set(this@PrototypeActivity, selected, interval)
                P0PlaybackModeStore.setDebugActionRow(this@PrototypeActivity, actionRow)
                WidgetRenderer.cancelAll(this@PrototypeActivity)
                modeStatus.text = getString(R.string.mode_applied)
            }
        })
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }
}
