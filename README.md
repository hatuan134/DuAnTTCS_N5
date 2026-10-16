# LIBRA — HỆ THỐNG QUẢN LÝ MƯỢN / TRẢ SÁCH THƯ VIỆN

**Thực tập cơ sở — Nhóm 5**  
Ứng dụng web hỗ trợ quản lý bạn đọc, danh mục sách, bản sao sách, đặt giữ, mượn/trả, phí và các nghiệp vụ thư viện.

**Frontend:** React 18 · TypeScript · Vite 8 · Tailwind CSS 4  
**Backend:** Java 17 · Spring Boot 4.1.1 · Spring Security · JWT  
**Dữ liệu:** PostgreSQL 15 · Spring Data JPA · Flyway  
</div>

## Mục lục

1. [Giới thiệu](#1-giới-thiệu)
2. [Các nhóm chức năng](#2-các-nhóm-chức-năng)
3. [Công nghệ và kiến trúc](#3-công-nghệ-và-kiến-trúc)
4. [Cấu trúc thư mục](#4-cấu-trúc-thư-mục)
5. [Yêu cầu môi trường](#5-yêu-cầu-môi-trường)
6. [Cài đặt và chạy trên Windows](#6-cài-đặt-và-chạy-trên-windows)
7. [Cấu hình biến môi trường](#7-cấu-hình-biến-môi-trường)
8. [Chạy và kiểm tra thủ công](#8-chạy-và-kiểm-tra-thủ-công)
9. [Cấu trúc cơ sở dữ liệu và Flyway](#9-cấu-trúc-cơ-sở-dữ-liệu-và-flyway)
10. [Quy trình Git và đóng góp](#12-quy-trình-git-và-đóng-góp)
11. [Sự cố thường gặp](#13-sự-cố-thường-gặp)

## 1. Giới thiệu

**LIBRA** là hệ thống web quản lý quy trình mượn và trả sách trong thư viện. Người dùng có thể tra cứu sách và thực hiện các nghiệp vụ phù hợp với vai trò; nhân viên thư viện quản lý tài nguyên, bạn đọc và quy trình lưu thông sách.

Mục tiêu của dự án:

- Số hóa thông tin sách, bản sao sách, thẻ thư viện và hồ sơ bạn đọc.
- Hỗ trợ đặt giữ sách, xác nhận mượn, gia hạn, trả sách và theo dõi quá hạn.
- Quản lý khoản phí, sự cố sách, thông báo và hoạt động nghiệp vụ.
- Phân quyền truy cập, lưu lịch sử thao tác, hỗ trợ báo cáo và xuất dữ liệu.

**Repository:** <https://github.com/hatuan134/DuAnTTCS_N5>  
**Nhánh phát triển/tích hợp:** `develop`  
**Nhánh phát hành dự kiến:** `main` (chỉ merge mã đã được rà soát và kiểm thử).

## 2. Các nhóm chức năng

Các nhóm chức năng dưới đây được tổng hợp từ các module thực tế trong `frontend/src/features` và các thành phần backend; trạng thái hoàn thiện của từng backlog phải đối chiếu tài liệu sprint và kết quả kiểm thử.

| Nhóm | Nội dung chính |
|---|---|
| Tài khoản và phân quyền | Đăng nhập, JWT access/refresh, quản lý người dùng, đăng ký bạn đọc, đổi/quên mật khẩu, phân quyền và nhật ký hoạt động |
| Thư viện | Danh mục sách, tác giả, thể loại, nhà xuất bản, bản sao sách, bìa sách và cấu hình thư viện |
| Thẻ và chính sách | Thẻ thư viện, loại thẻ, chính sách mượn, khóa/mở khóa và gợi ý khóa thẻ |
| Đặt giữ | Đặt giữ sách, quản lý hàng đợi, sách chờ nhận, đơn cá nhân và hủy tự động |
| Mượn / trả | Lập phiếu mượn, mượn trực tiếp, trả sách, theo dõi sách đang mượn và sách quá hạn |
| Phí và sự cố | Thu phí, giảm phí, quản lý sự cố sách và bồi thường |
| Thông báo và báo cáo | Nhắc hạn qua email, báo cáo mượn sách, xuất danh sách công nợ quá hạn, rà soát phân quyền |

**Vai trò được khởi tạo trong cơ sở dữ liệu:**

- `READER` — Bạn đọc.
- `LIBRARIAN` — Thủ thư.
- `LIBRARY_MANAGER` — Quản lý thư viện.
- `ADMIN` — Quản trị hệ thống.

> Các quyền cụ thể được kiểm tra tại backend bằng Spring Security và các ràng buộc nghiệp vụ; không coi việc ẩn nút trên giao diện là cơ chế bảo mật.

## 3. Công nghệ và kiến trúc

| Tầng | Công nghệ | Vai trò |
|---|---|---|
| Frontend | React 18, TypeScript, Vite 8, Tailwind CSS 4 | Giao diện web responsive, điều hướng, gọi API |
| Giao tiếp | Axios, REST API `/api/v1` | Trao đổi dữ liệu JSON giữa frontend và backend |
| Backend | Java 17, Spring Boot 4.1.1, Spring Web MVC | Xử lý yêu cầu HTTP và nghiệp vụ |
| Xác thực | Spring Security, JWT | Xác thực và kiểm soát quyền truy cập |
| Truy cập dữ liệu | Spring Data JPA / Hibernate | Ánh xạ entity, repository |
| Database | PostgreSQL 15 | Lưu dữ liệu nghiệp vụ |
| Thay đổi schema | Flyway | Quản lý migration theo phiên bản |
| Vận hành | Docker / Docker Compose | Đóng gói, vận hành dịch vụ |
| Reverse proxy | Caddy | HTTPS, chuyển tiếp request tới Spring Boot |
| Hạ tầng | Microsoft Azure VM | Chạy backend và PostgreSQL |
| Hosting giao diện | Cloudflare Pages *(đang triển khai)* | Phân phối ứng dụng React tĩnh |

Kiến trúc triển khai dự kiến:

```text
Người dùng (Chrome / Edge / điện thoại)
                  |
                  v
Cloudflare Pages — Frontend React
                  | HTTPS / REST API
                  v
Azure Public DNS + Caddy :443
                  |
                  v
Spring Boot :8080 (container nội bộ)
                  |
                  v
PostgreSQL :5432 (mạng Docker nội bộ, không public)
```

Mô hình trên mô tả kiến trúc mục tiêu; frontend và CORS production cần hoàn tất và kiểm thử end-to-end.

## 4. Cấu trúc thư mục

```text
DuAnTTCS_N5/
├── backend/
│   ├── pom.xml                 # Cấu hình Maven / Spring Boot
│   ├── mvnw, mvnw.cmd          # Maven Wrapper
│   └── src/main/
│       ├── java/com/duanttcsn5/library/
│       │   ├── config/        # Cấu hình bảo mật, JWT, ứng dụng
│       │   ├── controller/    # REST controllers
│       │   ├── dto/           # Dữ liệu yêu cầu / phản hồi
│       │   ├── entity/        # Các entity JPA
│       │   ├── repository/    # Truy cập dữ liệu
│       │   ├── security/      # Bộ lọc và xử lý xác thực
│       │   └── service/       # Nghiệp vụ hệ thống
│       └── resources/
│           ├── application.yml
│           └── db/migration/  # Các migration Flyway
├── frontend/
│   ├── src/
│   │   ├── app/               # Router, đăng ký module
│   │   ├── components/        # Component dùng chung
│   │   ├── core/              # API client, auth
│   │   ├── features/          # Chức năng theo backlog
│   │   └── layouts/           # Bố cục giao diện
│   ├── tests/                 # Kiểm thử frontend
│   ├── package.json
│   ├── package-lock.json
│   └── vite.config.ts
├── docs/                     # Tài liệu database, sơ đồ, sprint
├── scripts/                  # Script hỗ trợ setup và chạy local
├── docker-compose.yml        # PostgreSQL cho môi trường local
├── .env.example              # Ví dụ cấu hình local (không chứa secret thật)
├── SETUP_LOCAL.cmd
├── SETUP_EMAIL.cmd
├── RUN_ALL.cmd
├── RUN_BACKEND.cmd
├── RUN_FRONTEND.cmd
└── README.md
```

## 5. Yêu cầu môi trường

Để chạy thuận tiện trên **Windows 10/11**:

- Git.
- **JDK 17** (không phải Java 21/25 nếu chạy theo script kiểm tra hiện tại).
- **Node.js 22 trở lên** và npm.
- Docker Desktop + Docker Engine đang chạy (hoặc PostgreSQL đã cài và được cấu hình tương đương).
- PowerShell và kết nối Internet để tải dependency ở lần thiết lập đầu tiên.
- VS Code / IntelliJ IDEA (tùy chọn).

Kiểm tra phiên bản:

```powershell
git --version
java -version
node -v
npm -v
docker --version
docker compose version
```

## 6. Cài đặt và chạy trên Windows

### 6.1. Lấy mã nguồn

Trong khi nhóm tiếp tục phát triển trên `develop`:

```powershell
git clone -b develop https://github.com/hatuan134/DuAnTTCS_N5.git
cd DuAnTTCS_N5
```

**Khi `main` đã được merge và chốt phiên bản nộp/bảo vệ**, có thể lấy bản phát hành bằng:

```powershell
git clone -b main https://github.com/hatuan134/DuAnTTCS_N5.git
cd DuAnTTCS_N5
```

> Chỉ dùng một trong hai lệnh clone, không clone đè vào cùng thư mục đã tồn tại.

### 6.2. Thiết lập lần đầu

1. Mở **Docker Desktop**, đợi Docker Engine chạy.
2. Trong thư mục gốc dự án, chạy:

```powershell
.\SETUP_LOCAL.cmd
```

Script kiểm tra công cụ, tạo/sửa `.env` local, tạo JWT secret ngẫu nhiên, khởi động PostgreSQL và cài dependency frontend khi cần.

### 6.3. Chạy hệ thống hằng ngày

```powershell
.\RUN_ALL.cmd
```

Script khởi động PostgreSQL và mở hai cửa sổ PowerShell:

- **Frontend:** <http://localhost:5173>
- **Backend:** <http://localhost:8080>
- **API:** <http://localhost:8080/api/v1>
- **API kiểm tra công khai:** <http://localhost:8080/api/v1/public/overview>

> Lần đầu chạy có thể chậm vì Maven/npm cần tải thư viện. Để sử dụng đầy đủ chức năng, giữ các cửa sổ backend/frontend và PostgreSQL hoạt động.

### 6.4. Tài khoản chạy thử local

`SETUP_LOCAL.cmd` tạo cấu hình bootstrap admin trong `.env` local. Xem `BOOTSTRAP_ADMIN_EMAIL` và `BOOTSTRAP_ADMIN_PASSWORD` tại **máy của bạn** để đăng nhập trong môi trường thử nghiệm; **không đưa mật khẩu thật vào README hoặc ảnh công khai**. Mật khẩu mặc định của script local chỉ dùng cho phát triển, phải đổi trước khi mở website cho người khác sử dụng.

## 7. Cấu hình biến môi trường

### 7.1. Backend và PostgreSQL

File tham khảo: `.env.example` ở thư mục gốc. File `.env` thật được tạo tại máy chạy và **không commit**.

| Biến | Ví dụ / mặc định local | Ý nghĩa |
|---|---|---|
| `DB_HOST` | `127.0.0.1` | Địa chỉ PostgreSQL; trong Docker production thường là `postgres` |
| `DB_PORT` | `5433` (local) / `5432` (trong Docker) | Cổng PostgreSQL |
| `DB_NAME` | `library_management` | Tên database |
| `DB_USERNAME` | `postgres` (local) | Tài khoản kết nối database |
| `DB_PASSWORD` | *Thiết lập riêng* | Mật khẩu database |
| `SERVER_PORT` | `8080` | Cổng Spring Boot |
| `JWT_SECRET_BASE64` | *Tạo ngẫu nhiên, Base64* | Khóa ký JWT, không dùng giá trị mẫu |
| `BOOTSTRAP_ADMIN_EMAIL` | `admin@libra.edu.vn` | Email admin khởi tạo (chỉ tạo nếu chưa tồn tại) |
| `BOOTSTRAP_ADMIN_PASSWORD` | *Thiết lập riêng* | Mật khẩu bootstrap admin |
| `BOOTSTRAP_ADMIN_FULL_NAME` | `Quản trị hệ thống` | Tên hiển thị admin |
| `FRONTEND_URL` | `http://localhost:5173` | Địa chỉ frontend dùng trong liên kết email |
| `MAIL_HOST`, `MAIL_PORT` | `smtp.gmail.com`, `587` | Máy chủ/cổng SMTP |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | *Thiết lập riêng* | Cấu hình gửi email |
| `PICKUP_EMAIL_AUTOMATIC_ENABLED` | `true` | Bật/tắt xử lý email chờ nhận tự động |

Cấu hình chi tiết và giá trị mặc định nằm tại `backend/src/main/resources/application.yml`. **Không copy mật khẩu production vào file `.env.example`.**

### 7.2. Frontend

Frontend sử dụng **`VITE_API_URL`** trong `frontend/src/core/api/apiClient.ts` và mã xác thực.

Local (khi không cấu hình khác):

```dotenv
VITE_API_URL=http://localhost:8080/api/v1
```

Production (Cloudflare Pages → Settings / Variables and Secrets → Production):

```dotenv
VITE_API_URL=https://libra-library-n5.eastasia.cloudapp.azure.com/api/v1
```

> Trong phiên bản nguồn được kiểm tra, `frontend/.env.example` đang ghi `8083`, **khác** với backend local thực tế `8080`. Khi chuẩn hóa repository, hãy sửa ví dụ này về `8080`, hoặc chủ động đặt cổng tương ứng nếu backend thực sự chạy cổng khác. Vite chỉ đọc biến `VITE_...` tại thời điểm build; thay biến production cần rebuild/redeploy frontend.

### 7.3. Cấu hình email local (tùy chọn)

```powershell
.\SETUP_EMAIL.cmd
.\RUN_ALL.cmd
```

SMTP phải có tài khoản và thông tin xác thực hợp lệ. Không có SMTP hợp lệ thì **không** coi các chức năng gửi email đã được kiểm thử thành công.

## 8. Chạy và kiểm tra thủ công

### 8.1. Chạy từng phần trên Windows

```powershell
# PostgreSQL local
powershell -ExecutionPolicy Bypass -File .\scripts\start-db.ps1

# Backend (mở terminal riêng)
.\RUN_BACKEND.cmd

# Frontend (mở terminal khác)
.\RUN_FRONTEND.cmd
```

### 8.2. Build / kiểm thử

```powershell
# Tại thư mục backend
cd backend
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package

# Tại thư mục frontend (terminal khác)
cd frontend
npm ci
npm run build
npm run lint
```

Đây là **lệnh để thực hiện kiểm tra**, không phải tuyên bố mọi bài test đều đã đạt. Khi nghiệm thu, lưu kết quả thực tế.

### 8.3. Smoke test cổng và API

```powershell
# Tại thư mục gốc (Windows)
docker compose ps
Test-NetConnection 127.0.0.1 -Port 8080
Test-NetConnection 127.0.0.1 -Port 5173
curl.exe -i http://localhost:8080/api/v1/public/overview
```

Mong đợi: database chạy, hai cổng hoạt động, endpoint công khai trả HTTP 200.

## 9. Cấu trúc cơ sở dữ liệu và Flyway

- **PostgreSQL 15** là hệ quản trị cơ sở dữ liệu chính.
- Entity và Repository nằm trong `backend/src/main/java/com/duanttcsn5/library/`.
- Migration được lưu ở `backend/src/main/resources/db/migration/` theo dạng `V<version>__<mo_ta>.sql`.
- `spring.jpa.hibernate.ddl-auto=none`; schema được quản lý bằng **Flyway**, không tạo tự động bằng Hibernate.
- Trên Azure ngày 16/10/2026: bảng `flyway_schema_history` ghi nhận đến **V61**; 5 phiên bản cuối V57–V61 báo thành công.

Kiểm tra lịch sử migration trên Azure (sau khi SSH vào VM):

```bash
cd ~/libra/deploy
sudo docker compose -p libra-prod exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT installed_rank, version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"'
```

**Không sửa migration đã áp dụng**; bổ sung migration mới với phiên bản tiếp theo. Không dùng `docker compose down -v` khi muốn giữ dữ liệu vì tùy chọn `-v` có thể xóa volume database.

## 10. Quy trình Git và đóng góp

Nhánh áp dụng:

```text
feature/...  →  develop  →  main
 (làm việc)    (tích hợp)   (phát hành)
```

Quy trình đề xuất:

```bash
git switch develop
git pull origin develop
git switch -c feature/ten-chuc-nang
# Sửa mã, commit, push nhánh feature
# Tạo Pull Request: feature/* → develop
# Kiểm thử trên develop; sau khi ổn định tạo PR: develop → main
```

- Không merge code chưa kiểm thử vào `main`.
- Trước buổi bảo vệ, giữ một phiên bản demo ổn định; ghi lại commit/tag tương ứng khi phát hành.
- Các thay đổi schema dùng Flyway migration mới, không sửa phiên bản đã chạy ở production.
- Trước khi cập nhật production, luôn dự phòng dữ liệu và đánh giá ảnh hưởng đến backend/frontend.

## 11. Sự cố thường gặp

| Hiện tượng | Kiểm tra / hướng xử lý |
|---|---|
| Docker chưa chạy | Mở Docker Desktop; kiểm tra `docker info` |
| PostgreSQL lỗi kết nối | Kiểm tra `.env`, `DB_PORT=5433` ở local, `docker compose ps`, log container |
| Backend không khởi động | Xem dòng `Caused by` trong log; `BUILD SUCCESS` không đồng nghĩa server đã chạy |
| Lỗi JWT | Kiểm tra `JWT_SECRET_BASE64` hợp lệ và đủ độ dài; không thay khóa tùy tiện trên production |
| Frontend không gọi được API | Kiểm tra `VITE_API_URL`, CORS Spring Security, trạng thái backend và HTTPS |
| Trang con refresh báo 404 | Kiểm tra cấu hình điều hướng SPA trên nền tảng hosting |
| Lỗi email | Kiểm tra SMTP, quyền gửi, `MAIL_*`, URL frontend trong email |
| Vite chuyển sang 5174 | Dừng tiến trình chiếm cổng 5173; script dùng `--strictPort` |
| `npm.ps1` bị chặn | Dùng `npm.cmd` hoặc script `.cmd` có sẵn |
| Azure báo lỗi sau deploy | `docker compose ps`, `logs backend/caddy`, kiểm tra DNS và NSG |
