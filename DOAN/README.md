# TTTT Cinema

Ứng dụng Android đặt vé xem phim: tìm phim, chọn rạp/ngày/suất/ghế, thanh toán VNPAY Sandbox hoặc giữ chỗ thanh toán tại quầy, xem vé QR và xác thực vé tại rạp.

## Mở và build Android

1. Mở thư mục `DOAN` bằng Android Studio mới có Android SDK 35 và JDK 17 trở lên.
2. Chờ Gradle Sync hoàn tất, sau đó chạy cấu hình `app` trên thiết bị/emulator Android 11 trở lên.
3. Build APK debug trên Windows:

   ```powershell
   .\gradlew.bat assembleDebug
   ```

   APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Firebase mới

- Firebase project: `tttt-cinema-20261020`.
- Android package: `com.example.doan`.
- Cấu hình `app/google-services.json` hiện trỏ tới project mới. Không chép file cấu hình từ project `doan-ecdfa` cũ đè lên.
- Email/password Authentication được khai báo trong `firebase.json`. Sau khi đăng nhập Firebase CLI, có thể triển khai riêng cấu hình này bằng:

  ```powershell
  npx firebase-tools deploy --only auth --project tttt-cinema-20261020
  ```

- Realtime Database Rules nằm ở `database.rules.json` và mặc định chặn mọi truy cập trực tiếp từ client. Cloud Functions dùng Admin SDK để truy cập dữ liệu.
- Tạo database mặc định trong Firebase Console tại **Build → Realtime Database → Create Database**, chọn `asia-southeast1`, rồi triển khai Rules:

  ```powershell
  npx firebase-tools deploy --only database --project tttt-cinema-20261020
  ```

  Nếu Firebase yêu cầu nâng gói hoặc liên kết Cloud Billing khi tạo database/deploy Functions, chủ project phải tự chọn và xác nhận phương thức thanh toán. Không có billing account nào được liên kết tự động.

## Deploy Cloud Functions và VNPAY Sandbox

Backend trong `functions/` tự tính tiền, giữ ghế theo phim/rạp/ngày/suất, ký URL VNPAY, xác minh chữ ký callback/IPN, và chỉ phát hành vé sau khi giao dịch được xác nhận. Thanh toán tại quầy chỉ phát hành vé khi tài khoản có quyền staff/admin xác nhận đã thu tiền. Các callback lặp được xử lý idempotent; mỗi vé chỉ được soát một lần.

1. Cài Node.js 22 và Firebase CLI; đăng nhập bằng tài khoản có quyền trên project.
2. Tạo Realtime Database trước và bảo đảm Rules đang chặn truy cập trực tiếp.
3. Tạo hai secrets bằng lệnh sau. CLI sẽ hỏi giá trị riêng trong terminal; không thêm giá trị vào lệnh, mã nguồn, Gradle, `.env`, tài liệu hoặc Git:

   ```powershell
   npx firebase-tools functions:secrets:set VNPAY_HASH_SECRET --project tttt-cinema-20261020
   npx firebase-tools functions:secrets:set ADMIN_BOOTSTRAP_CODE --project tttt-cinema-20261020
   ```

   Nhập Hash Secret VNPAY đã được cung cấp trước đó vào Secret `VNPAY_HASH_SECRET`. Tạo một mã bootstrap ngẫu nhiên dài, giữ riêng, nhập vào Secret `ADMIN_BOOTSTRAP_CODE`, rồi dùng mã đó đúng một lần trong app để kích hoạt tài khoản admin đầu tiên.

4. Cài dependencies và deploy:

   ```powershell
   Push-Location functions
   npm ci
   Pop-Location
   npx firebase-tools deploy --only functions:vnpayApi --project tttt-cinema-20261020
   ```

   Cloud Functions Gen 2 cần project dùng Blaze và billing đã bật. Chỉ triển khai sau khi chủ project đã chấp thuận điều khoản và liên kết billing trong Firebase/Google Cloud Console.

5. Trong VNPAY Sandbox Merchant Admin, đặt **IPN URL** thành:

   ```text
   https://asia-southeast1-tttt-cinema-20261020.cloudfunctions.net/vnpayApi/api/payments/vnpay/ipn
   ```

   URL return được backend tạo cho từng giao dịch. App hiển thị QR chứa chính URL thanh toán đã ký và có thể mở URL đó trong trình duyệt. Chỉ IPN/return đã qua kiểm tra chữ ký, mã website, đơn và số tiền mới cập nhật trạng thái thanh toán.

6. Build lại APK sau khi backend đã deploy. Terminal ID được cấu hình là `GW923J6D`; Hash Secret chỉ nằm trong Secret Manager.

### Thẻ VNPAY Sandbox

Theo [tài liệu chính thức VNPAY Sandbox](https://sandbox.vnpayment.vn/apis/docs/gioi-thieu/) và [trang demo VNPAY](https://sandbox.vnpayment.vn/apis/vnpay-demo/):

- Ngân hàng: NCB
- Số thẻ: `9704198526191432198`
- Tên chủ thẻ: `NGUYEN VAN A`
- Ngày phát hành: `07/15`
- OTP: `123456`

Chỉ dùng thông tin thẻ test này trong Sandbox.

## Tài khoản quản trị và nhân viên

1. Tạo tài khoản Firebase Authentication **bằng email riêng mà bạn kiểm soát**; tài khoản này đăng nhập như khách bình thường.
2. Đăng xuất khỏi tài khoản khách, đăng ký email admin riêng trong app, đăng nhập, nhập bootstrap code để kích hoạt admin đầu tiên.
3. Admin có thể cấp quyền staff cho email đã đăng ký. Backend xác thực custom claims từ Firebase ID token; client không thể tự bật quyền bằng cờ gửi lên.
4. Nhân viên mở **Tài khoản → Khu vực nhân viên**, xác nhận đã thu tiền tại quầy hoặc dùng camera quét QR vé. Backend chỉ chấp nhận vé đã thanh toán và từ chối quét lại.

Ứng dụng không thể tạo hộ một hộp thư Gmail/email bên ngoài. Hãy dùng email admin riêng do bạn sở hữu để nhận link khôi phục mật khẩu.

## Chạy Emulator khi phát triển

1. Tạo `functions/.secret.local` (file này đã bị Git ignore) với hai dòng `VNPAY_HASH_SECRET=...` và `ADMIN_BOOTSTRAP_CODE=...`. Không dùng secret thật nếu không cần; không commit file này.
2. Tại thư mục `DOAN`, chạy:

   ```powershell
   npx firebase-tools emulators:start --only auth,functions,database --project demo-tttt-cinema
   ```

3. Trên Android Emulator, build trỏ tới emulator host:

   ```powershell
   .\gradlew.bat installDebug `
     -PFIREBASE_AUTH_EMULATOR_HOST=10.0.2.2 `
     -PVNPAY_API_BASE_URL=http://10.0.2.2:5001/demo-tttt-cinema/asia-southeast1/vnpayApi
   ```

   `10.0.2.2` là địa chỉ từ Android Emulator về máy chạy Firebase Emulator. Khi dùng điện thoại thật, thay bằng IP LAN của máy và bảo đảm hai thiết bị cùng mạng. VNPAY Sandbox thật cần URL IPN public; Functions Emulator trên localhost không nhận được callback từ VNPAY.

## Giới hạn cần biết

- Project Firebase mới và Android app đã tạo; file Android config đã đổi sang project mới.
- Email/password Auth được bật qua cấu hình CLI nếu lệnh deploy Auth hoàn tất.
- Database và Functions production chưa được xem là hoạt động cho đến khi tạo được Realtime Database, nhập secrets, bật billing nếu Firebase yêu cầu, deploy Functions, và đăng ký IPN URL trên Merchant Admin.
- Danh mục phim, rạp và suất chiếu hiện là dữ liệu mẫu trong app; cần thay bằng lịch chiếu thật của rạp trước khi vận hành.
- Build/test xác minh mã nguồn cục bộ; không chứng minh giao dịch VNPAY end-to-end khi backend/callback production chưa deploy.
