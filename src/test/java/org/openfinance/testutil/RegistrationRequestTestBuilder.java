package org.openfinance.testutil;

import org.openfinance.dto.UserRegistrationRequest;

/**
 * Test data builder for UserRegistrationRequest DTO. Provides fluent API for creating test
 * registration requests.
 *
 * <p>Example usage:
 *
 * <pre>
 * UserRegistrationRequest request = RegistrationRequestTestBuilder.aRegistrationRequest()
 *     .withUsername("newuser")
 *     .withPassword("SecurePass123!")
 *     .build();
 * </pre>
 */
public class RegistrationRequestTestBuilder {

    private String username = "testuser";
    private String email = "test@example.com";
    private String password = "TestPassword123!";
    private String masterPassword = "MasterPass456!";

    private RegistrationRequestTestBuilder() {}

    public static RegistrationRequestTestBuilder aRegistrationRequest() {
        return new RegistrationRequestTestBuilder();
    }

    public RegistrationRequestTestBuilder withUsername(String username) {
        this.username = username;
        return this;
    }

    public RegistrationRequestTestBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public RegistrationRequestTestBuilder withPassword(String password) {
        this.password = password;
        return this;
    }

    public RegistrationRequestTestBuilder withMasterPassword(String masterPassword) {
        this.masterPassword = masterPassword;
        return this;
    }

    public UserRegistrationRequest build() {
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        request.setMasterPassword(masterPassword);
        return request;
    }
}
