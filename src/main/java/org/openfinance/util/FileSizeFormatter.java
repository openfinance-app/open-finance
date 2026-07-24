package org.openfinance.util;

/**
 * Single shared source of truth for human-readable file sizes.
 *
 * <p>Previously, {@code Backup} and {@code Attachment} each had their own {@code
 * getFormattedFileSize()} implementation with different precision (2 decimals everywhere in {@code
 * Backup} vs 1 decimal for KB/MB but 2 for GB in {@code Attachment}). This class replaces both with
 * one consistent formatter (2 decimal places at every tier).
 */
public final class FileSizeFormatter {

    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;

    private FileSizeFormatter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Formats {@code bytes} as a human-readable size (e.g. "1.50 MB", "512 bytes").
     *
     * @param bytes the size in bytes, or {@code null}
     * @return the formatted size, or {@code "Unknown"} if {@code bytes} is {@code null}
     */
    public static String format(Long bytes) {
        if (bytes == null) {
            return "Unknown";
        }
        if (bytes < KB) {
            return bytes + " bytes";
        }
        if (bytes < MB) {
            return String.format("%.2f KB", bytes / (double) KB);
        }
        if (bytes < GB) {
            return String.format("%.2f MB", bytes / (double) MB);
        }
        return String.format("%.2f GB", bytes / (double) GB);
    }
}
