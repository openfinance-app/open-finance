package org.openfinance.service;

import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.RealEstateSimulationRequest;
import org.openfinance.dto.RealEstateSimulationResponse;
import org.openfinance.entity.RealEstateSimulation;
import org.openfinance.exception.DuplicateSimulationException;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.RealEstateSimulationRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing real estate simulations.
 *
 * <p>Handles CRUD operations for user simulations with data encryption.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealEstateSimulationService {

    private final RealEstateSimulationRepository simulationRepository;
    private final EncryptionService encryptionService;

    /** Create a new simulation. */
    @Transactional
    public RealEstateSimulationResponse createSimulation(
            Long userId, RealEstateSimulationRequest request, SecretKey encryptionKey) {

        log.info(
                "Creating simulation for user {}: type={}, name={}",
                userId,
                request.getSimulationType(),
                request.getName());

        // Check for duplicate name
        if (simulationRepository.existsByUserIdAndName(userId, request.getName())) {
            throw new DuplicateSimulationException(
                    "Une simulation avec le nom '" + request.getName() + "' existe déjà");
        }

        // Encrypt the data
        String encryptedData = encryptionService.encrypt(request.getData(), encryptionKey);

        RealEstateSimulation simulation =
                RealEstateSimulation.builder()
                        .userId(userId)
                        .name(request.getName())
                        .simulationType(request.getSimulationType())
                        .data(encryptedData)
                        .build();

        RealEstateSimulation saved = simulationRepository.save(simulation);

        log.info("Simulation created: id={}", saved.getId());

        return mapToResponse(saved, request.getData()); // Return original data
    }

    /** Get all simulations for a user. */
    @Transactional(readOnly = true)
    public List<RealEstateSimulationResponse> getSimulationsByUserId(
            Long userId, String simulationType, SecretKey encryptionKey) {

        log.info("Retrieving simulations for user {}: type={}", userId, simulationType);

        List<RealEstateSimulation> simulations;
        if (simulationType != null && !simulationType.isEmpty()) {
            simulations =
                    simulationRepository.findByUserIdAndSimulationType(userId, simulationType);
        } else {
            simulations = simulationRepository.findByUserId(userId);
        }

        return simulations.stream()
                .map(
                        s -> {
                            String decryptedData =
                                    encryptionService.decrypt(s.getData(), encryptionKey);
                            return mapToResponse(s, decryptedData);
                        })
                .collect(Collectors.toList());
    }

    /** Get a simulation by ID. */
    @Transactional(readOnly = true)
    public RealEstateSimulationResponse getSimulationById(
            Long simulationId, Long userId, SecretKey encryptionKey) {

        log.info("Retrieving simulation {} for user {}", simulationId, userId);

        RealEstateSimulation simulation =
                simulationRepository
                        .findByIdAndUserId(simulationId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Simulation not found with id: " + simulationId));

        String decryptedData = encryptionService.decrypt(simulation.getData(), encryptionKey);
        return mapToResponse(simulation, decryptedData);
    }

    /** Update a simulation. */
    @Transactional
    public RealEstateSimulationResponse updateSimulation(
            Long simulationId,
            Long userId,
            RealEstateSimulationRequest request,
            SecretKey encryptionKey) {

        log.info("Updating simulation {} for user {}", simulationId, userId);

        RealEstateSimulation simulation =
                simulationRepository
                        .findByIdAndUserId(simulationId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Simulation not found with id: " + simulationId));

        // Check for duplicate name (excluding current simulation)
        if (!simulation.getName().equals(request.getName())
                && simulationRepository.existsByUserIdAndName(userId, request.getName())) {
            throw new DuplicateSimulationException(
                    "Une simulation avec le nom '" + request.getName() + "' existe déjà");
        }

        // Encrypt the new data
        String encryptedData = encryptionService.encrypt(request.getData(), encryptionKey);

        simulation.setName(request.getName());
        simulation.setSimulationType(request.getSimulationType());
        simulation.setData(encryptedData);

        RealEstateSimulation updated = simulationRepository.save(simulation);

        log.info("Simulation updated: id={}", updated.getId());

        return mapToResponse(updated, request.getData());
    }

    /** Delete a simulation. */
    @Transactional
    public void deleteSimulation(Long simulationId, Long userId) {
        log.info("Deleting simulation {} for user {}", simulationId, userId);

        RealEstateSimulation simulation =
                simulationRepository
                        .findByIdAndUserId(simulationId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Simulation not found with id: " + simulationId));

        simulationRepository.delete(simulation);

        log.info("Simulation deleted: id={}", simulationId);
    }

    /** Check if simulation name exists. */
    @Transactional(readOnly = true)
    public boolean hasSimulationWithName(Long userId, String name) {
        return simulationRepository.existsByUserIdAndName(userId, name);
    }

    /** Map entity to response DTO. */
    private RealEstateSimulationResponse mapToResponse(
            RealEstateSimulation simulation, String decryptedData) {

        return RealEstateSimulationResponse.builder()
                .id(simulation.getId())
                .name(simulation.getName())
                .simulationType(simulation.getSimulationType())
                .data(decryptedData)
                .createdAt(simulation.getCreatedAt())
                .updatedAt(simulation.getUpdatedAt())
                .build();
    }
}
