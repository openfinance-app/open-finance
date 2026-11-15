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
import { Button } from '@/components/ui/Button';

interface FileUploadProps {
  /** Advance only once every selected file has been uploaded. */
  onUploadSuccess?: (response: FileUploadResponse, additionalUploads: FileUploadResponse[]) => void;
  onUploadingChange?: (uploading: boolean) => void;
  onUploadError?: (error: string) => void;
  maxSizeMB?: number;
  acceptedFormats?: string[];
}

const DEFAULT_ACCEPTED_FORMATS = ['.qif', '.ofx', '.qfx', '.csv', '.json'];
const DEFAULT_MAX_SIZE_MB = 10;

export function FileUpload({
  onUploadSuccess,
  onUploadError,
  onUploadingChange,
  maxSizeMB = DEFAULT_MAX_SIZE_MB,
  acceptedFormats = DEFAULT_ACCEPTED_FORMATS,
}: FileUploadProps) {
  const { t } = useTranslation('import');
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const selectedFile = selectedFiles[0];
  const uploadedFiles = useRef(new Map<File, FileUploadResponse>());
  const [uploadingFileName, setUploadingFileName] = useState('');
  const [isDragging, setIsDragging] = useState(false);
  const [uploadStatus, setUploadStatus] = useState<'idle' | 'uploading' | 'success' | 'error'>(
    'idle'
  );
  const [errorMessage, setErrorMessage] = useState<string>('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const uploadFile = useFileUpload();

  // Handle file selection
  const handleFileSelect = useCallback(
    (files: File[]) => {
      if (uploadStatus === 'uploading' || uploadStatus === 'success') return;
      // Reset previous state
      setErrorMessage('');
      setUploadStatus('idle');
      const validFiles: File[] = [];
      const errors: string[] = [];
      for (const file of files) {
        const extension = '.' + file.name.split('.').pop()?.toLowerCase();
        const sizeMB = file.size / (1024 * 1024);
        const error = !acceptedFormats.includes(extension)
          ? t('upload.invalidFormat', { formats: acceptedFormats.join(', ') })
          : file.size === 0
            ? t('upload.fileEmpty')
            : sizeMB > maxSizeMB
              ? t('upload.fileTooLarge', { max: maxSizeMB, actual: sizeMB.toFixed(2) })
              : null;
        if (error) errors.push(`${file.name}: ${error}`);
        else validFiles.push(file);
      }
      const combined = [...selectedFiles];
      for (const file of validFiles) {
        if (!combined.includes(file)) combined.push(file);
      }
      setSelectedFiles(combined);
      if (errors.length > 0) {
        const message = errors.join('\n');
        setErrorMessage(message);
        if (combined.length === 0) setUploadStatus('error');
        onUploadError?.(message);
      }
    },
    [acceptedFormats, maxSizeMB, onUploadError, selectedFiles, t, uploadStatus]
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
        handleFileSelect(files);
      }
    },
    [handleFileSelect]
  );

  // Handle file input change
  const handleFileInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = e.target.files;
      if (files && files.length > 0) {
        handleFileSelect(Array.from(files));
      }
      e.target.value = '';
    },
    [handleFileSelect]
  );

  // Trigger file browser
  const handleBrowseClick = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  // Clear selected file
  const handleClearFile = useCallback(() => {
    setSelectedFiles([]);
    uploadedFiles.current.clear();
    setUploadStatus('idle');
    setErrorMessage('');
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }, []);

  // Upload file
  const handleUpload = useCallback(async () => {
    if (!selectedFiles.length || uploadStatus === 'uploading') return;
    setUploadStatus('uploading');
    setErrorMessage('');
    onUploadingChange?.(true);
    let currentFile: File | undefined;
    try {
      const responses: FileUploadResponse[] = [];
      for (const file of selectedFiles) {
        currentFile = file;
        setUploadingFileName(file.name);
        const response = uploadedFiles.current.get(file) ?? (await uploadFile.mutateAsync(file));
        if (!response.uploadId) throw new Error(response.message || t('upload.failed'));
        uploadedFiles.current.set(file, response);
        responses.push(response);
      }
      setUploadStatus('success');
      onUploadSuccess?.(responses[0], responses.slice(1));
    } catch (error) {
      const detail = error as { message?: string; response?: { data?: { message?: string } } };
      const message = `${currentFile?.name}: ${detail.response?.data?.message || detail.message || t('upload.failed')}`;
      setErrorMessage(message);
      setUploadStatus('error');
      onUploadError?.(message);
    } finally {
      onUploadingChange?.(false);
    }
  }, [
    selectedFiles,
    uploadStatus,
    uploadFile,
    onUploadSuccess,
    onUploadError,
    onUploadingChange,
    t,
  ]);

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
          ${
            isDragging
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
          multiple
          disabled={uploadStatus === 'uploading'}
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
              <p className="text-lg font-medium text-text-primary mb-1">{t('upload.dropHere')}</p>
              <p className="text-sm text-text-secondary mb-4">{t('upload.orBrowse')}</p>
              <Button type="button" onClick={handleBrowseClick}>
                {t('upload.browse')}
              </Button>
            </div>
            <div className="text-xs text-text-tertiary space-y-1">
              <p>{t('upload.acceptedFormats', { formats: acceptedFormats.join(', ') })}</p>
              <p>{t('upload.maxSize', { size: maxSizeMB })}</p>
            </div>
          </div>
        )}

        {selectedFile && uploadStatus === 'idle' && (
          <div className="space-y-4">
            <p className="text-sm text-text-secondary">
              {t('upload.selected', { count: selectedFiles.length })}
            </p>
            {selectedFiles.length > 1 && (
              <p className="text-sm text-text-secondary">{t('upload.batchHint')}</p>
            )}
            {errorMessage && (
              <p role="alert" className="whitespace-pre-line break-words text-sm text-error">
                {errorMessage}
              </p>
            )}
            <ul className="max-h-72 overflow-y-auto divide-y divide-border text-left">
              {selectedFiles.map((file, index) => (
                <li
                  key={`${file.name}-${index}`}
                  className="flex items-center justify-between gap-3 py-3"
                >
                  <File className="h-6 w-6 shrink-0 text-primary" />
                  <div className="min-w-0 flex-1">
                    <p className="break-all font-medium text-text-primary">{file.name}</p>
                    <p className="text-sm text-text-tertiary">{formatFileSize(file.size)}</p>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={t('upload.removeNamedFile', { name: file.name })}
                    onClick={() =>
                      setSelectedFiles(files => files.filter((_, fileIndex) => fileIndex !== index))
                    }
                  >
                    <X className="h-5 w-5 text-text-tertiary" />
                  </Button>
                </li>
              ))}
            </ul>
            <div className="flex flex-wrap gap-3 justify-center">
              <Button type="button" onClick={handleUpload}>
                {selectedFiles.length > 1 ? t('upload.startBatch') : t('common:buttons.upload')}
              </Button>
              <Button type="button" variant="secondary" onClick={handleBrowseClick}>
                {t('upload.addFiles')}
              </Button>
              <Button type="button" variant="ghost" onClick={handleClearFile}>
                {t('common:buttons.cancel')}
              </Button>
            </div>
          </div>
        )}

        {uploadStatus === 'uploading' && (
          <div className="space-y-4">
            <div className="flex justify-center">
              <Loader2 className="h-12 w-12 text-primary animate-spin" />
            </div>
            <p className="text-lg font-medium text-text-primary">{t('upload.uploading')}</p>
            <p className="break-all text-sm text-text-secondary">{uploadingFileName}</p>
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
              <p className="text-lg font-medium text-green-500 mb-1">{t('upload.success')}</p>
              <p className="text-sm text-text-tertiary">{t('upload.successDesc')}</p>
            </div>
            <Button type="button" variant="secondary" onClick={handleClearFile}>
              {t('upload.uploadAnother')}
            </Button>
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
              <p className="text-lg font-medium text-red-500 mb-1">{t('upload.failed')}</p>
              <p
                role="alert"
                className="whitespace-pre-line break-words text-sm text-text-secondary"
              >
                {errorMessage}
              </p>
            </div>
            <Button
              type="button"
              onClick={() => {
                setUploadStatus('idle');
                setErrorMessage('');
              }}
            >
              {t('upload.tryAgain')}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
