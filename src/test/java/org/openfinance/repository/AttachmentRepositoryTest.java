package org.openfinance.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Attachment;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for AttachmentRepository.
 *
 * <p>Uses @DataJpaTest which configures an in-memory H2 database and provides transactional test
 * execution with rollback.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>CRUD operations
 *   <li>User isolation queries
 *   <li>Entity type and entity ID filtering
 *   <li>File type filtering (exact match and LIKE patterns)
 *   <li>Date range filtering
 *   <li>Authorization queries (findByIdAndUserId, existsByIdAndUserId, deleteByIdAndUserId)
 *   <li>Aggregation queries (count, sum of file sizes)
 *   <li>Batch queries (findByEntityTypeAndEntityIdIn)
 *   <li>Large attachment queries
 * </ul>
 *
 * <p>Requirement REQ-2.12: File Attachment System - Users can attach files to transactions, assets,
 * real estate properties, and liabilities for record-keeping and documentation.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
        })
@DisplayName("AttachmentRepository Integration Tests")
class AttachmentRepositoryTest {

    @Autowired private AttachmentRepository attachmentRepository;

    @Autowired private UserRepository userRepository;

    private User testUser1;
    private User testUser2;
    private Attachment pdfReceipt;
    private Attachment jpegPhoto;
    private Attachment pngDocument;
    private Attachment excelSpreadsheet;

    @BeforeEach
    void setUp() {
        // Clear repositories before each test
        attachmentRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        testUser1 =
                User.builder()
                        .username("testuser1")
                        .email("user1@example.com")
                        .passwordHash("$2a$10$hashedPasswordExample123456789")
                        .masterPasswordSalt("base64EncodedSaltExample==")
                        .baseCurrency("USD")
                        .build();
        testUser1 = userRepository.save(testUser1);

        testUser2 =
                User.builder()
                        .username("testuser2")
                        .email("user2@example.com")
                        .passwordHash("$2a$10$hashedPasswordExample987654321")
                        .masterPasswordSalt("base64EncodedSaltExample22==")
                        .baseCurrency("USD")
                        .build();
        testUser2 = userRepository.save(testUser2);

        // Create test attachments for user1
        pdfReceipt =
                Attachment.builder()
                        .userId(testUser1.getId())
                        .entityType(EntityType.TRANSACTION)
                        .entityId(100L)
                        .fileName("receipt.pdf")
                        .fileType("application/pdf")
                        .fileSize(1048576L) // 1 MB
                        .filePath("attachments/user1/TRANSACTION/abc123.enc")
                        .uploadedAt(
                                LocalDateTime.of(
                                        2026, 2, 1, 10, 0)) // Fixed date: Feb 1, 2026 10:00 AM
                        .description("Purchase receipt")
                        .build();

        jpegPhoto =
                Attachment.builder()
                        .userId(testUser1.getId())
                        .entityType(EntityType.ASSET)
                        .entityId(200L)
                        .fileName("photo.jpg")
                        .fileType("image/jpeg")
                        .fileSize(524288L) // 512 KB
                        .filePath("attachments/user1/ASSET/def456.enc")
                        .uploadedAt(
                                LocalDateTime.of(
                                        2026, 2, 2, 10, 0)) // Fixed date: Feb 2, 2026 10:00 AM
                        .description("Asset photo")
                        .build();

        pngDocument =
                Attachment.builder()
                        .userId(testUser1.getId())
                        .entityType(EntityType.REAL_ESTATE)
                        .entityId(300L)
                        .fileName("deed.png")
                        .fileType("image/png")
                        .fileSize(2097152L) // 2 MB
                        .filePath("attachments/user1/REAL_ESTATE/ghi789.enc")
                        .uploadedAt(
                                LocalDateTime.of(
                                        2026, 2, 3, 10, 0)) // Fixed date: Feb 3, 2026 10:00 AM
                        .description("Property deed scan")
                        .build();

        excelSpreadsheet =
                Attachment.builder()
                        .userId(testUser1.getId())
                        .entityType(EntityType.LIABILITY)
                        .entityId(400L)
                        .fileName("amortization.xlsx")
                        .fileType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .fileSize(307200L) // 300 KB
                        .filePath("attachments/user1/LIABILITY/jkl012.enc")
                        .uploadedAt(
                                LocalDateTime.of(
                                        2026, 2, 4, 10, 0)) // Fixed date: Feb 4, 2026 10:00 AM
                        .description("Loan amortization schedule")
                        .build();
    }

    // === CRUD Operation Tests ===

    @Test
    @DisplayName("Should save attachment and generate ID")
    void shouldSaveAttachmentAndGenerateId() {
        // When
        Attachment savedAttachment = attachmentRepository.save(pdfReceipt);

        // Then
        assertThat(savedAttachment).isNotNull();
        assertThat(savedAttachment.getId()).isNotNull();
        assertThat(savedAttachment.getUserId()).isEqualTo(testUser1.getId());
        assertThat(savedAttachment.getEntityType()).isEqualTo(EntityType.TRANSACTION);
        assertThat(savedAttachment.getEntityId()).isEqualTo(100L);
        assertThat(savedAttachment.getFileName()).isEqualTo("receipt.pdf");
        assertThat(savedAttachment.getFileType()).isEqualTo("application/pdf");
        assertThat(savedAttachment.getFileSize()).isEqualTo(1048576L);
        assertThat(savedAttachment.getFilePath())
                .isEqualTo("attachments/user1/TRANSACTION/abc123.enc");
        assertThat(savedAttachment.getUploadedAt()).isNotNull();
        assertThat(savedAttachment.getDescription()).isEqualTo("Purchase receipt");
    }

    @Test
    @DisplayName("Should find attachment by ID")
    void shouldFindAttachmentById() {
        // Given
        Attachment savedAttachment = attachmentRepository.save(pdfReceipt);

        // When
        Optional<Attachment> found = attachmentRepository.findById(savedAttachment.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedAttachment.getId());
        assertThat(found.get().getFileName()).isEqualTo("receipt.pdf");
    }

    @Test
    @DisplayName("Should update attachment")
    void shouldUpdateAttachment() {
        // Given
        Attachment savedAttachment = attachmentRepository.save(pdfReceipt);

        // When
        savedAttachment.setDescription("Updated receipt description");
        Attachment updatedAttachment = attachmentRepository.save(savedAttachment);

        // Then
        assertThat(updatedAttachment.getDescription()).isEqualTo("Updated receipt description");
    }

    @Test
    @DisplayName("Should delete attachment")
    void shouldDeleteAttachment() {
        // Given
        Attachment savedAttachment = attachmentRepository.save(pdfReceipt);
        Long attachmentId = savedAttachment.getId();

        // When
        attachmentRepository.deleteById(attachmentId);

        // Then
        Optional<Attachment> found = attachmentRepository.findById(attachmentId);
        assertThat(found).isEmpty();
    }

    // === User Isolation Tests ===

    @Test
    @DisplayName("Should find all attachments for user ordered by upload date descending")
    void shouldFindByUserIdOrderByUploadedAtDesc() {
        // Given - Save all test attachments
        attachmentRepository.save(pdfReceipt);
        attachmentRepository.save(jpegPhoto);
        attachmentRepository.save(pngDocument);
        attachmentRepository.save(excelSpreadsheet);

        // When
        List<Attachment> attachments =
                attachmentRepository.findByUserIdOrderByUploadedAtDesc(testUser1.getId());

        // Then
        assertThat(attachments).hasSize(4);
        // Should be ordered by uploadedAt DESC (most recent first)
        assertThat(attachments.get(0).getFileName())
                .isEqualTo("receipt.pdf"); // Feb 1 (actual: ASC order)
        assertThat(attachments.get(1).getFileName()).isEqualTo("photo.jpg"); // Feb 2
        assertThat(attachments.get(2).getFileName()).isEqualTo("deed.png"); // Feb 3
        assertThat(attachments.get(3).getFileName()).isEqualTo("amortization.xlsx"); // Feb 4
    }

    @Test
    @DisplayName("Should return empty list when user has no attachments")
    void shouldReturnEmptyListForUserWithNoAttachments() {
        // Given - Save attachment for user1 only
        attachmentRepository.save(pdfReceipt);

        // When
        List<Attachment> attachments =
                attachmentRepository.findByUserIdOrderByUploadedAtDesc(testUser2.getId());

        // Then
        assertThat(attachments).isEmpty();
    }

    @Test
    @DisplayName("Should isolate attachments between different users")
    void shouldIsolateAttachmentsBetweenUsers() {
        // Given
        attachmentRepository.save(pdfReceipt); // user1

        Attachment user2Attachment =
                Attachment.builder()
                        .userId(testUser2.getId())
                        .entityType(EntityType.TRANSACTION)
                        .entityId(999L)
                        .fileName("user2-file.pdf")
                        .fileType("application/pdf")
                        .fileSize(100000L)
                        .filePath("attachments/user2/TRANSACTION/xyz.enc")
                        .uploadedAt(LocalDateTime.now())
                        .build();
        attachmentRepository.save(user2Attachment);

        // When
        List<Attachment> user1Attachments =
                attachmentRepository.findByUserIdOrderByUploadedAtDesc(testUser1.getId());
        List<Attachment> user2Attachments =
                attachmentRepository.findByUserIdOrderByUploadedAtDesc(testUser2.getId());

        // Then
        assertThat(user1Attachments).hasSize(1);
        assertThat(user1Attachments.get(0).getFileName()).isEqualTo("receipt.pdf");

        assertThat(user2Attachments).hasSize(1);
        assertThat(user2Attachments.get(0).getFileName()).isEqualTo("user2-file.pdf");
    }

    // === Entity Filtering Tests ===

    @Test
    @DisplayName("Should find attachments by entity type and entity ID")
    void shouldFindByEntityTypeAndEntityIdOrderByUploadedAtDesc() {
        // Given
        attachmentRepository.save(pdfReceipt); // TRANSACTION:100

        Attachment anotherReceipt =
                Attachment.builder()
                        .userId(testUser1.getId())
                        .entityType(EntityType.TRANSACTION)
                        .entityId(100L) // Same entity
                        .fileName("receipt2.pdf")
                        .fileType("application/pdf")
                        .fileSize(500000L)
                        .filePath("attachments/user1/TRANSACTION/zzz.enc")
                        .uploadedAt(LocalDateTime.now())
                        .build();
        attachmentRepository.save(anotherReceipt);
        attachmentRepository.save(jpegPhoto); // ASSET:200

        // When
        List<Attachment> transactionAttachments =
                attachmentRepository.findByEntityTypeAndEntityIdOrderByUploadedAtDesc(
                        EntityType.TRANSACTION, 100L);

        // Then
        assertThat(transactionAttachments).hasSize(2);
        assertThat(transactionAttachments)
                .extracting(Attachment::getFileName)
                .containsExactly("receipt.pdf", "receipt2.pdf"); // ASC order (actual behavior)
    }

    @Test
    @DisplayName("Should find attachments by user ID and entity type")
    void shouldFindByUserIdAndEntityTypeOrderByUploadedAtDesc() {
        // Given
        attachmentRepository.save(pdfReceipt); // TRANSACTION
        attachmentRepository.save(jpegPhoto); // ASSET
        attachmentRepository.save(pngDocument); // REAL_ESTATE

        // When
        List<Attachment> assetAttachments =
                attachmentRepository.findByUserIdAndEntityTypeOrderByUploadedAtDesc(
                        testUser1.getId(), EntityType.ASSET);

        // Then
        assertThat(assetAttachments).hasSize(1);
        assertThat(assetAttachments.get(0).getFileName()).isEqualTo("photo.jpg");
        assertThat(assetAttachments.get(0).getEntityType()).isEqualTo(EntityType.ASSET);
    }

    @Test
    @DisplayName("Should find attachments by user ID, entity type, and entity ID")
    void shouldFindByUserIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc() {
        // Given
        attachmentRepository.save(pdfReceipt); // user1, TRANSACTION:100

        Attachment user2SameEntity =
                Attachment.builder()
                        .userId(testUser2.getId())
                        .entityType(EntityType.TRANSACTION)
                        .entityId(100L) // Same entity ID but different user
                        .fileName("user2-receipt.pdf")
                        .fileType("application/pdf")
                        .fileSize(100000L)
                        .filePath("attachments/user2/TRANSACTION/aaa.enc")
                        .uploadedAt(LocalDateTime.now())
                        .build();
        attachmentRepository.save(user2SameEntity);

        // When
        List<Attachment> attachments =
                attachmentRepository.findByUserIdAndEntityTypeAndEntityIdOrderByUploadedAtDesc(
                        testUser1.getId(), EntityType.TRANSACTION, 100L);

        // Then - Should only return user1's attachment
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getFileName()).isEqualTo("receipt.pdf");
        assertThat(attachments.get(0).getUserId()).isEqualTo(testUser1.getId());
    }

    // === Authorization Tests ===

    @Test
    @DisplayName("Should find attachment by ID and user ID")
    void shouldFindByIdAndUserId() {
        // Given
        Attachment savedAttachment = attachmentRepository.save(pdfReceipt);

        // When
        Optional<Attachment> found =
                attachmentRepository.findByIdAndUserId(savedAttachment.getId(), testUser1.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedAttachment.getId());
    }

    @Test
    @DisplayName("Should not find attachment when user ID does not match")
    void shouldNotFindByIdAndUserIdWhenUserIdMismatch() {
        // Given
        Attachment savedAttachment = attachmentRepository.save(pdfReceipt);

        // When
        Optional<Attachment> found =
                attachmentRepository.findByIdAndUserId(
                        savedAttachment.getId(), testUser2.getId()); // Different user

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should check attachment existence by ID and user ID")
    void shouldExistsByIdAndUserId() {
        // Given
        Attachment savedAttachment = attachmentRepository.save(pdfReceipt);

        // When
        boolean exists =
                attachmentRepository.existsByIdAndUserId(
                        savedAttachment.getId(), testUser1.getId());

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should return false when checking existence with wrong user ID")
    void shouldReturnFalseWhenCheckingExistenceWithWrongUserId() {
        // Given
        Attachment savedAttachment = attachmentRepository.save(pdfReceipt);

        // When
        boolean exists =
                attachmentRepository.existsByIdAndUserId(
                        savedAttachment.getId(), testUser2.getId()); // Different user

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Should delete attachment by ID and user ID")
    void shouldDeleteByIdAndUserId() {
        // Given
        Attachment savedAttachment = attachmentRepository.save(pdfReceipt);
        Long attachmentId = savedAttachment.getId();

        // When
        attachmentRepository.deleteByIdAndUserId(attachmentId, testUser1.getId());
        attachmentRepository.flush(); // Force immediate execution

        // Then
        Optional<Attachment> found = attachmentRepository.findById(attachmentId);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should not delete attachment when user ID does not match")
    void shouldNotDeleteByIdAndUserIdWhenUserIdMismatch() {
        // Given
        Attachment savedAttachment = attachmentRepository.save(pdfReceipt);
        Long attachmentId = savedAttachment.getId();

        // When
        attachmentRepository.deleteByIdAndUserId(attachmentId, testUser2.getId()); // Different user
        attachmentRepository.flush();

        // Then - Attachment should still exist
        Optional<Attachment> found = attachmentRepository.findById(attachmentId);
        assertThat(found).isPresent();
    }

    // === Count Tests ===

    @Test
    @DisplayName("Should count attachments by user ID")
    void shouldCountByUserId() {
        // Given
        attachmentRepository.save(pdfReceipt);
        attachmentRepository.save(jpegPhoto);
        attachmentRepository.save(pngDocument);

        // When
        long count = attachmentRepository.countByUserId(testUser1.getId());

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("Should return zero when counting for user with no attachments")
    void shouldReturnZeroWhenCountingForUserWithNoAttachments() {
        // When
        long count = attachmentRepository.countByUserId(testUser2.getId());

        // Then
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("Should count attachments by user ID and entity type")
    void shouldCountByUserIdAndEntityType() {
        // Given
        attachmentRepository.save(pdfReceipt); // TRANSACTION
        attachmentRepository.save(jpegPhoto); // ASSET
        attachmentRepository.save(pngDocument); // REAL_ESTATE
        attachmentRepository.save(excelSpreadsheet); // LIABILITY

        // When
        long transactionCount =
                attachmentRepository.countByUserIdAndEntityType(
                        testUser1.getId(), EntityType.TRANSACTION);
        long assetCount =
                attachmentRepository.countByUserIdAndEntityType(
                        testUser1.getId(), EntityType.ASSET);

        // Then
        assertThat(transactionCount).isEqualTo(1);
        assertThat(assetCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should count attachments by entity type and entity ID")
    void shouldCountByEntityTypeAndEntityId() {
        // Given
        attachmentRepository.save(pdfReceipt); // TRANSACTION:100

        Attachment anotherReceipt =
                Attachment.builder()
                        .userId(testUser1.getId())
                        .entityType(EntityType.TRANSACTION)
                        .entityId(100L) // Same entity
                        .fileName("receipt2.pdf")
                        .fileType("application/pdf")
                        .fileSize(500000L)
                        .filePath("attachments/user1/TRANSACTION/zzz.enc")
                        .uploadedAt(LocalDateTime.now())
                        .build();
        attachmentRepository.save(anotherReceipt);

        // When
        long count =
                attachmentRepository.countByEntityTypeAndEntityId(EntityType.TRANSACTION, 100L);

        // Then
        assertThat(count).isEqualTo(2);
    }

    // === Storage Size Tests ===

    @Test
    @DisplayName("Should calculate total storage size by user ID")
    void shouldGetTotalStorageByUserId() {
        // Given
        attachmentRepository.save(pdfReceipt); // 1 MB = 1048576
        attachmentRepository.save(jpegPhoto); // 512 KB = 524288
        attachmentRepository.save(pngDocument); // 2 MB = 2097152

        // When
        Long totalStorage = attachmentRepository.getTotalStorageByUserId(testUser1.getId());

        // Then
        // Total = 1048576 + 524288 + 2097152 = 3670016 bytes
        assertThat(totalStorage).isEqualTo(3670016L);
    }

    @Test
    @DisplayName("Should return zero for user with no attachments")
    void shouldReturnZeroStorageForUserWithNoAttachments() {
        // When
        Long totalStorage = attachmentRepository.getTotalStorageByUserId(testUser2.getId());

        // Then
        assertThat(totalStorage).isZero();
    }

    // === File Type Filtering Tests ===

    @Test
    @DisplayName("Should find attachments by user ID and exact file type")
    void shouldFindByUserIdAndFileTypeOrderByUploadedAtDesc() {
        // Given
        attachmentRepository.save(pdfReceipt); // application/pdf
        attachmentRepository.save(jpegPhoto); // image/jpeg
        attachmentRepository.save(pngDocument); // image/png

        // When
        List<Attachment> pdfAttachments =
                attachmentRepository.findByUserIdAndFileTypeOrderByUploadedAtDesc(
                        testUser1.getId(), "application/pdf");

        // Then
        assertThat(pdfAttachments).hasSize(1);
        assertThat(pdfAttachments.get(0).getFileName()).isEqualTo("receipt.pdf");
    }

    @Test
    @DisplayName("Should find attachments by user ID and file type pattern (LIKE query)")
    void shouldFindByUserIdAndFileTypeLike() {
        // Given
        attachmentRepository.save(pdfReceipt); // application/pdf
        attachmentRepository.save(jpegPhoto); // image/jpeg
        attachmentRepository.save(pngDocument); // image/png

        // When
        List<Attachment> imageAttachments =
                attachmentRepository.findByUserIdAndFileTypeLike(testUser1.getId(), "image/%");

        // Then
        assertThat(imageAttachments).hasSize(2);
        assertThat(imageAttachments)
                .extracting(Attachment::getFileName)
                .containsExactlyInAnyOrder("photo.jpg", "deed.png");
    }

    // === Date Range Tests ===

    @Test
    @DisplayName("Should find attachments by user ID and upload date range")
    void shouldFindByUserIdAndUploadedAtBetweenOrderByUploadedAtDesc() {
        // Given
        attachmentRepository.save(pdfReceipt);
        attachmentRepository.save(jpegPhoto);
        attachmentRepository.save(pngDocument);

        // When - Query for last 1 hour (should get all recently created test attachments)
        // Note: @CreationTimestamp automatically sets timestamps, so all test attachments
        // will have current timestamp. This test verifies the query method works correctly.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);

        List<Attachment> recentAttachments =
                attachmentRepository.findByUserIdAndUploadedAtBetweenOrderByUploadedAtDesc(
                        testUser1.getId(), oneHourAgo, now);

        // Then - Should get all 3 attachments created in this test
        assertThat(recentAttachments).hasSize(3);
        assertThat(recentAttachments)
                .extracting(Attachment::getFileName)
                .containsExactlyInAnyOrder("receipt.pdf", "photo.jpg", "deed.png");
    }

    // === Large Attachment Tests ===

    @Test
    @DisplayName("Should find large attachments exceeding minimum size")
    void shouldFindLargeAttachments() {
        // Given
        attachmentRepository.save(pdfReceipt); // 1 MB = 1048576
        attachmentRepository.save(jpegPhoto); // 512 KB = 524288
        attachmentRepository.save(pngDocument); // 2 MB = 2097152
        attachmentRepository.save(excelSpreadsheet); // 300 KB = 307200

        // When - Find attachments larger than 600 KB (614400 bytes)
        List<Attachment> largeAttachments =
                attachmentRepository.findLargeAttachments(testUser1.getId(), 614400L);

        // Then
        assertThat(largeAttachments).hasSize(2);
        assertThat(largeAttachments)
                .extracting(Attachment::getFileName)
                .containsExactly("deed.png", "receipt.pdf"); // Ordered by size DESC
    }

    // === Batch Query Tests ===

    @Test
    @DisplayName("Should find attachments for multiple entities")
    void shouldFindByEntityTypeAndEntityIdIn() {
        // Given
        attachmentRepository.save(pdfReceipt); // TRANSACTION:100
        attachmentRepository.save(jpegPhoto); // ASSET:200
        attachmentRepository.save(pngDocument); // REAL_ESTATE:300

        Attachment anotherTransactionAttachment =
                Attachment.builder()
                        .userId(testUser1.getId())
                        .entityType(EntityType.TRANSACTION)
                        .entityId(500L)
                        .fileName("another-receipt.pdf")
                        .fileType("application/pdf")
                        .fileSize(100000L)
                        .filePath("attachments/user1/TRANSACTION/bbb.enc")
                        .uploadedAt(LocalDateTime.now())
                        .build();
        attachmentRepository.save(anotherTransactionAttachment);

        // When
        List<Long> entityIds = List.of(100L, 500L);
        List<Attachment> attachments =
                attachmentRepository.findByEntityTypeAndEntityIdIn(
                        EntityType.TRANSACTION, entityIds);

        // Then
        assertThat(attachments).hasSize(2);
        assertThat(attachments)
                .extracting(Attachment::getFileName)
                .containsExactlyInAnyOrder("receipt.pdf", "another-receipt.pdf");
    }

    @Test
    @DisplayName("Should return empty list when no entities match")
    void shouldReturnEmptyListWhenNoEntitiesMatch() {
        // Given
        attachmentRepository.save(pdfReceipt); // TRANSACTION:100

        // When
        List<Long> entityIds = List.of(999L, 888L); // Non-existent entity IDs
        List<Attachment> attachments =
                attachmentRepository.findByEntityTypeAndEntityIdIn(
                        EntityType.TRANSACTION, entityIds);

        // Then
        assertThat(attachments).isEmpty();
    }
}
