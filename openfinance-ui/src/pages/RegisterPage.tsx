/**
 * RegisterPage - User registration form
 *
 * Implements TASK-1.2.8-1.2.12:
 * - React Hook Form with Zod validation
 * - Password strength indicator
 * - Loading and error states
 * - Dark theme styling (Finary-style)
 *
 * Requirements: REQ-2.1.1, REQ-2.1.2 (User Registration)
 */
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link } from 'react-router';
import { Lock, Mail, User, Shield, Eye, EyeOff } from 'lucide-react';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { PasswordStrength } from '@/components/PasswordStrength';
import { createRegisterSchema, type RegisterFormData } from '@/validators/authSchemas';
import { useRegister } from '@/hooks/useAuth';
import { resolveEncryptionEnabled, useSecurityConfig } from '@/hooks/useSecurityConfig';
import { LanguageSelector } from '@/components/settings/LanguageSelector';

const passwordRevealButtonClass =
  'absolute right-0 top-7 h-10 w-10 inline-flex items-center justify-center text-text-muted hover:text-text-primary transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-surface';

/**
 * RegisterPage component
 * Displays registration form with username, email, password, and master password fields
 */
export default function RegisterPage() {
  const { t } = useTranslation(['auth', 'validation']);
  const [showPassword, setShowPassword] = useState(false);
  const [showMasterPassword, setShowMasterPassword] = useState(false);
  const registerMutation = useRegister();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);
  const registerSchema = createRegisterSchema(encryptionEnabled);

  // Initialize react-hook-form with zod validation
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema) as any,
    mode: 'onChange', // Validate on change for immediate feedback on password match
  });

  // Watch password fields for strength indicator
  const password = watch('password');
  const masterPassword = watch('masterPassword');

  /**
   * Handle form submission
   * Maps form data to API request format
   */
  const onSubmit = async (data: RegisterFormData) => {
    try {
      await registerMutation.mutateAsync({
        username: data.username,
        email: data.email,
        password: data.password,
        ...(encryptionEnabled ? { masterPassword: data.masterPassword } : {}),
      });
    } catch {
      // Error is handled by the mutation's onError callback
    }
  };

  // Extract error message from API response (robust to different error shapes)
  const getErrorMessage = (): string | null => {
    if (!registerMutation.isError || !registerMutation.error) return null;
    const err: any = registerMutation.error;
    const apiMessage = err?.response?.data?.message || err?.response?.data?.error;
    if (err?.response?.status === 409) {
      return t('register.errors.conflict');
    }
    return apiMessage || err?.message || 'Registration failed. Please try again.';
  };

  const errorMessage = getErrorMessage();

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4 relative py-12">
      <div className="absolute top-4 right-4 z-10" role="region" aria-label="Language selection">
        <LanguageSelector />
      </div>
      <div className="w-full max-w-lg">
        {/* Header */}
        <div className="text-center mb-6">
          <h1 className="text-3xl font-bold text-text-primary mb-2">{t('register.title')}</h1>
          <p className="text-text-secondary">{t('register.subtitle')}</p>
        </div>

        {/* Registration Form */}
        <div className="bg-surface rounded-xl p-6 border border-border">
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            {/* Global Error Message */}
            {errorMessage && (
              <div
                className="bg-error/10 border border-error rounded-lg p-3 text-error text-sm"
                role="alert"
                aria-live="assertive"
              >
                {errorMessage}
              </div>
            )}

            {/* Username & Email Fields */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                id="username"
                {...register('username')}
                label={t('register.username.label')}
                type="text"
                placeholder={t('register.username.placeholder')}
                icon={<User className="h-5 w-5" />}
                error={errors.username?.message ? t(errors.username.message) : undefined}
                autoComplete="username"
              />

              <Input
                id="email"
                {...register('email')}
                label={t('register.email.label')}
                type="email"
                placeholder={t('register.email.placeholder')}
                icon={<Mail className="h-5 w-5" />}
                error={errors.email?.message ? t(errors.email.message) : undefined}
                autoComplete="email"
              />
            </div>

            {/* Password Fields */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-start">
              <div>
                <div className="relative">
                  <Input
                    id="password"
                    {...register('password')}
                    label={t('register.password.label')}
                    type={showPassword ? 'text' : 'password'}
                    placeholder="••••••••"
                    icon={<Lock className="h-5 w-5" />}
                    className="pr-10"
                    error={errors.password?.message ? t(errors.password.message) : undefined}
                    autoComplete="new-password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className={passwordRevealButtonClass}
                    aria-label={
                      showPassword ? t('register.password.hide') : t('register.password.show')
                    }
                    aria-pressed={showPassword}
                  >
                    {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
                <PasswordStrength password={password || ''} />
              </div>

              <Input
                id="confirmPassword"
                {...register('confirmPassword')}
                label={t('register.confirmPassword.label')}
                type={showPassword ? 'text' : 'password'}
                placeholder="••••••••"
                icon={<Lock className="h-5 w-5" />}
                error={
                  errors.confirmPassword?.message ? t(errors.confirmPassword.message) : undefined
                }
                autoComplete="new-password"
              />
            </div>

            {/* Master Password Fields */}
            {encryptionEnabled && (
              <div className="pt-4 border-t border-border">
                <div className="mb-3 text-xs text-text-secondary">
                  {t('register.masterPassword.helper')}
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-start">
                  <div>
                    <div className="relative">
                      <Input
                        id="masterPassword"
                        {...register('masterPassword')}
                        label={t('register.masterPassword.label')}
                        type={showMasterPassword ? 'text' : 'password'}
                        placeholder="••••••••••••"
                        icon={<Shield className="h-5 w-5 text-primary" />}
                        className="pr-10"
                        error={
                          errors.masterPassword?.message
                            ? t(errors.masterPassword.message)
                            : undefined
                        }
                        autoComplete="new-password"
                      />
                      <button
                        type="button"
                        onClick={() => setShowMasterPassword(!showMasterPassword)}
                        className={passwordRevealButtonClass}
                        aria-label={
                          showMasterPassword
                            ? t('register.masterPassword.hide')
                            : t('register.masterPassword.show')
                        }
                        aria-pressed={showMasterPassword}
                      >
                        {showMasterPassword ? (
                          <EyeOff className="h-5 w-5" />
                        ) : (
                          <Eye className="h-5 w-5" />
                        )}
                      </button>
                    </div>
                    <PasswordStrength password={masterPassword || ''} />
                  </div>

                  <Input
                    id="confirmMasterPassword"
                    {...register('confirmMasterPassword')}
                    label={t('register.confirmMasterPassword.label')}
                    type={showMasterPassword ? 'text' : 'password'}
                    placeholder="••••••••••••"
                    icon={<Shield className="h-5 w-5 text-primary" />}
                    error={
                      errors.confirmMasterPassword?.message
                        ? t(errors.confirmMasterPassword.message)
                        : undefined
                    }
                    autoComplete="new-password"
                  />
                </div>
              </div>
            )}

            {/* Submit Button */}
            <Button
              type="submit"
              variant="primary"
              size="lg"
              className="w-full"
              isLoading={isSubmitting || registerMutation.isPending}
              disabled={isSubmitting || registerMutation.isPending}
            >
              {isSubmitting || registerMutation.isPending
                ? t('register.submitting')
                : t('register.submit')}
            </Button>

            {/* Success Message */}
            {registerMutation.isSuccess && (
              <div className="bg-success/10 border border-success rounded-lg p-3 text-success text-sm text-center">
                {t('register.success')}
              </div>
            )}
          </form>

          {/* Login Link */}
          <div className="mt-5 text-center">
            <p className="text-text-secondary text-sm">
              {t('register.hasAccount')}{' '}
              <Link
                to="/login"
                className="text-primary hover:text-primary/90 font-medium transition-colors"
              >
                {t('register.signIn')}
              </Link>
            </p>
          </div>
        </div>

        {/* Security Notice */}
        {encryptionEnabled && (
          <div className="mt-5 bg-surface-elevated/50 rounded-lg p-4 border border-border/50">
            <div className="flex gap-3">
              <Shield className="h-5 w-5 text-primary flex-shrink-0 mt-0.5" />
              <div>
                <p className="text-sm text-text-primary font-medium mb-1">
                  {t('register.securityNote.title')}
                </p>
                <p className="text-xs text-text-secondary">
                  {t('register.securityNote.description')}
                </p>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
