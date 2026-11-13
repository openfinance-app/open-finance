package org.openfinance.repository;

import java.util.Optional;
import org.openfinance.entity.PropertyConversionReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyConversionReceiptRepository
        extends JpaRepository<PropertyConversionReceipt, Long> {
    Optional<PropertyConversionReceipt> findByUserIdAndOperationId(Long userId, String operationId);
}
