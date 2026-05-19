package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.DataExportRequest;
import org.openfinance.dto.DataExportResponse;
import org.openfinance.entity.User;
import org.openfinance.service.DataExportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for data export operations.
 *
 * <p>Provides endpoints for backing up user financial data in JSON or CSV format.
 *
 * <p><strong>Note:</strong> Import functionality is intentionally not implemented. Exported data is
 * intended for backup, archival, and external analysis purposes only.
 *
 * <p>Requirement: REQ-3.4 - Data Export and Backup
 *
 * @author Open Finance Development Team
 */
@RestController
@RequestMapping("/api/v1/data")
@RequiredArgsConstructor
@Slf4j
public class DataExportController {

    private final DataExportService dataExportService;

    /**
     * Export all user data.
     *
     * <p><b>Example Request:</b>
     *
     * <pre>
     * POST /api/v1/data/export
     * Headers:
     *   Authorization: Bearer {jwt-token}
     *   X-Encryption-Key: {base64-encoded-key}
     *
     * Body:
     * {
     *   "format": "JSON",
     *   "includeAccounts": true,
     *   "includeTransactions": true,
     *   "includeAssets": true,
     *   "includeLiabilities": true,
     *   "includeBudgets": true,
     *   "includeCategories": true,
     *   "includeRealEstate": true,
     *   "startDate": "2024-01-01",
     *   "endDate": "2024-12-31",
     *   "includeDeleted": false
     * }
     * </pre>
     *
     * <p><b>Example Response:</b>
     *
     * <pre>
     * {
     *   "exportId": "a7f8c9d0-1234-5678-90ab-cdef12345678",
     *   "format": "JSON",
     *   "filename": "openfinance-export-20240203-102530.json",
     *   "fileSizeBytes": 524288,
     *   "accountCount": 5,
     *   "transactionCount": 234,
     *   "assetCount": 12,
     *   "liabilityCount": 2,
     *   "budgetCount": 8,
     *   "categoryCount": 25,
     *   "realEstateCount": 1,
     *   "generatedAt": "2024-02-03T10:25:30",
     *   "expiresAt": "2024-02-04T10:25:30",
     *   "message": "Export completed successfully"
     * }
     * </pre>
     *
     * @param request Export request with format and inclusion options
     * @param authentication Spring Security authentication
     * @param encryptionKeyHeader Base64-encoded encryption key
     * @return Export response with metadata
     */
    @PostMapping("/export")
    public ResponseEntity<DataExportResponse> exportData(
            @Valid @RequestBody DataExportRequest request,
            Authentication authentication,
            @RequestHeader("X-Encryption-Key") String encryptionKeyHeader) {

        log.info("Export data request received for format: {}", request.getFormat());

        // Get user ID from authentication
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();

        // Decode encryption key
        SecretKey secretKey;
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyHeader);
            secretKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            log.error("Failed to decode encryption key", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Perform export
        DataExportResponse response = dataExportService.exportUserData(userId, request, secretKey);

        log.info("Export completed for user {}. Export ID: {}", userId, response.getExportId());

        return ResponseEntity.ok(response);
    }

    /**
     * Get export statistics for user.
     *
     * <p>Returns information about the user's exportable data without actually performing the
     * export.
     *
     * @param authentication Spring Security authentication
     * @return Statistics response with entity counts
     */
    @GetMapping("/statistics")
    public ResponseEntity<DataExportResponse> getStatistics(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        Long userId = user.getId();

        log.info("Statistics request for user {}", userId);

        // TODO: Implement actual statistics calculation
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
