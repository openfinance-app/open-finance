package org.openfinance.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.PayeeRequest;
import org.openfinance.dto.PayeeResponse;
import org.openfinance.entity.Category;
import org.openfinance.entity.Payee;
import org.openfinance.exception.DuplicatePayeeException;
import org.openfinance.exception.PayeeNotFoundException;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.PayeeRepository;
import org.openfinance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for managing payees.
 *
 * <p>This service handles business logic for payee CRUD operations:
 *
 * <ul>
 *   <li>Creating new payees (custom/user-created)
 *   <li>Updating existing payees (custom only)
 *   <li>Deleting payees (custom only - system payees protected)
 *   <li>Retrieving payees with filters
 *   <li>Searching payees by name
 *   <li>Finding or creating payees automatically for transactions
 * </ul>
 *
 * <p>Requirements: Payee Management Feature
 *
 * <p>Requirements: REQ-CAT-5.1 - Payee-to-Category Auto-Fill
 *
 * @see org.openfinance.entity.Payee
 * @see org.openfinance.dto.PayeeRequest
 * @see org.openfinance.dto.PayeeResponse
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PayeeService {

    private final PayeeRepository payeeRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final LogoFetchService logoFetchService;

    /**
     * Get all payees visible to a specific user.
     *
     * @param userId the authenticated user's ID
     * @return list of payees ordered by name
     */
    @Transactional(readOnly = true)
    public List<PayeeResponse> getAllPayees(Long userId) {
        log.debug("Fetching all payees for user: {}", userId);
        return payeeRepository.findAllByUser(userId).stream()
                .map(p -> toResponse(p, null, null))
                .toList();
    }

    /**
     * Get all payees with transaction statistics for a user.
     *
     * @param userId the user ID
     * @return list of all payees with stats
     */
    @Transactional(readOnly = true)
    public List<PayeeResponse> getAllPayeesWithStats(Long userId) {
        log.debug("Fetching all payees with stats for user: {}", userId);
        Map<String, TransactionRepository.PayeeStats> statsMap = getPayeeStatsMap(userId);

        return payeeRepository.findAllByUser(userId).stream()
                .map(
                        payee -> {
                            TransactionRepository.PayeeStats stats = statsMap.get(payee.getName());
                            return toResponse(
                                    payee,
                                    stats != null ? stats.getCount() : 0L,
                                    stats != null ? stats.getTotal() : BigDecimal.ZERO);
                        })
                .toList();
    }

    /**
     * Get all active payees visible to a specific user.
     *
     * @param userId the authenticated user's ID
     * @return list of active payees ordered by system first, then name
     */
    @Transactional(readOnly = true)
    public List<PayeeResponse> getActivePayees(Long userId) {
        log.debug("Fetching active payees for user: {}", userId);
        return payeeRepository.findAllActiveByUserOrderBySystemFirst(userId).stream()
                .map(p -> toResponse(p, null, null))
                .toList();
    }

    /**
     * Get payee by ID.
     *
     * @param id the payee ID
     * @return the payee
     * @throws PayeeNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public PayeeResponse getPayeeById(Long id) {
        log.debug("Fetching payee by id: {}", id);
        Payee payee =
                payeeRepository.findById(id).orElseThrow(() -> new PayeeNotFoundException(id));
        return toResponse(payee, null, null);
    }

    /**
     * Get system payees (default merchants/providers).
     *
     * @return list of system payees
     */
    @Transactional(readOnly = true)
    public List<PayeeResponse> getSystemPayees() {
        log.debug("Fetching system payees");
        return payeeRepository.findByIsSystemTrueOrderByNameAsc().stream()
                .map(p -> toResponse(p, null, null))
                .toList();
    }

    /**
     * Get custom (user-created) payees for a specific user.
     *
     * @param userId the authenticated user's ID
     * @return list of custom payees belonging to the user
     */
    @Transactional(readOnly = true)
    public List<PayeeResponse> getCustomPayees(Long userId) {
        log.debug("Fetching custom payees for user: {}", userId);
        return payeeRepository.findByIsSystemFalseAndUserIdOrderByNameAsc(userId).stream()
                .map(p -> toResponse(p, null, null))
                .toList();
    }

    /**
     * Search payees by name visible to a specific user.
     *
     * @param query the search query
     * @param userId the authenticated user's ID
     * @return list of matching payees
     */
    @Transactional(readOnly = true)
    public List<PayeeResponse> searchPayees(String query, Long userId) {
        log.debug("Searching payees by name: {} for user: {}", query, userId);
        return payeeRepository.searchByNameAndUser(query, userId).stream()
                .map(p -> toResponse(p, null, null))
                .toList();
    }

    /**
     * Get distinct category names from payees.
     *
     * @return list of category names
     */
    @Transactional(readOnly = true)
    public List<String> getCategoryNames() {
        log.debug("Fetching distinct category names");
        return payeeRepository.findDistinctCategoryNames();
    }

    /**
     * Get distinct categories from payees.
     *
     * @return list of categories
     */
    @Transactional(readOnly = true)
    public List<Category> getCategories() {
        log.debug("Fetching distinct categories");
        return payeeRepository.findDistinctCategories();
    }

    /**
     * Get payees by category ID visible to a specific user.
     *
     * @param categoryId the category ID
     * @param userId the authenticated user's ID
     * @return list of payees in the category
     */
    @Transactional(readOnly = true)
    public List<PayeeResponse> getPayeesByCategoryId(Long categoryId, Long userId) {
        log.debug("Fetching payees by category id: {} for user: {}", categoryId, userId);
        return payeeRepository
                .findByDefaultCategoryIdAndIsActiveTrueAndUser(categoryId, userId)
                .stream()
                .map(p -> toResponse(p, null, null))
                .toList();
    }

    /**
     * Find or create a payee by name for a specific user.
     *
     * <p>This method is used when entering transactions. If a payee with the given name already
     * exists (case-insensitive) as a system payee or owned by the user, it returns that payee.
     * Otherwise, it creates a new custom payee for the user.
     *
     * @param name the payee name
     * @param userId the authenticated user's ID
     * @return the existing or newly created payee
     */
    @Transactional
    public PayeeResponse findOrCreatePayee(String name, Long userId) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        String trimmedName = name.trim();
        log.debug("Finding or creating payee: {} for user: {}", trimmedName, userId);

        // Try to find existing payee (case-insensitive) visible to the user
        Payee existingPayee = payeeRepository.findByNameIgnoreCaseAndUser(trimmedName, userId);
        if (existingPayee != null) {
            // If found but was inactive, reactivate it
            if (Boolean.FALSE.equals(existingPayee.getIsActive())) {
                existingPayee.setIsActive(true);
                Payee saved = payeeRepository.save(existingPayee);
                log.info("Reactivated payee: {}", saved.getName());
                return toResponse(saved, null, null);
            }
            log.debug("Found existing payee: {}", existingPayee.getName());
            return toResponse(existingPayee, null, null);
        }

        // Create new custom payee for the user — attempt to auto-fetch a logo
        String logo = logoFetchService.fetchLogo(trimmedName).orElse(null);

        Payee newPayee =
                Payee.builder()
                        .name(trimmedName)
                        .logo(logo)
                        .isSystem(false)
                        .isActive(true)
                        .userId(userId)
                        .build();

        Payee saved = payeeRepository.save(newPayee);
        log.info("Created new payee: {} for user: {}", saved.getName(), userId);
        return toResponse(saved, null, null);
    }

    /**
     * Create a new custom payee for a specific user.
     *
     * <p>User-created payees are marked as non-system and scoped to the creating user.
     *
     * @param request the payee request
     * @param userId the authenticated user's ID
     * @return the created payee
     */
    public PayeeResponse createPayee(PayeeRequest request, Long userId) {
        String trimmedName = request.getName().trim();
        log.debug("Creating payee: {} for user: {}", trimmedName, userId);

        if (payeeRepository.existsByNameIgnoreCaseAndUser(trimmedName, userId)) {
            throw new DuplicatePayeeException(trimmedName);
        }

        // Set default category if categoryId provided
        Category defaultCategory = null;
        if (request.getCategoryId() != null) {
            defaultCategory = categoryRepository.findById(request.getCategoryId()).orElse(null);
        }

        String logo = request.getLogo();
        if (logo == null || logo.isBlank()) {
            logo = logoFetchService.fetchLogo(trimmedName).orElse(null);
        }

        Payee payee =
                Payee.builder()
                        .name(trimmedName)
                        .logo(logo)
                        .defaultCategory(defaultCategory)
                        .isSystem(false)
                        .isActive(true)
                        .userId(userId)
                        .build();

        Payee saved = payeeRepository.save(payee);
        log.info("Created custom payee with id: {}", saved.getId());
        return toResponse(saved, null, null);
    }

    /**
     * Update an existing payee.
     *
     * <p>Only custom (non-system) payees owned by the user can be updated.
     *
     * @param id the payee ID
     * @param request the update request
     * @param userId the authenticated user's ID
     * @return the updated payee
     * @throws PayeeNotFoundException if not found
     * @throws IllegalStateException if trying to update a system payee or another user's
     */
    public PayeeResponse updatePayee(Long id, PayeeRequest request, Long userId) {
        log.debug("Updating payee id: {} for user: {}", id, userId);

        Payee payee =
                payeeRepository.findById(id).orElseThrow(() -> new PayeeNotFoundException(id));

        // Prevent updating system payees
        if (Boolean.TRUE.equals(payee.getIsSystem())) {
            throw new IllegalStateException("Cannot update system payees");
        }

        // Prevent updating another user's payee
        if (!userId.equals(payee.getUserId())) {
            throw new IllegalStateException("Cannot update another user's payee");
        }

        String trimmedName = request.getName().trim();
        // Check for duplicate name if name is being changed (scoped to user)
        if (!payee.getName().equalsIgnoreCase(trimmedName)
                && payeeRepository.existsByNameIgnoreCaseAndUser(trimmedName, userId)) {
            throw new DuplicatePayeeException(trimmedName);
        }

        payee.setName(trimmedName);
        payee.setLogo(request.getLogo());

        // Update default category if provided
        if (request.getCategoryId() != null) {
            Category defaultCategory =
                    categoryRepository.findById(request.getCategoryId()).orElse(null);
            payee.setDefaultCategory(defaultCategory);
        }

        Payee saved = payeeRepository.save(payee);
        log.info("Updated payee id: {}", saved.getId());
        return toResponse(saved, null, null);
    }

    /**
     * Toggle payee active status.
     *
     * <p>Used to hide/show system payees without deleting them.
     *
     * @param id the payee ID
     * @return the updated payee
     * @throws PayeeNotFoundException if not found
     */
    public PayeeResponse togglePayeeActive(Long id) {
        log.debug("Toggling payee active status id: {}", id);

        Payee payee =
                payeeRepository.findById(id).orElseThrow(() -> new PayeeNotFoundException(id));

        if (!Boolean.TRUE.equals(payee.getIsSystem())) {
            throw new IllegalStateException(
                    "Only system payees can be toggled. Delete custom payees instead.");
        }

        payee.setIsActive(!payee.getIsActive());

        Payee saved = payeeRepository.save(payee);
        log.info("Set payee id: {} active: {}", saved.getId(), saved.getIsActive());
        return toResponse(saved, null, null);
    }

    /**
     * Delete a payee.
     *
     * <p>Only custom (non-system) payees owned by the user can be deleted.
     *
     * @param id the payee ID
     * @param userId the authenticated user's ID
     * @throws PayeeNotFoundException if not found
     * @throws IllegalStateException if trying to delete a system payee or another user's
     */
    public void deletePayee(Long id, Long userId) {
        log.debug("Deleting payee id: {} for user: {}", id, userId);

        Payee payee =
                payeeRepository.findById(id).orElseThrow(() -> new PayeeNotFoundException(id));

        // Prevent deleting system payees
        if (Boolean.TRUE.equals(payee.getIsSystem())) {
            throw new IllegalStateException("Cannot delete system payees");
        }

        // Prevent deleting another user's payee
        if (!userId.equals(payee.getUserId())) {
            throw new IllegalStateException("Cannot delete another user's payee");
        }

        payeeRepository.delete(payee);
        log.info("Deleted payee id: {}", id);
    }

    /**
     * Check if payee exists.
     *
     * @param id the payee ID
     * @return true if exists
     */
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return payeeRepository.existsById(id);
    }

    /** Map helper to get statistics for all payees of a user. */
    private Map<String, TransactionRepository.PayeeStats> getPayeeStatsMap(Long userId) {
        if (userId == null) return Collections.emptyMap();
        return transactionRepository.findPayeeStatsByUserId(userId).stream()
                .collect(
                        Collectors.toMap(
                                TransactionRepository.PayeeStats::getPayee,
                                stats -> stats,
                                (existing, replacement) ->
                                        existing // Should not happen with GROUP BY
                                ));
    }

    /** Convert entity to response DTO. */
    private PayeeResponse toResponse(Payee payee, Long transactionCount, BigDecimal totalAmount) {
        PayeeResponse.PayeeResponseBuilder builder =
                PayeeResponse.builder()
                        .id(payee.getId())
                        .name(payee.getName())
                        .logo(payee.getLogo())
                        .isSystem(payee.getIsSystem())
                        .isActive(payee.getIsActive())
                        .createdAt(payee.getCreatedAt())
                        .updatedAt(payee.getUpdatedAt())
                        .transactionCount(transactionCount)
                        .totalAmount(totalAmount);

        // Include default category info if set
        if (payee.getDefaultCategory() != null) {
            builder.categoryId(payee.getDefaultCategory().getId());
            // For system categories, name is not encrypted
            if (Boolean.TRUE.equals(payee.getDefaultCategory().getIsSystem())) {
                builder.categoryName(payee.getDefaultCategory().getName());
            }
        }

        return builder.build();
    }
}
