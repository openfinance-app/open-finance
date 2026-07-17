package org.openfinance.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.SpringContextHolder;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.User;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({EncryptionService.class, SpringContextHolder.class})
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
        })
@DisplayName("Encrypted Storage Integration Tests")
class EncryptedStorageIntegrationTest {

    private static final SecretKey TEST_KEY =
            new SecretKeySpec(
                    "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8), "AES");

    @Autowired private AccountRepository accountRepository;

    @Autowired private UserRepository userRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private EntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        user =
                userRepository.save(
                        User.builder()
                                .username("enc-user")
                                .email("enc-user@example.com")
                                .passwordHash("$2a$10$hashedPasswordExample123456789")
                                .masterPasswordSalt("base64EncodedSaltExample==")
                                .build());
    }

    @AfterEach
    void tearDown() {
        EncryptionContext.clear();
    }

    @Test
    @DisplayName("Encrypted account fields are not stored as plaintext in the database")
    void encryptedAccountFieldsAreNotStoredAsPlaintextInTheDatabase() {
        Account account =
                Account.builder()
                        .userId(user.getId())
                        .name("Household Checking")
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .balance(new BigDecimal("1234.56"))
                        .openingBalance(new BigDecimal("1200.00"))
                        .description("Primary household account")
                        .build();

        EncryptionContext.setKey(TEST_KEY);
        Account saved = accountRepository.saveAndFlush(account);
        EncryptionContext.clear();

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "SELECT name, balance, opening_balance, description FROM accounts WHERE id = ?",
                        saved.getId());

        assertThat((String) row.get("name"))
                .isNotEqualTo("Household Checking")
                .doesNotContain("Household Checking");
        assertThat((String) row.get("balance")).isNotEqualTo("1234.56").doesNotContain("1234.56");
        assertThat((String) row.get("opening_balance"))
                .isNotEqualTo("1200.00")
                .doesNotContain("1200.00");
        assertThat((String) row.get("description"))
                .isNotEqualTo("Primary household account")
                .doesNotContain("Primary household account");

        entityManager.clear();
        EncryptionContext.setKey(TEST_KEY);

        Account reloaded = accountRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getName()).isEqualTo("Household Checking");
        assertThat(reloaded.getBalance()).isEqualByComparingTo("1234.56");
        assertThat(reloaded.getOpeningBalance()).isEqualByComparingTo("1200.00");
        assertThat(reloaded.getDescription()).isEqualTo("Primary household account");
    }
}
