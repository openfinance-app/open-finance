package org.openfinance.service;

import java.util.Base64;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;

/** Raw encrypted-column access shared by key rotation and portable user backups. */
public interface EncryptedUserDataService {
    boolean isEncryptedColumn(String table, String column);

    String plaintext(String value, SecretKey key);

    Object translate(
            String table, String column, Object value, SecretKey sourceKey, SecretKey targetKey);

    void rotate(Long userId, SecretKey sourceKey, SecretKey targetKey);

    void rebuildSearchTokens(Long userId, SecretKey key);

    /** Refresh only the records changed by a reversible action, including removed records. */
    void refreshSearchTokens(Long userId, SecretKey key, Map<String, Set<Long>> changedIds);

    static String identifier(String name) {
        if (!name.matches("[a-z][a-z0-9_]*"))
            throw new IllegalArgumentException("Invalid database identifier");
        return '"' + name + '"';
    }

    static boolean looksEncrypted(String value) {
        try {
            return value != null && Base64.getDecoder().decode(value).length >= 28;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
