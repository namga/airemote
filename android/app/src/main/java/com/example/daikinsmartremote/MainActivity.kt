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
            val manager = ir ?: return "Không truy cập được dịch vụ Consumer IR của Android; chưa gửi tín hiệu."
            if (!manager.hasIrEmitter()) return "KẾT QUẢ: Android không phát hiện bộ phát IR. Chưa gửi tín hiệu."
            val ranges = try { manager.carrierFrequencies?.joinToString { "${it.minFrequency}-${it.maxFrequency} Hz" } ?: "không rõ dải tần" }
                catch (_: Exception) { "không đọc được dải tần" }
            return "KIỂM TRA PHẦN CỨNG: Có bộ phát IR ($ranges). Đây chưa phải phép thử phát tín hiệu và chưa xác nhận máy lạnh nhận lệnh."
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
