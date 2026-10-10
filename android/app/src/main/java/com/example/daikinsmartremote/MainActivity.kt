package com.example.daikinsmartremote

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.hardware.ConsumerIrManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.content.res.ColorStateList

class MainActivity : Activity() {
    private var power = false
    private var temperature = 24
    private var mode = "COOL"
    private var fan = "AUTO"
    private var brand = "Sharp"
    private var ir: ConsumerIrManager? = null
    private lateinit var status: TextView
    private lateinit var temp: TextView
    private lateinit var powerButton: Button
    private lateinit var brandSpinner: Spinner
    private lateinit var modeSpinner: Spinner
    private lateinit var fanSpinner: Spinner
    private val prefs by lazy { getSharedPreferences("ac_remote", Context.MODE_PRIVATE) }
    private val blue = Color.rgb(37, 115, 137)
    private val pale = Color.rgb(239, 248, 250)
    private val ink = Color.rgb(35, 58, 68)
    private val muted = Color.rgb(112, 137, 145)
    private val pink = Color.rgb(192, 86, 119)
    private val white = Color.WHITE

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 22, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun label(text: String, size: Float = 16f, color: Int = ink, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = true
        }
    private fun button(text: String, bg: Int = blue, fg: Int = white, action: () -> Unit): Button =
        Button(this).apply {
            this.text = text; textSize = 15f; setTextColor(fg); isAllCaps = false
            background = rounded(bg, 18)
            backgroundTintList = null
            setPadding(dp(12), dp(8), dp(12), dp(8))
            stateListAnimator = null
            setOnClickListener { action() }
        }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(white, 26)
        elevation = dp(2).toFloat()
    }
    private fun params(width: Int = -1, height: Int = -2, top: Int = 0) =
        LinearLayout.LayoutParams(width, height).apply { topMargin = dp(top) }
    private fun addGap(parent: LinearLayout, height: Int) { parent.addView(View(this), params(1, height)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pale
        window.navigationBarColor = Color.rgb(250, 251, 252)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        temperature = prefs.getInt("temperature", 24)
        power = prefs.getBoolean("power", false)
        mode = prefs.getString("mode", "COOL") ?: "COOL"
        fan = prefs.getString("fan", "AUTO") ?: "AUTO"
        brand = prefs.getString("brand", "Sharp") ?: "Sharp"
        render()
    }

    private fun save() {
        prefs.edit().putInt("temperature", temperature).putBoolean("power", power)
            .putString("mode", mode).putString("fan", fan).putString("brand", brand).apply()
    }

    private fun render() {
        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(pale)
        }
        scrollContent.addView(label("❄  AC Remote", 27f, ink, true), params(top = 4))
        scrollContent.addView(label("Điều khiển máy lạnh qua hồng ngoại", 14f, muted), params(top = 2))
        addGap(scrollContent, 18)

        val room = card()
        room.addView(label("ĐIỀU KHIỂN PHÒNG", 12f, muted, true))
        addGap(room, 12)
        room.addView(label("Hãng máy lạnh", 14f, muted), params(top = 2))
        brandSpinner = spinner(listOf("Sharp", "Daikin", "Panasonic", "LG", "Samsung", "Mitsubishi Electric", "Toshiba", "Gree", "Midea", "AUX", "Khác"), brand)
        room.addView(brandSpinner, params(top = 6))
        brandSpinner.onItemSelectedListener = selectionListener { brand = it; save() }
        status = label(if (power) "● ĐANG BẬT · $mode" else "○ ĐANG TẮT", 16f, if (power) blue else muted, true)
        room.addView(status, params(top = 12))
        addGap(room, 4)

        val tempRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; }
        tempRow.addView(button("−", pale, blue) { temperature = (temperature - 1).coerceAtLeast(16); save(); updateDisplay() }, LinearLayout.LayoutParams(dp(64), dp(56)))
        temp = label("$temperature°", 54f, ink, false)
        tempRow.addView(temp, LinearLayout.LayoutParams(0, dp(86), 1f))
        tempRow.addView(button("+", pale, blue) { temperature = (temperature + 1).coerceAtMost(30); save(); updateDisplay() }, LinearLayout.LayoutParams(dp(64), dp(56)))
        room.addView(tempRow, params(top = 8))
        room.addView(label("16°C                                      30°C", 12f, muted), params(top = 0))
        val seek = SeekBar(this).apply {
            max = 14; progress = temperature - 16
            progressTintList = ColorStateList.valueOf(blue)
            thumbTintList = ColorStateList.valueOf(blue)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { if (user) { temperature = p + 16; updateDisplay(); save() } }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        room.addView(seek, params(top = 0))
        powerButton = button(if (power) "⏻   Tắt máy lạnh" else "⏻   Bật máy lạnh", pink) { power = !power; save(); updateDisplay(); send() }
        room.addView(powerButton, params(height = dp(58), top = 12))
        scrollContent.addView(room, params(top = 0))

        addGap(scrollContent, 16)
        val settings = card()
        settings.addView(label("Chế độ hoạt động", 18f, ink, true), params())
        modeSpinner = spinner(listOf("COOL", "DRY", "FAN", "HEAT"), mode)
        settings.addView(modeSpinner, params(top = 8))
        modeSpinner.onItemSelectedListener = selectionListener { mode = it; save(); updateDisplay() }
        settings.addView(label("Tốc độ quạt", 18f, ink, true), params(top = 16))
        fanSpinner = spinner(listOf("AUTO", "LOW", "MED", "HIGH"), fan)
        settings.addView(fanSpinner, params(top = 8))
        fanSpinner.onItemSelectedListener = selectionListener { fan = it; save() }
        val irButton = button("Kiểm tra bộ phát hồng ngoại", pale, blue) { checkIrHardware() }
        settings.addView(irButton, params(height = dp(52), top = 18))
        scrollContent.addView(settings)

        addGap(scrollContent, 14)
        val note = card()
        note.addView(label("Lưu ý về hồng ngoại", 15f, ink, true))
        note.addView(label("Chọn đúng hãng và model trước khi gửi lệnh. Sharp có nhiều giao thức; ứng dụng sẽ không gửi mã Daikin thay thế. Kết quả kiểm tra phần cứng không xác nhận máy lạnh đã nhận lệnh.", 13f, muted), params(top = 8))
        scrollContent.addView(note)
        scrollContent.addView(label("Universal AC Remote · Cài đặt được lưu tự động", 12f, muted), params(top = 18))
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(scrollContent)
            setBackgroundColor(pale)
        })
        updateDisplay()
    }

    private fun spinner(items: List<String>, selected: String): Spinner {
        val s = Spinner(this)
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        s.setSelection(items.indexOf(selected).coerceAtLeast(0))
        s.background = rounded(Color.rgb(247, 251, 252), 16, Color.rgb(220, 232, 235))
        return s
    }
    private fun selectionListener(change: (String) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            (parent?.adapter?.getItem(position) as? String)?.let(change)
        }
    }
    private fun updateDisplay() {
        if (!::temp.isInitialized) return
        temp.text = "$temperature°"
        status.text = if (power) "● ĐANG BẬT · $mode" else "○ ĐANG TẮT"
        status.setTextColor(if (power) blue else muted)
        powerButton.text = if (power) "⏻   Tắt máy lạnh" else "⏻   Bật máy lạnh"
    }
    private fun checkIrHardware() {
        val manager = ir
        if (manager == null) {
            Toast.makeText(this, "Android không cung cấp ConsumerIrManager trên thiết bị này.", Toast.LENGTH_LONG).show(); return
        }
        if (!manager.hasIrEmitter()) {
            Toast.makeText(this, "Android chưa nhận diện bộ phát IR. Hãy kiểm tra hệ thống và ứng dụng Mi Remote.", Toast.LENGTH_LONG).show(); return
        }
        val ranges = try { manager.carrierFrequencies?.joinToString { "${it.minFrequency}-${it.maxFrequency} Hz" } ?: "Không đọc được dải tần" }
            catch (_: Exception) { "Không đọc được dải tần" }
        Toast.makeText(this, "Đã phát hiện IR emitter. Dải tần: $ranges. Chưa xác nhận máy lạnh nhận lệnh.", Toast.LENGTH_LONG).show()
    }
    private fun send() {
        val manager = ir
        if (manager == null || !manager.hasIrEmitter()) {
            Toast.makeText(this, "Không phát hiện bộ phát IR qua Android API.", Toast.LENGTH_LONG).show(); return
        }
        if (brand != "Daikin") {
            Toast.makeText(this, if (brand == "Sharp") "Chưa gửi: cần model Sharp để chọn đúng giao thức." else "Chưa có encoder IR đã xác minh cho $brand.", Toast.LENGTH_LONG).show()
            status.text = "CHƯA GỬI · CẦN XÁC MINH MODEL"
            return
        }
        try {
            val state = DaikinIr.buildState(power, temperature, mode, fan)
            manager.transmit(DaikinIr.frequency(), DaikinIr.waveform(state))
            Toast.makeText(this, "Đã yêu cầu phát IR; chưa xác nhận máy lạnh nhận lệnh.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Không phát được IR: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
