package org.openfinance.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SkroogeImportParseResult {
    private List<ImportedTransaction> transactions;
    private SkroogeImportMetadata skroogeMetadata;
    private String currency;
}
