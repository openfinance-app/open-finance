package org.openfinance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.ImportSession;

/** A file's parsing context inside one import session, stored in encrypted session metadata. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportSourceFile {
    private String uploadId;
    private String fileName;
    private String fileFormat;
    private Long accountId;
    private String suggestedAccountName;
    private int totalTransactions;
    private String metadata;

    public static ImportSourceFile fromSession(ImportSession session) {
        return ImportSourceFile.builder()
                .uploadId(session.getUploadId())
                .fileName(session.getFileName())
                .fileFormat(session.getFileFormat())
                .accountId(session.getAccountId())
                .suggestedAccountName(session.getSuggestedAccountName())
                .totalTransactions(session.getTotalTransactions())
                .metadata(session.getMetadata())
                .build();
    }

    /** No database ID: this context shares its parent session's lifecycle and transaction. */
    public ImportSession toSession(Long userId) {
        return ImportSession.builder()
                .uploadId(uploadId)
                .userId(userId)
                .fileName(fileName)
                .fileFormat(fileFormat)
                .accountId(accountId)
                .suggestedAccountName(suggestedAccountName)
                .totalTransactions(totalTransactions)
                .metadata(metadata)
                .status(ImportSession.ImportStatus.PARSED)
                .build();
    }
}
