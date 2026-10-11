# Universal AC Remote v1.5.5 — Reetech RT9-BM5 profile (protocol unverified)

## Important fix for the “button does nothing” report

- The previous Daikin waveform generator contained a placeholder state layout and assumed checksums, not a verified encoder for a specific Daikin indoor-unit/remote model. A successful Android `transmit()` call only means the API request completed; it does not prove that an AC accepted the command.
- v1.4.0 disables that unverified send path for **all brands** and reports why it did not send a command, instead of showing a misleading transmit-success result. This is an honesty/safety fix, not a claim that universal AC control is now working.
- To enable actual control, add a model-specific encoder backed by a reliable protocol reference/test vectors, or import a compatible raw waveform from an external IR learning device. The built-in Xiaomi 17 IR emitter is transmit-only through Android's public API and cannot learn directly from the CHUNGHOP K-1038E remote.
- The BLE learner UI is generic BLE/GATT only; it is not guaranteed to support every commercial learner or vendor-specific protocol.
- Android version: `1.5.5` (`versionCode 23`). Added a dedicated Reetech profile for indoor unit RT9-BM5 / outdoor unit RC9-BM5. Source ZIP only; no APK was built in this environment and no physical Xiaomi 17/Reetech test was performed.
- Corrected the native Kotlin Midea 48-bit state layout and transmission framing: bytes are ordered as the public IRremoteESP8266 implementation, checksum is validated against its documented reset vector `0xA1826FFFFF62`, and the frame is followed by the bitwise-inverted payload. The previous encoder incorrectly packed the state bytes, temperature and repeat sequence. The app transmits using the experimental Midea encoder only when Midea is selected; Reetech and other unsupported brands remain blocked rather than reusing Midea/Daikin. Reetech 38 kHz is displayed only as a generic reference, not a confirmed model frequency.
- Compatibility is **experimental**. Midea variants/model-specific fields may differ; Android API completion is not proof the air conditioner received or accepted the command. If it does not respond, collect the indoor unit/remote model or compare against a captured known-good waveform.
- Reference: IRremoteESP8266 `ir_Midea.cpp` / `ir_Midea.h` (Midea protocol support is explicitly described as alpha/needs testing).

# Universal AC Remote — ứng dụng web có thể cài trên điện thoại

Bản này là PWA (Progressive Web App): sau khi triển khai lên HTTPS, người dùng có thể cài vào màn hình chính Android và mở như một ứng dụng độc lập. Giao diện được lưu cache để có thể mở khi ngoại tuyến sau lần tải đầu tiên.

## Cài lên điện thoại Android
1. Triển khai thư mục này lên Netlify (hoặc host HTTPS khác). Nếu triển khai toàn bộ dự án qua Git, Netlify sẽ dùng `netlify.toml` và thư mục publish `web`. Nếu dùng Netlify Drop, kéo-thả **nội dung bên trong thư mục `web/`**.
2. Mở URL HTTPS của website trên Chrome Android.
3. Nhấn nút **Cài ứng dụng** nếu hiện ra; hoặc mở menu `⋮` của Chrome → **Cài đặt ứng dụng** / **Thêm vào màn hình chính**. Tên mục có thể khác nhau tùy phiên bản Chrome.
4. Mở ứng dụng từ biểu tượng **AC Remote** trên màn hình chính.

## Cài trên iPhone/iPad
Mở URL bằng Safari → Chia sẻ → **Thêm vào Màn hình chính**.

## Source Android APK
Thư mục `android/` là dự án Android Studio riêng. Để tạo APK cài trực tiếp: mở thư mục `android/` trong Android Studio, đồng bộ Gradle, sau đó chọn **Build → Build Bundle(s) / APK(s) → Build APK(s)**. APK debug thường nằm trong `android/app/build/outputs/apk/debug/`. Cần JDK 17 và Android SDK phù hợp. APK chưa được build trong gói này.

## Lưu ý về điều khiển hồng ngoại
PWA trên Netlify chỉ cung cấp giao diện và lưu trạng thái; trình duyệt không thể phát tia hồng ngoại trực tiếp. Muốn điều khiển máy lạnh thật bằng IR, cần ứng dụng Android trên điện thoại có bộ phát hồng ngoại và encoder phù hợp với model. Encoder Daikin trong source hiện là bản thử nghiệm chưa được xác minh trên thiết bị thực tế; chọn hãng khác không đồng nghĩa giao thức IR của hãng đó đã được hỗ trợ.


### Cá nhân hóa giao diện
Mở mục **Cá nhân hóa giao diện** trong website để chọn một trong năm màu (xanh biển, xanh lá, tím dịu, hồng đào, vàng ấm) và cỡ chữ nhỏ/vừa/lớn. Lựa chọn được lưu tự động trên thiết bị và áp dụng cả khi mở ứng dụng PWA. Chọn **Khôi phục mặc định** để trở lại màu xanh biển và cỡ chữ vừa.


## Xiaomi 17 + Sharp IR status (important)

- Xiaomi's official Xiaomi 17 specifications list an IR blaster. Android apps can only use it through `ConsumerIrManager` if the device firmware exposes the Android consumer-IR API; use **KIỂM TRA BỘ PHÁT IR** in the native app to verify this. Hardware presence alone does not prove transmission or compatibility.
- Sharp air conditioners use model-dependent protocols (including SHARP_AC variants A705, A903 and A907). This package does **not** guess a Sharp state/checksum and does **not** send the experimental Daikin waveform when Sharp is selected. The Sharp send action is deliberately blocked until the exact indoor-unit model or original remote model is known and its encoder can be validated.
- Public protocol reference: https://github.com/crankyoldgit/IRremoteESP8266/blob/master/SupportedProtocols.md and https://crankyoldgit.github.io/IRremoteESP8266/doxygen/html/ir__Sharp_8h.html. The library documents Sharp AC support but labels `sendSharpAc` alpha/untested, so model-level testing is required.
- To finish Sharp support safely, provide a photo of the AC nameplate and/or the remote model label. Test transmissions should be checked against the original remote and actual unit.

## Build APK automatically (GitHub Actions)

This environment does not include Android SDK/Gradle and cannot download build dependencies, so an APK was not compiled here. To build it in GitHub-hosted CI:

1. Upload this project to a GitHub repository.
2. Open the repository's **Actions** tab and select **Build Android APK**.
3. Click **Run workflow** (or push a change under `android/`).
4. When the run finishes successfully, open the run and download the artifact `UniversalACRemote-v1.5.5-run-...`.
5. Extract the artifact ZIP to get `app-debug.apk`, then install it on Android. You may need to allow installation from that source.

This produces a debug APK for testing, not a Play Store release. The Sharp encoder is deliberately not enabled until the exact AC/remote model and protocol are verified. The current experimental Daikin encoder is not verified on real hardware either.


## Build APK bằng GitHub Actions (đã đặt workflow ở đúng vị trí)

**Quan trọng:** Khi tạo repository GitHub, hãy tải lên các tệp/thư mục nằm ngay trong ZIP này. Không đặt toàn bộ dự án lồng trong một thư mục con. Sau khi tải lên, repository phải hiển thị `.github/workflows/build-apk.yml` ở cấp gốc, cùng cấp với `android/`.

1. Tạo repository GitHub và đưa toàn bộ nội dung ZIP này lên nhánh mặc định (thường là `main`). Thư mục `.github` có thể bị ẩn trên máy tính; bật hiển thị file ẩn nếu cần.
2. Mở **Actions**. Nếu GitHub hỏi cho phép Actions, hãy bật cho repository.
3. Chọn workflow **Build Android APK** ở cột bên trái. Nếu không thấy, vào tab **Code** và xác nhận `.github/workflows/build-apk.yml` đã nằm ở cấp gốc trên nhánh mặc định.
4. Nhấn **Run workflow**. Workflow cũng chạy khi có commit thay đổi trong `android/` hoặc chính file workflow.
5. Khi chạy thành công, mở run → **Artifacts** → tải `UniversalACRemote-debug-apk`; giải nén để lấy `app-debug.apk`.

Nếu workflow không xuất hiện: GitHub chỉ nhận workflow từ thư mục chính xác `.github/workflows/` ở cấp gốc repository, và file YAML phải được commit lên nhánh mặc định. Nếu workflow xuất hiện nhưng build thất bại, mở run để xem log lỗi; việc có workflow không đảm bảo mã Android đã build thành công.

## UI refresh
The Android UI has rounded cards, improved spacing, larger temperature controls, responsive scrolling, a visible power status, and automatic local saving. Target SDK is 34 to avoid forced edge-to-edge overlap while explicit window insets are not implemented.

Sharp IR is intentionally not enabled without an exact model/protocol match. A successful build does not prove IR compatibility on a real air conditioner.

## Shared UI v1.2.3 — dùng chung giao diện web
Android hiện mở chính bộ HTML/CSS/JavaScript được đóng gói từ `web/` vào WebView, thay vì dựng lại giao diện bằng các widget Android riêng. Vì vậy header, thẻ bo tròn, bảng màu, cỡ chữ, nút nhiệt độ, chế độ, tốc độ quạt và cài đặt giao diện đều dùng cùng một nguồn thiết kế với web/PWA. Khi thay đổi giao diện, cập nhật `web/` rồi đồng bộ lại `android/app/src/main/assets/web/`.

Nút nguồn trong Android gọi cầu nối native để thử phát IR. Chỉ encoder Daikin hiện có mới được gọi; các hãng khác, đặc biệt Sharp, vẫn bị chặn an toàn khi chưa xác minh đúng model. Trạng thái giao diện và lựa chọn được lưu trong localStorage của WebView; trạng thái lệnh cũng được lưu vào SharedPreferences. Web/PWA chạy ngoài trình duyệt vẫn chỉ là giao diện và không tự phát IR.

## Verify the installed Android build
Bản source này dùng Android versionName `1.2.3` / versionCode `3`. Sau khi GitHub Actions build thành công, cài APK artifact của đúng run/commit đó và kiểm tra Settings → Apps → Universal AC Remote → App info hiển thị version 1.2.3. Chưa thể khẳng định APK đã build hoặc kiểm thử trên thiết bị thật chỉ dựa vào source ZIP.


## App icon v1.2.3
Updated Android launcher icons (legacy and adaptive) and web/PWA icons to the new blue air-conditioner remote artwork. Android package version: 1.2.3 (versionCode 6).


## IR diagnostics, power icon fix, and protocol status (v1.3.3)

- The Android app now exposes **Kiểm tra IR** to query `ConsumerIrManager`, whether Android reports an IR emitter, and supported carrier-frequency ranges. This checks reported hardware capability only; it does not prove an emitted pulse was optically measured or that an AC received it.
- A browser/PWA cannot access the phone's Android Consumer IR API; the diagnostic button explains this limitation.
- AC remotes do not share one universal command encoding. This build does not send random/common test pulses as if they were AC commands. For brands without a verified encoder, the app reports that it did not send an AC command. The existing Daikin encoder remains experimental and must be tested against the actual unit.
- `ConsumerIrManager.transmit()` returning means Android's API completed the transmit request, not that the target appliance received or accepted the command.
- Physical operation cannot be guaranteed from source inspection alone; test on the target phone and air conditioner.


## v1.3.3 UI fix
- Replaced the power-button Unicode glyph (which rendered as a missing-glyph box on some Android/WebView fonts) with an inline SVG power symbol.
- The button updates its accessible label and `aria-pressed` state whenever power state toggles.
- Synced the web/PWA files and Android bundled web assets.
- Android version: `1.3.3` (`versionCode 10`); Java/Kotlin JVM target remains 17.
- The icon/UI change does not prove that a selected brand has a supported IR protocol or that the AC received a command.


## v1.3.4 — IR carrier frequency references

- Added clearly labelled common protocol carrier-frequency references: 36 kHz (Philips RC-5/RC-6), 38 kHz (NEC/Samsung and many consumer remotes), and 40 kHz (Sony SIRC and some others). These are reference values from public IR protocol documentation, **not** a measured frequency list for the phone and not universal AC codes.
- If Android's `getCarrierFrequencies()` returns `null` or an empty list, the app now shows the common references separately and explicitly says that device support is still unknown. If Android reports ranges, the app shows which reference frequencies fall inside those ranges.
- The diagnostic does not transmit arbitrary codes or claim an AC received a command. AC control still requires a matching, verified protocol encoder.
- Sources: Android `ConsumerIrManager` API https://developer.android.com/reference/android/hardware/ConsumerIrManager ; IRremoteESP8266 FAQ (common 38 kHz reference and protocol/model caveats) https://github.com/crankyoldgit/IRremoteESP8266/wiki/Frequently-Asked-Questions ; IRremoteESP8266 protocol documentation https://github.com/crankyoldgit/IRremoteESP8266/blob/master/docs/doxygen_index.md .
- Android version: `1.3.4` (`versionCode 11`); Java and Kotlin targets remain JVM 17.


## v1.3.5 permission fix

Added `android.permission.TRANSMIT_IR` to `android/app/src/main/AndroidManifest.xml`. The screenshot error `Access denied, requires android.permission.TRANSMIT_IR` indicates the installed APK did not declare this permission. It is a normal permission, so no runtime permission dialog is expected; build and install this new APK. This fixes the permission denial, but does not prove that a compatible air-conditioner protocol is available or that the appliance receives the signal.


## v1.3.7 — brand-specific IR frequency references

- Added brand-specific reference notes for Sharp, Mitsubishi Electric, Casper, Midea, Daikin and LG, shown when **Kiểm tra IR** is used.
- Added Casper to the brand selector.
- The reference notes identify protocol families documented by the public IRremoteESP8266 protocol list; they do not assert that every listed protocol applies to every model. Casper is explicitly marked as OEM/model-dependent and not independently verified.
- 38 kHz is presented as a common nominal reference only, not a measured Xiaomi 17 capability or a guarantee of compatibility. General 36/38/40 kHz references remain separate. Carrier frequency alone is insufficient to control an AC: the correct protocol, message structure, checksum and model variant are also required.
- Sources: https://github.com/crankyoldgit/IRremoteESP8266/blob/master/SupportedProtocols.md ; https://developer.android.com/reference/android/hardware/ConsumerIrManager .
- Android version: `1.3.7` (`versionCode 14`); Java/Kotlin targets remain JVM 17. No APK was compiled or tested on hardware as part of this source update.


## v1.3.7 build fix

Moved `BrandIrReference` out of the `inner class AndroidRemoteBridge` into the enclosing `MainActivity` scope. Kotlin does not allow this nested class declaration inside an inner class, which caused `Class is prohibited here`. Version bumped to 1.3.7 (versionCode 14).


## IR Learning Assistant v1.3.8 (phone-only limitation)

- Xiaomi 17's built-in IR emitter is a transmitter. Android's public `ConsumerIrManager` exposes `hasIrEmitter()`, `getCarrierFrequencies()`, and `transmit()`; it does not expose a general receive/capture API for learning from an original remote. The app therefore never pretends that a signal was captured.
- The new **Học lệnh IR** panel explains this limitation, accepts a manually imported/pasted raw mark/space pattern, offers heuristic timing classification (NEC/Sony SIRC only as a guess), saves imported commands in local storage, and can request native replay when the timing pattern is under 1.9 seconds and the chosen frequency is within Android-reported ranges.
- The pattern is not automatically captured from the phone. The decoder is heuristic, not a full AC protocol decoder. Many AC commands contain complete state frames and exceed Android ConsumerIrManager's 2-second transmission limit; long samples are rejected.
- No APK build or physical Xiaomi 17 / air-conditioner test has been performed in this packaging step. Android API transmission completion does not prove that the appliance received or accepted a command.


## v1.3.9 — Bluetooth IR learning assistant

- Adds BLE/GATT scanning, device selection, connection, GATT service/characteristic inspection, and READ/NOTIFY/INDICATE capture workflow.
- Captured payload bytes are stored as HEX/text for diagnostics. The app only imports timings when the peripheral explicitly sends a JSON payload containing `pattern` or `raw` as positive microsecond durations; arbitrary vendor bytes are never guessed into pulse timings.
- Adds Android 12+ `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` runtime permissions, plus legacy scan/location declarations for Android 11 and earlier.
- Compatibility boundary: this is a generic BLE/GATT client, not a universal adapter for every Bluetooth IR learner. The specific device must expose its signal through a readable/notifiable GATT characteristic. Proprietary serial-over-Bluetooth, Bluetooth Classic SPP, unknown binary formats, encrypted payloads, and device-specific command handshakes may need a model-specific driver.
- Android phone IR blaster is transmit-only through the public `ConsumerIrManager` API; this app does not claim that the Xiaomi 17 itself can receive IR.
- Validation performed for this source package: manifest/permissions, version/workflow consistency, JS syntax and ZIP integrity. A full Gradle build and physical BLE hardware test have not been performed in this environment.


## v1.5.1 IR transmission fix
- Removed the hard block that refused Midea commands when Android's optional `getCarrierFrequencies()` query returned null/empty or did not list 38 kHz. The built-in Xiaomi remote may work even when this query is incomplete; `hasIrEmitter()` and `transmit()` remain the runtime checks.
- The same optional-frequency-query block was removed for manually imported raw IR patterns.
- This does not guarantee protocol/model compatibility. If the AC still does not react, capture a known Midea command from a compatible IR learner or identify the exact remote/model; the phone's own IR blaster cannot receive IR through Android's public ConsumerIrManager API.


## IR quick diagnostics v1.5.4
- `Test nhanh phát IR` sends a short generic 38 kHz pulse train. It is not an air-conditioner command. A second phone camera may show the emitter flashing (front cameras often have less IR filtering), but no visible flash is not definitive because camera filters vary.
- `Test mã Midea` runs an offline checksum test vector and validates that the generated pattern is positive and shorter than Android's 2-second transmit limit. It does not prove the target unit uses this Midea variant.
- Android's `ConsumerIrManager.transmit()` returning means the API transmitted the pattern, not that the AC received it. The Midea encoder remains experimental until tested against this specific unit.


### v1.5.4 Midea encoder correction

Corrected the Celsius temperature byte: bits 0–4 carry the offset from 17°C, bit 5 is Fahrenheit, and bits 6–7 are reserved. The previous encoder incorrectly set bit 6, which could cause a Midea receiver to ignore the command. Added a state-layout self-test in addition to the checksum vector. Physical compatibility still requires testing with the actual air conditioner.


## Reetech RT9-BM5 / RC9-BM5 (v1.5.5)

- Added Reetech to the brand selector and a model-specific diagnostic note for indoor RT9-BM5 / outdoor RC9-BM5.
- No verified raw waveform/state encoder was found in this project, so the app intentionally does not transmit a fabricated Reetech command. The universal-remote preset numbers 181/697 are not proof of the AC IR carrier frequency or protocol.
- The generic IR pulse test checks only whether Android can request a short 38 kHz burst; it is not a valid AC command.
- To enable actual control, a known-good capture from a compatible external IR learner or a validated protocol implementation for this model is still required.
