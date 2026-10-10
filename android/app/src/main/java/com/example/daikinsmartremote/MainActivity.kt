package com.example.daikinsmartremote

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.hardware.ConsumerIrManager
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Typeface

class MainActivity : Activity() {
    private var power = false
    private var temperature = 24
    private var mode = "COOL"
    private var fan = "AUTO"
    private var brand = "Daikin"
    private var ir: ConsumerIrManager? = null
    private lateinit var status: TextView
    private lateinit var temp: TextView
    private val blue = Color.rgb(31, 111, 139)
    private val pale = Color.rgb(235, 247, 250)
    private val ink = Color.rgb(30, 55, 66)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        render()
    }

    private fun label(text: String, size: Float = 16f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(ink); gravity = Gravity.CENTER
    }
    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text; textSize = 15f; setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(blue)
        setOnClickListener { action() }
    }
    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22, 24, 22, 22); setBackgroundColor(pale)
        }
        root.addView(label("Universal AC Remote", 25f))
        root.addView(label("Điều khiển đa hãng qua hồng ngoại", 14f))
        root.addView(label("Hãng máy lạnh", 15f))
        val brands = listOf("Daikin", "Panasonic", "LG", "Samsung", "Mitsubishi Electric", "Toshiba", "Sharp", "Gree", "Midea", "AUX", "Khác")
        val brandSpinner = Spinner(this)
        brandSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, brands)
        brandSpinner.setSelection(brands.indexOf(brand).coerceAtLeast(0))
        brandSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { brand = brands[position] }
        }
        root.addView(brandSpinner, LinearLayout.LayoutParams(-1, 52))
        status = label(if (power) "ĐANG BẬT • $mode" else "ĐANG TẮT", 15f)
        root.addView(status, LinearLayout.LayoutParams(-1, 54))
        val powerButton = button(if (power) "TẮT MÁY LẠNH" else "BẬT MÁY LẠNH") {
            power = !power; send()
        }
        root.addView(powerButton, LinearLayout.LayoutParams(-1, 58))
        val tempRow = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        tempRow.addView(button("−") { temperature = (temperature - 1).coerceAtLeast(16); refreshTemp() })
        temp = label("$temperature°C", 42f).apply { typeface = Typeface.DEFAULT_BOLD }
        tempRow.addView(temp, LinearLayout.LayoutParams(150, 90))
        tempRow.addView(button("+") { temperature = (temperature + 1).coerceAtMost(30); refreshTemp() })
        root.addView(tempRow)
        root.addView(label("Chế độ", 15f))
        val modes = Spinner(this)
        modes.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("COOL", "DRY", "FAN", "HEAT"))
        modes.setSelection(listOf("COOL","DRY","FAN","HEAT").indexOf(mode))
        modes.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                mode = listOf("COOL","DRY","FAN","HEAT")[position]
            }
        }
        root.addView(modes, LinearLayout.LayoutParams(-1, 52))
        root.addView(label("Tốc độ quạt", 15f))
        val fans = Spinner(this)
        fans.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("AUTO", "LOW", "MED", "HIGH"))
        fans.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                fan = listOf("AUTO","LOW","MED","HIGH")[position]
            }
        }
        root.addView(fans, LinearLayout.LayoutParams(-1, 52))
        root.addView(button("GỬI CÀI ĐẶT") { send() }, LinearLayout.LayoutParams(-1, 58))
        root.addView(button("KIỂM TRA BỘ PHÁT IR") { checkIrHardware() }, LinearLayout.LayoutParams(-1, 52))
        root.addView(label("Xiaomi 17 có bộ phát hồng ngoại theo thông số Xiaomi. Khả năng Android truy cập IR vẫn cần kiểm tra trên máy.", 13f))
        root.addView(label("Sharp: cần model dàn lạnh/remote để chọn đúng biến thể. Ứng dụng sẽ không gửi mã Daikin thay thế.", 12f))
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun refreshTemp() {
        temp.text = "$temperature°C"
        status.text = if (power) "ĐANG BẬT • $mode" else "ĐANG TẮT"
    }
    private fun checkIrHardware() {
        val manager = ir
        if (manager == null) {
            Toast.makeText(this, "Android không cung cấp ConsumerIrManager trên thiết bị này.", Toast.LENGTH_LONG).show()
            return
        }
        if (!manager.hasIrEmitter()) {
            Toast.makeText(this, "Không phát hiện IR emitter qua Android API. Kiểm tra Mi Remote và phiên bản hệ thống.", Toast.LENGTH_LONG).show()
            return
        }
        val ranges = try {
            manager.carrierFrequencies?.joinToString { "${it.minFrequency}-${it.maxFrequency} Hz" } ?: "Không đọc được dải tần"
        } catch (_: Exception) { "Không đọc được dải tần" }
        Toast.makeText(this, "Android phát hiện IR emitter. Dải tần: $ranges. Đây chưa phải kiểm tra máy lạnh nhận lệnh.", Toast.LENGTH_LONG).show()
    }

    private fun send() {
        if (ir == null || ir?.hasIrEmitter() != true) {
            Toast.makeText(this, "Không phát hiện IR emitter qua Android API. Hãy chạy Kiểm tra bộ phát IR.", Toast.LENGTH_LONG).show()
            return
        }
        // Never send the Daikin test waveform to a different brand. Sharp AC has
        // multiple model/protocol variants; the exact indoor-unit/remote model is needed.
        if (brand != "Daikin") {
            val message = if (brand == "Sharp") {
                "Chưa phát lệnh Sharp: cần model dàn lạnh hoặc mã remote để chọn đúng giao thức. Không gửi mã thay thế."
            } else {
                "Chưa có encoder đã xác minh cho $brand. Không phát mã Daikin thay thế."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            status.text = "CHƯA GỬI • CẦN XÁC MINH MODEL"
            return
        }
        try {
            // Daikin encoder remains explicitly experimental; do not treat Toast as
            // confirmation that the air conditioner received the command.
            val state = DaikinIr.buildState(power, temperature, mode, fan)
            ir?.transmit(DaikinIr.frequency(), DaikinIr.waveform(state))
            status.text = if (power) "ĐÃ PHÁT TÍN HIỆU • CHƯA XÁC NHẬN" else "ĐÃ PHÁT TÍN HIỆU • CHƯA XÁC NHẬN"
            Toast.makeText(this, "Đã yêu cầu phát IR; chưa xác nhận máy lạnh nhận lệnh.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Không phát được IR: ${e.message}", Toast.LENGTH_LONG).show()
        }
        render()
    }
}
