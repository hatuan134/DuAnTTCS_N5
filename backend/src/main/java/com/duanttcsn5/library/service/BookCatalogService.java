package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.dto.book.CatalogBookRequest;
import com.duanttcsn5.library.entity.Author;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.Category;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.AuthorRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookCatalogService {

    private static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogRepository auditLogRepository;

    public BookCatalogService(BookRepository bookRepository,
                              AuthorRepository authorRepository,
                              CategoryRepository categoryRepository,
                              AuditLogRepository auditLogRepository) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.categoryRepository = categoryRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<BookResponse> getAllBooks() {
        return bookRepository.findAllWithAuthorAndCategory().stream()
                .map(BookResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getPublisherOptions() {
        return bookRepository.findDistinctPublishers();
    }

    @Transactional(readOnly = true)
    public BookResponse getById(Long id) {
        return BookResponse.fromEntity(bookRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách.")));
    }

    @Transactional
    public BookResponse catalogBook(CatalogBookRequest request, Long currentUserId, String ipAddress) {
        validateBasicBibliographicData(request);

        List<Author> authors = resolveAuthors(request, currentUserId, ipAddress);

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND",
                        "Không tìm thấy thể loại ID: " + request.categoryId()));

        if (!category.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_INACTIVE",
                    "Không thể biên mục sách với thể loại '" + category.getName()
                            + "' đã ngừng sử dụng. Vui lòng chọn thể loại đang hoạt động.");
        }

        String publisher = request.publisher().trim();
        if (!bookRepository.existsPublisherInCatalog(publisher)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PUBLISHER_NOT_IN_CATALOG",
                    "Nhà xuất bản '" + publisher + "' chưa có trong danh mục hiện tại. Vui lòng chọn nhà xuất bản có sẵn.");
        }

        Book book = new Book();
        book.setTitle(request.title().trim());
        book.setSubtitle(normalizeOptional(request.subtitle()));
        book.setIsbn(normalizeOptional(request.isbn()));
        // Giữ author_id là tác giả đầu tiên để tương thích dữ liệu/chức năng cũ.
        book.setAuthor(authors.get(0));
        book.setAuthors(authors);
        book.setCategory(category);
        book.setPublisher(publisher);
        book.setPublicationYear(request.publicationYear());
        book.setPageCount(request.pageCount());
        book.setDescription(normalizeOptional(request.description()));

        Book saved = bookRepository.save(book);

        String authorNames = authors.stream()
                .map(Author::getName)
                .collect(Collectors.joining(", "));

        auditLogRepository.insert(
                currentUserId,
                "BOOK_CATALOGED",
                "BOOK",
                saved.getId().toString(),
                "{\"action\":\"Biên mục sách mới\",\"title\":\"" + escapeJson(saved.getTitle())
                        + "\",\"authors\":\"" + escapeJson(authorNames)
                        + "\",\"category\":\"" + escapeJson(category.getName()) + "\"}",
                ipAddress
        );

        return BookResponse.fromEntity(saved);
    }

    private void validateBasicBibliographicData(CatalogBookRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOK_REQUEST", "Dữ liệu đầu sách không được để trống.");
        }
        if (request.title() == null || request.title().trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TITLE_REQUIRED", "Nhan đề không được để trống.");
        }
        if (request.categoryId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_REQUIRED", "Vui lòng chọn thể loại.");
        }
        if (request.publisher() == null || request.publisher().trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PUBLISHER_REQUIRED", "Vui lòng chọn nhà xuất bản.");
        }
        if (request.publicationYear() == null || request.publicationYear() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PUBLICATION_YEAR",
                    "Năm xuất bản phải là số nguyên lớn hơn 0.");
        }

        int currentYear = Year.now(LIBRARY_ZONE).getValue();
        if (request.publicationYear() > currentYear) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PUBLICATION_YEAR",
                    "Năm xuất bản không được lớn hơn năm hiện tại (" + currentYear + ").");
        }

        if (request.pageCount() == null || request.pageCount() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_COUNT",
                    "Số trang phải là số nguyên lớn hơn 0.");
        }
    }

    private List<Author> resolveAuthors(CatalogBookRequest request, Long currentUserId, String ipAddress) {
        if (request.authorIds() != null && !request.authorIds().isEmpty()) {
            return resolveSelectedAuthors(request.authorIds());
        }

        // Tương thích request cũ của S2-01.1.
        if (request.authorId() != null) {
            Author author = authorRepository.findById(request.authorId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "AUTHOR_NOT_FOUND",
                            "Không tìm thấy tác giả ID: " + request.authorId()));
            ensureAuthorActive(author);
            return List.of(author);
        }

        // Hành vi cũ được giữ để không phá client cũ; frontend S2-01.2 không sử dụng nhánh này.
        String authorName = request.authorName() == null ? "" : request.authorName().trim();
        if (authorName.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AUTHOR_REQUIRED",
                    "Vui lòng chọn ít nhất một tác giả từ danh mục tác giả.");
        }

        Author author = authorRepository.findByNameIgnoreCase(authorName)
                .map(existing -> {
                    ensureAuthorActive(existing);
                    return existing;
                })
                .orElseGet(() -> {
                    Author created = new Author(authorName, "Tự động tạo khi biên mục đầu sách", true);
                    Author saved = authorRepository.save(created);
                    auditLogRepository.insert(
                            currentUserId,
                            "AUTHOR_CREATED",
                            "AUTHOR",
                            saved.getId().toString(),
                            "{\"action\":\"Tạo tác giả mới khi biên mục sách\",\"name\":\""
                                    + escapeJson(saved.getName()) + "\"}",
                            ipAddress
                    );
                    return saved;
                });
        return List.of(author);
    }

    private List<Author> resolveSelectedAuthors(List<Long> authorIds) {
        Set<Long> seen = new HashSet<>();
        List<Author> resolved = new ArrayList<>();

        for (Long authorId : authorIds) {
            if (authorId == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "AUTHOR_REQUIRED",
                        "Danh sách tác giả chứa giá trị không hợp lệ.");
            }
            if (!seen.add(authorId)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_AUTHOR",
                        "Không thể chọn cùng một tác giả nhiều lần cho một đầu sách.");
            }

            Author author = authorRepository.findById(authorId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "AUTHOR_NOT_FOUND",
                            "Không tìm thấy tác giả ID: " + authorId));
            ensureAuthorActive(author);
            resolved.add(author);
        }

        if (resolved.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AUTHOR_REQUIRED",
                    "Vui lòng chọn ít nhất một tác giả từ danh mục tác giả.");
        }

        return List.copyOf(resolved);
    }

    private void ensureAuthorActive(Author author) {
        if (!author.isActive()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AUTHOR_INACTIVE",
                    "Tác giả '" + author.getName()
                            + "' đã ngừng sử dụng. Vui lòng kích hoạt lại tác giả trước khi biên mục sách.");
        }
    }

    private String normalizeOptional(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
