package org.openfinance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Public, non-sensitive import settings needed to drive the Import view. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportConfigResponse {

    private boolean skroogeJsonEnabled;
}
