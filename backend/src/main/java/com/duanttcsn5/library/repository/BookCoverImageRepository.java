package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.BookCoverImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookCoverImageRepository extends JpaRepository<BookCoverImage, Long> {
    // Avoid loading the (potentially multi-megabyte) original when only the
    // 160x240 public catalog thumbnail is needed.
    @Query("select cover.thumbnailData from BookCoverImage cover where cover.bookId = :bookId")
    byte[] findThumbnailBytes(@Param("bookId") Long bookId);
}
