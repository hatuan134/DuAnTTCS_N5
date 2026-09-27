package com.duanttcsn5.library.repository;

import com.duanttcsn5.library.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCaseAndParent(String name, Category parent);

    boolean existsByNameIgnoreCaseAndParentIsNull(String name);

    boolean existsByNameIgnoreCaseAndParentAndIdNot(String name, Category parent, Long id);

    boolean existsByNameIgnoreCaseAndParentIsNullAndIdNot(String name, Long id);

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId")
    List<Category> findAllByParent_Id(@Param("parentId") Long parentId);

    List<Category> findAllByOrderByCreatedAtDesc();

    List<Category> findAllByIsActiveTrueOrderByNameAsc();

    @Query("SELECT COUNT(b) FROM Book b WHERE b.category.id = :categoryId")
    long countBooksUsingCategory(@Param("categoryId") Long categoryId);
}
