package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.author.AuthorResponse;
import com.duanttcsn5.library.dto.author.CreateAuthorRequest;
import com.duanttcsn5.library.dto.author.UpdateAuthorRequest;
import com.duanttcsn5.library.entity.Author;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.AuthorRepository;
import com.duanttcsn5.library.service.AuthorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorServiceTest {

    @Mock
    private AuthorRepository authorRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuthorService authorService;

    @BeforeEach
    void setUp() {
        authorService = new AuthorService(authorRepository, auditLogRepository);
    }

    @Test
    @DisplayName("Tạo tác giả thành công khi tên chưa tồn tại")
    void createAuthor_Success() {
        CreateAuthorRequest request = new CreateAuthorRequest("Nguyễn Nhật Ánh", "Nhà văn Việt Nam");

        when(authorRepository.existsByNameIgnoreCase("Nguyễn Nhật Ánh")).thenReturn(false);

        Author saved = new Author("Nguyễn Nhật Ánh", "Nhà văn Việt Nam", true);
        saved.setId(1L);
        when(authorRepository.save(any(Author.class))).thenReturn(saved);

        AuthorResponse response = authorService.createAuthor(request, 10L, "127.0.0.1");

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("Nguyễn Nhật Ánh", response.name());
        assertEquals("Nhà văn Việt Nam", response.note());
        assertTrue(response.active());
        assertEquals(0, response.bookCount());

        verify(auditLogRepository).insert(eq(10L), eq("AUTHOR_CREATED"), eq("AUTHOR"), eq("1"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Từ chối tạo tác giả khi tên bị trùng")
    void createAuthor_DuplicateName_ThrowsConflict() {
        CreateAuthorRequest request = new CreateAuthorRequest("  Nguyễn Nhật Ánh  ", "Ghi chú");

        when(authorRepository.existsByNameIgnoreCase("Nguyễn Nhật Ánh")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                authorService.createAuthor(request, 10L, "127.0.0.1")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("AUTHOR_NAME_EXISTS", ex.getCode());
        verify(authorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật tác giả thành công")
    void updateAuthor_Success() {
        Author author = new Author("Tô Hoài", "Tác giả cũ", true);
        author.setId(2L);

        when(authorRepository.findById(2L)).thenReturn(Optional.of(author));
        when(authorRepository.existsByNameIgnoreCaseAndIdNot("Tô Hoài mới", 2L)).thenReturn(false);
        when(authorRepository.save(any(Author.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorRepository.countBooksUsingAuthor(2L)).thenReturn(5L);

        UpdateAuthorRequest request = new UpdateAuthorRequest("Tô Hoài mới", "Ghi chú mới");
        AuthorResponse response = authorService.updateAuthor(2L, request, 10L, "127.0.0.1");

        assertEquals("Tô Hoài mới", response.name());
        assertEquals("Ghi chú mới", response.note());
        assertEquals(5L, response.bookCount());
        verify(auditLogRepository).insert(eq(10L), eq("AUTHOR_UPDATED"), eq("AUTHOR"), eq("2"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Từ chối cập nhật tác giả khi tên mới trùng với tác giả khác")
    void updateAuthor_DuplicateName_ThrowsConflict() {
        Author author = new Author("Tô Hoài", "Tác giả cũ", true);
        author.setId(2L);

        when(authorRepository.findById(2L)).thenReturn(Optional.of(author));
        when(authorRepository.existsByNameIgnoreCaseAndIdNot("Nam Cao", 2L)).thenReturn(true);

        UpdateAuthorRequest request = new UpdateAuthorRequest("Nam Cao", "Ghi chú");
        ApiException ex = assertThrows(ApiException.class, () ->
                authorService.updateAuthor(2L, request, 10L, "127.0.0.1")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("AUTHOR_NAME_EXISTS", ex.getCode());
    }

    @Test
    @DisplayName("Chuyển đổi trạng thái ngừng sử dụng / kích hoạt lại tác giả")
    void toggleStatus_TogglesActiveState() {
        Author author = new Author("Vũ Trọng Phụng", "Nhà văn", true);
        author.setId(3L);

        when(authorRepository.findById(3L)).thenReturn(Optional.of(author));
        when(authorRepository.save(any(Author.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorRepository.countBooksUsingAuthor(3L)).thenReturn(3L);

        AuthorResponse response = authorService.toggleStatus(3L, 10L, "127.0.0.1");

        assertFalse(response.active());
        verify(auditLogRepository).insert(eq(10L), eq("AUTHOR_DEACTIVATED"), eq("AUTHOR"), eq("3"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Không cho phép xoá tác giả đang gắn với ít nhất một đầu sách")
    void deleteAuthor_LinkedToBooks_ThrowsBadRequest() {
        Author author = new Author("Nguyễn Nhật Ánh", "Ghi chú", true);
        author.setId(1L);

        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(authorRepository.countBooksUsingAuthor(1L)).thenReturn(12L);

        ApiException ex = assertThrows(ApiException.class, () ->
                authorService.deleteAuthor(1L, 10L, "127.0.0.1")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("AUTHOR_IN_USE", ex.getCode());
        assertTrue(ex.getMessage().contains("12 đầu sách"));
        verify(authorRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xoá tác giả thành công khi không gắn với đầu sách nào")
    void deleteAuthor_NotLinkedToBooks_Success() {
        Author author = new Author("Tác giả mới", "Chưa có sách", true);
        author.setId(5L);

        when(authorRepository.findById(5L)).thenReturn(Optional.of(author));
        when(authorRepository.countBooksUsingAuthor(5L)).thenReturn(0L);

        authorService.deleteAuthor(5L, 10L, "127.0.0.1");

        verify(authorRepository).delete(author);
        verify(auditLogRepository).insert(eq(10L), eq("AUTHOR_DELETED"), eq("AUTHOR"), eq("5"), anyString(), eq("127.0.0.1"));
    }
}
