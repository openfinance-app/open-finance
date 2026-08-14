/**
 * Attachment types
 * Task 12.1.10-12.1.13: Frontend attachment implementation
 * 
 * Type definitions for file attachments
 */

/**
 * Entity types that can have attachments
 */
export const AttachmentEntityType = {
  TRANSACTION: 'TRANSACTION',
  ASSET: 'ASSET',
  REAL_ESTATE: 'REAL_ESTATE',
  LIABILITY: 'LIABILITY',
  ACCOUNT: 'ACCOUNT',
  RECURRING_TRANSACTION: 'RECURRING_TRANSACTION',
} as const;

export type AttachmentEntityType = typeof AttachmentEntityType[keyof typeof AttachmentEntityType];

/**
 * Attachment model (from backend)
 */
export interface Attachment {
  id: number;
  userId: number;
  entityType: AttachmentEntityType;
  entityId: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  uploadedAt: string;
  description?: string;
  // Helper properties from backend
  fileExtension: string;
  formattedFileSize: string;
  image: boolean;
  pdf: boolean;
  document: boolean;
}

/**
 * Attachment upload request
 */
export interface AttachmentUploadRequest {
  file: File;
  entityType: AttachmentEntityType;
  entityId: number;
  description?: string;
}

/**
 * Attachment filters for listing
 */
export interface AttachmentFilters {
  entityType?: AttachmentEntityType;
  entityId?: number;
}

/**
 * Storage statistics response
 */
export interface StorageStats {
  totalAttachments: number;
  totalSizeBytes: number;
  totalSizeFormatted: string;
}

/**
 * File upload progress
 */
export interface UploadProgress {
  loaded: number;
  total: number;
  percentage: number;
}

/**
 * Allowed file types configuration
 */
export const ALLOWED_FILE_TYPES = {
  // Images
  'image/jpeg': ['.jpg', '.jpeg'],
  'image/png': ['.png'],
  'image/gif': ['.gif'],
  'image/webp': ['.webp'],
  // Documents
  'application/pdf': ['.pdf'],
  'application/msword': ['.doc'],
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'],
  'application/vnd.ms-excel': ['.xls'],
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx'],
  'text/csv': ['.csv'],
  'text/plain': ['.txt'],
} as const;

export const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

/**
 * Get allowed file extensions as array
 */
export const getAllowedExtensions = (): string[] => {
  return Object.values(ALLOWED_FILE_TYPES).flat();
};

/**
 * Get allowed MIME types as array
 */
export const getAllowedMimeTypes = (): string[] => {
  return Object.keys(ALLOWED_FILE_TYPES);
};

/**
 * Check if file type is allowed
 */
export const isFileTypeAllowed = (fileType: string): boolean => {
  return getAllowedMimeTypes().includes(fileType);
};

/**
 * Format file size for display
 */
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 bytes';
  const k = 1024;
  const sizes = ['bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
};

/**
 * Get file icon based on file type
 */
export const getFileIcon = (attachment: Attachment): string => {
  if (attachment.image) return 'Image';
  if (attachment.pdf) return 'FileText';
  if (attachment.fileType.includes('word')) return 'FileText';
  if (attachment.fileType.includes('excel') || attachment.fileType.includes('spreadsheet')) return 'Table';
  if (attachment.fileType.includes('csv')) return 'Table';
  return 'File';
};
