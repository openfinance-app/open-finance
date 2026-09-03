/**
 * Property image support on top of the existing attachment system.
 * Thin REAL_ESTATE wrapper over the generic useEntityImages hook.
 */
import { AttachmentEntityType } from '@/types/attachment';
import { useEntityImages } from './useEntityImages';

export function usePropertyImages(propertyId: number | null) {
  return useEntityImages(AttachmentEntityType.REAL_ESTATE, propertyId);
}

export { useAttachmentImageUrl } from './useEntityImages';
