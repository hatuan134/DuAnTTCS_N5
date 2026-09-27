package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.category.CategoryResponse;
import com.duanttcsn5.library.dto.category.CreateCategoryRequest;
import com.duanttcsn5.library.dto.category.UpdateCategoryRequest;
import com.duanttcsn5.library.entity.Category;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.CategoryRepository;
import com.duanttcsn5.library.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, auditLogRepository);
    }

    @Test
    @DisplayName("Tạo thể loại cấp 1 thành công")
    void createCategory_Level1_Success() {
        CreateCategoryRequest request = new CreateCategoryRequest("Văn học", "Mô tả văn học", null);

        when(categoryRepository.existsByNameIgnoreCaseAndParentIsNull("Văn học")).thenReturn(false);

        Category saved = new Category("Văn học", null, "Mô tả văn học", true);
        saved.setId(1L);
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryResponse response = categoryService.createCategory(request, 10L, "127.0.0.1");

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("Văn học", response.name());
        assertNull(response.parentId());
        assertEquals(1, response.level());
        assertTrue(response.active());

        verify(auditLogRepository).insert(eq(10L), eq("CATEGORY_CREATED"), eq("CATEGORY"), eq("1"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Tạo thể loại cấp 2 thành công dưới thể loại cấp 1")
    void createCategory_Level2_Success() {
        Category parent = new Category("Văn học", null, "Cấp 1", true);
        parent.setId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByNameIgnoreCaseAndParent("Văn học trong nước", parent)).thenReturn(false);

        Category child = new Category("Văn học trong nước", parent, "Cấp 2", true);
        child.setId(2L);
        when(categoryRepository.save(any(Category.class))).thenReturn(child);

        CreateCategoryRequest request = new CreateCategoryRequest("Văn học trong nước", "Cấp 2", 1L);
        CategoryResponse response = categoryService.createCategory(request, 10L, "127.0.0.1");

        assertNotNull(response);
        assertEquals(2L, response.id());
        assertEquals(1L, response.parentId());
        assertEquals("Văn học", response.parentName());
        assertEquals(2, response.level());
    }

    @Test
    @DisplayName("Từ chối tạo thể loại vượt quá 2 cấp")
    void createCategory_DepthOver2_ThrowsBadRequest() {
        Category grandParent = new Category("Văn học", null, "Cấp 1", true);
        grandParent.setId(1L);

        Category parent = new Category("Văn học trong nước", grandParent, "Cấp 2", true);
        parent.setId(2L);

        when(categoryRepository.findById(2L)).thenReturn(Optional.of(parent));

        CreateCategoryRequest request = new CreateCategoryRequest("Truyện ngắn hiện đại", "Cấp 3", 2L);

        ApiException ex = assertThrows(ApiException.class, () ->
                categoryService.createCategory(request, 10L, "127.0.0.1")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("CATEGORY_MAX_DEPTH_EXCEEDED", ex.getCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Từ chối tạo thể loại khi tên bị trùng trong cùng danh mục cha")
    void createCategory_DuplicateNameInSameParent_ThrowsConflict() {
        Category parent = new Category("Văn học", null, "Cấp 1", true);
        parent.setId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByNameIgnoreCaseAndParent("Văn học trong nước", parent)).thenReturn(true);

        CreateCategoryRequest request = new CreateCategoryRequest("Văn học trong nước", "Mô tả", 1L);

        ApiException ex = assertThrows(ApiException.class, () ->
                categoryService.createCategory(request, 10L, "127.0.0.1")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("CATEGORY_NAME_EXISTS", ex.getCode());
    }

    @Test
    @DisplayName("Từ chối khi thể loại chọn chính nó làm cha")
    void updateCategory_SelfAsParent_ThrowsBadRequest() {
        Category category = new Category("Văn học", null, "Mô tả", true);
        category.setId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        UpdateCategoryRequest request = new UpdateCategoryRequest("Văn học", "Mô tả", 1L);

        ApiException ex = assertThrows(ApiException.class, () ->
                categoryService.updateCategory(1L, request, 10L, "127.0.0.1")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("CATEGORY_INVALID_PARENT", ex.getCode());
    }

    @Test
    @DisplayName("Từ chối chuyển thể loại đang có con thành cấp 2")
    void updateCategory_HasChildren_CannotBecomeLevel2() {
        Category category = new Category("Văn học", null, "Cấp 1", true);
        category.setId(1L);

        Category potentialParent = new Category("Tổng hợp", null, "Cấp 1 khác", true);
        potentialParent.setId(99L);

        Category child = new Category("Văn học trong nước", category, "Cấp 2", true);
        child.setId(2L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findById(99L)).thenReturn(Optional.of(potentialParent));
        when(categoryRepository.findAllByParentId(1L)).thenReturn(List.of(child));

        UpdateCategoryRequest request = new UpdateCategoryRequest("Văn học", "Mô tả", 99L);

        ApiException ex = assertThrows(ApiException.class, () ->
                categoryService.updateCategory(1L, request, 10L, "127.0.0.1")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("CATEGORY_HAS_CHILDREN", ex.getCode());
    }

    @Test
    @DisplayName("Ngừng sử dụng thể loại cha thì tự động ngừng sử dụng tất cả thể loại con")
    void toggleStatus_DeactivateParent_CascadesToChildren() {
        Category parent = new Category("Văn học", null, "Cấp 1", true);
        parent.setId(1L);

        Category child1 = new Category("Văn học trong nước", parent, "Cấp 2", true);
        child1.setId(2L);
        Category child2 = new Category("Văn học nước ngoài", parent, "Cấp 2", true);
        child2.setId(3L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(parent)).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findAllByParentId(1L)).thenReturn(List.of(child1, child2));
        when(categoryRepository.countBooksUsingCategory(1L)).thenReturn(10L);

        CategoryResponse response = categoryService.toggleStatus(1L, 10L, "127.0.0.1");

        assertFalse(response.active());
        assertFalse(child1.isActive());
        assertFalse(child2.isActive());
        verify(categoryRepository).save(child1);
        verify(categoryRepository).save(child2);
        verify(auditLogRepository).insert(eq(10L), eq("CATEGORY_DEACTIVATED"), eq("CATEGORY"), eq("1"), anyString(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Không cho phép xoá thể loại đang gắn với đầu sách")
    void deleteCategory_LinkedToBooks_ThrowsBadRequest() {
        Category category = new Category("Văn học", null, "Mô tả", true);
        category.setId(1L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findAllByParentId(1L)).thenReturn(Collections.emptyList());
        when(categoryRepository.countBooksUsingCategory(1L)).thenReturn(25L);

        ApiException ex = assertThrows(ApiException.class, () ->
                categoryService.deleteCategory(1L, 10L, "127.0.0.1")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("CATEGORY_IN_USE", ex.getCode());
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Không cho phép xoá thể loại đang có thể loại con")
    void deleteCategory_HasChildren_ThrowsBadRequest() {
        Category parent = new Category("Văn học", null, "Mô tả", true);
        parent.setId(1L);
        Category child = new Category("Văn học trong nước", parent, "Mô tả", true);
        child.setId(2L);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.findAllByParentId(1L)).thenReturn(List.of(child));

        ApiException ex = assertThrows(ApiException.class, () ->
                categoryService.deleteCategory(1L, 10L, "127.0.0.1")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("CATEGORY_HAS_CHILDREN", ex.getCode());
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xoá thể loại thành công khi không có sách và không có thể loại con")
    void deleteCategory_NoChildrenAndNoBooks_Success() {
        Category category = new Category("Thể loại rỗng", null, "Mô tả", true);
        category.setId(5L);

        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(categoryRepository.findAllByParentId(5L)).thenReturn(Collections.emptyList());
        when(categoryRepository.countBooksUsingCategory(5L)).thenReturn(0L);

        categoryService.deleteCategory(5L, 10L, "127.0.0.1");

        verify(categoryRepository).delete(category);
        verify(auditLogRepository).insert(eq(10L), eq("CATEGORY_DELETED"), eq("CATEGORY"), eq("5"), anyString(), eq("127.0.0.1"));
    }
}
