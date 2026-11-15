package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Backup;
import org.openfinance.exception.BackupException;
import org.openfinance.repository.BackupRepository;
import org.openfinance.repository.UserRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class BackupBoundaryTest {
    @Mock private BackupRepository backups;
    @Mock private UserRepository users;
    @Mock private UserBackupArchive archives;
    @InjectMocks private BackupService service;
    @TempDir Path directory;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "databaseUrl", "jdbc:sqlite:fixture.db");
        ReflectionTestUtils.setField(service, "backupDirectory", directory.toString());
        ReflectionTestUtils.setField(service, "maxExpandedBytes", 4L);
    }

    @ParameterizedTest
    @CsvSource({"FAILED,restore", "IN_PROGRESS,restore", "FAILED,download", "IN_PROGRESS,download"})
    @DisplayName("Incomplete backups cannot be restored or downloaded")
    void rejectsIncompleteBackups(String status, String action) {
        Backup backup = backup(status, directory.resolve("missing.ofbak"));
        when(backups.findByIdAndUserId(2L, 7L)).thenReturn(Optional.of(backup));
        assertThatThrownBy(
                        () -> {
                            if (action.equals("restore")) service.restoreBackup(7L, 2L);
                            else service.downloadBackup(7L, 2L);
                        })
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("incomplete");
        verifyNoInteractions(archives);
    }

    @ParameterizedTest
    @ValueSource(strings = {"restore", "download"})
    @DisplayName("Missing archive files fail before restoration or validation can run")
    void reportsMissingBackupFiles(String action) {
        when(backups.findByIdAndUserId(2L, 7L))
                .thenReturn(Optional.of(backup("COMPLETED", directory.resolve("missing.ofbak"))));
        assertThatThrownBy(
                        () -> {
                            if (action.equals("restore")) service.restoreBackup(7L, 2L);
                            else service.downloadBackup(7L, 2L);
                        })
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("file not found");
        verifyNoInteractions(archives);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"backup.zip", "backup.ofbak.zip"})
    @DisplayName("An uploaded archive must have an explicit portable-backup filename")
    void rejectsWrongFilenameBeforeOpeningTheUpload(String name) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(name);
        assertThatThrownBy(() -> service.restoreBackupFromFile(7L, file))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("format");
        verify(file, never()).getInputStream();
        verifyNoInteractions(backups, archives);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    @DisplayName(
            "Expanded size is checked before any restore or safety backup, with an inclusive limit")
    void enforcesExpandedSizeBeforeRestoration(int size) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
            gzip.write(new byte[size]);
        }
        MockMultipartFile upload =
                new MockMultipartFile(
                        "file", "valid.ofbak", "application/octet-stream", buffer.toByteArray());
        if (size > 4) {
            assertThatThrownBy(() -> service.restoreBackupFromFile(7L, upload, "secret"))
                    .isInstanceOf(BackupException.class)
                    .hasMessageContaining("size limit");
            verifyNoInteractions(backups, archives);
            return;
        }
        when(backups.save(any())).thenAnswer(call -> call.getArgument(0));
        doAnswer(call -> Files.writeString(call.getArgument(1), "safe"))
                .when(archives)
                .write(eq(7L), any(Path.class));
        Path[] restored = new Path[1];
        doAnswer(
                        call -> {
                            restored[0] = call.getArgument(1);
                            assertThat(Files.readAllBytes(restored[0])).hasSize(size);
                            return null;
                        })
                .when(archives)
                .restore(eq(7L), any(Path.class), eq("secret"));
        service.restoreBackupFromFile(7L, upload, "secret");
        verify(archives).restore(eq(7L), any(Path.class), eq("secret"));
        assertThat(Files.exists(restored[0])).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"false,jdbc:sqlite:fixture.db", "true,jdbc:postgresql:fixture"})
    @DisplayName("Disabled or unsupported backup schedules never enumerate users")
    void ignoresUnavailableSchedules(boolean enabled, String url) {
        ReflectionTestUtils.setField(service, "scheduleEnabled", enabled);
        ReflectionTestUtils.setField(service, "databaseUrl", url);
        service.scheduledBackup();
        verifyNoInteractions(users, archives, backups);
    }

    @Test
    @DisplayName("Portable backup creation rejects unsupported databases before storing metadata")
    void rejectsUnsupportedDatabase() {
        ReflectionTestUtils.setField(service, "databaseUrl", "jdbc:postgresql:fixture");
        assertThatThrownBy(() -> service.createBackup(7L, "Manual"))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("SQLite");
        verifyNoInteractions(backups, archives);
    }

    @Test
    @DisplayName(
            "An unwritable archive directory records failure without invoking the archive writer")
    void recordsDirectoryFailure() throws Exception {
        Path file = directory.resolve("not-a-directory");
        Files.writeString(file, "occupied");
        ReflectionTestUtils.setField(service, "backupDirectory", file.toString());
        when(backups.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThatThrownBy(() -> service.createBackup(7L, "Manual"))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("Could not create");
        verify(archives, never()).write(anyLong(), any(Path.class));
        assertThat(Files.readString(file)).isEqualTo("occupied");
    }

    private Backup backup(String status, Path file) {
        return Backup.builder().id(2L).userId(7L).status(status).filePath(file.toString()).build();
    }
}
