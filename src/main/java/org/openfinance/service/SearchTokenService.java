package org.openfinance.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Generates deterministic, non-reversible search tokens using HMAC-SHA256.
 *
 * <p>
 * Tokens are stored in the {@code search_tokens} table and used for blind-index
 * keyword search on encrypted fields. Each token is an 8-byte (16-hex-char)
 * truncated
 * HMAC — enough to avoid collisions within a user's data while remaining
 * compact.
 *
 * <p>
 * The search key is derived from the user's encryption key via
 * HMAC("search-key-derivation")
 * so that the search tokens cannot be used to decrypt actual data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SEARCH_KEY_DERIVATION_LABEL = "open-finance-search-key-v1";
    private static final int TOKEN_BYTES = 8; // 8 bytes = 16 hex chars

    private final JdbcTemplate jdbcTemplate;

    /**
     * Derives a search-specific sub-key from the user's encryption key using HMAC.
     * This ensures search tokens cannot be used to decrypt actual data.
     *
     * @param encryptionKey the user's AES-256 encryption key
     * @return a derived SecretKey for HMAC token generation
     */
    public SecretKey deriveSearchKey(SecretKey encryptionKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(encryptionKey);
            byte[] derived = mac.doFinal(
                    SEARCH_KEY_DERIVATION_LABEL.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, HMAC_ALGORITHM);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to derive search key", e);
        }
    }

    /**
     * Generates word-level search tokens from plaintext.
     *
     * <p>
     * Process: lowercase → split on non-alphanumeric → remove short words (< 2
     * chars)
     * → HMAC each word → truncate to 8 bytes → hex encode.
     *
     * @param plaintext the plaintext to tokenize
     * @param searchKey the derived search key from {@link #deriveSearchKey}
     * @return set of hex-encoded token strings
     */
    public Set<String> generateTokens(String plaintext, SecretKey searchKey) {
        if (plaintext == null || plaintext.isBlank()) {
            return Collections.emptySet();
        }
        String[] words = plaintext.toLowerCase().split("[^a-zA-Z0-9àâäéèêëïîôùûüÿçœæ]+");
        Set<String> tokens = new LinkedHashSet<>();
        for (String word : words) {
            if (word.length() >= 2) {
                tokens.add(hmacToken(word, searchKey));
            }
        }
        return tokens;
    }

    /**
     * Generates n-gram tokens for substring/prefix search.
     *
     * <p>
     * Each word is split into overlapping n-grams (default n=3), then each n-gram
     * is HMAC'd. This allows partial-word matching at the cost of more tokens per
     * field.
     *
     * @param plaintext the plaintext to tokenize
     * @param searchKey the derived search key
     * @param n         the n-gram size (typically 3)
     * @return set of hex-encoded n-gram token strings
     */
    public Set<String> generateNGramTokens(String plaintext, SecretKey searchKey, int n) {
        if (plaintext == null || plaintext.isBlank()) {
            return Collections.emptySet();
        }
        String lower = plaintext.toLowerCase().replaceAll("[^a-zA-Z0-9àâäéèêëïîôùûüÿçœæ]+", " ").trim();
        Set<String> tokens = new LinkedHashSet<>();
        String[] words = lower.split("\\s+");
        for (String word : words) {
            if (word.length() < n) {
                // Word shorter than n-gram size — hash the whole word
                if (word.length() >= 2) {
                    tokens.add(hmacToken(word, searchKey));
                }
            } else {
                for (int i = 0; i <= word.length() - n; i++) {
                    tokens.add(hmacToken(word.substring(i, i + n), searchKey));
                }
            }
        }
        return tokens;
    }

    /**
     * Indexes an entity's field by generating tokens and storing them in the
     * {@code search_tokens} table. Removes any existing tokens for this
     * entity/field first.
     *
     * @param userId     the owning user's ID
     * @param entityType the entity type (e.g., "TRANSACTION", "ACCOUNT")
     * @param entityId   the entity's ID
     * @param fieldName  the field being indexed (e.g., "description", "name")
     * @param plaintext  the plaintext value to tokenize
     * @param searchKey  the derived search key
     */
    public void indexField(
            Long userId,
            String entityType,
            Long entityId,
            String fieldName,
            String plaintext,
            SecretKey searchKey) {
        // Remove old tokens for this entity+field
        jdbcTemplate.update(
                "DELETE FROM search_tokens WHERE entity_type = ? AND entity_id = ? AND field_name = ?",
                entityType, entityId, fieldName);

        if (plaintext == null || plaintext.isBlank()) {
            return;
        }

        // Generate word tokens + 3-gram tokens for substring matching
        Set<String> allTokens = new LinkedHashSet<>();
        allTokens.addAll(generateTokens(plaintext, searchKey));
        allTokens.addAll(generateNGramTokens(plaintext, searchKey, 3));

        // Batch insert
        if (!allTokens.isEmpty()) {
            List<Object[]> batchArgs = allTokens.stream()
                    .map(token -> new Object[] { userId, entityType, entityId, fieldName, token })
                    .toList();
            jdbcTemplate.batchUpdate(
                    "INSERT INTO search_tokens (user_id, entity_type, entity_id, field_name, token) VALUES (?, ?, ?, ?, ?)",
                    batchArgs);
        }
    }

    /**
     * Indexes multiple fields of an entity at once.
     *
     * @param userId     the owning user's ID
     * @param entityType the entity type
     * @param entityId   the entity's ID
     * @param fields     pairs of (fieldName, plaintext)
     * @param searchKey  the derived search key
     */
    public void indexEntity(
            Long userId,
            String entityType,
            Long entityId,
            List<String[]> fields,
            SecretKey searchKey) {
        for (String[] field : fields) {
            if (field.length == 2) {
                indexField(userId, entityType, entityId, field[0], field[1], searchKey);
            }
        }
    }

    /**
     * Removes all search tokens for an entity (used on delete).
     *
     * @param entityType the entity type
     * @param entityId   the entity's ID
     */
    public void removeEntity(String entityType, Long entityId) {
        jdbcTemplate.update(
                "DELETE FROM search_tokens WHERE entity_type = ? AND entity_id = ?",
                entityType, entityId);
    }

    /**
     * Searches for entity IDs matching a query by tokenizing the query and
     * looking up matching tokens.
     *
     * @param userId     the user performing the search
     * @param entityType the entity type to search (or null for all types)
     * @param query      the search query
     * @param searchKey  the derived search key
     * @param limit      maximum results
     * @return list of matching entity IDs ordered by relevance (most token matches
     *         first)
     */
    public List<Long> search(
            Long userId,
            String entityType,
            String query,
            SecretKey searchKey,
            int limit) {
        Set<String> queryTokens = generateSearchTokens(query, searchKey);
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = String.join(",", Collections.nCopies(queryTokens.size(), "?"));

        String sql;
        Object[] params;
        if (entityType != null) {
            sql = "SELECT entity_id, COUNT(DISTINCT token) as matches "
                    + "FROM search_tokens "
                    + "WHERE user_id = ? AND entity_type = ? AND token IN (" + placeholders + ") "
                    + "GROUP BY entity_id "
                    + "ORDER BY matches DESC "
                    + "LIMIT ?";
            Object[] base = { userId, entityType };
            params = new Object[base.length + queryTokens.size() + 1];
            System.arraycopy(base, 0, params, 0, base.length);
            int i = base.length;
            for (String token : queryTokens) {
                params[i++] = token;
            }
            params[i] = limit;
        } else {
            sql = "SELECT entity_id, COUNT(DISTINCT token) as matches "
                    + "FROM search_tokens "
                    + "WHERE user_id = ? AND token IN (" + placeholders + ") "
                    + "GROUP BY entity_type, entity_id "
                    + "ORDER BY matches DESC "
                    + "LIMIT ?";
            Object[] base = { userId };
            params = new Object[base.length + queryTokens.size() + 1];
            params[0] = userId;
            int i = 1;
            for (String token : queryTokens) {
                params[i++] = token;
            }
            params[i] = limit;
        }

        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("entity_id"), params);
    }

    /**
     * Searches for entities matching a query, returning entity type + ID pairs.
     *
     * @param userId    the user performing the search
     * @param query     the search query
     * @param searchKey the derived search key
     * @param limit     maximum results
     * @return list of [entityType, entityId] pairs ordered by relevance
     */
    public List<Object[]> searchAll(
            Long userId,
            String query,
            SecretKey searchKey,
            int limit) {
        Set<String> queryTokens = generateSearchTokens(query, searchKey);
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = String.join(",", Collections.nCopies(queryTokens.size(), "?"));
        String sql = "SELECT entity_type, entity_id, COUNT(DISTINCT token) as matches "
                + "FROM search_tokens "
                + "WHERE user_id = ? AND token IN (" + placeholders + ") "
                + "GROUP BY entity_type, entity_id "
                + "ORDER BY matches DESC "
                + "LIMIT ?";

        Object[] params = new Object[1 + queryTokens.size() + 1];
        params[0] = userId;
        int i = 1;
        for (String token : queryTokens) {
            params[i++] = token;
        }
        params[i] = limit;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new Object[] { rs.getString("entity_type"), rs.getLong("entity_id") },
                params);
    }

    private Set<String> generateSearchTokens(String plaintext, SecretKey searchKey) {
        Set<String> queryTokens = new LinkedHashSet<>();
        queryTokens.addAll(generateTokens(plaintext, searchKey));
        queryTokens.addAll(generateNGramTokens(plaintext, searchKey, 3));
        return queryTokens;
    }

    /**
     * Computes a truncated HMAC-SHA256 of a word, returned as lowercase hex.
     */
    private String hmacToken(String word, SecretKey searchKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(searchKey);
            byte[] fullHmac = mac.doFinal(word.getBytes(StandardCharsets.UTF_8));
            byte[] truncated = Arrays.copyOf(fullHmac, TOKEN_BYTES);
            return bytesToHex(truncated);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
