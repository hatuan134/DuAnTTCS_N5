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

import java.util.List;

@Service
public class BookCatalogService {

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

    @Transactional
    public BookResponse catalogBook(CatalogBookRequest request, Long currentUserId, String ipAddress) {
        Author author = authorRepository.findById(request.authorId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUTHOR_NOT_FOUND", "Không tìm thấy tác giả ID: " + request.authorId()));

        if (!author.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTHOR_INACTIVE",
                    "Không thể biên mục sách với tác giả '" + author.getName() + "' đã ngừng sử dụng. Vui lòng chọn tác giả đang hoạt động.");
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Không tìm thấy thể loại ID: " + request.categoryId()));

        if (!category.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_INACTIVE",
                    "Không thể biên mục sách với thể loại '" + category.getName() + "' đã ngừng sử dụng. Vui lòng chọn thể loại đang hoạt động.");
        }

        Book book = new Book();
        book.setTitle(request.title().trim());
        book.setIsbn(request.isbn() != null ? request.isbn().trim() : null);
        book.setAuthor(author);
        book.setCategory(category);
        book.setPublisher(request.publisher() != null ? request.publisher().trim() : null);
        book.setPublicationYear(request.publicationYear());
        book.setDescription(request.description() != null ? request.description().trim() : null);

        Book saved = bookRepository.save(book);

        auditLogRepository.insert(
                currentUserId,
                "BOOK_CATALOGED",
                "BOOK",
                saved.getId().toString(),
                "{\"action\":\"Biên mục sách mới\",\"title\":\"" + escapeJson(saved.getTitle()) +
                        "\",\"author\":\"" + escapeJson(author.getName()) +
                        "\",\"category\":\"" + escapeJson(category.getName()) + "\"}",
                ipAddress
        );

        return BookResponse.fromEntity(saved);
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
