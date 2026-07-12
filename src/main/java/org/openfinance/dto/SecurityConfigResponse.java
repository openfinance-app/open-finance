package org.openfinance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Public, non-sensitive security settings needed before authentication. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityConfigResponse {

    private boolean encryptionEnabled;
}
