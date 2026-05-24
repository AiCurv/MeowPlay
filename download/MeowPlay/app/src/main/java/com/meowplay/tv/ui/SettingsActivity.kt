package com.meowplay.tv.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.meowplay.tv.R
import com.meowplay.tv.player.CacheManager

/**
 * Settings Activity — 8 categories of settings, ALL with selectable options that ACTUALLY WORK.
 * Every option configures Media3 ExoPlayer parameters via CacheManager.
 *
 * CRITICAL: NEVER use AlertDialog.setMessage() + setSingleChoiceItems() together!
 * They are mutually exclusive — setMessage hides the choice items.
 * Instead, show description as part of the title or first list item.
 */
class SettingsActivity : FragmentActivity() {

    private lateinit var settingsList: LinearLayout
    private lateinit var backBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsList = findViewById(R.id.settings_list)
        backBtn = findViewById(R.id.back_btn)

        backBtn.setOnClickListener { finish() }

        val cm = CacheManager.getInstance(this)

        addSettingRow("1. Cache Manager", "Advanced cache & buffer settings") {
            startActivity(Intent(this, CacheManagerActivity::class.java))
        }

        addSettingRow("2. Hardware/Software Decoder", "Current: ${CacheManager.HwAccelerationOptions.LABELS[cm.getHwAcceleration()]}") {
            showOptionDialog("Hardware Acceleration", CacheManager.HwAccelerationOptions.LABELS, cm.getHwAcceleration()) { idx ->
                cm.setHwAcceleration(CacheManager.HwAccelerationOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingRow("SW Decoder Fallback", "Current: ${if (cm.getSwFallback()) "Enabled" else "Disabled"}") {
            val opts = arrayOf("Enabled (default)", "Disabled")
            val cur = if (cm.getSwFallback()) 0 else 1
            showOptionDialog("Software Decoder Fallback", opts, cur) { idx ->
                cm.setSwFallback(idx == 0)
                refreshActivity()
            }
        }

        addSettingRow("Decoder Priority", "Current: ${CacheManager.DecoderPriorityOptions.LABELS[cm.getDecoderPriority()]}") {
            showOptionDialog("Decoder Priority", CacheManager.DecoderPriorityOptions.LABELS, cm.getDecoderPriority()) { idx ->
                cm.setDecoderPriority(CacheManager.DecoderPriorityOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingCategory("3. Audio Output Configuration")

        addSettingRow("Audio Passthrough", "Current: ${if (cm.getAudioPassthrough()) "Enabled" else "Disabled"}") {
            val opts = arrayOf("Enabled", "Disabled")
            val cur = if (cm.getAudioPassthrough()) 0 else 1
            showOptionDialog("Audio Passthrough", opts, cur) { idx ->
                cm.setAudioPassthrough(idx == 0)
                refreshActivity()
            }
        }

        addSettingRow("Audio Passthrough Codecs", "AC3/EAC3/DTS/TrueHD") {
            showPassthroughCodecsDialog(cm)
        }

        addSettingRow("Audio Output Device", "Current: ${CacheManager.AudioOutputOptions.LABELS[cm.getAudioOutput()]}") {
            showOptionDialog("Audio Output Device", CacheManager.AudioOutputOptions.LABELS, cm.getAudioOutput()) { idx ->
                cm.setAudioOutput(CacheManager.AudioOutputOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingRow("Audio Boost Mode", "Current: ${CacheManager.AudioBoostOptions.LABELS[CacheManager.AudioBoostOptions.VALUES.indexOfCompat(cm.getAudioBoost()).coerceAtLeast(0)]}") {
            showOptionDialog("Audio Boost Mode\nWARNING: High values may damage speakers!", CacheManager.AudioBoostOptions.LABELS,
                CacheManager.AudioBoostOptions.VALUES.indexOfCompat(cm.getAudioBoost()).coerceAtLeast(0)) { idx ->
                cm.setAudioBoost(CacheManager.AudioBoostOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingCategory("4. Subtitle Customization")

        addSettingRow("Subtitle Font Size", "Current: ${CacheManager.SubtitleSizeOptions.LABELS[cm.getSubtitleSize()]}") {
            showOptionDialog("Subtitle Font Size", CacheManager.SubtitleSizeOptions.LABELS, cm.getSubtitleSize()) { idx ->
                cm.setSubtitleSize(CacheManager.SubtitleSizeOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingRow("Subtitle Color", "Current: ${CacheManager.SubtitleColorOptions.LABELS[cm.getSubtitleColor()]}") {
            showOptionDialog("Subtitle Color", CacheManager.SubtitleColorOptions.LABELS, cm.getSubtitleColor()) { idx ->
                cm.setSubtitleColor(CacheManager.SubtitleColorOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingRow("Subtitle Background", "Current: ${CacheManager.SubtitleBgOpacityOptions.LABELS[cm.getSubtitleBgOpacity()]}") {
            showOptionDialog("Subtitle Background Opacity", CacheManager.SubtitleBgOpacityOptions.LABELS, cm.getSubtitleBgOpacity()) { idx ->
                cm.setSubtitleBgOpacity(CacheManager.SubtitleBgOpacityOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingRow("Subtitle Position", "Current: ${CacheManager.SubtitlePositionOptions.LABELS[cm.getSubtitlePosition()]}") {
            showOptionDialog("Subtitle Position", CacheManager.SubtitlePositionOptions.LABELS, cm.getSubtitlePosition()) { idx ->
                cm.setSubtitlePosition(CacheManager.SubtitlePositionOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingCategory("5. Playback Behavior")

        addSettingRow("Auto-Resume", "Current: ${CacheManager.AutoResumeOptions.LABELS[cm.getAutoResume()]}") {
            showOptionDialog("Auto-Resume", CacheManager.AutoResumeOptions.LABELS, cm.getAutoResume()) { idx ->
                cm.setAutoResume(CacheManager.AutoResumeOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingRow("Default Playback Speed", "Current: ${cm.getPlaybackSpeed()}x") {
            val speeds = CacheManager.PlaybackSpeedOptions.VALUES
            val labels = CacheManager.PlaybackSpeedOptions.LABELS
            var curIdx = 2 // default 1.0x
            for (i in speeds.indices) { if (speeds[i] == cm.getPlaybackSpeed()) { curIdx = i; break } }
            showOptionDialog("Default Playback Speed", labels, curIdx) { idx ->
                cm.setPlaybackSpeed(speeds[idx])
                refreshActivity()
            }
        }

        addSettingRow("Background Playback", "Current: ${if (cm.getBackgroundPlayback()) "Enabled" else "Disabled"}") {
            val opts = arrayOf("Enabled", "Disabled")
            val cur = if (cm.getBackgroundPlayback()) 0 else 1
            showOptionDialog("Background Playback (Audio Only)", opts, cur) { idx ->
                cm.setBackgroundPlayback(idx == 0)
                refreshActivity()
            }
        }

        addSettingRow("Picture-in-Picture", "Current: ${if (cm.getPipMode()) "Enabled" else "Disabled"}") {
            val opts = arrayOf("Enabled", "Disabled")
            val cur = if (cm.getPipMode()) 0 else 1
            showOptionDialog("Picture-in-Picture Mode", opts, cur) { idx ->
                cm.setPipMode(idx == 0)
                refreshActivity()
            }
        }

        addSettingCategory("6. Audio & Synchronization")

        addSettingRow("Audio Delay", "Current: ${cm.getAudioDelayMs()}ms") {
            showAudioDelayDialog(cm)
        }

        addSettingRow("Volume Control", "Current: ${CacheManager.VolumeControlOptions.LABELS[cm.getVolumeControl()]}") {
            showOptionDialog("Volume Control", CacheManager.VolumeControlOptions.LABELS, cm.getVolumeControl()) { idx ->
                cm.setVolumeControl(CacheManager.VolumeControlOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingRow("Normalize Audio", "Current: ${if (cm.getNormalizeAudio()) "Enabled" else "Disabled"}") {
            val opts = arrayOf("Enabled", "Disabled")
            val cur = if (cm.getNormalizeAudio()) 0 else 1
            showOptionDialog("Normalize Audio", opts, cur) { idx ->
                cm.setNormalizeAudio(idx == 0)
                refreshActivity()
            }
        }

        addSettingRow("Audio Channel Downmix", "Current: ${CacheManager.AudioChannelOptions.LABELS[cm.getAudioChannel()]}") {
            showOptionDialog("Audio Channel Downmix", CacheManager.AudioChannelOptions.LABELS, cm.getAudioChannel()) { idx ->
                cm.setAudioChannel(CacheManager.AudioChannelOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingCategory("7. Video & Display")

        addSettingRow("Screen Orientation", "Current: ${CacheManager.ScreenOrientationOptions.LABELS[cm.getScreenOrientation()]}") {
            showOptionDialog("Screen Orientation", CacheManager.ScreenOrientationOptions.LABELS, cm.getScreenOrientation()) { idx ->
                cm.setScreenOrientation(CacheManager.ScreenOrientationOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingRow("Aspect Ratio", "Current: ${CacheManager.AspectRatioOptions.LABELS[cm.getAspectRatio()]}") {
            showOptionDialog("Aspect Ratio", CacheManager.AspectRatioOptions.LABELS, cm.getAspectRatio()) { idx ->
                cm.setAspectRatio(CacheManager.AspectRatioOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingRow("Default Zoom Level", "Current: ${cm.getZoomLevel()}%") {
            showOptionDialog("Default Zoom Level", CacheManager.ZoomLevelOptions.LABELS,
                CacheManager.ZoomLevelOptions.VALUES.indexOfCompat(cm.getZoomLevel()).coerceAtLeast(0)) { idx ->
                cm.setZoomLevel(CacheManager.ZoomLevelOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingCategory("8. Network & Streaming")

        addSettingRow("Network Buffer Size", "Current: ${CacheManager.NetworkBufferOptions.LABELS[CacheManager.NetworkBufferOptions.VALUES.indexOfCompat(cm.getNetworkBufferMb()).coerceAtLeast(0)]}") {
            showOptionDialog("Buffer Size for Network Streams", CacheManager.NetworkBufferOptions.LABELS,
                CacheManager.NetworkBufferOptions.VALUES.indexOfCompat(cm.getNetworkBufferMb()).coerceAtLeast(0)) { idx ->
                cm.setNetworkBufferMb(CacheManager.NetworkBufferOptions.VALUES[idx])
                refreshActivity()
            }
        }

        addSettingRow("Prefer IPv6", "Current: ${if (cm.getPreferIpv6()) "Enabled" else "Disabled"}") {
            val opts = arrayOf("Enabled", "Disabled")
            val cur = if (cm.getPreferIpv6()) 0 else 1
            showOptionDialog("Prefer IPv6", opts, cur) { idx ->
                cm.setPreferIpv6(idx == 0)
                refreshActivity()
            }
        }

        addSettingRow("User-Agent String", "Current: ${cm.getUserAgent()}") {
            showUserAgentDialog(cm)
        }

        addSettingRow("Max Cache (Network)", "Current: ${CacheManager.NetworkMaxCacheOptions.LABELS[CacheManager.NetworkMaxCacheOptions.VALUES.indexOfCompat(cm.getNetworkMaxCacheMb()).coerceAtLeast(0)]}") {
            showOptionDialog("Max Cache Size for Network Streams", CacheManager.NetworkMaxCacheOptions.LABELS,
                CacheManager.NetworkMaxCacheOptions.VALUES.indexOfCompat(cm.getNetworkMaxCacheMb()).coerceAtLeast(0)) { idx ->
                cm.setNetworkMaxCacheMb(CacheManager.NetworkMaxCacheOptions.VALUES[idx])
                refreshActivity()
            }
        }
    }

    // ─── UI Helpers ───────────────────────────────────────────────────────────

    private fun IntArray.indexOfCompat(value: Int): Int {
        for (i in indices) { if (this[i] == value) return i }
        return -1
    }

    private fun addSettingCategory(title: String) {
        val tv = TextView(this).apply {
            text = title
            setTextColor(0xFFBB86FC.toInt())
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 8)
        }
        settingsList.addView(tv)
    }

    private fun addSettingRow(title: String, subtitle: String, onClick: () -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            background = createRowBackground(false)
            isFocusable = true
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.background = createRowBackground(hasFocus)
                v.animate().scaleX(if (hasFocus) 1.01f else 1.0f)
                    .scaleY(if (hasFocus) 1.01f else 1.0f)
                    .setDuration(100).start()
            }
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
        }
        val subtitleView = TextView(this).apply {
            text = subtitle
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 13f
        }

        container.addView(titleView)
        container.addView(subtitleView)
        settingsList.addView(container)
    }

    private fun createRowBackground(focused: Boolean) = android.graphics.drawable.GradientDrawable().apply {
        setColor(if (focused) 0x2D2D5E else 0xFF1A1A2E.toInt())
        cornerRadius = 12f
        if (focused) setStroke(2, 0x40FFFFFF.toInt())
    }

    /**
     * Show a selection dialog — uses ONLY setSingleChoiceItems, NEVER setMessage() + items together.
     * The description/warning goes in the TITLE, not as a message.
     */
    private fun showOptionDialog(title: String, items: Array<String>, currentIndex: Int, onSelect: (Int) -> Unit) {
        val dialog = Dialog(this, R.style.GlassDialog)
        dialog.setContentView(R.layout.dialog_track_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtDialogTitle = dialog.findViewById<TextView>(R.id.txt_dialog_title)
        val trackListLayout = dialog.findViewById<LinearLayout>(R.id.track_list_layout)

        txtDialogTitle.text = title

        items.forEachIndexed { index, label ->
            val btn = Button(this).apply {
                text = if (index == currentIndex) "● $label" else "  $label"
                setTextColor(if (index == currentIndex) 0xFFBB86FC.toInt() else 0xFFFFFFFF.toInt())
                setBackgroundColor(0x1A1A2E.toInt())
                setPadding(24, 16, 24, 16)
                textSize = 16f
                isFocusable = true
                setOnFocusChangeListener { v, hasFocus ->
                    v.setBackgroundColor(if (hasFocus) 0x2D2D5E.toInt() else 0x1A1A2E.toInt())
                }
                setOnClickListener {
                    onSelect(index)
                    dialog.dismiss()
                }
            }
            trackListLayout.addView(btn)
        }

        dialog.show()
    }

    private fun showPassthroughCodecsDialog(cm: CacheManager) {
        val codecs = arrayOf("AC3", "EAC3", "DTS", "TrueHD")
        val states = booleanArrayOf(cm.getAudioPassthroughAc3(), cm.getAudioPassthroughEac3(), cm.getAudioPassthroughDts(), cm.getAudioPassthroughTruehd())
        val setters = listOf<(Boolean) -> Unit>(
            { v: Boolean -> cm.setAudioPassthroughAc3(v) },
            { v: Boolean -> cm.setAudioPassthroughEac3(v) },
            { v: Boolean -> cm.setAudioPassthroughDts(v) },
            { v: Boolean -> cm.setAudioPassthroughTruehd(v) }
        )

        val dialog = Dialog(this, R.style.GlassDialog)
        dialog.setContentView(R.layout.dialog_track_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtDialogTitle = dialog.findViewById<TextView>(R.id.txt_dialog_title)
        val trackListLayout = dialog.findViewById<LinearLayout>(R.id.track_list_layout)
        txtDialogTitle.text = "Passthrough Codecs"

        codecs.forEachIndexed { index, codec ->
            val btn = Button(this).apply {
                text = if (states[index]) "● $codec" else "  $codec"
                setTextColor(if (states[index]) 0xFFBB86FC.toInt() else 0xFFFFFFFF.toInt())
                setBackgroundColor(0x1A1A2E.toInt())
                setPadding(24, 16, 24, 16)
                textSize = 16f
                isFocusable = true
                setOnFocusChangeListener { v, hasFocus ->
                    v.setBackgroundColor(if (hasFocus) 0x2D2D5E.toInt() else 0x1A1A2E.toInt())
                }
                setOnClickListener {
                    setters[index](!states[index])
                    dialog.dismiss()
                    refreshActivity()
                }
            }
            trackListLayout.addView(btn)
        }

        dialog.show()
    }

    private fun showAudioDelayDialog(cm: CacheManager) {
        val dialog = Dialog(this, R.style.GlassDialog)
        dialog.setContentView(R.layout.dialog_audio_delay)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtDelayValue = dialog.findViewById<TextView>(R.id.txt_delay_value)
        val seekBar = dialog.findViewById<SeekBar>(R.id.seek_bar_delay)

        var currentDelay = cm.getAudioDelayMs()
        txtDelayValue.text = "${currentDelay}ms"
        seekBar.max = 1000  // -500 to +500
        seekBar.progress = currentDelay + 500

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val delay = progress - 500
                txtDelayValue.text = "${delay}ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.findViewById<Button>(R.id.btn_apply_delay).setOnClickListener {
            val delay = seekBar.progress - 500
            cm.setAudioDelayMs(delay)
            Toast.makeText(this, "Audio delay set to ${delay}ms", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            refreshActivity()
        }

        dialog.show()
    }

    private fun showUserAgentDialog(cm: CacheManager) {
        val dialog = Dialog(this, R.style.GlassDialog)
        dialog.setContentView(R.layout.dialog_user_agent)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val editText = dialog.findViewById<android.widget.EditText>(R.id.edit_user_agent)
        editText.setText(cm.getUserAgent())

        dialog.findViewById<Button>(R.id.btn_apply_ua).setOnClickListener {
            val ua = editText.text.toString().trim()
            if (ua.isNotEmpty()) {
                cm.setUserAgent(ua)
                Toast.makeText(this, "User-Agent updated", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
            refreshActivity()
        }

        dialog.show()
    }

    private fun refreshActivity() {
        recreate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
