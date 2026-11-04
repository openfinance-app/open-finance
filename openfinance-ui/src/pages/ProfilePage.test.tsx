import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import ProfilePage from '@/pages/ProfilePage';
import i18n from '@/test/i18n-test';

const state = vi.hoisted(() => ({
  profile: {
    id: 1,
    username: 'alice',
    email: 'alice@example.test',
    createdAt: '2025-01-10T12:00:00Z',
  },
  loading: false,
  error: null as Error | null,
  update: vi.fn(),
}));
vi.mock('@/hooks/useAuth', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useAuth')>()),
  useGetProfile: () => ({ data: state.profile, isLoading: state.loading, error: state.error }),
  useUpdateProfile: () => ({ mutateAsync: state.update, isPending: false }),
}));
vi.mock('@/components/settings/ProfileImageUpload', () => ({ ProfileImageUpload: () => null }));
const tr = (key: string) => i18n.t(`profile.${key}`, { ns: 'settings' });
beforeEach(async () => {
  mockAuthentication();
  await i18n.changeLanguage('en');
  state.loading = false;
  state.error = null;
  state.update.mockReset().mockResolvedValue({});
});

describe('profile updates', () => {
  it('submits only the changed email and clears the password after success', async () => {
    renderWithProviders(<ProfilePage />);
    expect(screen.getByText('alice')).toBeInTheDocument();
    const email = screen.getByLabelText(tr('emailAddress'));
    const password = screen.getByLabelText(/Current Password/);
    expect(email).toHaveValue('alice@example.test');
    fireEvent.change(email, { target: { value: 'new@example.test' } });
    fireEvent.change(password, { target: { value: 'secret-password' } });
    fireEvent.click(screen.getByRole('button', { name: tr('updateButton') }));
    await waitFor(() =>
      expect(state.update).toHaveBeenCalledWith({
        email: 'new@example.test',
        currentPassword: 'secret-password',
      })
    );
    await screen.findByText(tr('updateSuccess'));
    expect(password).toHaveValue('');
    expect(email).toHaveValue('new@example.test');
  });

  it('does not send unchanged values or invalid email addresses', async () => {
    renderWithProviders(<ProfilePage />);
    fireEvent.change(screen.getByLabelText(/Current Password/), {
      target: { value: 'secret-password' },
    });
    fireEvent.click(screen.getByRole('button', { name: tr('updateButton') }));
    await screen.findByText(tr('noChanges'));
    expect(state.update).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(tr('emailAddress')), { target: { value: 'invalid' } });
    fireEvent.click(screen.getByRole('button', { name: tr('updateButton') }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: tr('updateButton') })).toBeEnabled()
    );
    expect(state.update).not.toHaveBeenCalled();
  });

  it('keeps the edit available when the update fails and never reports success', async () => {
    state.update.mockRejectedValue(new Error('Offline'));
    const errorLog = vi.spyOn(console, 'error').mockImplementation(() => {});
    renderWithProviders(<ProfilePage />);
    fireEvent.change(screen.getByLabelText(tr('emailAddress')), {
      target: { value: 'retry@example.test' },
    });
    fireEvent.change(screen.getByLabelText(/Current Password/), {
      target: { value: 'secret-password' },
    });
    fireEvent.click(screen.getByRole('button', { name: tr('updateButton') }));
    await waitFor(() => expect(state.update).toHaveBeenCalledOnce());
    await waitFor(() =>
      expect(screen.getByRole('button', { name: tr('updateButton') })).toBeEnabled()
    );
    expect(screen.queryByText(tr('updateSuccess'))).not.toBeInTheDocument();
    expect(screen.getByLabelText(tr('emailAddress'))).toHaveValue('retry@example.test');
    errorLog.mockRestore();
  });

  it('shows loading and load errors without an editable form', () => {
    state.loading = true;
    const view = renderWithProviders(<ProfilePage />);
    expect(screen.getByText(i18n.t('profile.loading', { ns: 'errors' }))).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    state.loading = false;
    state.error = new Error('Unavailable');
    view.rerender(<ProfilePage />);
    expect(screen.getByText(i18n.t('profile.loadError', { ns: 'errors' }))).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });
});
