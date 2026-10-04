package com.duanttcsn5.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "book_cover_images")
public class BookCoverImage {
    @Id
    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    // byte[] maps to PostgreSQL BYTEA; do not use @Lob (OID).
    @Column(name = "image_data", nullable = false, columnDefinition = "bytea")
    private byte[] imageData;

    @Column(name = "thumbnail_data", columnDefinition = "bytea")
    private byte[] thumbnailData;

    public byte[] getThumbnailData() { return thumbnailData; }
    public void setThumbnailData(byte[] thumbnailData) { this.thumbnailData = thumbnailData; }

    protected BookCoverImage() {}

    public BookCoverImage(Long bookId, String contentType, byte[] imageData) {
        this.bookId = bookId;
        this.contentType = contentType;
        this.imageData = imageData;
    }

    public Long getBookId() { return bookId; }
    public String getContentType() { return contentType; }
    public byte[] getImageData() { return imageData; }
}
