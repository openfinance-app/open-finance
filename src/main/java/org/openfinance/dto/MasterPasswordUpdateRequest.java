package org.openfinance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating user's master password.
 *
 * <p>The master password is used to derive the encryption key for securing sensitive financial
 * data. When changed, a new salt is generated and the user's data will need to be re-encrypted with
 * the new key.
 *
 * <p>Note: Full data re-encryption is a complex operation that requires re-encrypting all accounts,
 * transactions, assets, and liabilities. This implementation updates the salt and validates the
 * current password.
 *
 * <p>Requirement REQ-6.3.16: Password change functionality
 */
public record MasterPasswordUpdateRequest(

        /**
         * Current master password for verification. Used to derive the current encryption key to
         * verify correctness.
         */
        @NotBlank(message = "{master.password.current.required}") String currentMasterPassword,

        /** New master password to set. Must be at least 8 characters long. */
        @NotBlank(message = "{master.password.new.required}")
                @Size(min = 8, message = "{master.password.new.min}")
                String newMasterPassword) {}
