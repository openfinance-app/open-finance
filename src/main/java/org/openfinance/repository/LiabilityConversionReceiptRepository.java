package org.openfinance.repository;

import java.util.Optional;
import org.openfinance.entity.LiabilityConversionReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiabilityConversionReceiptRepository
        extends JpaRepository<LiabilityConversionReceipt, Long> {
    Optional<LiabilityConversionReceipt> findByUserIdAndOperationId(
            Long userId, String operationId);
}
