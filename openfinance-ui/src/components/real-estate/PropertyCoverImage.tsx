/**
 * PropertyCard cover: first image attachment, or the existing gradient/icon placeholder.
 * Display only — uploads go through the Attachments tab in PropertyDetailView.
 */
import { usePropertyImages, useAttachmentImageUrl } from '@/hooks/usePropertyImages';

interface PropertyCoverImageProps {
  propertyId: number;
  placeholderIcon: React.ReactNode;
}

export function PropertyCoverImage({ propertyId, placeholderIcon }: PropertyCoverImageProps) {
  const { images } = usePropertyImages(propertyId);
  const cover = images.length > 0 ? images[0] : null;
  const url = useAttachmentImageUrl(cover?.id ?? null);

  return (
    <div className="mb-4 aspect-video rounded-lg overflow-hidden bg-gradient-to-br from-primary/20 to-primary/5 flex items-center justify-center">
      {url ? (
        <img src={url} alt="" className="h-full w-full object-cover" />
      ) : (
        <div className="text-primary/30 scale-150">{placeholderIcon}</div>
      )}
    </div>
  );
}
