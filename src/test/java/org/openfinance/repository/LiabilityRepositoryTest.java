package org.openfinance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for LiabilityRepository. Tests CRUD operations, custom queries, and user
 * isolation.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
        })
class LiabilityRepositoryTest {

    @Autowired private TestEntityManager entityManager;

    @Autowired private LiabilityRepository liabilityRepository;

    private User testUser1;
    private User testUser2;

    @BeforeEach
    void setUp() {
        // Create test users
        testUser1 =
                User.builder()
                        .username("user1")
                        .email("user1@test.com")
                        .passwordHash("hash1")
                        .masterPasswordSalt("salt1")
                        .build();
        entityManager.persist(testUser1);

        testUser2 =
                User.builder()
                        .username("user2")
                        .email("user2@test.com")
                        .passwordHash("hash2")
                        .masterPasswordSalt("salt2")
                        .build();
        entityManager.persist(testUser2);

        entityManager.flush();
    }

    private Liability createLiability(User user, String name, LiabilityType type) {
        Liability liability = new Liability();
        liability.setUser(user);
        liability.setUserId(user.getId());
        liability.setName(name);
        liability.setType(type);
        liability.setPrincipal("encrypted_10000");
        liability.setCurrentBalance("encrypted_8000");
        liability.setInterestRate("encrypted_5.5");
        liability.setStartDate(LocalDate.now().minusYears(1));
        liability.setEndDate(LocalDate.now().plusYears(2));
        liability.setMinimumPayment("encrypted_250");
        liability.setCurrency("USD");
        return liability;
    }

    @Test
    void shouldSaveAndFindLiabilityById() {
        // Given
        Liability liability = createLiability(testUser1, "Home Mortgage", LiabilityType.MORTGAGE);

        // When
        Liability saved = liabilityRepository.save(liability);
        entityManager.flush();
        Optional<Liability> found = liabilityRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Home Mortgage");
        assertThat(found.get().getType()).isEqualTo(LiabilityType.MORTGAGE);
        assertThat(found.get().getUserId()).isEqualTo(testUser1.getId());
    }

    @Test
    void shouldFindByUserIdOrderByCreatedAtDesc() throws InterruptedException {
        // Given
        Liability liability1 = createLiability(testUser1, "Mortgage", LiabilityType.MORTGAGE);
        Liability liability2 = createLiability(testUser1, "Car Loan", LiabilityType.LOAN);
        Liability liability3 = createLiability(testUser2, "Credit Card", LiabilityType.CREDIT_CARD);

        liabilityRepository.save(liability1);
        Thread.sleep(10); // Ensure different timestamps
        liabilityRepository.save(liability2);
        liabilityRepository.save(liability3);
        entityManager.flush();

        // When
        List<Liability> user1Liabilities =
                liabilityRepository.findByUserIdOrderByCreatedAtDesc(testUser1.getId());

        // Then
        assertThat(user1Liabilities).hasSize(2);
        assertThat(user1Liabilities.get(0).getName()).isEqualTo("Car Loan"); // Most recent first
        assertThat(user1Liabilities.get(1).getName()).isEqualTo("Mortgage");
    }

    @Test
    void shouldFindByIdAndUserId() {
        // Given
        Liability liability1 = createLiability(testUser1, "Mortgage", LiabilityType.MORTGAGE);
        Liability saved = liabilityRepository.save(liability1);
        entityManager.flush();

        // When
        Optional<Liability> found =
                liabilityRepository.findByIdAndUserId(saved.getId(), testUser1.getId());
        Optional<Liability> notFound =
                liabilityRepository.findByIdAndUserId(saved.getId(), testUser2.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Mortgage");
        assertThat(notFound).isEmpty(); // User2 cannot access User1's liability
    }

    @Test
    void shouldFindByUserIdAndTypeOrderByCreatedAtDesc() {
        // Given
        Liability mortgage = createLiability(testUser1, "Home Mortgage", LiabilityType.MORTGAGE);
        Liability loan1 = createLiability(testUser1, "Car Loan", LiabilityType.LOAN);
        Liability loan2 = createLiability(testUser1, "Personal Loan", LiabilityType.PERSONAL_LOAN);
        Liability creditCard = createLiability(testUser1, "Credit Card", LiabilityType.CREDIT_CARD);

        liabilityRepository.save(mortgage);
        liabilityRepository.save(loan1);
        liabilityRepository.save(loan2);
        liabilityRepository.save(creditCard);
        entityManager.flush();

        // When
        List<Liability> loans =
                liabilityRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
                        testUser1.getId(), LiabilityType.LOAN);
        List<Liability> mortgages =
                liabilityRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
                        testUser1.getId(), LiabilityType.MORTGAGE);

        // Then
        assertThat(loans).hasSize(1);
        assertThat(loans.get(0).getName()).isEqualTo("Car Loan");
        assertThat(mortgages).hasSize(1);
        assertThat(mortgages.get(0).getName()).isEqualTo("Home Mortgage");
    }

    @Test
    void shouldCountByUserId() {
        // Given
        Liability liability1 = createLiability(testUser1, "Mortgage", LiabilityType.MORTGAGE);
        Liability liability2 = createLiability(testUser1, "Car Loan", LiabilityType.LOAN);
        Liability liability3 = createLiability(testUser2, "Credit Card", LiabilityType.CREDIT_CARD);

        liabilityRepository.save(liability1);
        liabilityRepository.save(liability2);
        liabilityRepository.save(liability3);
        entityManager.flush();

        // When
        long user1Count = liabilityRepository.countByUserId(testUser1.getId());
        long user2Count = liabilityRepository.countByUserId(testUser2.getId());

        // Then
        assertThat(user1Count).isEqualTo(2);
        assertThat(user2Count).isEqualTo(1);
    }

    @Test
    void shouldCheckExistsByIdAndUserId() {
        // Given
        Liability liability = createLiability(testUser1, "Mortgage", LiabilityType.MORTGAGE);
        Liability saved = liabilityRepository.save(liability);
        entityManager.flush();

        // When
        boolean existsForUser1 =
                liabilityRepository.existsByIdAndUserId(saved.getId(), testUser1.getId());
        boolean existsForUser2 =
                liabilityRepository.existsByIdAndUserId(saved.getId(), testUser2.getId());

        // Then
        assertThat(existsForUser1).isTrue();
        assertThat(existsForUser2).isFalse(); // User2 cannot see User1's liability
    }

    @Test
    void shouldDeleteByIdAndUserId() {
        // Given
        Liability liability = createLiability(testUser1, "Mortgage", LiabilityType.MORTGAGE);
        Liability saved = liabilityRepository.save(liability);
        entityManager.flush();
        Long liabilityId = saved.getId();

        // When
        liabilityRepository.deleteByIdAndUserId(liabilityId, testUser1.getId());
        entityManager.flush();

        // Then
        assertThat(liabilityRepository.findById(liabilityId)).isEmpty();
    }

    @Test
    void shouldNotDeleteByIdAndUserId_WhenWrongUser() {
        // Given
        Liability liability = createLiability(testUser1, "Mortgage", LiabilityType.MORTGAGE);
        Liability saved = liabilityRepository.save(liability);
        entityManager.flush();
        Long liabilityId = saved.getId();

        // When
        liabilityRepository.deleteByIdAndUserId(liabilityId, testUser2.getId());
        entityManager.flush();

        // Then
        assertThat(liabilityRepository.findById(liabilityId)).isPresent(); // Still exists
    }

    @Test
    void shouldUpdateLiability() {
        // Given
        Liability liability = createLiability(testUser1, "Original Name", LiabilityType.MORTGAGE);
        Liability saved = liabilityRepository.save(liability);
        entityManager.flush();
        entityManager.clear();

        // When
        Liability toUpdate = liabilityRepository.findById(saved.getId()).orElseThrow();
        toUpdate.setName("Updated Name");
        toUpdate.setCurrentBalance("encrypted_7000");
        Liability updated = liabilityRepository.save(toUpdate);
        entityManager.flush();

        // Then
        Liability found = liabilityRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Updated Name");
        assertThat(found.getCurrentBalance()).isEqualTo("encrypted_7000");
        assertThat(found.getUpdatedAt()).isAfter(found.getCreatedAt());
    }

    @Test
    void shouldIsolateUserData() {
        // Given
        Liability user1Liability =
                createLiability(testUser1, "User1 Mortgage", LiabilityType.MORTGAGE);
        Liability user2Liability = createLiability(testUser2, "User2 Loan", LiabilityType.LOAN);

        liabilityRepository.save(user1Liability);
        liabilityRepository.save(user2Liability);
        entityManager.flush();

        // When
        List<Liability> user1Liabilities =
                liabilityRepository.findByUserIdOrderByCreatedAtDesc(testUser1.getId());
        List<Liability> user2Liabilities =
                liabilityRepository.findByUserIdOrderByCreatedAtDesc(testUser2.getId());

        // Then
        assertThat(user1Liabilities).hasSize(1);
        assertThat(user1Liabilities.get(0).getName()).isEqualTo("User1 Mortgage");

        assertThat(user2Liabilities).hasSize(1);
        assertThat(user2Liabilities.get(0).getName()).isEqualTo("User2 Loan");
    }

    @Test
    void shouldHandleOptionalFields() {
        // Given
        Liability liability = new Liability();
        liability.setUser(testUser1);
        liability.setUserId(testUser1.getId());
        liability.setName("Minimal Liability");
        liability.setType(LiabilityType.OTHER);
        liability.setPrincipal("encrypted_5000");
        liability.setCurrentBalance("encrypted_5000");
        liability.setStartDate(LocalDate.now());
        liability.setCurrency("USD");
        // No interestRate, endDate, minimumPayment, notes

        // When
        Liability saved = liabilityRepository.save(liability);
        entityManager.flush();

        // Then
        Optional<Liability> found = liabilityRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getInterestRate()).isNull();
        assertThat(found.get().getEndDate()).isNull();
        assertThat(found.get().getMinimumPayment()).isNull();
        assertThat(found.get().getNotes()).isNull();
    }

    @Test
    void shouldReturnEmptyList_WhenNoLiabilitiesForUser() {
        // When
        List<Liability> liabilities = liabilityRepository.findByUserIdOrderByCreatedAtDesc(999L);

        // Then
        assertThat(liabilities).isEmpty();
    }

    @Test
    void shouldReturnZeroCount_WhenNoLiabilitiesForUser() {
        // When
        long count = liabilityRepository.countByUserId(999L);

        // Then
        assertThat(count).isZero();
    }
}
