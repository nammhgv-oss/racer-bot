# Racer AutoPilot — bot chơi game đua xe trong video (Kotlin, Android 8.0+)

> **TRẠNG THÁI THẬT (đọc trước):** mã nguồn đầy đủ, **CHƯA được biên dịch và CHƯA chạy trên điện thoại**.
> Môi trường tạo dự án không có Android SDK, Kotlin compiler, Gradle và không có mạng, nên **không có file APK nào được tạo**.
> Phần đã kiểm chứng thật là **thuật toán thị giác chạy bằng Python trên chính video** (xem mục 3). Lỗi biên dịch nhỏ có thể xuất hiện ở lần build đầu.

## 1. Cách build APK (Debug + Release)

Cần JDK 17 và Android SDK (platform 34, `ANDROID_HOME` đã đặt).

```bash
cd RacerBot
gradle wrapper --gradle-version 8.7     # tạo gradlew một lần (hoặc mở bằng Android Studio, nó tự tạo)
./gradlew assembleDebug                 # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease               # -> app/build/outputs/apk/release/app-release.apk
```

Không có Android Studio? Đẩy thư mục này lên một repo GitHub: workflow `.github/workflows/build.yml` tự build và đính kèm 2 APK ở tab *Actions → Artifacts*.
APK release được ký bằng keystore debug tự sinh để cài trực tiếp được; muốn phát hành thật, thay `signingConfig` trong `app/build.gradle.kts`.

## 2. Những gì đo được từ video (và cái gì KHÔNG đo được)

| Hạng mục | Kết quả |
|---|---|
| Video | dọc 720×1640, 30 fps, 21,8 s. 80 px trên cùng là thanh của app chứa game ("Sting", nút ··· và ✕) → đây là mini-game nhúng; vùng game bắt đầu ở y≈0,05 |
| Camera | góc nhìn thứ ba từ phía sau, đường 3D uốn lượn; xe đứng yên theo trục dọc trên màn hình, đầu xe ở y≈0,59 (trung vị) |
| "Làn" | **Không có làn cố định.** Tâm xe lệch ngang liên tục từ x≈0,03 đến 0,96 bề rộng. Vạch trắng chỉ trang trí. Bot dùng N vị trí ảo (mặc định 5) trải trên bề rộng đường; đường rộng ≈0,86 màn hình, xe ≈0,25 bề rộng đường |
| Cách điều khiển | Hướng dẫn đầu video: bàn tay + mũi tên ‹‹ ›› → **vuốt/kéo ngang**. Quãng vuốt, thời lượng, và ánh xạ ngón→xe **không quan sát được** (video không hiện chạm). Vì vậy: chế độ mặc định *vuốt tỉ lệ + tự học độ nhạy*, có chế độ *1 vuốt = 1 bước* và nút đảo hướng |
| Tốc độ lại gần | Đo từ 1.257 mẫu theo dõi: vy ≈ 1,2·(y−0,19)² chiều-cao/giây. Chướng ngại lần đầu thấy ở y≈0,38 chạm đầu xe sau **≈2,2 s**; từ y=0,5 chỉ còn **≈0,6 s**. Tốc độ game có đổi hay không: **chưa xác định** |
| Thanh năng lượng | 7 ô, góc trên-trái (x 0,254–0,746; y≈0,124). Giảm dần theo thời gian; tăng 1 ô khi nhặt vật phẩm (7 lần tăng được thấy trong video) |
| Vật phẩm | **viên nang vàng phát sáng** và **lon đỏ**; nhặt xong có tia sét ⚡ bắn ra |
| Chướng ngại | rào vàng-đen, con quay vàng, búa/pháo vàng, tháp pháo đỏ, thùng đỏ-đen có xích, cầu gai đen, cầu đỏ-đen có tia sét. Chữ "NGUY HIỂM" hiện phía trên chướng ngại ở xa. Vòng/mái trong mờ ở ~8 s: **không rõ ý nghĩa** |
| Game over / menu / loading / chơi lại | **Không có trong video** → không suy ra được. Các trạng thái này chỉ được *suy luận* (HUD biến mất sau khi đang chơi) và chưa kiểm chứng; mặc định không gửi thao tác ở bất kỳ trạng thái nào ngoài PLAYING |

## 3. Đã kiểm chứng gì (Python mô phỏng đúng thuật toán, chạy trên video)

- Đọc thanh năng lượng: khớp **95,6 %** khung hình so với số đo full-res (sai ở ±3 khung quanh lúc đổi ô); luôn nhận ra ≥6/7 ô.
- Tìm xe: thấy ở **96,6 %** khung hình.
- Vật phẩm: trước cả 7 lần năng lượng tăng đều có phát hiện vật phẩm (3–33 trong 45 khung trước đó). Vẫn còn dương tính giả nhỏ ở ray đỏ-trắng, và lon đỏ lớn ở gần đôi khi bị gộp thành "chướng ngại".
- Chướng ngại: **không có nhãn chuẩn nên chưa đo được độ chính xác.** Xem bằng mắt: bắt tốt vật lớn (con quay, thùng, rào), nhưng còn mảng giả ở chỗ đường rẽ nhánh và có khung bị sót. Hai chỉnh sửa rút ra từ việc soi lỗi (lọc mảng đứng yên, nới độ đặc cho vật thể thưa) đã được đưa vào Kotlin nhưng chưa đo lại định lượng.
- Bộ ra quyết định: chỉ replay vòng hở (xe trong video không nghe lệnh bot) nên **không chứng minh được chất lượng chơi hay chống dao động**. Hiệu năng vòng kín chỉ biết được khi chạy trên máy thật.

17 mục kiểm thử trong yêu cầu (biên dịch, cài APK, Accessibility, chụp màn hình, vuốt thật, overlay, start/stop/pause, calibration, hành vi khi tin cậy thấp / trạng thái lạ…): **chưa thực hiện mục nào trên thiết bị.**

## 4. Kiến trúc

```
MediaProjection → ImageReader nhỏ (≈180 px rộng) → HSV
 → PlayerDetector (vệt khói xả + thân đỏ) → LaneDetector (mép đường theo từng hàng, toạ độ "tương đối theo đường")
 → EnergyDetector (màu vàng/đỏ + vòng đường navy; đọc thanh HUD) + ObstacleDetector (vật không phải đường)
 → ObjectTracker → CollisionPredictor (TTC theo định luật phối cảnh đo được; LOW/MEDIUM/HIGH/CRITICAL)
 → DecisionEngine (điểm an toàn từng vị trí, chi phí đổi làn, hysteresis, chống đảo chiều, ngưỡng tin cậy)
 → SwipeController → AccessibilityService.dispatchGesture → quan sát lại phản hồi của xe (tự học độ nhạy)
```
Toàn bộ là thị giác cổ điển thuần Kotlin, không cần OpenCV hay mô hình ML (giảm rủi ro build và CPU). Thư mục `tools/` chứa các script Python đã dùng để kiểm chứng.

Mọi quyết định đều dựa trên khung hình hiện tại; không có macro, không có chuỗi vuốt ghi sẵn, không có chu kỳ cố định. Không có jitter ngẫu nhiên và không có cơ chế nào để né hay đánh lừa hệ thống chống gian lận.

## 5. An toàn & quyền riêng tư
- Không có quyền INTERNET; không lưu/gửi ảnh chụp. Quyền: Trợ năng (vuốt + quét chữ xác minh), hiển thị trên ứng dụng khác (bảng nổi), dịch vụ nền ghi màn hình.
- Màn hình có chữ kiểu CAPTCHA/xác minh (quét qua Trợ năng) → bot **tự dừng và cảnh báo**, không cố giải/né. Quét theo từ khoá nên không phát hiện được thử thách chỉ vẽ bằng hình.
- Không nhận ra trạng thái / mất HUD → ngừng gửi thao tác, chờ và nhận diện lại. Độ tin cậy dưới ngưỡng → chờ, không vuốt bừa.
- Dùng bot có thể vi phạm điều khoản của game/nhà phát hành (nhất là nếu game có giải thưởng). Bạn tự chịu trách nhiệm.
- Lớp phủ nằm trong ảnh chụp: mặc định radar nhỏ ở góc dưới-trái, ngoài vùng phân tích. Lớp phủ toàn màn hình chỉ bật khi hiệu chuẩn hoặc nếu bạn chủ động bật (có thể gây nhiễu nhận diện).

## 6. Hướng dẫn cài đặt & sử dụng
1. Cài APK (cho phép "cài từ nguồn không rõ" cho trình cài đặt bạn dùng).
2. Mở app → **Bước 1**: bật dịch vụ Trợ năng "Racer AutoPilot" (Cài đặt → Trợ năng → Ứng dụng đã tải xuống). Android có thể cảnh báo; đó là quyền cần thiết để vuốt thay bạn.
3. **Bước 2**: cấp quyền hiển thị trên ứng dụng khác.
4. **Bước 3**: *Bắt đầu phiên* → đồng ý hộp thoại ghi màn hình. Bảng nổi "RACER BOT" xuất hiện.
5. Mở game (đang ở màn chơi). Chạm **CALIBRATE**: lớp phủ hiện vạch trắng (mép đường & các vị trí), khung vàng (vật phẩm), đỏ (nguy hiểm), xanh dương (xe). Kiểm tra: khung xanh dương bám xe, 7 ô thanh năng lượng nằm trong khung vàng. Chưa khớp → *SETTINGS* chỉnh vùng.
6. Chạm **CALIBRATE** lần nữa để bot **vuốt thử** (xe phải dịch ngang). Nếu xe đi ngược → bật "Đảo hướng vuốt"; nếu 1 vuốt đi quá/thiếu → cứ vuốt thử vài lần, bot tự học độ nhạy, hoặc chọn chế độ vuốt rời rạc và chỉnh quãng vuốt.
7. Chạm **STOP** để thoát hiệu chuẩn, rồi **START**. Thu gọn bảng nổi (nút —) khi chơi.
8. Debug: Cài đặt → "Lớp phủ debug" (radar + chữ: PLAYER, RISK/TTC, TARGET, ACT, FPS, CONF).
9. **Dừng ngay:** nút STOP trên bảng, nút DỪNG ở thông báo, hoặc tắt dịch vụ Trợ năng.

## 7. Việc cần chỉnh khi chạy thật (dự đoán, chưa thử)
Độ nhạy vuốt/hướng vuốt; `carHalfWidth`, vùng game/xe/thanh năng lượng nếu máy có tỉ lệ khác; ngưỡng tin cậy và độ nhạy chướng ngại nếu bot né nhầm vật không có thật hoặc bỏ sót rào; cooldown nếu xe phản ứng chậm. Game over/chơi lại cần bạn tự đặt điểm chạm nếu muốn dùng tự khởi động lại.
