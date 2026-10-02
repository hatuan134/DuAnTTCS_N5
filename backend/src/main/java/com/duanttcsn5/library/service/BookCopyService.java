package com.duanttcsn5.library.service;

import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.dto.bookcopy.BookCopyStatusHistoryResponse;
import com.duanttcsn5.library.dto.bookcopy.RepairBookCopyRequest;
import com.duanttcsn5.library.repository.BookCopyLifecycleRepository;
import com.duanttcsn5.library.dto.bookcopy.BarcodeMode;
import com.duanttcsn5.library.dto.bookcopy.BulkBarcodePreviewResponse;
import com.duanttcsn5.library.dto.bookcopy.BookCopyResponse;
import com.duanttcsn5.library.dto.bookcopy.BookCopySummaryResponse;
import com.duanttcsn5.library.dto.bookcopy.BulkCreateBookCopiesRequest;
import com.duanttcsn5.library.dto.bookcopy.BulkCreateBookCopiesResponse;
import com.duanttcsn5.library.dto.bookcopy.CreateBookCopyRequest;
import com.duanttcsn5.library.dto.bookcopy.UpdateBookCopyRequest;
import com.duanttcsn5.library.entity.BookCopy;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.ShelfRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class BookCopyService {
    private static final String AUTO_BARCODE_PREFIX = "TV-";
    private static final int AUTO_BARCODE_NUMBER_LENGTH = 6;
    private static final long AUTO_BARCODE_MAX_NUMBER = 999_999L;

    private final BookCopyRepository copies;
    private final BookRepository books;
    private final ShelfRepository shelves;
    private final BookCopyLifecycleRepository lifecycle;

    public BookCopyService(BookCopyRepository copies, BookRepository books, ShelfRepository shelves,
            BookCopyLifecycleRepository lifecycle) {
        this.copies = copies;
        this.books = books;
        this.shelves = shelves;
        this.lifecycle = lifecycle;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookCopyResponse create(Long bookId, CreateBookCopyRequest request) {
        if (!books.existsById(bookId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách.");
        }
        if (request.bookId() != null || request.status() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COPY_FIXED_FIELDS",
                    "Không được truyền đầu sách hoặc trạng thái trong biểu mẫu thêm bản sao.");
        }
        LocalDate date = request.receivedDate();
        if (date == null || date.getYear() < 1 || date.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECEIVED_DATE",
                    "Ngày nhập phải là ngày hợp lệ và không được sau hôm nay.");
        }

        BarcodeMode mode = request.barcodeMode() == null ? BarcodeMode.MANUAL : request.barcodeMode();
        String manualBarcode = null;
        if (mode == BarcodeMode.MANUAL) {
            manualBarcode = normalizeManualBarcode(request.barcode());
            copies.findByBarcode(manualBarcode).ifPresent(copy -> { throw duplicate(copy); });
        } else if (request.barcode() != null && !request.barcode().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTO_BARCODE_OVERRIDE",
                    "Khi chọn hệ thống sinh mã, không được tự gửi mã vạch.");
        }

        var shelf = shelves.findForCopyCreation(request.shelfId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHELF_NOT_FOUND", "Không tìm thấy kệ."));
        if (!shelf.getWarehouse().getId().equals(request.warehouseId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHELF_WAREHOUSE_MISMATCH", "Kệ không thuộc kho đã chọn.");
        }
        if (!shelf.isActive() || !shelf.getWarehouse().isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_INACTIVE", "Kho hoặc kệ đã ngừng sử dụng.");
        }

        if (mode == BarcodeMode.AUTO) {
            return createWithAutoBarcode(bookId, request, shelf.getId(), date);
        }
        return createWithManualBarcode(bookId, manualBarcode, request, shelf.getId(), date);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BulkBarcodePreviewResponse previewBulk(Long bookId, java.math.BigDecimal quantity) {
        if (bookId == null || bookId < 1 || !books.existsById(bookId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách.");
        }
        int count = parseBulkQuantity(quantity);
        copies.lockAutoBarcodeSequence();
        return previewRange(count);
    }

    private BulkBarcodePreviewResponse previewRange(int quantity) {
        Long start = copies.peekAutoBarcodeNumber();
        if (start == null || start < 1 || start > AUTO_BARCODE_MAX_NUMBER) {
            throw exhaustedRange();
        }
        var skipped = new java.util.ArrayList<String>();
        String first = null;
        String last = null;
        int accepted = 0;
        for (long number = start; accepted < quantity; number++) {
            if (number > AUTO_BARCODE_MAX_NUMBER) throw exhaustedRange();
            String barcode = formatBarcode(number);
            // Global lookup: copies of other books and all statuses also occupy a barcode.
            if (copies.existsByBarcode(barcode)) {
                skipped.add(barcode);
                continue;
            }
            if (first == null) first = barcode;
            last = barcode;
            accepted++;
        }
        // startNumber is the sequence cursor, not necessarily the first usable barcode.
        return new BulkBarcodePreviewResponse(start, first, last, quantity, List.copyOf(skipped));
    }

    private ApiException exhaustedRange() {
        return new ApiException(HttpStatus.CONFLICT, "BARCODE_SEQUENCE_EXHAUSTED",
                "Dãy mã vạch tự sinh không còn đủ mã cho lô này.");
    }

    private ApiException stalePreview() {
        return new ApiException(HttpStatus.CONFLICT, "BULK_PREVIEW_STALE",
                "Dãy mã hoặc danh sách mã bỏ qua đã thay đổi. Vui lòng xem lại và xác nhận lại.");
    }

    private String formatBarcode(long number) {
        return AUTO_BARCODE_PREFIX + String.format(Locale.ROOT, "%0" + AUTO_BARCODE_NUMBER_LENGTH + "d", number);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BulkCreateBookCopiesResponse createBulk(Long bookId, BulkCreateBookCopiesRequest request) {
        if (bookId == null || bookId < 1 || !books.existsById(bookId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách.");
        }
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BULK_REQUEST", "Dữ liệu tạo lô không hợp lệ.");
        }

        int quantity = parseBulkQuantity(request.quantity());
        LocalDate date = request.receivedDate();
        if (date == null || date.getYear() < 1 || date.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECEIVED_DATE",
                    "Ngày nhập phải là ngày hợp lệ và không được sau hôm nay.");
        }
        if (request.warehouseId() == null || request.warehouseId() < 1
                || request.shelfId() == null || request.shelfId() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BULK_LOCATION",
                    "Vui lòng chọn kho và kệ hợp lệ.");
        }

        var shelf = shelves.findForCopyCreation(request.shelfId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHELF_NOT_FOUND", "Không tìm thấy kệ."));
        if (!shelf.getWarehouse().getId().equals(request.warehouseId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHELF_WAREHOUSE_MISMATCH", "Kệ không thuộc kho đã chọn.");
        }
        if (!shelf.isActive() || !shelf.getWarehouse().isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_INACTIVE", "Kho hoặc kệ đã ngừng sử dụng.");
        }

        if (!Boolean.TRUE.equals(request.confirmed()) || request.expectedStartNumber() == null
                || request.expectedSkippedBarcodes() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BULK_CONFIRMATION_REQUIRED",
                    "Vui lòng xem trước và xác nhận khoảng mã vạch trước khi tạo lô.");
        }
        copies.lockAutoBarcodeSequence();
        var preview = previewRange(quantity);
        if (preview.startNumber() != request.expectedStartNumber()
                || !preview.skippedBarcodes().equals(request.expectedSkippedBarcodes())) {
            throw stalePreview();
        }

        // S2-04.1 chỉ thu thập số lượng, kho, kệ và ngày nhập.
        // Setting này chỉ có hiệu lực trong transaction hiện tại để migration V15
        // cho phép cover_price/physical_condition để trống mà không làm yếu luồng tạo đơn.
        copies.enableBulkBookCopyCreation();

        var skipped = new java.util.HashSet<>(preview.skippedBarcodes());
        int created = 0;
        long expectedNumber = preview.startNumber();
        while (created < quantity) {
            Long number = copies.nextAutoBarcodeNumber();
            if (number == null || number < 1 || number > AUTO_BARCODE_MAX_NUMBER) throw exhaustedRange();
            if (number != expectedNumber++) throw stalePreview();
            String barcode = formatBarcode(number);
            if (skipped.contains(barcode)) continue;
            int inserted = copies.insertBulkGeneratedCopy(bookId, barcode, shelf.getId(), date);
            // A writer outside this service may still win the UNIQUE constraint.
            // Roll back the entire batch so nothing differs silently from the confirmed preview.
            if (inserted != 1) throw stalePreview();
            created++;
        }

        return new BulkCreateBookCopiesResponse(created, preview.startBarcode(), preview.endBarcode(),
                preview.skippedBarcodes());
    }

    private int parseBulkQuantity(java.math.BigDecimal quantity) {
        if (quantity == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BULK_QUANTITY",
                    "Số lượng bản sao phải là số nguyên từ 1 đến 50.");
        }
        try {
            int value = quantity.intValueExact();
            if (value < 1 || value > 50) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BULK_QUANTITY",
                        "Số lượng bản sao phải là số nguyên từ 1 đến 50.");
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BULK_QUANTITY",
                    "Số lượng bản sao phải là số nguyên từ 1 đến 50.");
        }
    }

    private BookCopyResponse createWithManualBarcode(Long bookId, String barcode, CreateBookCopyRequest request,
            Long shelfId, LocalDate date) {
        // Same lock/order as bulk creation: shelf first, then the barcode sequence.
        copies.lockAutoBarcodeSequence();
        int inserted = insert(bookId, barcode, shelfId, date, request);
        BookCopy copy = copies.findByBarcode(barcode).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "COPY_RETRY", "Dữ liệu vừa thay đổi. Vui lòng thử lại."));
        if (inserted == 0) {
            throw duplicate(copy);
        }
        return BookCopyResponse.fromEntity(copy);
    }

    private BookCopyResponse createWithAutoBarcode(Long bookId, CreateBookCopyRequest request,
            Long shelfId, LocalDate date) {
        copies.lockAutoBarcodeSequence();
        while (true) {
            Long number = copies.nextAutoBarcodeNumber();
            if (number == null || number < 1 || number > AUTO_BARCODE_MAX_NUMBER) {
                throw new ApiException(HttpStatus.CONFLICT, "BARCODE_SEQUENCE_EXHAUSTED",
                        "Dãy mã vạch tự sinh đã hết. Vui lòng liên hệ quản trị hệ thống.");
            }
            String barcode = AUTO_BARCODE_PREFIX
                    + String.format(Locale.ROOT, "%0" + AUTO_BARCODE_NUMBER_LENGTH + "d", number);
            int inserted = insert(bookId, barcode, shelfId, date, request);
            if (inserted == 0) {
                // Mã này có thể đã được nhập tay trước đó hoặc vừa được tạo đồng thời.
                // Lấy số tiếp theo trong dãy thay vì báo trùng cho chế độ tự sinh.
                continue;
            }
            BookCopy copy = copies.findByBarcode(barcode).orElseThrow(() ->
                    new ApiException(HttpStatus.CONFLICT, "COPY_RETRY", "Dữ liệu vừa thay đổi. Vui lòng thử lại."));
            return BookCopyResponse.fromEntity(copy);
        }
    }

    private int insert(Long bookId, String barcode, Long shelfId, LocalDate date, CreateBookCopyRequest request) {
        return copies.insertIfBarcodeAbsent(bookId, barcode, shelfId, date,
                request.coverPrice(), request.physicalCondition().name());
    }

    private String normalizeManualBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BARCODE_REQUIRED", "Vui lòng nhập mã vạch.");
        }
        String normalized = barcode.trim();
        if (normalized.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BARCODE_TOO_LONG",
                    "Mã vạch không được dài quá 100 ký tự.");
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public List<BookCopyResponse> getByBookId(Long bookId) {
        if (!books.existsById(bookId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách.");
        }
        return copies.findAllByBookIdOrderByIdAsc(bookId).stream()
                .map(BookCopyResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookCopySummaryResponse getSummaryByBookId(Long bookId) {
        if (bookId == null || bookId < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOK_ID", "Mã đầu sách không hợp lệ.");
        }
        // Count the same snapshot used by the table, without a second database query.
        List<BookCopyResponse> items = getByBookId(bookId);
        long availableCount = items.stream()
                .filter(copy -> "AVAILABLE".equals(copy.status()))
                .count();
        return new BookCopySummaryResponse(items, availableCount);
    }

    @Transactional(readOnly = true)
    public BookCopyResponse getById(Long id) {
        return BookCopyResponse.fromEntity(copies.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "COPY_NOT_FOUND", "Không tìm thấy bản sao.")));
    }

    @Transactional
    public BookCopyResponse update(Long id, UpdateBookCopyRequest request) {
        if (id == null || id < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COPY_ID", "Mã bản sao không hợp lệ.");
        }
        if (request.barcode() != null || request.bookId() != null || request.status() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COPY_FIXED_FIELDS",
                    "Không được thay đổi mã vạch, đầu sách hoặc trạng thái trong chức năng này.");
        }
        if (request.warehouseId() == null || request.warehouseId() < 1
                || request.shelfId() == null || request.shelfId() < 1 || request.physicalCondition() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COPY_DETAILS",
                    "Vui lòng chọn kho, kệ và tình trạng vật lý hợp lệ.");
        }
        if (request.notes() != null && request.notes().length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTES_TOO_LONG", "Ghi chú không được dài quá 2000 ký tự.");
        }
        BookCopy copy = copies.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "COPY_NOT_FOUND", "Không tìm thấy bản sao."));
        // Reuse the location lock and validation convention of copy creation.
        var shelf = shelves.findForCopyCreation(request.shelfId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "SHELF_NOT_FOUND", "Không tìm thấy kệ."));
        if (!shelf.getWarehouse().getId().equals(request.warehouseId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHELF_WAREHOUSE_MISMATCH", "Kệ không thuộc kho đã chọn.");
        }
        if (!shelf.isActive() || !shelf.getWarehouse().isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_INACTIVE", "Kho hoặc kệ đã ngừng sử dụng.");
        }
        String notes = request.notes() == null ? null : request.notes().trim();
        copy.updateDetails(shelf, request.physicalCondition(), notes == null || notes.isEmpty() ? null : notes);
        // Managed entity: transaction dirty checking persists only the edited fields.
        copies.flush();
        return BookCopyResponse.fromEntity(copy);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookCopyResponse repair(Long id, RepairBookCopyRequest request,
            UserPrincipal actor) {
        if (actor == null || actor.id() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Vui lòng đăng nhập.");
        }
        String reason = request == null || request.reason() == null ? "" : request.reason().strip();
        if (reason.isBlank() || reason.length() > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPAIR_REASON", "Lý do phải có từ 1 đến 2000 ký tự, không chỉ gồm khoảng trắng.");
        }
        if (id == null || id < 1) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COPY_ID", "Mã bản sao không hợp lệ.");
        BookCopy copy = copies.findForStatusChange(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "COPY_NOT_FOUND", "Không tìm thấy bản sao."));
        if (lifecycle.hasUnreturnedLoan(id) || "BORROWED".equals(copy.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "COPY_ON_LOAN", "Bản sao chưa thể sửa chữa vì đang được mượn. Cần ghi nhận trả sách trước.");
        }
        if (!"AVAILABLE".equals(copy.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "COPY_STATUS_NOT_ALLOWED", "Chỉ bản sao Sẵn sàng mới được chuyển sang Đang sửa chữa.");
        }
        String before = copy.getStatus();
        copy.sendToRepair();
        copies.flush();
        lifecycle.append(id, before, actor.id(), reason);
        return BookCopyResponse.fromEntity(copy);
    }

    @Transactional(readOnly = true)
    public List<BookCopyStatusHistoryResponse> history(Long id) {
        if (id == null || id < 1) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COPY_ID", "Mã bản sao không hợp lệ.");
        if (!copies.existsById(id)) throw new ApiException(HttpStatus.NOT_FOUND, "COPY_NOT_FOUND", "Không tìm thấy bản sao.");
        return lifecycle.history(id);
    }

    private ApiException duplicate(BookCopy copy) {
        return new ApiException(HttpStatus.CONFLICT, "BARCODE_EXISTS", "Mã vạch đã được sử dụng bởi một bản sao khác.",
                Map.of("existingCopyId", copy.getId(), "barcode", copy.getBarcode(),
                        "bookId", copy.getBook().getId(), "bookTitle", copy.getBook().getTitle(),
                        "copyUrl", "/book-copies/" + copy.getId()));
    }
}
