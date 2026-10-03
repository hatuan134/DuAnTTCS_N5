package com.duanttcsn5.library.service;

import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.BookCoverImage;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookCoverImageRepository;
import com.duanttcsn5.library.repository.BookRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/** Binary storage/validation is separate from catalog search and bibliographic editing. */
@Service
public class BookCoverService {
    public static final int MAX_BYTES = 3 * 1024 * 1024;
    public static final String INVALID_MESSAGE =
            "Chỉ chấp nhận ảnh JPG hoặc PNG hợp lệ, dung lượng tối đa 3MB (3.145.728 byte).";

    private final BookRepository books;
    private final BookCoverImageRepository covers;
    private final BookCopyRepository copies;

    public BookCoverService(BookRepository books, BookCoverImageRepository covers, BookCopyRepository copies) {
        this.books = books;
        this.covers = covers;
        this.copies = copies;
    }

    @Transactional
    public void upload(Long bookId, MultipartFile file) {
        if (bookId == null || bookId < 1) throw missingBook();
        byte[] bytes = readFile(file);
        String contentType = detectImage(bytes, file.getOriginalFilename());
        // Lock the parent even before its first cover exists: two uploads cannot overwrite each other.
        Book book = books.findForCoverUpload(bookId).orElseThrow(this::missingBook);
        if ((book.getCoverImageUrl() != null && !book.getCoverImageUrl().isBlank()) || covers.existsById(bookId)) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOK_COVER_ALREADY_EXISTS",
                    "Đầu sách đã có ảnh bìa. Chức năng thay thế ảnh cũ chưa được hỗ trợ.");
        }
        covers.saveAndFlush(new BookCoverImage(bookId, contentType, bytes));
        book.setCoverImageUrl("/api/v1/books/public/" + bookId + "/cover");
        books.save(book);
    }

    @Transactional(readOnly = true)
    public BookCoverImage get(Long bookId, boolean publicView) {
        if (bookId == null || bookId < 1 || (publicView && copies.countByBookId(bookId) == 0)) {
            throw missingCover();
        }
        return covers.findById(bookId).orElseThrow(this::missingCover);
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw invalid();
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid();
            return bytes;
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private String detectImage(byte[] bytes, String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        boolean jpg = name.endsWith(".jpg") || name.endsWith(".jpeg");
        boolean png = name.endsWith(".png");
        if (!jpg && !png) throw invalid();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!(jpg && "JPEG".equalsIgnoreCase(format)) && !(png && "PNG".equalsIgnoreCase(format))) {
                    throw invalid();
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1) throw invalid();
                // Decode to verify actual image content, with bounded output memory for highly compressed images.
                var params = reader.getDefaultReadParam();
                int sample = (int) Math.max(1L, (Math.max((long) width, height) + 1023L) / 1024L);
                params.setSourceSubsampling(sample, sample, 0, 0);
                if (reader.read(0, params) == null) throw invalid();
                return jpg ? "image/jpeg" : "image/png";
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOK_COVER", INVALID_MESSAGE);
    }
    private ApiException missingBook() {
        return new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách.");
    }
    private ApiException missingCover() {
        return new ApiException(HttpStatus.NOT_FOUND, "BOOK_COVER_NOT_FOUND", "Không tìm thấy ảnh bìa đầu sách.");
    }
}
