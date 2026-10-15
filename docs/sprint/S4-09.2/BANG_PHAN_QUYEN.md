# Bảng quyền đã chốt — S4-09.2

PO xác nhận “Chốt bảng” ngày 15/10/2026. Giữ Admin tách nhiệm vụ theo S4-09.1; 1B đề xuất theo source, 2A database thử riêng PostgreSQL 5433, 3A giữ API chưa có màn hình gọi trực tiếp.

Mỗi API được đếm theo **phương thức HTTP + đường dẫn**: 190 hàm xử lý, 191 API vì cập nhật bản sao có cả PUT và PATCH. Bảng dưới ghi riêng từng phương thức. Có 19 API công khai; chúng được kiểm tra cả khi chưa đăng nhập với dữ liệu hợp lệ. `OWN` còn kiểm tra thay mã định danh; `OVERRIDE` phải có vi phạm thực tế và lý do hợp lệ.

Admin nhận 405 tại ba API sửa/xóa nhật ký vì nhật ký bất biến; các vai trò khác nhận 403. API ngoài quyền nhận 403; API cá nhân trỏ tài nguyên của người khác nhận 404 để không lộ sự tồn tại.

Các điều kiện nghiệp vụ, validation, hiệu lực tài khoản/thẻ và cơ chế khóa vẫn áp dụng sau cổng vai trò. Ma trận trên UI phân biệt quyền theo tài liệu với cổng quyền runtime; kiểm thử HTTP xác minh phần điều kiện dữ liệu.

## Thu phí và miễn giảm

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `GET /api/v1/fee-payments/readers` | Tìm bạn đọc để thu phí | Không được phép | Không được phép | Cho phép | Không được phép | Chỉ Thủ thư; không kế thừa quyền thu phí cho Admin hoặc Quản lý. |
| `GET /api/v1/fee-payments/readers/{readerId}` | Xem công nợ tại quầy thu phí | Không được phép | Không được phép | Cho phép | Không được phép | Chỉ Thủ thư; không kế thừa quyền thu phí cho Admin hoặc Quản lý. |
| `POST /api/v1/fee-payments/readers/{readerId}/collect-full` | Thu toàn bộ phí | Không được phép | Không được phép | Cho phép | Không được phép | Chỉ Thủ thư; không kế thừa quyền thu phí cho Admin hoặc Quản lý. |
| `POST /api/v1/fee-payments/readers/{readerId}/collect-partial` | Thu một phần phí | Không được phép | Không được phép | Cho phép | Không được phép | Chỉ Thủ thư; không kế thừa quyền thu phí cho Admin hoặc Quản lý. |
| `GET /api/v1/fee-payments/{paymentId}/receipt` | Xem và in lại biên lai | Không được phép | Không được phép | Cho phép | Không được phép | Chỉ Thủ thư; không kế thừa quyền thu phí cho Admin hoặc Quản lý. |
| `GET /api/v1/overdue-fees/{feeId}` | Xem chi tiết phí quá hạn | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/overdue-fees/{feeId}/reduction-requests` | Lập đề nghị miễn giảm | Không được phép | Không được phép | Cho phép | Không được phép |  |
| `GET /api/v1/overdue-fees/reduction-requests/pending` | Xem đề nghị miễn giảm chờ duyệt | Không được phép | Cho phép | Không được phép | Không được phép |  |
| `POST /api/v1/overdue-fees/reduction-requests/{requestId}/approve` | Duyệt miễn giảm | Không được phép | Cho phép | Không được phép | Không được phép |  |
| `POST /api/v1/overdue-fees/reduction-requests/{requestId}/reject` | Từ chối miễn giảm | Không được phép | Cho phép | Không được phép | Không được phép |  |

## Bạn đọc và thẻ

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `GET /api/v1/readers/me/profile` | Xem hồ sơ cá nhân | Không được phép | Không được phép | Không được phép | Chỉ dữ liệu của mình |  |
| `PUT /api/v1/readers/me/contact` | Cập nhật liên hệ cá nhân | Không được phép | Không được phép | Không được phép | Chỉ dữ liệu của mình |  |
| `PUT /api/v1/readers/me/password` | Đổi mật khẩu cá nhân | Không được phép | Không được phép | Không được phép | Chỉ dữ liệu của mình |  |
| `GET /api/v1/readers` | Xem danh sách bạn đọc | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/readers/{id}` | Xem hồ sơ bạn đọc | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/readers/{id}/loan-history` | Xem lịch sử mượn của bạn đọc | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/readers/{id}/loan-history/export` | Xuất lịch sử mượn của bạn đọc | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/library-cards/lock-suggestions` | Xem đề xuất khóa thẻ | Không được phép | Cho phép | Không được phép | Không được phép |  |
| `GET /api/v1/library-cards/lock-suggestions/{id}` | Xem chi tiết đề xuất khóa thẻ | Không được phép | Cho phép | Không được phép | Không được phép |  |
| `POST /api/v1/library-cards/lock-suggestions/{id}/accept` | Chấp nhận đề xuất khóa thẻ | Không được phép | Cho phép | Không được phép | Không được phép |  |
| `POST /api/v1/library-cards/lock-suggestions/{id}/ignore` | Bỏ qua đề xuất khóa thẻ | Không được phép | Cho phép | Không được phép | Không được phép |  |
| `GET /api/v1/library-cards/pending` | Xem hồ sơ chờ duyệt | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/library-cards` | Xem danh sách thẻ | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/library-cards/{readerUserId}/approve` | Duyệt hồ sơ và cấp thẻ | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/library-cards/{readerUserId}/reject` | Từ chối hồ sơ | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/library-cards/{cardId}/lock` | Khóa thẻ | Không được phép | Cho phép | Không được phép | Không được phép |  |
| `GET /api/v1/library-cards/{cardId}` | Xem chi tiết thẻ | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/library-cards/{cardId}/unlock` | Mở khóa thẻ | Không được phép | Cho phép | Không được phép | Không được phép |  |
| `GET /api/v1/library-cards/me` | Xem thẻ cá nhân | Không được phép | Không được phép | Không được phép | Chỉ dữ liệu của mình |  |
| `GET /api/v1/library-cards/lock-suggestions/settings` | Cấu hình đề xuất khóa thẻ | Không được phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/library-cards/lock-suggestions/settings` | Cập nhật ngưỡng đề xuất khóa thẻ | Không được phép | Cho phép | Không được phép | Không được phép | Giữ quyền Quản lý thư viện duy nhất của cấu hình đề xuất khóa thẻ theo source và nguyên tắc tách nhiệm vụ đã chốt. |

## Mượn và trả sách

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `GET /api/v1/reservations/{reservationId}/loan-context` | Xem ngữ cảnh nhận sách từ đặt giữ | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/reservations/{reservationId}/pickup-check` | Kiểm tra nhận sách tại quầy | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/reservations/{reservationId}/loan` | Tạo phiếu từ một đơn đặt giữ | Cho phép | Chỉ luồng ngoại lệ có lý do | Cho phép | Không được phép | Quản lý chỉ được xử lý ngoại lệ có lý do; các điều kiện không được bỏ qua vẫn phải hợp lệ. |
| `POST /api/v1/reservations/loans` | Tạo phiếu từ nhiều đơn đặt giữ | Cho phép | Chỉ luồng ngoại lệ có lý do | Cho phép | Không được phép | Quản lý chỉ được xử lý ngoại lệ có lý do; các điều kiện không được bỏ qua vẫn phải hợp lệ. |
| `GET /api/v1/loans/me/borrowed-books` | Xem sách đang mượn cá nhân | Không được phép | Không được phép | Không được phép | Chỉ dữ liệu của mình |  |
| `POST /api/v1/loans/me/borrowed-books/{itemId}/renewal-check` | Kiểm tra và xử lý gia hạn cá nhân | Không được phép | Không được phép | Không được phép | Chỉ dữ liệu của mình |  |
| `GET /api/v1/loans/me/returned-books` | Xem sách đã trả cá nhân | Không được phép | Không được phép | Không được phép | Chỉ dữ liệu của mình |  |
| `GET /api/v1/loans/search` | Tìm phiếu mượn | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/loans/overdue` | Xem sách quá hạn | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/loans` | Xem danh sách phiếu mượn | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/loans/reader-eligibility` | Xem điều kiện mượn | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/loans/reader-eligibility/check` | Kiểm tra điều kiện và ghi nhận từ chối | Cho phép | Không được phép | Cho phép | Không được phép |  |
| `GET /api/v1/loans/rejections` | Xem nhật ký từ chối mượn | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/loans/rejections/{id}` | Xem chi tiết từ chối mượn | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/loans/direct/items/preview` | Kiểm tra bản sách mượn trực tiếp | Cho phép | Chỉ luồng ngoại lệ có lý do | Cho phép | Không được phép | Quản lý chỉ được xử lý ngoại lệ có lý do; các điều kiện không được bỏ qua vẫn phải hợp lệ. |
| `POST /api/v1/loans/direct` | Tạo phiếu mượn trực tiếp | Cho phép | Chỉ luồng ngoại lệ có lý do | Cho phép | Không được phép | Quản lý chỉ được xử lý ngoại lệ có lý do; các điều kiện không được bỏ qua vẫn phải hợp lệ. |
| `GET /api/v1/loans/return-lookup` | Tra cứu tiếp nhận trả sách | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/loans/return-confirmation` | Xác nhận trả sách | Cho phép | Không được phép | Cho phép | Không được phép |  |
| `GET /api/v1/loans/{loanId}` | Xem chi tiết phiếu mượn | Cho phép | Cho phép | Cho phép | Chỉ dữ liệu của mình | Bạn đọc chỉ xem được phiếu thuộc chính mình; tài nguyên khác được che bằng HTTP 404. |
| `GET /api/v1/loans/{loanId}/overdue-contacts` | Xem lịch sử liên hệ quá hạn | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/loans/{loanId}/overdue-contacts` | Ghi nhận liên hệ quá hạn | Cho phép | Cho phép | Cho phép | Không được phép |  |

## Kho và sự cố sách

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `POST /api/v1/book-copies/{id}/repair` | Chuyển bản sao sang sửa chữa | Cho phép | Không được phép | Cho phép | Không được phép |  |
| `GET /api/v1/book-copies/{id}/status-history` | Xem lịch sử trạng thái bản sao | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/books/{bookId}/copies` | Thêm bản sao | Cho phép | Không được phép | Cho phép | Không được phép |  |
| `GET /api/v1/books/{bookId}/copies/bulk/preview` | Xem trước mã vạch hàng loạt | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/books/{bookId}/copies/bulk` | Thêm bản sao hàng loạt | Cho phép | Không được phép | Cho phép | Không được phép |  |
| `GET /api/v1/books/{bookId}/copies` | Xem bản sao của đầu sách | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/books/{bookId}/copies/summary` | Xem tổng hợp bản sao | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/book-copies/{id}` | Xem chi tiết bản sao | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `PUT /api/v1/book-copies/{id}` | Sửa thông tin và vị trí bản sao | Cho phép | Không được phép | Cho phép | Không được phép |  |
| `PATCH /api/v1/book-copies/{id}` | Sửa thông tin và vị trí bản sao | Cho phép | Không được phép | Cho phép | Không được phép |  |
| `GET /api/v1/book-incidents/open-items` | Tìm sách để ghi nhận mất/hỏng | Không được phép | Không được phép | Cho phép | Không được phép |  |
| `GET /api/v1/book-incidents/preview` | Xem trước ghi nhận mất/hỏng | Không được phép | Không được phép | Cho phép | Không được phép |  |
| `POST /api/v1/book-incidents` | Ghi nhận sách mất/hỏng | Không được phép | Không được phép | Cho phép | Không được phép |  |
| `GET /api/v1/book-incidents` | Xem lịch sử sự cố | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/book-incidents/{id}` | Xem chi tiết sự cố | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/book-incidents/{id}/images/{imageId}` | Xem ảnh sự cố | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/book-incidents/{id}/adjustments` | Xem lịch sử điều chỉnh bồi thường | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/book-incidents/{id}/adjustment-preview` | Xem trước điều chỉnh bồi thường | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/book-incidents/{id}/adjustments` | Lập đề nghị điều chỉnh bồi thường | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/book-incidents/{id}/adjustments/{adjustmentId}/confirm` | Xác nhận điều chỉnh bồi thường | Cho phép | Cho phép | Không được phép | Không được phép |  |
| `GET /api/v1/library-settings/warehouses` | Xem kho để chọn vị trí bản sao | Cho phép | Cho phép | Cho phép | Không được phép | Chỉ rà soát API đọc vị trí phục vụ nghiệp vụ bản sao; các API cấu hình ngoài phạm vi. |
| `GET /api/v1/library-settings/shelves` | Xem kệ để chọn vị trí bản sao | Cho phép | Cho phép | Cho phép | Không được phép | Chỉ rà soát API đọc vị trí phục vụ nghiệp vụ bản sao; các API cấu hình ngoài phạm vi. |

## Đặt giữ

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `GET /api/v1/reservations/mine` | Xem đơn đặt giữ cá nhân | Không được phép | Không được phép | Không được phép | Chỉ dữ liệu của mình |  |
| `POST /api/v1/reservations/mine/{reservationId}/cancel` | Hủy đơn đặt giữ cá nhân | Không được phép | Không được phép | Không được phép | Chỉ dữ liệu của mình |  |
| `POST /api/v1/books/{bookId}/reservations` | Đặt giữ một đầu sách | Không được phép | Không được phép | Không được phép | Chỉ dữ liệu của mình |  |
| `POST /api/v1/books/{bookId}/reservations/bulk` | Đặt giữ nhiều bản | Không được phép | Không được phép | Không được phép | Chỉ dữ liệu của mình |  |
| `GET /api/v1/books/{bookId}/reservations` | Xem hàng đợi đặt giữ | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/reservations/{reservationId}/cancel` | Hủy đơn đặt giữ tại quầy | Cho phép | Không được phép | Cho phép | Không được phép |  |
| `GET /api/v1/reservations/ready-for-pickup` | Xem đơn sẵn sàng nhận | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `GET /api/v1/reservations/ready-for-pickup/{reservationId}` | Xem chi tiết đơn sẵn sàng nhận | Cho phép | Cho phép | Cho phép | Không được phép |  |
| `POST /api/v1/reservations/auto-cancel-overdue` | Xử lý đơn đặt giữ quá hạn | Cho phép | Cho phép | Không được phép | Không được phép |  |
| `GET /api/v1/reservations/auto-cancel-runs/latest` | Xem lần tự hủy gần nhất | Cho phép | Cho phép | Không được phép | Không được phép |  |
| `GET /api/v1/reservations/auto-cancel-runs` | Xem lịch sử tự hủy | Cho phép | Cho phép | Không được phép | Không được phép |  |
| `POST /api/v1/reservations/auto-cancel-runs/trigger` | Kích hoạt kiểm tra tự hủy | Cho phép | Cho phép | Không được phép | Không được phép |  |
| `GET /api/v1/reservations/auto-cancelled-last-30-days` | Xem đơn đã tự hủy trong 30 ngày | Cho phép | Cho phép | Không được phép | Không được phép |  |

## Tài khoản nhân sự

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `POST /api/v1/admin/accounts` | Tạo tài khoản nhân sự | Cho phép | Không được phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/admin/accounts` | Danh sách tài khoản nhân sự | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/admin/accounts/{id}` | Cập nhật tài khoản nhân sự | Cho phép | Không được phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `DELETE /api/v1/admin/accounts/{id}` | Xóa tài khoản nhân sự | Cho phép | Không được phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PATCH /api/v1/admin/accounts/{id}/status` | Khóa hoặc mở tài khoản nhân sự | Cho phép | Không được phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |

## Nhật ký hoạt động

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `GET /api/v1/admin/audit-logs` | Tra cứu | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/admin/audit-logs/filter-options` | Danh sách bộ lọc | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/admin/audit-logs/{id}` | Xem chi tiết | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/admin/audit-logs/{id}` | Chặn ghi đè nhật ký | Từ chối sửa/xóa (405) | Không được phép | Không được phép | Không được phép | Nhật ký bất biến: Admin nhận 405; vai trò khác 403; không sửa hoặc xóa dữ liệu. |
| `PATCH /api/v1/admin/audit-logs/{id}` | Chặn sửa nhật ký | Từ chối sửa/xóa (405) | Không được phép | Không được phép | Không được phép | Nhật ký bất biến: Admin nhận 405; vai trò khác 403; không sửa hoặc xóa dữ liệu. |
| `DELETE /api/v1/admin/audit-logs/{id}` | Chặn xóa nhật ký | Từ chối sửa/xóa (405) | Không được phép | Không được phép | Không được phép | Nhật ký bất biến: Admin nhận 405; vai trò khác 403; không sửa hoặc xóa dữ liệu. |

## Danh mục và biên mục

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `GET /api/v1/authors` | Danh sách tác giả | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/authors/active` | Tác giả đang sử dụng | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/authors/{id}` | Chi tiết tác giả | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/authors` | Thêm tác giả | Cho phép | Không được phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/authors/{id}` | Sửa tác giả | Cho phép | Không được phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PATCH /api/v1/authors/{id}/toggle-status` | Đổi trạng thái | Cho phép | Không được phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `DELETE /api/v1/authors/{id}` | Xóa tác giả | Cho phép | Không được phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/books` | Danh sách đầu sách nội bộ | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/books/publishers` | Danh sách nhà xuất bản | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/books/{id}` | Xem chi tiết | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/books` | Biên mục đầu sách | Cho phép | Không được phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/books/{id}/cover` | Tải ảnh bìa | Cho phép | Không được phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/books/{id}/cover` | Ảnh bìa nội bộ | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/categories` | Danh sách thể loại | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/categories/active` | Thể loại đang sử dụng | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/categories/{id}` | Chi tiết thể loại | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/categories` | Thêm thể loại | Cho phép | Không được phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/categories/{id}` | Sửa thể loại | Cho phép | Không được phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PATCH /api/v1/categories/{id}/toggle-status` | Đổi trạng thái | Cho phép | Không được phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `DELETE /api/v1/categories/{id}` | Xóa thể loại | Cho phép | Không được phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/publishers` | Thêm nhà xuất bản | Cho phép | Không được phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |

## Chính sách và cấu hình

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `GET /api/v1/book-incidents/factors` | Xem hệ số bồi thường | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/book-incidents/factors` | Cập nhật hệ số bồi thường | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/card-types` | Danh sách loại thẻ | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/card-types/active` | Loại thẻ đang sử dụng | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/card-types/{id}` | Chi tiết loại thẻ | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/card-types/history` | Lịch sử chính sách mượn | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/card-types` | Thêm loại thẻ | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/card-types/{id}` | Sửa loại thẻ | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PATCH /api/v1/card-types/{id}/toggle-status` | Đổi trạng thái | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `DELETE /api/v1/card-types/{id}` | Xóa loại thẻ | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/library-settings/overdue-fee` | Xem cấu hình phí quá hạn | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/library-settings/overdue-fee` | Cập nhật cấu hình phí quá hạn | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/library-settings/warehouses` | Thêm kho | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/library-settings/warehouses/{id}` | Sửa kho | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/library-settings/shelves` | Thêm kệ | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/library-settings/shelves/{id}` | Sửa kệ | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `DELETE /api/v1/library-settings/shelves/{id}` | Xóa kệ | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/library-settings/weekly-schedule` | Xem lịch làm việc | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/library-settings/weekly-schedule` | Cập nhật lịch làm việc | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/library-settings/closed-dates` | Xem ngày nghỉ | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/library-settings/closed-dates` | Thêm ngày nghỉ | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/library-settings/closed-dates/bulk` | Thêm nhiều ngày nghỉ | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `PUT /api/v1/library-settings/closed-dates/{id}` | Sửa ngày nghỉ | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `DELETE /api/v1/library-settings/closed-dates/{id}` | Xóa ngày nghỉ | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/library-settings/adjust-due-date` | Xem ngày trả sau điều chỉnh | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |

## Email nhắc và thông báo

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `GET /api/v1/loan-reminders/preview` | Xem trước email nhắc trả | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/loan-reminders/trigger` | Chạy gửi email nhắc trả | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/loan-reminders/retry-pending` | Gửi lại email đang chờ | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/loan-reminders/{id}/retry` | Gửi lại một email | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/loan-reminders/history` | Lịch sử gửi email | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/reservations/pickup-last-day-reminders/preview` | Xem trước nhắc ngày cuối nhận sách | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/reservations/pickup-last-day-reminders/run` | Chạy nhắc ngày cuối nhận sách | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/reservations/{reservationId}/ready-email` | Xem lịch sử | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/reservations/{reservationId}/ready-email/cancellation-preview` | Xem trước hủy sau email nhận sách | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/reservations/{reservationId}/ready-email/cancellation-run` | Hủy đặt giữ theo điều kiện email | Cho phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/reservations/{reservationId}/ready-email/trigger` | Gửi email sách sẵn sàng | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |

## Thống kê và báo cáo

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `GET /api/v1/dashboard/stats` | Thống kê nội bộ | Cho phép | Cho phép | Cho phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/reports/loans` | Báo cáo mượn theo ngày và hàng chờ | Không được phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/reports/overdue-debts` | Xem báo cáo nợ quá hạn | Không được phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/reports/overdue-debts/export` | Xuất báo cáo nợ quá hạn | Không được phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/reports/overdue-debts/filter-options` | Bộ lọc loại thẻ báo cáo | Không được phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/reports/overdue-debts/history` | Xem lịch sử | Không được phép | Cho phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |

## Xác thực và tài khoản cá nhân

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `POST /api/v1/auth/login` | Đăng nhập | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `POST /api/v1/auth/refresh` | Làm mới phiên | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/auth/me` | Thông tin tài khoản đang đăng nhập | Chỉ dữ liệu của mình | Chỉ dữ liệu của mình | Chỉ dữ liệu của mình | Chỉ dữ liệu của mình | Luôn lấy tài khoản từ JWT; không nhận userId do khách gửi. |
| `GET /api/v1/auth/initial-password` | Kiểm tra liên kết mật khẩu ban đầu | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `POST /api/v1/auth/initial-password` | Thiết lập mật khẩu ban đầu | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `POST /api/v1/auth/forgot-password` | Yêu cầu đặt lại mật khẩu | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/auth/reset-password/validate` | Kiểm tra liên kết đặt lại mật khẩu | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `POST /api/v1/auth/reset-password` | Đặt lại mật khẩu | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |

## Tra cứu và đăng ký công khai

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `GET /api/v1/books/public` | Danh sách sách công khai | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/books/public/search` | Tìm sách công khai | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/books/public/featured` | Sách nổi bật | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/books/public/filters` | Bộ lọc sách công khai | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/books/public/{id}` | Chi tiết sách công khai | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/books/public/{id}/available-copies` | Bản sao có thể mượn công khai | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/books/public/{id}/queue` | Hàng chờ công khai | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/books/public/{id}/cover` | Ảnh bìa công khai | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/books/public/{id}/cover/thumbnail` | Ảnh bìa thu nhỏ công khai | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/public/overview` | Tổng quan công khai | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `GET /api/v1/readers/check-duplicate` | Kiểm tra trùng đăng ký | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |
| `POST /api/v1/readers/register` | Đăng ký bạn đọc | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn cần dữ liệu/token hợp lệ | Công khai; vẫn kiểm tra dữ liệu, mật khẩu/token và điều kiện nghiệp vụ tương ứng. |

## Rà soát phân quyền

| API | Chức năng | Admin | Quản lý | Thủ thư | Bạn đọc | Điều kiện |
|---|---|---|---|---|---|---|
| `GET /api/v1/permission-review` | Đối chiếu ma trận quyền | Cho phép | Không được phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/permission-review/reports` | Danh sách biên bản rà soát | Cho phép | Không được phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `GET /api/v1/permission-review/reports/{id}` | Chi tiết biên bản rà soát | Cho phép | Không được phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/permission-review/reports` | Nhập biên bản kiểm thử | Cho phép | Không được phép | Không được phép | Không được phép | Giữ điều kiện validation, trạng thái và phạm vi dữ liệu hiện có. |
| `POST /api/v1/permission-review/runs` | Chạy kiểm thử toàn bộ | Cho phép | Không được phép | Không được phép | Không được phép | Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống. |
| `GET /api/v1/permission-review/runs/latest` | Lần kiểm thử gần nhất | Cho phép | Không được phép | Không được phép | Không được phép | Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống. |
| `GET /api/v1/permission-review/runs/{runKey}` | Tiến độ lần kiểm thử | Cho phép | Không được phép | Không được phép | Không được phép | Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống. |
| `GET /api/v1/permission-review/reports/{id}/coverage` | Độ bao phủ và điều kiện hoàn tất | Cho phép | Không được phép | Không được phép | Không được phép | Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống. |
| `POST /api/v1/permission-review/reports/{id}/complete` | Xác nhận hoàn tất rà soát | Cho phép | Không được phép | Không được phép | Không được phép | Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống. |
| `GET /api/v1/permission-review/reports/{id}/export` | Xuất biên bản đầy đủ | Cho phép | Không được phép | Không được phép | Không được phép | Chỉ Admin; kiểm thử chạy trong database riêng; xác nhận cần biên bản toàn bộ hiện hành từ bộ chạy của hệ thống. |
