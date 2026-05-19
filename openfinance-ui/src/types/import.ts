/**
 * Import-related type definitions
 * Task 7.1.8: Create file upload types
 * Task 7.4.9-7.4.14: Extended types for import wizard and session management
 */

/**
 * File upload response from the backend
 */
export interface FileUploadResponse {
  uploadId: string | null;
  fileName: string;
  fileSize: number;
  fileType: string;
  status: 'VALIDATED' | 'INVALID' | 'ERROR';
  message: string;
  uploadedAt: string;
  recordCount: number | null;
}

/**
 * File upload status
 */
export type UploadStatus = 'idle' | 'uploading' | 'success' | 'error';

/**
 * File upload error
 */
export interface FileUploadError {
  message: string;
  status?: number;
}

/**
 * Import session status
 */
export type ImportSessionStatus =
  | 'PENDING'
  | 'PARSING'
  | 'PARSED'
  | 'IMPORTING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

/**
 * File format types
 */
export type FileFormat = 'QIF' | 'OFX' | 'CSV' | 'JSON';

/**
 * Import session response from backend
 */
export interface ImportSessionResponse {
  id: number;
  uploadId: string;
  userId: number;
  fileName: string;
  fileFormat: FileFormat;
  accountId: number | null;
  suggestedAccountName: string | null;
  status: ImportSessionStatus;
  totalTransactions: number;
  importedCount: number;
  errorCount: number;
  duplicateCount: number;
  skippedCount: number;
  errorMessage: string | null;
  metadata: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  terminal: boolean;
  cancellable: boolean;
  readyForReview: boolean;
  confirmable: boolean;
}

/**
 * Import split transaction
 */
export interface ImportSplit {
  category: string;
  amount: number;
  memo: string;
}

/**
 * Import transaction DTO
 */
export interface ImportTransactionDTO {
  transactionDate: string;
  payee: string;
  /** Original payee from the import file before any auto-categorization replacement. */
  originalPayee?: string | null;
  amount: number;
  currency: string;
  memo: string | null;
  category: string | null;
  clearedStatus: string;
  referenceNumber: string | null;
  accountName: string | null;
  lineNumber: number | null;
  splits: ImportSplit[];
  validationErrors: string[];
  potentialDuplicate: boolean;
  sourceFileName: string;
  rawData: string | null;
  splitTransaction: boolean;
}

/**
 * Request to start import process
 */
export interface ImportProcessRequest {
  uploadId: string;
  accountId: number | null;
  /** Original filename shown to the user; used to name auto-created accounts */
  originalFileName?: string;
}

/**
 * Request to confirm import
 */
export interface ImportConfirmRequest {
  /** Optional — when null the backend auto-creates an account from the session's suggestedAccountName */
  accountId: number | null;
  categoryMappings: Record<string, number>;
  skipDuplicates: boolean;
}

/**
 * Category mapping for import
 */
export interface CategoryMapping {
  sourceCategory: string;
  targetCategoryId: number | null;
  transactionCount: number;
}

/**
 * Import wizard step
 * Steps: upload → account → review → confirm → progress
 * (Category mapping has been merged into the review step)
 */
export type ImportWizardStep = 'upload' | 'account' | 'review' | 'confirm' | 'progress';

/**
 * Import wizard state
 */
export interface ImportWizardState {
  currentStep: ImportWizardStep;
  uploadId: string | null;
  accountId: number | null;
  sessionId: number | null;
  categoryMappings: Record<string, number>;
  skipDuplicates: boolean;
}
