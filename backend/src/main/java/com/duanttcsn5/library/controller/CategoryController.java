package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.category.CategoryResponse;
import com.duanttcsn5.library.dto.category.CreateCategoryRequest;
import com.duanttcsn5.library.dto.category.UpdateCategoryRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.CategoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/active")
    public ResponseEntity<List<CategoryResponse>> getActiveCategories() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CreateCategoryRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CategoryResponse response = categoryService.createCategory(request, userId, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CategoryResponse response = categoryService.updateCategory(id, request, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<CategoryResponse> toggleStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        CategoryResponse response = categoryService.toggleStatus(id, userId, ipAddress);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        Long userId = principal != null ? principal.id() : null;
        categoryService.deleteCategory(id, userId, ipAddress);
        return ResponseEntity.noContent().build();
    }
}
