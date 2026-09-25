# Hệ thống Quản lý Mượn / Trả Sách Thư viện

## Công nghệ

### Frontend
- React 18
- TypeScript
- Vite

### Backend
- Spring Boot 3
- Java 17
- Maven

### Database
- PostgreSQL 15

### Authentication
- Spring Security
- JWT Access Token / Refresh Token
- BCrypt

### Email
- SMTP / Mailtrap

### Storage
- MinIO

## Cấu trúc dự án

- `frontend/`: giao diện React
- `backend/`: REST API Spring Boot
- `docs/`: tài liệu dự án

## Biến môi trường Backend

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SERVER_PORT`

Xem mẫu tại `.env.example`.

## Git Flow

main
↑
develop
↑
feature/*

Không push trực tiếp lên `main` hoặc `develop`.
Mỗi chức năng được phát triển trên branch riêng và merge thông qua Pull Request.