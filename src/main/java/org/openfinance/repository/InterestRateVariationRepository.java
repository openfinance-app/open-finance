package org.openfinance.repository;

import java.util.List;
import org.openfinance.entity.InterestRateVariation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for InterestRateVariation entity. */
@Repository
public interface InterestRateVariationRepository
        extends JpaRepository<InterestRateVariation, Long> {

    /**
     * Find all interest rate variations for a specific account, ordered by validFrom descending.
     *
     * @param accountId the account ID
     * @return list of variations
     */
    List<InterestRateVariation> findByAccountIdOrderByValidFromDesc(Long accountId);
}
