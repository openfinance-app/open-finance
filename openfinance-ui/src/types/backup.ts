/**
 * Backup types
 * Task 12.5.8: Create useBackup hook - TypeScript types
 *
 * Type definitions for backup and restore operations
 */

/**
 * Backup status enum
 */
export type BackupStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';

/**
 * Backup type enum
 */
export type BackupType = 'MANUAL' | 'AUTOMATIC';

/**
 * Backup response from API
 */
export interface BackupResponse {
  /** Unique backup ID */
  id: number;

  /** Backup filename (e.g., openfinance-backup-20260203-143022.ofbak) */
  filename: string;

  /** File size in bytes */
  fileSize: number;

  /** Human-readable file size (e.g., "2.5 MB") */
  formattedFileSize: string;

  /** SHA-256 checksum (64 hex characters) */
  checksum: string;

  /** Current backup status */
  status: BackupStatus;

  /** Backup type (manual or automatic) */
  backupType: BackupType;

  /** Optional user description */
  description?: string;

  /** Error message if status is FAILED */
  errorMessage?: string;

  /** ISO timestamp when backup was created */
  createdAt: string;

  /** ISO timestamp when backup was last updated */
  updatedAt: string;
}

/**
 * Request to create a new backup
 */
export interface BackupRequest {
  /** Optional user description for the backup */
  description?: string;
}

/**
 * Backup settings configuration
 */
export interface BackupSettings {
  /** Enable automatic scheduled backups */
  autoBackupEnabled: boolean;

  /** Number of automatic backups to keep */
  retentionCount: number;

  /** Backup schedule frequency */
  schedule: 'DAILY' | 'WEEKLY' | 'MONTHLY';

  /** Last automatic backup timestamp */
  lastBackupDate?: string;

  /** Total storage used by backups (human-readable) */
  storageUsed?: string;
}
