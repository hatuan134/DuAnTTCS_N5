package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("""
            SELECT DISTINCT b
            FROM Book b
            LEFT JOIN FETCH b.authors
            LEFT JOIN FETCH b.author
            JOIN FETCH b.category
            ORDER BY b.createdAt DESC
            """)
    List<Book> findAllWithAuthorAndCategory();

    @Query(value = """
            SELECT DISTINCT BTRIM(publisher)
            FROM books
            WHERE publisher IS NOT NULL
              AND BTRIM(publisher) <> ''
            ORDER BY 1
            """, nativeQuery = true)
    List<String> findDistinctPublishers();

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM books
                WHERE publisher IS NOT NULL
                  AND LOWER(BTRIM(publisher)) = LOWER(BTRIM(:publisher))
            )
            """, nativeQuery = true)
    boolean existsPublisherInCatalog(@Param("publisher") String publisher);

    boolean existsByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);
}
