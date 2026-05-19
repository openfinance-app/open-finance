package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.CategoryType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkroogeImportMetadata {

    @Builder.Default private List<SkroogeInstitution> institutions = new ArrayList<>();

    @Builder.Default private List<SkroogeAccount> accounts = new ArrayList<>();

    @Builder.Default private List<SkroogeCategory> categories = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkroogeInstitution {
        private Long sourceId;
        private String name;
        private String logo;
        private String country;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkroogeAccount {
        private Long sourceId;
        private Long sourceInstitutionId;
        private String name;
        private String accountNumber;
        private String currency;
        private AccountType accountType;
        private String description;
        private BigDecimal openingBalance;
        private LocalDate openingDate;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkroogeCategory {
        private Long sourceId;
        private Long parentSourceId;
        private String name;
        private String fullName;
        private CategoryType type;
    }
}
