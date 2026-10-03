package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    // Serialize reservation creation for the same title, without locking any copy.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findForReservation(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findForCoverUpload(@Param("id") Long id);


    @Query("""
            SELECT DISTINCT b
            FROM Book b
            LEFT JOIN FETCH b.authors
            LEFT JOIN FETCH b.author
            JOIN FETCH b.category
            ORDER BY b.createdAt DESC
            """)
    List<Book> findAllWithAuthorAndCategory();

    /**
     * Tra cứu công khai chỉ trả về đầu sách đã có ít nhất một bản sao.
     * Điều kiện nằm ở backend để client public không thể lấy đầu sách 0 bản sao.
     */
    @Query("""
            SELECT DISTINCT b
            FROM Book b
            LEFT JOIN FETCH b.authors
            LEFT JOIN FETCH b.author
            JOIN FETCH b.category
            WHERE EXISTS (
                SELECT bc.id
                FROM BookCopy bc
                WHERE bc.book = b
            )
            ORDER BY b.createdAt DESC
            """)
    List<Book> findAllPublicWithAuthorAndCategory();

    @Query("""
            SELECT DISTINCT b
            FROM Book b
            LEFT JOIN FETCH b.authors
            LEFT JOIN FETCH b.author
            JOIN FETCH b.category
            WHERE b.id = :id
            """)
    Optional<Book> findPublicByIdWithAuthorAndCategory(@Param("id") Long id);

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

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM books
                WHERE isbn IS NOT NULL
                  AND REGEXP_REPLACE(isbn, '[^0-9]', '', 'g') = :isbn
            )
            """, nativeQuery = true)
    boolean existsByNormalizedIsbn(@Param("isbn") String isbn);

    @Query("""
            SELECT DISTINCT b
            FROM Book b
            LEFT JOIN FETCH b.authors
            LEFT JOIN FETCH b.author
            JOIN FETCH b.category
            WHERE LOWER(TRIM(b.title)) = LOWER(TRIM(:title))
            ORDER BY b.createdAt ASC
            """)
    List<Book> findAllByNormalizedTitle(@Param("title") String title);

    boolean existsByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);

    // Shared WHERE: count and page always use the same keyword, filters and availability.
    String PUBLIC_SEARCH_WHERE = """
            FROM books b
            WHERE EXISTS (SELECT 1 FROM book_copies c WHERE c.book_id = b.id)
              AND (CAST(:categoryId AS bigint) IS NULL OR b.category_id = :categoryId)
              AND (CAST(:publicationYear AS integer) IS NULL OR b.publication_year = :publicationYear)
              AND (NOT :availableOnly OR EXISTS (
                  SELECT 1 FROM book_copies c WHERE c.book_id = b.id AND c.status = 'AVAILABLE'
                    AND NOT EXISTS (SELECT 1 FROM loan_items li
                        WHERE li.book_copy_id = c.id AND li.returned_at IS NULL)))
              AND (:text = '' OR POSITION(:text IN b.title_search) > 0
                OR EXISTS (SELECT 1 FROM authors a WHERE a.id = b.author_id
                    AND POSITION(:text IN a.name_search) > 0)
                OR EXISTS (SELECT 1 FROM book_authors ba JOIN authors a ON a.id = ba.author_id
                    WHERE ba.book_id = b.id AND POSITION(:text IN a.name_search) > 0)
                OR (:isbn <> '' AND POSITION(:isbn IN b.isbn_digits) > 0)
                OR (:isbn = '' AND POSITION(:text IN b.isbn_search) > 0))
            """;

    String PUBLIC_RELEVANCE = """
            CASE WHEN :text = '' THEN 0 ELSE
              (CASE WHEN b.title_search = :text THEN 100
                    WHEN POSITION(:text IN b.title_search) > 0 THEN 40 ELSE 0 END)
              + COALESCE((SELECT MAX(CASE WHEN a.name_search = :text THEN 80
                    WHEN POSITION(:text IN a.name_search) > 0 THEN 30 ELSE 0 END)
                  FROM (SELECT a.name_search FROM authors a WHERE a.id = b.author_id
                        UNION ALL SELECT a.name_search FROM book_authors ba
                        JOIN authors a ON a.id = ba.author_id WHERE ba.book_id = b.id) a), 0)
              + (CASE WHEN :isbn <> '' THEN
                    CASE WHEN b.isbn_digits = :isbn THEN 120
                         WHEN POSITION(:isbn IN b.isbn_digits) > 0 THEN 20 ELSE 0 END
                  ELSE CASE WHEN b.isbn_search = :text THEN 120
                         WHEN POSITION(:text IN b.isbn_search) > 0 THEN 20 ELSE 0 END END)
            END
            """;

    @Query(value = "SELECT COUNT(*) " + PUBLIC_SEARCH_WHERE, nativeQuery = true)
    long countPublicSearch(@Param("text") String text, @Param("isbn") String isbn,
            @Param("categoryId") Long categoryId, @Param("publicationYear") Integer publicationYear,
            @Param("availableOnly") boolean availableOnly);

    @Query(value = "SELECT b.id " + PUBLIC_SEARCH_WHERE + """
            ORDER BY
              CASE WHEN :sort = 'publicationYear' THEN b.publication_year END DESC NULLS LAST,
              CASE WHEN :sort = 'relevance' THEN
            """ + PUBLIC_RELEVANCE + """
              END DESC,
              b.title_search COLLATE "C" ASC, b.id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Long> findPublicSearchIds(@Param("text") String text, @Param("isbn") String isbn,
            @Param("categoryId") Long categoryId, @Param("publicationYear") Integer publicationYear,
            @Param("availableOnly") boolean availableOnly, @Param("sort") String sort,
            @Param("limit") int limit, @Param("offset") long offset);

    // Fetch joins only AFTER SQL has selected <= 20 IDs, so JPA never pages a collection join.
    @Query("""
            SELECT DISTINCT b FROM Book b LEFT JOIN FETCH b.authors
            LEFT JOIN FETCH b.author JOIN FETCH b.category WHERE b.id IN :ids
            """)
    List<Book> findPublicPageWithAuthorAndCategory(@Param("ids") List<Long> ids);

    @Query(value = """
            SELECT DISTINCT b.category_id, c.name, b.publication_year
            FROM books b JOIN categories c ON c.id = b.category_id
            WHERE EXISTS (SELECT 1 FROM book_copies bc WHERE bc.book_id = b.id)
            """, nativeQuery = true)
    List<Object[]> findPublicFilterRows();
}
