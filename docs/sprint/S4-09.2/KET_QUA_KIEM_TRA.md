# Kết quả kiểm tra thực tế S4-09.2

Kiểm tra ngày 15/10/2026 trên source DuAnTTCS_N5(7).zip đã mở rộng; JDK 17, PostgreSQL 15.19 thật trên loopback 5433, Linux, database thử tạm riêng. Không dùng database/email hệ thống đang vận hành. Maven Wrapper 3.9.16 của dự án đã được khởi chạy thật ở lượt E2E; các lượt kiểm tra chọn lớp dùng Maven 3.9.9 với cùng pom.

## Build và kiểm thử đã đạt

| Nhóm | Kết quả | Bằng chứng/độ sâu |
|---|---|---|
| Backend compile/package | Đạt | Biên dịch toàn bộ source/test và đóng gói Spring Boot JAR; không đổi dependency. |
| Frontend build | Đạt | TypeScript + Vite production build. |
| API toàn hệ thống | 975/975 | Controller/validation/service/repository/JWT ký thật/Flyway/PostgreSQL; 764 ca chuẩn bốn vai trò, 191 anonymous, 16 scope, 4 override. |
| PermissionReviewCoverageTest | 7/7 | Kiểm tra điều kiện và hồi quy theo lớp test trong source. |
| PermissionReviewInspectorTest | 3/3 | Kiểm tra điều kiện và hồi quy theo lớp test trong source. |
| PermissionReviewServiceTest | 5/5 | Kiểm tra điều kiện và hồi quy theo lớp test trong source. |
| PermissionReviewControllerTest | 3/3 | Kiểm tra điều kiện và hồi quy theo lớp test trong source. |
| PermissionReviewSimulationTest | 1/1 | Kiểm tra điều kiện và hồi quy theo lớp test trong source. |
| CriticalPermissionApiTest | 425/425 | Kiểm tra điều kiện và hồi quy theo lớp test trong source. |
| LoanReminderServiceTest | 14/14 | Kiểm tra điều kiện và hồi quy theo lớp test trong source. |
| CompensationAdjustmentTest | 4/4 | Kiểm tra điều kiện và hồi quy theo lớp test trong source. |
| Frontend tập trung | 48/48 | 11 ca permissionReview và 37 ca component/luồng thông báo cũ. |
| Browser QA | 15/15 | API fixture; desktop 1366/768/390, modal xác nhận trên mobile, 3000 ms, 3 vai trò bị cấm, chạy/polling/xác nhận/xuất. |

Tổng các ca backend của các lớp liên quan đã kiểm tra: **1437 đạt**, tính mỗi ca một lần; không cộng lặp các lần chạy lại. Lượt toàn hệ thống cần khoảng 5–6 phút trong môi trường này; lần đầu tải Maven/dependency có thể lâu hơn. Không hứa bộ kiểm thử sẽ hoàn tất trong 1–3 phút.

## Luồng máy chủ thực tế (HTTP + tiến trình con)

Các kiểm tra sau dùng ứng dụng Spring Boot chạy thật, database ứng dụng thử riêng và worker được POST /permission-review/runs khởi chạy bằng Maven Wrapper của dự án:

| Kiểm tra | Kết quả |
|---|---|
| Ma trận runtime | 190 hàm, 191 API, 0 sai lệch/chưa xác định. |
| Chưa đăng nhập | 401. |
| Ba vai trò ngoài Admin gọi chạy/xác nhận | 403 bằng đăng nhập/JWT thật. |
| Bấm chạy trùng | 409; chỉ một job hoạt động. |
| Tiến trình con | SUCCEEDED, 975 ca, tự nhập biên bản đúng run UUID và dấu vân tay máy chủ. |
| Độ bao phủ | 191/191 API, 764/764 ca chuẩn bốn vai trò; 975 đạt, 0 không đạt, đủ ca phụ. |
| Xác nhận | Thành công, có người/thời điểm; gọi hai lần chỉ có một dòng xác nhận. |
| JSON thủ công chỉ có một ca | Lưu để đối chiếu được; thiếu coverage/server provenance, POST complete trả 409. |
| Xuất sprint | Có toàn bộ 975 chi tiết báo cáo đã xác nhận và cả báo cáo nhập thử trong lịch sử. |
| Database ứng dụng | Các bảng nghiệp vụ đối chiếu không đổi do worker. |
| Dọn database worker | Đúng database UUID của job đã biến mất; database ứng dụng vẫn hoạt động. |

Thời gian E2E gồm đóng gói/khởi động/chạy/đối chiếu: 353.89 giây. Dữ liệu này là bằng chứng môi trường kiểm chứng, không phải biên bản để nhập lên máy khác; mỗi máy phải chạy lại để có dấu vân tay phù hợp.

## Khắc phục được ghi nhận

- Baseline source gốc: 45 yêu cầu HTTP thật cho 9 GET danh mục × 5 danh tính; 9 ca Bạn đọc trả 200 dù quyền PO là 403. Sau bổ sung guard, cả 9 ca trả 403 và không thay đổi nghiệp vụ; 3 vai trò nhân sự vẫn 200, anonymous 401.
- Baseline hai ca Admin/Quản lý xác nhận điều chỉnh sự cố trả 500 trên PostgreSQL do cột generated trong BEFORE trigger. V60 loại đúng cột generated khỏi so sánh nội dung bất biến; BookIncidentRepository dự kiến updated_at theo trigger bản sao để snapshot sau giao dịch khớp. Không bỏ kiểm tra actor, số tiền, snapshot, sổ cái hoặc lịch sử. Hai ca sau sửa trả 200.
- Tổng 11 ca khắc phục có lịch sử quan sát thật trong permissions/S4-09.2-fixes.json; worker dùng lịch sử đó làm beforeStatus, không gán mã trước sửa giả định.
- Lỗi phụ thuộc email: lấy generated key đúng cột id thay vì tất cả cột PostgreSQL; retry API và 14 test LoanReminderService đạt.

## Giới hạn kiểm chứng và lỗi cũ ngoài phạm vi

- Không chạy toàn bộ mọi test class của dự án. Các lớp trong bảng trên đã đạt; các kiểm thử UI cũ dùng fixture, chưa thao tác tay từng màn hình với PostgreSQL thật.
- Chạy bổ sung IncidentReversalDatabaseTest: 9 ca đạt, 1 ca lỗi fixture cũ ở foundBookAndMultipleItemsKeepLoanOpenUntilAllCopiesResolved: INSERT book_copies thiếu received_date/cover_price/physical_condition mà schema hiện có yêu cầu. Không sửa nghiệp vụ/test cũ ngoài phạm vi chỉ để làm xanh toàn dự án. Test API xác nhận của cả Admin/Quản lý trong bộ 975 ca đã đạt sau sửa phụ thuộc.
- Lượt thử trên vùng đĩa overlay từng gặp lỗi PostgreSQL XX000 “unexpected data beyond EOF”; loại lượt đó khỏi kết quả nghiệm thu, dựng database mới trên vùng tạm và chạy lại thành công.
- Chưa chạy RUN_ALL.cmd/mvnw.cmd trực tiếp trên Windows/Docker Desktop; đã kiểm tra script gốc và chạy Maven Wrapper sh trên Linux. Chưa kiểm tra SMTP thật vì yêu cầu tách email thử, không gửi email ra ngoài.
- Java test chạy trực tiếp controller qua MockMvc, không dựng một server HTTP riêng cho từng ca; vẫn dùng đầy đủ SecurityFilterChain, method security, validation, dịch vụ, repository và PostgreSQL thật. Luồng điều phối/nhập/xác nhận/xuất đã có lượt HTTP thật riêng như bảng E2E.
- Trong worker, endpoint startRun vẫn kiểm tra quyền và ghi job nhưng tắt khởi chạy Maven lồng nhau. Điều phối tiến trình con được kiểm chứng bằng lượt E2E bên ngoài; không giả lập kết quả để xác nhận.

## Demo 1–3 phút sau chuẩn bị

Chạy kiểm thử toàn bộ trước buổi demo, chờ kết quả thật. Khi trình bày: mở ma trận và lọc vai trò (30–45 giây), mở biên bản đủ 975 ca/xem 403 và các ca khắc phục (45–60 giây), xác nhận/xuất/làm mới để thấy dữ liệu vẫn còn (30–45 giây). Có thể bấm chạy lại để minh họa job mới; chỉ công bố kết quả của job đó sau khi hoàn tất.

## Phân bố mã phản hồi của bộ 975 ca

| HTTP | Số ca |
|---:|---:|
| 200 | 404 |
| 201 | 53 |
| 202 | 1 |
| 204 | 17 |
| 401 | 172 |
| 403 | 322 |
| 404 | 3 |
| 405 | 3 |
