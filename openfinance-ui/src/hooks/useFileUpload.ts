/**
 * File upload hooks
 * Task 7.1.8: Create useFileUpload hook
 * 
 * Provides React Query hooks for file upload operations
 */
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import type { AxiosProgressEvent } from 'axios';
import apiClient from '@/services/apiClient';
import type { FileUploadResponse, FileUploadError } from '@/types/import';

/**
 * Upload a file for transaction import
 * 
 * @example
 * const uploadFile = useFileUpload();
 * 
 * const handleUpload = (file: File) => {
 *   uploadFile.mutate(file, {
 *     onSuccess: (data) => {
 *       console.log('Upload successful:', data.uploadId);
 *     },
 *     onError: (error) => {
 *       console.error('Upload failed:', error.message);
 *     },
 *   });
 * };
 */
export function useFileUpload() {
  return useMutation<FileUploadResponse, FileUploadError, File>({
    mutationFn: async (file: File) => {
      // Create FormData for multipart upload
      const formData = new FormData();
      formData.append('file', file);

      // Upload with authorization header (no encryption key needed for file upload)
      const response = await apiClient.post<FileUploadResponse>(
        '/import/upload',
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        }
      );

      // Check if upload was successful
      if (response.data.status === 'INVALID' || response.data.status === 'ERROR') {
        throw {
          message: response.data.message,
          status: response.status,
        } as FileUploadError;
      }

      return response.data;
    },
  });
}

/**
 * Upload a file with progress tracking
 *
 * Tracks upload progress via Axios `onUploadProgress` and exposes the current
 * completion percentage (0-100). The progress resets to 0 when a new upload
 * starts and reaches 100 once the request body has been fully sent.
 *
 * @example
 * const { uploadFileWithProgress, uploadProgress } = useFileUploadWithProgress();
 *
 * uploadFileWithProgress.mutate(file, {
 *   onSuccess: (data) => {
 *     console.log('Upload complete:', data.uploadId);
 *   },
 * });
 */
export function useFileUploadWithProgress() {
  const [uploadProgress, setUploadProgress] = useState(0);

  const uploadMutation = useMutation<FileUploadResponse, FileUploadError, File>({
    mutationFn: async (file: File) => {
      setUploadProgress(0);

      const formData = new FormData();
      formData.append('file', file);

      const response = await apiClient.post<FileUploadResponse>(
        '/import/upload',
        formData,
        {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
          onUploadProgress: (event: AxiosProgressEvent) => {
            if (event.total) {
              setUploadProgress(Math.round((event.loaded * 100) / event.total));
            }
          },
        }
      );

      if (response.data.status === 'INVALID' || response.data.status === 'ERROR') {
        throw {
          message: response.data.message,
          status: response.status,
        } as FileUploadError;
      }

      setUploadProgress(100);
      return response.data;
    },
    onError: () => {
      setUploadProgress(0);
    },
  });

  return {
    uploadFileWithProgress: uploadMutation,
    uploadProgress,
  };
}
