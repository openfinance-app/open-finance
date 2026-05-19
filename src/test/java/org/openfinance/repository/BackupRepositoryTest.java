package org.openfinance.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Backup;
import org.openfinance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for BackupRepository.
 *
 * <p>Uses @DataJpaTest which configures an in-memory H2 database and provides transactional test
 * execution with rollback.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>CRUD operations
 *   <li>User isolation queries
 *   <li>Status filtering
 *   <li>Backup type filtering
 *   <li>Authorization queries (findByIdAndUserId, existsByIdAndUserId)
 *   <li>Backup rotation queries (findOldAutomaticBackups, deleteOldBackups)
 *   <li>Most recent completed backup query
 *   <li>Count operations
 * </ul>
 *
 * <p>Requirement REQ-2.14.2: Data Backup and Restore - Users can create manual backups, schedule
 * automatic backups, and restore from previous backups.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
        })
@DisplayName("BackupRepository Integration Tests")
class BackupRepositoryTest {

    @Autowired private BackupRepository backupRepository;

    @Autowired private UserRepository userRepository;

    private User testUser1;
    private User testUser2;
    private Backup manualBackup1;
    private Backup automaticBackup1;
    private Backup automaticBackup2;
    private Backup failedBackup;

    @BeforeEach
    void setUp() {
        // Clear repositories before each test
        backupRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        testUser1 =
                User.builder()
                        .username("testuser1")
                        .email("user1@example.com")
                        .passwordHash("$2a$10$hashedPasswordExample123456789")
                        .masterPasswordSalt("base64EncodedSaltExample==")
                        .build();
        testUser1 = userRepository.save(testUser1);

        testUser2 =
                User.builder()
                        .username("testuser2")
                        .email("user2@example.com")
                        .passwordHash("$2a$10$hashedPasswordExample987654321")
                        .masterPasswordSalt("base64EncodedSaltExample22==")
                        .build();
        testUser2 = userRepository.save(testUser2);

        // Create test backups for user1
        manualBackup1 =
                Backup.builder()
                        .userId(testUser1.getId())
                        .filename("manual-backup-20260201-100000.ofbak")
                        .filePath("./backups/user1/manual-backup-20260201-100000.ofbak")
                        .fileSize(10485760L) // 10 MB
                        .checksum(
                                "abc123abc123abc123abc123abc123abc123abc123abc123abc123abc123abcd")
                        .status("COMPLETED")
                        .backupType("MANUAL")
                        .description("Monthly manual backup")
                        .createdAt(LocalDateTime.of(2026, 2, 1, 10, 0))
                        .build();

        automaticBackup1 =
                Backup.builder()
                        .userId(testUser1.getId())
                        .filename("auto-backup-20260203-020000.ofbak")
                        .filePath("./backups/user1/auto-backup-20260203-020000.ofbak")
                        .fileSize(10240000L)
                        .checksum(
                                "def456def456def456def456def456def456def456def456def456def456def4")
                        .status("COMPLETED")
                        .backupType("AUTOMATIC")
                        .createdAt(LocalDateTime.of(2026, 2, 3, 2, 0))
                        .build();

        automaticBackup2 =
                Backup.builder()
                        .userId(testUser1.getId())
                        .filename("auto-backup-20260210-020000.ofbak")
                        .filePath("./backups/user1/auto-backup-20260210-020000.ofbak")
                        .fileSize(10500000L)
                        .checksum(
                                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                        .status("COMPLETED")
                        .backupType("AUTOMATIC")
                        .createdAt(LocalDateTime.of(2026, 2, 10, 2, 0))
                        .build();

        failedBackup =
                Backup.builder()
                        .userId(testUser1.getId())
                        .filename("failed-backup-20260215-100000.ofbak")
                        .filePath("./backups/user1/failed-backup-20260215-100000.ofbak")
                        .fileSize(0L)
                        .checksum(
                                "0000000000000000000000000000000000000000000000000000000000000000")
                        .status("FAILED")
                        .backupType("MANUAL")
                        .errorMessage("Disk full")
                        .createdAt(LocalDateTime.of(2026, 2, 15, 10, 0))
                        .build();
    }

    // === CRUD Operation Tests ===

    @Test
    @DisplayName("Should save backup and generate ID")
    void shouldSaveBackupAndGenerateId() {
        // When
        Backup savedBackup = backupRepository.save(manualBackup1);

        // Then
        assertThat(savedBackup).isNotNull();
        assertThat(savedBackup.getId()).isNotNull();
        assertThat(savedBackup.getUserId()).isEqualTo(testUser1.getId());
        assertThat(savedBackup.getFilename()).isEqualTo("manual-backup-20260201-100000.ofbak");
        assertThat(savedBackup.getFilePath())
                .isEqualTo("./backups/user1/manual-backup-20260201-100000.ofbak");
        assertThat(savedBackup.getFileSize()).isEqualTo(10485760L);
        assertThat(savedBackup.getChecksum()).hasSize(64);
        assertThat(savedBackup.getStatus()).isEqualTo("COMPLETED");
        assertThat(savedBackup.getBackupType()).isEqualTo("MANUAL");
        assertThat(savedBackup.getDescription()).isEqualTo("Monthly manual backup");
        assertThat(savedBackup.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should find backup by ID")
    void shouldFindBackupById() {
        // Given
        Backup savedBackup = backupRepository.save(manualBackup1);

        // When
        Optional<Backup> found = backupRepository.findById(savedBackup.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedBackup.getId());
        assertThat(found.get().getFilename()).isEqualTo("manual-backup-20260201-100000.ofbak");
    }

    @Test
    @DisplayName("Should update backup")
    void shouldUpdateBackup() {
        // Given
        Backup savedBackup = backupRepository.save(manualBackup1);

        // When
        savedBackup.setDescription("Updated backup description");
        Backup updatedBackup = backupRepository.save(savedBackup);

        // Then
        assertThat(updatedBackup.getDescription()).isEqualTo("Updated backup description");
    }

    @Test
    @DisplayName("Should delete backup")
    void shouldDeleteBackup() {
        // Given
        Backup savedBackup = backupRepository.save(manualBackup1);
        Long backupId = savedBackup.getId();

        // When
        backupRepository.deleteById(backupId);

        // Then
        Optional<Backup> found = backupRepository.findById(backupId);
        assertThat(found).isEmpty();
    }

    // === User Isolation Tests ===

    @Test
    @DisplayName("Should find all backups for user ordered by creation date descending")
    void shouldFindByUserIdOrderByCreatedAtDesc() {
        // Given - Save all test backups
        backupRepository.save(manualBackup1);
        backupRepository.save(automaticBackup1);
        backupRepository.save(automaticBackup2);
        backupRepository.save(failedBackup);

        // When
        List<Backup> backups = backupRepository.findByUserIdOrderByCreatedAtDesc(testUser1.getId());

        // Then
        assertThat(backups).hasSize(4);
        // Should be ordered by createdAt DESC (most recent first)
        assertThat(backups.get(0).getFilename())
                .isEqualTo("failed-backup-20260215-100000.ofbak"); // Feb 15
        assertThat(backups.get(1).getFilename())
                .isEqualTo("auto-backup-20260210-020000.ofbak"); // Feb 10
        assertThat(backups.get(2).getFilename())
                .isEqualTo("auto-backup-20260203-020000.ofbak"); // Feb 3
        assertThat(backups.get(3).getFilename())
                .isEqualTo("manual-backup-20260201-100000.ofbak"); // Feb 1
    }

    @Test
    @DisplayName("Should return empty list when user has no backups")
    void shouldReturnEmptyListForUserWithNoBackups() {
        // Given - Save backup for user1 only
        backupRepository.save(manualBackup1);

        // When
        List<Backup> backups = backupRepository.findByUserIdOrderByCreatedAtDesc(testUser2.getId());

        // Then
        assertThat(backups).isEmpty();
    }

    @Test
    @DisplayName("Should isolate backups between different users")
    void shouldIsolateBackupsBetweenUsers() {
        // Given
        backupRepository.save(manualBackup1); // user1

        Backup user2Backup =
                Backup.builder()
                        .userId(testUser2.getId())
                        .filename("user2-backup-20260220.ofbak")
                        .filePath("./backups/user2/user2-backup-20260220.ofbak")
                        .fileSize(5000000L)
                        .checksum(
                                "def456def456def456def456def456def456def456def456def456def456def4")
                        .status("COMPLETED")
                        .backupType("MANUAL")
                        .createdAt(LocalDateTime.now())
                        .build();
        backupRepository.save(user2Backup);

        // When
        List<Backup> user1Backups =
                backupRepository.findByUserIdOrderByCreatedAtDesc(testUser1.getId());
        List<Backup> user2Backups =
                backupRepository.findByUserIdOrderByCreatedAtDesc(testUser2.getId());

        // Then
        assertThat(user1Backups).hasSize(1);
        assertThat(user1Backups.get(0).getFilename())
                .isEqualTo("manual-backup-20260201-100000.ofbak");

        assertThat(user2Backups).hasSize(1);
        assertThat(user2Backups.get(0).getFilename()).isEqualTo("user2-backup-20260220.ofbak");
    }

    // === Status Filtering Tests ===

    @Test
    @DisplayName("Should find backups by user ID and status")
    void shouldFindByUserIdAndStatus() {
        // Given
        backupRepository.save(manualBackup1); // COMPLETED
        backupRepository.save(automaticBackup1); // COMPLETED
        backupRepository.save(failedBackup); // FAILED

        // When
        List<Backup> completedBackups =
                backupRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                        testUser1.getId(), "COMPLETED");
        List<Backup> failedBackups =
                backupRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                        testUser1.getId(), "FAILED");

        // Then
        assertThat(completedBackups).hasSize(2);
        assertThat(failedBackups).hasSize(1);
        assertThat(failedBackups.get(0).getFilename())
                .isEqualTo("failed-backup-20260215-100000.ofbak");
    }

    @Test
    @DisplayName("Should return empty list when no backups match status")
    void shouldReturnEmptyListWhenNoBackupsMatchStatus() {
        // Given
        backupRepository.save(manualBackup1); // COMPLETED

        // When
        List<Backup> inProgressBackups =
                backupRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                        testUser1.getId(), "IN_PROGRESS");

        // Then
        assertThat(inProgressBackups).isEmpty();
    }

    // === Backup Type Filtering Tests ===

    @Test
    @DisplayName("Should find backups by user ID and backup type")
    void shouldFindByUserIdAndBackupType() {
        // Given
        backupRepository.save(manualBackup1); // MANUAL
        backupRepository.save(automaticBackup1); // AUTOMATIC
        backupRepository.save(automaticBackup2); // AUTOMATIC

        // When
        List<Backup> manualBackups =
                backupRepository.findByUserIdAndBackupTypeOrderByCreatedAtDesc(
                        testUser1.getId(), "MANUAL");
        List<Backup> automaticBackups =
                backupRepository.findByUserIdAndBackupTypeOrderByCreatedAtDesc(
                        testUser1.getId(), "AUTOMATIC");

        // Then
        assertThat(manualBackups).hasSize(1);
        assertThat(manualBackups.get(0).getFilename())
                .isEqualTo("manual-backup-20260201-100000.ofbak");

        assertThat(automaticBackups).hasSize(2);
        assertThat(automaticBackups)
                .extracting(Backup::getFilename)
                .containsExactlyInAnyOrder(
                        "auto-backup-20260203-020000.ofbak", "auto-backup-20260210-020000.ofbak");
    }

    // === Authorization Tests ===

    @Test
    @DisplayName("Should find backup by ID and user ID")
    void shouldFindByIdAndUserId() {
        // Given
        Backup savedBackup = backupRepository.save(manualBackup1);

        // When
        Optional<Backup> found =
                backupRepository.findByIdAndUserId(savedBackup.getId(), testUser1.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedBackup.getId());
    }

    @Test
    @DisplayName("Should not find backup when user ID does not match")
    void shouldNotFindByIdAndUserIdWhenUserIdMismatch() {
        // Given
        Backup savedBackup = backupRepository.save(manualBackup1);

        // When
        Optional<Backup> found =
                backupRepository.findByIdAndUserId(
                        savedBackup.getId(), testUser2.getId()); // Different user

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should check backup existence by ID and user ID")
    void shouldExistsByIdAndUserId() {
        // Given
        Backup savedBackup = backupRepository.save(manualBackup1);

        // When
        boolean exists =
                backupRepository.existsByIdAndUserId(savedBackup.getId(), testUser1.getId());

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should return false when checking existence with wrong user ID")
    void shouldReturnFalseWhenCheckingExistenceWithWrongUserId() {
        // Given
        Backup savedBackup = backupRepository.save(manualBackup1);

        // When
        boolean exists =
                backupRepository.existsByIdAndUserId(
                        savedBackup.getId(), testUser2.getId()); // Different user

        // Then
        assertThat(exists).isFalse();
    }

    // === Count Tests ===

    @Test
    @DisplayName("Should count backups by user ID")
    void shouldCountByUserId() {
        // Given
        backupRepository.save(manualBackup1);
        backupRepository.save(automaticBackup1);
        backupRepository.save(automaticBackup2);

        // When
        Long count = backupRepository.countByUserId(testUser1.getId());

        // Then
        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should return zero when counting for user with no backups")
    void shouldReturnZeroWhenCountingForUserWithNoBackups() {
        // When
        Long count = backupRepository.countByUserId(testUser2.getId());

        // Then
        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should count backups by user ID and backup type")
    void shouldCountByUserIdAndBackupType() {
        // Given
        backupRepository.save(manualBackup1); // MANUAL
        backupRepository.save(automaticBackup1); // AUTOMATIC
        backupRepository.save(automaticBackup2); // AUTOMATIC
        backupRepository.save(failedBackup); // MANUAL

        // When
        Long manualCount = backupRepository.countByUserIdAndBackupType(testUser1.getId(), "MANUAL");
        Long automaticCount =
                backupRepository.countByUserIdAndBackupType(testUser1.getId(), "AUTOMATIC");

        // Then
        assertThat(manualCount).isEqualTo(2L);
        assertThat(automaticCount).isEqualTo(2L);
    }

    // === Backup Rotation Tests ===

    @Test
    @DisplayName("Should find old automatic backups for rotation")
    void shouldFindOldAutomaticBackups() {
        // Given - Create 10 automatic backups
        for (int i = 1; i <= 10; i++) {
            Backup backup =
                    Backup.builder()
                            .userId(testUser1.getId())
                            .filename("auto-backup-" + i + ".ofbak")
                            .filePath("./backups/user1/auto-backup-" + i + ".ofbak")
                            .fileSize(1000000L * i)
                            .checksum(
                                    String.format(
                                            "abcdef%058d", i)) // 6 hex chars + 58 digits = 64 chars
                            .status("COMPLETED")
                            .backupType("AUTOMATIC")
                            .createdAt(LocalDateTime.now().minusDays(i))
                            .build();
            backupRepository.save(backup);
        }

        // When - Find backups older than 7 days (keep 7 most recent)
        // Use truncatedTo to avoid millisecond differences in comparison
        LocalDateTime cutoffDate =
                LocalDateTime.now().minusDays(7).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        List<Backup> oldBackups =
                backupRepository.findOldAutomaticBackups(
                        testUser1.getId(), cutoffDate, "AUTOMATIC");

        // Then - Should find backups 8, 9, 10 days old (7-day backup is exactly at cutoff, so may
        // or may not be included)
        assertThat(oldBackups).hasSizeGreaterThanOrEqualTo(3);
        assertThat(oldBackups)
                .extracting(Backup::getFilename)
                .contains("auto-backup-8.ofbak", "auto-backup-9.ofbak", "auto-backup-10.ofbak");
    }

    @Test
    @DisplayName("Should return empty list when all backups are within retention period")
    void shouldReturnEmptyListWhenAllBackupsWithinRetention() {
        // Given - 3 automatic backups all created within the last 5 days (well within any retention
        // window)
        Backup recent1 =
                Backup.builder()
                        .userId(testUser1.getId())
                        .filename("recent-auto-backup-1.ofbak")
                        .filePath("./backups/user1/recent-auto-backup-1.ofbak")
                        .fileSize(1000000L)
                        .checksum(
                                "aaa111aaa111aaa111aaa111aaa111aaa111aaa111aaa111aaa111aaa111aaa1")
                        .status("COMPLETED")
                        .backupType("AUTOMATIC")
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .build();
        Backup recent2 =
                Backup.builder()
                        .userId(testUser1.getId())
                        .filename("recent-auto-backup-2.ofbak")
                        .filePath("./backups/user1/recent-auto-backup-2.ofbak")
                        .fileSize(1000000L)
                        .checksum(
                                "bbb222bbb222bbb222bbb222bbb222bbb222bbb222bbb222bbb222bbb222bbb2")
                        .status("COMPLETED")
                        .backupType("AUTOMATIC")
                        .createdAt(LocalDateTime.now().minusDays(3))
                        .build();
        Backup recent3 =
                Backup.builder()
                        .userId(testUser1.getId())
                        .filename("recent-auto-backup-3.ofbak")
                        .filePath("./backups/user1/recent-auto-backup-3.ofbak")
                        .fileSize(1000000L)
                        .checksum(
                                "ccc333ccc333ccc333ccc333ccc333ccc333ccc333ccc333ccc333ccc333ccc3")
                        .status("COMPLETED")
                        .backupType("AUTOMATIC")
                        .createdAt(LocalDateTime.now().minusDays(5))
                        .build();
        backupRepository.save(recent1);
        backupRepository.save(recent2);
        backupRepository.save(recent3);

        // When - Cutoff date is 30 days ago (all backups are newer, so none should be returned)
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<Backup> oldBackups =
                backupRepository.findOldAutomaticBackups(
                        testUser1.getId(), cutoffDate, "AUTOMATIC");

        // Then
        assertThat(oldBackups).isEmpty();
    }

    @Test
    @DisplayName("Should not include manual backups in rotation query")
    void shouldNotIncludeManualBackupsInRotation() {
        // Given - Create backups with dynamic dates relative to today
        backupRepository.save(manualBackup1); // MANUAL (should not be included)

        // Create old automatic backup (23 days old) that should be found
        Backup oldAutomaticBackup =
                Backup.builder()
                        .userId(testUser1.getId())
                        .filename("old-auto-backup.ofbak")
                        .filePath("./backups/user1/old-auto-backup.ofbak")
                        .fileSize(10000000L)
                        .checksum(
                                "fedcba98fedcba98fedcba98fedcba98fedcba98fedcba98fedcba98fedcba98")
                        .status("COMPLETED")
                        .backupType("AUTOMATIC")
                        .createdAt(LocalDateTime.now().minusDays(23))
                        .build();
        backupRepository.save(oldAutomaticBackup);

        // Create recent automatic backup (16 days old) that should NOT be found
        Backup recentAutomaticBackup =
                Backup.builder()
                        .userId(testUser1.getId())
                        .filename("recent-auto-backup.ofbak")
                        .filePath("./backups/user1/recent-auto-backup.ofbak")
                        .fileSize(10500000L)
                        .checksum(
                                "0123456701234567012345670123456701234567012345670123456701234567")
                        .status("COMPLETED")
                        .backupType("AUTOMATIC")
                        .createdAt(LocalDateTime.now().minusDays(16))
                        .build();
        backupRepository.save(recentAutomaticBackup);

        // When - Find backups older than 20 days
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(20);
        List<Backup> oldBackups =
                backupRepository.findOldAutomaticBackups(
                        testUser1.getId(), cutoffDate, "AUTOMATIC");

        // Then - Should only find the oldest automatic backup (23 days old, not the manual one or
        // recent automatic)
        assertThat(oldBackups).hasSize(1);
        assertThat(oldBackups.get(0).getBackupType()).isEqualTo("AUTOMATIC");
        assertThat(oldBackups.get(0).getFilename()).isEqualTo("old-auto-backup.ofbak");
    }

    @Test
    @DisplayName("Should delete old backups by date and type")
    void shouldDeleteOldBackups() {
        // Given
        backupRepository.save(automaticBackup1); // Feb 3
        backupRepository.save(automaticBackup2); // Feb 10
        backupRepository.save(manualBackup1); // Feb 1 (MANUAL - should not be deleted)

        // When - Delete automatic backups before Feb 5
        LocalDateTime cutoffDate = LocalDateTime.of(2026, 2, 5, 0, 0);
        int deletedCount =
                backupRepository.deleteOldBackups(testUser1.getId(), cutoffDate, "AUTOMATIC");
        backupRepository.flush(); // Force immediate execution

        // Then
        assertThat(deletedCount).isEqualTo(1); // Only automaticBackup1 deleted
        List<Backup> remaining =
                backupRepository.findByUserIdOrderByCreatedAtDesc(testUser1.getId());
        assertThat(remaining).hasSize(2);
        assertThat(remaining)
                .extracting(Backup::getFilename)
                .containsExactlyInAnyOrder(
                        "auto-backup-20260210-020000.ofbak", "manual-backup-20260201-100000.ofbak");
    }

    // === Most Recent Backup Tests ===

    @Test
    @DisplayName("Should find most recent completed backup")
    void shouldFindMostRecentCompletedBackup() {
        // Given
        backupRepository.save(manualBackup1); // Feb 1, COMPLETED
        backupRepository.save(automaticBackup1); // Feb 3, COMPLETED
        backupRepository.save(automaticBackup2); // Feb 10, COMPLETED (most recent)
        backupRepository.save(failedBackup); // Feb 15, FAILED (should be ignored)

        // When
        Optional<Backup> mostRecent =
                backupRepository.findMostRecentCompletedBackup(testUser1.getId(), "COMPLETED");

        // Then
        assertThat(mostRecent).isPresent();
        assertThat(mostRecent.get().getFilename()).isEqualTo("auto-backup-20260210-020000.ofbak");
        assertThat(mostRecent.get().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Should return empty when no completed backups exist")
    void shouldReturnEmptyWhenNoCompletedBackups() {
        // Given - Only failed backup
        backupRepository.save(failedBackup);

        // When
        Optional<Backup> mostRecent =
                backupRepository.findMostRecentCompletedBackup(testUser1.getId(), "COMPLETED");

        // Then
        assertThat(mostRecent).isEmpty();
    }

    @Test
    @DisplayName("Should return empty for user with no backups")
    void shouldReturnEmptyForUserWithNoBackups() {
        // When
        Optional<Backup> mostRecent =
                backupRepository.findMostRecentCompletedBackup(testUser2.getId(), "COMPLETED");

        // Then
        assertThat(mostRecent).isEmpty();
    }

    // === Helper Method Tests ===

    @Test
    @DisplayName("Should correctly identify completed backups")
    void shouldCorrectlyIdentifyCompletedBackups() {
        // Given
        Backup savedManual = backupRepository.save(manualBackup1);
        Backup savedFailed = backupRepository.save(failedBackup);

        // When
        Backup refreshedManual = backupRepository.findById(savedManual.getId()).get();
        Backup refreshedFailed = backupRepository.findById(savedFailed.getId()).get();

        // Then
        assertThat(refreshedManual.isCompleted()).isTrue();
        assertThat(refreshedFailed.isCompleted()).isFalse();
        assertThat(refreshedFailed.isFailed()).isTrue();
    }

    @Test
    @DisplayName("Should correctly identify automatic backups")
    void shouldCorrectlyIdentifyAutomaticBackups() {
        // Given
        Backup savedManual = backupRepository.save(manualBackup1);
        Backup savedAutomatic = backupRepository.save(automaticBackup1);

        // When
        Backup refreshedManual = backupRepository.findById(savedManual.getId()).get();
        Backup refreshedAutomatic = backupRepository.findById(savedAutomatic.getId()).get();

        // Then
        assertThat(refreshedManual.isAutomatic()).isFalse();
        assertThat(refreshedAutomatic.isAutomatic()).isTrue();
    }

    @Test
    @DisplayName("Should format file size correctly")
    void shouldFormatFileSizeCorrectly() {
        // Given
        Backup savedBackup = backupRepository.save(manualBackup1); // 10 MB

        // When
        Backup refreshed = backupRepository.findById(savedBackup.getId()).get();

        // Then - Accept both "10.00 MB" (English) and "10,00 MB" (European locale)
        assertThat(refreshed.getFormattedFileSize()).matches("10[.,]00 MB");
    }
}
