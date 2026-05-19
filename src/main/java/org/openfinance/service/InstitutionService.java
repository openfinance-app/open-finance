package org.openfinance.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.InstitutionRequest;
import org.openfinance.dto.InstitutionResponse;
import org.openfinance.entity.Institution;
import org.openfinance.exception.InstitutionNotFoundException;
import org.openfinance.repository.InstitutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for managing financial institutions.
 *
 * <p>This service handles business logic for institution CRUD operations:
 *
 * <ul>
 *   <li>Creating new institutions (custom/user-created)
 *   <li>Updating existing institutions (custom only)
 *   <li>Deleting institutions (custom only - system institutions protected)
 *   <li>Retrieving institutions with filters
 *   <li>Searching institutions by name
 * </ul>
 *
 * <p>Requirements: REQ-2.6.1.3 - Predefined Financial Institutions
 *
 * @see org.openfinance.entity.Institution
 * @see org.openfinance.dto.InstitutionRequest
 * @see org.openfinance.dto.InstitutionResponse
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class InstitutionService {

    private final InstitutionRepository institutionRepository;
    private final LogoFetchService logoFetchService;

    /**
     * Get all institutions visible to a specific user. Returns system institutions plus the user's
     * own custom institutions.
     *
     * @param userId the authenticated user's ID
     * @return list of institutions ordered by country and name
     */
    @Transactional(readOnly = true)
    public List<InstitutionResponse> getAllInstitutions(Long userId) {
        log.debug("Fetching all institutions for user: {}", userId);
        return institutionRepository.findAllByUser(userId).stream().map(this::toResponse).toList();
    }

    /**
     * Get institution by ID.
     *
     * @param id the institution ID
     * @return the institution
     * @throws InstitutionNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public InstitutionResponse getInstitutionById(Long id) {
        log.debug("Fetching institution by id: {}", id);
        Institution institution =
                institutionRepository
                        .findById(id)
                        .orElseThrow(() -> new InstitutionNotFoundException(id));
        return toResponse(institution);
    }

    /**
     * Get institutions by country visible to a specific user.
     *
     * @param country the country code (ISO 3166-1 alpha-2)
     * @param userId the authenticated user's ID
     * @return list of institutions in the specified country
     */
    @Transactional(readOnly = true)
    public List<InstitutionResponse> getInstitutionsByCountry(String country, Long userId) {
        log.debug("Fetching institutions by country: {} for user: {}", country, userId);
        return institutionRepository.findByCountryAndUser(country, userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get system institutions (default EU banks).
     *
     * @return list of system institutions
     */
    @Transactional(readOnly = true)
    public List<InstitutionResponse> getSystemInstitutions() {
        log.debug("Fetching system institutions");
        return institutionRepository.findByIsSystemTrueOrderByCountryAscNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get custom (user-created) institutions for a specific user.
     *
     * @param userId the authenticated user's ID
     * @return list of custom institutions belonging to the user
     */
    @Transactional(readOnly = true)
    public List<InstitutionResponse> getCustomInstitutions(Long userId) {
        log.debug("Fetching custom institutions for user: {}", userId);
        return institutionRepository.findByIsSystemFalseAndUserIdOrderByNameAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Search institutions by name visible to a specific user.
     *
     * @param query the search query
     * @param userId the authenticated user's ID
     * @return list of matching institutions
     */
    @Transactional(readOnly = true)
    public List<InstitutionResponse> searchInstitutions(String query, Long userId) {
        log.debug("Searching institutions by name: {} for user: {}", query, userId);
        return institutionRepository.searchByNameAndUser(query, userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get distinct country codes from institutions visible to a specific user.
     *
     * @param userId the authenticated user's ID
     * @return list of country codes
     */
    @Transactional(readOnly = true)
    public List<String> getCountries(Long userId) {
        log.debug("Fetching distinct countries for user: {}", userId);
        return institutionRepository.findDistinctCountriesByUser(userId);
    }

    /**
     * Create a new custom institution for a specific user.
     *
     * <p>User-created institutions are marked as non-system and scoped to the creating user.
     *
     * @param request the institution request
     * @param userId the authenticated user's ID
     * @return the created institution
     */
    public InstitutionResponse createInstitution(InstitutionRequest request, Long userId) {
        log.debug("Creating institution: {} for user: {}", request.getName(), userId);

        String logo = request.getLogo();
        if (logo == null || logo.isBlank()) {
            logo = logoFetchService.fetchLogo(request.getName()).orElse(null);
        }

        Institution institution =
                Institution.builder()
                        .name(request.getName())
                        .bic(request.getBic())
                        .country(request.getCountry())
                        .logo(logo)
                        .isSystem(false)
                        .userId(userId)
                        .build();

        Institution saved = institutionRepository.save(institution);
        log.info("Created custom institution with id: {}", saved.getId());
        return toResponse(saved);
    }

    /**
     * Update an existing institution.
     *
     * <p>Only custom (non-system) institutions owned by the user can be updated.
     *
     * @param id the institution ID
     * @param request the update request
     * @param userId the authenticated user's ID
     * @return the updated institution
     * @throws InstitutionNotFoundException if not found
     * @throws IllegalStateException if trying to update a system institution or another user's
     */
    public InstitutionResponse updateInstitution(Long id, InstitutionRequest request, Long userId) {
        log.debug("Updating institution id: {} for user: {}", id, userId);

        Institution institution =
                institutionRepository
                        .findById(id)
                        .orElseThrow(() -> new InstitutionNotFoundException(id));

        // Prevent updating system institutions
        if (Boolean.TRUE.equals(institution.getIsSystem())) {
            throw new IllegalStateException("Cannot update system institutions");
        }

        // Prevent updating another user's institution
        if (!userId.equals(institution.getUserId())) {
            throw new IllegalStateException("Cannot update another user's institution");
        }

        institution.setName(request.getName());
        institution.setBic(request.getBic());
        institution.setCountry(request.getCountry());
        institution.setLogo(request.getLogo());

        Institution saved = institutionRepository.save(institution);
        log.info("Updated institution id: {}", saved.getId());
        return toResponse(saved);
    }

    /**
     * Delete an institution.
     *
     * <p>Only custom (non-system) institutions owned by the user can be deleted. Also checks if
     * institution is in use by any accounts.
     *
     * @param id the institution ID
     * @param userId the authenticated user's ID
     * @throws InstitutionNotFoundException if not found
     * @throws IllegalStateException if trying to delete a system institution or another user's
     */
    public void deleteInstitution(Long id, Long userId) {
        log.debug("Deleting institution id: {} for user: {}", id, userId);

        Institution institution =
                institutionRepository
                        .findById(id)
                        .orElseThrow(() -> new InstitutionNotFoundException(id));

        // Prevent deleting system institutions
        if (Boolean.TRUE.equals(institution.getIsSystem())) {
            throw new IllegalStateException("Cannot delete system institutions");
        }

        // Prevent deleting another user's institution
        if (!userId.equals(institution.getUserId())) {
            throw new IllegalStateException("Cannot delete another user's institution");
        }

        // Check if institution is in use
        if (institutionRepository.isInUse(id)) {
            throw new IllegalStateException(
                    "Cannot delete institution that is associated with accounts");
        }

        institutionRepository.delete(institution);
        log.info("Deleted institution id: {}", id);
    }

    /**
     * Check if institution exists.
     *
     * @param id the institution ID
     * @return true if exists
     */
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return institutionRepository.existsById(id);
    }

    /** Convert entity to response DTO. */
    private InstitutionResponse toResponse(Institution institution) {
        return InstitutionResponse.builder()
                .id(institution.getId())
                .name(institution.getName())
                .bic(institution.getBic())
                .country(institution.getCountry())
                .logo(institution.getLogo())
                .isSystem(institution.getIsSystem())
                .createdAt(institution.getCreatedAt())
                .updatedAt(institution.getUpdatedAt())
                .build();
    }
}
