package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.bookcopy.BarcodeMode;
import com.duanttcsn5.library.dto.bookcopy.BookCopyResponse;
import com.duanttcsn5.library.dto.bookcopy.BookCopySummaryResponse;
import com.duanttcsn5.library.dto.bookcopy.CreateBookCopyRequest;
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

    public BookCopyService(BookCopyRepository copies, BookRepository books, ShelfRepository shelves) {
        this.copies = copies;
        this.books = books;
        this.shelves = shelves;
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

    private BookCopyResponse createWithManualBarcode(Long bookId, String barcode, CreateBookCopyRequest request,
            Long shelfId, LocalDate date) {
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

    @Transactional(readOnly = true)
    public void rejectUpdate(Long id) {
        getById(id);
        throw new ApiException(HttpStatus.CONFLICT, "BOOK_COPY_IMMUTABLE",
                "Không được chuyển bản sao sang đầu sách khác. Lát này chưa hỗ trợ chỉnh sửa bản sao.");
    }

    private ApiException duplicate(BookCopy copy) {
        return new ApiException(HttpStatus.CONFLICT, "BARCODE_EXISTS", "Mã vạch đã được sử dụng bởi một bản sao khác.",
                Map.of("existingCopyId", copy.getId(), "barcode", copy.getBarcode(),
                        "bookId", copy.getBook().getId(), "bookTitle", copy.getBook().getTitle(),
                        "copyUrl", "/book-copies/" + copy.getId()));
    }
}
