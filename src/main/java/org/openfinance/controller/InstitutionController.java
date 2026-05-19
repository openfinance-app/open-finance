package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.InstitutionRequest;
import org.openfinance.dto.InstitutionResponse;
import org.openfinance.entity.User;
import org.openfinance.service.InstitutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing financial institutions.
 *
 * <p>Provides CRUD operations for institutions and search functionality. System institutions
 * (default EU banks) cannot be modified.
 *
 * <p>Requirements: REQ-2.6.1.3 - Predefined Financial Institutions
 */
@RestController
@RequestMapping("/api/v1/institutions")
@RequiredArgsConstructor
@Slf4j
public class InstitutionController {

    private final InstitutionService institutionService;

    /**
     * Get all institutions.
     *
     * @return list of all institutions
     */
    @GetMapping
    public ResponseEntity<List<InstitutionResponse>> getAllInstitutions(
            Authentication authentication) {
        log.debug("GET /api/v1/institutions");
        User user = (User) authentication.getPrincipal();
        List<InstitutionResponse> institutions =
                institutionService.getAllInstitutions(user.getId());
        return ResponseEntity.ok(institutions);
    }

    /**
     * Get institution by ID.
     *
     * @param id the institution ID
     * @return the institution
     */
    @GetMapping("/{id}")
    public ResponseEntity<InstitutionResponse> getInstitution(@PathVariable Long id) {
        log.debug("GET /api/v1/institutions/{}", id);
        InstitutionResponse institution = institutionService.getInstitutionById(id);
        return ResponseEntity.ok(institution);
    }

    /**
     * Get institutions by country.
     *
     * @param country the country code
     * @return list of institutions in the country
     */
    @GetMapping("/country/{country}")
    public ResponseEntity<List<InstitutionResponse>> getInstitutionsByCountry(
            @PathVariable String country, Authentication authentication) {
        log.debug("GET /api/v1/institutions/country/{}", country);
        User user = (User) authentication.getPrincipal();
        List<InstitutionResponse> institutions =
                institutionService.getInstitutionsByCountry(country, user.getId());
        return ResponseEntity.ok(institutions);
    }

    /**
     * Get system institutions (default EU banks).
     *
     * @return list of system institutions
     */
    @GetMapping("/system")
    public ResponseEntity<List<InstitutionResponse>> getSystemInstitutions() {
        log.debug("GET /api/v1/institutions/system");
        List<InstitutionResponse> institutions = institutionService.getSystemInstitutions();
        return ResponseEntity.ok(institutions);
    }

    /**
     * Get custom (user-created) institutions.
     *
     * @return list of custom institutions
     */
    @GetMapping("/custom")
    public ResponseEntity<List<InstitutionResponse>> getCustomInstitutions(
            Authentication authentication) {
        log.debug("GET /api/v1/institutions/custom");
        User user = (User) authentication.getPrincipal();
        List<InstitutionResponse> institutions =
                institutionService.getCustomInstitutions(user.getId());
        return ResponseEntity.ok(institutions);
    }

    /**
     * Search institutions by name.
     *
     * @param query the search query
     * @return list of matching institutions
     */
    @GetMapping("/search")
    public ResponseEntity<List<InstitutionResponse>> searchInstitutions(
            @RequestParam String query, Authentication authentication) {
        log.debug("GET /api/v1/institutions/search?q={}", query);
        User user = (User) authentication.getPrincipal();
        List<InstitutionResponse> institutions =
                institutionService.searchInstitutions(query, user.getId());
        return ResponseEntity.ok(institutions);
    }

    /**
     * Get distinct country codes.
     *
     * @return list of country codes
     */
    @GetMapping("/countries")
    public ResponseEntity<List<String>> getCountries(Authentication authentication) {
        log.debug("GET /api/v1/institutions/countries");
        User user = (User) authentication.getPrincipal();
        List<String> countries = institutionService.getCountries(user.getId());
        return ResponseEntity.ok(countries);
    }

    /**
     * Create a new custom institution.
     *
     * @param request the institution request
     * @return the created institution
     */
    @PostMapping
    public ResponseEntity<InstitutionResponse> createInstitution(
            @Valid @RequestBody InstitutionRequest request, Authentication authentication) {
        log.debug("POST /api/v1/institutions");
        User user = (User) authentication.getPrincipal();
        InstitutionResponse created = institutionService.createInstitution(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update an institution.
     *
     * <p>Only custom institutions owned by the user can be updated.
     *
     * @param id the institution ID
     * @param request the update request
     * @return the updated institution
     */
    @PutMapping("/{id}")
    public ResponseEntity<InstitutionResponse> updateInstitution(
            @PathVariable Long id,
            @Valid @RequestBody InstitutionRequest request,
            Authentication authentication) {
        log.debug("PUT /api/v1/institutions/{}", id);
        User user = (User) authentication.getPrincipal();
        InstitutionResponse updated =
                institutionService.updateInstitution(id, request, user.getId());
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete an institution.
     *
     * <p>Only custom institutions owned by the user can be deleted.
     *
     * @param id the institution ID
     * @return no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstitution(
            @PathVariable Long id, Authentication authentication) {
        log.debug("DELETE /api/v1/institutions/{}", id);
        User user = (User) authentication.getPrincipal();
        institutionService.deleteInstitution(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
