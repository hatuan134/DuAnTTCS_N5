# Bản vá S4-06.3 — Tự động gửi email hủy đơn do quá hạn nhận

Bản vá dựa trên DuAnTTCS_N5(20261014-130218).zip. Đã triển khai source hoàn chỉnh, không tạo project mới và không đổi dependency. Các kiểm tra đã chạy và những phần cần xác nhận local được ghi ở cuối tài liệu; không coi các kiểm tra chưa chạy là đã đạt.

## 1. Phạm vi và quy tắc đã chốt

- Hết bản rảnh: tạo đơn xếp hàng mới ở cuối hàng đợi, không giữ vị trí cũ.
- Lỗi SMTP tạm thời xác định thư chưa được chấp nhận: tối đa 3 lượt tổng cộng, chờ 5 giây rồi 10 giây giữa các lượt. Hết lượt ghi FAILED. Lỗi xác thực/cấu hình/từ chối vĩnh viễn ghi thất bại; kết quả SMTP không xác định không tự gửi lại để tránh trùng.
- Mốc tương lai chỉ xem trước. Hủy thật chỉ khi đơn đang Chờ nhận, chưa được nhận và đã quá hạn theo giờ thực tế. Bằng đúng hạn chưa tính quá hạn.
- Chỉ sự kiện READY_FOR_PICKUP → CANCELLED do Hệ thống (`cancelled_by` rỗng, `cancelled_by_name = Hệ thống`, lý do hiện có `Đã huỷ do quá hạn nhận`) mới sinh email hủy. Hủy thủ công nhập cùng câu lý do vẫn không gửi.
- Email có tên sách, mã đơn, hạn nhận, thời điểm hủy, hướng dẫn và link trang sách `/catalog/books/{bookId}#reserve-book`.
- Đặt giữ lại mở quy trình hiện có. Backend kiểm tra tài khoản/thẻ, khóa thẻ, hạn thẻ, đang mượn đầu sách, đơn trùng đang hiệu lực và giới hạn 3 đơn; không khôi phục đơn hay vị trí cũ.
- Email hủy không dùng giới hạn gửi 5 phút của email sẵn sàng, cũng không bị chặn vì hạn nhận đã qua. Hai loại email cũ giữ quy tắc hiện có.
- Không tự gửi bù cho các đơn đã hủy trước khi V54 chạy. Sự kiện mới được lưu cùng transaction hủy, unique theo đơn + thời điểm hủy, worker xử lý sau commit và dùng cơ chế claim bền vững hiện có.
- Không triển khai backlog khác, không thay SMTP/JWT/router/role, không sửa V1–V53. Rà soát toàn hệ thống không phát hiện thông báo tạm thời cũ cần sửa thêm ngoài lát: tái sử dụng FeedbackAlert 3000 ms và không thêm hệ thống thông báo.

## 2. Cách paste

1. Giải nén PATCH_S4-06.3.zip vào một thư mục riêng.
2. Mở thư mục dự án hiện tại, nơi có `RUN_ALL.cmd`, `backend`, `frontend`. Dừng backend/frontend đang chạy để thay file và chạy lại migration một lần.
3. Dùng `DANH_SACH_FILE_S4-06.3.md` để đối chiếu. TẠO MỚI: tạo đúng thư mục cha nếu chưa có, rồi chép nguyên file. THAY THẾ: chép đè toàn bộ file tương ứng. Mỗi file trong ZIP có đầy đủ nội dung, import và mã chạy; không cần ghép snippet.
4. Có thể chép hai thư mục `backend` và `frontend` trong bản vá vào gốc dự án rồi chọn thay thế đúng các file trùng tên. Đây chỉ là bản vá, không phải toàn bộ source; không xóa hai thư mục backend/frontend hiện có trước khi chép.
5. Bốn file `.md` chỉ là tài liệu, có thể để bên ngoài dự án. `CODE_DAY_DU_S4-06.3.md` cho phép đọc/copy toàn bộ nội dung từng file nếu không muốn chép từ ZIP.
6. Không sửa hoặc chép đè `.env`, `.env.example`, `pom.xml`, `package.json`, `package-lock.json`, các script setup/run và migration V1–V53. Không có node_modules, target, dist, .git, secret hoặc file cache trong bản vá.

## 3. Cách chạy đúng project

Tại PowerShell ở gốc dự án:

```powershell
.\RUN_ALL.cmd
```

Backend chạy 8080, frontend 5173, PostgreSQL 5433. Flyway tự áp dụng `V54__pickup_expiry_cancellation_email.sql` khi backend khởi động. Không sửa checksum migration cũ.

Nếu chưa cấu hình email, dùng script đã có, không tạo setup khác:

```powershell
.\SETUP_EMAIL.cmd
.\RUN_ALL.cmd
```

Dùng SMTP/địa chỉ người nhận hợp lệ do bạn quản lý để nghiệm thu. `FRONTEND_URL` đã có trong `.env.example` và setup, local là `http://localhost:5173`; đây là gốc đường dẫn trong email. Nếu bạn đọc dùng máy khác, gốc URL phải là địa chỉ frontend mà máy đó truy cập được. `PICKUP_EMAIL_AUTOMATIC_ENABLED` mặc định true: cần bật để worker tự xử lý email khi hủy. Không thay mật khẩu/secret bằng giá trị giả trong file source.

Cách vào chức năng:

- Quản lý thư viện/Quản trị: sidebar **Đơn huỷ tự động** → `/manager/auto-cancellations` → khối **Chạy thử email hủy do quá hạn nhận**. Nhập mã đơn; để trống mốc để dùng giờ thực tế, hoặc nhập mốc xem trước theo UTC+7.
- Thủ thư/Quản lý/Quản trị: chi tiết đầu sách → **Hàng đợi đặt giữ** (`/books/{bookId}/reservations`) → chọn trạng thái Đã hủy hoặc Tất cả → **Lịch sử email** ở dòng đơn. Xem được cả 3 loại, kể cả đơn không còn trong danh sách Chờ nhận.
- Bạn đọc: **Đơn đặt giữ của tôi** → dòng Đã hủy có lý do/thời điểm/hạn nhận → **Đặt giữ lại** → trang chi tiết sách và form Đặt giữ hiện có. Bấm link chưa tạo đơn; cần gửi form và qua kiểm tra backend.

Lịch hủy cũ vẫn chạy **00:30 giờ Việt Nam**. Email hủy được xử lý ngay sau khi quy trình này xác nhận hủy, hoặc sau thao tác hủy một đơn quá hạn trong chức năng chạy thử. Bản vá không đổi lịch hủy thành 17:00. Email nhắc ngày cuối vẫn **07:00** theo cấu hình đã chốt trước đó.

## 4. API bổ sung và quyền

| API | Quyền | Ý nghĩa |
|---|---|---|
| GET `/api/v1/reservations/{id}/ready-email/cancellation-preview?checkTime=...` | LIBRARIAN, LIBRARY_MANAGER, ADMIN | Chỉ đọc, không hủy/gửi/ghi lịch sử; checkTime có offset UTC+7 và có thể ở tương lai |
| POST `/api/v1/reservations/{id}/ready-email/cancellation-run` | LIBRARY_MANAGER, ADMIN | Body `{ "checkTime": null }` hoặc thời điểm đã qua; kiểm tra lại và hủy đúng một đơn theo giờ thực tế |
| GET `/api/v1/reservations/{id}/ready-email` | Quyền cũ: LIBRARIAN, LIBRARY_MANAGER, ADMIN | Lịch sử có thêm PICKUP_EXPIRED_CANCELLATION, lý do/thời điểm hủy |
| GET `/api/v1/reservations/mine` | READER, chỉ dữ liệu của mình | Thêm cancellationReason, cancelledAt, cancelledBySystem; giữ hạn nhận cho đơn đã hủy |

Hai API hủy thật cũ có checkTime cũng từ chối mốc tương lai. Lỗi dùng ApiException/error response hiện có. Không thay API đặt giữ hoặc tạo API đặt lại song song.

## 5. Chuẩn bị dữ liệu nghiệm thu

Dùng tài khoản Bạn đọc A có email nhận được thư, thẻ ACTIVE/còn hạn/không khóa, chưa mượn đầu sách kiểm tra và còn chỗ trong giới hạn đặt giữ. Chuẩn bị đầu sách **Mạng máy tính** và ít nhất một bản AVAILABLE bằng chức năng sẵn có. Đặt giữ để có đơn Chờ nhận và ghi lại mã đơn thật.

Ngày 14/10/2026, tình huống hết hạn 17:00 ngày 15/10/2026 chỉ được xem trước; giao diện không cho hủy thật trước thời điểm đó. Để nghiệm thu gửi thật sớm hơn, dùng một đơn đã quá hạn thực tế trong dữ liệu kiểm thử. Nếu cần chuẩn bị đơn thử trên database nghiệm thu, có thể đặt hạn nhận của riêng đơn thử về trước giờ hiện tại bằng SQL dưới đây (thay 123 bằng mã đơn thử vừa ghi; không tự sửa trạng thái hủy):

```sql
UPDATE book_reservations
SET pickup_deadline = clock_timestamp() - INTERVAL '1 minute'
WHERE id = 123 AND status = 'READY_FOR_PICKUP';
```

SQL chỉ chuẩn bị thời hạn cho đơn thử. Chức năng mới thực hiện hủy, xử lý bản sao và sinh email qua quy trình thật. Không đổi đồng hồ máy hay dùng API thời gian tương lai để hủy thật. Muốn chứng minh đủ 3 email trên một đơn, chuẩn bị trước email sẵn sàng/nhắc ngày cuối bằng chức năng và dữ liệu đã có; bài demo này không tạo giả lịch sử hai email đó.

## 6. Checklist test đúng backlog

Mỗi dòng dưới đây là điều kiện → bước thao tác/dữ liệu → kết quả mong đợi. Cần thực hiện local để xác nhận PostgreSQL/SMTP/UI thực tế.

| # | Điều kiện | Thao tác và dữ liệu | Kết quả mong đợi |
|---|---|---|---|
| 1 | A có email hợp lệ; đơn Chờ nhận đã quá hạn; SMTP bật | Quản lý nhập mã đơn, để trống mốc, Xem trước → Hủy đơn quá hạn và gửi email → xác nhận | Đơn CANCELLED, lý do hệ thống quá hạn, email hủy PENDING/SENDING rồi SENT; có thời gian gửi |
| 2 | Đơn chưa quá hạn theo giờ thực tế | Nhập mốc sau hạn nhưng ở tương lai, ví dụ 15/10/2026 17:01 | Chỉ xem trước, không có nút hủy thật; không đổi dữ liệu/ghi lịch sử/gửi thư |
| 3 | Đơn chưa hết hạn hoặc bằng đúng hạn | Xem trước ở trước hạn/17:00 đúng ngày hạn | eligible=false, không cho hủy; bằng đúng hạn chưa quá hạn |
| 4 | Đơn quá hạn nhưng vẫn READY_FOR_PICKUP | Chưa thực hiện quy trình hủy | Chưa có email hủy chỉ vì đồng hồ đã quá hạn; phải có chuyển trạng thái hủy hợp lệ |
| 5 | Đơn Chờ nhận | Thủ thư hủy thủ công, nhập nguyên câu `Đã huỷ do quá hạn nhận` | Không có loại email Hủy do quá hạn nhận; hủy thủ công vẫn theo quy trình cũ |
| 6 | Đơn bị hủy vì mất sách/lý do khác | Kiểm tra lịch sử email | Không gửi email quá hạn cho nguyên nhân khác |
| 7 | Đơn đã nhận/FULFILLED hoặc đã gắn phiếu mượn | Xem trước/chạy lại; hoặc nhận sách giữa xem trước và xác nhận | Backend từ chối hủy; không tạo email hủy |
| 8 | Đơn đã hủy và email hủy đã gửi | Xem lại, quét lại hoặc gọi chạy thử lại | Không hủy/lập thêm email hoặc gửi lại cùng sự kiện |
| 9 | Nhiều đơn Chờ nhận đã quá hạn | Dùng Quét thủ công ngay hoặc chờ lịch hủy 00:30 | Mỗi sự kiện hợp lệ có một email hủy riêng; lịch sử/mã đơn/người nhận không trộn nhau |
| 10 | Email hủy đã SENT | Mở email và nội dung lịch sử | Đúng Mạng máy tính, mã đơn thật, hạn nhận và thời điểm hủy UTC+7; có lý do và hướng dẫn |
| 11 | Email có link | Mở link bằng tài khoản A | Đến `/catalog/books/{id}#reserve-book`; form đặt giữ hiện có, không gọi API đặt mới chỉ khi bấm link |
| 12 | Lỗi kết nối SMTP trước khi gửi; sau đó phục hồi | Test tự động service hoặc SMTP kiểm thử gây lỗi tạm thời | Cùng một email chuyển RETRY rồi SENT; mỗi lượt có lịch sử |
| 13 | SMTP lỗi tạm thời liên tục | Test service/database với SMTP mock | Tối đa 3 lượt tổng cộng, FAILED/SMTP_RETRY_EXHAUSTED, không có lượt 4 |
| 14 | Sai SMTP/cấu hình, SMTP từ chối vĩnh viễn hoặc kết quả không xác định | Test service/SMTP kiểm thử tương ứng | Ghi FAILED rõ mã lỗi; đơn vẫn Đã hủy; không gửi trùng khi không biết thư đã được nhận hay chưa |
| 15 | A chưa có email hợp lệ | Hủy một đơn quá hạn kiểm thử | Email SKIPPED/INVALID_EMAIL; lịch sử ghi lý do; bạn đọc vẫn xem được đơn Đã hủy |
| 16 | Sau hủy, sách còn bản rảnh; A đủ điều kiện | A bấm Đặt giữ lại và gửi form | Tạo mã đơn mới, cấp bản theo quy trình hiện có; đơn cũ vẫn Đã hủy |
| 17 | Sách không còn bản AVAILABLE; A đủ điều kiện | A bấm Đặt giữ lại và gửi form | Đơn mới PENDING ở cuối hàng đợi, không giữ vị trí cũ |
| 18 | Thẻ A bị khóa tại thời điểm gửi form | Bấm Đặt giữ lại rồi gửi | Backend chặn với LIBRARY_CARD_LOCKED; lỗi tạm thời 3000 ms; không tạo đơn mới |
| 19 | Thẻ hết hạn/tài khoản không hoạt động/đang mượn sách/đơn hiệu lực trùng/đủ 3 đơn | Gửi form đặt giữ hiện có sau khi mở link | Backend tiếp tục kiểm tra quy tắc cũ và chặn đúng lý do; không bỏ qua điều kiện do xuất phát từ đơn đã hủy |
| 20 | Thủ thư xem hàng đợi và A xem trang cá nhân | Mở Lịch sử email của đơn đã hủy; A mở Đơn đặt giữ của tôi | Thủ thư xem đủ 3 loại nếu trước đó đã phát sinh; A thấy Đã hủy, lý do, thời điểm, hạn nhận và Đặt giữ lại |
| 21 | READER hoặc chưa đăng nhập | Gọi API preview/run của nhân viên | Bị từ chối; không đọc email của người khác hay hủy đơn |
| 22 | LIBRARIAN | Xem lịch sử/preview; gọi cancellation-run | Được đọc lịch sử/xem trước; không được hủy qua chạy thử, quyền hủy quản lý không bị nới |
| 23 | Transaction hủy rollback | Chạy PickupCancellationEmailDatabaseTest | Không còn sự kiện email hủy; trạng thái đơn vẫn trước giao dịch |
| 24 | Hai worker hoặc hai thao tác cùng đơn | Test database đồng thời | Claim/lock và unique ngăn gửi trùng cùng sự kiện |
| 25 | Thành công/lỗi API/nhập mã đơn 0 trên UI mới; luồng cũ từng vai trò | Quan sát thông báo 2999 ms và 3000 ms; mở hộp thoại chờ lâu hơn 3 giây | Thông báo còn ở 2999 ms, mất ở 3000 ms; lỗi cố định dưới trường và hộp thoại xác nhận vẫn giữ |
| 26 | Desktop 1366×768 và màn hình nhỏ 390×844 | Kiểm tra khối chạy thử, modal email, trang cá nhân, nội dung dài | Không tràn ra ngoài trang; bảng dài dùng vùng cuộn có sẵn; nút đóng/xác nhận và label đọc được |

## 7. Lệnh kiểm thử local

Ở backend:

```powershell
cd backend
.\mvnw.cmd test "-Dtest=PickupCancellationEmailServiceTest,PickupCancellationTrialServiceTest,PickupCancellationPermissionTest,MyBookReservationServiceTest,ReadyPickupNotificationServiceTest,ReadyPickupNotificationPermissionTest,PickupLastDayReminderServiceTest"
cd ..
```

Ở frontend:

```powershell
cd frontend
npm run build
node --test tests/pickupCancellation.test.mjs tests/readyPickupEmail.test.mjs tests/pickupLastDay.test.mjs tests/feedbackAlert.test.mjs tests/notificationFlows.test.mjs tests/readerLoanHistory.test.mjs tests/readerHistoryExportNotifications.test.mjs
cd ..
```

Test database được tắt mặc định và chỉ dùng database riêng. Tạo database `library_management_s4063_test` trên PostgreSQL local rồi cấu hình (nhập mật khẩu của PostgreSQL kiểm thử trên máy của bạn):

```powershell
$env:S4_06_3_DB_TEST="true"
$env:S4_06_3_TEST_DB_URL="jdbc:postgresql://127.0.0.1:5433/library_management_s4063_test"
$env:S4_06_3_TEST_DB_USER="postgres"
$env:S4_06_3_TEST_DB_PASSWORD="MAT_KHAU_DATABASE_KIEM_THU"
cd backend
.\mvnw.cmd test "-Dtest=PickupCancellationEmailDatabaseTest"
cd ..
Remove-Item Env:S4_06_3_DB_TEST
Remove-Item Env:S4_06_3_TEST_DB_PASSWORD
```

Test này migrate V1–V54 và tạo/xóa fixture riêng; không dùng .env của ứng dụng. Có test rollback, lọc hủy thủ công/lý do khác, quá hạn chưa hủy/chưa hết hạn, cập nhật lặp và 2 worker cùng lúc. SMTP là mock, không gửi thư thật.

## 8. Demo 1–3 phút

Chuẩn bị sẵn một đơn thử đã quá hạn thực tế và email của A, cùng các email sẵn sàng/nhắc nếu cần chứng minh cả ba.

1. Quản lý đăng nhập → Đơn huỷ tự động → nhập mã đơn → để trống mốc → Xem trước. Chỉ rõ tên sách và hạn nhận. (20 giây)
2. Bấm Hủy đơn quá hạn và gửi email → đọc xác nhận → xác nhận. Đơn được hủy, bản sao xử lý theo quy trình cũ và sự kiện email sinh tự động. (20 giây)
3. Mở lịch sử email và hộp thư A: loại Hủy do quá hạn nhận, mã đơn, Mạng máy tính, hạn nhận, thời điểm hủy và link đặt lại. Không bấm gửi lại email đã SENT. (30 giây)
4. A mở Đơn đặt giữ của tôi → xem Đã hủy/lý do/thời điểm → Đặt giữ lại → gửi form nếu thẻ đủ điều kiện. Chỉ ra mã đơn mới và vị trí mới hoặc bản được cấp. (30–45 giây)
5. Thủ thư mở Hàng đợi đặt giữ của sách → dòng Đã hủy → Lịch sử email. Chỉ ba nhãn Sách sẵn sàng, Nhắc ngày cuối, Hủy do quá hạn nhận khi cả ba đã phát sinh. (20 giây)

Riêng ví dụ 17:00 ngày 15/10/2026: trước hạn chỉ demo Xem trước ở 17:01 ngày 15/10/2026. Sau hạn, dùng quét thật hoặc chờ lịch hủy 00:30; thư được gửi khi trạng thái hủy được xác nhận, không tự gửi vào 17:00 nếu quy trình hủy chưa chạy.

## 9. Git workflow

Kiểm tra trên chính máy của bạn trước khi đổi nhánh/paste:

```powershell
git status
git branch --show-current
```

Nếu có thay đổi chưa commit, giữ code đó an toàn trước: commit đúng nhánh đang làm hoặc stash có chủ đích. Không chạy switch develop khi chưa xử lý thay đổi. Snapshot ZIP đang ở develop; điều này không thay thế việc kiểm tra working tree trên máy bạn.

Khi working tree sạch và muốn bắt đầu lát mới:

```powershell
git switch develop
git pull origin develop
git switch -c feature/S4-06.3-pickup-expiry-cancellation-email
```

Paste, chạy và test xong, kiểm tra diff rồi commit/push:

```powershell
git status
git diff --stat
git add backend/src frontend/src frontend/tests
git diff --cached --stat
git commit -m "feat(S4-06.3): gửi email hủy đơn quá hạn và hỗ trợ đặt giữ lại"
git push -u origin feature/S4-06.3-pickup-expiry-cancellation-email
```

PR vào develop. Tiêu đề: `[S4-06.3] Tự động gửi email hủy đơn do quá hạn nhận sách`.

Mô tả gợi ý: Khi hệ thống hủy đơn quá hạn nhận, tạo sự kiện email bền vững cùng giao dịch và tự gửi thông báo có lý do, hạn nhận, thời điểm hủy và hướng dẫn đặt lại. Dùng chung lịch sử email, giới hạn lỗi tạm thời tối đa 3 lượt, bổ sung Đặt giữ lại qua quy trình hiện có và chạy thử có xem trước; backend chặn hủy thật ở mốc tương lai.

## 10. Kết quả đã kiểm chứng và giới hạn

- Biên dịch **268 file Java main** bằng Java 17 với đúng 86 dependency runtime lấy từ JAR đã kèm ZIP: đạt. Đây là kiểm tra javac thực, không phải Maven build.
- Service harness thực thi **30/30** kiểm tra: nội dung/link/thời gian, trạng thái/lý do/người hủy, đã nhận, sự kiện thay đổi, gửi một lần, retry 3 lượt, lỗi SMTP, xem trước không ghi/gửi và chặn mốc tương lai: đạt. Repository và SMTP là đối tượng kiểm thử trong bộ nhớ, không phải PostgreSQL/SMTP thật.
- **9/9** kiểm tra phân quyền qua proxy Spring Method Security thực: đạt.
- Frontend `tsc -b`: đạt.
- Nhóm frontend liên quan **84/84** test: đạt, có timer 2999/3000 ms, lỗi nhập cố định, xác nhận chưa thực hiện gửi/hủy, chống double submit, Đặt giữ lại và lịch sử.
- Toàn bộ frontend: bản gốc **473/485 đạt, 12 lỗi**; sau vá **482/494 đạt, cùng 12 lỗi cũ**, không phát sinh lỗi mới. Chi tiết tên test cũ trong báo cáo thông báo. Không sửa các test/lỗi ngoài phạm vi để ép toàn suite xanh.
- Maven/JUnit chưa chạy được tại môi trường bàn giao vì chưa tải parent/dependency test của Spring Boot 4.1.1. Các file JUnit được cung cấp để chạy local, chưa ghi nhận chúng đã đạt.
- Chưa chạy PostgreSQL/Flyway V54/kiểm tra đồng thời trên database thật hoặc gửi SMTP tới hộp thư thật. Phải xác nhận các mục này bằng checklist/local database test.
- Vite production build bị chặn vì node_modules trong ZIP chứa binding Windows, môi trường kiểm tra là Linux thiếu binding native tương ứng; không đổi dependency/lockfile để khắc phục ngoài scope. TypeScript đã đạt; chạy npm run build trên máy Windows của bạn để xác nhận bundling.
- Đã kiểm tra bố cục/component qua source. Chưa xác minh hình ảnh desktop/mobile trong trình duyệt vì môi trường thiếu executable Chromium; mục 26 cần kiểm tra local. Không coi render/component test là kiểm tra giao diện toàn hệ thống hay E2E thật.

Thông tin ngoài phạm vi chỉ ghi nhận: 12 test frontend cũ không khớp source hiện tại (mock loadablePage/Router và kỳ vọng API nhắc phiếu mượn). Không đổi nghiệp vụ, router hoặc API đó trong bản vá.
