package org.openfinance.service;

import lombok.RequiredArgsConstructor;
import org.openfinance.entity.RevokedToken;
import org.openfinance.entity.User;
import org.openfinance.repository.RevokedTokenRepository;
import org.openfinance.security.EncryptionKeyCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Persistent JWT logout revocation and credential-version invalidation. */
@Service
@RequiredArgsConstructor
public class SessionRevocationServiceImpl implements SessionRevocationService {
    private final RevokedTokenRepository revokedTokens;
    private final JwtService jwtService;
    private final EncryptionKeyCache encryptionKeyCache;

    @Transactional(readOnly = true)
    public boolean isRevoked(String token) {
        return revokedTokens.existsById(jwtService.extractSessionId(token));
    }

    @Transactional
    public void revoke(String token) {
        if (token == null || !jwtService.validateToken(token)) return;
        revokedTokens.save(
                new RevokedToken(
                        jwtService.extractSessionId(token),
                        jwtService.sessionRevocationExpiresAt(token)));
    }

    /** The caller persists the user in the same transaction as the password change. */
    public void revokeAllSessions(User user) {
        user.setTokenVersion(Math.incrementExact(user.getTokenVersion()));
        Long userId = user.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            encryptionKeyCache.invalidateUserSessions(userId);
                        }
                    });
        } else {
            encryptionKeyCache.invalidateUserSessions(userId);
        }
    }

    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void purgeExpiredTokens() {
        revokedTokens.deleteByExpiresAtLessThanEqual(System.currentTimeMillis());
    }
}
