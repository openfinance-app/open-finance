import '@testing-library/jest-dom';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, beforeEach, vi, expect } from 'vitest';
import type { UseMutationResult } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import type { LoginRequest, LoginResponse } from '@/types/user';

// Mock useLogin hook - return value will be controlled in tests
vi.mock('@/hooks/useAuth', () => ({
  useLogin: vi.fn(),
}));

vi.mock('@/hooks/useSecurityConfig', async () => {
  const actual = await vi.importActual<typeof import('@/hooks/useSecurityConfig')>(
    '@/hooks/useSecurityConfig'
  );

  return {
    ...actual,
    useSecurityConfig: vi.fn(),
  };
});

import LoginPage from './LoginPage';
import { useLogin } from '@/hooks/useAuth';
import { useSecurityConfig } from '@/hooks/useSecurityConfig';

import { renderWithProviders } from '@/test/test-utils';

describe('LoginPage', () => {
  const mockMutateAsync = vi.fn();
  const mockUseLogin = vi.mocked(useLogin);

  const mockSecurityConfig = (
    data: { encryptionEnabled: boolean } | undefined,
    isError = false
  ) => {
    vi.mocked(useSecurityConfig).mockReturnValue({
      data,
      isError,
      isLoading: false,
    } as ReturnType<typeof useSecurityConfig>);
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockMutateAsync.mockResolvedValue({ token: 'abc', userId: 1, onboardingComplete: true });
    mockSecurityConfig({ encryptionEnabled: true });
    // Reset to default behavior
    mockUseLogin.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
      isError: false,
      error: null,
      data: undefined,
      variables: undefined,
      isIdle: true,
      isSuccess: false,
      status: 'idle',
      mutate: vi.fn(),
      reset: vi.fn(),
      failureCount: 0,
      failureReason: null,
      submittedAt: 0,
      context: undefined,
    } as unknown as UseMutationResult<LoginResponse, AxiosError, LoginRequest>);
  });

  it('should render form and validation errors', async () => {
    renderWithProviders(<LoginPage />);

    const submit = screen.getByRole('button', { name: /sign in/i });

    // Submit empty form
    fireEvent.click(submit);

    await waitFor(() => {
      expect(screen.getByText(/username is required/i)).toBeTruthy();
      // There are two "password is required" messages (password and master password)
      // Use getAllByText instead
      const passwordErrors = screen.getAllByText(/password is required/i);
      expect(passwordErrors.length).toBeGreaterThanOrEqual(2);
    });
  });

  it('should call useLogin mutateAsync with master password when encryption is enabled', async () => {
    renderWithProviders(<LoginPage />);

    fireEvent.input(screen.getByPlaceholderText('Enter your username'), {
      target: { value: 'alice' },
    });
    fireEvent.input(screen.getByPlaceholderText('••••••••'), { target: { value: 'Password1!' } });
    fireEvent.input(screen.getByPlaceholderText('••••••••••••'), {
      target: { value: 'MasterPass1!' },
    });

    const consoleLogSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    try {
      fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

      await waitFor(() =>
        expect(mockMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            username: 'alice',
            password: 'Password1!',
            masterPassword: 'MasterPass1!',
            rememberMe: false,
          })
        )
      );
      expect(consoleLogSpy).not.toHaveBeenCalled();
    } finally {
      consoleLogSpy.mockRestore();
    }
  });

  it('should hide master password and submit without it when encryption is disabled', async () => {
    mockSecurityConfig({ encryptionEnabled: false });
    mockMutateAsync.mockResolvedValue({ token: 'abc', userId: 1, onboardingComplete: true });

    renderWithProviders(<LoginPage />);

    expect(screen.queryByPlaceholderText('••••••••••••')).not.toBeInTheDocument();
    fireEvent.input(screen.getByPlaceholderText('Enter your username'), {
      target: { value: 'alice' },
    });
    fireEvent.input(screen.getByPlaceholderText('••••••••'), { target: { value: 'Password1!' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(mockMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({ username: 'alice', password: 'Password1!', rememberMe: false })
      )
    );
    expect(mockMutateAsync.mock.calls[0][0]).not.toHaveProperty('masterPassword');
  });

  it('should fail closed and keep master password visible when security config is missing', () => {
    mockSecurityConfig(undefined);

    renderWithProviders(<LoginPage />);

    expect(screen.getByPlaceholderText('••••••••••••')).toBeInTheDocument();
  });

  it('should fail closed and keep master password visible when security config fails', () => {
    mockSecurityConfig(undefined, true);

    renderWithProviders(<LoginPage />);

    expect(screen.getByPlaceholderText('••••••••••••')).toBeInTheDocument();
  });

  it('should display API error message when mutation has error', async () => {
    // Make hook return an error state
    const error = { response: { data: { message: 'Invalid credentials' } } };
    mockUseLogin.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
      isError: true,
      error: error as AxiosError,
      data: undefined,
      variables: undefined,
      isIdle: false,
      isSuccess: false,
      status: 'error',
      mutate: vi.fn(),
      reset: vi.fn(),
      failureCount: 1,
      failureReason: error,
      submittedAt: Date.now(),
      context: undefined,
    } as unknown as UseMutationResult<LoginResponse, AxiosError, LoginRequest>);

    renderWithProviders(<LoginPage />);

    // Error should be visible immediately since isError is true
    await waitFor(() => {
      const alert = screen.getByRole('alert');
      expect(alert).toHaveTextContent(/invalid credentials/i);
    });
  });

  it('should toggle password visibility', async () => {
    renderWithProviders(<LoginPage />);
    const passwordInput = screen.getByPlaceholderText('••••••••');
    expect(passwordInput).toHaveAttribute('type', 'password');

    // The toggle button has aria-label from translation
    const toggleButton = screen
      .getAllByRole('button')
      .find(
        btn =>
          btn.getAttribute('aria-label')?.toLowerCase().includes('show') &&
          btn.getAttribute('aria-label')?.toLowerCase().includes('password') &&
          !btn.getAttribute('aria-label')?.toLowerCase().includes('master')
      );
    expect(toggleButton).toBeTruthy();
    expect(toggleButton).toHaveClass(
      'h-10',
      'w-10',
      'inline-flex',
      'items-center',
      'justify-center'
    );
    expect(toggleButton).toHaveClass(
      'focus-visible:outline-none',
      'focus-visible:ring-2',
      'focus-visible:ring-primary',
      'focus-visible:ring-offset-2',
      'focus-visible:ring-offset-surface'
    );
    expect(toggleButton).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(toggleButton!);
    expect(passwordInput).toHaveAttribute('type', 'text');
    expect(toggleButton).toHaveAttribute('aria-pressed', 'true');
  });

  it('should show loading state when pending', () => {
    mockUseLogin.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: true,
      isError: false,
      error: null,
      data: undefined,
      variables: undefined,
      isIdle: false,
      isSuccess: false,
      status: 'pending',
      mutate: vi.fn(),
      reset: vi.fn(),
      failureCount: 0,
      failureReason: null,
      submittedAt: Date.now(),
      context: undefined,
    } as unknown as UseMutationResult<LoginResponse, AxiosError, LoginRequest>);

    renderWithProviders(<LoginPage />);

    const submitButton = screen.getByRole('button', { name: /sign in|signing/i });
    expect(submitButton).toBeDisabled();
  });

  it('should render the login page title', () => {
    renderWithProviders(<LoginPage />);
    expect(screen.getByRole('heading')).toBeInTheDocument();
  });

  it('should toggle master password visibility', () => {
    renderWithProviders(<LoginPage />);
    const masterPasswordInput = screen.getByPlaceholderText('••••••••••••');
    expect(masterPasswordInput).toHaveAttribute('type', 'password');

    const toggleButton = screen
      .getAllByRole('button')
      .find(btn => btn.getAttribute('aria-label')?.toLowerCase().includes('master'));
    expect(toggleButton).toBeTruthy();
    expect(toggleButton).toHaveClass(
      'h-10',
      'w-10',
      'inline-flex',
      'items-center',
      'justify-center'
    );
    expect(toggleButton).toHaveClass(
      'focus-visible:outline-none',
      'focus-visible:ring-2',
      'focus-visible:ring-primary',
      'focus-visible:ring-offset-2',
      'focus-visible:ring-offset-surface'
    );
    expect(toggleButton).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(toggleButton!);
    expect(masterPasswordInput).toHaveAttribute('type', 'text');
    expect(toggleButton).toHaveAttribute('aria-pressed', 'true');
  });

  it('should render register link', () => {
    renderWithProviders(<LoginPage />);
    const registerLink = screen.getByRole('link', { name: /create one/i });
    expect(registerLink).toBeInTheDocument();
  });

  it('should render remember me checkbox', () => {
    renderWithProviders(<LoginPage />);
    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).toBeInTheDocument();
  });

  it('should include rememberMe in submit data', async () => {
    mockMutateAsync.mockResolvedValue({ token: 'abc', userId: 1, onboardingComplete: true });
    renderWithProviders(<LoginPage />);

    fireEvent.input(screen.getByPlaceholderText('Enter your username'), {
      target: { value: 'alice' },
    });
    fireEvent.input(screen.getByPlaceholderText('••••••••'), { target: { value: 'Password1!' } });
    fireEvent.input(screen.getByPlaceholderText('••••••••••••'), {
      target: { value: 'MasterPass1!' },
    });
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() =>
      expect(mockMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ rememberMe: true }))
    );
  });

  it('should render security notice section', () => {
    renderWithProviders(<LoginPage />);
    expect(screen.getAllByText(/secure|security/i).length).toBeGreaterThan(0);
  });

  it('should handle submit error and show fallback message', async () => {
    mockMutateAsync.mockRejectedValue(new Error('Network error'));
    renderWithProviders(<LoginPage />);

    fireEvent.input(screen.getByPlaceholderText('Enter your username'), {
      target: { value: 'alice' },
    });
    fireEvent.input(screen.getByPlaceholderText('••••••••'), { target: { value: 'Password1!' } });
    fireEvent.input(screen.getByPlaceholderText('••••••••••••'), {
      target: { value: 'MasterPass1!' },
    });
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    try {
      fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

      await waitFor(() => {
        const alert = screen.getByRole('alert');
        expect(alert).toBeInTheDocument();
      });
      expect(consoleErrorSpy).not.toHaveBeenCalled();
    } finally {
      consoleErrorSpy.mockRestore();
    }
  });
});
