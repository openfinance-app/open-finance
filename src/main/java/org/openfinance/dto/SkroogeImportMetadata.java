package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.InterestDayCountBasis;
import org.openfinance.entity.InterestValueDateMode;

@Data
@Builder
@NoArgsConstructor
public class SkroogeImportMetadata {

    @Builder.Default private List<SkroogeInstitution> institutions = new ArrayList<>();

    @Builder.Default private List<SkroogeAccount> accounts = new ArrayList<>();

    @Builder.Default private List<SkroogeCategory> categories = new ArrayList<>();

    @Builder.Default private List<TransactionRuleRequest> rules = new ArrayList<>();
    @Builder.Default private List<RuleWarning> ruleWarnings = new ArrayList<>();
    private int sourceRuleCount;
    private int skippedRuleCount;
    private int importedRuleCount;
    private int existingRuleCount;

    @Singular private List<SkroogeInterestRate> interestRates;
    @Singular private List<RuleWarning> interestWarnings;
    private int sourceInterestRateCount;
    private int importedInterestRateCount;
    private int existingInterestRateCount;
    private int skippedInterestRateCount;

    public SkroogeImportMetadata(
            List<SkroogeInstitution> institutions,
            List<SkroogeAccount> accounts,
            List<SkroogeCategory> categories,
            List<TransactionRuleRequest> rules,
            List<RuleWarning> ruleWarnings,
            int sourceRuleCount,
            int skippedRuleCount,
            int importedRuleCount,
            int existingRuleCount,
            List<SkroogeInterestRate> interestRates,
            List<RuleWarning> interestWarnings,
            int sourceInterestRateCount,
            int importedInterestRateCount,
            int existingInterestRateCount,
            int skippedInterestRateCount) {
        this.institutions = institutions;
        this.accounts = accounts;
        this.categories = categories;
        this.rules = rules;
        this.ruleWarnings = ruleWarnings;
        this.sourceRuleCount = sourceRuleCount;
        this.skippedRuleCount = skippedRuleCount;
        this.importedRuleCount = importedRuleCount;
        this.existingRuleCount = existingRuleCount;
        setInterestRates(interestRates);
        setInterestWarnings(interestWarnings);
        this.sourceInterestRateCount = sourceInterestRateCount;
        this.importedInterestRateCount = importedInterestRateCount;
        this.existingInterestRateCount = existingInterestRateCount;
        this.skippedInterestRateCount = skippedInterestRateCount;
    }

    public List<SkroogeInterestRate> getInterestRates() {
        return interestRates == null ? List.of() : List.copyOf(interestRates);
    }

    public void setInterestRates(List<SkroogeInterestRate> rates) {
        interestRates = rates == null ? List.of() : List.copyOf(rates);
    }

    public List<RuleWarning> getInterestWarnings() {
        return interestWarnings == null ? List.of() : List.copyOf(interestWarnings);
    }

    public void setInterestWarnings(List<RuleWarning> warnings) {
        interestWarnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record SkroogeInterestRate(
            Long sourceId, Long sourceAccountId, InterestSettings settings) {
        public SkroogeInterestRate(
                Long sourceId, Long sourceAccountId, InterestRateVariationRequest settings) {
            this(
                    sourceId,
                    sourceAccountId,
                    new InterestSettings(
                            settings.getRate(),
                            settings.getTaxRate(),
                            settings.getValidFrom(),
                            settings.getCreditValueDateMode(),
                            settings.getDebitValueDateMode(),
                            settings.getDayCountBasis()));
        }
    }

    public record InterestSettings(
            BigDecimal rate,
            BigDecimal taxRate,
            LocalDate validFrom,
            InterestValueDateMode creditValueDateMode,
            InterestValueDateMode debitValueDateMode,
            InterestDayCountBasis dayCountBasis) {}

    public record RuleWarning(Long sourceId, String code) {}

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
    }
}
