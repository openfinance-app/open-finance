/**
 * LoginPage - User login form
 *
 * Implements TASK-1.3.10:
 * - Form with username, password, master password
 * - Remember me checkbox (optional)
 * - Loading and error states
 * - Dark theme styling (Finary-style)
 *
 * Requirements: REQ-2.1.3, REQ-2.1.4 (User Login & Authentication)
 */
import { useTranslation } from 'react-i18next';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useLocation, useNavigate } from 'react-router';
import { Lock, User, Shield, Eye, EyeOff, AlertCircle } from 'lucide-react';
import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { createLoginSchema, type LoginFormData } from '@/validators/authSchemas';
import { useLogin } from '@/hooks/useAuth';
import { resolveEncryptionEnabled, useSecurityConfig } from '@/hooks/useSecurityConfig';
import { LanguageSelector } from '@/components/settings/LanguageSelector';

const passwordRevealButtonClass =
  'absolute right-0 top-7 h-10 w-10 inline-flex items-center justify-center text-text-muted hover:text-text-primary transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-surface';

/**
 * LoginPage component
 * Displays login form with username, password, and master password fields
 */
export default function LoginPage() {
  const { t } = useTranslation(['auth', 'validation']);
  const [showPassword, setShowPassword] = useState(false);
  const [showMasterPassword, setShowMasterPassword] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);
  const location = useLocation();
  const navigate = useNavigate();
  const loginMutation = useLogin();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);
  const loginSchema = createLoginSchema(encryptionEnabled);

  // Get success message from navigation state (e.g., after registration).
  // Prefer a translation key (messageKey) so the message renders in the active language;
  // fall back to a raw message string for backwards compatibility.
  const navState = location.state as { messageKey?: string; message?: string } | null;
  const successMessage = navState?.messageKey ? t(navState.messageKey) : navState?.message;

  // Initialize react-hook-form with zod validation
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema) as any,
    mode: 'onSubmit',
  });

  // Sync mutation error → local error state so the error banner always renders
  // on login failure regardless of the submission path.
  useEffect(() => {
    if (loginMutation.isError && loginMutation.error) {
      const error = loginMutation.error as any;
      const msg =
        error?.response?.data?.message ||
        error?.response?.data?.error ||
        error?.message ||
        t('login.failedDefault');
      setLoginError(msg);
    }
  }, [loginMutation.isError, loginMutation.error, t]);

  // Expose a testable submit helper in development so E2E automation can
  // trigger a real login attempt directly via the mutation (bypasses RHF DOM issues).
  useEffect(() => {
    if (import.meta.env.DEV) {
      (window as any).__loginTestHelper = {
        /**
         * Directly invokes the login mutation with the given credentials.
         * On success navigates to /dashboard; on failure sets loginError state
         * which will cause the error banner to render.
         */
        submitLogin: async (username: string, password: string, masterPassword?: string) => {
          setLoginError(null);
          try {
            const result = await loginMutation.mutateAsync({
              username,
              password,
              rememberMe: false,
              ...(encryptionEnabled ? { masterPassword: masterPassword ?? '' } : {}),
            });
            if (!result.onboardingComplete) {
              navigate('/onboarding', { replace: true });
            } else {
              navigate('/dashboard', { replace: true });
            }
          } catch (error: any) {
            const msg =
              error?.response?.data?.message ||
              error?.response?.data?.error ||
              error?.message ||
              t('login.failedDefault');
            setLoginError(msg);
          }
        },
      };
      return () => {
        delete (window as any).__loginTestHelper;
      };
    }
  }, [encryptionEnabled, loginMutation, navigate, t]);

  /**
   * Handle form submission
   * Maps form data to API request format
   */
  const handleFormSubmit = async (data: LoginFormData) => {
    // Clear any previous errors
    setLoginError(null);

    try {
      const loginResult = await loginMutation.mutateAsync({
        username: data.username,
        password: data.password,
        rememberMe: data.rememberMe || false,
        ...(encryptionEnabled ? { masterPassword: data.masterPassword } : {}),
      });
      // Redirect to onboarding for first-time users; otherwise go to the
      // originally requested page or the dashboard.
      if (!loginResult.onboardingComplete) {
        navigate('/onboarding', { replace: true });
      } else {
        const from = (location.state as any)?.from?.pathname || '/dashboard';
        navigate(from, { replace: true });
      }
    } catch (error: any) {
      // Extract error message and store in state for display
      const errorMessage =
        error?.response?.data?.message ||
        error?.response?.data?.error ||
        error?.message ||
        t('login.failedDefault');

      setLoginError(errorMessage);
    }
  };

  // Extract error message from API response or local state
  const getErrorMessage = (): string | null => {
    // First check local error state (set explicitly in catch block)
    if (loginError) {
      return loginError;
    }

    // Fallback to mutation error state (may not persist across re-renders)
    if (loginMutation.isError && loginMutation.error) {
      const error = loginMutation.error as any;
      return (
        error?.response?.data?.message ||
        error?.response?.data?.error ||
        error?.message ||
        t('login.failedDefault')
      );
    }
    return null;
  };

  const errorMessage = getErrorMessage();

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4 relative">
      <div className="absolute top-4 right-4 z-10" role="region" aria-label="Language selection">
        <LanguageSelector />
      </div>
      <div className="w-full max-w-md">
        {/* Header */}
        <div className="text-center mb-6">
          <h1 className="text-3xl font-bold text-text-primary mb-2">{t('login.title')}</h1>
          <p className="text-text-secondary">{t('login.subtitle')}</p>
        </div>

        {/* Login Form */}
        <div className="bg-surface rounded-xl p-6 border border-border">
          <form onSubmit={handleSubmit(handleFormSubmit)} className="space-y-4">
            {/* Success Message (e.g., after registration) */}
            {successMessage && (
              <div className="bg-success/10 border border-success rounded-lg p-3 text-success text-sm flex gap-2">
                <AlertCircle className="h-5 w-5 flex-shrink-0" />
                <span>{successMessage}</span>
              </div>
            )}

            {/* Global Error Message */}
            {errorMessage && (
              <div
                className="bg-error/10 border border-error rounded-lg p-3 text-error text-sm flex gap-2"
                role="alert"
                aria-live="assertive"
                data-testid="login-error-message"
              >
                <AlertCircle className="h-5 w-5 flex-shrink-0" aria-hidden="true" />
                <span>{errorMessage}</span>
              </div>
            )}

            {/* Username Field */}
            <div>
              <Input
                id="username"
                {...register('username')}
                label={t('login.username.label')}
                type="text"
                placeholder={t('login.username.placeholder')}
                icon={<User className="h-5 w-5" />}
                error={errors.username?.message ? t(errors.username.message) : undefined}
                autoComplete="username"
                autoFocus
              />
            </div>

            {/* Password Field */}
            <div>
              <div className="relative">
                <Input
                  id="password"
                  {...register('password')}
                  label={t('login.password.label')}
                  type={showPassword ? 'text' : 'password'}
                  placeholder="••••••••"
                  icon={<Lock className="h-5 w-5" />}
                  className="pr-10"
                  error={errors.password?.message ? t(errors.password.message) : undefined}
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className={passwordRevealButtonClass}
                  aria-label={showPassword ? t('login.password.hide') : t('login.password.show')}
                  aria-pressed={showPassword}
                >
                  {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            {/* Master Password Field */}
            {encryptionEnabled && (
              <div>
                <div className="relative">
                  <Shield
                    className="absolute left-3 top-10 h-5 w-5 text-primary"
                    aria-hidden="true"
                  />
                  <Input
                    id="masterPassword"
                    {...register('masterPassword')}
                    label={t('login.masterPassword.label')}
                    type={showMasterPassword ? 'text' : 'password'}
                    placeholder="••••••••••••"
                    className="pl-10 pr-10"
                    error={
                      errors.masterPassword?.message ? t(errors.masterPassword.message) : undefined
                    }
                    autoComplete="current-password"
                    helperText={t('login.masterPassword.helper')}
                  />
                  <button
                    type="button"
                    onClick={() => setShowMasterPassword(!showMasterPassword)}
                    className={passwordRevealButtonClass}
                    aria-label={
                      showMasterPassword
                        ? t('login.masterPassword.hide')
                        : t('login.masterPassword.show')
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
              </div>
            )}

            {/* Remember Me Checkbox */}
            <div className="flex items-start space-x-2">
              <input
                {...register('rememberMe')}
                type="checkbox"
                id="remember-me"
                data-testid="remember-me-checkbox"
                className="h-4 w-4 mt-0.5 rounded border-border bg-surface text-primary focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-surface cursor-pointer"
              />
              <div className="flex-1">
                <label
                  htmlFor="remember-me"
                  className="text-sm text-text-primary cursor-pointer select-none"
                >
                  {t('login.rememberMe.label')}
                </label>
                <p className="text-xs text-text-secondary mt-0.5">
                  {t('login.rememberMe.warning')}
                </p>
              </div>
            </div>

            {/* Submit Button */}
            <Button
              type="submit"
              variant="primary"
              size="lg"
              className="w-full"
              isLoading={isSubmitting || loginMutation.isPending}
              disabled={isSubmitting || loginMutation.isPending}
            >
              {isSubmitting || loginMutation.isPending ? t('login.submitting') : t('login.submit')}
            </Button>
          </form>

          {/* Register Link */}
          <div className="mt-5 text-center">
            <p className="text-text-secondary text-sm">
              {t('login.noAccount')}{' '}
              <Link
                to="/register"
                className="text-primary hover:text-primary/90 font-medium transition-colors"
              >
                {t('login.createAccount')}
              </Link>
            </p>
          </div>
        </div>

        {/* Security Notice */}
        {encryptionEnabled && (
          <div className="mt-5 bg-surface-elevated/50 rounded-lg p-4 border border-border/50">
            <div className="flex gap-3">
              <Shield className="h-5 w-5 text-primary flex-shrink-0 mt-0.5" aria-hidden="true" />
              <div>
                <p className="text-sm text-text-primary font-medium mb-1">
                  {t('login.secureLogin.title')}
                </p>
                <p className="text-xs text-text-secondary">{t('login.secureLogin.description')}</p>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
