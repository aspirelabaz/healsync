# HealSync

Đồng bộ dữ liệu Fitbit (Charge 5) sang Samsung Health:

```
Charge 5 → app Fitbit → Google Health API → HealSync → Health Connect → Samsung Health
```

Các loại dữ liệu đang hỗ trợ: bước chân (gộp theo khung 15 phút), nhịp tim (mỗi giờ một bản ghi) và giấc ngủ (có các giai đoạn).
App tự đồng bộ mỗi giờ khi có mạng. Mỗi bản ghi có `clientRecordId` cố định, nên đồng bộ lại cùng khoảng thời gian không tạo bản trùng.

## 1. Tạo OAuth client trên Google Cloud

1. Vào https://console.cloud.google.com và tạo một project mới.
2. Vào **APIs & Services → Library**, tìm **Google Health API** và bật nó.
3. Vào **Google Auth Platform → Branding / Audience**:
   - User type: **External**, Publishing status: **Testing**.
   - Mục **Test users**: thêm email Google bạn dùng cho Fitbit.
4. Vào **Data Access**, thêm 3 scope sau:
   - `https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly`
   - `https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly`
   - `https://www.googleapis.com/auth/googlehealth.sleep.readonly`
5. Vào **Clients → Create client**, chọn loại **Android**:
   - Package name: `com.aspirelab.healsync`
   - SHA-1: mã của khoá ký app. Với bản debug trên máy này:
     `10:73:CC:7C:76:D0:75:F4:29:EC:A6:05:E6:DE:BE:B2:5E:63:FF:EB`
     Lệnh để tự lấy mã: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
   - Mở **Advanced settings** và bật **Enable custom URI scheme**. App dùng redirect `com.aspirelab.healsync:/oauth2redirect`.
6. Copy Client ID, có dạng `xxxx.apps.googleusercontent.com`.

## 2. Cấu hình và build

Thêm Client ID vào file `local.properties` (file này không đưa lên git):

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
google.clientId=xxxx.apps.googleusercontent.com
```

Build và cài lên điện thoại (bật USB debugging trên máy):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest   # unit test
```

Hoặc mở thư mục này bằng Android Studio rồi bấm Run.

## 3. Sử dụng

1. **Đăng nhập Google** bằng đúng tài khoản Google đang dùng trong app Fitbit.
2. **Cấp quyền ghi** trong Health Connect cho bước chân, nhịp tim và giấc ngủ.
3. Bấm **Đồng bộ ngay**. Lần đầu app lấy dữ liệu 7 ngày gần nhất.
4. Trong Samsung Health, bật đọc dữ liệu từ Health Connect: **Cài đặt → Health Connect**.

## Lưu ý

- **Trùng bước chân:** nếu app Fitbit cũng ghi bước chân vào Health Connect, hoặc điện thoại Samsung tự đếm bước, hãy vào Samsung Health và chọn **nguồn dữ liệu bước chân** ưu tiên. Bạn cũng có thể tắt quyền ghi bước chân của app Fitbit trong Health Connect.
- **Đăng nhập lại mỗi 7 ngày:** khi OAuth app còn ở chế độ *Testing*, refresh token hết hạn sau 7 ngày. Khi đó nhật ký báo "Phiên đăng nhập đã hết hạn" và bạn bấm đăng nhập lại.
- **Lỗi `ACCOUNT_NOT_LINKED`:** tài khoản Google chưa được liên kết với dữ liệu Fitbit. Mở app Fitbit và hoàn tất bước chuyển sang tài khoản Google.
- App chỉ đọc nguồn `google-wearables`, để dữ liệu chính app ghi vào Health Connect không bị đọc ngược lại.
- Google Health API v4 còn mới, và một vài trường JSON chưa được tài liệu mô tả rõ. Nếu một loại dữ liệu báo `0 bản ghi` dù đồng hồ có dữ liệu, xem `GoogleHealthParser`. Đây là nơi duy nhất cần sửa khi schema thay đổi.

## Cấu trúc

```
app/src/main/java/com/aspirelab/healsync/
├── auth/           AuthManager: Google OAuth (AppAuth + PKCE), refresh token
├── data/           AppPrefs (DataStore), SecretBox (mã hoá token bằng Keystore)
├── googlehealth/   GoogleHealthClient (REST v4), GoogleHealthParser (JSON → model)
├── healthconnect/  RecordFactory (model → Health Connect record), HealthConnectWriter
├── sync/           SyncRepository (logic đồng bộ), SyncWorker (WorkManager)
└── ui/             MainActivity (Compose), PrivacyPolicyActivity
```

## Việc tiếp theo

- Thêm SpO2, HRV, nhịp tim nghỉ, calo, quãng đường và bài tập.
- Màn hình cài đặt để chọn loại dữ liệu và tần suất đồng bộ.
- Thông báo khi cần đăng nhập lại.
