# TTTT Cinema

Ứng dụng Android đặt vé xem phim. Mã nguồn Android, Firebase CLI, Cloud Functions, hướng dẫn build, test và cấu hình thanh toán nằm trong [DOAN](DOAN/README.md).

Firebase project mới: `tttt-cinema-20261020` · Android package: `com.example.doan`.

Tính năng hiện có: tìm phim và xem chi tiết, chọn ngày/rạp/suất/ghế, giữ ghế ở backend, tạo URL/QR VNPAY Sandbox, đăng nhập Firebase, cấp quyền staff/admin qua custom claims và xác thực QR vé một lần. Khi VNPAY xác nhận thanh toán, QR thanh toán trên màn hình đơn được thay bằng QR vé đã ký.

Danh mục phim/rạp/suất là dữ liệu mẫu. Production chưa sẵn sàng cho đến khi Realtime Database được tạo, Cloud Functions được deploy và IPN URL được cấu hình trên VNPAY Merchant Admin. Xem README bên trong `DOAN` để hoàn tất các bước này.
