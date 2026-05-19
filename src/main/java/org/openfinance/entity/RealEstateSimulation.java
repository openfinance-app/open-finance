package org.openfinance.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing a saved real estate simulation in the Open-Finance system.
 *
 * <p>Simulations store user inputs and results for both Buy vs Rent comparisons and Property Rental
 * Investment calculations. Each simulation belongs to a user and can be retrieved later for review
 * or modification.
 *
 * <p><strong>Security Note:</strong> The simulation data (stored as JSON) may contain sensitive
 * financial information and should be encrypted by the service layer.
 *
 * <p>Requirement REQ-1.7.x: Simulation persistence
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(
        name = "real_estate_simulations",
        indexes = {
            @Index(name = "idx_simulation_user_id", columnList = "user_id"),
            @Index(name = "idx_simulation_type", columnList = "simulation_type"),
            @Index(name = "idx_simulation_user_type", columnList = "user_id, simulation_type"),
            @Index(name = "idx_simulation_name", columnList = "user_id, name")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RealEstateSimulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    /** The user who owns this simulation. */
    @NotNull(message = "User ID cannot be null")
    @Column(name = "user_id", nullable = false)
    @ToString.Include
    private Long userId;

    /** Relationship to the User entity. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /** Simulation name for user identification. */
    @NotNull(message = "Simulation name cannot be null")
    @Size(min = 1, max = 200, message = "Name must be between 1 and 200 characters")
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Type of simulation: 'buy_rent' or 'rental_investment'. */
    @NotNull(message = "Simulation type cannot be null")
    @Pattern(
            regexp = "buy_rent|rental_investment",
            message = "Type must be 'buy_rent' or 'rental_investment'")
    @Column(name = "simulation_type", nullable = false, length = 20)
    private String simulationType;

    /**
     * Simulation data stored as encrypted JSON string. Contains all user inputs and potentially
     * calculated results.
     */
    @NotNull(message = "Simulation data cannot be null")
    @Size(max = 10000, message = "Data cannot exceed 10000 characters")
    @Column(name = "data", nullable = false, columnDefinition = "TEXT")
    private String data;

    /** Timestamp when this simulation was created. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when this simulation was last updated. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
