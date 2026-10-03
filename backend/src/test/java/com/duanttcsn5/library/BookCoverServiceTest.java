package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.Book;
import com.duanttcsn5.library.entity.BookCoverImage;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.BookCopyRepository;
import com.duanttcsn5.library.repository.BookCoverImageRepository;
import com.duanttcsn5.library.repository.BookRepository;
import com.duanttcsn5.library.service.BookCoverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookCoverServiceTest {
    private BookRepository books;
    private BookCoverImageRepository covers;
    private BookCopyRepository copies;
    private BookCoverService service;
    private Book book;

    @BeforeEach void setup() {
        books = mock(BookRepository.class);
        covers = mock(BookCoverImageRepository.class);
        copies = mock(BookCopyRepository.class);
        service = new BookCoverService(books, covers, copies);
        book = new Book(); book.setId(7L);
        when(books.findForCoverUpload(7L)).thenReturn(Optional.of(book));
    }

    private byte[] image(String format) throws Exception {
        var out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), format, out));
        return out.toByteArray();
    }
    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }
    private void invalid(MockMultipartFile file) {
        var ex = assertThrows(ApiException.class, () -> service.upload(7L, file));
        assertEquals("INVALID_BOOK_COVER", ex.getCode());
        assertEquals(BookCoverService.INVALID_MESSAGE, ex.getMessage());
        verifyNoInteractions(covers);
        verify(books, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"jpg", "png", "jpeg"})
    void validImageIsStoredUnderTheCorrectBook(String format) throws Exception {
        byte[] bytes = image(format);
        service.upload(7L, file("cover." + format, bytes));
        verify(covers).saveAndFlush(argThat(c -> c.getBookId().equals(7L)
                && Arrays.equals(bytes, c.getImageData())
                && c.getContentType().equals(format.equals("png") ? "image/png" : "image/jpeg")));
        assertEquals("/api/v1/books/public/7/cover", book.getCoverImageUrl());
        verify(books).save(book);
    }

    @Test void exactlyThreeMiBIsAccepted() throws Exception {
        byte[] bytes = Arrays.copyOf(image("png"), BookCoverService.MAX_BYTES);
        service.upload(7L, file("cover.PNG", bytes));
        verify(covers).saveAndFlush(argThat(c -> c.getImageData().length == BookCoverService.MAX_BYTES));
    }
    @Test void oneByteOverLimitPreservesExistingCover() throws Exception {
        book.setCoverImageUrl("https://example.test/old.png");
        invalid(file("cover.png", Arrays.copyOf(image("png"), BookCoverService.MAX_BYTES + 1)));
        assertEquals("https://example.test/old.png", book.getCoverImageUrl());
    }
    @Test void fakeJpgPreservesExistingCover() {
        book.setCoverImageUrl("old.png");
        invalid(file("cover.jpg", "not an image".getBytes()));
        assertEquals("old.png", book.getCoverImageUrl());
    }
    @Test void wrongExtensionIsRejected() throws Exception { invalid(file("cover.gif", image("png"))); }
    @Test void renamedPngAsJpgIsRejected() throws Exception { invalid(file("cover.jpg", image("png"))); }
    @Test void corruptPngIsRejected() throws Exception { invalid(file("cover.png", Arrays.copyOf(image("png"), 24))); }
    @Test void emptyFileIsRejected() { invalid(file("cover.png", new byte[0])); }
    @Test void missingFileIsRejected() { invalid(null); }
    @Test void missingBookIs404AndDoesNotWrite() throws Exception {
        byte[] bytes = image("png");
        var ex = assertThrows(ApiException.class, () -> service.upload(9L, file("cover.png", bytes)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verifyNoInteractions(covers);
    }
    @Test void validUploadCannotReplaceOldImage() throws Exception {
        book.setCoverImageUrl("old.png");
        byte[] bytes = image("png");
        var ex = assertThrows(ApiException.class, () -> service.upload(7L, file("cover.png", bytes)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("old.png", book.getCoverImageUrl());
        verify(covers, never()).saveAndFlush(any());
    }
    @Test void storedImageAlsoPreventsReplacementWhenUrlMissing() throws Exception {
        when(covers.existsById(7L)).thenReturn(true);
        byte[] bytes = image("png");
        assertThrows(ApiException.class, () -> service.upload(7L, file("cover.png", bytes)));
        verify(covers, never()).saveAndFlush(any());
    }
    @Test void failedStorageDoesNotSetBookUrl() throws Exception {
        when(covers.saveAndFlush(any())).thenThrow(new IllegalStateException("storage unavailable"));
        byte[] bytes = image("png");
        assertThrows(IllegalStateException.class, () -> service.upload(7L, file("cover.png", bytes)));
        assertNull(book.getCoverImageUrl());
        verify(books, never()).save(any());
    }
    @Test void publicCoverRequiresPublishedBookWithCopies() {
        assertThrows(ApiException.class, () -> service.get(7L, true));
        verifyNoInteractions(covers);
    }
    @Test void staffCanReadCoverBeforeCopiesAreCreated() {
        var cover = new BookCoverImage(7L, "image/png", new byte[]{1});
        when(covers.findById(7L)).thenReturn(Optional.of(cover));
        assertSame(cover, service.get(7L, false));
        verifyNoInteractions(copies);
    }
    @Test void publicCanReadPublishedBookCover() {
        when(copies.countByBookId(7L)).thenReturn(1L);
        var cover = new BookCoverImage(7L, "image/png", new byte[]{1});
        when(covers.findById(7L)).thenReturn(Optional.of(cover));
        assertSame(cover, service.get(7L, true));
    }
    @Test void missingCoverIs404() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.get(7L, false)).getStatus());
    }
}
