/**
 * AttachmentList Component
 * Task 12.1.11: Create AttachmentList component
 * Task 12.1.14: Add image preview modal
 *
 * Display list of attachments with download/delete functionality and image preview
 */
import { useState } from 'react';
import {
  Download,
  Trash2,
  FileText,
  Image as ImageIcon,
  File,
  Table,
  Loader2,
  Eye,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { ImagePreviewModal } from './ImagePreviewModal';
import { useAttachments, useDownloadAttachment, useDeleteAttachment } from '@/hooks/useAttachments';
import { type Attachment, type AttachmentEntityType } from '@/types/attachment';
import { formatDistanceToNow } from 'date-fns';
import { cn } from '@/lib/utils';

interface AttachmentListProps {
  entityType: AttachmentEntityType;
  entityId: number;
  className?: string;
  showUploadDate?: boolean;
}

export function AttachmentList({
  entityType,
  entityId,
  className,
  showUploadDate = true,
}: AttachmentListProps) {
  const { data: attachments, isLoading } = useAttachments({ entityType, entityId });
  const { downloadAttachment, isDownloading } = useDownloadAttachment();
  const { mutate: deleteAttachment, isPending: isDeleting } = useDeleteAttachment();

  const [downloadingId, setDownloadingId] = useState<number | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [attachmentToDelete, setAttachmentToDelete] = useState<Attachment | null>(null);
  const [imagePreviewOpen, setImagePreviewOpen] = useState(false);
  const [previewImageIndex, setPreviewImageIndex] = useState(0);

  // Filter only image attachments for preview modal
  const imageAttachments = attachments?.filter(a => a.image) || [];

  const getFileIcon = (attachment: Attachment) => {
    if (attachment.image) return ImageIcon;
    if (attachment.pdf) return FileText;
    if (attachment.fileType.includes('word')) return FileText;
    if (attachment.fileType.includes('excel') || attachment.fileType.includes('spreadsheet'))
      return Table;
    if (attachment.fileType.includes('csv')) return Table;
    return File;
  };

  const handleDownload = async (attachment: Attachment) => {
    try {
      setDownloadingId(attachment.id);
      await downloadAttachment(attachment.id, attachment.fileName);
    } catch (error) {
      console.error('Download failed:', error);
    } finally {
      setDownloadingId(null);
    }
  };

  const handleDeleteClick = (attachment: Attachment) => {
    setAttachmentToDelete(attachment);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = () => {
    if (attachmentToDelete) {
      deleteAttachment(attachmentToDelete.id);
      setDeleteDialogOpen(false);
      setAttachmentToDelete(null);
    }
  };

  const handleImageClick = (attachment: Attachment) => {
    if (!attachment.image) return;

    const imageIndex = imageAttachments.findIndex(img => img.id === attachment.id);
    if (imageIndex !== -1) {
      setPreviewImageIndex(imageIndex);
      setImagePreviewOpen(true);
    }
  };

  if (isLoading) {
    return (
      <Card className={cn('p-6', className)}>
        <div className="flex items-center justify-center py-8">
          <Loader2 className="h-8 w-8 animate-spin text-text-muted" />
        </div>
      </Card>
    );
  }

  if (!attachments || attachments.length === 0) {
    return null;
  }

  return (
    <>
      <Card className={cn('p-6', className)}>
        <div className="space-y-2">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-medium">Attachments ({attachments.length})</h3>
          </div>

          <div className="space-y-2">
            {attachments.map(attachment => {
              const Icon = getFileIcon(attachment);
              const isDownloadingThis = downloadingId === attachment.id;

              return (
                <div
                  key={attachment.id}
                  className="flex items-center gap-3 p-3 bg-surface-elevated rounded-lg hover:bg-surface-elevated/70 transition-colors"
                >
                  {/* File icon */}
                  <div
                    className={cn(
                      'flex-shrink-0',
                      attachment.image && 'cursor-pointer hover:opacity-70 transition-opacity'
                    )}
                    onClick={() => handleImageClick(attachment)}
                    title={attachment.image ? 'Click to preview' : undefined}
                  >
                    <Icon className="h-8 w-8 text-text-muted" />
                  </div>

                  {/* File info */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <p
                        className={cn(
                          'text-sm font-medium truncate',
                          attachment.image && 'cursor-pointer hover:text-primary'
                        )}
                        onClick={() => handleImageClick(attachment)}
                        title={attachment.image ? 'Click to preview' : undefined}
                      >
                        {attachment.fileName}
                      </p>
                      {attachment.image && (
                        <Badge variant="default" className="text-xs">
                          Image
                        </Badge>
                      )}
                      {attachment.pdf && (
                        <Badge variant="default" className="text-xs">
                          PDF
                        </Badge>
                      )}
                    </div>
                    <div className="flex items-center gap-2 mt-1">
                      <span className="text-xs text-text-secondary">
                        {attachment.formattedFileSize}
                      </span>
                      {showUploadDate && (
                        <>
                          <span className="text-xs text-text-muted">•</span>
                          <span className="text-xs text-text-secondary">
                            {formatDistanceToNow(new Date(attachment.uploadedAt), {
                              addSuffix: true,
                            })}
                          </span>
                        </>
                      )}
                    </div>
                    {attachment.description && (
                      <p className="text-xs text-text-secondary mt-1 truncate">
                        {attachment.description}
                      </p>
                    )}
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-1">
                    {attachment.image && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleImageClick(attachment)}
                        disabled={isDeleting}
                        title="Preview image"
                      >
                        <Eye className="h-4 w-4" />
                      </Button>
                    )}
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleDownload(attachment)}
                      disabled={isDownloadingThis || isDeleting}
                      title="Download"
                    >
                      {isDownloadingThis ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <Download className="h-4 w-4" />
                      )}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleDeleteClick(attachment)}
                      disabled={isDownloading || isDeleting}
                      title="Delete"
                    >
                      <Trash2 className="h-4 w-4 text-red-500" />
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </Card>

      {/* Image preview modal */}
      {imageAttachments.length > 0 && (
        <ImagePreviewModal
          images={imageAttachments}
          initialIndex={previewImageIndex}
          open={imagePreviewOpen}
          onClose={() => setImagePreviewOpen(false)}
        />
      )}

      {/* Delete confirmation dialog */}
      <ConfirmationDialog
        open={deleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        onConfirm={handleDeleteConfirm}
        title="Delete Attachment"
        description={`Are you sure you want to delete "${attachmentToDelete?.fileName}"? This action cannot be undone.`}
        confirmText="Delete"
        variant="danger"
      />
    </>
  );
}
