package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuthorRepository extends JpaRepository<Author, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Author> findAllByOrderByCreatedAtDesc();

    List<Author> findAllByIsActiveTrueOrderByNameAsc();

    @Query("SELECT COUNT(b) FROM Book b WHERE b.author.id = :authorId")
    long countBooksUsingAuthor(@Param("authorId") Long authorId);
}
