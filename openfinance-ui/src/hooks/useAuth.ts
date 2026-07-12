/**
 * Authentication hooks using React Query
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router';
import type { AxiosError } from 'axios';
import apiClient from '@/services/apiClient';
import type {
  RegisterRequest,
  LoginRequest,
  LoginResponse,
  User,
  UpdateProfileRequest,
  OnboardingRequest,
  UserSettings,
} from '@/types/user';
import { useAuthContext } from '@/context/AuthContext';
import { useCurrencyDisplay } from '@/context/CurrencyDisplayContext';
import { clearStoredEncryptionEnabled, setStoredEncryptionEnabled } from '@/utils/encryption';

// Storage keys - keep consistent and avoid magic strings
const AUTH_TOKEN_KEY = 'auth_token';
const ENCRYPTION_SESSION_KEY = 'encryption_session';

interface SanitizedAuthErrorLog {
  status?: number;
  code?: string;
  message?: string;
}

function sanitizeAuthErrorForLog(error: unknown): SanitizedAuthErrorLog {
  const axiosError = error as AxiosError | undefined;
  const errorWithMessage = error as { message?: unknown } | undefined;
  const logDetails: SanitizedAuthErrorLog = {};

  if (typeof axiosError?.response?.status === 'number') {
    logDetails.status = axiosError.response.status;
  }

  if (typeof axiosError?.code === 'string') {
    logDetails.code = axiosError.code;
  }

  if (typeof errorWithMessage?.message === 'string') {
    logDetails.message = errorWithMessage.message;
  }

  return logDetails;
}

/**
 * Hook for user registration
 * @returns React Query mutation for registration
 */
export function useRegister() {
  const navigate = useNavigate();

  return useMutation<User, AxiosError, RegisterRequest>({
    mutationFn: async (data: RegisterRequest): Promise<User> => {
      const response = await apiClient.post<User>('/auth/register', data);
      return response.data;
    },
    onSuccess: () => {
      // Redirect to login page after successful registration
      // Pass a translation key so LoginPage can render it in the active language
      navigate('/login', {
        state: { messageKey: 'register.success' },
      });
    },
    onError: error => {
      // surface error to dev console; UI should read mutation.error
      console.error('Registration failed:', sanitizeAuthErrorForLog(error));
    },
  });
}

/**
 * Hook for user login
 * @returns React Query mutation for login
 */
export function useLogin() {
  const { setAuth } = useAuthContext();

  return useMutation<LoginResponse, AxiosError, LoginRequest>({
    mutationFn: async (data: LoginRequest): Promise<LoginResponse> => {
      const response = await apiClient.post<LoginResponse>('/auth/login', data);
      const encryptionEnabled = response.data.encryptionEnabled !== false;
      if (
        encryptionEnabled &&
        (typeof response.data.encryptionKey !== 'string' ||
          response.data.encryptionKey.length === 0)
      ) {
        throw new Error('Encryption session missing from login response');
      }
      return response.data;
    },
    onSuccess: (data: LoginResponse, variables: LoginRequest) => {
      // Store user and token in context (which also persists to localStorage or sessionStorage)
      const user: User = {
        id: data.userId,
        username: data.username,
        email: '', // Email not returned in login response
        baseCurrency: data.baseCurrency ?? 'USD',
        createdAt: new Date().toISOString(),
      };

      // Pass rememberMe preference to setAuth (defaults to false if not provided)
      const rememberMe = variables.rememberMe || false;
      setAuth(user, data.token, rememberMe);

      // Store encryption session token only when encryption is enabled.
      try {
        const encryptionEnabled = data.encryptionEnabled !== false;
        if (encryptionEnabled && typeof data.encryptionKey === 'string') {
          sessionStorage.setItem(ENCRYPTION_SESSION_KEY, data.encryptionKey);
          setStoredEncryptionEnabled(true);
        } else {
          sessionStorage.removeItem(ENCRYPTION_SESSION_KEY);
          setStoredEncryptionEnabled(false);
        }
      } catch (e) {
        console.warn('Failed to update encryption session storage', sanitizeAuthErrorForLog(e));
      }

      // NOTE: navigation/redirect is intentionally left to the caller so it can
      // redirect back to the original requested location (if any).
    },
    onError: error => {
      console.error('Login failed:', sanitizeAuthErrorForLog(error));
      // Clear any stale tokens from both storages
      try {
        localStorage.removeItem(AUTH_TOKEN_KEY);
        sessionStorage.removeItem(AUTH_TOKEN_KEY);
        sessionStorage.removeItem(ENCRYPTION_SESSION_KEY);
        clearStoredEncryptionEnabled();
      } catch (e) {
        console.warn('Failed to clear stale auth storage', sanitizeAuthErrorForLog(e));
      }
    },
  });
}

/**
 * Hook for user logout
 * @returns Logout function
 */
export function useLogout() {
  const navigate = useNavigate();
  const { clearAuth } = useAuthContext();

  return useCallback(() => {
    // Invalidate encryption session on the server (best-effort — don't block on failure)
    apiClient.post('/auth/logout').catch(() => {});

    // Clear auth context (which also clears localStorage/sessionStorage)
    clearAuth();

    // Navigate to login
    navigate('/login');
  }, [navigate, clearAuth]);
}

/**
 * Hook to get current user's profile
 * @returns React Query result with user profile data
 */
export function useGetProfile() {
  const { updateUser } = useAuthContext();

  const query = useQuery<User, AxiosError>({
    queryKey: ['profile'],
    queryFn: async (): Promise<User> => {
      const response = await apiClient.get<User>('/auth/profile');
      return response.data;
    },
    // Stale time of 5 minutes - profile data doesn't change often
    staleTime: 5 * 60 * 1000,
    // Don't retry on auth errors (401/403)
    retry: (failureCount, error) => {
      if (error.response?.status === 401 || error.response?.status === 403) {
        return false;
      }
      return failureCount < 3;
    },
  });

  // Sync profile image to auth context when the profile loads/reloads so
  // the header avatar always reflects the latest stored image (BUG-09).
  useEffect(() => {
    if (query.data) {
      updateUser({ profileImage: query.data.profileImage ?? null });
    }
  }, [query.data, updateUser]);

  return query;
}

/**
 * Hook for updating user profile
 * @returns React Query mutation for profile update
 */
export function useUpdateProfile() {
  const queryClient = useQueryClient();
  const { setAuth, user: currentUser } = useAuthContext();

  return useMutation<User, AxiosError, UpdateProfileRequest>({
    mutationFn: async (data: UpdateProfileRequest): Promise<User> => {
      const response = await apiClient.put<User>('/auth/profile', data);
      return response.data;
    },
    onSuccess: (updatedUser: User) => {
      // Update the profile cache
      queryClient.setQueryData(['profile'], updatedUser);

      // Update auth context with new user data (preserves token and storage preference)
      if (currentUser) {
        // Check which storage has the token to preserve the "remember me" preference
        const token =
          localStorage.getItem(AUTH_TOKEN_KEY) || sessionStorage.getItem(AUTH_TOKEN_KEY) || '';
        const rememberMe = !!localStorage.getItem(AUTH_TOKEN_KEY); // true if in localStorage
        setAuth(updatedUser, token, rememberMe);
      }
    },
    onError: error => {
      console.error('Profile update failed:', error?.message ?? error);
    },
  });
}

/**
 * Hook for uploading the current user's profile image.
 * Sends the file as multipart/form-data to POST /api/v1/users/me/profile-image.
 */
export function useUploadProfileImage() {
  const queryClient = useQueryClient();
  const { updateUser } = useAuthContext();

  return useMutation<User, AxiosError, File>({
    mutationFn: async (imageFile: File): Promise<User> => {
      const formData = new FormData();
      formData.append('image', imageFile);
      const response = await apiClient.post<User>('/users/me/profile-image', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      return response.data;
    },
    onSuccess: (updatedUser: User) => {
      queryClient.setQueryData(['profile'], updatedUser);
      // Keep auth context in sync so the avatar updates everywhere immediately
      updateUser({ profileImage: updatedUser.profileImage });
    },
    onError: error => {
      console.error('Profile image upload failed:', error?.message ?? error);
    },
  });
}

/**
 * Hook for deleting the current user's profile image.
 * Calls DELETE /api/v1/users/me/profile-image.
 */
export function useDeleteProfileImage() {
  const queryClient = useQueryClient();
  const { updateUser } = useAuthContext();

  return useMutation<User, AxiosError, void>({
    mutationFn: async (): Promise<User> => {
      const response = await apiClient.delete<User>('/users/me/profile-image');
      return response.data;
    },
    onSuccess: (updatedUser: User) => {
      queryClient.setQueryData(['profile'], updatedUser);
      updateUser({ profileImage: null });
    },
    onError: error => {
      console.error('Profile image deletion failed:', error?.message ?? error);
    },
  });
}

/**
 * Hook for submitting the initial onboarding preferences.
 * Calls POST /api/v1/users/me/onboarding and redirects to /dashboard on success.
 */
export function useCompleteOnboarding() {
  const navigate = useNavigate();
  const { updateUser } = useAuthContext();
  const { setDisplayMode } = useCurrencyDisplay();
  const queryClient = useQueryClient();

  return useMutation<UserSettings, AxiosError, OnboardingRequest>({
    mutationFn: async (data: OnboardingRequest): Promise<UserSettings> => {
      const response = await apiClient.post<UserSettings>('/users/me/onboarding', data);
      return response.data;
    },
    onSuccess: (_settings: UserSettings, variables: OnboardingRequest) => {
      // Sync the base currency into auth context so the whole app updates immediately
      updateUser({ baseCurrency: variables.baseCurrency });
      // Sync the amount display mode into context (and localStorage)
      setDisplayMode(variables.amountDisplayMode);
      queryClient.invalidateQueries({ queryKey: ['userSettings'] });
      navigate('/dashboard', { replace: true });
    },
    onError: error => {
      console.error('Onboarding submission failed:', error?.message ?? error);
    },
  });
}
