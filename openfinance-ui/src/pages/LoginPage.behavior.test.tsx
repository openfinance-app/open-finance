import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import LoginPage from '@/pages/LoginPage';
const state = vi.hoisted(() => ({
  submit: vi.fn(),
  navigate: vi.fn(),
  encryption: true,
  failed: false,
  error: null as unknown,
  navigationState: null as unknown,
}));
vi.mock('@/hooks/useAuth', () => ({
  useLogin: () => ({
    mutateAsync: state.submit,
    isPending: false,
    isError: state.failed,
    error: state.error,
  }),
}));
vi.mock('@/hooks/useSecurityConfig', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useSecurityConfig')>()),
  useSecurityConfig: () => ({ data: { encryptionEnabled: state.encryption }, isError: false }),
}));
vi.mock('react-router', async importOriginal => ({
  ...(await importOriginal<typeof import('react-router')>()),
  useNavigate: () => state.navigate,
  useLocation: () => ({
    pathname: '/login',
    search: '',
    hash: '',
    key: 'login',
    state: state.navigationState,
  }),
}));
type LoginWindow = Window & {
  __loginTestHelper?: {
    submitLogin: (username: string, password: string, masterPassword?: string) => Promise<void>;
  };
};
const helper = () => (window as LoginWindow).__loginTestHelper!;
async function submitForm() {
  fireEvent.change(screen.getByLabelText(i18n.t('auth:login.username.label')), {
    target: { value: 'alice' },
  });
  fireEvent.change(screen.getByLabelText(i18n.t('auth:login.password.label')), {
    target: { value: 'LoginPass123!' },
  });
  if (state.encryption)
    fireEvent.change(screen.getByLabelText(i18n.t('auth:login.masterPassword.label')), {
      target: { value: 'MasterPass123!' },
    });
  fireEvent.click(screen.getByRole('button', { name: /sign in/i }));
  await waitFor(() => expect(state.submit).toHaveBeenCalled());
}
const errors = [
  [{ response: { data: { message: 'Locked account', error: 'ignored' } } }, 'Locked account'],
  [{ response: { data: { error: 'Invalid credentials' } } }, 'Invalid credentials'],
  [new Error('Connection failed'), 'Connection failed'],
  [{ response: {} }, null],
  [null, null],
] as const;

describe('Login error and navigation boundaries', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
    state.encryption = true;
    state.failed = false;
    state.error = null;
    state.navigationState = null;
    state.submit.mockResolvedValue({ onboardingComplete: true });
  });

  it.each([true, false])(
    'routes a form login according to onboarding completion %s',
    async complete => {
      state.submit.mockResolvedValue({ onboardingComplete: complete });
      state.navigationState = { from: { pathname: '/transactions' } };
      renderWithProviders(<LoginPage />);
      await submitForm();
      await waitFor(() =>
        expect(state.navigate).toHaveBeenCalledWith(complete ? '/transactions' : '/onboarding', {
          replace: true,
        })
      );
    }
  );

  it.each(['key', 'raw', 'empty'])(
    'shows a registration confirmation from %s navigation state',
    source => {
      state.navigationState =
        source === 'key'
          ? { messageKey: 'register.success', message: 'Ignore raw text' }
          : source === 'raw'
            ? { message: 'Registration confirmed' }
            : {};
      renderWithProviders(<LoginPage />);
      if (source === 'key') {
        expect(screen.getByText(i18n.t('auth:register.success'))).toBeInTheDocument();
        expect(screen.queryByText('Ignore raw text')).not.toBeInTheDocument();
      }
      if (source === 'raw') expect(screen.getByText('Registration confirmed')).toBeInTheDocument();
      if (source === 'empty')
        expect(screen.queryByText('Registration confirmed')).not.toBeInTheDocument();
    }
  );

  it.each(errors)(
    'uses a useful form-submission failure message for %j',
    async (error, message) => {
      state.submit.mockRejectedValueOnce(error);
      renderWithProviders(<LoginPage />);
      await submitForm();
      await screen.findByText(message ?? i18n.t('auth:login.failedDefault'));
      expect(state.navigate).not.toHaveBeenCalled();
      await submitForm();
      await waitFor(() =>
        expect(state.navigate).toHaveBeenCalledWith('/dashboard', { replace: true })
      );
      expect(
        screen.queryByText(message ?? i18n.t('auth:login.failedDefault'))
      ).not.toBeInTheDocument();
    }
  );

  it.each(errors.filter(([error]) => error != null))(
    'also shows asynchronous mutation failures for %j',
    (error, message) => {
      state.failed = true;
      state.error = error;
      renderWithProviders(<LoginPage />);
      expect(screen.getByText(message ?? i18n.t('auth:login.failedDefault'))).toBeInTheDocument();
    }
  );

  it.each([true, false])(
    'the development login entry point follows onboarding state %s and removes itself on unmount',
    async complete => {
      state.submit.mockResolvedValue({ onboardingComplete: complete });
      const view = renderWithProviders(<LoginPage />);
      await act(() => helper().submitLogin('alice', 'LoginPass123!', 'MasterPass123!'));
      expect(state.submit).toHaveBeenCalledWith({
        username: 'alice',
        password: 'LoginPass123!',
        masterPassword: 'MasterPass123!',
        rememberMe: false,
      });
      expect(state.navigate).toHaveBeenCalledWith(complete ? '/dashboard' : '/onboarding', {
        replace: true,
      });
      view.unmount();
      expect((window as LoginWindow).__loginTestHelper).toBeUndefined();
    }
  );

  it.each([true, false])(
    'the direct entry point sends omitted master credentials only when encryption is %s',
    async encrypted => {
      state.encryption = encrypted;
      renderWithProviders(<LoginPage />);
      await act(() => helper().submitLogin('alice', 'LoginPass123!'));
      expect(state.submit).toHaveBeenCalledWith({
        username: 'alice',
        password: 'LoginPass123!',
        rememberMe: false,
        ...(encrypted ? { masterPassword: '' } : {}),
      });
    }
  );

  it.each(errors)(
    'the direct entry point reports %j and permits a clean retry',
    async (error, message) => {
      state.submit.mockRejectedValueOnce(error);
      renderWithProviders(<LoginPage />);
      await act(() => helper().submitLogin('alice', 'LoginPass123!', 'MasterPass123!'));
      expect(screen.getByText(message ?? i18n.t('auth:login.failedDefault'))).toBeInTheDocument();
      expect(state.navigate).not.toHaveBeenCalled();
      await act(() => helper().submitLogin('alice', 'LoginPass123!', 'MasterPass123!'));
      expect(state.navigate).toHaveBeenCalledOnce();
      expect(
        screen.queryByText(message ?? i18n.t('auth:login.failedDefault'))
      ).not.toBeInTheDocument();
    }
  );
});
