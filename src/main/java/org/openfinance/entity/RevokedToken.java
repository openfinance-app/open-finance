package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One-way token fingerprints retained only until their JWT expiration. */
@Entity
@Table(name = "revoked_tokens")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {
    @Id
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private long expiresAt;
}
