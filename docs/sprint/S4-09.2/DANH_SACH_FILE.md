# Danh sách file S4-09.2

Các đường dẫn tính từ gốc project hiện tại. File “Thay thế” phải ghi đè bằng **toàn bộ nội dung** trong gói; file “Mới” tạo đúng đường dẫn. Không có file cần xóa.

| Đường dẫn | Cách đưa vào project | Lý do |
|---|---|---|
| `backend/src/main/java/com/duanttcsn5/library/controller/AuthorController.java` | Thay thế | Thêm guard cho ba GET đọc danh mục. |
| `backend/src/main/java/com/duanttcsn5/library/controller/CardTypeController.java` | Thay thế | Thêm guard cho ba GET đọc chính sách loại thẻ. |
| `backend/src/main/java/com/duanttcsn5/library/controller/CategoryController.java` | Thay thế | Thêm guard cho ba GET đọc danh mục. |
| `backend/src/main/java/com/duanttcsn5/library/controller/PermissionReviewController.java` | Thay thế | Mở rộng 6 API điều phối, coverage, xác nhận và xuất. |
| `backend/src/main/java/com/duanttcsn5/library/dto/permission/PermissionReviewDtos.java` | Thay thế | Giữ constructor cũ; thêm schema v2, method/assertion, job, coverage và xác nhận. |
| `backend/src/main/java/com/duanttcsn5/library/repository/BookIncidentRepository.java` | Thay thế | Phụ thuộc kiểm thử: ảnh chụp dự kiến giữ updated_at đúng trigger PostgreSQL. |
| `backend/src/main/java/com/duanttcsn5/library/repository/LoanReminderRepository.java` | Thay thế | Phụ thuộc kiểm thử: yêu cầu PostgreSQL chỉ trả cột id khi lấy generated key. |
| `backend/src/main/java/com/duanttcsn5/library/repository/PermissionReviewRepository.java` | Thay thế | Lưu tác vụ, nguồn máy chủ, xác nhận và lấy đầy đủ lịch sử sprint. |
| `backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewCatalog.java` | Thay thế | Đọc policy toàn hệ thống; hỗ trợ public/immutable và scope. |
| `backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewCoverageService.java` | Mới | Đối chiếu từng API/method/vai trò, anonymous, ownership, override; chặn/xác nhận/xuất. |
| `backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewInspector.java` | Thay thế | Kiểm tra mapping/URL/method guard, phát hiện API ngoài tài liệu, dấu vân tay đầy đủ. |
| `backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewRunService.java` | Mới | Bộ chạy Maven Wrapper cố định, database riêng, timeout, provenance, dọn database do chính tác vụ tạo. |
| `backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewService.java` | Thay thế | Kiểm tra bằng chứng v2, thời gian lịch sử, method và dữ liệu sau từ chối. |
| `backend/src/main/resources/db/migration/V59__complete_permission_review.sql` | Mới | Bảng job và xác nhận bất biến; không sửa/xóa dữ liệu nghiệp vụ. |
| `backend/src/main/resources/db/migration/V60__allow_authorized_incident_adjustment_confirmation.sql` | Mới | Phụ thuộc kiểm thử: sửa so sánh cột generated trong BEFORE trigger, giữ guard khác. |
| `backend/src/main/resources/permissions/S4-09.2-fixes.json` | Mới | Lịch sử 11 ca khắc phục quan sát thật, gồm dấu vân tay baseline và mã phản hồi trước. |
| `backend/src/main/resources/permissions/S4-09.2-policy.json` | Mới | Bảng quyền PO cho 190 hàm/191 API; giữ quyền lát 1. |
| `backend/src/test/java/com/duanttcsn5/library/CriticalPermissionApiTest.java` | Thay thế | Ghim bộ 425 ca lát 1 vào policy lát 1 để giữ hồi quy độc lập. |
| `backend/src/test/java/com/duanttcsn5/library/FullPermissionDatabaseTest.java` | Mới | 975 ca API thật với JWT và database PostgreSQL riêng; mail trong bộ nhớ; bằng chứng v2. |
| `backend/src/test/java/com/duanttcsn5/library/PermissionReviewApiFixtures.java` | Mới | Dữ liệu hợp lệ theo từng API và hai chủ sở hữu; kiểm tra snapshot/hiệu ứng chính. |
| `backend/src/test/java/com/duanttcsn5/library/PermissionReviewControllerTest.java` | Thay thế | Kiểm tra truy cập các route mới, anonymous, ba vai trò bị cấm, validation. |
| `backend/src/test/java/com/duanttcsn5/library/PermissionReviewCoverageTest.java` | Mới | Thiếu vai trò/method/scope/anonymous/override, stale, mô phỏng, người xác nhận, guard DB. |
| `backend/src/test/java/com/duanttcsn5/library/PermissionReviewInspectorTest.java` | Thay thế | Giữ kiểm tra thừa/thiếu quyền và thêm API runtime ngoài tài liệu. |
| `docs/sprint/S4-09.2/BANG_PHAN_QUYEN.md` | Mới | Tài liệu bàn giao của lát S4-09.2. |
| `docs/sprint/S4-09.2/DANH_SACH_FILE.md` | Mới | Tài liệu bàn giao của lát S4-09.2. |
| `docs/sprint/S4-09.2/DOI_CHIEU_SOURCE_VA_TAI_LIEU.md` | Mới | Tài liệu bàn giao của lát S4-09.2. |
| `docs/sprint/S4-09.2/KET_QUA_KIEM_TRA.md` | Mới | Tài liệu bàn giao của lát S4-09.2. |
| `docs/sprint/S4-09.2/RA_SOAT_THONG_BAO.md` | Mới | Tài liệu bàn giao của lát S4-09.2. |
| `docs/sprint/S4-09.2/README.md` | Mới | Tài liệu bàn giao của lát S4-09.2. |
| `docs/sprint/S4-09.2/TOAN_BO_FILE_HOAN_CHINH.md` | Mới | Tài liệu bàn giao của lát S4-09.2. |
| `frontend/src/features/s4-09-permission-review/PermissionReviewPage.tsx` | Thay thế | Bộ lọc, chạy/polling, coverage, xác nhận, xuất, phân trang kết quả; FeedbackAlert 3000 ms. |
| `frontend/src/features/s4-09-permission-review/permissionReviewService.ts` | Thay thế | Các kiểu dữ liệu/API mới, lọc và xuất đầy đủ. |
| `frontend/tests/permissionReview.browser.cjs` | Thay thế | 15 bước QA browser, desktop/mobile, 3000 ms, polling và xác nhận/xuất. |
| `frontend/tests/permissionReview.test.mjs` | Thay thế | Kiểm tra unit/component quyền, API mới, bộ lọc, import/notification/completion. |

Tổng **34 file**: 18 file thay thế, 16 file mới. Ngoại lệ thông báo cũ: không có file cũ cần thay thêm; cơ chế hiện có đã đúng 3000 ms, kết quả rà soát ghi riêng.

## Đưa đúng các file của bản vá vào vùng chuẩn bị commit

Chạy bằng PowerShell tại gốc project, **sau khi đã tạo nhánh feature mới** theo README.md. Nếu trước đó đã stage file khác, xem git diff --cached --name-only để phân biệt; khối này không tự bỏ các file đã stage của bạn.

```powershell
$s4092PatchFiles = @(
  'backend/src/main/java/com/duanttcsn5/library/controller/AuthorController.java'
  'backend/src/main/java/com/duanttcsn5/library/controller/CardTypeController.java'
  'backend/src/main/java/com/duanttcsn5/library/controller/CategoryController.java'
  'backend/src/main/java/com/duanttcsn5/library/controller/PermissionReviewController.java'
  'backend/src/main/java/com/duanttcsn5/library/dto/permission/PermissionReviewDtos.java'
  'backend/src/main/java/com/duanttcsn5/library/repository/BookIncidentRepository.java'
  'backend/src/main/java/com/duanttcsn5/library/repository/LoanReminderRepository.java'
  'backend/src/main/java/com/duanttcsn5/library/repository/PermissionReviewRepository.java'
  'backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewCatalog.java'
  'backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewCoverageService.java'
  'backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewInspector.java'
  'backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewRunService.java'
  'backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewService.java'
  'backend/src/main/resources/db/migration/V59__complete_permission_review.sql'
  'backend/src/main/resources/db/migration/V60__allow_authorized_incident_adjustment_confirmation.sql'
  'backend/src/main/resources/permissions/S4-09.2-fixes.json'
  'backend/src/main/resources/permissions/S4-09.2-policy.json'
  'backend/src/test/java/com/duanttcsn5/library/CriticalPermissionApiTest.java'
  'backend/src/test/java/com/duanttcsn5/library/FullPermissionDatabaseTest.java'
  'backend/src/test/java/com/duanttcsn5/library/PermissionReviewApiFixtures.java'
  'backend/src/test/java/com/duanttcsn5/library/PermissionReviewControllerTest.java'
  'backend/src/test/java/com/duanttcsn5/library/PermissionReviewCoverageTest.java'
  'backend/src/test/java/com/duanttcsn5/library/PermissionReviewInspectorTest.java'
  'docs/sprint/S4-09.2/BANG_PHAN_QUYEN.md'
  'docs/sprint/S4-09.2/DANH_SACH_FILE.md'
  'docs/sprint/S4-09.2/DOI_CHIEU_SOURCE_VA_TAI_LIEU.md'
  'docs/sprint/S4-09.2/KET_QUA_KIEM_TRA.md'
  'docs/sprint/S4-09.2/RA_SOAT_THONG_BAO.md'
  'docs/sprint/S4-09.2/README.md'
  'docs/sprint/S4-09.2/TOAN_BO_FILE_HOAN_CHINH.md'
  'frontend/src/features/s4-09-permission-review/PermissionReviewPage.tsx'
  'frontend/src/features/s4-09-permission-review/permissionReviewService.ts'
  'frontend/tests/permissionReview.browser.cjs'
  'frontend/tests/permissionReview.test.mjs'
)
git add -- $s4092PatchFiles
git diff --cached --stat
git diff --cached --check
```

`git add` chỉ chuẩn bị nội dung để commit; chưa push và chưa thay đổi nhánh main hay đầu nhánh cũ. Chỉ commit khi danh sách staged đúng ý bạn. Không dùng git add . nếu còn file riêng/.env không muốn đưa lên.
