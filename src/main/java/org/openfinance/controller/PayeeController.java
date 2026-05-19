package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.PayeeRequest;
import org.openfinance.dto.PayeeResponse;
import org.openfinance.entity.User;
import org.openfinance.service.PayeeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing payees.
 *
 * <p>Provides CRUD operations for payees and search functionality. System payees (default
 * merchants) cannot be modified, only hidden.
 *
 * <p>Requirements: Payee Management Feature
 */
@RestController
@RequestMapping("/api/v1/payees")
@RequiredArgsConstructor
@Slf4j
public class PayeeController {

    private final PayeeService payeeService;

    /**
     * Get all payees.
     *
     * @return list of all payees
     */
    @GetMapping
    public ResponseEntity<List<PayeeResponse>> getAllPayees(Authentication authentication) {
        log.debug("GET /api/v1/payees");
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();

        List<PayeeResponse> payees = payeeService.getAllPayeesWithStats(userId);
        return ResponseEntity.ok(payees);
    }

    /**
     * Get all active payees (for transaction form dropdown).
     *
     * @return list of active payees
     */
    @GetMapping("/active")
    public ResponseEntity<List<PayeeResponse>> getActivePayees(Authentication authentication) {
        log.debug("GET /api/v1/payees/active");
        User user = (User) authentication.getPrincipal();
        List<PayeeResponse> payees = payeeService.getActivePayees(user.getId());
        return ResponseEntity.ok(payees);
    }

    /**
     * Get payee by ID.
     *
     * @param id the payee ID
     * @return the payee
     */
    @GetMapping("/{id}")
    public ResponseEntity<PayeeResponse> getPayee(@PathVariable Long id) {
        log.debug("GET /api/v1/payees/{}", id);
        PayeeResponse payee = payeeService.getPayeeById(id);
        return ResponseEntity.ok(payee);
    }

    /**
     * Get system payees (default merchants/providers).
     *
     * @return list of system payees
     */
    @GetMapping("/system")
    public ResponseEntity<List<PayeeResponse>> getSystemPayees() {
        log.debug("GET /api/v1/payees/system");
        List<PayeeResponse> payees = payeeService.getSystemPayees();
        return ResponseEntity.ok(payees);
    }

    /**
     * Get custom (user-created) payees.
     *
     * @return list of custom payees
     */
    @GetMapping("/custom")
    public ResponseEntity<List<PayeeResponse>> getCustomPayees(Authentication authentication) {
        log.debug("GET /api/v1/payees/custom");
        User user = (User) authentication.getPrincipal();
        List<PayeeResponse> payees = payeeService.getCustomPayees(user.getId());
        return ResponseEntity.ok(payees);
    }

    /**
     * Search payees by name.
     *
     * @param query the search query
     * @return list of matching payees
     */
    @GetMapping("/search")
    public ResponseEntity<List<PayeeResponse>> searchPayees(
            @RequestParam String query, Authentication authentication) {
        log.debug("GET /api/v1/payees/search?q={}", query);
        User user = (User) authentication.getPrincipal();
        List<PayeeResponse> payees = payeeService.searchPayees(query, user.getId());
        return ResponseEntity.ok(payees);
    }

    /**
     * Get distinct category names.
     *
     * @return list of category names
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        log.debug("GET /api/v1/payees/categories");
        List<String> categories = payeeService.getCategoryNames();
        return ResponseEntity.ok(categories);
    }

    /**
     * Get payees by category ID.
     *
     * @param categoryId the category ID
     * @return list of payees in the category
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<PayeeResponse>> getPayeesByCategory(
            @PathVariable Long categoryId, Authentication authentication) {
        log.debug("GET /api/v1/payees/category/{}", categoryId);
        User user = (User) authentication.getPrincipal();
        List<PayeeResponse> payees = payeeService.getPayeesByCategoryId(categoryId, user.getId());
        return ResponseEntity.ok(payees);
    }

    /**
     * Find or create a payee by name.
     *
     * <p>Used when entering transactions. If payee exists, returns it. If not, creates a new custom
     * payee automatically.
     *
     * @param name the payee name
     * @return the existing or newly created payee
     */
    @PostMapping("/find-or-create")
    public ResponseEntity<PayeeResponse> findOrCreatePayee(
            @RequestParam String name, Authentication authentication) {
        log.debug("POST /api/v1/payees/find-or-create?name={}", name);
        User user = (User) authentication.getPrincipal();
        PayeeResponse payee = payeeService.findOrCreatePayee(name, user.getId());
        if (payee == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(payee);
    }

    /**
     * Create a new custom payee.
     *
     * @param request the payee request
     * @return the created payee
     */
    @PostMapping
    public ResponseEntity<PayeeResponse> createPayee(
            @Valid @RequestBody PayeeRequest request, Authentication authentication) {
        log.debug("POST /api/v1/payees");
        User user = (User) authentication.getPrincipal();
        PayeeResponse created = payeeService.createPayee(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update a payee.
     *
     * <p>Only custom payees owned by the user can be updated.
     *
     * @param id the payee ID
     * @param request the update request
     * @return the updated payee
     */
    @PutMapping("/{id}")
    public ResponseEntity<PayeeResponse> updatePayee(
            @PathVariable Long id,
            @Valid @RequestBody PayeeRequest request,
            Authentication authentication) {
        log.debug("PUT /api/v1/payees/{}", id);
        User user = (User) authentication.getPrincipal();
        PayeeResponse updated = payeeService.updatePayee(id, request, user.getId());
        return ResponseEntity.ok(updated);
    }

    /**
     * Toggle payee active status.
     *
     * <p>Used to hide/show system payees without deleting them.
     *
     * @param id the payee ID
     * @return the updated payee
     */
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<PayeeResponse> togglePayeeActive(@PathVariable Long id) {
        log.debug("PATCH /api/v1/payees/{}/toggle-active", id);
        PayeeResponse updated = payeeService.togglePayeeActive(id);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a payee.
     *
     * <p>Only custom payees owned by the user can be deleted.
     *
     * @param id the payee ID
     * @return no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayee(@PathVariable Long id, Authentication authentication) {
        log.debug("DELETE /api/v1/payees/{}", id);
        User user = (User) authentication.getPrincipal();
        payeeService.deletePayee(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
