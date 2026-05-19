package org.openfinance.testutil;

import java.time.LocalDateTime;
import org.openfinance.entity.User;

/**
 * Test data builder for User entity. Provides fluent API for creating test User instances with
 * sensible defaults.
 *
 * <p>Example usage:
 *
 * <pre>
 * User user = UserTestBuilder.aUser()
 *     .withUsername("testuser")
 *     .withEmail("test@example.com")
 *     .build();
 * </pre>
 */
public class UserTestBuilder {

    private Long id;
    private String username = "testuser";
    private String email = "test@example.com";
    private String passwordHash = "$2a$10$hashedpassword";
    private String masterPasswordSalt = "salt123456";
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    private UserTestBuilder() {}

    /**
     * Create a new UserTestBuilder with default values
     *
     * @return new builder instance
     */
    public static UserTestBuilder aUser() {
        return new UserTestBuilder();
    }

    /**
     * Create a new UserTestBuilder with saved entity defaults (includes ID)
     *
     * @return new builder instance with ID
     */
    public static UserTestBuilder aSavedUser() {
        return new UserTestBuilder().withId(1L);
    }

    public UserTestBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public UserTestBuilder withUsername(String username) {
        this.username = username;
        return this;
    }

    public UserTestBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public UserTestBuilder withPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        return this;
    }

    public UserTestBuilder withMasterPasswordSalt(String masterPasswordSalt) {
        this.masterPasswordSalt = masterPasswordSalt;
        return this;
    }

    public UserTestBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public UserTestBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    /**
     * Build the User instance
     *
     * @return User entity
     */
    public User build() {
        User user =
                User.builder()
                        .username(username)
                        .email(email)
                        .passwordHash(passwordHash)
                        .masterPasswordSalt(masterPasswordSalt)
                        .createdAt(createdAt)
                        .updatedAt(updatedAt)
                        .build();

        // Use reflection to set ID if needed (since it's generated)
        if (id != null) {
            try {
                var idField = User.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(user, id);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set ID", e);
            }
        }

        return user;
    }
}
