package org.openfinance.repository;

import java.time.LocalDateTime;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OperationHistoryRepository extends JpaRepository<OperationHistory, Long> {

    // neither filter (existing — used by internal callers such as undo)
    Page<OperationHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // since only
    Page<OperationHistory> findByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long userId, LocalDateTime since, Pageable pageable);

    // entityType only
    Page<OperationHistory> findByUserIdAndEntityTypeOrderByCreatedAtDesc(
            Long userId, EntityType entityType, Pageable pageable);

    // entityType + since
    Page<OperationHistory>
            findByUserIdAndEntityTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                    Long userId, EntityType entityType, LocalDateTime since, Pageable pageable);
}
