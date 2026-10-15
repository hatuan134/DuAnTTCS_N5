# Kết quả kiểm tra bản vá S4-09.1

Ngày kiểm tra: 15/10/2026. Dùng source trong `DuAnTTCS_N5(6).zip` làm đối chứng. Không thay dependency của project.

## Build

| Hạng mục | Kết quả |
|---|---|
| Backend Java 17 / Spring Boot 4.1.1, `mvn -DskipTests package` | Đạt; compile cả source/test và đóng gói JAR. Không đồng nghĩa đã khởi động với database |
| Frontend React 18 / TypeScript / Vite, `npm run build` | Đạt |
| Gói bản vá | Chỉ source, test, migration mới và hướng dẫn; không có cấu hình riêng, dependency, cache hoặc file build |

## Bộ kiểm thử mới

| Class | Tổng test | Lỗi assert | Lỗi thực thi | Skip |
|---|---:|---:|---:|---:|
| `CriticalPermissionApiTest` | 425 | 0 | 0 | 0 |
| `PermissionReviewControllerTest` | 3 | 0 | 0 | 0 |
| `PermissionReviewServiceTest` | 5 | 0 | 0 | 0 |
| `PermissionReviewInspectorTest` | 2 | 0 | 0 | 0 |
| `PermissionReviewSimulationTest` | 1 | 0 | 0 | 0 |
| `CriticalPermissionDatabaseTest` | 1 | 0 | 0 | 1 |

**436 test đạt, 1 test PostgreSQL chủ động skip** do chưa bật môi trường PostgreSQL thử riêng. Trong 425 test của `CriticalPermissionApiTest` có 420 lời gọi theo 84 API × (bốn vai trò + chưa đăng nhập), bốn luồng ngoại lệ Quản lý và một test đối chiếu danh mục. API PUT/PATCH được kiểm tra cả hai phương thức.

MockMvc dùng SecurityConfig, JwtAuthenticationFilter, validation và controller thật. Decoder JWT, repository người dùng và service nghiệp vụ được giả lập. Request bị từ chối được kiểm tra không gọi service nghiệp vụ. Các ca được phép nhận đúng mã 2xx của controller; đây không phải chứng minh toàn bộ nghiệp vụ SQL đã chạy.

`PermissionReviewInspectorTest` phát hiện quyền thừa và quyền thiếu từ mapping/annotation đã thay riêng trong test. `PermissionReviewSimulationTest` chứng minh Bạn đọc trước 200 → sau 403 và sổ thử không đổi sau từ chối; Thủ thư trước 403 → sau 200. Controller sai chỉ nằm ở `src/test`; không thêm endpoint hoặc profile sai quyền vào ứng dụng.

## Frontend và thông báo

- **49/49 test tập trung đạt**, gồm 8 test feature mới và các test FeedbackAlert/luồng cũ. Kiểm tra thành công, lỗi API, tệp sai, submit trùng, đóng sớm, cleanup, thông báo hết hạn ở 3000 ms, modal và lỗi validation cố định vẫn còn.
- **11/11 kiểm tra browser đạt** với Playwright và Chromium, API fixture: Admin xem bốn vai trò/lọc Thu phí; responsive 1366/768/390 px; đánh dấu sai lệch; JSON sai; thông báo ở 2999/3000 ms; nhập/xem biên bản mô phỏng; lỗi nhập và thử lại; ba vai trò còn lại truy cập trực tiếp bị từ chối, không phát sinh request dữ liệu rà soát và không thấy menu.
- Đã xem ảnh desktop/mobile phục vụ QA: bố cục sau khi animation hoàn tất không bị sidebar che, không cuộn ngang; đã thu gọn phân trang riêng trên màn hình mới để vừa 390 px. Không sửa component phân trang chung hoặc CSS của các màn hình cũ.
- Bộ test frontend toàn bộ sau bản vá: **532 test, 520 đạt, 12 lỗi có sẵn**. Bản gốc: **524 test, 512 đạt, 12 lỗi tương tự**. Các test cũ này nạp `feature.tsx` bằng CommonJS giả lập nhưng chưa mock import `../../app/loadablePage` sau cơ chế lazy load sẵn có. Không sửa chúng ngoài phạm vi backlog.

## Hồi quy backend cũ và đối chứng source gốc

Chạy cùng 14 class kiểm thử cũ liên quan quyền/thu phí/bạn đọc/kho/đặt giữ/trả sách trên cả source gốc và bản vá: **96 test, 63 đạt, 33 lỗi khởi tạo context**, không có lỗi assert. Hai bản có cùng các lỗi thiếu mock `ReadyPickupNotificationService` trong các test sau:

| Class cũ | Số lỗi context |
|---|---:|
| `BookReservationControllerTest` | 13 |
| `ReaderLoanOwnershipControllerTest` | 5 |
| `ReaderReservationCancellationControllerTest` | 4 |
| `ReturnConfirmationControllerTest` | 6 |
| `StaffReservationCancellationControllerTest` | 5 |

Đây là lỗi cấu hình test đã có trong ZIP, không phải kết luận API sản phẩm hỏng. Bộ kiểm thử mới cung cấp các bean giả lập còn thiếu và đã kiểm tra lại toàn bộ 84 API trong phạm vi, nhưng không chỉnh sửa các test cũ ngoài phạm vi chỉ để đổi kết quả tổng.

## Những phần chưa kiểm chứng bằng PostgreSQL thật

Môi trường tạo bản vá không có PostgreSQL 15/Docker đang chạy. Vì vậy:

- Migration V58 và `demo-accounts.sql` đã kiểm tra cấu trúc, chưa thực thi trên PostgreSQL tại đây.
- `CriticalPermissionDatabaseTest` đã compile, chưa chạy: đăng nhập thật bốn vai trò, JWT ký thật, so sánh dữ liệu SQL sau yêu cầu cấm, thu phí đúng quyền và lưu biên bản thật.
- Chưa chạy E2E toàn hệ thống với dữ liệu hiện có trên máy người dùng; các kiểm tra browser dùng API fixture, không thay thế kiểm thử database.
- Chưa rà soát các phân hệ báo cáo, cấu hình, gửi thông báo và quản trị tài khoản; không tự nhận AC1–AC3 hoàn tất toàn hệ thống.

Hướng dẫn bật và chạy database test nằm trong README cùng thư mục. Không ghi nhận trạng thái test PostgreSQL là đạt nếu chưa thực hiện bước này.
