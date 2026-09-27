package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.author.AuthorResponse;
import com.duanttcsn5.library.dto.author.CreateAuthorRequest;
import com.duanttcsn5.library.dto.author.UpdateAuthorRequest;
import com.duanttcsn5.library.entity.Author;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.AuthorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final AuditLogRepository auditLogRepository;

    public AuthorService(AuthorRepository authorRepository, AuditLogRepository auditLogRepository) {
        this.authorRepository = authorRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<AuthorResponse> getAllAuthors() {
        return authorRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(author -> {
                    long bookCount = authorRepository.countBooksUsingAuthor(author.getId());
                    return AuthorResponse.fromEntity(author, bookCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuthorResponse> getActiveAuthors() {
        return authorRepository.findAllByIsActiveTrueOrderByNameAsc().stream()
                .map(author -> {
                    long bookCount = authorRepository.countBooksUsingAuthor(author.getId());
                    return AuthorResponse.fromEntity(author, bookCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AuthorResponse getAuthorById(Long id) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUTHOR_NOT_FOUND", "Không tìm thấy tác giả ID: " + id));
        long bookCount = authorRepository.countBooksUsingAuthor(author.getId());
        return AuthorResponse.fromEntity(author, bookCount);
    }

    @Transactional
    public AuthorResponse createAuthor(CreateAuthorRequest request, Long currentUserId, String ipAddress) {
        String trimmedName = request.name().trim();
        if (authorRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new ApiException(HttpStatus.CONFLICT, "AUTHOR_NAME_EXISTS",
                    "Tên tác giả '" + trimmedName + "' đã tồn tại trong danh mục tác giả.");
        }

        Author author = new Author();
        author.setName(trimmedName);
        author.setNote(request.note() != null ? request.note().trim() : null);
        author.setActive(true);

        Author saved = authorRepository.save(author);

        auditLogRepository.insert(
                currentUserId,
                "AUTHOR_CREATED",
                "AUTHOR",
                saved.getId().toString(),
                "{\"action\":\"Tạo mới tác giả\",\"name\":\"" + escapeJson(saved.getName()) + "\"}",
                ipAddress
        );

        return AuthorResponse.fromEntity(saved, 0);
    }

    @Transactional
    public AuthorResponse updateAuthor(Long id, UpdateAuthorRequest request, Long currentUserId, String ipAddress) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUTHOR_NOT_FOUND", "Không tìm thấy tác giả ID: " + id));

        String trimmedName = request.name().trim();
        if (authorRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "AUTHOR_NAME_EXISTS",
                    "Tên tác giả '" + trimmedName + "' đã tồn tại trong danh mục tác giả.");
        }

        String beforeName = author.getName();
        author.setName(trimmedName);
        author.setNote(request.note() != null ? request.note().trim() : null);

        Author updated = authorRepository.save(author);

        auditLogRepository.insert(
                currentUserId,
                "AUTHOR_UPDATED",
                "AUTHOR",
                updated.getId().toString(),
                "{\"action\":\"Cập nhật tác giả\",\"beforeName\":\"" + escapeJson(beforeName) + "\",\"afterName\":\"" + escapeJson(updated.getName()) + "\"}",
                ipAddress
        );

        long bookCount = authorRepository.countBooksUsingAuthor(id);
        return AuthorResponse.fromEntity(updated, bookCount);
    }

    @Transactional
    public AuthorResponse toggleStatus(Long id, Long currentUserId, String ipAddress) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUTHOR_NOT_FOUND", "Không tìm thấy tác giả ID: " + id));

        author.setActive(!author.isActive());
        Author saved = authorRepository.save(author);

        String actionName = saved.isActive() ? "Kích hoạt lại tác giả" : "Ngừng sử dụng tác giả";
        auditLogRepository.insert(
                currentUserId,
                saved.isActive() ? "AUTHOR_ACTIVATED" : "AUTHOR_DEACTIVATED",
                "AUTHOR",
                saved.getId().toString(),
                "{\"action\":\"" + actionName + "\",\"name\":\"" + escapeJson(saved.getName()) + "\",\"active\":" + saved.isActive() + "}",
                ipAddress
        );

        long bookCount = authorRepository.countBooksUsingAuthor(id);
        return AuthorResponse.fromEntity(saved, bookCount);
    }

    @Transactional
    public void deleteAuthor(Long id, Long currentUserId, String ipAddress) {
        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUTHOR_NOT_FOUND", "Không tìm thấy tác giả ID: " + id));

        long bookCount = authorRepository.countBooksUsingAuthor(id);
        if (bookCount > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTHOR_IN_USE",
                    "Không thể xoá tác giả '" + author.getName() + "' vì đang gắn với " + bookCount + " đầu sách. Chỉ có thể chọn ngừng sử dụng.");
        }

        String name = author.getName();
        authorRepository.delete(author);

        auditLogRepository.insert(
                currentUserId,
                "AUTHOR_DELETED",
                "AUTHOR",
                id.toString(),
                "{\"action\":\"Xoá tác giả\",\"name\":\"" + escapeJson(name) + "\"}",
                ipAddress
        );
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
