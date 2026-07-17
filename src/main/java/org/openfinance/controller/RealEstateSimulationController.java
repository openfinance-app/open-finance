package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.RealEstateSimulationRequest;
import org.openfinance.dto.RealEstateSimulationResponse;
import org.openfinance.service.RealEstateSimulationService;
import org.openfinance.util.ControllerUtil;
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

    private final RealEstateSimulationService simulationService;

    /** Create a new simulation. */
    @PostMapping
    public ResponseEntity<RealEstateSimulationResponse> createSimulation(
            @Valid @RequestBody RealEstateSimulationRequest request,
            Authentication authentication) {

        log.info("Creating simulation: type={}", request.getSimulationType());

        Long userId = ControllerUtil.extractUserId(authentication);

        RealEstateSimulationResponse response = simulationService.createSimulation(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Get all simulations for the authenticated user. */
    @GetMapping
    public ResponseEntity<List<RealEstateSimulationResponse>> getSimulations(
            @RequestParam(required = false) String simulationType, Authentication authentication) {

        log.info("Retrieving simulations: type={}", simulationType);

        Long userId = ControllerUtil.extractUserId(authentication);

        List<RealEstateSimulationResponse> simulations =
                simulationService.getSimulationsByUserId(userId, simulationType);

        return ResponseEntity.ok(simulations);
    }

    /** Get a simulation by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<RealEstateSimulationResponse> getSimulationById(
            @PathVariable("id") Long simulationId, Authentication authentication) {

        log.info("Retrieving simulation: id={}", simulationId);

        Long userId = ControllerUtil.extractUserId(authentication);

        RealEstateSimulationResponse response =
                simulationService.getSimulationById(simulationId, userId);

        return ResponseEntity.ok(response);
    }

    /** Update a simulation. */
    @PutMapping("/{id}")
    public ResponseEntity<RealEstateSimulationResponse> updateSimulation(
            @PathVariable("id") Long simulationId,
            @Valid @RequestBody RealEstateSimulationRequest request,
            Authentication authentication) {

        log.info("Updating simulation: id={}", simulationId);

        Long userId = ControllerUtil.extractUserId(authentication);

        RealEstateSimulationResponse response =
                simulationService.updateSimulation(simulationId, userId, request);

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
