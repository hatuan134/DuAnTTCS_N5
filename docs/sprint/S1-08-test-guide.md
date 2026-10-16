# Hướng dẫn kiểm thử S1-08 — Khai báo danh mục tác giả và thể loại

## 1. Mục tiêu và Yêu cầu (Sprint 1 — S1-08)

Là **Thủ thư** (hoặc Quản lý thư viện / Quản trị hệ thống), tôi muốn khai báo danh mục tác giả và thể loại để khi biên mục sách có thể chọn từ danh sách sẵn có thay vì gõ tay mỗi lần một kiểu:
- **Thêm, sửa, ngừng sử dụng** tác giả và thể loại; tên trùng nhau trong cùng danh mục bị từ chối.
- **Thể loại xếp lồng tối đa 2 cấp** (ví dụ: *Văn học trong nước* nằm dưới *Văn học*).
- **Ràng buộc xoá:** Không cho xoá tác giả hoặc thể loại đang gắn với ít nhất một đầu sách, chỉ cho phép ngừng sử dụng.
- **Ràng buộc biên mục:** Danh mục đã ngừng sử dụng không hiện trong ô chọn khi biên mục mới, nhưng vẫn hiển thị chính xác trên các sách cũ đã biên mục.

---

## 2. Chuẩn bị môi trường và khởi chạy

Tại thư mục gốc dự án:

1. Chạy hệ thống:
   ```cmd
   RUN_ALL.cmd
   ```
   Hoặc chạy riêng Backend:
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -Command "& ./scripts/run-backend.ps1"
   ```
   Và Frontend:
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -Command "& ./scripts/run-frontend.ps1"
   ```

2. Đăng nhập hệ thống:
   - URL: `http://localhost:5173`
   - Tài khoản: `admin@libra.edu.vn` (hoặc tài khoản có vai trò `LIBRARIAN` / `LIBRARY_MANAGER`)
   - Mật khẩu: `Admin123`

3. Mở menu:
   - **Tác giả:** `http://localhost:5173/authors`
   - **Thể loại:** `http://localhost:5173/categories`
   - Hoặc chuyển đổi linh hoạt qua 3 tab: **Tác giả**, **Thể loại**, **Sách đã biên mục**.

---

## 3. Các kịch bản kiểm thử chi tiết

### Kịch bản 1: Quản lý tác giả và kiểm tra chặn trùng tên
1. Mở tab **Tác giả**, bấm nút **"Thêm tác giả"**.
2. Nhập tên: `Nguyễn Nhật Ánh` -> Bấm Lưu.
   - **Kết quả mong đợi:** Báo lỗi từ chối: *"Tên tác giả 'Nguyễn Nhật Ánh' đã tồn tại trong danh mục tác giả."*
3. Nhập tên mới: `Kim Lân`, ghi chú: `Nhà văn hiện thực tiêu biểu` -> Bấm Lưu.
   - **Kết quả mong đợi:** Thêm thành công, tác giả mới hiển thị với 0 đầu sách và trạng thái *Đang sử dụng*.
4. Bấm biểu tượng Sửa (bút chì) tác giả `Kim Lân`, sửa tên thành `Nam Cao`.
   - **Kết quả mong đợi:** Báo lỗi trùng tên với tác giả khác đã có.

### Kịch bản 2: Ngừng sử dụng và kích hoạt lại tác giả
1. Tìm tác giả `Kim Lân` (hoặc bất kỳ tác giả nào), bấm nút **Power** (Ngừng sử dụng).
   - **Kết quả mong đợi:** Trạng thái chuyển thành *Ngừng sử dụng* (nhãn xám).
2. Bấm lại nút **Power**:
   - **Kết quả mong đợi:** Trạng thái chuyển lại thành *Đang sử dụng* (nhãn xanh).

### Kịch bản 3: Chặn xoá tác giả đang gắn với đầu sách
1. Tìm tác giả `Nguyễn Nhật Ánh` (đang có 2 đầu sách: *Cho tôi xin một vé đi tuổi thơ*, *Mắt biếc*).
2. Bấm nút **Xoá** (thùng rác).
   - **Kết quả mong đợi:** Hộp thoại hiển thị thông báo chặn xoá:
     *"Không cho phép xoá: Tác giả 'Nguyễn Nhật Ánh' đang gắn với 2 đầu sách trong hệ thống. Quy tắc chỉ cho phép ngừng sử dụng để bảo toàn dữ liệu sách cũ."*
3. Tìm tác giả `Tác giả thử nghiệm xoá` (0 đầu sách) hoặc `Kim Lân` (0 đầu sách):
   - Bấm nút **Xoá**: Hiển thị hộp thoại xác nhận xoá an toàn. Bấm **Xác nhận xoá** -> Tác giả được xoá hoàn toàn khỏi hệ thống.

### Kịch bản 4: Phân cấp thể loại tối đa 2 cấp
1. Mở tab **Thể loại**, bấm **"Thêm thể loại"**.
2. Tạo thể loại Cấp 1:
   - Tên: `Khoa học tự nhiên`, Thể loại cha: *Không có — Là thể loại cấp 1*.
   - **Kết quả:** Tạo thành công thể loại Cấp 1.
3. Tạo thể loại Cấp 2:
   - Tên: `Vật lý đại cương`, Thể loại cha: chọn `Khoa học tự nhiên (Cấp 1)`.
   - **Kết quả:** Tạo thành công thể loại Cấp 2, hiển thị lồng dưới `Khoa học tự nhiên`.
4. Kiểm tra chặn cấp 3:
   - Ô chọn thể loại cha trong giao diện và API Backend chỉ chấp nhận thể loại Cấp 1. Nếu cố tình gọi API gán cha là một thể loại Cấp 2 (ví dụ: gán cha là *Văn học trong nước*), Backend trả về mã lỗi `400 Bad Request`: *"Thể loại chỉ được phép xếp lồng tối đa 2 cấp."*
5. Kiểm tra chặn trùng tên trong cùng danh mục:
   - Thêm thể loại Cấp 2 có tên `Văn học trong nước` dưới cha `Văn học`.
   - **Kết quả:** Từ chối với thông báo: *"Tên thể loại 'Văn học trong nước' đã tồn tại trong cùng danh mục."*

### Kịch bản 5: Ngừng sử dụng thể loại cha tự động ngừng thể loại con
1. Bấm nút **Power** (Ngừng sử dụng) trên thể loại Cấp 1 `Công nghệ thông tin`.
2. **Kết quả mong đợi:**
   - Thể loại `Công nghệ thông tin` chuyển sang trạng thái *Ngừng sử dụng*.
   - Tất cả các thể loại con trực thuộc (`Lập trình Web`, `Khoa học dữ liệu & AI`) tự động chuyển sang trạng thái *Ngừng sử dụng*.

### Kịch bản 6: Chặn xoá thể loại đang gắn với đầu sách hoặc có thể loại con
1. Bấm nút **Xoá** thể loại `Văn học` (đang có các thể loại con):
   - **Kết quả:** Chặn xoá với lý do: *"Thể loại đang có các thể loại con trực thuộc. Vui lòng chuyển hoặc xoá các thể loại con trước."*
2. Bấm nút **Xoá** thể loại `Văn học trong nước` (đang có 4 đầu sách gắn vào):
   - **Kết quả:** Chặn xoá với lý do: *"Thể loại đang gắn với 4 đầu sách trong hệ thống. Quy tắc chỉ cho phép ngừng sử dụng."*
3. Bấm nút **Xoá** thể loại `Thể loại thử nghiệm xoá` (0 sách, 0 con):
   - **Kết quả:** Cho phép xoá thành công.

### Kịch bản 7: Kiểm chứng quy tắc biên mục (Sách cũ vs Biên mục mới)
1. Mở tab **Sách đã biên mục**:
   - Quan sát cuốn sách mẫu: `Số đỏ (Bản lưu trữ thư viện)`.
   - **Kết quả:** Cuốn sách vẫn hiển thị đầy đủ tên tác giả `Vũ Trọng Phụng` và thể loại `Thể loại cũ ngừng dùng`, kèm theo huy hiệu `Đã ngừng dùng`. Dữ liệu sách cũ được bảo toàn trọn vẹn, không bị mất thông tin.
2. Bấm nút **"Biên mục sách mới"**:
   - Mở ô chọn **Tác giả**: Kiểm tra danh sách -> Tác giả `Vũ Trọng Phụng` (đang ngừng dùng) **hoàn toàn không xuất hiện** trong danh sách chọn.
   - Mở ô chọn **Thể loại**: Kiểm tra danh sách -> `Thể loại cũ ngừng dùng` (đang ngừng dùng) **hoàn toàn không xuất hiện** trong danh sách chọn.
   - Nhập tiêu đề: `Kính Vạn Hoa`, chọn tác giả `Nguyễn Nhật Ánh`, thể loại `Văn học trong nước` -> Bấm **"Lưu & Biên mục"**.
   - **Kết quả:** Sách mới được biên mục thành công, số lượng đầu sách của tác giả `Nguyễn Nhật Ánh` tự động tăng thêm 1 cuốn.

---

## 4. Danh sách API Backend S1-08

### Tác giả (`/api/v1/authors`)
| Phương thức | Đường dẫn | Quyền truy cập | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/authors` | Authenticated | Lấy danh sách toàn bộ tác giả kèm số đầu sách |
| `GET` | `/api/v1/authors/active` | Authenticated | Lấy danh sách tác giả đang hoạt động (dùng cho ô chọn biên mục) |
| `GET` | `/api/v1/authors/{id}` | Authenticated | Chi tiết tác giả |
| `POST` | `/api/v1/authors` | `LIBRARIAN`, `LIBRARY_MANAGER`, `ADMIN` | Thêm tác giả mới (chặn trùng tên) |
| `PUT` | `/api/v1/authors/{id}` | `LIBRARIAN`, `LIBRARY_MANAGER`, `ADMIN` | Cập nhật tác giả (chặn trùng tên) |
| `PATCH`| `/api/v1/authors/{id}/toggle-status`| `LIBRARIAN`, `LIBRARY_MANAGER`, `ADMIN` | Ngừng sử dụng / Kích hoạt lại tác giả |
| `DELETE`| `/api/v1/authors/{id}` | `LIBRARIAN`, `LIBRARY_MANAGER`, `ADMIN` | Xoá tác giả (chặn xoá nếu `bookCount > 0`) |

### Thể loại (`/api/v1/categories`)
| Phương thức | Đường dẫn | Quyền truy cập | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/categories` | Authenticated | Lấy danh sách thể loại phân cấp kèm số đầu sách |
| `GET` | `/api/v1/categories/active` | Authenticated | Lấy danh sách thể loại đang hoạt động (dùng cho ô chọn biên mục) |
| `GET` | `/api/v1/categories/{id}` | Authenticated | Chi tiết thể loại |
| `POST` | `/api/v1/categories` | `LIBRARIAN`, `LIBRARY_MANAGER`, `ADMIN` | Thêm thể loại mới (tối đa 2 cấp, chặn trùng tên cùng danh mục) |
| `PUT` | `/api/v1/categories/{id}` | `LIBRARIAN`, `LIBRARY_MANAGER`, `ADMIN` | Cập nhật thể loại (chặn chuyển thành cấp 2 nếu đang có con) |
| `PATCH`| `/api/v1/categories/{id}/toggle-status`| `LIBRARIAN`, `LIBRARY_MANAGER`, `ADMIN` | Ngừng sử dụng / Kích hoạt lại (ngừng cha thì ngừng luôn các con) |
| `DELETE`| `/api/v1/categories/{id}` | `LIBRARIAN`, `LIBRARY_MANAGER`, `ADMIN` | Xoá thể loại (chặn xoá nếu có con hoặc có sách gắn vào) |

### Biên mục sách (`/api/v1/books`)
| Phương thức | Đường dẫn | Quyền truy cập | Mô tả |
|---|---|---|---|
| `GET` | `/api/v1/books` | Authenticated | Lấy danh sách các đầu sách đã biên mục |
| `POST` | `/api/v1/books` | `LIBRARIAN`, `LIBRARY_MANAGER`, `ADMIN` | Biên mục sách mới (chỉ cho phép chọn tác giả và thể loại đang hoạt động) |
