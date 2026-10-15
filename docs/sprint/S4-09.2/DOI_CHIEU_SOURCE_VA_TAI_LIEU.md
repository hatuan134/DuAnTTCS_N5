# Đối chiếu source, tài liệu và kiểm thử

## Căn cứ

Source `DuAnTTCS_N5(7).zip` là nguồn sự thật. Mở rộng màn hình/service/repository/DTO và JSON policy S4-09.1 đã có. Không tạo dự án hay cơ chế rà soát song song. Các hướng dẫn sprint trong source mô tả nghiệp vụ, còn bảng S4-09.1 chỉ bao phủ 84 hàm. Phần quyền còn thiếu đã được PO chốt bằng bảng S4-09.2.

| Phân hệ | Hàm | API (method + path) | Hàm đã có ở lát 1 | Hàm bổ sung |
|---|---:|---:|---:|---:|
| Thu phí và miễn giảm | 10 | 10 | 10 | 0 |
| Bạn đọc và thẻ | 21 | 21 | 19 | 2 |
| Mượn và trả sách | 21 | 21 | 21 | 0 |
| Kho và sự cố sách | 21 | 22 | 21 | 0 |
| Đặt giữ | 13 | 13 | 13 | 0 |
| Tài khoản nhân sự | 5 | 5 | 0 | 5 |
| Nhật ký hoạt động | 6 | 6 | 0 | 6 |
| Danh mục và biên mục | 21 | 21 | 0 | 21 |
| Chính sách và cấu hình | 25 | 25 | 0 | 25 |
| Email nhắc và thông báo | 11 | 11 | 0 | 11 |
| Thống kê và báo cáo | 6 | 6 | 0 | 6 |
| Xác thực và tài khoản cá nhân | 8 | 8 | 0 | 8 |
| Tra cứu và đăng ký công khai | 12 | 12 | 0 | 12 |
| Rà soát phân quyền | 10 | 10 | 0 | 10 |

Tổng: 84 hàm cũ + 100 hàm đã có nhưng chưa thuộc policy lát 1 + 6 API điều phối/độ bao phủ/xác nhận/xuất mới = 190 hàm, 191 API. 100 hàm bổ sung gồm 4 API rà soát đã có ở lát 1 nhưng chưa tự nằm trong policy.

## Đối chiếu chức năng

| Nhóm chức năng/tài liệu nguồn | API/chức năng đối chiếu | Kết quả |
|---|---|---|
| Các hướng dẫn S1 tài khoản, danh mục, cấu hình, nhật ký | UserManagement, Author, Category, CardType, LibraryConfiguration, AuditLog | Bổ sung policy và dữ liệu thử cho xem/thêm/sửa/xóa/đổi trạng thái. |
| S2 kho, đặt giữ, ảnh bìa, email nhận sách | BookCopy, BookReservation, BookCover, ReadyPickupNotification | Giữ phần lát 1; bổ sung ảnh và email; đọc ảnh dùng ảnh PNG hợp lệ. |
| S3 mượn/trả, cá nhân, lịch sử, ngoại lệ | Loan, Reader, LibraryCard, OverdueContact | Giữ quyền S4-09.1; kiểm tra hai chủ sở hữu, JWT thật, ngoại lệ có nợ thử. |
| S4 phí, sự cố, khóa thẻ, nhắc trả, báo cáo | FeePayment, FeeReduction, BookIncident, LoanReminder, LoanReport, OverdueDebtReport | Bao phủ quyền đã khai báo, sửa lỗi phụ thuộc PostgreSQL khiến vai trò hợp lệ không thao tác được. |
| Đăng nhập, refresh, quên/đặt mật khẩu, công khai | Auth, InitialPassword, PasswordReset, PublicOverview, các API books/public và readers/register | Không coi public là API bị bỏ; kiểm tra dữ liệu và token hợp lệ. |
| Rà soát quyền | PermissionReviewController | Mười API quản trị được kiểm thử cả 4 vai trò và anonymous. Bộ chạy lồng nhau được tắt riêng trong worker; điều phối tiến trình được kiểm chứng riêng. |

## API không thấy màn hình gọi trực tiếp

Các GET chi tiết tác giả, thể loại, loại thẻ được giữ theo lựa chọn 3A, có quyền nhân sự và bộ thử API trực tiếp; không xóa vì thiếu nút UI. Chín GET đọc danh mục trước đây chỉ yêu cầu đăng nhập, đã bổ sung guard để Bạn đọc nhận 403.

Runtime phát hiện thêm `PUT /api/v1/library-cards/lock-suggestions/settings` dùng annotation tên đầy đủ nên không nằm trong lượt quét tĩnh ban đầu. API này được ghi vào policy và kiểm thử; giữ quyền chỉ Quản lý như source. Không gán quyền này cho Admin.

## Cơ chế phát hiện thiếu kiểm thử

Inspector đọc RequestMappingHandlerMapping của ứng dụng đang chạy, kiểm tra method/path/handler và cả SecurityFilterChain + @PreAuthorize. API mới thiếu policy xuất hiện “Chưa xác định”, được đưa vào tổng số, làm chặn xác nhận. Không suy ra việc kiểm thử thành công từ việc có nút trên UI.

Coverage đối chiếu từng method/path với bốn vai trò, anonymous, dữ liệu cá nhân và luồng ngoại lệ; báo rõ từng ca thiếu. API nhiều phương thức được kiểm tra riêng PUT/PATCH. Chỉ chấp nhận hoàn tất khi kết quả v2 của bộ chạy máy chủ trên database riêng có đủ ca, tất cả assertion đạt, dấu vân tay hiện tại khớp và ma trận không còn lệch/chưa xác định. Nhập JSON thủ công hoặc mô phỏng không tự tạo quyền xác nhận.

Dấu vân tay bao gồm policy, guard/mapping runtime, bytecode controller/service/security/repository/DTO/config/entity, migration SQL và lịch sử khắc phục. Source thay đổi phải khởi động lại backend rồi chạy lại kiểm thử. Biên bản cũ vẫn đọc được và giữ nguyên.
