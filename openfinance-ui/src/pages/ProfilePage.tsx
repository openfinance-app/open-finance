/**
 * User Profile Page
 * Task 4.4.6: Document title management
 *
 * Allows users to view and update their profile information including:
 * - Upload / remove profile image
 * - View username (read-only), email, and account creation date
 * - Update email address
 *
 * Note: Password change has been moved to Security Settings
 */
import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { CheckCircle, AlertCircle, Loader2 } from 'lucide-react';
import { useTranslation, Trans } from 'react-i18next';
import { Link } from 'react-router';
import { ROUTES } from '@/constants/routes';
import { useLocale } from '@/context/LocaleContext';
import { updateProfileSchema, type UpdateProfileFormData } from '@/validators/authSchemas';
import { useGetProfile, useUpdateProfile } from '@/hooks/useAuth';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { PageHeader } from '@/components/layout/PageHeader';
import { ProfileImageUpload } from '@/components/settings/ProfileImageUpload';

export default function ProfilePage() {
  const { t } = useTranslation('settings');
  const { t: tErrors } = useTranslation('errors');
  const { locale } = useLocale();
  useDocumentTitle(t('profile.documentTitle'));
  const { data: profile, isLoading: isLoadingProfile, error: profileError } = useGetProfile();
  const updateProfile = useUpdateProfile();
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    reset,
  } = useForm<UpdateProfileFormData>({
    resolver: zodResolver(updateProfileSchema),
    defaultValues: {
      email: profile?.email || '',
      currentPassword: '',
    },
  });

  // Update form when profile loads
  useEffect(() => {
    if (profile) {
      reset({
        email: profile.email,
        currentPassword: '',
      });
    }
  }, [profile, reset]);

  const onSubmit = async (data: UpdateProfileFormData) => {
    setSuccessMessage(null);
    setInfoMessage(null);

    // Build request payload - only email update (password change is in Security Settings)
    const payload: { email?: string; currentPassword: string } = {
      currentPassword: data.currentPassword,
    };

    // Only include email if it changed
    if (data.email && data.email !== profile?.email) {
      payload.email = data.email;
    }

    // Validate that there's something to update
    if (!payload.email) {
      setInfoMessage(t('profile.noChanges'));
      return;
    }

    try {
      await updateProfile.mutateAsync(payload);
      setSuccessMessage(t('profile.updateSuccess'));
      // Reset password field after successful update
      reset({
        email: payload.email || profile?.email || '',
        currentPassword: '',
      });
    } catch (error) {
      // Error is handled by mutation's onError
      console.error('Failed to update profile:', error);
    }
  };

  if (isLoadingProfile) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-text-secondary">{tErrors('profile.loading')}</div>
      </div>
    );
  }

  if (profileError) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-error">
          {tErrors('profile.loadError')}
        </div>
      </div>
    );
  }

  if (!profile) {
    return null;
  }

  return (
    <div className="container mx-auto px-4 py-8 max-w-2xl">
      <PageHeader
        title={t('profile.title')}
        description={
          <Trans
            t={t}
            i18nKey="profile.description"
            components={{ 1: <Link to={ROUTES.SETTINGS} className="text-primary hover:underline" /> }}
          />
        }
      />

      {/* ── Profile Image Card ─────────────────────────────────────────── */}
      <div className="mt-8 bg-surface rounded-lg shadow-lg border border-border p-6">
        <h2 className="text-lg font-semibold text-text-primary mb-4">{t('profile.photoSection')}</h2>
        <ProfileImageUpload
          currentImage={profile.profileImage}
          username={profile.username}
        />
      </div>

      {/* ── Profile Details Form ───────────────────────────────────────── */}
      <div className="mt-6 bg-surface rounded-lg shadow-lg border border-border">
        <form noValidate onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-6">
          {/* Success Message */}
          {successMessage && (
            <div className="p-4 bg-success/10 border border-success/30 rounded-lg text-success flex items-center gap-2">
              <CheckCircle size={20} className="flex-shrink-0" />
              <span>{successMessage}</span>
            </div>
          )}

          {/* No-changes Info */}
          {infoMessage && (
            <div className="p-4 bg-primary/10 border border-primary/30 rounded-lg text-text-secondary flex items-center gap-2">
              <AlertCircle size={20} className="flex-shrink-0" />
              <span>{infoMessage}</span>
            </div>
          )}

          {/* Error Message */}
          {updateProfile.error && (
            <div className="p-4 bg-error/10 border border-error/30 rounded-lg text-error flex items-center gap-2">
              <AlertCircle size={20} className="flex-shrink-0" />
              <span>
                {(updateProfile.error.response?.data as { message?: string })?.message ||
                   tErrors('profile.updateError')}
              </span>
            </div>
          )}

          {/* Read-only Information */}
          <div className="space-y-4 pb-6 border-b border-border">
            <h2 className="text-lg font-semibold text-text-primary">{t('profile.accountInfoSection')}</h2>

            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1">
                {t('profile.username')}
              </label>
              <div className="p-3 bg-surface-elevated rounded-lg text-text-primary">
                {profile.username}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1">
                {t('profile.memberSince')}
              </label>
              <div className="p-3 bg-surface-elevated rounded-lg text-text-primary">
                {new Date(profile.createdAt).toLocaleDateString(locale, {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                })}
              </div>
            </div>
          </div>

          {/* Editable Fields */}
          <div className="space-y-4">
            <h2 className="text-lg font-semibold text-text-primary">{t('profile.updateSection')}</h2>

            {/* Email */}
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-text-primary mb-1">
                {t('profile.emailAddress')}
              </label>
              <input
                type="email"
                id="email"
                {...register('email')}
                className="w-full px-4 py-2 bg-surface-elevated border border-border rounded-lg text-text-primary placeholder-text-secondary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                placeholder={t('profile.emailPlaceholder')}
              />
              {errors.email && (
                <p className="mt-1 text-sm text-error">{t(errors.email.message!)}</p>
              )}
            </div>

            {/* Current Password - Always Required */}
            <div>
              <label htmlFor="currentPassword" className="block text-sm font-medium text-text-primary mb-1">
                {t('profile.currentPassword')} <span className="text-error">*</span>
              </label>
              <input
                type="password"
                id="currentPassword"
                autoComplete="current-password"
                {...register('currentPassword')}
                className="w-full px-4 py-2 bg-surface-elevated border border-border rounded-lg text-text-primary placeholder-text-secondary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                placeholder={t('profile.currentPasswordPlaceholder')}
              />
              {errors.currentPassword && (
                <p className="mt-1 text-sm text-error">{t(errors.currentPassword.message!)}</p>
              )}
              <p className="mt-1 text-xs text-text-secondary">
                {t('profile.currentPasswordHint')}
              </p>
            </div>
          </div>

          {/* Submit Button */}
          <div className="pt-6 border-t border-border">
            <button
              type="submit"
              disabled={isSubmitting || updateProfile.isPending}
              className="w-full px-6 py-3 bg-primary hover:bg-primary-hover disabled:bg-surface-elevated disabled:cursor-not-allowed disabled:text-text-secondary text-background font-semibold rounded-lg transition-colors duration-200 flex items-center justify-center gap-2"
            >
              {(isSubmitting || updateProfile.isPending) ? (
                <>
                  <Loader2 size={20} className="animate-spin" />
                  <span>{t('profile.updating')}</span>
                </>
              ) : (
                <span>{t('profile.updateButton')}</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
