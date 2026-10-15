# Bảng phân quyền chuẩn S4-09.1-v1

Người giao backlog chốt ngày 15/10/2026. Đây là tài liệu chuẩn độc lập; không tự cấp quyền bằng cách đọc bảng này. Admin tách nhiệm vụ.

✓: được phép; —: không được phép; Cá nhân: chỉ dữ liệu từ tài khoản đăng nhập; Ngoại lệ: vẫn phải đáp ứng luồng ngoại lệ có lý do và các điều kiện nghiệp vụ hiện có. API được bảo vệ: chưa đăng nhập 401, thiếu vai trò 403. Phiếu của người khác được che bằng 404 theo convention hiện tại. Đăng ký/tra cứu công khai giữ nguyên và không thuộc bảng API yêu cầu xác thực này.

## Thu phí và miễn giảm

| Chức năng | API | Admin | Quản lý | Thủ thư | Bạn đọc |
|---|---|:---:|:---:|:---:|:---:|
| Tìm bạn đọc để thu phí | `GET /api/v1/fee-payments/readers` | — | — | ✓ | — |
| Xem công nợ tại quầy thu phí | `GET /api/v1/fee-payments/readers/{readerId}` | — | — | ✓ | — |
| Thu toàn bộ phí | `POST /api/v1/fee-payments/readers/{readerId}/collect-full` | — | — | ✓ | — |
| Thu một phần phí | `POST /api/v1/fee-payments/readers/{readerId}/collect-partial` | — | — | ✓ | — |
| Xem và in lại biên lai | `GET /api/v1/fee-payments/{paymentId}/receipt` | — | — | ✓ | — |
| Xem chi tiết phí quá hạn | `GET /api/v1/overdue-fees/{feeId}` | ✓ | ✓ | ✓ | — |
| Lập đề nghị miễn giảm | `POST /api/v1/overdue-fees/{feeId}/reduction-requests` | — | — | ✓ | — |
| Xem đề nghị miễn giảm chờ duyệt | `GET /api/v1/overdue-fees/reduction-requests/pending` | — | ✓ | — | — |
| Duyệt miễn giảm | `POST /api/v1/overdue-fees/reduction-requests/{requestId}/approve` | — | ✓ | — | — |
| Từ chối miễn giảm | `POST /api/v1/overdue-fees/reduction-requests/{requestId}/reject` | — | ✓ | — | — |

Điều kiện bổ sung:
- Chỉ Thủ thư; không kế thừa quyền thu phí cho Admin hoặc Quản lý.

## Bạn đọc và thẻ

| Chức năng | API | Admin | Quản lý | Thủ thư | Bạn đọc |
|---|---|:---:|:---:|:---:|:---:|
| Xem hồ sơ cá nhân | `GET /api/v1/readers/me/profile` | — | — | — | Cá nhân |
| Cập nhật liên hệ cá nhân | `PUT /api/v1/readers/me/contact` | — | — | — | Cá nhân |
| Đổi mật khẩu cá nhân | `PUT /api/v1/readers/me/password` | — | — | — | Cá nhân |
| Xem danh sách bạn đọc | `GET /api/v1/readers` | ✓ | ✓ | ✓ | — |
| Xem hồ sơ bạn đọc | `GET /api/v1/readers/{id}` | ✓ | ✓ | ✓ | — |
| Xem lịch sử mượn của bạn đọc | `GET /api/v1/readers/{id}/loan-history` | ✓ | ✓ | ✓ | — |
| Xuất lịch sử mượn của bạn đọc | `GET /api/v1/readers/{id}/loan-history/export` | ✓ | ✓ | ✓ | — |
| Xem đề xuất khóa thẻ | `GET /api/v1/library-cards/lock-suggestions` | — | ✓ | — | — |
| Xem chi tiết đề xuất khóa thẻ | `GET /api/v1/library-cards/lock-suggestions/{id}` | — | ✓ | — | — |
| Chấp nhận đề xuất khóa thẻ | `POST /api/v1/library-cards/lock-suggestions/{id}/accept` | — | ✓ | — | — |
| Bỏ qua đề xuất khóa thẻ | `POST /api/v1/library-cards/lock-suggestions/{id}/ignore` | — | ✓ | — | — |
| Xem hồ sơ chờ duyệt | `GET /api/v1/library-cards/pending` | ✓ | ✓ | ✓ | — |
| Xem danh sách thẻ | `GET /api/v1/library-cards` | ✓ | ✓ | ✓ | — |
| Duyệt hồ sơ và cấp thẻ | `POST /api/v1/library-cards/{readerUserId}/approve` | ✓ | ✓ | ✓ | — |
| Từ chối hồ sơ | `POST /api/v1/library-cards/{readerUserId}/reject` | ✓ | ✓ | ✓ | — |
| Khóa thẻ | `POST /api/v1/library-cards/{cardId}/lock` | — | ✓ | — | — |
| Xem chi tiết thẻ | `GET /api/v1/library-cards/{cardId}` | ✓ | ✓ | ✓ | — |
| Mở khóa thẻ | `POST /api/v1/library-cards/{cardId}/unlock` | — | ✓ | — | — |
| Xem thẻ cá nhân | `GET /api/v1/library-cards/me` | — | — | — | Cá nhân |

Điều kiện bổ sung:
- Giữ nguyên validation, trạng thái tài nguyên và điều kiện nghiệp vụ hiện có; có quyền không đồng nghĩa mọi dữ liệu nhập đều được chấp nhận.

## Mượn và trả sách

| Chức năng | API | Admin | Quản lý | Thủ thư | Bạn đọc |
|---|---|:---:|:---:|:---:|:---:|
| Xem ngữ cảnh nhận sách từ đặt giữ | `GET /api/v1/reservations/{reservationId}/loan-context` | ✓ | ✓ | ✓ | — |
| Kiểm tra nhận sách tại quầy | `POST /api/v1/reservations/{reservationId}/pickup-check` | ✓ | ✓ | ✓ | — |
| Tạo phiếu từ một đơn đặt giữ | `POST /api/v1/reservations/{reservationId}/loan` | ✓ | Ngoại lệ có lý do | ✓ | — |
| Tạo phiếu từ nhiều đơn đặt giữ | `POST /api/v1/reservations/loans` | ✓ | Ngoại lệ có lý do | ✓ | — |
| Xem sách đang mượn cá nhân | `GET /api/v1/loans/me/borrowed-books` | — | — | — | Cá nhân |
| Kiểm tra và xử lý gia hạn cá nhân | `POST /api/v1/loans/me/borrowed-books/{itemId}/renewal-check` | — | — | — | Cá nhân |
| Xem sách đã trả cá nhân | `GET /api/v1/loans/me/returned-books` | — | — | — | Cá nhân |
| Tìm phiếu mượn | `GET /api/v1/loans/search` | ✓ | ✓ | ✓ | — |
| Xem sách quá hạn | `GET /api/v1/loans/overdue` | ✓ | ✓ | ✓ | — |
| Xem danh sách phiếu mượn | `GET /api/v1/loans` | ✓ | ✓ | ✓ | — |
| Xem điều kiện mượn | `GET /api/v1/loans/reader-eligibility` | ✓ | ✓ | ✓ | — |
| Kiểm tra điều kiện và ghi nhận từ chối | `POST /api/v1/loans/reader-eligibility/check` | ✓ | — | ✓ | — |
| Xem nhật ký từ chối mượn | `GET /api/v1/loans/rejections` | ✓ | ✓ | ✓ | — |
| Xem chi tiết từ chối mượn | `GET /api/v1/loans/rejections/{id}` | ✓ | ✓ | ✓ | — |
| Kiểm tra bản sách mượn trực tiếp | `POST /api/v1/loans/direct/items/preview` | ✓ | Ngoại lệ có lý do | ✓ | — |
| Tạo phiếu mượn trực tiếp | `POST /api/v1/loans/direct` | ✓ | Ngoại lệ có lý do | ✓ | — |
| Tra cứu tiếp nhận trả sách | `GET /api/v1/loans/return-lookup` | ✓ | ✓ | ✓ | — |
| Xác nhận trả sách | `POST /api/v1/loans/return-confirmation` | ✓ | — | ✓ | — |
| Xem chi tiết phiếu mượn | `GET /api/v1/loans/{loanId}` | ✓ | ✓ | ✓ | Cá nhân |
| Xem lịch sử liên hệ quá hạn | `GET /api/v1/loans/{loanId}/overdue-contacts` | ✓ | ✓ | ✓ | — |
| Ghi nhận liên hệ quá hạn | `POST /api/v1/loans/{loanId}/overdue-contacts` | ✓ | ✓ | ✓ | — |

Điều kiện bổ sung:
- Quản lý chỉ được xử lý ngoại lệ có lý do; các điều kiện không được bỏ qua vẫn phải hợp lệ.
- Bạn đọc chỉ xem được phiếu thuộc chính mình; tài nguyên khác được che bằng HTTP 404.

## Kho và sự cố sách

| Chức năng | API | Admin | Quản lý | Thủ thư | Bạn đọc |
|---|---|:---:|:---:|:---:|:---:|
| Chuyển bản sao sang sửa chữa | `POST /api/v1/book-copies/{id}/repair` | ✓ | — | ✓ | — |
| Xem lịch sử trạng thái bản sao | `GET /api/v1/book-copies/{id}/status-history` | ✓ | ✓ | ✓ | — |
| Thêm bản sao | `POST /api/v1/books/{bookId}/copies` | ✓ | — | ✓ | — |
| Xem trước mã vạch hàng loạt | `GET /api/v1/books/{bookId}/copies/bulk/preview` | ✓ | ✓ | ✓ | — |
| Thêm bản sao hàng loạt | `POST /api/v1/books/{bookId}/copies/bulk` | ✓ | — | ✓ | — |
| Xem bản sao của đầu sách | `GET /api/v1/books/{bookId}/copies` | ✓ | ✓ | ✓ | — |
| Xem tổng hợp bản sao | `GET /api/v1/books/{bookId}/copies/summary` | ✓ | ✓ | ✓ | — |
| Xem chi tiết bản sao | `GET /api/v1/book-copies/{id}` | ✓ | ✓ | ✓ | — |
| Sửa thông tin và vị trí bản sao | `PUT/PATCH /api/v1/book-copies/{id}` | ✓ | — | ✓ | — |
| Tìm sách để ghi nhận mất/hỏng | `GET /api/v1/book-incidents/open-items` | — | — | ✓ | — |
| Xem trước ghi nhận mất/hỏng | `GET /api/v1/book-incidents/preview` | — | — | ✓ | — |
| Ghi nhận sách mất/hỏng | `POST /api/v1/book-incidents` | — | — | ✓ | — |
| Xem lịch sử sự cố | `GET /api/v1/book-incidents` | ✓ | ✓ | ✓ | — |
| Xem chi tiết sự cố | `GET /api/v1/book-incidents/{id}` | ✓ | ✓ | ✓ | — |
| Xem ảnh sự cố | `GET /api/v1/book-incidents/{id}/images/{imageId}` | ✓ | ✓ | ✓ | — |
| Xem lịch sử điều chỉnh bồi thường | `GET /api/v1/book-incidents/{id}/adjustments` | ✓ | ✓ | ✓ | — |
| Xem trước điều chỉnh bồi thường | `GET /api/v1/book-incidents/{id}/adjustment-preview` | ✓ | ✓ | ✓ | — |
| Lập đề nghị điều chỉnh bồi thường | `POST /api/v1/book-incidents/{id}/adjustments` | ✓ | ✓ | ✓ | — |
| Xác nhận điều chỉnh bồi thường | `POST /api/v1/book-incidents/{id}/adjustments/{adjustmentId}/confirm` | ✓ | ✓ | — | — |
| Xem kho để chọn vị trí bản sao | `GET /api/v1/library-settings/warehouses` | ✓ | ✓ | ✓ | — |
| Xem kệ để chọn vị trí bản sao | `GET /api/v1/library-settings/shelves` | ✓ | ✓ | ✓ | — |

Điều kiện bổ sung:
- Chỉ rà soát API đọc vị trí phục vụ nghiệp vụ bản sao; các API cấu hình ngoài phạm vi.

## Đặt giữ

| Chức năng | API | Admin | Quản lý | Thủ thư | Bạn đọc |
|---|---|:---:|:---:|:---:|:---:|
| Xem đơn đặt giữ cá nhân | `GET /api/v1/reservations/mine` | — | — | — | Cá nhân |
| Hủy đơn đặt giữ cá nhân | `POST /api/v1/reservations/mine/{reservationId}/cancel` | — | — | — | Cá nhân |
| Đặt giữ một đầu sách | `POST /api/v1/books/{bookId}/reservations` | — | — | — | Cá nhân |
| Đặt giữ nhiều bản | `POST /api/v1/books/{bookId}/reservations/bulk` | — | — | — | Cá nhân |
| Xem hàng đợi đặt giữ | `GET /api/v1/books/{bookId}/reservations` | ✓ | ✓ | ✓ | — |
| Hủy đơn đặt giữ tại quầy | `POST /api/v1/reservations/{reservationId}/cancel` | ✓ | — | ✓ | — |
| Xem đơn sẵn sàng nhận | `GET /api/v1/reservations/ready-for-pickup` | ✓ | ✓ | ✓ | — |
| Xem chi tiết đơn sẵn sàng nhận | `GET /api/v1/reservations/ready-for-pickup/{reservationId}` | ✓ | ✓ | ✓ | — |
| Xử lý đơn đặt giữ quá hạn | `POST /api/v1/reservations/auto-cancel-overdue` | ✓ | ✓ | — | — |
| Xem lần tự hủy gần nhất | `GET /api/v1/reservations/auto-cancel-runs/latest` | ✓ | ✓ | — | — |
| Xem lịch sử tự hủy | `GET /api/v1/reservations/auto-cancel-runs` | ✓ | ✓ | — | — |
| Kích hoạt kiểm tra tự hủy | `POST /api/v1/reservations/auto-cancel-runs/trigger` | ✓ | ✓ | — | — |
| Xem đơn đã tự hủy trong 30 ngày | `GET /api/v1/reservations/auto-cancelled-last-30-days` | ✓ | ✓ | — | — |

Điều kiện bổ sung:
- Giữ nguyên validation, trạng thái tài nguyên và điều kiện nghiệp vụ hiện có; có quyền không đồng nghĩa mọi dữ liệu nhập đều được chấp nhận.

## Rà soát mới

Chỉ ADMIN được GET `/api/v1/permission-review`, GET/POST `/api/v1/permission-review/reports` và GET `/api/v1/permission-review/reports/{id}`. Ba vai trò còn lại 403; chưa đăng nhập 401. Không có API sửa quyền, sửa/xóa biên bản hoặc chạy giao dịch thử trên máy chủ sản phẩm.
