import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { QueryClient } from '@tanstack/react-query';
import { renderWithProviders, mockAuthentication, userEvent } from '@/test/test-utils';
import {
  useGetProfile,
  useUpdateProfile,
  useUploadProfileImage,
  useDeleteProfileImage,
  useCompleteOnboarding,
} from '@/hooks/useAuth';
import apiClient from '@/services/apiClient';
import type { User, UpdateProfileRequest } from '@/types/user';
const state = vi.hoisted(() => ({
  user: null as User | null,
  setAuth: vi.fn(),
  clearAuth: vi.fn(),
  updateUser: vi.fn(),
}));
vi.mock('@/context/AuthContext', async original => ({
  ...(await original<typeof import('@/context/AuthContext')>()),
  useAuthContext: () => ({ ...state, baseCurrency: 'USD', isAuthenticated: true }),
}));
vi.mock('@/services/apiClient');
const user: User = {
  id: 1,
  username: 'alice',
  email: 'a@example.test',
  createdAt: '2025-01-01',
  baseCurrency: 'USD',
};
function Profile() {
  const query = useGetProfile();
  return (
    <output>
      {query.isError ? 'Profile failed' : query.isSuccess ? 'Profile loaded' : 'Loading'}
    </output>
  );
}
function Update({ request }: { request: UpdateProfileRequest }) {
  const mutation = useUpdateProfile();
  return (
    <>
      <button onClick={() => mutation.mutate(request)}>Save profile</button>
      <output>{mutation.isSuccess ? 'Saved' : mutation.isError ? 'Failed' : 'Ready'}</output>
    </>
  );
}
function FailingMutation({ kind }: { kind: 'profile' | 'upload' | 'delete' | 'onboarding' }) {
  const profile = useUpdateProfile();
  const upload = useUploadProfileImage();
  const remove = useDeleteProfileImage();
  const onboarding = useCompleteOnboarding();
  return (
    <>
      <button
        onClick={() => {
          if (kind === 'profile') profile.mutate({ username: 'Alice' });
          else if (kind === 'upload')
            upload.mutate(new File(['image'], 'avatar.png', { type: 'image/png' }));
          else if (kind === 'delete') remove.mutate();
          else onboarding.mutate({ baseCurrency: 'EUR', language: 'en' });
        }}
      >
        Run mutation
      </button>
      <output>
        {[profile, upload, remove, onboarding].some(mutation => mutation.isError)
          ? 'Failed'
          : 'Ready'}
      </output>
    </>
  );
}
describe('profile authentication boundaries', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
    state.user = user;
    vi.mocked(apiClient.get).mockResolvedValue({ data: {} });
  });
  it.each([401, 403, 500, undefined])(
    'profile failure %s retries only transient failures',
    async status => {
      vi.mocked(apiClient.get).mockImplementation(async url => {
        if (url === '/auth/profile')
          throw {
            response: status == null ? undefined : { status },
            message: 'Profile unavailable',
          };
        return { data: {} };
      });
      const client = new QueryClient({
        defaultOptions: { queries: { retryDelay: 0, gcTime: Infinity } },
      });
      renderWithProviders(<Profile />, { queryClient: client });
      expect(await screen.findByText('Profile failed')).toBeVisible();
      expect(
        vi.mocked(apiClient.get).mock.calls.filter(([url]) => url === '/auth/profile')
      ).toHaveLength(status === 401 || status === 403 ? 1 : 4);
      expect(state.updateUser).not.toHaveBeenCalled();
    }
  );
  it.each([undefined, 'data:image/png;base64,AAAA'])(
    'loading the profile synchronizes avatar value %s',
    async profileImage => {
      vi.mocked(apiClient.get).mockImplementation(async url => ({
        data: url === '/auth/profile' ? { ...user, profileImage } : {},
      }));
      renderWithProviders(<Profile />);
      expect(await screen.findByText('Profile loaded')).toBeVisible();
      await waitFor(() =>
        expect(state.updateUser).toHaveBeenCalledWith({ profileImage: profileImage ?? null })
      );
    }
  );
  it.each(['local', 'session', 'missing'] as const)(
    'profile edits preserve %s credential storage preference',
    async storage => {
      localStorage.removeItem('auth_token');
      sessionStorage.removeItem('auth_token');
      if (storage !== 'missing')
        (storage === 'local' ? localStorage : sessionStorage).setItem(
          'auth_token',
          'existing-token'
        );
      const updated = { ...user, username: 'Updated' };
      vi.mocked(apiClient.put).mockResolvedValue({ data: updated });
      const client = new QueryClient();
      const events = userEvent.setup();
      renderWithProviders(<Update request={{ username: 'Updated' }} />, { queryClient: client });
      await events.click(screen.getByRole('button', { name: 'Save profile' }));
      expect(await screen.findByText('Saved')).toBeVisible();
      expect(state.setAuth).toHaveBeenCalledWith(
        updated,
        storage === 'missing' ? '' : 'existing-token',
        storage === 'local'
      );
      expect(client.getQueryData(['profile'])).toEqual(updated);
      expect(state.clearAuth).not.toHaveBeenCalled();
    }
  );
  it('changing the password signs out instead of resurrecting the previous token', async () => {
    vi.mocked(apiClient.put).mockResolvedValue({ data: user });
    const events = userEvent.setup();
    renderWithProviders(
      <Update request={{ currentPassword: 'OldPassword1!', newPassword: 'NewPassword1!' }} />
    );
    await events.click(screen.getByRole('button', { name: 'Save profile' }));
    expect(await screen.findByText('Saved')).toBeVisible();
    expect(state.clearAuth).toHaveBeenCalledOnce();
    expect(state.setAuth).not.toHaveBeenCalled();
  });
  it('a response arriving after the user has signed out cannot recreate authentication', async () => {
    state.user = null;
    vi.mocked(apiClient.put).mockResolvedValue({ data: user });
    const events = userEvent.setup();
    renderWithProviders(<Update request={{ username: 'Updated' }} />);
    await events.click(screen.getByRole('button', { name: 'Save profile' }));
    expect(await screen.findByText('Saved')).toBeVisible();
    expect(state.setAuth).not.toHaveBeenCalled();
  });
  it.each(['profile', 'upload', 'delete', 'onboarding'] as const)(
    'propagates unstructured %s failures without logging nonexistent messages or changing authentication',
    async kind => {
      const failure = { reason: 'Network refused request' };
      const log = vi.spyOn(console, 'error').mockImplementation(() => {});
      vi.mocked(apiClient.put).mockRejectedValue(failure);
      vi.mocked(apiClient.post).mockRejectedValue(failure);
      vi.mocked(apiClient.delete).mockRejectedValue(failure);
      try {
        const events = userEvent.setup();
        renderWithProviders(<FailingMutation kind={kind} />);
        await events.click(screen.getByRole('button', { name: 'Run mutation' }));
        expect(await screen.findByText('Failed')).toBeVisible();
        expect(log).toHaveBeenCalledWith(expect.any(String), failure);
        expect(state.setAuth).not.toHaveBeenCalled();
        expect(state.clearAuth).not.toHaveBeenCalled();
      } finally {
        log.mockRestore();
      }
    }
  );
});
