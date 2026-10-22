# Cinema

Ứng dụng Android đặt vé xem phim: tìm phim, chọn rạp/ngày/suất/ghế, xác nhận thanh toán bằng VNPAY Sandbox, xem QR thanh toán rồi nhận QR vé sau khi giao dịch được máy chủ xác minh.

## Mở và build Android

1. Mở thư mục `DOAN` bằng Android Studio mới có Android SDK 35 và JDK 17 trở lên.
2. Chờ Gradle Sync hoàn tất, sau đó chạy cấu hình `app` trên thiết bị/emulator Android 11 trở lên.
3. Build APK debug trên Windows:

   ```powershell
   .\gradlew.bat assembleDebug
   ```

   APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Chuyển sang máy Windows khác để demo thanh toán

Firebase là dịch vụ cloud nên không cần chép database sang máy mới. Dự án dùng lại Firebase project `tttt-cinema-20261020`; máy mới cần đăng nhập tài khoản Google có quyền truy cập project. Hai file cấu hình Firebase đã được lưu trong Git: `.firebaserc` và `app/google-services.json`.

1. Mở PowerShell và cài Git, Android Studio, Node.js 22, cùng Cloudflare Tunnel bằng `winget`:

   ```powershell
   winget install --id Git.Git --exact --accept-source-agreements --accept-package-agreements
   winget install --id Google.AndroidStudio --exact --accept-source-agreements --accept-package-agreements
   winget install --id OpenJS.NodeJS.22 --exact --accept-source-agreements --accept-package-agreements
   ```

   Cài Cloudflare Tunnel từ MSI Windows x64 chính thức:

   ```powershell
   $cloudflaredInstaller = Join-Path $env:TEMP 'cloudflared-windows-amd64.msi'
   Invoke-WebRequest -Uri 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.msi' -OutFile $cloudflaredInstaller
   Start-Process -FilePath msiexec.exe -ArgumentList "/i `"$cloudflaredInstaller`"" -Wait
   ```

   Cài Google Cloud CLI bằng installer chính thức:

   ```powershell
   $gcloudInstaller = Join-Path $env:TEMP 'GoogleCloudSDKInstaller.exe'
   Invoke-WebRequest -Uri 'https://dl.google.com/dl/cloudsdk/channels/rapid/GoogleCloudSDKInstaller.exe' -OutFile $gcloudInstaller
   Start-Process -FilePath $gcloudInstaller -Wait
   ```

   Nếu `winget` chưa được nhận diện, cài **App Installer** từ Microsoft Store rồi mở lại PowerShell. Sau khi cài xong, đóng/mở terminal mới. Kiểm tra các lệnh đã vào PATH:

   ```powershell
   git --version
   node --version
   npm --version
   cloudflared --version
   & "$env:LOCALAPPDATA\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd" --version
   ```

   Android Studio có Java runtime riêng; trong **SDK Manager**, cài **Android SDK Platform 35** và các build tools được gợi ý.

2. Clone GitHub rồi vào đúng thư mục ứng dụng `DOAN`:

   ```powershell
   git clone https://github.com/hothithanhtuyen/TTTT.git
   cd TTTT\DOAN
   ```

   Mở `DOAN` bằng Android Studio một lần và hoàn tất Gradle Sync. Việc này tạo `local.properties` có đường dẫn SDK; script khởi động backend sẽ giữ lại đường dẫn đó.

3. Cài các package backend theo lockfile và đăng nhập Firebase CLI:

   ```powershell
   Push-Location .\functions
   npm ci
   Pop-Location
   npx.cmd --yes firebase-tools@15.34.0 login
   ```

4. Đăng nhập Application Default Credentials bằng tài khoản Google có quyền trên project. Nếu `gcloud` chưa nhận diện trong PowerShell, dùng đường dẫn cài mặc định:

   ```powershell
   & "$env:LOCALAPPDATA\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd" auth application-default login
   & "$env:LOCALAPPDATA\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd" auth application-default set-quota-project tttt-cinema-20261020
   ```

   Nếu đăng nhập ADC không mở callback tự động, thêm `--no-launch-browser`, mở đường dẫn Google được in ra, rồi dán mã xác minh trở lại terminal.

5. Tạo `functions/.secret.local` trên máy mới bằng Notepad, gồm hai dòng `VNPAY_HASH_SECRET=...` và `ADMIN_BOOTSTRAP_CODE=...`. Dùng đúng Hash Secret Sandbox của VNPAY; đặt bootstrap code riêng nếu cần cấp quyền admin. Các giá trị này không nằm trong Git. Không chép `local.properties`, `.env.local` hoặc `.secret.local` từ máy cũ: chúng là cấu hình cục bộ, và tunnel cũ có thể đã hết hiệu lực.

6. Mở terminal thứ nhất tại `DOAN` và khởi động HTTPS tunnel:

   ```powershell
   cloudflared tunnel --url http://127.0.0.1:5001
   ```

   Để nguyên terminal này chạy. Sao chép origin HTTPS được cấp, ví dụ `https://abc.trycloudflare.com`.

7. Mở terminal thứ hai tại `DOAN`, thay origin bằng địa chỉ vừa nhận:

   ```powershell
   .\functions\start-spark-vnpay.ps1 -TunnelOrigin "https://abc.trycloudflare.com"
   ```

   Script sẽ ghi API URL cục bộ vào `local.properties` cho Android và `functions/.env.local` cho URL trả về VNPAY, rồi chạy Functions Emulator. Giữ terminal này chạy trong suốt lúc demo. Backend ghi đơn/ghế/vé vào Realtime Database thật; không bật Database Emulator hoặc Auth Emulator trong luồng này.

8. Trong VNPAY Sandbox Merchant Admin, đặt IPN URL đúng origin hiện tại:

   ```text
   https://abc.trycloudflare.com/tttt-cinema-20261020/asia-southeast1/vnpayApi/api/payments/vnpay/ipn
   ```

   Origin trên là ví dụ: phải thay bằng URL tunnel mới. Tunnel miễn phí thường cấp URL khác khi chạy lại, vì vậy mỗi lần đổi máy/khởi động lại tunnel cần cập nhật IPN URL này.

9. Trong Android Studio, chọn **File → Sync Project with Gradle Files**, rồi chạy cấu hình `app` trên emulator hoặc điện thoại. Có thể cài nhanh bằng `.\gradlew.bat installDebug` trong PowerShell. Chỉ tạo đơn thanh toán sau khi cả tunnel và Functions Emulator đã chạy.

Máy tính chạy tunnel phải luôn thức và có Internet cho đến khi VNPAY gửi IPN và app nhận trạng thái. Nếu máy tắt, ngủ hoặc tunnel đổi URL mà chưa cập nhật IPN, callback không tới được backend. Cách này phù hợp demo Sandbox trên gói Spark; để chạy liên tục cần một endpoint backend công khai ổn định, chẳng hạn triển khai Cloud Functions sau khi project đủ điều kiện billing.

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

Backend trong `functions/` tự tính tiền, giữ ghế theo phim/rạp/ngày/suất, ký URL VNPAY, xác minh chữ ký callback/IPN, và chỉ phát hành vé sau khi giao dịch được xác nhận. Đơn mới chỉ chấp nhận VNPAY; callback lặp được xử lý idempotent và mỗi vé chỉ được soát một lần.

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

## Chạy backend trên máy và nhận IPN qua tunnel (không cần Blaze)

Nếu không muốn liên kết Cloud Billing, có thể chạy chính backend Functions ở chế độ Emulator trên máy tính rồi đưa cổng local ra Internet bằng HTTPS tunnel, giống cách backend BusBooking được expose. Dữ liệu đơn, ghế và vé vẫn lưu trong Realtime Database Firebase; không dùng MySQL. Cách này không deploy Functions lên Firebase và máy tính phải luôn bật khi thử thanh toán.

1. Tạo Realtime Database trên Spark và triển khai `database.rules.json` theo phần Firebase phía trên. Không khởi động Database Emulator hoặc Auth Emulator trong luồng này: Functions Emulator sẽ dùng Realtime Database và Authentication thật của project.
2. Đăng nhập Firebase CLI. Vì backend local dùng Firebase Admin SDK để xác minh token và ghi dữ liệu cloud, hãy cài Google Cloud CLI rồi đăng nhập Application Default Credentials bằng tài khoản có quyền quản trị project:

   ```powershell
   npx firebase-tools login
   gcloud auth application-default login
   ```

3. Tạo `functions/.secret.local` (đã được Git ignore) với hai dòng `VNPAY_HASH_SECRET=...` và `ADMIN_BOOTSTRAP_CODE=...`. Nhập Hash Secret đã được cung cấp vào file này; tạo bootstrap code ngẫu nhiên, giữ riêng. Không gửi, commit hoặc chép các giá trị này vào APK.
4. Mở một terminal để chạy HTTPS tunnel tới cổng Functions Emulator:

   ```powershell
   cloudflared tunnel --url http://127.0.0.1:5001
   ```

   Hoặc dùng `ngrok http 5001`. Giữ tunnel chạy và lấy origin HTTPS được cấp, ví dụ `https://abc.ngrok-free.app`.
5. Từ thư mục `DOAN`, mở backend với origin của tunnel:

   ```powershell
   .\functions\start-spark-vnpay.ps1 -TunnelOrigin "https://abc.ngrok-free.app"
   ```

   Script chạy Functions Emulator cho project thật, chỉ bật emulator cho backend và tự tạo địa chỉ API/IPN đầy đủ. Lệnh không deploy Functions và không yêu cầu nâng Firebase lên Blaze. Script lưu URL API vào `local.properties` để Android Studio dùng khi bấm **Run**.
6. Build app trỏ cùng API base URL. Thay URL bằng origin HTTPS tunnel của bạn:

   ```powershell
   $api = "https://abc.ngrok-free.app/tttt-cinema-20261020/asia-southeast1/vnpayApi"
   .\gradlew.bat installDebug "-PVNPAY_API_BASE_URL=$api"
   ```

7. Trong VNPAY Sandbox Merchant Admin, đặt IPN URL thành API base URL cộng với `/api/payments/vnpay/ipn`, ví dụ:

   ```text
   https://abc.ngrok-free.app/tttt-cinema-20261020/asia-southeast1/vnpayApi/api/payments/vnpay/ipn
   ```

Tunnel thường đổi URL khi khởi động lại. Khi URL đổi, chạy lại script, chọn **File → Sync Project with Gradle Files** trong Android Studio và cập nhật IPN trên VNPAY. VNPAY cần gọi được IPN qua HTTPS; địa chỉ LAN hoặc `localhost` không nhận được callback từ máy chủ VNPAY. Chỉ dùng cách này để phát triển/Sandbox, không xem máy cá nhân là backend vận hành liên tục.

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
4. Nhân viên mở **Tài khoản → Khu vực nhân viên** và dùng camera quét QR vé. Backend chỉ chấp nhận vé đã thanh toán và từ chối quét lại.

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
- Cloud Functions production chưa deploy vì Firebase Spark không cho deploy Functions. Có thể chạy backend qua Functions Emulator + HTTPS tunnel theo hướng dẫn trên; cần tạo Realtime Database, thiết lập Application Default Credentials, lưu secrets local, và đăng ký IPN URL Sandbox.
- Danh mục phim, rạp và suất chiếu hiện là dữ liệu mẫu trong app; cần thay bằng lịch chiếu thật của rạp trước khi vận hành.
- Build/test cục bộ không tự xác minh giao dịch VNPAY end-to-end. Chỉ xác nhận thanh toán Sandbox khi tunnel công khai hoạt động, VNPAY gọi được IPN và app nhận trạng thái đã xác minh.
