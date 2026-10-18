package org.openfinance.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.entity.Account;
import org.openfinance.entity.InterestPeriod;
import org.openfinance.entity.InterestRateVariation;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.InterestRateVariationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SkroogeInterestImportServiceImpl implements SkroogeInterestImportService {
    private final AccountRepository accountRepository;
    private final InterestRateVariationRepository variationRepository;

    @Override
    public void importInterest(
            SkroogeImportMetadata metadata, Map<Long, Long> accountIds, Long userId) {
        metadata.setImportedInterestRateCount(0);
        metadata.setExistingInterestRateCount(0);
        metadata.setSkippedInterestRateCount(
                metadata.getSourceInterestRateCount() - metadata.getInterestRates().size());
        metadata.setInterestWarnings(
                metadata.getInterestWarnings().stream()
                        .filter(w -> w.code().equals("unsupported"))
                        .toList());
        for (SkroogeImportMetadata.SkroogeInterestRate imported : metadata.getInterestRates()) {
            Long accountId = accountIds.get(imported.sourceAccountId());
            Account account =
                    accountId == null
                            ? null
                            : accountRepository.findByIdAndUserId(accountId, userId).orElse(null);
            if (account == null) {
                skipped(metadata, imported.sourceId(), "accountMissing");
                continue;
            }
            SkroogeImportMetadata.InterestSettings settings = imported.settings();
            InterestRateVariation existing =
                    variationRepository.findByAccountIdOrderByValidFromDesc(accountId).stream()
                            .filter(v -> v.getValidFrom().equals(settings.validFrom()))
                            .findFirst()
                            .orElse(null);
            if (existing != null && existing.getRate().compareTo(settings.rate()) != 0) {
                skipped(metadata, imported.sourceId(), "conflict");
                continue;
            }
            boolean same =
                    existing != null
                            && existing.getCreditValueDateMode() == settings.creditValueDateMode()
                            && existing.getDebitValueDateMode() == settings.debitValueDateMode()
                            && existing.getDayCountBasis() == settings.dayCountBasis();
            if (same) {
                metadata.setExistingInterestRateCount(metadata.getExistingInterestRateCount() + 1);
            } else {
                InterestRateVariation variation =
                        existing != null
                                ? existing
                                : InterestRateVariation.builder()
                                        .accountId(accountId)
                                        .validFrom(settings.validFrom())
                                        .rate(settings.rate())
                                        .build();
                variation.setCreditValueDateMode(settings.creditValueDateMode());
                variation.setDebitValueDateMode(settings.debitValueDateMode());
                variation.setDayCountBasis(settings.dayCountBasis());
                variationRepository.save(variation);
                metadata.setImportedInterestRateCount(metadata.getImportedInterestRateCount() + 1);
            }
            if (!Boolean.TRUE.equals(account.getIsInterestEnabled())
                    || account.getInterestPeriod() == null) {
                account.setIsInterestEnabled(true);
                if (account.getInterestPeriod() == null)
                    account.setInterestPeriod(InterestPeriod.ANNUAL);
                accountRepository.save(account);
            }
        }
    }

    private void skipped(SkroogeImportMetadata metadata, Long sourceId, String code) {
        metadata.setSkippedInterestRateCount(metadata.getSkippedInterestRateCount() + 1);
        java.util.List<SkroogeImportMetadata.RuleWarning> warnings =
                new java.util.ArrayList<>(metadata.getInterestWarnings());
        warnings.add(new SkroogeImportMetadata.RuleWarning(sourceId, code));
        metadata.setInterestWarnings(warnings);
    }
}
