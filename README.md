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
4. When the run finishes successfully, open the run and download the artifact `UniversalACRemote-debug-apk`.
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

## Shared design update
The web/PWA uses the reference design in `web/style.css`; Android native UI was aligned to the same rounded white cards, pale background, amber accent, large temperature controls, mode/fan selectors, and IR diagnostic messaging. Android preferences persist locally. The Android app and web page still use separate rendering code, so future visual changes should be applied to both implementations.

The Sharp encoder is intentionally not enabled without an exact model/protocol match. Build success does not prove IR compatibility on a real air conditioner.

## Verify the installed Android build
This package uses Android versionName `1.1.0` / versionCode `2` and shows `UI SYNC 2026.10 · v1.1.0` at the bottom of the native app screen. After a successful GitHub Actions run, install the APK artifact from that exact run. Confirm the fingerprint is visible and Android App info reports version 1.1.0. If either does not match, the installed APK is not from this source revision.
