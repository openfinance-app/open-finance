/**
 * ProfileImageUpload Component
 *
 * Provides a clean, accessible profile image uploader for the Profile Settings page.
 * The camera badge and "Upload photo" button are both <label> elements wired to the
 * file input via htmlFor — this guarantees the native file picker opens on click
 * regardless of browser security restrictions on programmatic .click() calls.
 */
import { useState, type ChangeEvent } from 'react';
import { PROFILE_IMAGE_SUCCESS_MESSAGE_DURATION_MS } from '@/constants/timing';
import { Camera, Trash2, Loader2, AlertCircle, CheckCircle } from 'lucide-react';
import { useUploadProfileImage, useDeleteProfileImage } from '@/hooks/useAuth';
import { cn } from '@/lib/utils';
import { useTranslation } from 'react-i18next';

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
const MAX_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

interface ProfileImageUploadProps {
  /** Current profile image as a Base64 data URL, or null/undefined for no image. */
  currentImage?: string | null;
  /** Username used to generate the initials fallback avatar. */
  username: string;
}

/** Returns 1-2 character initials extracted from the username. */
function getInitials(username: string): string {
  const parts = username.trim().split(/[\s._-]+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/**
 * Renders children as a button-like element with a transparent <input type="file">
 * stretched over it. The native input receives the real pointer event directly,
 * which satisfies browser security requirements for opening the file picker.
 */
function FilePickerButton({
  onChange,
  disabled,
  className,
  ariaLabel,
  children,
}: {
  onChange: (e: ChangeEvent<HTMLInputElement>) => void;
  disabled?: boolean;
  className?: string;
  ariaLabel?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={cn('relative overflow-hidden', className)}>
      {children}
      <input
        type="file"
        accept={ALLOWED_TYPES.join(',')}
        onChange={onChange}
        disabled={disabled}
        className="absolute inset-0 w-full h-full opacity-0 cursor-pointer disabled:cursor-not-allowed"
        // Reset value so the same file can be re-selected after an error
        onClick={(e) => { (e.target as HTMLInputElement).value = ''; }}
        aria-label={ariaLabel}
      />
    </div>
  );
}

export function ProfileImageUpload({ currentImage, username }: ProfileImageUploadProps) {
  const { t } = useTranslation('settings');
  const [preview, setPreview] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const uploadMutation = useUploadProfileImage();
  const deleteMutation = useDeleteProfileImage();

  const displayImage = preview ?? currentImage;
  const isLoading = uploadMutation.isPending || deleteMutation.isPending;
  const initials = getInitials(username);

  // ── Validation ───────────────────────────────────────────────────────────────

  function validateFile(file: File): string | null {
    if (!ALLOWED_TYPES.includes(file.type)) {
      return t('profile.unsupportedFileType');
    }
    if (file.size > MAX_SIZE_BYTES) {
      return t('profile.imageTooLarge', { size: (file.size / 1024 / 1024).toFixed(1) });
    }
    return null;
  }

  // ── File handling ────────────────────────────────────────────────────────────

  function handleFile(file: File) {
    setValidationError(null);
    setSuccessMessage(null);

    const error = validateFile(file);
    if (error) {
      setValidationError(error);
      return;
    }

    // Show preview immediately while uploading
    const reader = new FileReader();
    reader.onload = (e) => setPreview(e.target?.result as string);
    reader.readAsDataURL(file);

    uploadMutation.mutate(file, {
      onSuccess: () => {
        setPreview(null); // server response reflected via React Query cache
        setSuccessMessage(t('profile.uploadSuccess'));
        setTimeout(() => setSuccessMessage(null), PROFILE_IMAGE_SUCCESS_MESSAGE_DURATION_MS);
      },
      onError: (err) => {
        setPreview(null);
        const msg =
          (err.response?.data as { message?: string })?.message ||
          t('profile.uploadError');
        setValidationError(msg);
      },
    });
  }

  const handleInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleFile(file);
  };

  // ── Delete handler ───────────────────────────────────────────────────────────

  const handleDelete = () => {
    setValidationError(null);
    setSuccessMessage(null);
    setPreview(null);
    deleteMutation.mutate(undefined, {
      onSuccess: () => {
        setSuccessMessage(t('profile.removeSuccess'));
        setTimeout(() => setSuccessMessage(null), PROFILE_IMAGE_SUCCESS_MESSAGE_DURATION_MS);
      },
      onError: (err) => {
        const msg =
          (err.response?.data as { message?: string })?.message ||
          t('profile.removeError');
        setValidationError(msg);
      },
    });
  };

  return (
    <div className="space-y-4">
      {/* ── Avatar + controls row ──────────────────────────────────────────── */}
      <div className="flex items-start gap-6">
        {/* Avatar with camera badge */}
        <div className="relative flex-shrink-0">
          <div
            className={cn(
              'w-24 h-24 rounded-full overflow-hidden ring-2 ring-offset-2 ring-offset-background',
              isLoading ? 'ring-primary/50' : 'ring-border'
            )}
          >
            {displayImage ? (
              <img
                src={displayImage}
                alt={`${username}'s profile`}
                className="w-full h-full object-cover"
              />
            ) : (
              <div className="w-full h-full bg-primary flex items-center justify-center select-none">
                <span className="text-2xl font-bold text-background">{initials}</span>
              </div>
            )}

            {/* Loading overlay */}
            {isLoading && (
              <div className="absolute inset-0 bg-background/60 flex items-center justify-center rounded-full">
                <Loader2 size={28} className="animate-spin text-primary" />
              </div>
            )}
          </div>

          {/* Camera badge with transparent file input overlaid on top */}
          <FilePickerButton
            onChange={handleInputChange}
            disabled={isLoading}
            ariaLabel={t('profile.chooseProfilePhoto')}
            className={cn(
              'absolute bottom-0 right-0',
              'w-8 h-8 rounded-full',
              'bg-primary hover:bg-primary-hover',
              'flex items-center justify-center',
              'ring-2 ring-offset-1 ring-offset-background ring-primary',
              'transition-colors duration-150',
              isLoading && 'opacity-50 pointer-events-none'
            )}
          >
            <Camera size={14} className="text-background pointer-events-none" />
          </FilePickerButton>
        </div>

        {/* Right-side info + action buttons */}
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-text-primary mb-1">{t('profile.photoSection')}</p>
          <p className="text-xs text-text-secondary leading-relaxed mb-3">
            {t('profile.uploadHint')}
          </p>

          <div className="flex flex-wrap gap-2">
            {(displayImage || currentImage) && (
              <button
                type="button"
                onClick={handleDelete}
                disabled={isLoading}
                className={cn(
                  'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium',
                  'border border-border text-text-secondary hover:text-error hover:border-error',
                  'transition-colors duration-150',
                  'disabled:opacity-50 disabled:cursor-not-allowed'
                )}
              >
                <Trash2 size={14} />
                {deleteMutation.isPending ? t('profile.removing') : t('profile.removePhoto')}
              </button>
            )}
          </div>
        </div>
      </div>

      {/* ── Status messages ────────────────────────────────────────────────── */}
      {validationError && (
        <div
          role="alert"
          className="flex items-start gap-2 p-3 rounded-lg bg-error/10 border border-error/30 text-error text-sm"
        >
          <AlertCircle size={16} className="flex-shrink-0 mt-0.5" />
          <span>{validationError}</span>
        </div>
      )}

      {successMessage && (
        <div
          role="status"
          className="flex items-center gap-2 p-3 rounded-lg bg-success/10 border border-success/30 text-success text-sm"
        >
          <CheckCircle size={16} className="flex-shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}
    </div>
  );
}
