# Ứng Dụng Đặt Vé Xem Phim (Cinema Movie Booking App)

> **Đồ án phát triển ứng dụng di động Android**  
> **Ngôn ngữ:** Kotlin 2.1.0 | **Cơ sở dữ liệu:** Firebase Realtime Database | **Kiến trúc:** Clean Layered Architecture (Model - Adapter - UI - Utils)

---

## 📌 1. Giới thiệu tổng quan dự án

Ứng dụng **Cinema Movie Booking App** là hệ thống đặt vé xem phim trực tuyến hoàn chỉnh trên nền tảng Android, kết hợp giữa ứng dụng di động dành cho khách hàng và bảng điều khiển quản trị dành cho Admin:
- **Thời gian thực (Realtime):** Kết nối trực tiếp với Firebase Realtime Database để đồng bộ danh sách phim, suất chiếu, sơ đồ ghế đã đặt, trạng thái đơn hàng.
- **Tương tác trực quan:** Sơ đồ chọn ghế ma trận tương tác (A1 đến F5) tự động khóa các ghế đã được đặt trước; Slider poster phim tự động lướt mượt mà bằng `ViewPager2` và làm mờ nền động.
- **Dịch vụ thông báo Email tự động:** Tự động gửi email xác nhận đặt vé và mã khôi phục tài khoản (OTP 6 ký tự) thông qua giao thức SMTP Gmail (JavaMail API).
- **Phân quyền người dùng rõ ràng:** Khách hàng (User) và Quản trị viên (Admin - `admin` / `admin123`).

---

## 🌳 2. Sơ đồ cây cấu trúc toàn bộ dự án (Project Directory Tree)

Dưới đây là toàn bộ cấu trúc thư mục và tập tin chi tiết của dự án:

```text
DOAN (5)/
├── README.md                                # Tài liệu giới thiệu & cấu trúc dự án ở thư mục gốc
└── DOAN/                                    # Thư mục gốc dự án Android Studio
    ├── build.gradle.kts                     # Cấu hình plugin cấp project (Kotlin, AGP, Google Services)
    ├── settings.gradle.kts                  # Cấu hình repository và khai báo module (:app)
    ├── gradle.properties                    # Cấu hình JVM arguments và thuộc tính build Gradle
    ├── local.properties                     # Đường dẫn SDK Android cục bộ (sdk.dir)
    ├── gradlew                              # Script chạy Gradle Wrapper trên Linux/macOS
    ├── gradlew.bat                          # Script chạy Gradle Wrapper trên Windows
    ├── README.md                            # Bản sao tài liệu dự án trong thư mục làm việc
    │
    ├── gradle/
    │   ├── libs.versions.toml               # Version Catalog quản lý tập trung phiên bản thư viện
    │   └── wrapper/
    │       ├── gradle-wrapper.jar           # Executable wrapper của Gradle 8.10.2
    │       └── gradle-wrapper.properties    # Cấu hình URL tải Gradle 8.10.2
    │
    └── app/                                 # Module ứng dụng chính
        ├── build.gradle.kts                 # Cấu hình build module app (SDK 35, dependencies, packaging)
        ├── google-services.json             # File cấu hình kết nối Google Firebase (Database, Auth, Analytics)
        ├── proguard-rules.pro               # Cấu hình ProGuard / R8 tối ưu hóa mã nguồn
        │
        ├── build/outputs/apk/debug/
        │   └── app-debug.apk                # [OUTPUT] File cài đặt APK Debug hoàn chỉnh (~25.6 MB)
        │
        └── src/
            ├── test/java/com/example/doan/
            │   └── ExampleUnitTest.kt       # Bộ kiểm thử đơn vị (Unit Tests: Model, Pricing, Validation)
            │
            └── main/
                ├── AndroidManifest.xml      # File cấu hình quyền mạng, Application & 20 Activity
                │
                ├── java/com/example/doan/    # Mã nguồn Kotlin chuẩn hóa theo kiến trúc phân tầng
                │   │
                │   ├── model/               # 📦 Lớp dữ liệu thực thể (Data Models / POJO)
                │   │   ├── User.kt          # Thực thể User (đăng ký) và AppUser (đăng nhập)
                │   │   ├── Movie.kt         # Thực thể Movie cho slider trang chủ
                │   │   ├── Film.kt          # Thực thể Film (User) và FilmAdmin (Admin quản lý)
                │   │   ├── Cinema.kt        # Thực thể Cinema và cấu hình lịch chiếu trong tuần
                │   │   ├── Ticket.kt        # Thực thể Ticket, TicketOrder và ConfirmTicketItem
                │   │   ├── Event.kt         # Thực thể Event và EventAdmin
                │   │   └── Promotion.kt     # Thực thể Promotion và PromotionAdmin
                │   │
                │   ├── adapter/             # 🔄 Các bộ điều hợp danh sách (RecyclerView / ViewPager)
                │   │   ├── MovieAdapter.kt          # Adapter cho slider phim nổi bật trang chủ
                │   │   ├── FilmAdapter.kt           # Adapter danh sách phim & Poster slider tự động cuộn
                │   │   ├── CinemaAdapter.kt         # Adapter quản lý rạp chiếu & chọn khung giờ (Admin)
                │   │   ├── SeatAdapter.kt           # Adapter sơ đồ ghế ngồi (A1 - F5), khóa ghế đã đặt
                │   │   ├── TicketAdapter.kt         # Adapter hiển thị vé & ViewPager lịch sử vé
                │   │   ├── EventAdapter.kt          # Adapter hiển thị sự kiện cho khách hàng
                │   │   ├── EventAdminAdapter.kt     # Adapter quản trị sự kiện (Sửa / Xóa) cho Admin
                │   │   ├── PromotionAdapter.kt      # Adapter hiển thị khuyến mãi cho khách hàng
                │   │   └── PromotionAdminAdapter.kt # Adapter quản trị khuyến mãi (Sửa / Xóa) cho Admin
                │   │
                │   ├── ui/                  # 📱 Các Activity giao diện phân theo nhóm chức năng
                │   │   ├── auth/            # [Phân hệ Xác thực]
                │   │   │   ├── MainActivity.kt          # Màn hình đăng nhập & điều hướng Admin/User
                │   │   │   ├── RegisterActivity.kt      # Màn hình đăng ký tài khoản khách hàng
                │   │   │   └── ForgotPasswordActivity.kt# Quên mật khẩu, gửi mã OTP qua Gmail & đặt lại MK
                │   │   │
                │   │   ├── admin/           # [Phân hệ Quản trị viên (Admin)]
                │   │   │   ├── AdminActivity.kt             # Bảng điều khiển quản trị trung tâm
                │   │   │   ├── FilmManagerActivity.kt       # Thêm phim mới & cấu hình rạp, suất chiếu
                │   │   │   ├── ListFilmActivity.kt          # Danh sách toàn bộ phim và thao tác xóa phim
                │   │   │   ├── ConfirmationActivity.kt      # Duyệt đơn đặt vé ("Chờ xác nhận" -> "Đã xác nhận")
                │   │   │   ├── EventManagementActivity.kt   # Thêm, sửa, xóa sự kiện
                │   │   │   └── PromotionManagementActivity.kt# Thêm, sửa, xóa chương trình khuyến mãi
                │   │   │
                │   │   └── user/            # [Phân hệ Khách hàng (User)]
                │   │       ├── HomeActivity.kt          # Trang chủ (Banner, Slider phim, Drawer Menu)
                │   │       ├── FilmActivity.kt          # Xem phim đang chiếu & sắp chiếu theo ngày
                │   │       ├── SelectProvinceActivity.kt# Chọn Tỉnh / Thành phố
                │   │       ├── SelectCinemaActivity.kt  # Chọn Cụm rạp theo tỉnh thành đã chọn
                │   │       ├── BookingActivity.kt       # Chọn ngày trong tuần & khung giờ suất chiếu
                │   │       ├── SelectSeatActivity.kt    # Sơ đồ chọn ghế tương tác & tự động tính tiền
                │   │       ├── ConfirmActivity.kt       # Xác nhận thông tin vé & kích hoạt gửi mail
                │   │       ├── TicketActivity.kt        # Quản lý vé (Tab Vé đang chờ duyệt / Vé đã duyệt)
                │   │       ├── UserInfoActivity.kt      # Xem, cập nhật thông tin cá nhân hoặc xóa tài khoản
                │   │       ├── EventActivity.kt         # Danh sách sự kiện điện ảnh
                │   │       ├── PromotionActivity.kt     # Danh sách ưu đãi, khuyến mãi
                │   │       └── ShowInfoActivity.kt      # Xem chi tiết sự kiện hoặc khuyến mãi
                │   │
                │   └── utils/               # 🛠️ Các module tiện ích dùng chung
                │       ├── Constants.kt     # Hằng số Firebase node, danh sách Tỉnh/Rạp mặc định
                │       └── GMailSender.kt   # Module kết nối SMTP smtp.gmail.com:587 gửi thư tự động
                │
                └── res/                     # Tài nguyên giao diện (Layouts, Drawables, Values)
                    ├── color/               # Bảng màu XML (blue.xml, light_gray.xml)
                    ├── drawable/            # Ảnh nền, icon vector, hiệu ứng nút bo tròn (36 files)
                    ├── layout/              # 27 file giao diện XML của toàn bộ Activity & Item View
                    ├── menu/                # Menu điều hướng đáy (bottom_nav_menu.xml)
                    ├── mipmap-*/            # Bộ icon ứng dụng đa độ phân giải (ic_launcher, ic_cinema_logo)
                    ├── values/              # strings.xml, colors.xml, themes.xml (Material Themes)
                    ├── values-night/        # themes.xml hỗ trợ chế độ nền tối (Dark mode)
                    └── xml/                 # backup_rules.xml, data_extraction_rules.xml
```

---

## 📋 3. Bảng tra cứu tệp mã nguồn (Source Code Reference Table)

| Package | Tệp tin | Vai trò & Trách nhiệm chính |
| :--- | :--- | :--- |
| **`model`** | `User.kt` | Chứa class `User` (đăng ký) và `AppUser` (đăng nhập) |
| | `Movie.kt` | Định nghĩa phim slider trang chủ (`title`, `info`, `imageResId`) |
| | `Film.kt` | Định nghĩa `Film` (thông tin hiển thị) và `FilmAdmin` (lưu trữ đầy đủ kèm rạp) |
| | `Cinema.kt` | Cấu hình địa điểm rạp và lịch chiếu các thứ trong tuần |
| | `Ticket.kt` | Định nghĩa `Ticket`, `TicketOrder` (lưu node Ticker) và `ConfirmTicketItem` |
| | `Event.kt` | Định nghĩa đối tượng Sự kiện (`Event`, `EventAdmin`) |
| | `Promotion.kt` | Định nghĩa đối tượng Khuyến mãi (`Promotion`, `PromotionAdmin`) |
| **`adapter`** | `MovieAdapter.kt` | Adapter hiển thị ViewPager2 dạng slide phim nổi bật |
| | `FilmAdapter.kt` | Adapter danh sách phim `RecyclerView` và slider áp phích `PosterAdapter` |
| | `CinemaAdapter.kt` | Adapter chọn rạp và đánh dấu checkbox các slot chiếu |
| | `SeatAdapter.kt` | GridView Adapter ghế A1-F5, xử lý chọn/hủy và disable ghế đã đặt |
| | `TicketAdapter.kt` | Adapter hiển thị danh sách vé và FragmentStateAdapter chuyển tab |
| | `EventAdapter.kt` | Adapter danh sách sự kiện người dùng |
| | `EventAdminAdapter.kt`| Adapter quản lý danh sách sự kiện cho Admin |
| | `PromotionAdapter.kt`| Adapter danh sách ưu đãi người dùng |
| | `PromotionAdminAdapter.kt`| Adapter quản lý ưu đãi cho Admin |
| **`ui.auth`** | `MainActivity.kt` | Xác thực đăng nhập; điều hướng rẽ nhánh Admin (`admin/admin123`) hoặc User |
| | `RegisterActivity.kt` | Đăng ký tài khoản mới lên Firebase Node `Users` |
| | `ForgotPasswordActivity.kt` | Sinh mã OTP 6 ký tự ngẫu nhiên, gửi qua GMail và cập nhật mật khẩu mới |
| **`ui.admin`** | `AdminActivity.kt` | Dashboard trung tâm của quản trị viên |
| | `FilmManagerActivity.kt` | Thêm phim mới cùng lịch chiếu rạp vào Firebase Node `Films` |
| | `ListFilmActivity.kt` | Hiển thị tất cả phim hiện hành, hỗ trợ Admin xóa phim |
| | `ConfirmationActivity.kt`| Duyệt phiếu đặt vé từ trạng thái "Chờ xác nhận" sang "Đã xác nhận" |
| | `EventManagementActivity.kt` | Quản lý thêm, sửa, xóa sự kiện tại Node `Event` |
| | `PromotionManagementActivity.kt` | Quản lý thêm, sửa, xóa khuyến mãi tại Node `Promotion` |
| **`ui.user`** | `HomeActivity.kt` | Trang chủ hiển thị Banner, Slider trượt tự động và Menu Drawer |
| | `FilmActivity.kt` | Danh sách phim có lọc ngày khởi chiếu: Đang chiếu và Sắp chiếu |
| | `SelectProvinceActivity.kt` | Cho phép người dùng chọn Tỉnh / Thành phố |
| | `SelectCinemaActivity.kt` | Cho phép chọn Cụm rạp tương ứng với Tỉnh đã chọn |
| | `BookingActivity.kt` | Cho phép chọn ngày trong tuần (CN - T7) và hiển thị các suất chiếu tương ứng |
| | `SelectSeatActivity.kt` | Sơ đồ chọn ghế ma trận, tự động tính tổng tiền theo số ghế |
| | `ConfirmActivity.kt` | Xem lại hóa đơn vé, xác nhận đặt và tự động gửi email hóa đơn |
| | `TicketActivity.kt` | Quản lý danh sách vé với TabLayout: Vé chờ xác nhận / Vé đã xác nhận |
| | `UserInfoActivity.kt` | Xem thông tin cá nhân, cập nhật thông tin hoặc xóa tài khoản vĩnh viễn |
| | `EventActivity.kt` | Danh sách sự kiện dành cho khách hàng |
| | `PromotionActivity.kt` | Danh sách khuyến mãi dành cho khách hàng |
| | `ShowInfoActivity.kt` | Xem bài viết chi tiết sự kiện / khuyến mãi |
| **`utils`** | `Constants.kt` | Hằng số tập trung: Node Firebase, danh sách Tỉnh thành và Cụm rạp |
| | `GMailSender.kt` | Client gửi email giao thức SMTP Gmail (Port 587, STARTTLS) |

---

## 🛠️ 4. Bảng thông số công nghệ & Cấu hình thư viện

| Thành phần | Phiên bản | Mục đích sử dụng |
| :--- | :--- | :--- |
| **Android SDK** | `compileSdk 35`, `targetSdk 35`, `minSdk 30` | Tương thích tối ưu từ Android 11.0 (API 30) trở lên |
| **Kotlin** | `2.1.0` (JVM Target: 11) | Ngôn ngữ phát triển ứng dụng chính |
| **Gradle / AGP** | Gradle `8.10.2`, AGP `8.8.0` | Hệ thống tự động hóa build và đóng gói |
| **Google Firebase BoM** | `33.9.0` | Quản lý phiên bản thống nhất của các module Firebase |
| **Firebase Realtime Database**| `firebase-database-ktx` (BoM) | Cơ sở dữ liệu đám mây NoSQL thời gian thực |
| **Firebase Auth & Analytics** | BoM managed | Xác thực người dùng và phân tích dữ liệu ứng dụng |
| **UI Components** | `material:1.12.0`, `appcompat:1.7.0` | Giao diện chuẩn Material Design 3 |
| **RecyclerView / ViewPager2** | `recyclerview:1.4.0`, ViewPager2 | Danh sách cuộn mượt và slider banner/poster |
| **Image Loading** | `glide:4.15.1` | Tải, giải mã và lưu bộ nhớ đệm (cache) ảnh poster |
| **JSON Parser** | `gson:2.10.1` | Chuyển đổi dữ liệu đối tượng sang định dạng chuỗi JSON |
| **JavaMail API** | `android-mail:1.6.6`, `android-activation:1.6.6` | Gửi email thông báo tự động từ ứng dụng qua SMTP |
| **Unit Testing** | `junit:4.13.2`, `espresso:3.6.1` | Kiểm thử đơn vị tự động trên môi trường host |

---

## ⚡ 5. Hướng dẫn cài đặt & Thực thi dự án

### Yêu cầu tiên quyết
1. Máy tính cài đặt **Android Studio** (bản Ladybug 2024.2+ hoặc mới hơn).
2. **JDK:** Java 17, 21 hoặc 22 (sử dụng sẵn bộ JBR đi kèm Android Studio).
3. **Android SDK:** Đã cài đặt Android SDK Platform 35 và Build-Tools 35.

### Thao tác trên Android Studio
1. Mở Android Studio, chọn **Open** và trỏ đến thư mục:  
   `C:\Users\admin\Downloads\DOAN (5)\DOAN`
2. Chờ Android Studio chạy **Gradle Sync** tải các thư viện cần thiết.
3. Dự án đã được thiết lập sẵn **3 Edit Configurations (Run/Debug Configurations)** tại thanh công cụ trên cùng:
   - 📱 **`app`**: Cấu hình chạy ứng dụng Android trên Emulator hoặc thiết bị thật (Khởi chạy trực tiếp `MainActivity`).
   - 🧪 **`All Unit Tests`**: Chạy toàn bộ bộ kiểm thử đơn vị (`:app:testDebugUnitTest`).
   - 📦 **`Build Debug APK`**: Tự động biên dịch và tạo file APK (`:app:assembleDebug`).
4. Khởi động một thiết bị Android Emulator (API 30 trở lên) hoặc kết nối điện thoại Android qua cáp USB, chọn cấu hình **`app`** rồi nhấn **Run** (`Shift + F10`).

### Lệnh thực thi bằng dòng lệnh (Command Line)
Mở PowerShell tại thư mục `DOAN`:
```powershell
# Chạy toàn bộ Unit Tests
.\gradlew.bat testDebugUnitTest

# Đóng gói bản cài đặt APK Debug
.\gradlew.bat assembleDebug
```
Sau khi hoàn tất, file APK sẽ được tạo tại:  
📁 `DOAN/app/build/outputs/apk/debug/app-debug.apk`

### Cấu hình Firebase Realtime Database & Dữ liệu mẫu
Dự án sử dụng Google Firebase Realtime Database. Để hệ thống hoạt động ổn định và lưu trữ dữ liệu riêng:
1. Truy cập [Firebase Console](https://console.firebase.google.com/) và đăng nhập bằng tài khoản Google.
2. Bấm **Add project (Thêm dự án)** ➔ Nhập tên dự án (ví dụ: `AppBanVePhim`) ➔ Bấm Tiếp tục.
3. Thêm ứng dụng Android với Package Name: **`com.example.doan`** ➔ Tải file `google-services.json` về.
4. Chép đè file `google-services.json` vào thư mục: `DOAN/app/google-services.json`.
5. Vào **Build** ➔ **Realtime Database** ➔ Bấm **Create Database (Tạo CSDL)**.
6. Tại tab **Rules (Quy tắc)**, thiết lập quyền đọc ghi:
   ```json
   {
     "rules": {
       ".read": true,
       ".write": true
     }
   }
   ```
7. Bấm vào biểu tượng dấu 3 chấm `...` ở góc trên bên phải bảng dữ liệu ➔ Chọn **Import JSON** ➔ Chọn file [`sample_data.json`](file:///C:/Users/admin/Downloads/DOAN%20%285%29/DOAN/sample_data.json) có sẵn trong dự án để nạp toàn bộ danh sách phim, lịch chiếu, tài khoản mẫu và khuyến mãi!

---

## 🔒 6. Danh mục các điểm đã được tối ưu & khắc phục

1. **Chuẩn hóa cấu trúc gói (Clean Architecture):** Tách biệt rành mạch các tầng `model/`, `adapter/`, `ui/auth/`, `ui/admin/`, `ui/user/`, `utils/`.
2. **Dọn dẹp mã rác:** Đã xóa bỏ các file mồ côi `TEST.kt`, `adapterlistfilm.kt` cùng các file layout dư thừa `test.xml`, `movie_card.xml`.
3. **Sửa lỗi đăng nhập Admin:** Xử lý đăng nhập quản trị viên độc lập, loại bỏ lỗi hiển thị thông báo "Sai mật khẩu" khi Admin vào hệ thống thành công.
4. **Ngăn ngừa Crash ngày tháng:** Khắc phục triệt để lỗi crash `ParseException` bằng khối `try-catch` an toàn khi duyệt ngày khởi chiếu phim.
5. **An toàn kiểu dữ liệu Intent:** Thay thế các lệnh ép kiểu cưỡng bức `as String` bằng fallback an toàn `?: ""` tránh lỗi `NullPointerException`.
6. **Bổ sung quyền Manifest:** Khai báo đầy đủ `<uses-permission android:name="android.permission.INTERNET" />` và `android:usesCleartextTraffic="true"` cho phép hiển thị ảnh từ các nguồn HTTP.
7. **Kiểm thử tự động:** Mở rộng bộ Unit Test trong `ExampleUnitTest.kt` và đạt kết quả kiểm thử **PASS 100% (BUILD SUCCESSFUL)**.
8. **Tích hợp Cổng thanh toán VNPAY Sandbox:** Thêm màn hình [`VNPayPaymentActivity`](file:///C:/Users/admin/Downloads/DOAN%20%285%29/DOAN/app/src/main/java/com/example/doan/ui/user/VNPayPaymentActivity.kt) tích hợp Cổng thanh toán VNPAY Sandbox qua thẻ thử nghiệm NCB (`9704198526191432198` / `NGUYEN VAN A` / OTP: `123456`), tự động nạp đơn hàng, hỗ trợ sao chép số thẻ nhanh và xác nhận thanh toán trực tuyến.
9. **Khắc phục triệt để luồng đặt vé:** Căn chỉnh đồng bộ mã ca chiếu (`1..5`), tự động nhận diện ngày hiện tại và kích hoạt suất chiếu tương ứng trên toàn bộ 8 cụm rạp.
