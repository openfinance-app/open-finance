import '@testing-library/jest-dom';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderWithProviders } from '@/test/test-utils';

// Mock useRegister hook
const mutateAsync = vi.fn();
const useRegisterMock = vi.fn(() => ({
  mutateAsync,
  isPending: false,
  isError: false,
  isSuccess: false,
  error: null,
}));
vi.mock('@/hooks/useAuth', () => ({ useRegister: () => useRegisterMock() }));

vi.mock('@/hooks/useSecurityConfig', async () => {
  const actual = await vi.importActual<typeof import('@/hooks/useSecurityConfig')>(
    '@/hooks/useSecurityConfig'
  );

  return {
    ...actual,
    useSecurityConfig: vi.fn(),
  };
});

import RegisterPage from './RegisterPage';
import { useSecurityConfig } from '@/hooks/useSecurityConfig';

function renderPage() {
  return renderWithProviders(<RegisterPage />);
}

describe('RegisterPage', () => {
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
    vi.resetAllMocks();
    mockSecurityConfig({ encryptionEnabled: true });
  });

  it('should display validation errors and prevent submission', async () => {
    await renderPage();

    // Submit without filling fields - use actual translated text "Create Account"
    const submitButton = screen.getByRole('button', { name: /create account/i });
    fireEvent.click(submitButton);

    // Expect multiple errors to be present
    await waitFor(() =>
      expect(screen.getByText('Username must be at least 3 characters')).toBeInTheDocument()
    );
    expect(screen.getByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password must be at least 8 characters')).toBeInTheDocument();

    expect(mutateAsync).not.toHaveBeenCalled();
  });

  it('should submit valid form and call register mutation', async () => {
    mutateAsync.mockResolvedValueOnce({ id: 1, username: 'ok' });
    await renderPage();

    // Use translated placeholders
    fireEvent.input(screen.getByPlaceholderText('Choose a username'), {
      target: { value: 'alice' },
    });
    fireEvent.input(screen.getByPlaceholderText(/enter.*email/i), {
      target: { value: 'alice@example.com' },
    });
    // There are two password inputs with same placeholder; target them by index
    const pwAll = screen.getAllByPlaceholderText('••••••••');
    fireEvent.input(pwAll[0], { target: { value: 'Aa1@abcd' } });
    if (pwAll.length >= 2) fireEvent.input(pwAll[1], { target: { value: 'Aa1@abcd' } });

    // Master password
    const masterAll = screen.getAllByPlaceholderText('••••••••••••');
    if (masterAll.length >= 2) {
      fireEvent.input(masterAll[0], { target: { value: 'Master1@3456' } });
      fireEvent.input(masterAll[1], { target: { value: 'Master1@3456' } });
    } else if (masterAll.length === 1) {
      fireEvent.input(masterAll[0], { target: { value: 'Master1@3456' } });
    }

    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    expect(mutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        username: 'alice',
        email: 'alice@example.com',
        password: 'Aa1@abcd',
        masterPassword: 'Master1@3456',
      })
    );
  });

  it('should hide master password and submit without it when encryption is disabled', async () => {
    mockSecurityConfig({ encryptionEnabled: false });
    mutateAsync.mockResolvedValueOnce({ id: 1, username: 'ok' });

    renderPage();

    expect(screen.queryByPlaceholderText('••••••••••••')).not.toBeInTheDocument();
    fireEvent.input(screen.getByPlaceholderText('Choose a username'), {
      target: { value: 'alice' },
    });
    fireEvent.input(screen.getByPlaceholderText(/enter.*email/i), {
      target: { value: 'alice@example.com' },
    });
    const passwordFields = screen.getAllByPlaceholderText('••••••••');
    fireEvent.input(passwordFields[0], { target: { value: 'Aa1@abcd' } });
    fireEvent.input(passwordFields[1], { target: { value: 'Aa1@abcd' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    expect(mutateAsync.mock.calls[0][0]).not.toHaveProperty('masterPassword');
  });

  it('should fail closed and keep master password fields visible when security config is missing', () => {
    mockSecurityConfig(undefined);

    renderPage();

    expect(screen.getAllByPlaceholderText('••••••••••••')).toHaveLength(2);
  });

  it('should fail closed and keep master password fields visible when security config fails', () => {
    mockSecurityConfig(undefined, true);

    renderPage();

    expect(screen.getAllByPlaceholderText('••••••••••••')).toHaveLength(2);
  });

  it('should expose accessible password reveal toggles', () => {
    renderPage();

    const passwordToggle = screen
      .getAllByRole('button')
      .find(
        btn =>
          btn.getAttribute('aria-label')?.toLowerCase().includes('show') &&
          btn.getAttribute('aria-label')?.toLowerCase().includes('password') &&
          !btn.getAttribute('aria-label')?.toLowerCase().includes('master')
      );
    const masterPasswordToggle = screen
      .getAllByRole('button')
      .find(btn => btn.getAttribute('aria-label')?.toLowerCase().includes('master'));

    expect(passwordToggle).toBeTruthy();
    expect(masterPasswordToggle).toBeTruthy();

    for (const toggle of [passwordToggle!, masterPasswordToggle!]) {
      expect(toggle).toHaveClass('h-10', 'w-10', 'inline-flex', 'items-center', 'justify-center');
      expect(toggle).toHaveClass(
        'focus-visible:outline-none',
        'focus-visible:ring-2',
        'focus-visible:ring-primary',
        'focus-visible:ring-offset-2',
        'focus-visible:ring-offset-surface'
      );
      expect(toggle).toHaveAttribute('aria-pressed', 'false');
    }

    fireEvent.click(passwordToggle!);
    fireEvent.click(masterPasswordToggle!);

    expect(passwordToggle).toHaveAttribute('aria-pressed', 'true');
    expect(masterPasswordToggle).toHaveAttribute('aria-pressed', 'true');
  });

  it('should handle submit errors without logging full registration error objects', async () => {
    mutateAsync.mockRejectedValueOnce(new Error('Registration failed'));
    renderPage();

    fireEvent.input(screen.getByPlaceholderText('Choose a username'), {
      target: { value: 'alice' },
    });
    fireEvent.input(screen.getByPlaceholderText(/enter.*email/i), {
      target: { value: 'alice@example.com' },
    });
    const passwordFields = screen.getAllByPlaceholderText('••••••••');
    fireEvent.input(passwordFields[0], { target: { value: 'Aa1@abcd' } });
    fireEvent.input(passwordFields[1], { target: { value: 'Aa1@abcd' } });
    const masterFields = screen.getAllByPlaceholderText('••••••••••••');
    fireEvent.input(masterFields[0], { target: { value: 'Master1@3456' } });
    fireEvent.input(masterFields[1], { target: { value: 'Master1@3456' } });

    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    try {
      fireEvent.click(screen.getByRole('button', { name: /create account/i }));

      await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
      expect(consoleErrorSpy).not.toHaveBeenCalled();
    } finally {
      consoleErrorSpy.mockRestore();
    }
  });

  it('should show API error message when mutation has error', async () => {
    // Simulate error state from useRegister
    const apiError = { response: { data: { message: 'Email already in use' } } } as any;
    useRegisterMock.mockReturnValue({
      mutateAsync,
      isPending: false,
      isError: true,
      isSuccess: false,
      error: apiError,
    });

    // Render the page with error state
    renderPage();

    // Check that error message is displayed
    const errorMessage = await screen.findByText('Email already in use');
    expect(errorMessage).toBeInTheDocument();
  });
});
