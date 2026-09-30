package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.bookcopy.BookCopyResponse;
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
import java.util.Map;

@Service
public class BookCopyService {
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
        String barcode = request.barcode().trim();
        copies.findByBarcode(barcode).ifPresent(copy -> { throw duplicate(copy); });
        var shelf = shelves.findForCopyCreation(request.shelfId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHELF_NOT_FOUND", "Không tìm thấy kệ."));
        if (!shelf.getWarehouse().getId().equals(request.warehouseId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHELF_WAREHOUSE_MISMATCH", "Kệ không thuộc kho đã chọn.");
        }
        if (!shelf.isActive() || !shelf.getWarehouse().isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_INACTIVE", "Kho hoặc kệ đã ngừng sử dụng.");
        }
        int inserted = copies.insertIfBarcodeAbsent(bookId, barcode, shelf.getId(), date,
                request.coverPrice(), request.physicalCondition().name());
        BookCopy copy = copies.findByBarcode(barcode).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "COPY_RETRY", "Dữ liệu vừa thay đổi. Vui lòng thử lại."));
        if (inserted == 0) { throw duplicate(copy); }
        return BookCopyResponse.fromEntity(copy);
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
