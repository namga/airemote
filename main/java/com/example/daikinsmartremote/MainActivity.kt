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
import android.widget.*

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
    private val blue = Color.rgb(37, 115, 137)
    private val pale = Color.rgb(239, 248, 250)
    private val ink = Color.rgb(35, 58, 68)
    private val muted = Color.rgb(112, 137, 145)
    private val pink = Color.rgb(192, 86, 119)
    private val white = Color.WHITE
    private val prefs by lazy { getSharedPreferences("ac_remote", Context.MODE_PRIVATE) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 20, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun lp(w: Int = -1, h: Int = -2, top: Int = 0) = LinearLayout.LayoutParams(w, h).apply { topMargin = dp(top) }
    private fun label(text: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun btn(text: String, color: Int = blue, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 15f; isAllCaps = false; setTextColor(white); background = bg(color, 18); backgroundTintList = null
        stateListAnimator = null; setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { action() }
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); background = bg(white, 26); elevation = dp(2).toFloat()
    }
    private fun gap(parent: LinearLayout, height: Int) { parent.addView(View(this), lp(1, height)) }
    private fun save() { prefs.edit().putInt("temperature", temperature).putBoolean("power", power).putString("mode", mode).putString("fan", fan).putString("brand", brand).apply() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pale; window.navigationBarColor = Color.rgb(250, 251, 252)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        temperature = prefs.getInt("temperature", 24); power = prefs.getBoolean("power", false)
        mode = prefs.getString("mode", "COOL") ?: "COOL"; fan = prefs.getString("fan", "AUTO") ?: "AUTO"
        brand = prefs.getString("brand", "Sharp") ?: "Sharp"
        render()
    }
    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(24)); setBackgroundColor(pale) }
        root.addView(label("❄  AC Remote", 27f, ink, true), lp(top = 4))
        root.addView(label("Điều khiển máy lạnh qua hồng ngoại", 14f, muted), lp(top = 3)); gap(root, 18)
        val room = card(); room.addView(label("ĐIỀU KHIỂN PHÒNG", 12f, muted, true)); gap(room, 10)
        room.addView(label("Hãng máy lạnh", 14f, muted))
        val brands = listOf("Sharp", "Daikin", "Panasonic", "LG", "Samsung", "Mitsubishi Electric", "Toshiba", "Gree", "Midea", "AUX", "Khác")
        val brandSpinner = spinner(brands, brand); room.addView(brandSpinner, lp(top = 5))
        brandSpinner.onItemSelectedListener = listener { brand = it; save() }
        status = label(if (power) "● ĐANG BẬT · $mode" else "○ ĐANG TẮT", 16f, if (power) blue else muted, true); room.addView(status, lp(top = 12))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        row.addView(btn("−", pale) { temperature = (temperature - 1).coerceAtLeast(16); save(); updateDisplay() }, LinearLayout.LayoutParams(dp(64), dp(56)))
        temp = label("$temperature°", 54f); row.addView(temp, LinearLayout.LayoutParams(0, dp(86), 1f))
        row.addView(btn("+", pale) { temperature = (temperature + 1).coerceAtMost(30); save(); updateDisplay() }, LinearLayout.LayoutParams(dp(64), dp(56)))
        room.addView(row, lp(top = 8)); room.addView(label("16°C                              30°C", 12f, muted))
        val seek = SeekBar(this).apply { max = 14; progress = temperature - 16; progressTintList = android.content.res.ColorStateList.valueOf(blue); thumbTintList = android.content.res.ColorStateList.valueOf(blue)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { if (user) { temperature = p + 16; updateDisplay(); save() } }; override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {} }) }
        room.addView(seek); powerButton = btn(if (power) "⏻   Tắt máy lạnh" else "⏻   Bật máy lạnh", pink) { power = !power; save(); updateDisplay(); send() }
        room.addView(powerButton, lp(h = dp(58), top = 12)); root.addView(room)
        gap(root, 16); val settings = card(); settings.addView(label("Chế độ hoạt động", 18f, ink, true))
        val modes = spinner(listOf("COOL", "DRY", "FAN", "HEAT"), mode); settings.addView(modes, lp(top = 8)); modes.onItemSelectedListener = listener { mode = it; save(); updateDisplay() }
        settings.addView(label("Tốc độ quạt", 18f, ink, true), lp(top = 16))
        val fans = spinner(listOf("AUTO", "LOW", "MED", "HIGH"), fan); settings.addView(fans, lp(top = 8)); fans.onItemSelectedListener = listener { fan = it; save() }
        settings.addView(btn("Kiểm tra bộ phát hồng ngoại", blue) { checkIr() }, lp(h = dp(52), top = 18)); root.addView(settings)
        gap(root, 14); val note = card(); note.addView(label("Lưu ý về hồng ngoại", 15f, ink, true))
        note.addView(label("Sharp có nhiều giao thức. Ứng dụng không gửi mã Daikin thay thế khi chọn Sharp. Kiểm tra phần cứng không xác nhận máy lạnh đã nhận lệnh.", 13f, muted), lp(top = 8)); root.addView(note)
        root.addView(label("Thiết lập được lưu tự động trên thiết bị", 12f, muted), lp(top = 18))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root); setBackgroundColor(pale) }); updateDisplay()
    }
    private fun spinner(items: List<String>, selected: String) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items); setSelection(items.indexOf(selected).coerceAtLeast(0)); background = bg(Color.rgb(247, 251, 252), 16, Color.rgb(220, 232, 235))
    }
    private fun listener(change: (String) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { (parent?.adapter?.getItem(position) as? String)?.let(change) }
    }
    private fun updateDisplay() { if (!::temp.isInitialized) return; temp.text = "$temperature°"; status.text = if (power) "● ĐANG BẬT · $mode" else "○ ĐANG TẮT"; status.setTextColor(if (power) blue else muted); powerButton.text = if (power) "⏻   Tắt máy lạnh" else "⏻   Bật máy lạnh" }
    private fun checkIr() {
        val m = ir
        if (m == null) { Toast.makeText(this, "Android không cung cấp ConsumerIrManager.", Toast.LENGTH_LONG).show(); return }
        if (!m.hasIrEmitter()) { Toast.makeText(this, "Android chưa nhận diện bộ phát IR. Kiểm tra Mi Remote và hệ thống.", Toast.LENGTH_LONG).show(); return }
        val ranges = try { m.carrierFrequencies?.joinToString { "${it.minFrequency}-${it.maxFrequency} Hz" } ?: "Không đọc được dải tần" } catch (_: Exception) { "Không đọc được dải tần" }
        Toast.makeText(this, "Phát hiện IR emitter: $ranges. Chưa xác nhận máy lạnh nhận lệnh.", Toast.LENGTH_LONG).show()
    }
    private fun send() {
        val m = ir
        if (m == null || !m.hasIrEmitter()) { Toast.makeText(this, "Không phát hiện bộ phát IR qua Android API.", Toast.LENGTH_LONG).show(); return }
        if (brand != "Daikin") { Toast.makeText(this, if (brand == "Sharp") "Chưa gửi: cần model Sharp để chọn đúng giao thức." else "Chưa có encoder đã xác minh cho $brand.", Toast.LENGTH_LONG).show(); status.text = "CHƯA GỬI · CẦN XÁC MINH MODEL"; return }
        try { val state = DaikinIr.buildState(power, temperature, mode, fan); m.transmit(DaikinIr.frequency(), DaikinIr.waveform(state)); Toast.makeText(this, "Đã yêu cầu phát IR; chưa xác nhận máy lạnh nhận lệnh.", Toast.LENGTH_LONG).show() }
        catch (e: Exception) { Toast.makeText(this, "Không phát được IR: ${e.message}", Toast.LENGTH_LONG).show() }
    }
}
