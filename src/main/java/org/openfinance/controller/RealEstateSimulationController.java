package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.RealEstateSimulationRequest;
import org.openfinance.dto.RealEstateSimulationResponse;
import org.openfinance.service.RealEstateSimulationService;
import org.openfinance.util.ControllerUtil;
import org.openfinance.util.EncryptionUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for real estate simulation management.
 *
 * <p>Provides endpoints for saving, loading, and managing real estate simulations.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/real-estate-simulations")
@RequiredArgsConstructor
@Slf4j
public class RealEstateSimulationController {

    private static final String ENCRYPTION_KEY_HEADER = "X-Encryption-Key";

    private final RealEstateSimulationService simulationService;

    /** Create a new simulation. */
    @PostMapping
    public ResponseEntity<RealEstateSimulationResponse> createSimulation(
            @Valid @RequestBody RealEstateSimulationRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Creating simulation: type={}", request.getSimulationType());

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RealEstateSimulationResponse response =
                simulationService.createSimulation(userId, request, encryptionKey);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Get all simulations for the authenticated user. */
    @GetMapping
    public ResponseEntity<List<RealEstateSimulationResponse>> getSimulations(
            @RequestParam(required = false) String simulationType,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Retrieving simulations: type={}", simulationType);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        List<RealEstateSimulationResponse> simulations =
                simulationService.getSimulationsByUserId(userId, simulationType, encryptionKey);

        return ResponseEntity.ok(simulations);
    }

    /** Get a simulation by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<RealEstateSimulationResponse> getSimulationById(
            @PathVariable("id") Long simulationId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Retrieving simulation: id={}", simulationId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RealEstateSimulationResponse response =
                simulationService.getSimulationById(simulationId, userId, encryptionKey);

        return ResponseEntity.ok(response);
    }

    /** Update a simulation. */
    @PutMapping("/{id}")
    public ResponseEntity<RealEstateSimulationResponse> updateSimulation(
            @PathVariable("id") Long simulationId,
            @Valid @RequestBody RealEstateSimulationRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Updating simulation: id={}", simulationId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RealEstateSimulationResponse response =
                simulationService.updateSimulation(simulationId, userId, request, encryptionKey);

        return ResponseEntity.ok(response);
    }

    /** Delete a simulation. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSimulation(
            @PathVariable("id") Long simulationId, Authentication authentication) {

        log.info("Deleting simulation: id={}", simulationId);

        Long userId = ControllerUtil.extractUserId(authentication);
        simulationService.deleteSimulation(simulationId, userId);

        return ResponseEntity.noContent().build();
    }

    /** Check if a simulation name exists. */
    @GetMapping("/check-name")
    public ResponseEntity<Boolean> checkSimulationNameExists(
            @RequestParam String name, Authentication authentication) {

        Long userId = ControllerUtil.extractUserId(authentication);
        boolean exists = simulationService.hasSimulationWithName(userId, name);

        return ResponseEntity.ok(exists);
    }
}
