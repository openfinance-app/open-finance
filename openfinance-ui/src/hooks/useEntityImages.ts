/**
 * Entity image support on top of the existing attachment system.
 * Lists image attachments for any entity type and resolves decrypted blob
 * object URLs for display (plain <img src> cannot send auth/encryption headers).
 */
import { useEffect, useState } from 'react';
import apiClient from '@/services/apiClient';
import { buildEncryptionHeaders } from '@/utils/encryption';
import { useAttachments } from '@/hooks/useAttachments';
import type { Attachment, AttachmentEntityType } from '@/types/attachment';

/** Image attachments of an entity (metadata only). Shares the ['attachments', filters] query key with the attachments tab, so uploads there auto-refresh covers/galleries. */
export function useEntityImages(
  entityType: AttachmentEntityType,
  entityId: number | null
) {
  const filters =
    entityId != null ? { entityType, entityId } : undefined;
  const { data: attachments, isLoading } = useAttachments(filters);
  const images: Attachment[] = (attachments ?? []).filter((a) => a.image);
  return { images, isLoading };
}

/** Decrypted object URL for an attachment; null while loading or on failure. */
export function useAttachmentImageUrl(attachmentId: number | null): string | null {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    if (attachmentId == null) return undefined;
    let cancelled = false;
    let objectUrl: string | null = null;
    (async () => {
      try {
        const response = await apiClient.get(`/attachments/${attachmentId}/download`, {
          responseType: 'blob',
          headers: buildEncryptionHeaders(),
        });
        if (cancelled) return;
        const blob = new Blob([response.data], { type: response.headers['content-type'] });
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      } catch {
        // Degrade to placeholder on failure
      }
    })();
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [attachmentId]);

  return url;
}
