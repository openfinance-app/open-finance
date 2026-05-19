package org.openfinance.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a backup.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRequest {

    /** Optional description for the backup. */
    @Size(max = 500, message = "{backup.description.max}")
    private String description;
}
