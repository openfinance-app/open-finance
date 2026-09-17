package org.openfinance.repository;

import java.util.List;
import org.openfinance.entity.AccountStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountStatusHistoryRepository extends JpaRepository<AccountStatusHistory, Long> {
    List<AccountStatusHistory> findByUserId(Long userId);
}
