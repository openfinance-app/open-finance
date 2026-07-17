package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.converter.EncryptedStringConverter;

/**
 * Entity representing a payee (merchant, service provider, etc.).
 *
 * <p>Payees are used to categorize and organize transactions. Users can create custom payees or use
 * system-provided defaults. Payees help with transaction search, duplicate detection, and financial
 * reporting.
 *
 * <p>Requirements: Payee Management Feature
 */
@Entity
@Table(
        name = "payees",
        indexes = {
            @Index(name = "idx_payee_is_system", columnList = "is_system"),
            @Index(name = "idx_payee_name", columnList = "name")
        })
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Payee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Name of the payee. Examples: "Amazon", "Netflix", "EDF", "Carrefour" */
    @NotBlank(message = "Payee name cannot be blank")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Column(nullable = false, length = 512)
    @Convert(converter = EncryptedStringConverter.class)
    private String name;

    /**
     * Payee logo stored as base64-encoded string or file path reference. Supports PNG, JPG, SVG
     * formats. Can be null for payees without logos.
     */
    @Column(name = "logo", columnDefinition = "TEXT")
    private String logo;

    /**
     * Default category to associate with this payee.
     *
     * <p>When a transaction is created with this payee, the category will be auto-filled from this
     * field (REQ-CAT-5.1).
     *
     * <p>Requirements: REQ-CAT-5.1 - Payee-to-Category Auto-Fill
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category defaultCategory;

    /**
     * Flag indicating if this is a system-provided payee. System payees cannot be deleted by users
     * but can be hidden.
     */
    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

    /**
     * ID of the user who created this payee. Null for system payees (visible to all users). Custom
     * payees are only visible to their creator.
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Flag indicating if this payee is active/visible. Users can hide system payees without
     * deleting them.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Timestamp when the payee was created. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when the payee was last updated. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isSystem == null) {
            isSystem = false;
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Check if this payee can be deleted. System payees cannot be deleted. */
    public boolean isDeletable() {
        return !Boolean.TRUE.equals(isSystem);
    }
}
