/**
 * AttachmentUpload Component
 * Task 12.1.10: Create AttachmentUpload component
 *
 * Drag-and-drop file upload component with preview and progress tracking
 */
import { useCallback, useState } from 'react';
import { useDropzone } from 'react-dropzone';
import { Upload, X, FileText, Image as ImageIcon, File, AlertCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Progress } from '@/components/ui/Progress';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import {
  ALLOWED_FILE_TYPES,
  MAX_FILE_SIZE,
  formatFileSize,
  type AttachmentEntityType,
} from '@/types/attachment';
import { useUploadAttachment } from '@/hooks/useAttachments';
import { cn } from '@/lib/utils';

interface FileWithPreview extends File {
  preview?: string;
}

interface AttachmentUploadProps {
  entityType: AttachmentEntityType;
  entityId: number;
  onUploadComplete?: () => void;
  className?: string;
  maxFiles?: number;
}

export function AttachmentUpload({
  entityType,
  entityId,
  onUploadComplete,
  className,
  maxFiles = 5,
}: AttachmentUploadProps) {
  const [files, setFiles] = useState<FileWithPreview[]>([]);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const { mutateAsync: uploadFile, isPending, uploadProgress } = useUploadAttachment();
  const { t } = useTranslation('common');

  const onDrop = useCallback(
    (acceptedFiles: File[], rejectedFiles: any[]) => {
      setUploadError(null);

      // Handle rejected files
      if (rejectedFiles.length > 0) {
        const rejection = rejectedFiles[0];
        if (rejection.errors[0]?.code === 'file-too-large') {
          setUploadError(
            t('attachmentUpload.errorTooLarge', { maxSize: formatFileSize(MAX_FILE_SIZE) })
          );
        } else if (rejection.errors[0]?.code === 'file-invalid-type') {
          setUploadError(t('attachmentUpload.errorInvalidType'));
        } else {
          setUploadError(t('attachmentUpload.errorUploadFailed'));
        }
        return;
      }

      // Check total files limit
      if (files.length + acceptedFiles.length > maxFiles) {
        setUploadError(t('attachmentUpload.errorTooManyFiles', { count: maxFiles }));
        return;
      }

      // Add preview URLs for images
      const newFiles = acceptedFiles.map(file => {
        if (file.type.startsWith('image/')) {
          Object.assign(file, {
            preview: URL.createObjectURL(file),
          });
        }
        return file as FileWithPreview;
      });

      setFiles(prev => [...prev, ...newFiles]);
    },
    [files, maxFiles]
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: ALLOWED_FILE_TYPES,
    maxSize: MAX_FILE_SIZE,
    multiple: true,
  });

  const removeFile = (fileToRemove: FileWithPreview) => {
    setFiles(files.filter(file => file !== fileToRemove));
    if (fileToRemove.preview) {
      URL.revokeObjectURL(fileToRemove.preview);
    }
    setUploadError(null);
  };

  const handleUpload = async () => {
    setUploadError(null);

    try {
      for (const file of files) {
        await uploadFile({
          file,
          entityType,
          entityId,
        });
      }

      // Clean up previews
      files.forEach(file => {
        if (file.preview) {
          URL.revokeObjectURL(file.preview);
        }
      });

      setFiles([]);
      onUploadComplete?.();
    } catch (error: any) {
      setUploadError(error.message || t('attachmentUpload.errorUploadFailed'));
    }
  };

  const getFileIcon = (file: File) => {
    if (file.type.startsWith('image/')) return ImageIcon;
    if (file.type === 'application/pdf') return FileText;
    return File;
  };

  return (
    <div className={className}>
      <div className="space-y-4">
        {/* Dropzone */}
        <div
          {...getRootProps()}
          className={cn(
            'border-2 border-dashed rounded-lg p-6 py-8 text-center cursor-pointer transition-colors',
            isDragActive ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/50',
            isPending && 'pointer-events-none opacity-50'
          )}
        >
          <input {...getInputProps()} />
          <Upload className="mx-auto h-8 w-8 text-text-muted mb-3" />
          {isDragActive ? (
            <p className="text-sm font-medium">{t('attachmentUpload.dragging')}</p>
          ) : (
            <>
              <p className="text-sm font-medium mb-1.5 text-text-primary">
                {t('attachmentUpload.dragDrop')}
              </p>
              <p className="text-xs text-text-secondary">
                {t('attachmentUpload.fileTypes', { maxSize: formatFileSize(MAX_FILE_SIZE) })}
              </p>
              <p className="text-[10px] text-text-tertiary mt-1">
                {t('attachmentUpload.maxFiles', { count: maxFiles })}
              </p>
            </>
          )}
        </div>

        {/* Error message */}
        {uploadError && (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{uploadError}</AlertDescription>
          </Alert>
        )}

        {/* File list */}
        {files.length > 0 && (
          <div className="space-y-2">
            <h4 className="text-sm font-medium">
              {t('attachmentUpload.selectedFiles', { count: files.length })}
            </h4>
            <div className="space-y-2">
              {files.map((file, index) => {
                const Icon = getFileIcon(file);
                return (
                  <div
                    key={`${file.name}-${index}`}
                    className="flex items-center gap-3 p-3 bg-surface-elevated rounded-lg"
                  >
                    {/* Preview or icon */}
                    {file.preview ? (
                      <img
                        src={file.preview}
                        alt={file.name}
                        className="w-10 h-10 object-cover rounded"
                      />
                    ) : (
                      <Icon className="w-10 h-10 text-text-muted" />
                    )}

                    {/* File info */}
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium truncate">{file.name}</p>
                      <p className="text-xs text-text-secondary">{formatFileSize(file.size)}</p>
                    </div>

                    {/* Remove button */}
                    {!isPending && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => removeFile(file)}
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    )}
                  </div>
                );
              })}
            </div>

            {/* Upload progress */}
            {isPending && uploadProgress && (
              <div className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span>{t('attachmentUpload.uploading')}</span>
                  <span>
                    {t('attachmentUpload.uploadProgress', {
                      percentage: uploadProgress.percentage,
                    })}
                  </span>
                </div>
                <Progress value={uploadProgress.percentage} />
              </div>
            )}

            {/* Upload button */}
            {!isPending && (
              <Button onClick={handleUpload} disabled={files.length === 0} className="w-full">
                <Upload className="mr-2 h-4 w-4" />
                {t('attachmentUpload.uploadFiles', { count: files.length })}
              </Button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
