# S4-09.2 — Hướng dẫn thay file, chạy và demo

## Kết quả triển khai

Mở rộng S4-09.1 hiện có để rà soát 14 nhóm, 190 hàm/191 API; thêm lọc phân hệ/vai trò/trạng thái, bộ chạy kiểm thử thật, độ bao phủ, chặn xác nhận khi thiếu ca/sai lệch và xuất toàn bộ biên bản sprint. Backend kiểm soát quyền độc lập giao diện. Admin giữ nhiệm vụ riêng.

Có hai migration mới V59 và V60, không sửa migration đã chạy. V59 lưu tác vụ và xác nhận; V60 sửa lỗi trigger ngăn vai trò hợp lệ xác nhận điều chỉnh sự cố. BookIncidentRepository đồng bộ thời điểm cập nhật bản sao trong ảnh chụp dự kiến; LoanReminderRepository lấy đúng cột id khi gửi lại email trên PostgreSQL. Đây là các phụ thuộc để kiểm tra API được cấp quyền thực hiện thành công.

## Thay file trên Windows

1. Dừng hai cửa sổ backend/frontend đang chạy. Sao lưu thư mục project hiện tại hoặc lưu các thay đổi riêng của bạn trước khi ghi đè.
2. Mở thư mục project có RUN_ALL.cmd, backend, frontend, docs. Trong terminal PowerShell, chạy `git status` và `git branch --show-current`; tạo nhánh riêng như phần Git bên dưới trước khi commit.
3. Giải nén PATCH_S4-09.2.zip vào một thư mục tạm. Bên trong PATCH_S4-09.2 có ba thư mục backend, frontend, docs.
4. Chọn **cả ba thư mục backend, frontend, docs** bên trong gói vá, Copy, rồi Paste vào **gốc project đang dùng**. Chọn “Replace the files in the destination” khi Windows hỏi. Không dán nguyên thư mục PATCH_S4-09.2 thành thư mục con của project; không thay toàn bộ project bằng riêng gói vá.
5. Kiểm tra đã có `backend/src/main/resources/db/migration/V60__allow_authorized_incident_adjustment_confirmation.sql` và `docs/sprint/S4-09.2/README.md`. Danh sách đầy đủ nằm trong DANH_SACH_FILE.md; toàn bộ code từng file có trong TOAN_BO_FILE_HOAN_CHINH.md.

Gói chỉ chứa file mới/file thay thế, không chứa .git, .env, node_modules, target hay database. Giữ cấu hình local hiện có. Sử dụng trên đúng source DuAnTTCS_N5(7).zip đã gửi; các sửa riêng phát sinh sau bản đó cần đối chiếu trước khi ghi đè.

## Chạy ứng dụng

Mở Docker Desktop, chờ Engine chạy rồi chạy tại gốc project:

```powershell
.\RUN_ALL.cmd
```

Nếu là lần cài mới, chạy SETUP_LOCAL.cmd theo README gốc trước. Không cần thêm dependency hoặc chạy script dựng dự án khác. Backend tự chạy V59/V60 bằng Flyway. Chờ log “Tomcat started on port 8080” và “Started LibraryManagementBackendApplication”; frontend ở http://localhost:5173. Mở tài khoản Admin đang dùng và chọn “Rà soát phân quyền”. Không reset hay xóa volume PostgreSQL.

Bộ chạy dùng JDK 17, Maven Wrapper và đầy đủ source backend/test trong project. Tiến trình backend phải chạy từ thư mục backend hoặc gốc project (RUN_ALL.cmd đã làm đúng). PostgreSQL phải truy cập ở localhost:5433; tài khoản DB hiện có cần quyền tạo database. Tài khoản postgres trong Docker Compose hiện tại đáp ứng cách chạy này. Nếu bạn dùng tài khoản DB riêng không có CREATEDB, nhờ người quản lý DB cấp đúng quyền cho môi trường local thử nghiệm.

## Chạy kiểm thử toàn bộ trên màn hình

1. Admin mở “Rà soát phân quyền”, xem tổng 191 API; chọn phân hệ, vai trò hoặc trạng thái để đối chiếu.
2. Bấm **Chạy kiểm thử toàn bộ** một lần. Nút bị khóa khi tác vụ đang chạy; tải lại trang vẫn đọc được tác vụ gần nhất. Có trạng thái bắt đầu/đang chạy/thành công/thất bại. Thời gian phụ thuộc máy; lần đầu cần tải Maven/dependency nên lâu hơn. Chờ trạng thái kết thúc, không chỉ chờ một toast.
3. Hệ thống tạo database riêng `s4092_review_<UUID của lần chạy bỏ dấu gạch nối>` trên cùng PostgreSQL 5433; chạy Flyway, dữ liệu thử và API bằng JWT ký thật. Không dùng bảng nghiệp vụ ở database đang vận hành. Mỗi ca có dữ liệu hợp lệ riêng; các transaction/REQUIRES_NEW hoạt động thật. Email dùng transport trong bộ nhớ, không kết nối SMTP hay gửi email ra ngoài.
4. Bộ thử chạy 975 ca: 191 × 4 = 764 ca chuẩn của bốn vai trò, 191 ca chưa đăng nhập và 20 ca phạm vi dữ liệu/ngoại lệ. Kiểm tra bị từ chối phải đúng mã và toàn bộ bảng nghiệp vụ không đổi (không coi log kiểm toán/xác thực là nghiệp vụ bị sửa).
5. Bấm **Xem kết quả kiểm thử**, xem độ bao phủ, ca thiếu, kết quả từng API/method/vai trò, trước/sau và nội dung khắc phục. Biên bản có người chạy, người yêu cầu/lưu, thời gian, nguồn và môi trường.
6. Chỉ khi mọi ca đạt, đủ bao phủ, source/bảng quyền còn khớp và không còn sai lệch, nút **Xác nhận hoàn tất rà soát** mới được bật. Bấm nút rồi **Đồng ý xác nhận**. Máy chủ kiểm tra lại điều kiện và lưu người/thời điểm xác nhận; bấm lại không tạo xác nhận trùng.
7. Bấm **Xuất biên bản đầy đủ** để tải JSON chứa chi tiết báo cáo đang xem, độ bao phủ/xác nhận và toàn bộ chi tiết các biên bản S4-09.* đã lưu trong database ứng dụng. Có thể mở JSON bằng VS Code. Biên bản cũ không bị ghi đè.

Database thử được xóa sau lần chạy; báo cáo/log nằm trong backend/target/permission-review/jobs và biên bản nhập thành công lưu ở database ứng dụng. Nếu tiến trình/OS bị dừng cưỡng bức có thể còn database thử; khi dọn, đối chiếu UUID trong màn hình và chỉ xóa đúng tên s4092_review_UUID đó. Không xóa database đang dùng hoặc chạy docker compose down -v. Nếu dọn tự động thất bại, trạng thái tác vụ có cảnh báo.

Nhập JSON thủ công vẫn phục vụ lưu/xem bằng chứng, nhưng không thay cho bộ chạy máy chủ để xác nhận hoàn tất. Biên bản mô phỏng luôn được ghi nhãn; không được xác nhận như kiểm thử thật. Sau khi sửa source, khởi động lại backend và chạy lại bộ kiểm thử để lấy dấu vân tay mới.

## Lệnh kiểm tra bổ sung

Build backend và chạy bộ kiểm tra quyền quản trị/coverage/legacy (không thực thi giao dịch trên DB đang dùng):

```powershell
cd backend
.\mvnw.cmd -DskipTests package
.\mvnw.cmd "-Dtest=PermissionReview*Test,CriticalPermissionApiTest" test
cd ..
```

FullPermissionDatabaseTest chỉ chạy khi biến S4_09_2_DB_TEST=true và DB là PostgreSQL loopback 5433 với tên s4092_review_<32 ký tự hex>, có khai báo PERMISSION_REVIEW_LIVE_DB và khác database đó; initializer kiểm tra trước Flyway. Không bật biến này trên database ứng dụng. Nút chạy toàn bộ tự chuẩn bị các biến cần thiết, bạn không cần tự tạo database thử hoặc đổi .env.

Kiểm tra frontend (PowerShell):

```powershell
cd frontend
npm.cmd run build
node --test tests/permissionReview.test.mjs tests/feedbackAlert.test.mjs tests/incidentNotificationPatch.test.mjs tests/notificationFlows.test.mjs tests/readerHistoryExportNotifications.test.mjs
cd ..
```

Browser QA là script tùy chọn theo convention dự án, cần Playwright/Chromium có sẵn; không thêm dependency sản phẩm chỉ để chạy QA. Chi tiết giới hạn và kết quả thực tế nằm ở KET_QUA_KIEM_TRA.md.

## Checklist demo

- Admin thấy đủ phân hệ, 191 API, đủ quyền bốn vai trò và ba trạng thái Khớp/Sai lệch/Chưa xác định.
- Chọn một API thu phí: chỉ Thủ thư được thu; Admin/Quản lý không được thừa quyền.
- Lọc Bạn đọc + danh mục: chín API nội bộ bị chặn; tra cứu công khai vẫn hoạt động.
- Chạy toàn bộ; xem 764/764 ca chuẩn bốn vai trò và 975 ca tổng đạt khi máy hoàn tất.
- Mở chi tiết: 403/401/404/405 đúng ngữ cảnh, denied không đổi nghiệp vụ, vai trò hợp lệ nhận 2xx.
- Xem chín lỗi quyền danh mục và hai ca xác nhận sự cố đã khắc phục; đây là quan sát baseline thật, không gán mã “trước sửa” tưởng tượng.
- Biên bản thiếu vai trò, thiếu method PATCH, mô phỏng, nguồn cũ hoặc có ca thất bại đều không được xác nhận.
- Xác nhận có bước Đồng ý; xuất đầy đủ sprint; làm mới trang vẫn còn biên bản/xác nhận.
- Quản lý/Thủ thư/Bạn đọc không thấy menu rà soát; gọi API trực tiếp vẫn bị 403, chưa đăng nhập 401.
- Thông báo tạm thời ẩn sau 3000 ms; modal, lỗi cố định và hộp xác nhận vẫn còn.

## Git: nhánh mới, giữ nguyên main và nhánh đang làm

Tại gốc project, chạy trước khi commit:

```powershell
git status
git branch --show-current
git switch -c feature/s4-09.2-complete-permission-review
```

Lệnh switch -c tạo nhánh mới từ HEAD hiện tại và mang theo các thay đổi chưa commit, nếu có. Nó không di chuyển đầu nhánh cũ/main. Nếu tên nhánh đã tồn tại, chọn tên mới có hậu tố; không dùng lệnh reset hay force. Không cần checkout/pull main hoặc merge vào nhánh hiện tại để đẩy riêng gói vá này.

Sau khi thay file và kiểm tra thành công, dùng **khối git add chính xác trong DANH_SACH_FILE.md**, xem git diff --cached --stat và git diff --cached --check. git add chỉ đưa nội dung file vào vùng chuẩn bị commit, chưa gửi lên GitHub. Kiểm tra không có .env/mật khẩu/file không liên quan. Sau đó:

```powershell
git commit -m "feat(S4-09.2): hoàn thiện rà soát và kiểm thử phân quyền toàn hệ thống"
git push -u origin feature/s4-09.2-complete-permission-review
```

Push này chỉ tạo/cập nhật nhánh feature vừa tạo. Main và nhánh cũ không được merge/cập nhật. Nếu nhóm cần Pull Request, tạo PR **base develop**, compare nhánh feature; chưa bấm Merge khi chưa review. Tiêu đề: **S4-09.2: Hoàn thiện rà soát và kiểm thử phân quyền toàn hệ thống**. Không tạo PR vào main theo mặc định.

## Phạm vi giữ nguyên

Giữ kiến trúc, router/layout, enum role, API cũ và nghiệp vụ ngoài phụ thuộc cần thiết đã giải thích. Không thêm backlog khác, sửa migration cũ, đổi dependency, xóa API chưa thấy UI, hoặc cho Admin quyền superuser. Rà soát thông báo toàn hệ thống được ghi riêng; không viết lại những file cũ đã đúng 3000 ms.
