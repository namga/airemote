package com.example.daikinsmartremote

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import android.hardware.ConsumerIrManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject

/** Android uses the exact same bundled HTML/CSS/JS as the web/PWA build. */
class MainActivity : Activity() {
    private data class BrandIrReference(val carrier: String, val protocols: String, val note: String)

    private var ir: ConsumerIrManager? = null
    private lateinit var webView: WebView
    private val prefs by lazy { getSharedPreferences("ac_remote", Context.MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val discoveredBle = linkedMapOf<String, BluetoothDevice>()
    private var bleAdapter: BluetoothAdapter? = null
    private var bleScanCallback: ScanCallback? = null
    private var bleGatt: BluetoothGatt? = null
    private var bleStatus = "Chưa quét thiết bị BLE."
    private var bleServicesJson = "[]"
    private var latestBleCapture: JSONObject? = null
    private var notifyingCharacteristic: BluetoothGattCharacteristic? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(234, 247, 250)
        window.navigationBarColor = android.graphics.Color.rgb(234, 247, 250)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bleAdapter = bluetoothManager?.adapter

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7801) {
            bleStatus = if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED })
                "Đã cấp quyền Bluetooth. Nhấn Quét BLE lần nữa." else "Chưa cấp đủ quyền Bluetooth; không thể quét/kết nối."
        }
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBlePermissions() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        runOnUiThread { requestPermissions(needed, 7801) }
    }

    inner class AndroidRemoteBridge {
        // Brand-specific reference notes. These are nominal protocol references, NOT
        // measured device capabilities and NOT proof that a particular AC model uses them.
        private val brandIrReferences = mapOf(
            "Sharp" to BrandIrReference("38 kHz (tham khảo phổ biến)", "SHARP / SHARP_AC", "Giao thức và biến thể phụ thuộc remote/model."),
            "Mitsubishi Electric" to BrandIrReference("38 kHz (tham khảo phổ biến)", "MITSUBISHI_AC / MITSUBISHI", "Có nhiều họ giao thức; không suy ra model chỉ từ hãng."),
            "Casper" to BrandIrReference("Chưa xác minh riêng cho Casper; 38 kHz chỉ là mốc tham khảo chung", "Chưa xác minh giao thức Casper riêng", "Có thể dùng nền tảng/OEM khác nhau tùy model."),
            "Midea" to BrandIrReference("38 kHz (tham khảo phổ biến)", "MIDEA / MIDEA24 / một số thiết bị tương thích COOLIX", "Không phải mọi model Midea đều dùng cùng biến thể."),
            "Gree" to BrandIrReference("38 kHz (profile thử nghiệm)", "Gree/YAC-family (biến thể chưa xác minh)", "Ứng dụng IR Remote for All của bạn điều khiển Reetech khi chọn Gree. Encoder trong app này là profile thử nghiệm, chưa phải bản ghi tín hiệu thực tế."),
            "Reetech" to BrandIrReference("Chưa xác minh cho RT9-BM5; 38 kHz chỉ là mốc tham khảo chung", "Chưa xác minh giao thức Reetech RT9-BM5 / RC9-BM5", "Mi Remote trên Xiaomi 17 điều khiển model này khi chọn Midea; vì vậy Reetech được định tuyến thử qua encoder Midea. Đây là giả thuyết tương thích dựa trên quan sát người dùng, chưa xác minh dạng sóng."),
            "Daikin" to BrandIrReference("38 kHz (tham khảo phổ biến)", "DAIKIN và nhiều biến thể DAIKINxx", "Daikin có nhiều định dạng lệnh và độ dài gói khác nhau."),
            "LG" to BrandIrReference("38 kHz (tham khảo phổ biến)", "LG / LG2", "Biến thể giao thức phụ thuộc dòng remote/model.")
        )
        private val commonFrequencyReferences = listOf(
            "36 kHz — tham khảo giao thức Philips RC-5/RC-6",
            "38 kHz — mốc phổ biến ở nhiều remote dân dụng, bao gồm một số giao thức điều hòa",
            "40 kHz — tham khảo giao thức Sony SIRC và một số thiết bị khác"
        )

        private fun brandReferenceText(brand: String): String {
            val ref = brandIrReferences[brand]
                ?: return "$brand: chưa có tham chiếu tần số/giao thức riêng đã xác minh; 36/38/40 kHz chỉ là các mốc tham khảo chung."
            return "$brand — tần số: ${ref.carrier}; họ giao thức tham khảo: ${ref.protocols}. ${ref.note} Đây không phải dữ liệu đo từ điện thoại hoặc xác nhận tương thích model."
        }

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

        /** Short harmless carrier burst for a quick emitter-path check; not an AC command. */
        @JavascriptInterface
        fun testIrPulse(): String {
            val manager = ir ?: return "TEST IR: Android không cung cấp dịch vụ Consumer IR. Chưa phát."
            return try {
                if (!manager.hasIrEmitter()) return "TEST IR: Android không phát hiện IR emitter. Chưa phát."
                val ranges = manager.carrierFrequencies
                val frequencySupported = ranges?.any { 38000 in it.minFrequency..it.maxFrequency }
                // Short generic pulse train. This only checks the Android transmit path; it is not a valid Midea command.
                val testPattern = intArrayOf(9000, 4500, 560, 560, 560, 1690, 560, 560, 560, 1690, 560, 560, 560, 1690, 560, 560)
                manager.transmit(38000, testPattern)
                val carrierNote = when (frequencySupported) {
                    true -> "Android báo dải tần có 38 kHz."
                    false -> "Android không báo dải 38 kHz; burst vẫn được gửi để kiểm tra API."
                    null -> "Android không trả được dải tần; burst vẫn được gửi để kiểm tra API."
                }
                "TEST IR: API transmit() đã hoàn tất burst 38 kHz ngắn. $carrierNote Đây KHÔNG phải lệnh điều hòa. Hãy dùng camera điện thoại khác (ưu tiên camera trước) nhìn vào cửa sổ IR khi bấm test; nếu thấy chớp sáng chỉ xác nhận có phát quang IR, không xác nhận giao thức Reetech hay mã điều hòa đúng."
            } catch (e: Exception) {
                "TEST IR thất bại: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        @JavascriptInterface
        fun mideaProtocolSelfTest(): String {
            val vectorOk = MideaIr.checksumSelfTest()
            val layoutOk = MideaIr.stateLayoutSelfTest()
            val pattern = MideaIr.buildPattern(true, 27, "COOL", "AUTO")
            val duration = pattern.sumOf { it.toLong() }
            val safe = pattern.isNotEmpty() && pattern.all { it > 0 } && duration < 1_900_000L
            return "Midea offline self-test: checksum ${if (vectorOk) "PASS" else "FAIL"}; state layout ${if (layoutOk) "PASS" else "FAIL"}; waveform ${if (safe) "PASS" else "FAIL"} (${MideaIr.describePattern(pattern)}). PASS chỉ xác nhận kiểm tra phần mềm, không xác nhận máy lạnh nhận lệnh."
        }

        /** Phone-only IR capture is unsupported by the public Android ConsumerIrManager API. */
        @JavascriptInterface
        fun startIrCapture(): String = "KHÔNG THỂ BẮT TÍN HIỆU: Xiaomi 17/Android ConsumerIrManager chỉ cung cấp phát IR, không có API nhận tín hiệu từ remote gốc. Không có dữ liệu nào được thu. Bạn có thể dán/import dạng sóng thô đã có để phân tích và lưu."

        /** Transmit a manually imported raw pattern; never claims the AC received it. */
        @JavascriptInterface
        fun transmitLearned(json: String): String {
            return try {
                val data = JSONObject(json)
                val frequency = data.optInt("frequency", 0)
                val values = data.optJSONArray("pattern")
                    ?: return "Dữ liệu dạng sóng không hợp lệ. Chưa phát."
                if (frequency !in 20000..60000) return "Tần số phải trong khoảng 20–60 kHz. Chưa phát."
                if (values.length() < 6 || values.length() > 1000) return "Số khoảng thời gian không hợp lệ. Chưa phát."
                val pattern = IntArray(values.length()) { i -> values.optInt(i, -1) }
                if (pattern.any { it <= 0 || it > 1_000_000 }) return "Dạng sóng chứa thời gian không hợp lệ. Chưa phát."
                if (pattern.sumOf { it.toLong() } > 1_900_000L) return "Dạng sóng dài gần hoặc quá 2 giây; Android ConsumerIrManager không đảm bảo phát mẫu dài này. Chưa phát."
                val manager = ir ?: return "Không có dịch vụ Consumer IR. Chưa phát."
                if (!manager.hasIrEmitter()) return "Android không phát hiện bộ phát IR. Chưa phát."
                val ranges = manager.carrierFrequencies
                if (ranges == null || ranges.isEmpty()) return "Không xác minh được dải tần được hỗ trợ trên điện thoại. Để an toàn, chưa phát lệnh nhập thủ công."
                if (ranges.none { frequency in it.minFrequency..it.maxFrequency }) return "Tần số ${frequency} Hz nằm ngoài dải Android báo hỗ trợ. Chưa phát."
                manager.transmit(frequency, pattern)
                "Android API đã hoàn tất yêu cầu phát mẫu IR nhập thủ công ở ${frequency} Hz. Đây không xác nhận máy lạnh đã nhận lệnh."
            } catch (e: Exception) {
                "Không phát được lệnh IR đã học: ${e.message ?: e.javaClass.simpleName}."
            }
        }

        @JavascriptInterface
        fun scanBleDevices(): String {
            if (!hasBlePermissions()) { requestBlePermissions(); return "Cần cấp quyền Thiết bị ở gần/Bluetooth. Sau khi cấp quyền, nhấn Quét BLE lần nữa." }
            val adapter = bleAdapter ?: return "Điện thoại không có Bluetooth adapter."
            if (!adapter.isEnabled) return "Hãy bật Bluetooth trong cài đặt Android rồi quét lại."
            val scanner = adapter.bluetoothLeScanner ?: return "Không có BLE scanner trên thiết bị này."
            try {
                stopBleScan()
                discoveredBle.clear()
                bleStatus = "Đang quét BLE trong 12 giây…"
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val device = result.device
                        discoveredBle[device.address] = device
                    }
                    override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { discoveredBle[it.device.address] = it.device } }
                    override fun onScanFailed(errorCode: Int) { bleStatus = "Quét BLE thất bại, mã lỗi $errorCode."; bleScanCallback = null }
                }
                bleScanCallback = callback
                scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
                mainHandler.postDelayed({ stopBleScan(); if (discoveredBle.isEmpty()) bleStatus = "Không tìm thấy thiết bị BLE. Kiểm tra thiết bị học IR đã bật và ở chế độ ghép nối." else bleStatus = "Tìm thấy ${discoveredBle.size} thiết bị BLE. Chọn đúng bộ học IR." }, 12000)
                return "Đã bắt đầu quét BLE. Danh sách sẽ cập nhật trong khoảng 12 giây. Chỉ hỗ trợ bộ học IR có BLE/GATT; thiết bị Bluetooth Classic hoặc giao thức riêng có thể không tương thích."
            } catch (e: SecurityException) { return "Thiếu quyền quét Bluetooth: ${e.message ?: "hãy cấp quyền Thiết bị ở gần"}." }
            catch (e: Exception) { return "Không quét được BLE: ${e.message ?: e.javaClass.simpleName}." }
        }

        @JavascriptInterface
        fun getBleDevices(): String {
            val arr = JSONArray()
            try {
                for ((address, device) in discoveredBle) {
                    val item = JSONObject().put("address", address).put("name", try { device.name ?: "Thiết bị BLE không tên" } catch (_: Exception) { "Thiết bị BLE" })
                    arr.put(item)
                }
            } catch (_: Exception) { }
            return JSONObject().put("status", bleStatus).put("devices", arr).toString()
        }

        @SuppressLint("MissingPermission")
        @JavascriptInterface
        fun connectBle(address: String): String {
            if (!hasBlePermissions()) { requestBlePermissions(); return "Cần cấp quyền Bluetooth trước khi kết nối." }
            val device = discoveredBle[address] ?: return "Không tìm thấy thiết bị đã chọn; hãy quét lại."
            return try {
                bleGatt?.close(); bleGatt = null; bleServicesJson = "[]"; notifyingCharacteristic = null
                bleStatus = "Đang kết nối ${device.name ?: address}…"
                bleGatt = device.connectGatt(this@MainActivity, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) { bleStatus = "Kết nối BLE lỗi $status."; gatt.close(); return }
                        if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                            bleStatus = "Đã kết nối BLE; đang đọc dịch vụ…"
                            gatt.discoverServices()
                        } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) bleStatus = "Đã ngắt kết nối BLE."
                    }
                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status != BluetoothGatt.GATT_SUCCESS) { bleStatus = "Không đọc được dịch vụ GATT (mã $status)."; return }
                        val services = JSONArray()
                        for (service in gatt.services) {
                            val chars = JSONArray()
                            for (ch in service.characteristics) {
                                val props = mutableListOf<String>()
                                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) props.add("READ")
                                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props.add("NOTIFY")
                                if (ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props.add("INDICATE")
                                if (ch.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) props.add("WRITE")
                                chars.put(JSONObject().put("uuid", ch.uuid.toString()).put("properties", props.joinToString("/")))
                            }
                            services.put(JSONObject().put("uuid", service.uuid.toString()).put("characteristics", chars))
                        }
                        bleServicesJson = services.toString()
                        bleStatus = "Đã kết nối; tìm thấy ${gatt.services.size} dịch vụ GATT. Chọn characteristic NOTIFY/INDICATE để nhận dữ liệu."
                    }
                    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) recordBlePayload(characteristic.uuid.toString(), characteristic.value ?: byteArrayOf())
                    }
                    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                        recordBlePayload(characteristic.uuid.toString(), characteristic.value ?: byteArrayOf())
                    }
                    @Suppress("DEPRECATION")
                    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                        recordBlePayload(characteristic.uuid.toString(), value)
                    }
                    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                        bleStatus = if (status == BluetoothGatt.GATT_SUCCESS) "Đã bật nhận dữ liệu BLE. Nhấn nút remote gốc để gửi lệnh đến bộ học IR." else "Không bật được thông báo BLE (mã $status)."
                    }
                })
                "Đã yêu cầu kết nối đến ${device.name ?: address}. Hãy đợi dịch vụ GATT được đọc."
            } catch (e: SecurityException) { "Thiếu quyền Bluetooth khi kết nối: ${e.message ?: "quyền bị từ chối"}." }
            catch (e: Exception) { "Không kết nối BLE được: ${e.message ?: e.javaClass.simpleName}." }
        }

        @JavascriptInterface
        fun getBleServices(): String = JSONObject().put("status", bleStatus).put("services", JSONArray(bleServicesJson)).toString()

        @SuppressLint("MissingPermission")
        @JavascriptInterface
        fun subscribeBleCharacteristic(uuidText: String): String {
            if (!hasBlePermissions()) { requestBlePermissions(); return "Cần cấp quyền Bluetooth trước khi nhận dữ liệu." }
            val gatt = bleGatt ?: return "Chưa kết nối bộ học IR qua BLE."
            val uuid = try { java.util.UUID.fromString(uuidText.trim()) } catch (_: Exception) { return "UUID characteristic không hợp lệ." }
            val characteristic = gatt.services.flatMap { it.characteristics }.firstOrNull { it.uuid == uuid }
                ?: return "Không tìm thấy characteristic $uuidText trong dịch vụ đã đọc."
            val notify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            val indicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            return try {
                if (!notify && !indicate) {
                    if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) { gatt.readCharacteristic(characteristic); "Characteristic chỉ hỗ trợ READ; đã yêu cầu đọc một lần. Nếu dữ liệu không phải dạng sóng, cần đặc tả của thiết bị." }
                    else "Characteristic này không hỗ trợ READ/NOTIFY/INDICATE."
                } else {
                    if (!gatt.setCharacteristicNotification(characteristic, true)) return "Android không bật được thông báo cho characteristic này."
                    val descriptor = characteristic.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        ?: return "Characteristic có NOTIFY nhưng không có descriptor CCCD chuẩn; cần tài liệu của thiết bị."
                    descriptor.value = if (indicate && !notify) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    notifyingCharacteristic = characteristic
                    val started = if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(descriptor, descriptor.value) == BluetoothGatt.GATT_SUCCESS else { @Suppress("DEPRECATION") val ok = gatt.writeDescriptor(descriptor); ok }
                    if (started) "Đã yêu cầu bật nhận dữ liệu từ ${characteristic.uuid}. Nhấn remote gốc; định dạng payload phụ thuộc firmware bộ học IR." else "Không gửi được yêu cầu bật thông báo BLE."
                }
            } catch (e: Exception) { "Lỗi đăng ký nhận BLE: ${e.message ?: e.javaClass.simpleName}." }
        }

        @JavascriptInterface
        fun getLatestBleCapture(): String = latestBleCapture?.toString() ?: "{}"

        @JavascriptInterface
        fun importBleCaptureToRaw(): String {
            val capture = latestBleCapture ?: return "Chưa nhận được dữ liệu BLE nào."
            val pattern = capture.optJSONArray("pattern") ?: return "Đã nhận payload BLE (${capture.optString("hex")}) nhưng chưa giải mã được thành mảng thời gian mark/space. Cần tài liệu payload/firmware của bộ học IR; không tự đoán xung từ byte bất kỳ."
            val values = mutableListOf<Int>()
            for (i in 0 until pattern.length()) values.add(pattern.optInt(i, -1))
            if (values.size < 6 || values.any { it <= 0 || it > 1_000_000 }) return "Payload có trường pattern nhưng giá trị chưa hợp lệ."
            return JSONObject().put("pattern", JSONArray(values)).put("frequency", capture.optInt("frequency", 38000)).put("protocol", capture.optString("protocol", "Chưa nhận diện")).toString()
        }

        @JavascriptInterface
        fun disconnectBle(): String {
            try { bleGatt?.disconnect(); bleGatt?.close() } catch (_: Exception) { }
            bleGatt = null; bleStatus = "Đã ngắt kết nối BLE."; return bleStatus
        }

        @JavascriptInterface
        fun setBrand(brand: String) {
            prefs.edit().putString("brand", brand.take(64)).apply()
        }

        @JavascriptInterface
        fun checkIr(): String {
            val manager = ir ?: return "KHÔNG KIỂM TRA ĐƯỢC: Android không cung cấp dịch vụ Consumer IR cho ứng dụng. Chưa gửi tín hiệu."
            val selectedBrand = prefs.getString("brand", "Daikin") ?: "Daikin"
            val selectedBrandReference = brandReferenceText(selectedBrand)
            return try {
                if (!manager.hasIrEmitter()) {
                    "KẾT QUẢ: Android báo không có bộ phát IR. Chưa gửi tín hiệu. Hãy kiểm tra đúng thiết bị/ROM; ứng dụng không thể tự bật phần cứng IR."
                } else {
                    // null means the IR service query failed; an empty array means no ranges were reported.
                    val ranges = manager.carrierFrequencies
                    when {
                        ranges == null -> "PHÁT HIỆN BỘ PHÁT IR, NHƯNG KHÔNG ĐỌC ĐƯỢC DẢI TẦN: dịch vụ Consumer IR trả về null. Tần số tham khảo của giao thức remote phổ biến (không phải dải tần đã xác nhận của điện thoại): ${commonFrequencyReferences.joinToString("; ")}. $selectedBrandReference Chưa gửi tín hiệu."
                        ranges.isEmpty() -> "PHÁT HIỆN BỘ PHÁT IR, NHƯNG ANDROID KHÔNG CUNG CẤP DẢI TẦN NÀO. Tần số tham khảo của giao thức remote phổ biến (chưa xác nhận điện thoại hỗ trợ): ${commonFrequencyReferences.joinToString("; ")}. $selectedBrandReference Chưa gửi tín hiệu."
                        else -> {
                            val readable = ranges.joinToString(separator = "; ") {
                                "${it.minFrequency / 1000.0}–${it.maxFrequency / 1000.0} kHz"
                            }
                            val candidateFrequencies = listOf(36000, 38000, 40000).filter { hz ->
                                ranges.any { hz in it.minFrequency..it.maxFrequency }
                            }.joinToString(", ") { "${it / 1000} kHz" }.ifBlank { "không có tần số tham khảo phổ biến nào nằm trong dải đã báo" }
                            "PHÁT HIỆN BỘ PHÁT IR. Dải tần Android báo hỗ trợ: $readable. Tần số tham khảo phổ biến nằm trong dải này: $candidateFrequencies. Tham khảo giao thức: ${commonFrequencyReferences.joinToString("; ")}. $selectedBrandReference Chưa gửi tín hiệu và chưa xác nhận máy lạnh nhận lệnh."
                        }
                    }
                }
            } catch (e: Exception) {
                "LỖI KIỂM TRA IR: ${e.message ?: e.javaClass.simpleName}. Chưa gửi tín hiệu."
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        try { bleScanCallback?.let { bleAdapter?.bluetoothLeScanner?.stopScan(it) } } catch (_: Exception) { }
        bleScanCallback = null
    }

    private fun recordBlePayload(characteristicUuid: String, bytes: ByteArray) {
        val hex = bytes.joinToString("") { "%02X".format(it) }
        val text = bytes.toString(Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
        val capture = JSONObject().put("characteristicUuid", characteristicUuid).put("hex", hex)
            .put("text", text.take(4000)).put("receivedAt", System.currentTimeMillis())
        // Only accept an explicit documented-like JSON object containing positive timings; arbitrary binary bytes are never guessed as timings.
        try {
            val json = JSONObject(text)
            val arr = json.optJSONArray("pattern") ?: json.optJSONArray("raw")
            if (arr != null && arr.length() in 6..1000) {
                val valid = (0 until arr.length()).all { i -> arr.optInt(i, -1) in 1..1_000_000 }
                if (valid) {
                    capture.put("pattern", arr).put("frequency", json.optInt("frequency", 38000).coerceIn(20000, 60000))
                    capture.put("protocol", json.optString("protocol", "BLE payload có pattern; cần xác minh giao thức"))
                }
            }
        } catch (_: Exception) {
            // Unknown vendor binary payload is kept as hex for diagnostics, not falsely decoded.
        }
        latestBleCapture = capture
        bleStatus = "Đã nhận ${bytes.size} byte từ characteristic $characteristicUuid. ${if (capture.has("pattern")) "Payload có trường pattern có thể nhập vào bộ phân tích." else "Đã lưu HEX thô; chưa giải mã được định dạng lệnh."}"
    }

    private fun transmit(brand: String, power: Boolean, temperature: Int, mode: String, fan: String): String {
        val manager = ir ?: return "Không phát hiện dịch vụ Consumer IR của Android."
        if (!manager.hasIrEmitter()) return "Android không phát hiện bộ phát IR. Chưa gửi lệnh."
        // Android-native transmit path. Reetech tries Gree first because the user's working
        // third-party app controls this exact unit when Gree is selected. This is a trial only.
        val isReetech = brand.equals("Reetech", ignoreCase = true)
        val isGree = brand.equals("Gree", ignoreCase = true)
        val isMidea = brand.equals("Midea", ignoreCase = true) || brand.equals("Media", ignoreCase = true)
        if (!isReetech && !isGree && !isMidea) {
            return "${brand}: encoder riêng chưa được tích hợp ở bản này. Chưa phát lệnh; không dùng giao thức của hãng khác."
        }
        return try {
            val pattern = when {
                isReetech || isGree -> GreeIr.buildExperimentalPattern(power, temperature, mode, fan)
                else -> MideaIr.buildPattern(power, temperature, mode, fan)
            }
            val carrier = if (isReetech || isGree) GreeIr.CARRIER_HZ else 38000
            IrTransmitter.send(manager, carrier, pattern)
            when {
                isReetech -> "Đã phát lệnh THỬ cho Reetech RT9-BM5 bằng profile Gree/YAC-family 38 kHz (${temperature.coerceIn(16, 30)}°C, $mode, quạt $fan). Encoder chưa được xác minh với model này; nếu máy không phản hồi, không kết luận phần cứng hỏng."
                isGree -> "Đã phát lệnh Gree bằng profile YAC-family thử nghiệm (${temperature.coerceIn(16, 30)}°C, $mode, quạt $fan). Biến thể Gree chưa được xác minh; chỉ phản hồi máy lạnh mới xác nhận tương thích."
                else -> "Android đã phát lệnh Midea 38 kHz (${temperature.coerceIn(17, 30)}°C, $mode, quạt $fan). API hoàn tất không chứng minh máy lạnh nhận lệnh."
            }
        } catch (e: Exception) {
            "Phát IR thất bại: ${e.message ?: e.javaClass.simpleName}."
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
