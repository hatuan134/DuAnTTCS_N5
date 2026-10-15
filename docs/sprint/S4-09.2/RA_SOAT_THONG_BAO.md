# Rà soát thông báo toàn hệ thống

Source mới nhất đã có FeedbackAlert dùng duy nhất FEEDBACK_DURATION_MS = 3000, timer bắt đầu khi thông báo được mount và cleanup khi đóng/unmount. Không có file thông báo cũ phải sửa trong lát này; các luồng phát hiện đã dùng cơ chế chung. Trang rà soát mở rộng tiếp tục dùng component đó. Không thêm hệ thống toast mới.

## Danh sách source đã rà soát

| Khu vực | Số file TSX tham chiếu FeedbackAlert | Các file |
|---|---:|---|
| `components/public` | 1 | `components/public/PublicLayout.tsx` |
| `components/ui` | 1 | `components/ui/FeedbackAlert.tsx` |
| `public-home` | 1 | `features/public-home/HomePage.tsx` |
| `s1-00-dashboard` | 1 | `features/s1-00-dashboard/DashboardPage.tsx` |
| `s1-01-auth-login` | 1 | `features/s1-01-auth-login/LoginPage.tsx` |
| `s1-02-user-management` | 2 | `features/s1-02-user-management/InitialPasswordPage.tsx`<br>`features/s1-02-user-management/UserManagementPage.tsx` |
| `s1-03-reader-registration` | 5 | `features/s1-03-reader-registration/ReaderBasicProfilePage.tsx`<br>`features/s1-03-reader-registration/ReaderHistoryDenied.tsx`<br>`features/s1-03-reader-registration/ReaderProfilePage.tsx`<br>`features/s1-03-reader-registration/ReadersPage.tsx`<br>`features/s1-03-reader-registration/RegisterPage.tsx` |
| `s1-04-library-card` | 3 | `features/s1-04-library-card/LibraryCardDetailsDialog.tsx`<br>`features/s1-04-library-card/LibraryCardsPage.tsx`<br>`features/s1-04-library-card/LockLibraryCardDialog.tsx` |
| `s1-05-borrow-policy` | 1 | `features/s1-05-borrow-policy/CardTypesPage.tsx` |
| `s1-06-change-password` | 1 | `features/s1-06-change-password/ChangePasswordPage.tsx` |
| `s1-07-password-reset` | 2 | `features/s1-07-password-reset/ForgotPasswordPage.tsx`<br>`features/s1-07-password-reset/ResetPasswordPage.tsx` |
| `s1-08-catalog` | 4 | `features/s1-08-catalog/CatalogManagementPage.tsx`<br>`features/s1-08-catalog/PublicBookDetailPage.tsx`<br>`features/s1-08-catalog/PublicCatalogPage.tsx`<br>`features/s1-08-catalog/PublicSiteFooter.tsx` |
| `s1-09-library-config` | 2 | `features/s1-09-library-config/LibrarySettingsPage.tsx`<br>`features/s1-09-library-config/OverdueFeeSettingsPanel.tsx` |
| `s1-10-audit-log` | 1 | `features/s1-10-audit-log/AuditLogPage.tsx` |
| `s2-02-book-copies` | 6 | `features/s2-02-book-copies/BookCopyDetailPage.tsx`<br>`features/s2-02-book-copies/BookDetailPage.tsx`<br>`features/s2-02-book-copies/BulkCreateBookCopiesForm.tsx`<br>`features/s2-02-book-copies/CreateBookCopyForm.tsx`<br>`features/s2-02-book-copies/EditBookCopyForm.tsx`<br>`features/s2-02-book-copies/RepairBookCopyPanel.tsx` |
| `s2-07-reservations` | 1 | `features/s2-07-reservations/ReserveBookPanel.tsx` |
| `s2-08-my-reservations` | 1 | `features/s2-08-my-reservations/MyReservationsPage.tsx` |
| `s2-09-ready-pickup` | 9 | `features/s2-09-ready-pickup/BookReservationQueuePage.tsx`<br>`features/s2-09-ready-pickup/CancelReservationPanel.tsx`<br>`features/s2-09-ready-pickup/CreateReservationLoanPanel.tsx`<br>`features/s2-09-ready-pickup/PickupCancellationTrialPanel.tsx`<br>`features/s2-09-ready-pickup/PickupLastDayReminderPanel.tsx`<br>`features/s2-09-ready-pickup/ReadyPickupDetailPage.tsx`<br>`features/s2-09-ready-pickup/ReadyPickupEmailPanel.tsx`<br>`features/s2-09-ready-pickup/ReadyPickupPage.tsx`<br>`features/s2-09-ready-pickup/ReservationLoanSelection.tsx` |
| `s2-10-book-cover` | 1 | `features/s2-10-book-cover/BookCoverEditorDialog.tsx` |
| `s3-01-loans` | 3 | `features/s3-01-loans/LoanDetailPage.tsx`<br>`features/s3-01-loans/LoanListPage.tsx`<br>`features/s3-01-loans/LoanSearchPage.tsx` |
| `s3-02-direct-loans` | 3 | `features/s3-02-direct-loans/DirectLoanItemsPanel.tsx`<br>`features/s3-02-direct-loans/DirectLoanPage.tsx`<br>`features/s3-02-direct-loans/LoanRejectionsPage.tsx` |
| `s3-04-my-borrowed-books` | 2 | `features/s3-04-my-borrowed-books/MyBorrowedBooksPage.tsx`<br>`features/s3-04-my-borrowed-books/MyReturnedBooksPanel.tsx` |
| `s3-06-auto-cancellations` | 1 | `features/s3-06-auto-cancellations/AutoCancelledReservationsPage.tsx` |
| `s3-07-returns` | 1 | `features/s3-07-returns/ReceiveReturnPage.tsx` |
| `s3-09-overdue-loans` | 2 | `features/s3-09-overdue-loans/OverdueContactDialog.tsx`<br>`features/s3-09-overdue-loans/OverdueLoansPage.tsx` |
| `s4-01-fee-reduction` | 3 | `features/s4-01-fee-reduction/OverdueFeeDetailPage.tsx`<br>`features/s4-01-fee-reduction/PendingFeeReductionsPage.tsx`<br>`features/s4-01-fee-reduction/ReviewFeeReductionActions.tsx` |
| `s4-02-fee-payments` | 2 | `features/s4-02-fee-payments/FeeCollectionPage.tsx`<br>`features/s4-02-fee-payments/FeeReceiptDialog.tsx` |
| `s4-03-book-incidents` | 4 | `features/s4-03-book-incidents/BookIncidentsPage.tsx`<br>`features/s4-03-book-incidents/CompensationSettingsPage.tsx`<br>`features/s4-03-book-incidents/IncidentAdjustmentPanel.tsx`<br>`features/s4-03-book-incidents/IncidentRecordCard.tsx` |
| `s4-04-card-lock-suggestions` | 3 | `features/s4-04-card-lock-suggestions/CardLockSuggestionDetailsDialog.tsx`<br>`features/s4-04-card-lock-suggestions/CardLockSuggestionSettingsPage.tsx`<br>`features/s4-04-card-lock-suggestions/CardLockSuggestionsPage.tsx` |
| `s4-05-loan-reminders` | 2 | `features/s4-05-loan-reminders/LoanRemindersPage.tsx`<br>`features/s4-05-loan-reminders/TriggerReminderModal.tsx` |
| `s4-07-loan-report` | 1 | `features/s4-07-loan-report/LoanReportPage.tsx` |
| `s4-08-overdue-debt-export` | 2 | `features/s4-08-overdue-debt-export/ExportHistoryPage.tsx`<br>`features/s4-08-overdue-debt-export/OverdueDebtExportPage.tsx` |
| `s4-09-permission-review` | 1 | `features/s4-09-permission-review/PermissionReviewPage.tsx` |

Tổng 74 tệp TSX tham chiếu component; đây là số file, không phải số luồng đã thao tác bằng trình duyệt.

Ngoài React: `frontend/public/threeui/bestsellers-book-showcase.html` dùng toast 3000 ms. Đã rà alert/window.alert, toast/snackbar, setTimeout, duration, success/error và catch trong source. Timer debounce, polling, điều hướng, in/thu hồi object URL không phải thông báo; giữ đúng chức năng. Lỗi validation cố định, nội dung thiếu quyền, kết quả giao dịch, modal và hộp xác nhận không tự đóng.

## Kiểm chứng

- Component chung: success/error/warning/info; còn ở 2999 ms, ẩn ở 3000 ms; re-render/callback không kéo dài; thay message/tone, đóng sớm và cleanup.
- ADMIN: trạng thái tài khoản; LIBRARY_MANAGER: trạng thái loại thẻ; LIBRARIAN: mã vạch/nhận trả và hàng đợi; READER: đặt giữ và danh sách cá nhân.
- Luồng cũ: xuất lịch sử, tự hủy đặt giữ, nhắc email, lỗi catalog công khai, sự cố sách; phản hồi thành công/lỗi/cảnh báo ở nơi có hỗ trợ.
- Màn hình mới: file sai, API lỗi, thành công lưu/xác nhận, bộ lọc; thông báo ẩn đúng 3000 ms, modal/validation/xác nhận vẫn còn.

Các test component dùng API fixture để kích hoạt trạng thái UI. Browser QA kiểm tra màn hình rà soát ở 1366/768/390 px và cấm ba vai trò khác đi trực tiếp. Chưa thao tác bằng trình duyệt từng màn hình cũ với backend thật; không coi phân tích source và component test là xác minh mọi thao tác người dùng toàn hệ thống. API nghiệp vụ được kiểm thử riêng trên PostgreSQL thật; xem KET_QUA_KIEM_TRA.md.
