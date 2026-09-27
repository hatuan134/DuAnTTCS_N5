package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.category.CategoryResponse;
import com.duanttcsn5.library.dto.category.CreateCategoryRequest;
import com.duanttcsn5.library.dto.category.UpdateCategoryRequest;
import com.duanttcsn5.library.entity.Category;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.AuditLogRepository;
import com.duanttcsn5.library.repository.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuditLogRepository auditLogRepository;

    public CategoryService(CategoryRepository categoryRepository, AuditLogRepository auditLogRepository) {
        this.categoryRepository = categoryRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(category -> {
                    long bookCount = categoryRepository.countBooksUsingCategory(category.getId());
                    return CategoryResponse.fromEntity(category, bookCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findAllByIsActiveTrueOrderByNameAsc().stream()
                .map(category -> {
                    long bookCount = categoryRepository.countBooksUsingCategory(category.getId());
                    return CategoryResponse.fromEntity(category, bookCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Không tìm thấy thể loại ID: " + id));
        long bookCount = categoryRepository.countBooksUsingCategory(category.getId());
        return CategoryResponse.fromEntity(category, bookCount);
    }

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request, Long currentUserId, String ipAddress) {
        String trimmedName = request.name().trim();
        Category parent = null;

        if (request.parentId() != null) {
            parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PARENT_CATEGORY_NOT_FOUND",
                            "Thể loại cha không tồn tại."));

            if (parent.getParent() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_MAX_DEPTH_EXCEEDED",
                        "Thể loại chỉ được phép xếp lồng tối đa 2 cấp.");
            }

            if (categoryRepository.existsByNameIgnoreCaseAndParent(trimmedName, parent)) {
                throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_NAME_EXISTS",
                        "Tên thể loại '" + trimmedName + "' đã tồn tại trong cùng danh mục.");
            }
        } else {
            if (categoryRepository.existsByNameIgnoreCaseAndParentIsNull(trimmedName)) {
                throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_NAME_EXISTS",
                        "Tên thể loại '" + trimmedName + "' đã tồn tại trong danh mục cấp 1.");
            }
        }

        Category category = new Category();
        category.setName(trimmedName);
        category.setParent(parent);
        category.setDescription(request.description() != null ? request.description().trim() : null);
        category.setActive(true);

        Category saved = categoryRepository.save(category);

        auditLogRepository.insert(
                currentUserId,
                "CATEGORY_CREATED",
                "CATEGORY",
                saved.getId().toString(),
                "{\"action\":\"Tạo mới thể loại\",\"name\":\"" + escapeJson(saved.getName()) +
                        "\",\"parentId\":" + (parent != null ? parent.getId() : "null") + "}",
                ipAddress
        );

        return CategoryResponse.fromEntity(saved, 0);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request, Long currentUserId, String ipAddress) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Không tìm thấy thể loại ID: " + id));

        String trimmedName = request.name().trim();
        Category parent = null;

        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_INVALID_PARENT",
                        "Thể loại không thể là cha của chính nó.");
            }

            parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PARENT_CATEGORY_NOT_FOUND",
                            "Thể loại cha không tồn tại."));

            if (parent.getParent() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_MAX_DEPTH_EXCEEDED",
                        "Thể loại chỉ được phép xếp lồng tối đa 2 cấp.");
            }

            // Nếu đang có thể loại con, không cho gán cha (để tránh biến con thành cấp 3)
            List<Category> children = categoryRepository.findAllByParent_Id(id);
            if (!children.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_HAS_CHILDREN",
                        "Thể loại đang có thể loại con nên không thể chuyển thành cấp 2.");
            }

            if (categoryRepository.existsByNameIgnoreCaseAndParentAndIdNot(trimmedName, parent, id)) {
                throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_NAME_EXISTS",
                        "Tên thể loại '" + trimmedName + "' đã tồn tại trong cùng danh mục.");
            }
        } else {
            if (categoryRepository.existsByNameIgnoreCaseAndParentIsNullAndIdNot(trimmedName, id)) {
                throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_NAME_EXISTS",
                        "Tên thể loại '" + trimmedName + "' đã tồn tại trong danh mục cấp 1.");
            }
        }

        String beforeName = category.getName();
        category.setName(trimmedName);
        category.setParent(parent);
        category.setDescription(request.description() != null ? request.description().trim() : null);

        Category updated = categoryRepository.save(category);

        auditLogRepository.insert(
                currentUserId,
                "CATEGORY_UPDATED",
                "CATEGORY",
                updated.getId().toString(),
                "{\"action\":\"Cập nhật thể loại\",\"beforeName\":\"" + escapeJson(beforeName) +
                        "\",\"afterName\":\"" + escapeJson(updated.getName()) + "\"}",
                ipAddress
        );

        long bookCount = categoryRepository.countBooksUsingCategory(id);
        return CategoryResponse.fromEntity(updated, bookCount);
    }

    @Transactional
    public CategoryResponse toggleStatus(Long id, Long currentUserId, String ipAddress) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Không tìm thấy thể loại ID: " + id));

        boolean nextActive = !category.isActive();
        category.setActive(nextActive);
        Category saved = categoryRepository.save(category);

        // Khi ngừng danh mục cha, tự động ngừng luôn tất cả các danh mục con
        if (!nextActive) {
            List<Category> children = categoryRepository.findAllByParent_Id(id);
            for (Category child : children) {
                if (child.isActive()) {
                    child.setActive(false);
                    categoryRepository.save(child);
                }
            }
        }

        String actionName = saved.isActive() ? "Kích hoạt lại thể loại" : "Ngừng sử dụng thể loại";
        auditLogRepository.insert(
                currentUserId,
                saved.isActive() ? "CATEGORY_ACTIVATED" : "CATEGORY_DEACTIVATED",
                "CATEGORY",
                saved.getId().toString(),
                "{\"action\":\"" + actionName + "\",\"name\":\"" + escapeJson(saved.getName()) +
                        "\",\"active\":" + saved.isActive() + "}",
                ipAddress
        );

        long bookCount = categoryRepository.countBooksUsingCategory(id);
        return CategoryResponse.fromEntity(saved, bookCount);
    }

    @Transactional
    public void deleteCategory(Long id, Long currentUserId, String ipAddress) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Không tìm thấy thể loại ID: " + id));

        List<Category> children = categoryRepository.findAllByParent_Id(id);
        if (!children.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_HAS_CHILDREN",
                    "Không thể xoá thể loại '" + category.getName() + "' vì đang có " + children.size() +
                            " thể loại con. Vui lòng xoá hoặc chuyển thể loại con trước.");
        }

        long bookCount = categoryRepository.countBooksUsingCategory(id);
        if (bookCount > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_IN_USE",
                    "Không thể xoá thể loại '" + category.getName() + "' vì đang gắn với " + bookCount +
                            " đầu sách. Chỉ có thể chọn ngừng sử dụng.");
        }

        String name = category.getName();
        categoryRepository.delete(category);

        auditLogRepository.insert(
                currentUserId,
                "CATEGORY_DELETED",
                "CATEGORY",
                id.toString(),
                "{\"action\":\"Xoá thể loại\",\"name\":\"" + escapeJson(name) + "\"}",
                ipAddress
        );
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
