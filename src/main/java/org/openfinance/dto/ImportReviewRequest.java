package org.openfinance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Review rows and confirmation choices saved together in the encrypted import session. */
@Data
@NoArgsConstructor
public class ImportReviewRequest {
    @NotNull @Valid private List<ImportedTransaction> transactions;

    @NotNull private Map<String, @NotNull @Positive Long> categoryMappings = new HashMap<>();

    private boolean skipDuplicates = true;

    public List<ImportedTransaction> getTransactions() {
        return transactions == null ? null : new ArrayList<>(transactions);
    }

    public void setTransactions(List<ImportedTransaction> transactions) {
        this.transactions = transactions == null ? null : new ArrayList<>(transactions);
    }

    public Map<String, Long> getCategoryMappings() {
        return categoryMappings == null ? null : new HashMap<>(categoryMappings);
    }

    public void setCategoryMappings(Map<String, Long> categoryMappings) {
        this.categoryMappings = categoryMappings == null ? null : new HashMap<>(categoryMappings);
    }
}
