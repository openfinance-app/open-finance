/**
 * ImagePreviewModal Component
 * Task 12.1.14: Add image preview modal
 *
 * Lightbox-style modal for previewing image attachments with navigation
 */
import { useState, useEffect, useCallback } from 'react';
import { X, ChevronLeft, ChevronRight, Download, ZoomIn, ZoomOut, RotateCw } from 'lucide-react';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { Dialog, DialogOverlay, DialogPortal } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { useDownloadAttachment, useFetchAttachmentBlob } from '@/hooks/useAttachments';
import { type Attachment } from '@/types/attachment';
import { cn } from '@/lib/utils';

interface ImagePreviewModalProps {
  /**
   * Array of image attachments to display
   */
  images: Attachment[];
  /**
   * Initial index to display
   */
  initialIndex: number;
  /**
   * Whether the modal is open
   */
  open: boolean;
  /**
   * Callback when modal should close
   */
  onClose: () => void;
}

export function ImagePreviewModal({ images, initialIndex, open, onClose }: ImagePreviewModalProps) {
  const [currentIndex, setCurrentIndex] = useState(initialIndex);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { downloadAttachment } = useDownloadAttachment();
  const fetchBlob = useFetchAttachmentBlob();

  const currentImage = images[currentIndex];

  /**
   * Load image blob and create object URL
   */
  const loadImage = useCallback(() => {
    if (!currentImage) return;

    setIsLoading(true);
    setError(null);

    fetchBlob.mutate(currentImage.id, {
      onSuccess: (blob: Blob) => {
        // Revoke previous URL to prevent memory leaks
        if (imageUrl) {
          URL.revokeObjectURL(imageUrl);
        }

        // Create new object URL for the image blob
        const url = URL.createObjectURL(blob);
        setImageUrl(url);
        setIsLoading(false);
      },
      onError: (err: any) => {
        console.error('Failed to load image:', err);
        setError('Failed to load image. Please try again.');
        setIsLoading(false);
      },
    });
  }, [currentImage, fetchBlob, imageUrl]);

  /**
   * Load image when current index changes
   */
  useEffect(() => {
    if (open && currentImage) {
      loadImage();
    }

    // Cleanup: revoke object URL when modal closes or image changes
    return () => {
      if (imageUrl) {
        URL.revokeObjectURL(imageUrl);
      }
    };
  }, [currentIndex, open]);

  /**
   * Navigate to previous image
   */
  const handlePrevious = useCallback(() => {
    setCurrentIndex(prev => (prev > 0 ? prev - 1 : images.length - 1));
    setZoom(1);
    setRotation(0);
  }, [images.length]);

  /**
   * Navigate to next image
   */
  const handleNext = useCallback(() => {
    setCurrentIndex(prev => (prev < images.length - 1 ? prev + 1 : 0));
    setZoom(1);
    setRotation(0);
  }, [images.length]);

  /**
   * Handle keyboard navigation
   */
  useEffect(() => {
    if (!open) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      switch (e.key) {
        case 'Escape':
          onClose();
          break;
        case 'ArrowLeft':
          handlePrevious();
          break;
        case 'ArrowRight':
          handleNext();
          break;
        case '+':
        case '=':
          setZoom(prev => Math.min(prev + 0.25, 3));
          break;
        case '-':
        case '_':
          setZoom(prev => Math.max(prev - 0.25, 0.5));
          break;
        case 'r':
        case 'R':
          setRotation(prev => (prev + 90) % 360);
          break;
        default:
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, onClose, handlePrevious, handleNext]);

  /**
   * Handle zoom in
   */
  const handleZoomIn = () => {
    setZoom(prev => Math.min(prev + 0.25, 3));
  };

  /**
   * Handle zoom out
   */
  const handleZoomOut = () => {
    setZoom(prev => Math.max(prev - 0.25, 0.5));
  };

  /**
   * Handle rotate
   */
  const handleRotate = () => {
    setRotation(prev => (prev + 90) % 360);
  };

  /**
   * Handle download current image
   */
  const handleDownload = () => {
    if (!currentImage) return;
    downloadAttachment(currentImage.id, currentImage.fileName);
  };

  /**
   * Reset state when modal closes
   */
  useEffect(() => {
    if (!open) {
      setZoom(1);
      setRotation(0);
      setError(null);
    }
  }, [open]);

  if (!currentImage) return null;

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogPortal>
        <DialogOverlay className="bg-black/90" />
        <DialogPrimitive.Content
          className={cn(
            'fixed inset-0 z-50 flex flex-col items-center justify-center',
            'data-[state=open]:animate-in data-[state=closed]:animate-out',
            'data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0'
          )}
        >
          {/* Header */}
          <div className="absolute top-0 left-0 right-0 flex items-center justify-between p-4 bg-gradient-to-b from-black/60 to-transparent">
            <div className="flex-1">
              <h2 className="text-white text-lg font-semibold truncate">{currentImage.fileName}</h2>
              <p className="text-white/70 text-sm">
                {currentIndex + 1} of {images.length}
              </p>
            </div>

            {/* Action buttons */}
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="icon"
                onClick={handleZoomOut}
                disabled={zoom <= 0.5}
                className="text-white hover:bg-white/20"
                title="Zoom out (-)"
              >
                <ZoomOut className="h-5 w-5" />
              </Button>

              <Button
                variant="ghost"
                size="icon"
                onClick={handleZoomIn}
                disabled={zoom >= 3}
                className="text-white hover:bg-white/20"
                title="Zoom in (+)"
              >
                <ZoomIn className="h-5 w-5" />
              </Button>

              <Button
                variant="ghost"
                size="icon"
                onClick={handleRotate}
                className="text-white hover:bg-white/20"
                title="Rotate (R)"
              >
                <RotateCw className="h-5 w-5" />
              </Button>

              <Button
                variant="ghost"
                size="icon"
                onClick={handleDownload}
                className="text-white hover:bg-white/20"
                title="Download"
              >
                <Download className="h-5 w-5" />
              </Button>

              <Button
                variant="ghost"
                size="icon"
                onClick={onClose}
                className="text-white hover:bg-white/20"
                title="Close (Esc)"
              >
                <X className="h-5 w-5" />
              </Button>
            </div>
          </div>

          {/* Image container */}
          <div className="flex-1 flex items-center justify-center w-full overflow-hidden p-16">
            {isLoading && (
              <div className="text-white text-center">
                <div className="animate-spin rounded-full h-12 w-12 border-4 border-white/30 border-t-white mx-auto mb-4" />
                <p>Loading image...</p>
              </div>
            )}

            {error && (
              <div className="text-white text-center">
                <p className="mb-4">{error}</p>
                <Button variant="primary" onClick={loadImage}>
                  Retry
                </Button>
              </div>
            )}

            {imageUrl && !isLoading && !error && (
              <img
                src={imageUrl}
                alt={currentImage.fileName}
                className="max-w-full max-h-full object-contain transition-transform duration-200"
                style={{
                  transform: `scale(${zoom}) rotate(${rotation}deg)`,
                }}
              />
            )}
          </div>

          {/* Navigation buttons */}
          {images.length > 1 && (
            <>
              <Button
                variant="ghost"
                size="icon"
                onClick={handlePrevious}
                className="absolute left-4 top-1/2 -translate-y-1/2 text-white hover:bg-white/20 h-12 w-12"
                title="Previous (←)"
              >
                <ChevronLeft className="h-8 w-8" />
              </Button>

              <Button
                variant="ghost"
                size="icon"
                onClick={handleNext}
                className="absolute right-4 top-1/2 -translate-y-1/2 text-white hover:bg-white/20 h-12 w-12"
                title="Next (→)"
              >
                <ChevronRight className="h-8 w-8" />
              </Button>
            </>
          )}

          {/* Keyboard shortcuts hint */}
          <div className="absolute bottom-4 left-1/2 -translate-x-1/2 text-white/50 text-xs text-center">
            <p>Use ← → to navigate • +/- to zoom • R to rotate • Esc to close</p>
          </div>
        </DialogPrimitive.Content>
      </DialogPortal>
    </Dialog>
  );
}
