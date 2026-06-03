/**
 * Attachment management hooks
 * Task 12.1.13: Create useAttachments hook
 * 
 * Provides React Query hooks for attachment CRUD operations
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type {
  Attachment,
  AttachmentUploadRequest,
  AttachmentFilters,
  StorageStats,
  UploadProgress
} from '@/types/attachment';
import { useState } from 'react';

/**
 * Fetch attachments for an entity
 */
export function useAttachments(filters?: AttachmentFilters) {
  return useQuery<Attachment[]>({
    queryKey: ['attachments', filters],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters?.entityType) params.append('entityType', filters.entityType);
      if (filters?.entityId) params.append('entityId', filters.entityId.toString());

      const queryString = params.toString();
      const url = queryString ? `/attachments?${queryString}` : '/attachments';

      const response = await apiClient.get<Attachment[]>(url);
      return response.data;
    },
    enabled: filters !== undefined, // Only fetch if filters are provided
  });
}

/**
 * Fetch single attachment metadata by ID
 */
export function useAttachment(attachmentId: number | null) {
  return useQuery<Attachment>({
    queryKey: ['attachments', attachmentId],
    queryFn: async () => {
      if (!attachmentId) throw new Error('Attachment ID is required');

      const response = await apiClient.get<Attachment>(`/attachments/${attachmentId}`);
      return response.data;
    },
    enabled: attachmentId !== null,
  });
}

/**
 * Fetch storage statistics
 */
export function useStorageStats() {
  return useQuery<StorageStats>({
    queryKey: ['attachments', 'stats'],
    queryFn: async () => {
      const response = await apiClient.get<StorageStats>('/attachments/stats');
      return response.data;
    },
  });
}

/**
 * Upload attachment with progress tracking
 */
export function useUploadAttachment() {
  const queryClient = useQueryClient();
  const [uploadProgress, setUploadProgress] = useState<UploadProgress | null>(null);

  const mutation = useMutation({
    mutationFn: async (request: AttachmentUploadRequest) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found. Please log in again.');
      }

      const formData = new FormData();
      formData.append('file', request.file);
      formData.append('entityType', request.entityType);
      formData.append('entityId', request.entityId.toString());
      if (request.description) {
        formData.append('description', request.description);
      }

      const response = await apiClient.post<Attachment>('/attachments', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
          'X-Encryption-Session': encryptionKey,
        },
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const percentage = Math.round((progressEvent.loaded * 100) / progressEvent.total);
            setUploadProgress({
              loaded: progressEvent.loaded,
              total: progressEvent.total,
              percentage,
            });
          }
        },
      });

      return response.data;
    },
    onSuccess: (_data, variables) => {
      // Invalidate attachments list for this entity
      queryClient.invalidateQueries({
        queryKey: ['attachments', {
          entityType: variables.entityType,
          entityId: variables.entityId
        }]
      });
      // Also invalidate all attachments query
      queryClient.invalidateQueries({ queryKey: ['attachments'] });
      // Invalidate storage stats
      queryClient.invalidateQueries({ queryKey: ['attachments', 'stats'] });
      // Reset upload progress
      setUploadProgress(null);
    },
    onError: () => {
      // Reset upload progress on error
      setUploadProgress(null);
    },
  });

  return {
    ...mutation,
    uploadProgress,
  };
}

/**
 * Download attachment (triggers browser download)
 */
export function useDownloadAttachment() {
  const [isDownloading, setIsDownloading] = useState(false);

  const downloadAttachment = async (attachmentId: number, fileName: string) => {
    try {
      setIsDownloading(true);

      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found. Please log in again.');
      }

      const response = await apiClient.get(`/attachments/${attachmentId}/download`, {
        responseType: 'blob',
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });

      // Create blob URL and trigger download
      const blob = new Blob([response.data], { type: response.headers['content-type'] });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);

      return true;
    } catch (error) {
      console.error('Download failed:', error);
      throw error;
    } finally {
      setIsDownloading(false);
    }
  };

  return {
    downloadAttachment,
    isDownloading,
  };
}

/**
 * Fetch attachment blob (for preview)
 */
export function useFetchAttachmentBlob() {
  return useMutation({
    mutationFn: async (attachmentId: number) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found. Please log in again.');
      }

      const response = await apiClient.get(`/attachments/${attachmentId}/download`, {
        responseType: 'blob',
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });

      return new Blob([response.data], { type: response.headers['content-type'] });
    },
  });
}

/**
 * Delete attachment
 */
export function useDeleteAttachment() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (attachmentId: number) => {
      await apiClient.delete(`/attachments/${attachmentId}`);
    },
    onSuccess: () => {
      // Invalidate all attachment queries
      queryClient.invalidateQueries({ queryKey: ['attachments'] });
    },
  });
}

/**
 * Combined hook for all attachment operations
 */
export function useAttachmentOperations(entityType?: string, entityId?: number) {
  const filters = entityType && entityId ? { entityType: entityType as any, entityId } : undefined;
  const attachments = useAttachments(filters);
  const upload = useUploadAttachment();
  const download = useDownloadAttachment();
  const deleteAttachment = useDeleteAttachment();
  const stats = useStorageStats();

  return {
    // List
    attachments: attachments.data || [],
    isLoading: attachments.isLoading,
    isError: attachments.isError,
    error: attachments.error,

    // Upload
    upload: upload.mutate,
    uploadAsync: upload.mutateAsync,
    isUploading: upload.isPending,
    uploadProgress: upload.uploadProgress,
    uploadError: upload.error,

    // Download
    download: download.downloadAttachment,
    isDownloading: download.isDownloading,

    // Delete
    deleteAttachment: deleteAttachment.mutate,
    deleteAsync: deleteAttachment.mutateAsync,
    isDeleting: deleteAttachment.isPending,
    deleteError: deleteAttachment.error,

    // Stats
    stats: stats.data,
    isLoadingStats: stats.isLoading,
  };
}
