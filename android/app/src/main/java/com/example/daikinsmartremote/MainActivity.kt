package com.example.daikinsmartremote

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.hardware.ConsumerIrManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject

/** Android uses the exact same bundled HTML/CSS/JS as the web/PWA build. */
class MainActivity : Activity() {
    private var ir: ConsumerIrManager? = null
    private lateinit var webView: WebView
    private val prefs by lazy { getSharedPreferences("ac_remote", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(234, 247, 250)
        window.navigationBarColor = android.graphics.Color.rgb(234, 247, 250)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(234, 247, 250))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Keep the web design's own status and color scheme; only change the IR hint for Android.
                    evaluateJavascript("(function(){var h=document.getElementById('hint');if(h)h.textContent='Ứng dụng Android · IR được kiểm tra khi gửi lệnh';})();", null)
                }
            }
            addJavascriptInterface(AndroidRemoteBridge(), "AndroidACRemote")
        }
        setContentView(webView)
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    inner class AndroidRemoteBridge {
        // Common carrier-frequency references seen in consumer IR protocols.
        // These are protocol references, NOT a claim that this phone supports every value.
        private val commonFrequencyReferences = listOf(
            "36 kHz — Philips RC-5/RC-6 (protocol reference)",
            "38 kHz — NEC/Samsung and many consumer remotes (common reference)",
            "40 kHz — Sony SIRC and some other remotes (protocol reference)"
        )

        @JavascriptInterface
        fun sendState(json: String): String {
            return try {
                val data = JSONObject(json)
                val power = data.optBoolean("power", false)
                val temperature = data.optInt("temp", 24).coerceIn(16, 30)
                val mode = data.optString("mode", "COOL")
                val fan = data.optString("fan", "AUTO")
                val brand = data.optString("brand", "Daikin")
                prefs.edit().putBoolean("power", power).putInt("temperature", temperature)
                    .putString("mode", mode).putString("fan", fan).putString("brand", brand).apply()
                transmit(brand, power, temperature, mode, fan)
            } catch (e: Exception) {
                "Không thể xử lý trạng thái điều khiển: ${e.message ?: "lỗi không xác định"}"
            }
        }

        @JavascriptInterface
        fun checkIr(): String {
            val manager = ir ?: return "KHÔNG KIỂM TRA ĐƯỢC: Android không cung cấp dịch vụ Consumer IR cho ứng dụng. Chưa gửi tín hiệu."
            return try {
                if (!manager.hasIrEmitter()) {
                    "KẾT QUẢ: Android báo không có bộ phát IR. Chưa gửi tín hiệu. Hãy kiểm tra đúng thiết bị/ROM; ứng dụng không thể tự bật phần cứng IR."
                } else {
                    // null means the IR service query failed; an empty array means no ranges were reported.
                    val ranges = manager.carrierFrequencies
                    when {
                        ranges == null -> "PHÁT HIỆN BỘ PHÁT IR, NHƯNG KHÔNG ĐỌC ĐƯỢC DẢI TẦN: dịch vụ Consumer IR trả về null. Tần số tham khảo của giao thức remote phổ biến (không phải dải tần đã xác nhận của điện thoại): ${commonFrequencyReferences.joinToString("; ")}. Chưa gửi tín hiệu."
                        ranges.isEmpty() -> "PHÁT HIỆN BỘ PHÁT IR, NHƯNG ANDROID KHÔNG CUNG CẤP DẢI TẦN NÀO. Tần số tham khảo của giao thức remote phổ biến (chưa xác nhận điện thoại hỗ trợ): ${commonFrequencyReferences.joinToString("; ")}. Chưa gửi tín hiệu."
                        else -> {
                            val readable = ranges.joinToString(separator = "; ") {
                                "${it.minFrequency / 1000.0}–${it.maxFrequency / 1000.0} kHz"
                            }
                            val candidateFrequencies = listOf(36000, 38000, 40000).filter { hz ->
                                ranges.any { hz in it.minFrequency..it.maxFrequency }
                            }.joinToString(", ") { "${it / 1000} kHz" }.ifBlank { "không có tần số tham khảo phổ biến nào nằm trong dải đã báo" }
                            "PHÁT HIỆN BỘ PHÁT IR. Dải tần Android báo hỗ trợ: $readable. Tần số tham khảo phổ biến nằm trong dải này: $candidateFrequencies. Tham khảo giao thức: ${commonFrequencyReferences.joinToString("; ")}. Đây không phải xác nhận giao thức của máy lạnh; chưa gửi tín hiệu và chưa xác nhận máy lạnh nhận lệnh."
                        }
                    }
                }
            } catch (e: Exception) {
                "LỖI KIỂM TRA IR: ${e.message ?: e.javaClass.simpleName}. Chưa gửi tín hiệu."
            }
        }
    }

    private fun transmit(brand: String, power: Boolean, temperature: Int, mode: String, fan: String): String {
        val manager = ir ?: return "Không phát hiện bộ phát IR qua Android API."
        if (!manager.hasIrEmitter()) return "KẾT QUẢ: Android không phát hiện bộ phát IR. Chưa gửi lệnh."
        if (brand != "Daikin") return "Chưa có bộ mã điều khiển đã xác minh cho $brand. Không phát mã thử ngẫu nhiên; chưa gửi lệnh máy lạnh."
        return try {
            val state = DaikinIr.buildState(power, temperature, mode, fan)
            manager.transmit(DaikinIr.frequency(), DaikinIr.waveform(state))
            "ANDROID API ĐÃ THỰC HIỆN LỆNH PHÁT theo encoder Daikin hiện có. Chưa xác nhận máy lạnh nhận lệnh; nếu không phản hồi, encoder/model có thể không tương thích."
        } catch (e: Exception) {
            "Không phát được IR: ${e.message ?: "lỗi không xác định"}"
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
