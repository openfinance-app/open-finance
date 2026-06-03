package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.CategoryRequest;
import org.openfinance.dto.CategoryResponse;
import org.openfinance.dto.CategoryTreeNode;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.User;
import org.openfinance.service.CategoryService;
import org.openfinance.util.EncryptionUtil;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for category management endpoints.
 *
 * <p>Provides CRUD operations for transaction categories (income and expense). Categories help
 * users organize and classify their transactions. All endpoints require authentication and use the
 * user's encryption key to secure category names.
 *
 * <p><strong>Endpoints:</strong>
 *
 * <ul>
 *   <li>POST /api/v1/categories - Create new category
 *   <li>GET /api/v1/categories - List all user categories
 *   <li>GET /api/v1/categories/{id} - Get category by ID
 *   <li>PUT /api/v1/categories/{id} - Update category
 *   <li>DELETE /api/v1/categories/{id} - Delete category
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>All endpoints require JWT authentication
 *   <li>Encryption key must be provided via X-Encryption-Session header
 *   <li>Users can only access their own categories
 *   <li>Category names are encrypted at rest
 *   <li>System categories (default categories) cannot be deleted
 * </ul>
 *
 * <p>Requirement REQ-2.4: Category Management - CRUD operations
 *
 * <p>Requirement REQ-2.4.1: Create, read, update, delete categories
 *
 * <p>Requirement REQ-2.4.2: Support for hierarchical categories (parent/subcategories)
 *
 * <p>Requirement REQ-2.18: Data encryption at rest
 *
 * <p>Requirement REQ-3.2: Authorization checks
 *
 * @see CategoryService
 * @see CategoryRequest
 * @see CategoryResponse
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {
    private final CategoryService categoryService;

    /**
     * Creates a new category for the authenticated user.
     *
     * <p>Users can create custom categories in addition to the default system categories.
     * Categories can be organized hierarchically using the {@code parentId} field.
     *
     * <p><strong>Request Headers:</strong>
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}</li>
     *   <li>X-Encryption-Session: {base64_encoded_key}</li>
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     * <pre>{@code
     * {
     *   "name": "Online Shopping",
     *   "type": "EXPENSE",
     *   "parentId": null,
     *   "icon": "🛒",
     *   "color": "#FF5733"
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 201 Created):</strong>
     * <pre>{@code
     * {
     *   "id": 20,
     *   "name": "Online Shopping",
     *   "type": "EXPENSE",
     *   "parentId": null,
     *   "parentName": null,
     *   "icon": "🛒",
     *
     * /**
     * Creates a new custom category for the authenticated user.
     *
     * <p><strong>Request Headers:</strong>
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}</li>
     *   <li>X-Encryption-Session: {base64_encoded_key}</li>
     * </ul>
     *
     * <p>Requirement REQ-2.4.2: Create user category</p>
     *
     * @param request category creation request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 201 Created with CategoryResponse
     */
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryRequest request,
            Authentication authentication) {

        log.info("Creating new category for user");
        User user = (User) authentication.getPrincipal();
        CategoryResponse response =
                categoryService.createCategory(user.getId(), request);

        log.info(
                "Category created successfully: id={}, name={}",
                response.getId(),
                response.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all categories for the authenticated user.
     *
     * <p>Returns both system-provided categories and the user's custom categories. If the {@code
     * X-Encryption-Session} header is missing, only system categories will be returned.
     *
     * @param type optional category type filter (INCOME or EXPENSE)
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of CategoryResponse (may be empty)
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategories(
            @RequestParam(required = false) CategoryType type,
            Authentication authentication) {

        log.debug("REST request to get all categories for current user. Type filter: {}", type);
        User user = (User) authentication.getPrincipal();
        List<CategoryResponse> categories;
        Locale locale = LocaleContextHolder.getLocale();

        if (type != null) {
            categories =
                    categoryService.getCategoriesByType(user.getId(), type, locale);
        } else {
            categories = categoryService.getAllCategories(user.getId(), locale);
        }

        return ResponseEntity.ok(categories);
    }

    /**
     * Retrieves hierarchical category tree for the authenticated user.
     *
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of CategoryTreeNode
     */
    @GetMapping("/tree")
    public ResponseEntity<List<CategoryTreeNode>> getCategoryTree(
            Authentication authentication) {

        log.debug("REST request to get category tree for current user");
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                categoryService.getCategoryTree(
                        user.getId(), LocaleContextHolder.getLocale()));
    }

    /**
     * Retrieves a specific category by ID.
     *
     * @param id the category ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with CategoryResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(
            @PathVariable("id") Long id,
            Authentication authentication) {

        log.debug("REST request to get category : {}", id);
        User user = (User) authentication.getPrincipal();
        CategoryResponse response =
                categoryService.getCategoryById(
                        user.getId(), id, LocaleContextHolder.getLocale());

        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing category.
     *
     * @param categoryId the category ID
     * @param request category update request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with updated CategoryResponse
     */
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable("id") Long categoryId,
            @Valid @RequestBody CategoryRequest request,
            Authentication authentication) {

        log.info("Updating category: id={}", categoryId);
        User user = (User) authentication.getPrincipal();
        CategoryResponse response =
                categoryService.updateCategory(user.getId(), categoryId, request);

        log.info("Category updated successfully: id={}", categoryId);

        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a custom category.
     *
     * @param categoryId the category ID
     * @param authentication Spring Security authentication object
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable("id") Long categoryId,
            Authentication authentication) {

        log.info("Deleting category: id={}", categoryId);
        User user = (User) authentication.getPrincipal();
        categoryService.deleteCategory(user.getId(), categoryId);

        log.info("Category deleted successfully: id={}", categoryId);

        return ResponseEntity.noContent().build();
    }
}
