# Danh sách file S4-09.1

Tất cả đường dẫn tính từ thư mục gốc project (nơi có RUN_ALL.cmd).

## TẠO MỚI

- `backend/src/main/java/com/duanttcsn5/library/controller/PermissionReviewController.java`
- `backend/src/main/java/com/duanttcsn5/library/dto/permission/PermissionReviewDtos.java`
- `backend/src/main/java/com/duanttcsn5/library/repository/PermissionReviewRepository.java`
- `backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewCatalog.java`
- `backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewInspector.java`
- `backend/src/main/java/com/duanttcsn5/library/service/PermissionReviewService.java`
- `backend/src/main/resources/db/migration/V58__permission_review_records.sql`
- `backend/src/main/resources/permissions/S4-09.1-policy.json`
- `backend/src/test/java/com/duanttcsn5/library/CriticalPermissionApiTest.java`
- `backend/src/test/java/com/duanttcsn5/library/CriticalPermissionDatabaseTest.java`
- `backend/src/test/java/com/duanttcsn5/library/PermissionReviewControllerTest.java`
- `backend/src/test/java/com/duanttcsn5/library/PermissionReviewInspectorTest.java`
- `backend/src/test/java/com/duanttcsn5/library/PermissionReviewServiceTest.java`
- `backend/src/test/java/com/duanttcsn5/library/PermissionReviewSimulationTest.java`
- `backend/src/test/resources/s4-09.1/demo-accounts.sql`
- `docs/sprint/S4-09.1/BANG_PHAN_QUYEN.md`
- `docs/sprint/S4-09.1/DANH_SACH_FILE.md`
- `docs/sprint/S4-09.1/KET_QUA_KIEM_TRA.md`
- `docs/sprint/S4-09.1/RA_SOAT_THONG_BAO.md`
- `docs/sprint/S4-09.1/README.md`
- `docs/sprint/S4-09.1/TOAN_BO_FILE_HOAN_CHINH.md`
- `frontend/src/features/s4-09-permission-review/PermissionReviewPage.tsx`
- `frontend/src/features/s4-09-permission-review/feature.tsx`
- `frontend/src/features/s4-09-permission-review/permissionReviewService.ts`
- `frontend/tests/permissionReview.browser.cjs`
- `frontend/tests/permissionReview.test.mjs`

## THAY THẾ TOÀN BỘ FILE HIỆN TẠI

- `backend/src/main/java/com/duanttcsn5/library/config/SecurityConfig.java`
- `frontend/src/app/navigation.ts`

## Lý do hai file cũ thay đổi

- `SecurityConfig.java`: thêm kiểm tra ADMIN cho URL rà soát mới trước bước đọc/validate body. Không đổi quyền URL nghiệp vụ cũ.
- `frontend/src/app/navigation.ts`: đưa menu rà soát mới vào nhóm Quản trị; các menu cũ giữ nguyên.

## Ngoại lệ thông báo toàn hệ thống

- Không có file thông báo cũ cần thay thế; đã có FeedbackAlert 3000 ms và các test hồi quy đạt. Xem `RA_SOAT_THONG_BAO.md`.
- Thông báo của feature mới tái sử dụng FeedbackAlert, không tạo provider hay thời lượng mới.

## Cách paste

- Copy thư mục backend/frontend/docs từ gói bản vá vào đúng gốc project. Các thư mục feature/dto/permissions/docs mới được tạo khi copy.
- Ghi đè nguyên file cho hai đường dẫn ở nhóm THAY THẾ. Không ghép từng đoạn code.
- Không đổi migration V1–V57, .env, database hiện có, package.json/package-lock.json hoặc các file không nằm trong danh sách.
- Các file code hoàn chỉnh cũng được tập hợp trong `TOAN_BO_FILE_HOAN_CHINH.md` để có thể Ctrl+A/copy/paste theo từng mục.
