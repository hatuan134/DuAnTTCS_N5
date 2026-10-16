# S4-09.1 — Rà soát phân quyền các phân hệ trọng yếu

## Kết quả và phạm vi

Bảng quyền `S4-09.1-v1` được người giao backlog chốt ngày 15/10/2026, với Admin tách nhiệm vụ. Danh mục gồm **84 API/thao tác**: Thu phí và miễn giảm (10), Bạn đọc và thẻ (19), Mượn/trả (21), Kho và sự cố sách (21), Đặt giữ (13). Xem bảng chi tiết trong `BANG_PHAN_QUYEN.md` và file chuẩn `backend/src/main/resources/permissions/S4-09.1-policy.json`.

Source trước bản vá đã có JWT, method security, 401/403, kiểm tra quyền thu phí ở controller và service, và kiểm tra sở hữu phiếu mượn. Không tạo lại những chức năng này. Không phát hiện sai lệch cổng vai trò giữa source và bảng đã chốt trong phạm vi được bộ kiểm thử mới kiểm tra. Các ràng buộc nghiệp vụ có điều kiện được giữ nguyên; đặc biệt Quản lý chỉ xác nhận mượn trong luồng ngoại lệ có lý do.

Bổ sung:

- Màn hình **Quản trị → Rà soát phân quyền**, đường dẫn `/permission-review`, chỉ dành cho `ADMIN`.
- Đọc route/annotation và kiểm tra URL authorization đang chạy, đối chiếu với tài liệu chuẩn độc lập. Không dùng tài liệu chuẩn để tự cấp quyền API.
- Lọc theo phân hệ, chức năng/API, sai lệch; hiển thị quyền của cả bốn vai trò, API tương ứng và điều kiện nghiệp vụ.
- Nhập JSON của bộ kiểm thử riêng; lưu biên bản vào PostgreSQL qua migration mới V58. Ghi người chạy, nguồn source, thời gian phát hiện/kiểm thử lại, người nhập, HTTP trước/sau, nội dung sửa và kiểm tra dữ liệu sau yêu cầu từ chối.
- Bản ghi biên bản không có API sửa/xóa và có trigger bảo vệ lịch sử. Kết quả chỉ được đánh dấu khắc phục khi kiểm thử lại đạt và, với thao tác ghi bị từ chối, có bằng chứng dữ liệu không đổi.
- Tách biên bản **Mô phỏng** khỏi **Hồi quy**. Mô phỏng không được cộng vào sai lệch thật của source đang chạy.
- Ràng buộc biên bản với phiên bản bảng quyền và SHA-256 bytecode của controller, service và cấu hình bảo mật. Khác bản code sẽ bị từ chối nhập; biên bản cũ vẫn được xem với nhãn cần chạy lại.

**Giới hạn được thể hiện ngay trên UI:** đối chiếu cấu hình không thay thế kiểm thử API; MockMvc với service giả lập không chứng minh giao dịch PostgreSQL. Biên bản là bằng chứng được Admin nhập, không có chữ ký chống giả mạo và không phải chứng nhận máy chủ này đã tự chạy kiểm thử.

Không triển khai rà soát quyền báo cáo, cấu hình, gửi thông báo hoặc quản trị tài khoản. Chỉ hai API đọc kho/kệ được đưa vào như phụ thuộc của biểu mẫu vị trí bản sao; không đổi các API cấu hình kho/kệ. Không thay dependency, đăng nhập, API nghiệp vụ, CSS toàn hệ thống hoặc migration cũ.

## A. Danh sách file và cách paste

Xem `DANH_SACH_FILE.md` trong cùng thư mục. ZIP chứa toàn bộ nội dung mỗi file, không có snippet, placeholder, `.env`, `.git`, `node_modules`, `target` hoặc file build.

1. Dừng backend/frontend đang chạy bằng Ctrl+C.
2. Giải nén bản vá vào thư mục tạm.
3. Copy các thư mục `backend`, `frontend`, `docs` trong bản vá vào **thư mục gốc project hiện có**, nơi có `RUN_ALL.cmd`; chấp nhận ghi đè đúng các file trong danh sách.
4. Thư mục chưa có sẽ được tạo khi copy. Không đặt thêm một thư mục `PATCH_S4-09.1` lồng bên trong project.
5. Tuyệt đối không sửa V1–V57, xóa dữ liệu database hoặc thay `.env` để áp dụng bản vá này. V58 chạy tự động khi backend khởi động.

Chỉ hai file source cũ cần thay thế: `SecurityConfig.java` để bảo vệ URL rà soát mới ngay trước bước đọc/validate body, và `frontend/src/app/navigation.ts` để đưa menu mới vào nhóm Quản trị. Các file triển khai còn lại đều tạo mới. Phần thông báo cũ đã có cơ chế 3000 ms; xem biên bản rà soát riêng, không sửa lại code đang đúng chỉ để tạo thay đổi.

## B. Git workflow

Chạy trong project thật của bạn trước khi paste:

```powershell
git status
git branch --show-current
```

Nếu còn thay đổi chưa commit, giữ nguyên nhánh và lưu phần việc đó bằng commit phù hợp hoặc `git stash push -u` trước khi chuyển nhánh; xem lại nội dung để tránh đưa file cấu hình riêng vào commit. Không dùng reset/checkout để xóa thay đổi. Trạng thái Git trong ZIP không chứng minh working tree hiện tại trên máy bạn sạch.

Khi working tree sạch, chưa có nhánh feature này:

```powershell
git switch develop
git pull origin develop
git switch -c feature/S4-09.1-review-critical-permissions
```

Sau khi paste và kiểm tra:

```powershell
git status
git add backend/src frontend/src frontend/tests/permissionReview.test.mjs docs/sprint/S4-09.1
```

Nếu có thêm script QA trình duyệt trong danh sách file, thêm riêng file đó vào staging. Kiểm tra `git diff --cached --stat`, rồi:

```powershell
git commit -m "feat(S4-09.1): bổ sung rà soát phân quyền các phân hệ trọng yếu"
git push -u origin feature/S4-09.1-review-critical-permissions
```

PR vào `develop`, tiêu đề: **[S4-09.1] Rà soát phân quyền các phân hệ nghiệp vụ trọng yếu**. Trong PR ghi rõ các kết quả test đã chạy ở máy bạn và phần chưa chạy.

## C. Build và bộ kiểm thử mới

Từ thư mục gốc project, mở PowerShell trong VS Code:

```powershell
cd .\backend
.\mvnw.cmd "-Dtest=CriticalPermissionApiTest,PermissionReviewControllerTest,PermissionReviewServiceTest,PermissionReviewInspectorTest,PermissionReviewSimulationTest,CriticalPermissionDatabaseTest" test
cd ..\frontend
npm run build
node --test tests/permissionReview.test.mjs tests/feedbackAlert.test.mjs tests/notificationFlows.test.mjs tests/loanReminderFeedback.test.mjs tests/incidentNotificationPatch.test.mjs tests/readerHistoryExportNotifications.test.mjs
cd ..
.\RUN_ALL.cmd
```

Nếu thiếu dependency frontend, chạy `npm ci` trong `frontend` rồi chạy lại. Không cần cập nhật package hoặc thay phiên bản dependency.

`CriticalPermissionDatabaseTest` mặc định **SKIP có chủ đích** nếu chưa bật biến môi trường, không cần PostgreSQL cho bộ MockMvc. Hai tệp sẽ được tạo khi test thành công:

- `backend/target/permission-review/regression.json`: 84 API × (4 vai trò + chưa đăng nhập), cộng bốn luồng ngoại lệ của Quản lý. Có thử cả PUT và PATCH khi API hỗ trợ cả hai. Dữ liệu nghiệp vụ được giả lập, ghi rõ trong từng bằng chứng.
- `backend/target/permission-review/simulation.json`: hai sai lệch được tạo riêng trong test (Bạn đọc thừa quyền, Thủ thư thiếu quyền), sau đó kiểm tra lại controller thật. Không có endpoint hoặc profile ứng dụng nào mở quyền sai.

Có thể ghi tên người chạy thật khi thực hiện test:

```powershell
.\mvnw.cmd "-Dtest=CriticalPermissionApiTest,PermissionReviewSimulationTest" "-Dpermission.review.executedBy=Nguyễn Tiến Vũ" test
```

Không nhập secret, mật khẩu hoặc token vào `sourceReference` hay phần bằng chứng.

## D. Chạy kiểm thử PostgreSQL thật

Bộ này tự tạo tài khoản bốn vai trò và một Bạn đọc thứ hai, đăng nhập thật, nhận JWT ký thật; tạo phiếu, bản sao, đặt giữ và khoản nợ thử. Sau mỗi API bị từ chối, so sánh dữ liệu PostgreSQL; Thủ thư được thu phí và có đúng giao dịch ghi vào database. Biên bản được lưu/đọc qua API và kiểm tra quyền xem. Dữ liệu thử nằm trong transaction được rollback. Chỉ chạy trên **database thử riêng**, vì Flyway sẽ áp dụng các migration của project vào database được cấu hình.

Dùng cách tạo PostgreSQL của project hiện tại, đặt database thử đã tạo vào các biến kết nối. Ví dụ PowerShell trong `backend`:

```powershell
$env:S4_09_1_DB_TEST = 'true'
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '5433'
$env:DB_NAME = 's4091_library_test'
# DB_USERNAME và DB_PASSWORD dùng tài khoản PostgreSQL local của bạn.
.\mvnw.cmd "-Dtest=CriticalPermissionDatabaseTest" test
Remove-Item Env:S4_09_1_DB_TEST
Remove-Item Env:DB_NAME
Remove-Item Env:DB_PORT
Remove-Item Env:DB_HOST
```

Không tự giả định database thử đã tồn tại hoặc thông tin đăng nhập của bạn là mặc định. Nếu đã đặt các biến trên trước khi test, khôi phục giá trị cũ thay vì xóa. Khi test đạt, tệp kết quả: `backend/target/permission-review/postgres.json`.

`backend/src/test/resources/s4-09.1/demo-accounts.sql` là dữ liệu **tùy chọn cho kiểm tra thủ công** sau khi database thử đã được Flyway khởi tạo. Script từ chối database không có tên bắt đầu `s4091_`. Nó không chạy tự động và không phải migration. Có bốn email `s4091-admin@example.invalid`, `s4091-manager@example.invalid`, `s4091-librarian@example.invalid`, `s4091-reader@example.invalid`; mật khẩu thử chung `ReviewTest123!`. Không dùng các tài khoản này cho hệ thống triển khai thật. Script tạo hồ sơ, thẻ và khoản nợ thử cho Bạn đọc; chạy lại không cộng thêm khoản nợ.

## E. Kiểm tra đúng backlog

| Test | Điều kiện và dữ liệu | Thao tác | Kết quả mong đợi |
|---|---|---|---|
| Bốn vai trò | Tài khoản Admin, Quản lý, Thủ thư, Bạn đọc | Đăng nhập từng vai trò; đối chiếu thao tác với bảng chuẩn | Quyền được phép giữ đúng; không coi Admin là tài khoản có mọi nghiệp vụ |
| Màn hình mới | Admin đã đăng nhập | Vào Quản trị → Rà soát phân quyền; chọn Thu phí | Có bốn vai trò, quyền theo tài liệu/cổng API, danh sách API, bộ lọc và số liệu |
| Cấm xem rà soát | Ba vai trò còn lại | Gõ trực tiếp `/permission-review`; gọi GET `/api/v1/permission-review` và `/reports/1` | Trang nêu thiếu quyền; API 403, không trả bảng quyền hoặc biên bản |
| Chưa đăng nhập | Không gửi Authorization | Gọi API nghiệp vụ được bảo vệ và API rà soát | HTTP 401; API công khai đăng ký/tra cứu sách vẫn giữ quy tắc cũ |
| Thông tin bạn đọc | Token Bạn đọc | Gọi GET `/api/v1/readers/{id-cua-nguoi-khac}` | HTTP 403; không lộ hồ sơ |
| Sở hữu phiếu | Hai Bạn đọc A/B và phiếu của A | A xem phiếu mình; B đổi ID hoặc thêm `readerId=A` | A 200; B 404 theo convention che tài nguyên hiện có, không lộ phiếu |
| Tạo phiếu trái quyền | Token Bạn đọc, payload hợp lệ từ request test | POST `/api/v1/loans/direct` | HTTP 403; số phiếu/chi tiết không đổi |
| Ngoại lệ của Quản lý | Token Quản lý | Tạo phiếu bình thường; sau đó gửi luồng ngoại lệ có lý do theo chức năng cũ | Bình thường 403; ngoại lệ chỉ được qua khi toàn bộ điều kiện nghiệp vụ hợp lệ |
| Trả sách trái quyền | Token Bạn đọc, `barcode` và `itemId` thử | POST `/api/v1/loans/return-confirmation` | 403; trạng thái chi tiết mượn và bản sao không đổi |
| Đặt giữ trái quyền | Token Thủ thư/Quản lý/Admin | POST `/api/v1/books/{id}/reservations` | 403; số đơn đặt giữ không đổi |
| Thu phí trái quyền | Có khoản nợ thử; ba vai trò không phải Thủ thư | POST collect-full/collect-partial với payload hợp lệ | 403; không có biên lai/giao dịch hoặc phân bổ thanh toán mới |
| Thu phí đúng quyền | Token Thủ thư, khoản nợ và debtToken vừa lấy | Xác nhận thu phí | Đúng kết quả nghiệp vụ và số dư, có biên lai; không tự xóa khóa thẻ |
| Kho trái quyền | Token Bạn đọc, bản sao thử | Sửa bản sao hoặc chuyển sửa chữa | 403; vị trí/tình trạng/trạng thái và lịch sử không đổi |
| Miễn giảm, khóa thẻ | Bảng quyền đã chốt | Thủ thư lập đề nghị; Quản lý duyệt miễn giảm/khóa thẻ; Admin thử thao tác chỉ dành Quản lý | Luồng đúng được xử lý; thao tác sai vai trò 403 |
| Sai lệch thừa/thiếu | Bộ simulation độc lập | Chạy PermissionReviewSimulationTest và nhập simulation.json | Biên bản có nhãn MÔ PHỎNG, hai sai lệch, trước/sau HTTP, nội dung sửa, kết quả khắc phục |
| Nhập biên bản | Admin, regression.json vừa tạo | Chọn tệp và Lưu biên bản | Lưu một lần, ghi Admin đang đăng nhập; không tin actorId tự gửi |
| Tệp sai/cũ/trùng | JSON hỏng, fingerprint khác, hoặc runKey đã nhập | Nhập lần lượt | Lỗi rõ ràng; fingerprint khác/trùng 409; không tạo biên bản lặp |
| Thông báo | Thành công, lỗi API, tệp sai | Quan sát ở 2999 ms và 3000 ms; thử đóng sớm | Thông báo tạm thời biến mất ở 3000 ms; lỗi cố định dưới input và modal biên bản không tự đóng |

Kết quả chạy trong môi trường tạo bản vá được ghi trong `KET_QUA_KIEM_TRA.md`. Không suy ra test PostgreSQL đã chạy nếu chỉ thấy test MockMvc đạt.

## F. Demo 1–3 phút

1. Chạy hai test tạo `regression.json` và `simulation.json`, khởi động bằng `RUN_ALL.cmd` như cũ.
2. Đăng nhập Admin, mở **Rà soát phân quyền**, chọn **Thu phí và miễn giảm**, tìm `collect-full`.
3. Chỉ vào bốn vai trò: tài liệu chỉ cho Thủ thư; cổng API thật cũng chặn Bạn đọc. Nói rõ source hiện tại không có lỗi thu phí này.
4. Chọn `simulation.json`, bấm **Lưu biên bản**. Mở biên bản có nhãn MÔ PHỎNG.
5. Chỉ vào ca Bạn đọc: trước 200 và sổ thử tăng; sau 403 và sổ thử không đổi. Chỉ vào ca Thủ thư: trước 403, sau 200. Hai ca được đánh dấu khắc phục dựa trên kiểm thử lại.
6. Đóng biên bản, nhập `regression.json` để xem kết quả hồi quy bốn vai trò/khách trên 84 API. Nếu đã chạy PostgreSQL thật, nhập thêm `postgres.json` và trình bày bằng chứng SQL.
7. Đăng nhập Bạn đọc, gõ trực tiếp `/permission-review`: bị từ chối; gọi API rà soát bằng token này phải 403.

**Không sửa source sản phẩm để tái tạo lỗ hổng khi demo.** Controller sai có chủ đích chỉ nằm trong `src/test`.

## G. QA trình duyệt tùy chọn

Có thêm `frontend/tests/permissionReview.browser.cjs`, theo convention script browser hiện có của project. Script dùng Playwright và API fixture, không cần backend/database. Khi môi trường của bạn đã có Playwright và Chromium, từ `frontend` chạy:

```powershell
node tests/permissionReview.browser.cjs
```

Nếu Playwright/Chromium nằm ở vị trí riêng, dùng `LIBRA_PLAYWRIGHT_MODULE` và `LIBRA_BROWSER_EXECUTABLE` như các script QA browser cũ. Không cần cài thêm thư viện để chạy ứng dụng; đây chỉ là kiểm tra tùy chọn. Để tạo ảnh QA cục bộ, có thể đặt `LIBRA_QA_OUTPUT`; ảnh không nằm trong gói bản vá.
