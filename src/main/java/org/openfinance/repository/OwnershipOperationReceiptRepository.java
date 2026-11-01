package org.openfinance.repository;

import java.util.Optional;
import org.openfinance.entity.OwnershipOperationReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnershipOperationReceiptRepository
        extends JpaRepository<OwnershipOperationReceipt, Long> {
    Optional<OwnershipOperationReceipt> findByUserIdAndOperationId(Long userId, String operationId);
}
