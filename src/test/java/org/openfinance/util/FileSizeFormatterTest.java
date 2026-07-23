package org.openfinance.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FileSizeFormatter} — the single shared source of truth for human-readable
 * file sizes, extracted to eliminate the precision drift previously duplicated (and inconsistent)
 * between {@code Backup.getFormattedFileSize()} (2 decimals everywhere) and {@code
 * Attachment.getFormattedFileSize()} (1 decimal for KB/MB, 2 for GB).
 */
class FileSizeFormatterTest {

    @Test
    @DisplayName("Returns 'Unknown' for a null size")
    void nullSize() {
        assertThat(FileSizeFormatter.format(null)).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("Formats sub-1KB sizes as whole bytes")
    void bytes() {
        assertThat(FileSizeFormatter.format(1L)).isEqualTo("1 bytes");
        assertThat(FileSizeFormatter.format(512L)).isEqualTo("512 bytes");
        assertThat(FileSizeFormatter.format(1023L)).isEqualTo("1023 bytes");
    }

    @Test
    @DisplayName("Formats KB with 2 decimal places")
    void kilobytes() {
        assertThat(FileSizeFormatter.format(1024L)).matches("1[.,]00 KB");
        assertThat(FileSizeFormatter.format(2560L)).matches("2[.,]50 KB");
    }

    @Test
    @DisplayName("Formats MB with 2 decimal places")
    void megabytes() {
        assertThat(FileSizeFormatter.format(1048576L)).matches("1[.,]00 MB");
        assertThat(FileSizeFormatter.format(5452595L)).matches("5[.,]20 MB");
    }

    @Test
    @DisplayName("Formats GB with 2 decimal places")
    void gigabytes() {
        assertThat(FileSizeFormatter.format(1073741824L)).matches("1[.,]00 GB");
        assertThat(FileSizeFormatter.format(1342177280L)).matches("1[.,]25 GB");
    }
}
