/**
 * Display-only gallery of a property's image attachments.
 * Upload/delete stay in the Attachments tab (existing AttachmentUpload/AttachmentList).
 */
import { useTranslation } from 'react-i18next';
import { ImageOff } from 'lucide-react';
import { usePropertyImages, useAttachmentImageUrl } from '@/hooks/usePropertyImages';

interface PropertyGalleryProps {
  propertyId: number;
}

function GalleryImage({ attachmentId, fileName }: { attachmentId: number; fileName: string }) {
  const url = useAttachmentImageUrl(attachmentId);
  if (!url) {
    return <div className="aspect-square w-full rounded-lg bg-surface-elevated animate-pulse" />;
  }
  return <img src={url} alt={fileName} className="aspect-square w-full rounded-lg object-cover" />;
}

export function PropertyGallery({ propertyId }: PropertyGalleryProps) {
  const { t } = useTranslation('realEstate');
  const { images, isLoading } = usePropertyImages(propertyId);

  if (isLoading) {
    return (
      <div className="grid grid-cols-3 gap-2">
        {[...Array(3)].map((_, i) => (
          <div key={i} className="aspect-square rounded-lg bg-surface-elevated animate-pulse" />
        ))}
      </div>
    );
  }

  if (images.length === 0) {
    return (
      <div className="text-center py-8 text-text-secondary text-sm">
        <ImageOff className="h-8 w-8 mx-auto mb-2 text-text-muted" />
        <p>{t('propertyDetail.gallery.empty')}</p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <div className="grid grid-cols-3 gap-2">
        {images.map((img) => (
          <GalleryImage key={img.id} attachmentId={img.id} fileName={img.fileName} />
        ))}
      </div>
      <p className="text-xs text-text-tertiary">{t('propertyDetail.gallery.hint')}</p>
    </div>
  );
}
