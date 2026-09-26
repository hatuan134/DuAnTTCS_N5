# Hệ thống Quản lý Mượn / Trả Sách Thư viện

## Stack hiện tại

- Backend: Spring Boot 4.1.1, Java 17, Maven Wrapper
- Frontend: React 18, TypeScript, Vite 8, Tailwind CSS
- Database local: PostgreSQL 15 bằng Docker Compose
- Authentication: Spring Security + JWT access/refresh token
- Migration: Flyway
- Branch tích hợp: `develop`

## Cách chạy dễ nhất trên Windows

### 1. Cài một lần

Cần có:

- Git
- JDK 17
- Node.js 22
- Docker Desktop
- VS Code (khuyến nghị)

Nếu chưa có, có thể cài bằng PowerShell/winget:

```powershell
winget install --id Git.Git -e
winget install --id EclipseAdoptium.Temurin.17.JDK -e
winget install --id OpenJS.NodeJS.22 -e
winget install --id Docker.DockerDesktop -e
winget install --id Microsoft.VisualStudioCode -e
```

Sau khi cài JDK/Docker lần đầu, restart Windows nếu PATH/Docker chưa nhận.

### 2. Clone đúng branch develop

```powershell
git clone -b develop https://github.com/hatuan134/DuAnTTCS_N5.git
cd DuAnTTCS_N5
```

### 3. Khởi tạo local một lần

Mở Docker Desktop và chờ Docker Engine chạy, sau đó double-click:

```text
SETUP_LOCAL.cmd
```

Hoặc chạy trong terminal:

```powershell
.\SETUP_LOCAL.cmd
```

Script chỉ làm 3 việc:

1. Tạo/sửa `.env` an toàn, tự sinh JWT Base64 hợp lệ.
2. Khởi động PostgreSQL Docker và đồng bộ password với `.env` kể cả khi volume cũ còn tồn tại.
3. Chạy `npm.cmd ci` nếu chưa có `node_modules`.

> Lần đầu `npm ci` có thể mất vài phút. Những lần sau không cài lại.

### 4. Chạy hằng ngày

Mở Docker Desktop, rồi double-click:

```text
RUN_ALL.cmd
```

Script mở 2 cửa sổ:

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:5173`

Trình duyệt cũng được mở tự động.

Tài khoản local mặc định:

```text
admin@libra.edu.vn
Admin123
```

## Chạy riêng từng phần

Database:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-db.ps1
```

Backend:

```powershell
.\RUN_BACKEND.cmd
```

Backend chỉ chạy thành công khi log có cả:

```text
Tomcat started on port 8080
Started LibraryManagementBackendApplication
```

`BUILD SUCCESS` một mình không có nghĩa server đang chạy.

Frontend:

```powershell
.\RUN_FRONTEND.cmd
```

Frontend script dùng `npm.cmd`, vì vậy không bị lỗi PowerShell chặn `npm.ps1`.

## Các lỗi đã được xử lý trong script

- PostgreSQL từ chối timezone `Asia/Saigon` -> ép `Asia/Ho_Chi_Minh`.
- JWT secret Base64 sai/mất dấu `=` -> tự kiểm tra và sinh lại.
- `.env` thiếu/rỗng/placeholder -> tự tạo/sửa.
- Docker volume cũ giữ password khác `.env` -> tự đồng bộ password PostgreSQL.
- Volume cũ chưa có `library_management` -> tự tạo DB khi cần.
- `npm.ps1 cannot be loaded` -> dùng `npm.cmd`.
- `npm ci EPERM` do Vite/Node đang giữ file -> script không cài lại nếu `node_modules` đã có; khi cài lần đầu hãy đóng các Vite/Node cũ.
- Vite tự nhảy 5174 -> dùng `--strictPort`, buộc 5173.

## Kiểm tra nhanh

```powershell
docker compose ps
Test-NetConnection 127.0.0.1 -Port 8080
Test-NetConnection 127.0.0.1 -Port 5173
```

## Database

PostgreSQL host port mặc định: `5433`.

```powershell
docker compose ps
docker compose logs postgres
docker compose stop
```

Không chạy lệnh sau nếu không chủ động muốn xóa toàn bộ DB local:

```powershell
docker compose down -v
```

## Git Flow

```powershell
git switch develop
git pull origin develop
git switch -c feature/s1-xx-ten-feature
```

Trước khi PR:

```powershell
git fetch origin
git merge origin/develop
```

PR:

```text
feature/... -> develop
```

Không commit `.env`, `node_modules`, `dist`, `target` hoặc secret.
