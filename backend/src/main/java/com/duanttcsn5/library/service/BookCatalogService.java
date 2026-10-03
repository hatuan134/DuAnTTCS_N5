package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.book.BookResponse;
import com.duanttcsn5.library.dto.book.CatalogBookRequest;
import com.duanttcsn5.library.dto.book.PublicCatalogFilterOptionsResponse;
import com.duanttcsn5.library.dto.book.PublicCatalogPageResponse;
import com.duanttcsn5.library.entity.Author;
import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.Category;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.AuthorRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookCatalogService {

    private static final ZoneId LIBRARY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogRepository auditLogRepository;

    public BookCatalogService(BookRepository bookRepository,
                              BookCopyRepository bookCopyRepository,
                              AuthorRepository authorRepository,
                              CategoryRepository categoryRepository,
                              AuditLogRepository auditLogRepository) {
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.authorRepository = authorRepository;
        this.categoryRepository = categoryRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<BookResponse> getAllBooks() {
        Map<Long, Long> availableCounts = loadAvailableCounts();
        Map<Long, Long> copyCounts = loadCopyCounts();
        return bookRepository.findAllWithAuthorAndCategory().stream()
                .map(book -> BookResponse.fromEntity(book, copyCounts.getOrDefault(book.getId(), 0L), availableCounts.getOrDefault(book.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookResponse> getPublicBooks() {
        return getPublicBooks(null);
    }

    @Transactional(readOnly = true)
    public List<BookResponse> getPublicBooks(String keyword) {
        return getPublicBooks(keyword, null, null, false);
    }

    @Transactional(readOnly = true)
    public List<BookResponse> getPublicBooks(String keyword, Long categoryId,
                                             Integer publicationYear, boolean availableOnly) {
        validatePublicFilters(categoryId, publicationYear);
        String normalizedKeyword = normalizeSearchText(keyword);
        String normalizedIsbnKeyword = normalizeIsbnForSearch(keyword);
        Map<Long, Long> availableCounts = loadAvailableCounts();
        Map<Long, Long> copyCounts = loadCopyCounts();

        return bookRepository.findAllPublicWithAuthorAndCategory().stream()
                .filter(book -> categoryId == null || (book.getCategory() != null
                        && categoryId.equals(book.getCategory().getId())))
                .filter(book -> publicationYear == null || publicationYear.equals(book.getPublicationYear()))
                .filter(book -> matchesPublicKeyword(book, normalizedKeyword, normalizedIsbnKeyword))
                .map(book -> BookResponse.fromEntity(
                        book,
                        copyCounts.getOrDefault(book.getId(), 0L),
                        availableCounts.getOrDefault(book.getId(), 0L)))
                .filter(BookResponse::hasCopies)
                .filter(book -> !availableOnly || book.availableCount() > 0)
                .toList();
    }

    /**
     * S2-05.3: lọc và sắp xếp toàn bộ tập kết quả trước khi cắt trang.
     * Giữ cách tìm không dấu và điều kiện công khai của S2-05.1/S2-05.2.
     */
    @Transactional(readOnly = true)
    public PublicCatalogPageResponse searchPublicBooks(String keyword, Long categoryId,
            Integer publicationYear, boolean availableOnly, int page, int size, String sort) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE",
                    "Số trang phải là số nguyên từ 0 trở lên.");
        }
        if (size < 1 || size > 20) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE",
                    "Mỗi trang phải có từ 1 đến tối đa 20 đầu sách.");
        }
        if (!"relevance".equals(sort) && !"publicationYear".equals(sort)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATALOG_SORT",
                    "Cách sắp xếp phải là mức phù hợp hoặc năm xuất bản.");
        }

        String text = normalizeSearchText(keyword);
        String isbn = normalizeIsbnForSearch(keyword);
        // ID duy nhất là tiêu chí cuối để các kết quả bằng điểm/năm không đổi chỗ giữa các trang.
        Comparator<BookResponse> tieBreaker = Comparator
                .comparing((BookResponse book) -> normalizeSearchText(book.title()))
                .thenComparing(BookResponse::id);
        Comparator<BookResponse> order = "publicationYear".equals(sort)
                ? Comparator.comparing(BookResponse::publicationYear,
                        Comparator.nullsLast(Comparator.<Integer>reverseOrder())).thenComparing(tieBreaker)
                : Comparator.comparingInt((BookResponse book) -> relevanceScore(book, text, isbn))
                        .reversed().thenComparing(tieBreaker);
        List<BookResponse> results = getPublicBooks(keyword, categoryId, publicationYear, availableOnly)
                .stream().sorted(order).toList();
        int totalPages = (int) ((results.size() + (long) size - 1) / size);
        // Nếu dữ liệu giảm khi đang ở trang cuối, trả trang cuối còn tồn tại.
        int currentPage = totalPages == 0 ? 0 : Math.min(page, totalPages - 1);
        int from = currentPage * size;
        int to = (int) Math.min(from + (long) size, results.size());
        return new PublicCatalogPageResponse(List.copyOf(results.subList(from, to)),
                currentPage, size, results.size(), totalPages, currentPage == 0,
                totalPages == 0 || currentPage == totalPages - 1, sort);
    }

    private int relevanceScore(BookResponse book, String text, String isbn) {
        if (text.isBlank()) return 0;
        int titleScore = fieldMatchScore(normalizeSearchText(book.title()), text, 100, 40);
        // Mỗi tiêu chí tác giả chỉ tính một lần, dù trùng author_id cũ hoặc nhiều tác giả cùng khớp.
        int authorScore = fieldMatchScore(normalizeSearchText(book.authorName()), text, 80, 30);
        for (BookResponse.BookAuthorResponse author : book.authors()) {
            authorScore = Math.max(authorScore,
                    fieldMatchScore(normalizeSearchText(author.name()), text, 80, 30));
        }
        int isbnScore = isbn.isBlank()
                ? fieldMatchScore(normalizeSearchText(book.isbn()), text, 120, 20)
                : fieldMatchScore(normalizeIsbnForSearch(book.isbn()), isbn, 120, 20);
        return titleScore + authorScore + isbnScore;
    }

    private int fieldMatchScore(String value, String keyword, int exact, int partial) {
        if (keyword.isBlank() || value.isBlank()) return 0;
        if (value.equals(keyword)) return exact;
        return value.contains(keyword) ? partial : 0;
    }

    @Transactional(readOnly = true)
    public PublicCatalogFilterOptionsResponse getPublicFilterOptions() {
        // Chỉ lấy lựa chọn từ đầu sách công khai; không phụ thuộc từ khóa/bộ lọc đang chọn.
        // Giữ cả thể loại ngừng sử dụng nếu vẫn có sách công khai thuộc thể loại đó.
        List<Book> publicBooks = bookRepository.findAllPublicWithAuthorAndCategory();
        Map<Long, PublicCatalogFilterOptionsResponse.CategoryOption> categories = new HashMap<>();
        Set<Integer> years = new HashSet<>();
        for (Book book : publicBooks) {
            Category category = book.getCategory();
            if (category != null && category.getId() != null) {
                categories.putIfAbsent(category.getId(),
                        new PublicCatalogFilterOptionsResponse.CategoryOption(category.getId(), category.getName()));
            }
            if (book.getPublicationYear() != null && book.getPublicationYear() > 0
                    && book.getPublicationYear() <= Year.now(LIBRARY_ZONE).getValue()) {
                years.add(book.getPublicationYear());
            }
        }
        return new PublicCatalogFilterOptionsResponse(
                categories.values().stream()
                        .sorted(Comparator.comparing(PublicCatalogFilterOptionsResponse.CategoryOption::name)
                                .thenComparing(PublicCatalogFilterOptionsResponse.CategoryOption::id))
                        .toList(),
                years.stream().sorted(Comparator.reverseOrder()).toList());
    }

    private void validatePublicFilters(Long categoryId, Integer publicationYear) {
        if (categoryId != null) {
            if (categoryId <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_FILTER",
                        "Mã thể loại phải là số nguyên lớn hơn 0.");
            }
            if (!categoryRepository.existsById(categoryId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_NOT_FOUND",
                        "Thể loại được chọn không tồn tại. Vui lòng chọn lại thể loại.");
            }
        }
        if (publicationYear != null && (publicationYear < 1
                || publicationYear > Year.now(LIBRARY_ZONE).getValue())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PUBLICATION_YEAR",
                    "Năm xuất bản phải là số nguyên từ 1 đến năm hiện tại.");
        }
    }

    @Transactional(readOnly = true)
    public BookResponse getPublicBookById(Long id) {
        Book book = bookRepository.findPublicByIdWithAuthorAndCategory(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PUBLIC_BOOK_NOT_FOUND",
                        "Không tìm thấy đầu sách trên trang tra cứu công khai."));

        return BookResponse.fromEntity(
                book,
                bookCopyRepository.countByBookId(id),
                bookCopyRepository.countAvailableByBookId(id));
    }

    @Transactional(readOnly = true)
    public List<String> getPublisherOptions() {
        return bookRepository.findDistinctPublishers();
    }

    @Transactional(readOnly = true)
    public BookResponse getById(Long id) {
        Book book = bookRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "Không tìm thấy đầu sách."));
        return BookResponse.fromEntity(book, bookCopyRepository.countByBookId(id), bookCopyRepository.countAvailableByBookId(id));
    }

    @Transactional
    public BookResponse catalogBook(CatalogBookRequest request, Long currentUserId, String ipAddress) {
        validateBasicBibliographicData(request);
        String isbn = validateAndNormalizeIsbn(request.isbn());

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

        String normalizedTitle = request.title().trim();
        validateDuplicateTitleConfirmation(normalizedTitle, request.confirmDuplicateTitle());

        Book book = new Book();
        book.setTitle(normalizedTitle);
        book.setSubtitle(normalizeOptional(request.subtitle()));
        book.setIsbn(isbn);
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

        // Đầu sách vừa biên mục chưa có bản sao; trạng thái được suy ra trực tiếp từ book_copies.
        return BookResponse.fromEntity(saved, 0L);
    }

    private boolean matchesPublicKeyword(Book book, String normalizedKeyword, String normalizedIsbnKeyword) {
        if (normalizedKeyword.isBlank()) {
            return true;
        }

        if (normalizeSearchText(book.getTitle()).contains(normalizedKeyword)) {
            return true;
        }

        if (book.getAuthors() != null) {
            for (Author author : book.getAuthors()) {
                if (author != null && normalizeSearchText(author.getName()).contains(normalizedKeyword)) {
                    return true;
                }
            }
        }

        Author legacyAuthor = book.getAuthor();
        if (legacyAuthor != null && normalizeSearchText(legacyAuthor.getName()).contains(normalizedKeyword)) {
            return true;
        }

        if (book.getIsbn() == null || book.getIsbn().isBlank()) {
            return false;
        }

        if (!normalizedIsbnKeyword.isBlank()) {
            return normalizeIsbnForSearch(book.getIsbn()).contains(normalizedIsbnKeyword);
        }

        return normalizeSearchText(book.getIsbn()).contains(normalizedKeyword);
    }

    private String normalizeSearchText(String input) {
        if (input == null) {
            return "";
        }

        String withoutMarks = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return withoutMarks
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizeIsbnForSearch(String input) {
        if (input == null || input.chars().anyMatch(Character::isLetter)) {
            return "";
        }
        return input.replaceAll("[^0-9]", "");
    }

    private Map<Long, Long> loadAvailableCounts() {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : bookCopyRepository.countAvailableGroupedByBookId()) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return result;
    }

    private Map<Long, Long> loadCopyCounts() {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : bookCopyRepository.countAllGroupedByBookId()) {
            if (row == null || row.length < 2 || !(row[0] instanceof Number bookId) || !(row[1] instanceof Number count)) {
                continue;
            }
            result.put(bookId.longValue(), count.longValue());
        }
        return result;
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

    private void validateDuplicateTitleConfirmation(String title, Boolean confirmDuplicateTitle) {
        if (Boolean.TRUE.equals(confirmDuplicateTitle)) {
            return;
        }

        List<Book> duplicates = bookRepository.findAllByNormalizedTitle(title);
        if (duplicates.isEmpty()) {
            return;
        }

        List<BookResponse> duplicateResponses = duplicates.stream()
                .map(BookResponse::fromEntity)
                .toList();

        throw new ApiException(
                HttpStatus.CONFLICT,
                "TITLE_ALREADY_EXISTS",
                "Nhan đề '" + title + "' đã tồn tại trong hệ thống. Vui lòng kiểm tra hồ sơ cũ hoặc xác nhận vẫn tạo đầu sách mới.",
                Map.of(
                        "field", "title",
                        "title", title,
                        "duplicates", duplicateResponses,
                        "matchingRule", "So sánh nhan đề sau khi bỏ khoảng trắng đầu/cuối và không phân biệt chữ hoa/chữ thường."
                ));
    }

    private String validateAndNormalizeIsbn(String rawIsbn) {
        String isbn = normalizeOptional(rawIsbn);
        if (isbn == null) {
            return null;
        }

        if (!isbn.matches("(?:[0-9]{10}|[0-9]{13})")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ISBN_FORMAT",
                    "ISBN phải gồm đúng 10 hoặc 13 chữ số và không chứa chữ cái hay ký tự đặc biệt.",
                    Map.of("field", "isbn"));
        }

        if (bookRepository.existsByNormalizedIsbn(isbn)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ISBN_ALREADY_EXISTS",
                    "ISBN '" + isbn + "' đã được sử dụng bởi một đầu sách khác.",
                    Map.of("field", "isbn", "isbn", isbn));
        }

        return isbn;
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
