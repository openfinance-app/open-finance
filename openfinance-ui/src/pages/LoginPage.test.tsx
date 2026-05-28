import '@testing-library/jest-dom';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { describe, it, beforeEach, vi, expect } from 'vitest';
import type { UseMutationResult } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import type { LoginRequest, LoginResponse } from '@/types/user';

// Mock useLogin hook - return value will be controlled in tests
vi.mock('@/hooks/useAuth', () => ({
  useLogin: vi.fn()
}));

import LoginPage from './LoginPage';
import { useLogin } from '@/hooks/useAuth';

import { renderWithProviders } from '@/test/test-utils';

describe('LoginPage', () => {
  const mockMutateAsync = vi.fn();
  const mockUseLogin = vi.mocked(useLogin);

  beforeEach(() => {
    vi.clearAllMocks();
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
      context: undefined
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

  it('should call useLogin mutateAsync on valid submit', async () => {
    renderWithProviders(<LoginPage />);

    fireEvent.input(screen.getByPlaceholderText('Enter your username'), { target: { value: 'alice' } });
    fireEvent.input(screen.getByPlaceholderText('••••••••'), { target: { value: 'Password1!' } });
    fireEvent.input(screen.getByPlaceholderText('••••••••••••'), { target: { value: 'MasterPass1!' } });

    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ username: 'alice' })));
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
      context: undefined
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
    const toggleButton = screen.getAllByRole('button').find(
      btn => btn.getAttribute('aria-label')?.toLowerCase().includes('show') &&
             btn.getAttribute('aria-label')?.toLowerCase().includes('password') &&
             !btn.getAttribute('aria-label')?.toLowerCase().includes('master')
    );
    expect(toggleButton).toBeTruthy();
    fireEvent.click(toggleButton!);
    expect(passwordInput).toHaveAttribute('type', 'text');
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

    const toggleButton = screen.getAllByRole('button').find(
      btn => btn.getAttribute('aria-label')?.toLowerCase().includes('master')
    );
    expect(toggleButton).toBeTruthy();
    fireEvent.click(toggleButton!);
    expect(masterPasswordInput).toHaveAttribute('type', 'text');
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

    fireEvent.input(screen.getByPlaceholderText('Enter your username'), { target: { value: 'alice' } });
    fireEvent.input(screen.getByPlaceholderText('••••••••'), { target: { value: 'Password1!' } });
    fireEvent.input(screen.getByPlaceholderText('••••••••••••'), { target: { value: 'MasterPass1!' } });
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ rememberMe: true })));
  });

  it('should render security notice section', () => {
    renderWithProviders(<LoginPage />);
    expect(screen.getAllByText(/secure|security/i).length).toBeGreaterThan(0);
  });

  it('should handle submit error and show fallback message', async () => {
    mockMutateAsync.mockRejectedValue(new Error('Network error'));
    renderWithProviders(<LoginPage />);

    fireEvent.input(screen.getByPlaceholderText('Enter your username'), { target: { value: 'alice' } });
    fireEvent.input(screen.getByPlaceholderText('••••••••'), { target: { value: 'Password1!' } });
    fireEvent.input(screen.getByPlaceholderText('••••••••••••'), { target: { value: 'MasterPass1!' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      const alert = screen.getByRole('alert');
      expect(alert).toBeInTheDocument();
    });
  });
});
