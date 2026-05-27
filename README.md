# AuctionPlatformJava - Hệ thống đấu giá trực tuyến

## Mô tả bài toán và phạm vi

AuctionPlatformJava là ứng dụng đấu giá trực tuyến gồm server HTTP/WebSocket và client desktop JavaFX. Hệ thống hỗ trợ người bán đăng sản phẩm, mở phiên đấu giá, người mua đặt giá, theo dõi lịch sử đấu giá và nhận cập nhật tự động khi dữ liệu thay đổi.

Phạm vi hiện tại tập trung vào chạy local hoặc trong mạng nội bộ:

- Quản lý tài khoản theo vai trò `Bidder`, `Seller`, `Admin`.
- Quản lý sản phẩm đấu giá, phiên đấu giá và trạng thái phiên.
- Đặt giá thủ công, auto-bid, lịch sử bid và cập nhật realtime qua WebSocket.
- Lưu dữ liệu bằng SQLite trong file `auction_data.db`.
- Chưa tích hợp cổng thanh toán thật, email/SMS hay triển khai production cloud.

## Công nghệ, môi trường và yêu cầu cài đặt

### Công nghệ sử dụng

- Java 21.
- Gradle Wrapper 9.5.0.
- JavaFX 21 cho giao diện desktop.
- Javalin 6.4.0 cho REST API và WebSocket server.
- SQLite JDBC cho lưu trữ dữ liệu.
- Jackson, Gson và OkHttp cho JSON/HTTP client.
- JUnit 5 cho kiểm thử.
- GitHub Actions cho CI.

### Yêu cầu môi trường

- Cài JDK 21 và đặt `JAVA_HOME` trỏ tới JDK 21.
- Không cần cài Gradle riêng vì dự án dùng Gradle Wrapper.
- Windows: dùng `gradlew.bat`.
- Linux/macOS: dùng `./gradlew`; nếu file chưa có quyền chạy, chạy:

```bash
chmod +x ./gradlew
```

## Cấu trúc thư mục

```text
AuctionPlatformJava/
├── client/                 # JavaFX desktop client, FXML, CSS, REST client
├── server/                 # Javalin server, REST/WebSocket API, SQLite repositories
├── shared/                 # Model, service nghiệp vụ, repository interface, exception, test
├── gradle/wrapper/         # Gradle Wrapper
├── .github/workflows/      # GitHub Actions CI
├── build.gradle            # Cấu hình Gradle chung
├── settings.gradle         # Khai báo module shared/server/client
├── gradlew                 # Gradle Wrapper cho Linux/macOS
└── gradlew.bat             # Gradle Wrapper cho Windows
```

## Câu lệnh chạy chương trình

Tất cả lệnh dưới đây chạy từ thư mục gốc của dự án.

### Kiểm tra và build

Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

Linux/macOS:

```bash
./gradlew test
./gradlew build
```

### Chạy server

Server mặc định chạy tại `http://localhost:8080/api`.

Windows PowerShell:

```powershell
.\gradlew.bat :server:run
```

Linux/macOS:

```bash
./gradlew :server:run
```

Có thể đổi port bằng tham số dòng lệnh:

Windows PowerShell:

```powershell
.\gradlew.bat :server:run --args="8081"
```

Linux/macOS:

```bash
./gradlew :server:run --args='8081'
```

### Chạy client

Mở terminal mới, giữ server đang chạy, sau đó chạy client.

Windows PowerShell:

```powershell
.\gradlew.bat :client:run
```

Linux/macOS:

```bash
./gradlew :client:run
```

Client mặc định kết nối tới `http://localhost:8080/api`. Nếu server chạy ở địa chỉ khác, nhập URL server ở màn hình đăng nhập.

## Hướng dẫn chạy Server/Client theo thứ tự

1. Mở terminal tại thư mục gốc dự án.
2. Chạy server bằng `.\gradlew.bat :server:run` trên Windows hoặc `./gradlew :server:run` trên Linux/macOS.
3. Chờ log server báo đã khởi động tại `http://localhost:8080/api`.
4. Mở terminal thứ hai.
5. Chạy client bằng `.\gradlew.bat :client:run` trên Windows hoặc `./gradlew :client:run` trên Linux/macOS.
6. Đăng nhập hoặc đăng ký tài khoản mới để sử dụng.

Tài khoản admin mặc định được tạo khi server khởi động lần đầu:

- Username: `admin`
- Password: `admin`

## Danh sách chức năng đã hoàn thành

- Đăng ký, đăng nhập và xác thực bằng token.
- Phân quyền theo vai trò `Bidder`, `Seller`, `Admin`.
- Tạo, sửa, xóa và xem danh sách sản phẩm đấu giá.
- Hỗ trợ nhiều loại sản phẩm bằng kế thừa và factory: đồ điện tử, tác phẩm nghệ thuật, phương tiện, loại khác.
- Người bán mở phiên đấu giá cho sản phẩm.
- Admin có thể bắt đầu, kết thúc sớm hoặc hủy phiên đấu giá.
- Đặt giá thủ công với kiểm tra giá hợp lệ, số dư và trạng thái phiên.
- Auto-bid với giới hạn giá tối đa và cooldown 5 giây giữa các lượt auto-bid.
- Chống sniping: tự gia hạn phiên khi có bid trong những giây cuối.
- Tự động đóng phiên đấu giá khi hết thời gian.
- Cập nhật trạng thái `OPEN`, `RUNNING`, `FINISHED`, `PAID`, `CANCELED`.
- Cập nhật danh sách và màn hình bid realtime qua WebSocket.
- Xem lịch sử bid và biểu đồ diễn biến giá.
- Quản lý số dư người dùng, nạp/rút tiền và thanh toán sau khi kết thúc đấu giá.
- Lưu dữ liệu bằng SQLite.
- Kiểm thử tự động bằng JUnit 5.
- CI GitHub Actions chạy `./gradlew test` trên Ubuntu.
