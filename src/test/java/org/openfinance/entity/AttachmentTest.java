package org.openfinance.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Attachment entity.
 *
 * <p>Tests entity field validations, helper methods for file size formatting, file type detection,
 * and extension extraction.
 *
 * <p>Requirements: REQ-2.12 - File Attachment System - Users can attach files to transactions,
 * assets, real estate properties, and liabilities.
 */
@DisplayName("Attachment Entity Tests")
class AttachmentTest {

    // ========== FORMATTED FILE SIZE TESTS ==========

    @Nested
    @DisplayName("getFormattedFileSize() Tests")
    class FormattedFileSizeTests {

        @Test
        @DisplayName("Should format bytes correctly when size is less than 1KB")
        void shouldFormatBytesCorrectly() {
            // Arrange
            Attachment attachment = Attachment.builder().fileSize(512L).build();

            // Act
            String formattedSize = attachment.getFormattedFileSize();

            // Assert
            assertThat(formattedSize).isEqualTo("512 bytes");
        }

        @Test
        @DisplayName("Should format kilobytes correctly when size is less than 1MB")
        void shouldFormatKilobytesCorrectly() {
            // Arrange - 2.5 KB
            Attachment attachment = Attachment.builder().fileSize(2560L).build();

            // Act
            String formattedSize = attachment.getFormattedFileSize();

            // Assert - Use regex to handle locale-specific decimal separator (. or ,)
            assertThat(formattedSize).matches("2[.,]50 KB");
        }

        @Test
        @DisplayName("Should format megabytes correctly when size is less than 1GB")
        void shouldFormatMegabytesCorrectly() {
            // Arrange - 5.2 MB
            Attachment attachment = Attachment.builder().fileSize(5452595L).build();

            // Act
            String formattedSize = attachment.getFormattedFileSize();

            // Assert - Use regex to handle locale-specific decimal separator (. or ,)
            assertThat(formattedSize).matches("5[.,]20 MB");
        }

        @Test
        @DisplayName("Should format gigabytes correctly when size is 1GB or more")
        void shouldFormatGigabytesCorrectly() {
            // Arrange - 1.25 GB
            Attachment attachment = Attachment.builder().fileSize(1342177280L).build();

            // Act
            String formattedSize = attachment.getFormattedFileSize();

            // Assert - Use regex to handle locale-specific decimal separator (. or ,)
            assertThat(formattedSize).matches("1[.,]25 GB");
        }

        @Test
        @DisplayName("Should return 'Unknown' when file size is null")
        void shouldReturnUnknownWhenFileSizeIsNull() {
            // Arrange
            Attachment attachment = Attachment.builder().fileSize(null).build();

            // Act
            String formattedSize = attachment.getFormattedFileSize();

            // Assert
            assertThat(formattedSize).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("Should handle exactly 1 byte")
        void shouldHandleOneByteCorrectly() {
            // Arrange
            Attachment attachment = Attachment.builder().fileSize(1L).build();

            // Act
            String formattedSize = attachment.getFormattedFileSize();

            // Assert
            assertThat(formattedSize).isEqualTo("1 bytes");
        }

        @Test
        @DisplayName("Should handle exactly 1 KB (1024 bytes)")
        void shouldHandleOneKilobyteCorrectly() {
            // Arrange
            Attachment attachment = Attachment.builder().fileSize(1024L).build();

            // Act
            String formattedSize = attachment.getFormattedFileSize();

            // Assert - Use regex to handle locale-specific decimal separator (. or ,)
            assertThat(formattedSize).matches("1[.,]00 KB");
        }

        @Test
        @DisplayName("Should handle exactly 1 MB (1048576 bytes)")
        void shouldHandleOneMegabyteCorrectly() {
            // Arrange
            Attachment attachment = Attachment.builder().fileSize(1048576L).build();

            // Act
            String formattedSize = attachment.getFormattedFileSize();

            // Assert - Use regex to handle locale-specific decimal separator (. or ,)
            assertThat(formattedSize).matches("1[.,]00 MB");
        }

        @Test
        @DisplayName("Should handle exactly 1 GB (1073741824 bytes)")
        void shouldHandleOneGigabyteCorrectly() {
            // Arrange
            Attachment attachment = Attachment.builder().fileSize(1073741824L).build();

            // Act
            String formattedSize = attachment.getFormattedFileSize();

            // Assert - Use regex to handle locale-specific decimal separator (. or ,)
            assertThat(formattedSize).matches("1[.,]00 GB");
        }
    }

    // ========== IS IMAGE TESTS ==========

    @Nested
    @DisplayName("isImage() Tests")
    class IsImageTests {

        @Test
        @DisplayName("Should return true for JPEG image")
        void shouldReturnTrueForJpegImage() {
            Attachment attachment = Attachment.builder().fileType("image/jpeg").build();

            assertThat(attachment.isImage()).isTrue();
        }

        @Test
        @DisplayName("Should return true for PNG image")
        void shouldReturnTrueForPngImage() {
            Attachment attachment = Attachment.builder().fileType("image/png").build();

            assertThat(attachment.isImage()).isTrue();
        }

        @Test
        @DisplayName("Should return true for GIF image")
        void shouldReturnTrueForGifImage() {
            Attachment attachment = Attachment.builder().fileType("image/gif").build();

            assertThat(attachment.isImage()).isTrue();
        }

        @Test
        @DisplayName("Should return true for WebP image")
        void shouldReturnTrueForWebpImage() {
            Attachment attachment = Attachment.builder().fileType("image/webp").build();

            assertThat(attachment.isImage()).isTrue();
        }

        @Test
        @DisplayName("Should return false for PDF document")
        void shouldReturnFalseForPdfDocument() {
            Attachment attachment = Attachment.builder().fileType("application/pdf").build();

            assertThat(attachment.isImage()).isFalse();
        }

        @Test
        @DisplayName("Should return false for Word document")
        void shouldReturnFalseForWordDocument() {
            Attachment attachment = Attachment.builder().fileType("application/msword").build();

            assertThat(attachment.isImage()).isFalse();
        }

        @Test
        @DisplayName("Should return false when file type is null")
        void shouldReturnFalseWhenFileTypeIsNull() {
            Attachment attachment = Attachment.builder().fileType(null).build();

            assertThat(attachment.isImage()).isFalse();
        }
    }

    // ========== IS PDF TESTS ==========

    @Nested
    @DisplayName("isPdf() Tests")
    class IsPdfTests {

        @Test
        @DisplayName("Should return true for PDF document")
        void shouldReturnTrueForPdfDocument() {
            Attachment attachment = Attachment.builder().fileType("application/pdf").build();

            assertThat(attachment.isPdf()).isTrue();
        }

        @Test
        @DisplayName("Should return false for image file")
        void shouldReturnFalseForImageFile() {
            Attachment attachment = Attachment.builder().fileType("image/jpeg").build();

            assertThat(attachment.isPdf()).isFalse();
        }

        @Test
        @DisplayName("Should return false for Word document")
        void shouldReturnFalseForWordDocument() {
            Attachment attachment = Attachment.builder().fileType("application/msword").build();

            assertThat(attachment.isPdf()).isFalse();
        }

        @Test
        @DisplayName("Should return false when file type is null")
        void shouldReturnFalseWhenFileTypeIsNull() {
            Attachment attachment = Attachment.builder().fileType(null).build();

            assertThat(attachment.isPdf()).isFalse();
        }
    }

    // ========== IS DOCUMENT TESTS ==========

    @Nested
    @DisplayName("isDocument() Tests")
    class IsDocumentTests {

        @Test
        @DisplayName("Should return true for PDF document")
        void shouldReturnTrueForPdfDocument() {
            Attachment attachment = Attachment.builder().fileType("application/pdf").build();

            assertThat(attachment.isDocument()).isTrue();
        }

        @Test
        @DisplayName("Should return true for Word document (.doc)")
        void shouldReturnTrueForWordDoc() {
            Attachment attachment = Attachment.builder().fileType("application/msword").build();

            assertThat(attachment.isDocument()).isTrue();
        }

        @Test
        @DisplayName("Should return true for Word document (.docx)")
        void shouldReturnTrueForWordDocx() {
            Attachment attachment =
                    Attachment.builder()
                            .fileType(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            .build();

            assertThat(attachment.isDocument()).isTrue();
        }

        @Test
        @DisplayName("Should return true for Excel spreadsheet (.xls)")
        void shouldReturnTrueForExcelXls() {
            Attachment attachment =
                    Attachment.builder().fileType("application/vnd.ms-excel").build();

            assertThat(attachment.isDocument()).isTrue();
        }

        @Test
        @DisplayName("Should return true for Excel spreadsheet (.xlsx)")
        void shouldReturnTrueForExcelXlsx() {
            Attachment attachment =
                    Attachment.builder()
                            .fileType(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            .build();

            assertThat(attachment.isDocument()).isTrue();
        }

        @Test
        @DisplayName("Should return false for image file")
        void shouldReturnFalseForImageFile() {
            Attachment attachment = Attachment.builder().fileType("image/png").build();

            assertThat(attachment.isDocument()).isFalse();
        }

        @Test
        @DisplayName("Should return false when file type is null")
        void shouldReturnFalseWhenFileTypeIsNull() {
            Attachment attachment = Attachment.builder().fileType(null).build();

            assertThat(attachment.isDocument()).isFalse();
        }
    }

    // ========== GET FILE EXTENSION TESTS ==========

    @Nested
    @DisplayName("getFileExtension() Tests")
    class GetFileExtensionTests {

        @Test
        @DisplayName("Should return extension for PDF file")
        void shouldReturnExtensionForPdfFile() {
            Attachment attachment = Attachment.builder().fileName("invoice.pdf").build();

            assertThat(attachment.getFileExtension()).isEqualTo("pdf");
        }

        @Test
        @DisplayName("Should return extension for JPEG file")
        void shouldReturnExtensionForJpegFile() {
            Attachment attachment = Attachment.builder().fileName("receipt.jpeg").build();

            assertThat(attachment.getFileExtension()).isEqualTo("jpeg");
        }

        @Test
        @DisplayName("Should return extension for PNG file")
        void shouldReturnExtensionForPngFile() {
            Attachment attachment = Attachment.builder().fileName("photo.PNG").build();

            assertThat(attachment.getFileExtension()).isEqualTo("png");
        }

        @Test
        @DisplayName("Should handle multiple dots in filename")
        void shouldHandleMultipleDotsInFilename() {
            Attachment attachment = Attachment.builder().fileName("my.document.final.docx").build();

            assertThat(attachment.getFileExtension()).isEqualTo("docx");
        }

        @Test
        @DisplayName("Should return empty string when filename has no extension")
        void shouldReturnEmptyStringWhenNoExtension() {
            Attachment attachment = Attachment.builder().fileName("README").build();

            assertThat(attachment.getFileExtension()).isEqualTo("");
        }

        @Test
        @DisplayName("Should return empty string when filename is null")
        void shouldReturnEmptyStringWhenFilenameIsNull() {
            Attachment attachment = Attachment.builder().fileName(null).build();

            assertThat(attachment.getFileExtension()).isEqualTo("");
        }

        @Test
        @DisplayName("Should return empty string when filename is just a dot")
        void shouldReturnEmptyStringWhenFilenameIsJustDot() {
            Attachment attachment = Attachment.builder().fileName(".").build();

            assertThat(attachment.getFileExtension()).isEqualTo("");
        }

        @Test
        @DisplayName("Should handle filename ending with dot")
        void shouldHandleFilenameEndingWithDot() {
            Attachment attachment = Attachment.builder().fileName("document.").build();

            assertThat(attachment.getFileExtension()).isEqualTo("");
        }
    }

    // ========== BUILDER PATTERN TESTS ==========

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Should build attachment with all fields")
        void shouldBuildAttachmentWithAllFields() {
            // Arrange & Act
            LocalDateTime uploadTime = LocalDateTime.now();
            Attachment attachment =
                    Attachment.builder()
                            .id(1L)
                            .userId(100L)
                            .entityType(EntityType.TRANSACTION)
                            .entityId(500L)
                            .fileName("receipt.pdf")
                            .fileType("application/pdf")
                            .fileSize(1048576L) // 1 MB
                            .filePath("attachments/100/TRANSACTION/abc123.enc")
                            .uploadedAt(uploadTime)
                            .description("Receipt for office supplies")
                            .build();

            // Assert
            assertThat(attachment.getId()).isEqualTo(1L);
            assertThat(attachment.getUserId()).isEqualTo(100L);
            assertThat(attachment.getEntityType()).isEqualTo(EntityType.TRANSACTION);
            assertThat(attachment.getEntityId()).isEqualTo(500L);
            assertThat(attachment.getFileName()).isEqualTo("receipt.pdf");
            assertThat(attachment.getFileType()).isEqualTo("application/pdf");
            assertThat(attachment.getFileSize()).isEqualTo(1048576L);
            assertThat(attachment.getFilePath())
                    .isEqualTo("attachments/100/TRANSACTION/abc123.enc");
            assertThat(attachment.getUploadedAt()).isEqualTo(uploadTime);
            assertThat(attachment.getDescription()).isEqualTo("Receipt for office supplies");
        }

        @Test
        @DisplayName("Should build attachment with minimal fields")
        void shouldBuildAttachmentWithMinimalFields() {
            // Arrange & Act
            Attachment attachment =
                    Attachment.builder()
                            .userId(200L)
                            .entityType(EntityType.ASSET)
                            .entityId(50L)
                            .fileName("photo.jpg")
                            .fileType("image/jpeg")
                            .fileSize(524288L)
                            .filePath("attachments/200/ASSET/xyz789.enc")
                            .build();

            // Assert
            assertThat(attachment.getId()).isNull();
            assertThat(attachment.getUserId()).isEqualTo(200L);
            assertThat(attachment.getEntityType()).isEqualTo(EntityType.ASSET);
            assertThat(attachment.getEntityId()).isEqualTo(50L);
            assertThat(attachment.getFileName()).isEqualTo("photo.jpg");
            assertThat(attachment.getFileType()).isEqualTo("image/jpeg");
            assertThat(attachment.getFileSize()).isEqualTo(524288L);
            assertThat(attachment.getFilePath()).isEqualTo("attachments/200/ASSET/xyz789.enc");
            assertThat(attachment.getUploadedAt()).isNull(); // Not set automatically in builder
            assertThat(attachment.getDescription()).isNull();
        }

        @Test
        @DisplayName("Should build attachment for REAL_ESTATE entity type")
        void shouldBuildAttachmentForRealEstate() {
            // Arrange & Act
            Attachment attachment =
                    Attachment.builder()
                            .userId(300L)
                            .entityType(EntityType.REAL_ESTATE)
                            .entityId(10L)
                            .fileName("deed.pdf")
                            .fileType("application/pdf")
                            .fileSize(2097152L) // 2 MB
                            .filePath("attachments/300/REAL_ESTATE/deed123.enc")
                            .description("Property deed and title documents")
                            .build();

            // Assert
            assertThat(attachment.getEntityType()).isEqualTo(EntityType.REAL_ESTATE);
            assertThat(attachment.getEntityId()).isEqualTo(10L);
            assertThat(attachment.getDescription()).isEqualTo("Property deed and title documents");
        }

        @Test
        @DisplayName("Should build attachment for LIABILITY entity type")
        void shouldBuildAttachmentForLiability() {
            // Arrange & Act
            Attachment attachment =
                    Attachment.builder()
                            .userId(400L)
                            .entityType(EntityType.LIABILITY)
                            .entityId(25L)
                            .fileName("loan_agreement.docx")
                            .fileType(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            .fileSize(512000L)
                            .filePath("attachments/400/LIABILITY/loan456.enc")
                            .description("Mortgage loan agreement")
                            .build();

            // Assert
            assertThat(attachment.getEntityType()).isEqualTo(EntityType.LIABILITY);
            assertThat(attachment.getEntityId()).isEqualTo(25L);
            assertThat(attachment.getFileName()).isEqualTo("loan_agreement.docx");
        }
    }

    // ========== EQUALS AND HASHCODE TESTS ==========

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Should be equal when IDs are the same")
        void shouldBeEqualWhenIdsAreSame() {
            Attachment attachment1 =
                    Attachment.builder().id(1L).userId(100L).fileName("file1.pdf").build();

            Attachment attachment2 =
                    Attachment.builder()
                            .id(1L)
                            .userId(200L) // Different userId
                            .fileName("file2.pdf") // Different fileName
                            .build();

            assertThat(attachment1).isEqualTo(attachment2);
            assertThat(attachment1.hashCode()).isEqualTo(attachment2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when IDs are different")
        void shouldNotBeEqualWhenIdsAreDifferent() {
            Attachment attachment1 =
                    Attachment.builder().id(1L).userId(100L).fileName("file.pdf").build();

            Attachment attachment2 =
                    Attachment.builder().id(2L).userId(100L).fileName("file.pdf").build();

            assertThat(attachment1).isNotEqualTo(attachment2);
        }

        @Test
        @DisplayName("Should not be equal when one ID is null")
        void shouldNotBeEqualWhenOneIdIsNull() {
            Attachment attachment1 = Attachment.builder().id(1L).fileName("file.pdf").build();

            Attachment attachment2 = Attachment.builder().id(null).fileName("file.pdf").build();

            assertThat(attachment1).isNotEqualTo(attachment2);
        }
    }

    // ========== TOSTRING TESTS ==========

    @Nested
    @DisplayName("toString() Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should include explicitly marked fields in toString")
        void shouldIncludeExplicitFieldsInToString() {
            Attachment attachment =
                    Attachment.builder()
                            .id(1L)
                            .userId(100L)
                            .entityType(EntityType.TRANSACTION)
                            .entityId(500L)
                            .fileName("receipt.pdf")
                            .fileType("application/pdf")
                            .fileSize(1024L)
                            .filePath("attachments/100/TRANSACTION/abc.enc")
                            .build();

            String toString = attachment.toString();

            // Should include @ToString.Include fields: id, userId, entityType, entityId, fileName
            assertThat(toString).contains("id=1");
            assertThat(toString).contains("userId=100");
            assertThat(toString).contains("entityType=TRANSACTION");
            assertThat(toString).contains("entityId=500");
            assertThat(toString).contains("fileName=receipt.pdf");
        }
    }
}
