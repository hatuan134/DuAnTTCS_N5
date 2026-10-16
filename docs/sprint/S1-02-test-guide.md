# S1-02 — Quản lý tài khoản nhân viên

## 1. Chuẩn bị email local

S1-02 gửi email thật để người dùng đặt mật khẩu lần đầu.

Chạy tại thư mục gốc project:

```powershell
.\SETUP_EMAIL.cmd
```

Nhập Gmail và **App Password** của Gmail khi được hỏi. Không dùng mật khẩu Gmail thông thường.

Sau đó chạy hệ thống:

```powershell
.\RUN_ALL.cmd
```

- Frontend: http://localhost:5173
- Backend: http://localhost:8080
- PostgreSQL Docker: localhost:5433

## 2. Kiểm tra chức năng

1. Đăng nhập bằng tài khoản `ADMIN`.
2. Mở menu **Tài khoản**.
3. Tạo tài khoản với một trong ba vai trò:
   - Quản trị hệ thống
   - Quản lý thư viện
   - Thủ thư
4. Kiểm tra email người nhận có liên kết `/set-initial-password?token=...`.
5. Mở liên kết, nhập mật khẩu tối thiểu 8 ký tự, có chữ và số.
6. Đặt mật khẩu thành công rồi thử dùng lại cùng liên kết: phải bị từ chối.
7. Thử tạo lại email đã có: phải báo email đang được sử dụng.
8. Khóa một tài khoản đang đăng nhập ở trình duyệt khác: request tiếp theo của tài khoản đó phải bị từ chối; refresh token cũ không được cấp access token mới.
9. Mở khóa lại tài khoản và đăng nhập bằng mật khẩu đúng.
10. Đăng nhập bằng vai trò `LIBRARIAN` hoặc `LIBRARY_MANAGER`: menu **Tài khoản** không hiển thị; gọi trực tiếp API `/api/v1/admin/accounts` phải nhận HTTP 403.

## 3. API S1-02

- `GET /api/v1/admin/accounts`
- `POST /api/v1/admin/accounts`
- `PATCH /api/v1/admin/accounts/{id}/status`
- `GET /api/v1/auth/initial-password?token=...`
- `POST /api/v1/auth/initial-password`

Các API `/api/v1/admin/accounts/**` chỉ dành cho vai trò `ADMIN`.
