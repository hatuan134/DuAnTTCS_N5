package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.book.PublisherResponse;
import com.duanttcsn5.library.entity.LibraryPublisher;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.LibraryPublisherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryPublisherService {
    private final LibraryPublisherRepository repository;

    public LibraryPublisherService(LibraryPublisherRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PublisherResponse create(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank() || name.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PUBLISHER_NAME", "Tên nhà xuất bản phải từ 1 đến 255 ký tự.");
        }
        // Trả về nhà xuất bản đã có thay vì tạo bản ghi trùng.
        LibraryPublisher publisher = repository.findByNameIgnoreCase(name)
                .orElseGet(() -> repository.saveAndFlush(new LibraryPublisher(name)));
        return new PublisherResponse(publisher.getId(), publisher.getName());
    }
}
