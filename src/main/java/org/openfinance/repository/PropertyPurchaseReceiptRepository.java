package org.openfinance.repository;

import java.util.Optional;
import org.openfinance.entity.PropertyPurchaseReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyPurchaseReceiptRepository
        extends JpaRepository<PropertyPurchaseReceipt, Long> {
    Optional<PropertyPurchaseReceipt> findByUserIdAndOperationId(Long userId, String operationId);
}
