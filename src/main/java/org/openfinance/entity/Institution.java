package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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

/**
 * Entity representing a financial institution (bank, investment platform, etc.).
 *
 * <p>Institutions are associated with accounts to help users identify which financial institution
 * each account belongs to.
 *
 * <p>Requirements: REQ-2.6.1.3 - Predefined Financial Institutions
 */
@Entity
@Table(
        name = "institutions",
        indexes = {
            @Index(name = "idx_institution_country", columnList = "country"),
            @Index(name = "idx_institution_is_system", columnList = "is_system"),
            @Index(name = "idx_institution_name", columnList = "name")
        })
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Name of the financial institution. Examples: "BNP Paribas", "Deutsche Bank", "Boursorama" */
    @NotBlank(message = "Institution name cannot be blank")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * BIC (Bank Identifier Code) in ISO 9362 format. 8 or 11 character code identifying the
     * institution. Example: "BNPAFRPP" for BNP Paribas France
     */
    @Size(min = 4, max = 15, message = "BIC must be 4 to 15 characters")
    @Column(name = "bic", length = 15)
    private String bic;

    /** Country code in ISO 3166-1 alpha-2 format. Examples: FR, DE, ES, IT, NL */
    @Size(min = 2, max = 2, message = "Country must be 2-letter ISO code")
    @Column(name = "country", length = 2)
    private String country;

    /** Institution logo stored as base64-encoded string. Supports PNG, JPG, SVG formats. */
    @Column(name = "logo", columnDefinition = "TEXT")
    private String logo;

    /**
     * Flag indicating if this is a system-provided institution. System institutions cannot be
     * deleted by users.
     */
    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

    /**
     * ID of the user who created this institution. Null for system institutions (visible to all
     * users). Custom institutions are only visible to their creator.
     */
    @Column(name = "user_id")
    private Long userId;

    /** Timestamp when the institution was created. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when the institution was last updated. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isSystem == null) {
            isSystem = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Check if this institution can be deleted. System institutions cannot be deleted. */
    public boolean isDeletable() {
        return !Boolean.TRUE.equals(isSystem);
    }
}
