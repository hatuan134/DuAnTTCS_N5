package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.service.BookCoverService;
import org.springframework.http.CacheControl;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Multipart and binary endpoints; existing JSON catalog endpoints stay in BookCatalogController. */
@RestController
@RequestMapping("/api/v1/books")
public class BookCoverController {
    private final BookCoverService service;

    public BookCoverController(BookCoverService service) { this.service = service; }

    @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<Void> upload(@PathVariable Long id,
                                       @RequestParam(value = "file", required = false) MultipartFile file) {
        service.upload(id, file);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/cover")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<byte[]> get(@PathVariable Long id) { return image(id, false, null); }

    @GetMapping("/public/{id}/cover")
    public ResponseEntity<byte[]> getPublic(@PathVariable Long id,
                                           @RequestParam(value = "v", required = false) String version) {
        return image(id, true, version);
    }

    @GetMapping("/public/{id}/cover/thumbnail")
    public ResponseEntity<byte[]> getPublicThumbnail(@PathVariable Long id,
                                                     @RequestParam(value = "v", required = false) String version) {
        return response(service.getThumbnail(id, true), true, version);
    }

    private ResponseEntity<byte[]> image(Long id, boolean publicView, String version) {
        return response(service.get(id, publicView), publicView, version);
    }

    private ResponseEntity<byte[]> response(com.duanttcsn5.library.entity.BookCoverImage cover,
                                            boolean publicView, String version) {
        // Each successful upload changes the public URL's ?v=UUID. A versioned URL
        // is safe to cache; legacy URLs and staff-only images must remain no-store.
        CacheControl cache = publicView && version != null && !version.isBlank()
                ? CacheControl.maxAge(Duration.ofDays(30)).cachePublic()
                : CacheControl.noStore();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cover.getContentType()))
                .contentLength(cover.getImageData().length)
                .cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff")
                .body(cover.getImageData());
    }
}
