/**
 * AssetCoverImage: attachment-backed cover for the physical asset card.
 * Priority: first image attachment → legacy `photoPath` → "No photo" placeholder.
 * Display only — uploads go through the Attachments tab in AssetDetailModal.
 */
import { Package } from 'lucide-react';
import { useEntityImages, useAttachmentImageUrl } from '@/hooks/useEntityImages';
import { AttachmentEntityType } from '@/types/attachment';
import type { Asset } from '@/types/asset';

export function AssetCoverImage({ asset }: { asset: Asset }) {
  const { images } = useEntityImages(AttachmentEntityType.ASSET, asset.id);
  const cover = images.length > 0 ? images[0] : null;
  const url = useAttachmentImageUrl(cover?.id ?? null);

  if (url) {
    return (
      <div className="rounded-lg overflow-hidden border border-border">
        <img
          src={url}
          alt={asset.name}
          className="w-full h-32 object-cover"
        />
      </div>
    );
  }

  if (asset.photoPath) {
    return (
      <div className="rounded-lg overflow-hidden border border-border">
        <img
          src={asset.photoPath}
          alt={asset.name}
          className="w-full h-32 object-cover"
        />
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center h-32 rounded-lg bg-surface-elevated border border-dashed border-border">
      <div className="text-center text-text-tertiary">
        <Package className="h-8 w-8 mx-auto mb-2 opacity-30" />
        <span className="text-xs">No photo</span>
      </div>
    </div>
  );
}
