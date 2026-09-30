package org.openfinance.repository;

import java.util.List;
import org.openfinance.entity.PropertyStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyStatusHistoryRepository
        extends JpaRepository<PropertyStatusHistory, Long> {
    List<PropertyStatusHistory> findByUserId(Long userId);
}
