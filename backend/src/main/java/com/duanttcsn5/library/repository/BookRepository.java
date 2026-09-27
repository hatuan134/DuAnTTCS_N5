package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("SELECT b FROM Book b JOIN FETCH b.author JOIN FETCH b.category ORDER BY b.createdAt DESC")
    List<Book> findAllWithAuthorAndCategory();

    boolean existsByAuthorId(Long authorId);

    boolean existsByCategoryId(Long categoryId);

    long countByAuthorId(Long authorId);

    long countByCategoryId(Long categoryId);
}
