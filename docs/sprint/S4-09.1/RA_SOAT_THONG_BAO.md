# Rà soát thông báo toàn hệ thống — ngoại lệ được cho phép

## Phát hiện từ source đã upload

- `frontend/src/components/ui/FeedbackAlert.tsx` đã định nghĩa một hằng `FEEDBACK_DURATION_MS = 3000`, dùng timer và cleanup. Cập nhật callback không kéo dài thời gian; đổi nội dung/tone tạo một thông báo mới; đóng sớm và unmount hủy timer.
- Có 73 tệp source gốc tham chiếu FeedbackAlert, tính cả component chung. Không có `alert()` / `window.alert()` cho thông báo tạm thời trong source React được rà soát.
- `frontend/public/threeui/bestsellers-book-showcase.html` có một toast độc lập dùng cùng thời lượng 3000 ms và ẩn trực tiếp, không có animation kết thúc kéo dài thời gian tồn tại. Đây là trang HTML tĩnh sẵn có, không phải tạo thêm hệ thống toast trong bản vá.
- Các timer còn lại phục vụ debounce, hủy request, điều hướng, hạn thẻ và thu hồi object URL/in biên lai. Không đổi chúng về 3000 ms vì chúng không phải thông báo.
- Lỗi cố định dưới input, trạng thái thiếu quyền/không tải được nội dung, kết quả giao dịch đã lưu, hộp thoại xác nhận và modal không phải thông báo tạm thời. Không xóa chúng theo timer.

## Danh sách khu vực đã rà soát trong source gốc

| Feature | Số file TSX dùng FeedbackAlert | Cơ chế/nhận định |
|---|---:|---|
| `public-home` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s1-00-dashboard` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s1-01-auth-login` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s1-02-user-management` | 2 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s1-03-reader-registration` | 5 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s1-04-library-card` | 3 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s1-05-borrow-policy` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s1-06-change-password` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s1-07-password-reset` | 2 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s1-08-catalog` | 4 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s1-09-library-config` | 2 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s1-10-audit-log` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s2-02-book-copies` | 6 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s2-07-reservations` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s2-08-my-reservations` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s2-09-ready-pickup` | 9 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s2-10-book-cover` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s3-01-loans` | 3 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s3-02-direct-loans` | 3 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s3-04-my-borrowed-books` | 2 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s3-06-auto-cancellations` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s3-07-returns` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s3-09-overdue-loans` | 2 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s4-01-fee-reduction` | 3 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s4-02-fee-payments` | 2 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s4-03-book-incidents` | 4 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s4-04-card-lock-suggestions` | 3 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s4-05-loan-reminders` | 2 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s4-07-loan-report` | 1 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |
| `s4-08-overdue-debt-export` | 2 | FeedbackAlert chung; form/luồng xử lý giữ thông tin cố định cần thiết. |

Thêm: `components/ui/Input.tsx` giữ lỗi validation cố định; `components/public/PublicLayout.tsx` và các component trang chủ có trạng thái nội dung/fallback không cần thông báo cho mỗi lần tải ảnh hay số liệu. `BookCoverImage.tsx` giữ fallback ảnh bìa khi tải thất bại, không phải toast thao tác người dùng.

## File/luồng cũ đã sửa

**Không có file thông báo cũ cần sửa trong bản vá này.** Source hiện tại đã có cơ chế tập trung 3000 ms và phản hồi thao tác trong các luồng tìm được. Không viết lại code đang đúng hoặc thay timer của công việc khác để tạo thay đổi không cần thiết.

Hai file cũ được thay thế (`SecurityConfig.java`, `frontend/src/app/navigation.ts`) thuộc backlog phân quyền, không phải ngoại lệ thông báo. Những thay đổi thông báo mới nằm hoàn toàn trong feature `s4-09-permission-review` và dùng FeedbackAlert hiện có.

## Luồng đã kiểm chứng tự động

- Component chung: thành công, lỗi, cảnh báo, thông tin; còn ở 2999 ms, ẩn ở 3000 ms; đổi message/tone; re-render không kéo dài timer; đóng sớm; cleanup khi unmount.
- ADMIN: khóa tài khoản, kết quả thành công/lỗi.
- LIBRARY_MANAGER: đổi trạng thái chính sách loại thẻ, kết quả thành công/lỗi.
- LIBRARIAN: mã vạch sai, tiếp nhận trả/giữ sách cho hàng đợi, kết quả lưu và lỗi thao tác; thông báo hết hạn nhưng chi tiết cố định còn nguyên.
- READER: đặt giữ thành công/lỗi, tải danh sách đơn, lỗi dữ liệu; kết quả/khả năng thử lại vẫn có sau khi thông báo ẩn.
- Luồng cũ: tự hủy đặt giữ; lỗi bộ lọc/catalog công khai; xuất lịch sử mượn; thử lại nhắc email; thông báo sự cố sách.
- Feature mới: nhập tệp hợp lệ/không hợp lệ, lỗi API, chặn submit trùng, đóng thông báo giữ nguyên modal biên bản và lỗi validation cố định. Khi đóng modal sớm, thông báo của modal được xóa cùng để không xuất hiện lại và khởi động lại timer ở ngoài modal.

Đây là kiểm chứng bằng unit/component test; script browser QA kiểm tra thêm feature mới với API fixture. Không tự nhận đã chạy mọi luồng cũ bằng trình duyệt với PostgreSQL thật. Kết quả cụ thể và giới hạn được ghi trong `KET_QUA_KIEM_TRA.md`.
