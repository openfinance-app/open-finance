/**
 * FileUpload Component
 * Task 7.1.7: Create FileUpload component with drag-and-drop
 * 
 * Provides a drag-and-drop file upload interface for importing transactions
 */
import React, { useState, useRef, useCallback } from 'react';
import { Upload, X, File, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';
import { useFileUpload } from '@/hooks/useFileUpload';
import { useTranslation } from 'react-i18next';
import type { FileUploadResponse } from '@/types/import';

interface FileUploadProps {
  onUploadSuccess?: (response: FileUploadResponse) => void;
  onUploadError?: (error: string) => void;
  maxSizeMB?: number;
  acceptedFormats?: string[];
}

const DEFAULT_ACCEPTED_FORMATS = ['.qif', '.ofx', '.qfx', '.csv', '.json'];
const DEFAULT_MAX_SIZE_MB = 10;

export function FileUpload({
  onUploadSuccess,
  onUploadError,
  maxSizeMB = DEFAULT_MAX_SIZE_MB,
  acceptedFormats = DEFAULT_ACCEPTED_FORMATS,
}: FileUploadProps) {
  const { t } = useTranslation('import');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [uploadStatus, setUploadStatus] = useState<'idle' | 'uploading' | 'success' | 'error'>('idle');
  const [errorMessage, setErrorMessage] = useState<string>('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const uploadFile = useFileUpload();

  // Handle file selection
  const handleFileSelect = useCallback(
    (file: File) => {
      // Reset previous state
      setErrorMessage('');
      setUploadStatus('idle');

      // Validate file extension
      const fileExtension = '.' + file.name.split('.').pop()?.toLowerCase();
      if (!acceptedFormats.includes(fileExtension)) {
        const error = t('upload.invalidFormat', { formats: acceptedFormats.join(', ') });
        setErrorMessage(error);
        setUploadStatus('error');
        onUploadError?.(error);
        return;
      }

      // Validate file size — reject empty files
      if (file.size === 0) {
        const error = t('upload.fileEmpty', 'Le fichier sélectionné est vide (0 B). Veuillez sélectionner un fichier valide.');
        setErrorMessage(error);
        setUploadStatus('error');
        onUploadError?.(error);
        return;
      }

      const fileSizeMB = file.size / (1024 * 1024);
      if (fileSizeMB > maxSizeMB) {
        const error = t('upload.fileTooLarge', { max: maxSizeMB, actual: fileSizeMB.toFixed(2) });
        setErrorMessage(error);
        setUploadStatus('error');
        onUploadError?.(error);
        return;
      }

      setSelectedFile(file);
    },
    [acceptedFormats, maxSizeMB, onUploadError]
  );

  // Handle drag and drop events
  const handleDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragging(false);

      const files = Array.from(e.dataTransfer.files);
      if (files.length > 0) {
        handleFileSelect(files[0]);
      }
    },
    [handleFileSelect]
  );

  // Handle file input change
  const handleFileInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = e.target.files;
      if (files && files.length > 0) {
        handleFileSelect(files[0]);
      }
    },
    [handleFileSelect]
  );

  // Trigger file browser
  const handleBrowseClick = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  // Clear selected file
  const handleClearFile = useCallback(() => {
    setSelectedFile(null);
    setUploadStatus('idle');
    setErrorMessage('');
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }, []);

  // Upload file
  const handleUpload = useCallback(() => {
    if (!selectedFile) return;

    setUploadStatus('uploading');
    setErrorMessage('');

    uploadFile.mutate(selectedFile, {
      onSuccess: (response) => {
        setUploadStatus('success');
        onUploadSuccess?.(response);
      },
      onError: (error) => {
        const axiosError = error as unknown as { response?: { data?: { message?: string } } };
        const errorMsg = axiosError?.response?.data?.message || error.message || t('upload.failed');
        setErrorMessage(errorMsg);
        setUploadStatus('error');
        onUploadError?.(errorMsg);
      },
    });
  }, [selectedFile, uploadFile, onUploadSuccess, onUploadError]);

  // Format file size
  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  };

  return (
    <div className="space-y-4">
      {/* Drag and Drop Area */}
      <div
        onDragEnter={handleDragEnter}
        onDragLeave={handleDragLeave}
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        className={`
          relative border-2 border-dashed rounded-lg p-8 text-center transition-all
          ${isDragging
            ? 'border-primary bg-primary/5'
            : 'border-border-subtle hover:border-primary/50'
          }
          ${uploadStatus === 'success' ? 'border-green-500 bg-green-500/5' : ''}
          ${uploadStatus === 'error' ? 'border-red-500 bg-red-500/5' : ''}
        `}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept={acceptedFormats.join(',')}
          onChange={handleFileInputChange}
          className="hidden"
        />

        {uploadStatus === 'idle' && !selectedFile && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <div className="p-3 bg-primary/10 rounded-full">
                <Upload className="h-8 w-8 text-primary" />
              </div>
            </div>
            <div>
              <p className="text-lg font-medium text-text-primary mb-1">
                {t('upload.dropHere')}
              </p>
              <p className="text-sm text-text-secondary mb-4">
                {t('upload.orBrowse')}
              </p>
              <button
                onClick={handleBrowseClick}
                className="px-4 py-2 bg-primary text-white rounded-md hover:bg-primary-hover transition-colors"
              >
                {t('upload.browse')}
              </button>
            </div>
            <div className="text-xs text-text-tertiary space-y-1">
              <p>{t('upload.acceptedFormats', { formats: acceptedFormats.join(', ') })}</p>
              <p>{t('upload.maxSize', { size: maxSizeMB })}</p>
            </div>
          </div>
        )}

        {selectedFile && uploadStatus === 'idle' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between bg-app-bg border border-border-subtle rounded-md p-4">
              <div className="flex items-center space-x-3">
                <File className="h-8 w-8 text-primary" />
                <div className="text-left">
                  <p className="font-medium text-text-primary">{selectedFile.name}</p>
                  <p className="text-sm text-text-tertiary">{formatFileSize(selectedFile.size)}</p>
                </div>
              </div>
              <button
                onClick={handleClearFile}
                className="p-2 hover:bg-surface-hover rounded-md transition-colors"
                title={t('upload.removeFile')}
              >
                <X className="h-5 w-5 text-text-tertiary" />
              </button>
            </div>
            <div className="flex space-x-3 justify-center">
              <button
                onClick={handleUpload}
                className="px-6 py-2 bg-primary text-white rounded-md hover:bg-primary-hover transition-colors"
              >
                {t('common:buttons.upload', 'Upload File')}
              </button>
              <button
                onClick={handleClearFile}
                className="px-6 py-2 border border-border-subtle text-text-secondary rounded-md hover:bg-surface-hover transition-colors"
              >
                {t('common:buttons.cancel')}
              </button>
            </div>
          </div>
        )}

        {uploadStatus === 'uploading' && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <Loader2 className="h-12 w-12 text-primary animate-spin" />
            </div>
            <p className="text-lg font-medium text-text-primary">{t('upload.uploading')}</p>
            <p className="text-sm text-text-tertiary">{t('upload.validating')}</p>
          </div>
        )}

        {uploadStatus === 'success' && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <div className="p-3 bg-green-500/10 rounded-full">
                <CheckCircle2 className="h-12 w-12 text-green-500" />
              </div>
            </div>
            <div>
              <p className="text-lg font-medium text-green-500 mb-1">
                {t('upload.success')}
              </p>
              <p className="text-sm text-text-tertiary">
                {t('upload.successDesc')}
              </p>
            </div>
            <button
              onClick={handleClearFile}
              className="px-4 py-2 border border-border-subtle text-text-secondary rounded-md hover:bg-surface-hover transition-colors"
            >
              {t('upload.uploadAnother')}
            </button>
          </div>
        )}

        {uploadStatus === 'error' && errorMessage && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <div className="p-3 bg-red-500/10 rounded-full">
                <AlertCircle className="h-12 w-12 text-red-500" />
              </div>
            </div>
            <div>
              <p className="text-lg font-medium text-red-500 mb-1">
                {t('upload.failed')}
              </p>
              <p className="text-sm text-text-secondary">{errorMessage}</p>
            </div>
            <button
              onClick={handleClearFile}
              className="px-4 py-2 bg-primary text-white rounded-md hover:bg-primary-hover transition-colors"
            >
              {t('upload.tryAgain')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
