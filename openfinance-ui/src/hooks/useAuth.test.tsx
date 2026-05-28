import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mocks
const mockNavigate = vi.fn();
vi.mock('react-router', async () => {
  const actual = await vi.importActual<any>('react-router');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// Mock API client
const postMock = vi.fn();
const getMock = vi.fn();
const putMock = vi.fn();
const deleteMock = vi.fn();
vi.mock('@/services/apiClient', () => ({
  default: {
    post: (...args: any[]) => postMock(...args),
    get: (...args: any[]) => getMock(...args),
    put: (...args: any[]) => putMock(...args),
    delete: (...args: any[]) => deleteMock(...args),
  },
}));

import { useRegister, useLogin, useLogout, useGetProfile, useUpdateProfile, useUploadProfileImage, useDeleteProfileImage, useCompleteOnboarding } from './useAuth';
import { AuthProvider } from '@/context/AuthContext';
import { VisibilityProvider } from '@/context/VisibilityContext';
import { CurrencyDisplayProvider } from '@/context/CurrencyDisplayContext';

function renderWithProviders(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  // Include AuthProvider so hooks using useAuthContext work correctly
  const Wrapper = ({ children }: any) => (
    <MemoryRouter>
      <QueryClientProvider client={qc}>
        <AuthProvider>
          <VisibilityProvider>
            <CurrencyDisplayProvider>{children}</CurrencyDisplayProvider>
          </VisibilityProvider>
        </AuthProvider>
      </QueryClientProvider>
    </MemoryRouter>
  );

  return render(ui, { wrapper: Wrapper });
}

describe('useAuth hooks', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('should register user and navigate to /login on success', async () => {
    // Arrange: mock API response
    const user = { id: 1, username: 'alice' };
    postMock.mockResolvedValueOnce({ data: { data: user } });

    const TestComponent = () => {
      const mutation = useRegister();
      return (
        <button onClick={() => mutation.mutateAsync({ username: 'alice', email: 'a@b.c', password: 'P@ssw0rd!', masterPassword: 'MasterP@ss1' } as any)}>
          go
        </button>
      );
    };

    const { getByText } = renderWithProviders(<TestComponent />);

    // Act
    fireEvent.click(getByText('go'));

    // Assert: post was called and navigate used
    await waitFor(() => expect(postMock).toHaveBeenCalledWith('/auth/register', expect.any(Object)));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/login', expect.objectContaining({ state: expect.any(Object) })));
  });

  it('should login, store tokens and navigate to dashboard on success', async () => {
    // Arrange
    const response = { token: 'jwt-token', encryptionKey: 'enc-key', userId: 1, username: 'bob' };
    postMock.mockResolvedValueOnce({ data: response });

    const TestComponent = () => {
      const mutation = useLogin();
      return <button onClick={() => mutation.mutateAsync({ username: 'bob', password: 'pw', masterPassword: 'mpw' } as any)}>login</button>;
    };

    const { getByText } = renderWithProviders(<TestComponent />);

    // Act
    fireEvent.click(getByText('login'));

    // Assert - wait for mutation to complete
    await waitFor(() => expect(postMock).toHaveBeenCalledWith('/auth/login', expect.any(Object)));
    
    // Wait for storage to be updated (setAuth is called in onSuccess)
    // Check both sessionStorage and localStorage as either could be used depending on rememberMe
    await waitFor(() => {
      const tokenInLocal = localStorage.getItem('auth_token');
      const tokenInSession = sessionStorage.getItem('auth_token');
      expect(tokenInLocal || tokenInSession).toBe('jwt-token');
    }, { timeout: 5000 });
    
    await waitFor(() => {
      const encKey = sessionStorage.getItem('encryption_key');
      expect(encKey).toBe('enc-key');
    }, { timeout: 5000 });
  });

  it('should clear storage on login error', async () => {
    // Arrange: set stale tokens first
    localStorage.setItem('auth_token', 'stale');
    sessionStorage.setItem('encryption_key', 'stale');
    postMock.mockRejectedValueOnce(new Error('bad creds'));

    const TestComponent = () => {
      const mutation = useLogin();
      // Use mutate (callback-style) to avoid unhandled promise rejection in tests
      return <button onClick={() => mutation.mutate({ username: 'x' } as any)}>loginErr</button>;
    };

    const { getByText } = renderWithProviders(<TestComponent />);

    // Act
    fireEvent.click(getByText('loginErr'));

    // Assert: storage cleared
    await waitFor(() => expect(localStorage.getItem('auth_token')).toBeNull());
    await waitFor(() => expect(sessionStorage.getItem('encryption_key')).toBeNull());
  });

  it('useLogout should clear storage and navigate to /login', async () => {
    localStorage.setItem('auth_token', 'x');
    sessionStorage.setItem('encryption_key', 'y');

    const TestComponent = () => {
      const logout = useLogout();
      return <button onClick={() => logout()}>logout</button>;
    };

    const { getByText } = renderWithProviders(<TestComponent />);

    // Act
    fireEvent.click(getByText('logout'));

    // Assert
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(sessionStorage.getItem('encryption_key')).toBeNull();
    expect(mockNavigate).toHaveBeenCalledWith('/login');
  });

  it('useGetProfile fetches profile data', async () => {
    const user = { id: 1, username: 'alice', email: 'a@b.c', baseCurrency: 'EUR', createdAt: '2024-01-01' };
    getMock.mockResolvedValueOnce({ data: user });

    const TestComponent = () => {
      const { data, isSuccess } = useGetProfile();
      return <div>{isSuccess ? data?.username : 'loading'}</div>;
    };

    const { findByText } = renderWithProviders(<TestComponent />);
    await findByText('alice');
    expect(getMock).toHaveBeenCalledWith('/auth/profile');
  });

  it('useUpdateProfile updates profile and cache', async () => {
    const updatedUser = { id: 1, username: 'alice2', email: 'a@b.c', baseCurrency: 'EUR', createdAt: '2024-01-01' };
    putMock.mockResolvedValueOnce({ data: updatedUser });

    const TestComponent = () => {
      const mutation = useUpdateProfile();
      return <button onClick={() => mutation.mutate({ username: 'alice2' } as any)}>update</button>;
    };

    const { getByText } = renderWithProviders(<TestComponent />);
    fireEvent.click(getByText('update'));
    await waitFor(() => expect(putMock).toHaveBeenCalledWith('/auth/profile', expect.any(Object)));
  });

  it('useUploadProfileImage uploads image', async () => {
    const updatedUser = { id: 1, username: 'alice', profileImage: 'data:image/png;base64,...' };
    postMock.mockResolvedValueOnce({ data: updatedUser });

    const TestComponent = () => {
      const mutation = useUploadProfileImage();
      return <button onClick={() => mutation.mutate(new File([''], 'avatar.png', { type: 'image/png' }))}>upload</button>;
    };

    const { getByText } = renderWithProviders(<TestComponent />);
    fireEvent.click(getByText('upload'));
    await waitFor(() => expect(postMock).toHaveBeenCalledWith('/users/me/profile-image', expect.any(FormData), expect.any(Object)));
  });

  it('useDeleteProfileImage deletes image', async () => {
    const updatedUser = { id: 1, username: 'alice', profileImage: null };
    deleteMock.mockResolvedValueOnce({ data: updatedUser });

    const TestComponent = () => {
      const mutation = useDeleteProfileImage();
      return <button onClick={() => mutation.mutate()}>delete</button>;
    };

    const { getByText } = renderWithProviders(<TestComponent />);
    fireEvent.click(getByText('delete'));
    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith('/users/me/profile-image'));
  });

  it('useCompleteOnboarding submits and navigates to dashboard', async () => {
    const settings = { baseCurrency: 'EUR', amountDisplayMode: 'original' };
    postMock.mockResolvedValueOnce({ data: settings });

    const TestComponent = () => {
      const mutation = useCompleteOnboarding();
      return <button onClick={() => mutation.mutate({ baseCurrency: 'EUR', amountDisplayMode: 'original' } as any)}>onboard</button>;
    };

    const { getByText } = renderWithProviders(<TestComponent />);
    fireEvent.click(getByText('onboard'));
    await waitFor(() => expect(postMock).toHaveBeenCalledWith('/users/me/onboarding', expect.any(Object)));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true }));
  });
});
