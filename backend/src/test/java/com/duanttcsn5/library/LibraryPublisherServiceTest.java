package com.duanttcsn5.library;

import com.duanttcsn5.library.entity.LibraryPublisher;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LibraryPublisherRepository;
import com.duanttcsn5.library.service.LibraryPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibraryPublisherServiceTest {
    @Mock
    private LibraryPublisherRepository repository;
    private LibraryPublisherService service;

    @BeforeEach
    void setUp() {
        service = new LibraryPublisherService(repository);
    }

    @Test
    void createPersistsBeforeSavingBook() {
        when(repository.findByNameIgnoreCase("NXB Trẻ")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(LibraryPublisher.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals("NXB Trẻ", service.create("  NXB Trẻ  ").name());
        verify(repository).saveAndFlush(any(LibraryPublisher.class));
    }

    @Test
    void existingNameIsReused() {
        when(repository.findByNameIgnoreCase("NXB Trẻ")).thenReturn(Optional.of(new LibraryPublisher("NXB Trẻ")));
        assertEquals("NXB Trẻ", service.create("NXB Trẻ").name());
        verify(repository, never()).saveAndFlush(any(LibraryPublisher.class));
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(ApiException.class, () -> service.create("  "));
        verify(repository, never()).saveAndFlush(any(LibraryPublisher.class));
    }
}
