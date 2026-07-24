/**
 * SecuritySettings - Security preferences component
 * 
 * Implements TASK-6.3.16:
 * - Change login password
 * - Change master password (with re-encryption warning)
 * - Two-factor authentication (future)
 * 
 * Requirements: REQ-6.3 (User Settings & Preferences)
 */
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Lock, Shield, AlertTriangle, Smartphone, Eye, EyeOff } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import apiClient from '@/services/apiClient';
import { useAuthContext } from '@/context/AuthContext';

// Validation schemas
const passwordChangeSchema = z.object({
  currentPassword: z.string().min(1, 'validation:currentPassword.required'),
  newPassword: z.string().min(8, 'validation:password.minLength'),
  confirmPassword: z.string(),
}).refine(data => data.newPassword === data.confirmPassword, {
  message: 'validation:password.mismatch',
  path: ['confirmPassword'],
});

const masterPasswordChangeSchema = z.object({
  currentMasterPassword: z.string().min(1, 'validation:masterPassword.required'),
  newMasterPassword: z.string().min(8, 'validation:masterPassword.minLengthShort'),
  confirmMasterPassword: z.string(),
}).refine(data => data.newMasterPassword === data.confirmMasterPassword, {
  message: 'validation:masterPassword.mismatch',
  path: ['confirmMasterPassword'],
});

type PasswordChangeFormData = z.infer<typeof passwordChangeSchema>;
type MasterPasswordChangeFormData = z.infer<typeof masterPasswordChangeSchema>;

// ---------------------------------------------------------------------------
// PasswordStrengthBar — inline strength indicator for new-password fields
// ---------------------------------------------------------------------------
function getPasswordStrength(password: string): { score: 0 | 1 | 2 | 3 | 4; label: string; color: string } {
  if (!password) return { score: 0, label: '', color: '' };
  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  if (/[A-Z]/.test(password) && /[a-z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[^A-Za-z0-9]/.test(password)) score++;
  const clamped = Math.min(score, 4) as 0 | 1 | 2 | 3 | 4;
  const map: Record<1 | 2 | 3 | 4, { label: string; color: string }> = {
    1: { label: 'Weak', color: 'bg-red-500' },
    2: { label: 'Fair', color: 'bg-yellow-500' },
    3: { label: 'Good', color: 'bg-blue-500' },
    4: { label: 'Strong', color: 'bg-green-500' },
  };
  if (clamped === 0) return { score: 0, label: '', color: '' };
  return { score: clamped, ...map[clamped] };
}

function PasswordStrengthBar({ password }: { password: string }) {
  const { score, label, color } = getPasswordStrength(password);
  if (!password) return null;
  return (
    <div className="mt-2">
      <div className="flex gap-1 mb-1">
        {[1, 2, 3, 4].map((i) => (
          <div
            key={i}
            className={`h-1 flex-1 rounded-full transition-all ${i <= score ? color : 'bg-border'}`}
          />
        ))}
      </div>
      <p className={`text-xs ${score <= 1 ? 'text-red-400' : score === 2 ? 'text-yellow-400' : score === 3 ? 'text-blue-400' : 'text-green-400'}`}>
        {label}
      </p>
    </div>
  );
}

/**
 * Security settings component with password management
 */
export function SecuritySettings() {
  useAuthContext();
  const { t } = useTranslation('settings');
  const [showPasswordForm, setShowPasswordForm] = useState(false);
  const [showMasterPasswordForm, setShowMasterPasswordForm] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Show/hide toggles for each password field
  const [showCurrentPw, setShowCurrentPw] = useState(false);
  const [showNewPw, setShowNewPw] = useState(false);
  const [showConfirmPw, setShowConfirmPw] = useState(false);
  const [showCurrentMasterPw, setShowCurrentMasterPw] = useState(false);
  const [showNewMasterPw, setShowNewMasterPw] = useState(false);
  const [showConfirmMasterPw, setShowConfirmMasterPw] = useState(false);

  // Password change form
  const {
    register: registerPassword,
    handleSubmit: handleSubmitPassword,
    formState: { errors: passwordErrors },
    reset: resetPasswordForm,
    watch: watchPassword,
  } = useForm<PasswordChangeFormData>({
    resolver: zodResolver(passwordChangeSchema),
  });
  const newPasswordValue = watchPassword('newPassword') ?? '';

  // Master password change form
  const {
    register: registerMasterPassword,
    handleSubmit: handleSubmitMasterPassword,
    formState: { errors: masterPasswordErrors },
    reset: resetMasterPasswordForm,
    watch: watchMasterPassword,
  } = useForm<MasterPasswordChangeFormData>({
    resolver: zodResolver(masterPasswordChangeSchema),
  });
  const newMasterPasswordValue = watchMasterPassword('newMasterPassword') ?? '';

  const handlePasswordChange = async (data: PasswordChangeFormData) => {
    setIsSubmitting(true);
    setSuccessMessage(null);
    setErrorMessage(null);

    try {
      await apiClient.put('/users/me/password', {
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      });

      setSuccessMessage(t('security.loginPassword.success'));
      resetPasswordForm();
      setShowPasswordForm(false);

      // Auto-clear success message after 5 seconds
      setTimeout(() => setSuccessMessage(null), 5000);
    } catch (error: any) {
      setErrorMessage(
        error.response?.data?.message ||
        t('security.masterPassword.failedPassword')
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleMasterPasswordChange = async (data: MasterPasswordChangeFormData) => {
    setIsSubmitting(true);
    setSuccessMessage(null);
    setErrorMessage(null);

    try {
      await apiClient.put('/users/me/master-password', {
        currentMasterPassword: data.currentMasterPassword,
        newMasterPassword: data.newMasterPassword,
      });

      setSuccessMessage(t('security.masterPassword.success'));
      resetMasterPasswordForm();
      setShowMasterPasswordForm(false);

      // Auto-clear success message after 8 seconds
      setTimeout(() => setSuccessMessage(null), 8000);
    } catch (error: any) {
      setErrorMessage(
        error.response?.data?.message ||
        t('security.masterPassword.failedMasterPassword')
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Section Header */}
      <div>
        <h2 className="text-xl font-semibold text-text-primary mb-2">{t('security.title')}</h2>
        <p className="text-text-secondary text-sm">{t('security.description')}</p>
      </div>

      {/* Success Message */}
      {successMessage && (
        <div className="bg-green-500/10 border border-green-500/20 rounded-lg p-4">
          <p className="text-green-400 text-sm flex items-center gap-2">
            <Shield className="h-4 w-4" />
            {successMessage}
          </p>
        </div>
      )}

      {/* Error Message */}
      {errorMessage && (
        <div className="bg-red-500/10 border border-red-500/20 rounded-lg p-4">
          <p className="text-red-400 text-sm flex items-center gap-2">
            <AlertTriangle className="h-4 w-4" />
            {errorMessage}
          </p>
        </div>
      )}

      {/* Change Login Password */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h3 className="text-lg font-semibold text-text-primary flex items-center gap-2">
              <Lock className="h-5 w-5 text-primary" />
              {t('security.loginPassword.title')}
            </h3>
            <p className="text-sm text-text-secondary mt-1">
              {t('security.loginPassword.description')}
            </p>
          </div>
          <button
            onClick={() => {
              setShowPasswordForm(!showPasswordForm);
              if (showPasswordForm) {
                resetPasswordForm();
                setErrorMessage(null);
              }
            }}
            className="text-primary hover:text-primary/80 text-sm font-medium transition-colors"
          >
            {showPasswordForm ? t('security.loginPassword.cancel') : t('security.loginPassword.change')}
          </button>
        </div>

        {showPasswordForm && (
          <form onSubmit={handleSubmitPassword(handlePasswordChange)} className="space-y-4 mt-4 pt-4 border-t border-border">
            {/* Current Password */}
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-2">
                {t('security.loginPassword.currentPassword')}
              </label>
              <div className="relative">
                <input
                  type={showCurrentPw ? 'text' : 'password'}
                  autoComplete="current-password"
                  {...registerPassword('currentPassword')}
                  className="w-full px-4 py-2 pr-10 bg-surface-elevated border border-border rounded-lg text-text-primary focus:outline-none focus:border-primary"
                  placeholder={t('security.loginPassword.currentPasswordPlaceholder')}
                />
                <button
                  type="button"
                  onClick={() => setShowCurrentPw((v) => !v)}
                  className="absolute inset-y-0 right-3 flex items-center text-text-muted hover:text-text-primary transition-colors"
                  aria-label={showCurrentPw ? 'Hide password' : 'Show password'}
                >
                  {showCurrentPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {passwordErrors.currentPassword && (
                <p className="text-red-400 text-sm mt-1">{t(passwordErrors.currentPassword.message!)}</p>
              )}
            </div>

            {/* New Password */}
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-2">
                {t('security.loginPassword.newPassword')}
              </label>
              <div className="relative">
                <input
                  type={showNewPw ? 'text' : 'password'}
                  autoComplete="new-password"
                  {...registerPassword('newPassword')}
                  className="w-full px-4 py-2 pr-10 bg-surface-elevated border border-border rounded-lg text-text-primary focus:outline-none focus:border-primary"
                  placeholder={t('security.loginPassword.newPasswordPlaceholder')}
                />
                <button
                  type="button"
                  onClick={() => setShowNewPw((v) => !v)}
                  className="absolute inset-y-0 right-3 flex items-center text-text-muted hover:text-text-primary transition-colors"
                  aria-label={showNewPw ? 'Hide password' : 'Show password'}
                >
                  {showNewPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              <PasswordStrengthBar password={newPasswordValue} />
              {passwordErrors.newPassword && (
                <p className="text-red-400 text-sm mt-1">{t(passwordErrors.newPassword.message!)}</p>
              )}
            </div>

            {/* Confirm New Password */}
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-2">
                {t('security.loginPassword.confirmPassword')}
              </label>
              <div className="relative">
                <input
                  type={showConfirmPw ? 'text' : 'password'}
                  autoComplete="new-password"
                  {...registerPassword('confirmPassword')}
                  className="w-full px-4 py-2 pr-10 bg-surface-elevated border border-border rounded-lg text-text-primary focus:outline-none focus:border-primary"
                  placeholder={t('security.loginPassword.confirmPasswordPlaceholder')}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPw((v) => !v)}
                  className="absolute inset-y-0 right-3 flex items-center text-text-muted hover:text-text-primary transition-colors"
                  aria-label={showConfirmPw ? 'Hide password' : 'Show password'}
                >
                  {showConfirmPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {passwordErrors.confirmPassword && (
                <p className="text-red-400 text-sm mt-1">{t(passwordErrors.confirmPassword.message!)}</p>
              )}
            </div>

            {/* Submit Button */}
            <div className="flex justify-end pt-2">
              <button
                type="submit"
                disabled={isSubmitting}
                className="px-6 py-2 bg-primary hover:bg-primary/90 text-white rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isSubmitting ? t('security.loginPassword.submitting') : t('security.loginPassword.submit')}
              </button>
            </div>
          </form>
        )}
      </div>

      {/* Change Master Password */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h3 className="text-lg font-semibold text-text-primary flex items-center gap-2">
              <Shield className="h-5 w-5 text-red-400" />
              {t('security.masterPassword.title')}
            </h3>
            <p className="text-sm text-text-secondary mt-1">
              {t('security.masterPassword.description')}
            </p>
          </div>
          <button
            onClick={() => {
              setShowMasterPasswordForm(!showMasterPasswordForm);
              if (showMasterPasswordForm) {
                resetMasterPasswordForm();
                setErrorMessage(null);
              }
            }}
            className="text-primary hover:text-primary/80 text-sm font-medium transition-colors"
          >
            {showMasterPasswordForm ? t('security.masterPassword.cancel') : t('security.masterPassword.change')}
          </button>
        </div>

        {/* Warning Box - Always Visible */}
        <div className="bg-red-500/10 border border-red-500/20 rounded-lg p-4 mb-4">
          <div className="flex items-start gap-3">
            <AlertTriangle className="h-5 w-5 text-red-400 flex-shrink-0 mt-0.5" />
            <div>
              <p className="text-red-400 font-semibold text-sm mb-1">{t('security.masterPassword.criticalWarningTitle')}</p>
              <p className="text-red-300 text-sm">
                {t('security.masterPassword.criticalWarningBody')}
              </p>
            </div>
          </div>
        </div>

        {showMasterPasswordForm && (
          <form onSubmit={handleSubmitMasterPassword(handleMasterPasswordChange)} className="space-y-4 pt-4 border-t border-border">
            {/* Current Master Password */}
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-2">
                {t('security.masterPassword.currentMasterPassword')}
              </label>
              <div className="relative">
                <input
                  type={showCurrentMasterPw ? 'text' : 'password'}
                  autoComplete="current-password"
                  {...registerMasterPassword('currentMasterPassword')}
                  className="w-full px-4 py-2 pr-10 bg-surface-elevated border border-border rounded-lg text-text-primary focus:outline-none focus:border-primary"
                  placeholder={t('security.masterPassword.currentMasterPasswordPlaceholder')}
                />
                <button
                  type="button"
                  onClick={() => setShowCurrentMasterPw((v) => !v)}
                  className="absolute inset-y-0 right-3 flex items-center text-text-muted hover:text-text-primary transition-colors"
                  aria-label={showCurrentMasterPw ? 'Hide password' : 'Show password'}
                >
                  {showCurrentMasterPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {masterPasswordErrors.currentMasterPassword && (
                <p className="text-red-400 text-sm mt-1">{t(masterPasswordErrors.currentMasterPassword.message!)}</p>
              )}
            </div>

            {/* New Master Password */}
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-2">
                {t('security.masterPassword.newMasterPassword')}
              </label>
              <div className="relative">
                <input
                  type={showNewMasterPw ? 'text' : 'password'}
                  autoComplete="new-password"
                  {...registerMasterPassword('newMasterPassword')}
                  className="w-full px-4 py-2 pr-10 bg-surface-elevated border border-border rounded-lg text-text-primary focus:outline-none focus:border-primary"
                  placeholder={t('security.masterPassword.newMasterPasswordPlaceholder')}
                />
                <button
                  type="button"
                  onClick={() => setShowNewMasterPw((v) => !v)}
                  className="absolute inset-y-0 right-3 flex items-center text-text-muted hover:text-text-primary transition-colors"
                  aria-label={showNewMasterPw ? 'Hide password' : 'Show password'}
                >
                  {showNewMasterPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              <PasswordStrengthBar password={newMasterPasswordValue} />
              {masterPasswordErrors.newMasterPassword && (
                <p className="text-red-400 text-sm mt-1">{t(masterPasswordErrors.newMasterPassword.message!)}</p>
              )}
            </div>

            {/* Confirm New Master Password */}
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-2">
                {t('security.masterPassword.confirmMasterPassword')}
              </label>
              <div className="relative">
                <input
                  type={showConfirmMasterPw ? 'text' : 'password'}
                  autoComplete="new-password"
                  {...registerMasterPassword('confirmMasterPassword')}
                  className="w-full px-4 py-2 pr-10 bg-surface-elevated border border-border rounded-lg text-text-primary focus:outline-none focus:border-primary"
                  placeholder={t('security.masterPassword.confirmMasterPasswordPlaceholder')}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmMasterPw((v) => !v)}
                  className="absolute inset-y-0 right-3 flex items-center text-text-muted hover:text-text-primary transition-colors"
                  aria-label={showConfirmMasterPw ? 'Hide password' : 'Show password'}
                >
                  {showConfirmMasterPw ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {masterPasswordErrors.confirmMasterPassword && (
                <p className="text-red-400 text-sm mt-1">{t(masterPasswordErrors.confirmMasterPassword.message!)}</p>
              )}
            </div>

            {/* Additional Warning */}
            <div className="bg-yellow-500/10 border border-yellow-500/20 rounded-lg p-3">
              <p className="text-yellow-400 text-xs">
                <strong>Important:</strong> {t('security.masterPassword.rememberNote')}
              </p>
            </div>

            {/* Submit Button */}
            <div className="flex justify-end pt-2">
              <button
                type="submit"
                disabled={isSubmitting}
                className="px-6 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isSubmitting ? t('security.masterPassword.submitting') : t('security.masterPassword.submit')}
              </button>
            </div>
          </form>
        )}
      </div>

      {/* Two-Factor Authentication (Future) */}
      <div className="bg-surface rounded-lg p-6 border border-border opacity-60">
        <div className="flex items-start justify-between">
          <div>
            <h3 className="text-lg font-semibold text-text-primary flex items-center gap-2">
              <Smartphone className="h-5 w-5 text-text-muted" />
              {t('security.twoFactor.title')}
            </h3>
            <p className="text-sm text-text-secondary mt-1">
              {t('security.twoFactor.description')}
            </p>
          </div>
          <div className="text-text-muted text-sm font-medium">
            {t('security.twoFactor.comingSoon')}
          </div>
        </div>

        <div className="mt-4 p-3 bg-blue-500/10 border border-blue-500/20 rounded-lg">
          <p className="text-xs text-blue-400">
            <span className="font-semibold">{t('security.twoFactor.comingSoon')}:</span> {t('security.twoFactor.comingSoonNote')}
          </p>
        </div>
      </div>
    </div>
  );
}
