package org.openfinance.repository;

import org.openfinance.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    void deleteByExpiresAtLessThanEqual(long expiresAt);
}
