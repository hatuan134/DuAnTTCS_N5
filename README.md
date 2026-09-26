# Hệ thống Quản lý Mượn / Trả Sách Thư viện

## Stack chuẩn của repository

- Backend: Spring Boot 4.1.1, Java 17, Maven Wrapper
- Frontend: React 18, TypeScript, Vite 8, Tailwind CSS
- Database local: PostgreSQL 15 chạy bằng Docker Compose
- Authentication: Spring Security + JWT Access/Refresh Token + BCrypt
- Migration: Flyway
- Branch chung: `develop`

> Nguồn cấu hình ưu tiên khi có khác biệt: `backend/pom.xml`, `frontend/package.json`, `backend/src/main/resources/application.yml`.

## 1. Setup máy mới - cách khuyến nghị

### Yêu cầu tối thiểu

Windows 10/11 có `winget` (App Installer).

Clone repository, chuyển sang `develop`, sau đó chạy PowerShell tại root repo:

```powershell
git switch develop
git pull origin develop
powershell -ExecutionPolicy Bypass -File .\setup.ps1
```

`setup.ps1` sẽ:

1. Kiểm tra/cài bằng winget: Git, JDK 17, Node.js, VS Code, Docker Desktop.
2. Tạo `.env` local nếu chưa có (file này bị Git ignore).
3. Sinh DB password và JWT secret local.
4. Khởi động PostgreSQL 15 bằng Docker Compose tại `localhost:5433`.
5. Chạy `npm ci`.
6. Compile Backend.
7. Build Frontend.

Nếu Docker Desktop vừa được cài lần đầu và chưa sẵn sàng, restart Windows hoặc mở Docker Desktop rồi chạy lại:

```powershell
powershell -ExecutionPolicy Bypass -File .\setup.ps1 -SkipInstall
```

## 2. Chạy dự án hằng ngày

Tại root repo:

### Terminal 1 - Backend

```powershell
.\scripts\run-backend.ps1
```

Script sẽ nạp `.env`, đảm bảo PostgreSQL Docker đang chạy rồi start Spring Boot.

### Terminal 2 - Frontend

```powershell
.\scripts\run-frontend.ps1
```

Frontend chạy cố định tại:

```text
http://localhost:5173
```

Backend mặc định:

```text
http://localhost:8080
```

## 3. Database Docker

Database được cấu hình bởi `docker-compose.yml` và `.env`.

Các lệnh thường dùng:

```powershell
docker compose up -d
docker compose ps
docker compose logs postgres
docker compose stop
docker compose start
docker compose down
```

Kiểm tra trực tiếp PostgreSQL mà không cần cài `psql` trên Windows:

```powershell
docker exec -it duanttcs_n5_postgres psql -U postgres -d library_management
```

Trong psql:

```sql
\dt
SELECT * FROM roles ORDER BY id;
\q
```

### Cảnh báo

```powershell
docker compose down -v
```

sẽ xóa volume và toàn bộ dữ liệu database local. Không chạy lệnh này nếu không chủ động muốn reset DB.

## 4. Biến môi trường

Mẫu nằm tại `.env.example`. File `.env` thật không được commit.

Các biến chính:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SERVER_PORT`
- `JWT_SECRET_BASE64`
- `BOOTSTRAP_ADMIN_EMAIL`
- `BOOTSTRAP_ADMIN_PASSWORD`
- `BOOTSTRAP_ADMIN_FULL_NAME`

Docker Compose và Backend dùng chung `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` để tránh lệch cấu hình.

## 5. Tài khoản ADMIN local

Khi database chưa có email bootstrap, Backend tạo tài khoản từ biến môi trường:

```text
BOOTSTRAP_ADMIN_EMAIL
BOOTSTRAP_ADMIN_PASSWORD
BOOTSTRAP_ADMIN_FULL_NAME
```

`setup.ps1` mặc định local:

```text
admin@libra.edu.vn / Admin123
```

Nếu email đã tồn tại thì đổi biến `BOOTSTRAP_ADMIN_PASSWORD` không tự đổi password của user cũ.

## 6. Build/Test trước Pull Request

Backend:

```powershell
.\scripts\Import-Env.ps1
cd backend
.\mvnw.cmd clean test
```

Frontend:

```powershell
cd frontend
npm ci
npm run lint
npm run build
```

## 7. Git Flow

Không code trực tiếp trên `main` hoặc `develop`.

Bắt đầu feature:

```powershell
git switch develop
git pull origin develop
git status
git switch -c feature/s1-xx-ten-feature
```

Trước khi push, cập nhật `develop` mới nhất vào feature:

```powershell
git fetch origin
git merge origin/develop
```

Sau khi test/build thành công:

```powershell
git status
git add <cac-file-can-commit>
git commit -m "feat: ..."
git push -u origin feature/s1-xx-ten-feature
```

Tạo Pull Request:

```text
feature/... -> develop
```

Không tạo PR trực tiếp vào `main`.

## 8. Tạo ZIP source sạch để đưa cho AI/thành viên khác

Chạy tại root repository:

```powershell
git archive --format=zip --output=..\DuAnTTCS_N5-source.zip HEAD
```

ZIP này chỉ chứa file đã commit của branch hiện tại, không chứa `.git`, `node_modules`, `dist`, `target` hay `.env`.
