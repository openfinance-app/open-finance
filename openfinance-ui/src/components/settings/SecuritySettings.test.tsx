import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication, userEvent } from '@/test/test-utils';
import { SecuritySettings } from './SecuritySettings';

vi.mock('@/lib/apiClient', () => ({
  default: {
    put: vi.fn(),
  },
}));

import apiClient from '@/lib/apiClient';
const mockPut = vi.mocked(apiClient.put);

describe('SecuritySettings', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders security settings heading', () => {
    renderWithProviders(<SecuritySettings />);
    expect(screen.getByText('Security Settings')).toBeInTheDocument();
  });

  it('renders login password section', () => {
    renderWithProviders(<SecuritySettings />);
    expect(screen.getByText('Change Login Password')).toBeInTheDocument();
  });

  it('renders master password section', () => {
    renderWithProviders(<SecuritySettings />);
    expect(screen.getByText('Change Master Password')).toBeInTheDocument();
  });

  it('renders two-factor auth section', () => {
    renderWithProviders(<SecuritySettings />);
    expect(screen.getByText('Two-Factor Authentication')).toBeInTheDocument();
  });

  it('shows password form when Change is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[0]); // first Change = login password
    expect(screen.getByText('Current Password')).toBeInTheDocument();
    expect(screen.getByText('New Password')).toBeInTheDocument();
    expect(screen.getByText('Confirm New Password')).toBeInTheDocument();
  });

  it('hides password form when Cancel is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[0]);
    expect(screen.getByText('Current Password')).toBeInTheDocument();
    await user.click(screen.getByText('Cancel'));
    expect(screen.queryByText('Current Password')).not.toBeInTheDocument();
  });

  it('shows master password form when Change is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[1]); // second Change = master password
    expect(screen.getByText('Current Master Password')).toBeInTheDocument();
    expect(screen.getByText('New Master Password')).toBeInTheDocument();
    expect(screen.getByText('Confirm New Master Password')).toBeInTheDocument();
  });

  it('shows critical warning in master password form', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[1]);
    expect(screen.getByText(/critical operation/i)).toBeInTheDocument();
  });

  it('submits login password change successfully', async () => {
    const user = userEvent.setup();
    mockPut.mockResolvedValueOnce({});
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[0]);

    await user.type(screen.getByPlaceholderText(/enter your current password/i), 'oldpass123');
    await user.type(screen.getByPlaceholderText(/enter your new password/i), 'newpass1234');
    await user.type(screen.getByPlaceholderText(/confirm your new password/i), 'newpass1234');
    await user.click(screen.getByRole('button', { name: /^change password$/i }));

    await waitFor(() => {
      expect(mockPut).toHaveBeenCalledWith('/api/v1/users/me/password', {
        currentPassword: 'oldpass123',
        newPassword: 'newpass1234',
      });
    });
    expect(screen.getByText(/password changed successfully/i)).toBeInTheDocument();
  });

  it('shows error on login password change failure', async () => {
    const user = userEvent.setup();
    mockPut.mockRejectedValueOnce({ response: { data: { message: 'Invalid current password' } } });
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[0]);

    await user.type(screen.getByPlaceholderText(/enter your current password/i), 'wrongpass');
    await user.type(screen.getByPlaceholderText(/enter your new password/i), 'newpass1234');
    await user.type(screen.getByPlaceholderText(/confirm your new password/i), 'newpass1234');
    await user.click(screen.getByRole('button', { name: /^change password$/i }));

    await waitFor(() => {
      expect(screen.getByText('Invalid current password')).toBeInTheDocument();
    });
  });

  it('submits master password change successfully', async () => {
    const user = userEvent.setup();
    mockPut.mockResolvedValueOnce({});
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[1]);

    await user.type(screen.getByPlaceholderText(/enter your current master password/i), 'oldmaster1');
    await user.type(screen.getByPlaceholderText(/enter your new master password/i), 'newmaster12');
    await user.type(screen.getByPlaceholderText(/confirm your new master password/i), 'newmaster12');
    await user.click(screen.getByRole('button', { name: /^change master password$/i }));

    await waitFor(() => {
      expect(mockPut).toHaveBeenCalledWith('/api/v1/users/me/master-password', {
        currentMasterPassword: 'oldmaster1',
        newMasterPassword: 'newmaster12',
      });
    });
    expect(screen.getByText(/master password changed successfully/i)).toBeInTheDocument();
  });

  it('toggles password visibility', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[0]);

    const currentPwInput = screen.getByPlaceholderText(/enter your current password/i);
    expect(currentPwInput).toHaveAttribute('type', 'password');

    const showButtons = screen.getAllByLabelText('Show password');
    await user.click(showButtons[0]);
    expect(currentPwInput).toHaveAttribute('type', 'text');
  });

  it('shows password strength bar for new password', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[0]);

    await user.type(screen.getByPlaceholderText(/enter your new password/i), 'StrongP@ss1');
    await waitFor(() => {
      expect(screen.getByText(/strong|good/i)).toBeInTheDocument();
    });
  });

  it('shows validation error for short password', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[0]);

    await user.type(screen.getByPlaceholderText(/enter your current password/i), 'old');
    await user.type(screen.getByPlaceholderText(/enter your new password/i), 'short');
    await user.type(screen.getByPlaceholderText(/confirm your new password/i), 'short');
    await user.click(screen.getByRole('button', { name: /^change password$/i }));

    await waitFor(() => {
      expect(screen.getByText(/password must be at least 8 characters/i)).toBeInTheDocument();
    });
  });

  it('shows error for password mismatch', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[0]);

    await user.type(screen.getByPlaceholderText(/enter your current password/i), 'oldpass123');
    await user.type(screen.getByPlaceholderText(/enter your new password/i), 'newpass1234');
    await user.type(screen.getByPlaceholderText(/confirm your new password/i), 'different34');
    await user.click(screen.getByRole('button', { name: /^change password$/i }));

    await waitFor(() => {
      expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
    });
  });

  it('shows error on master password change failure', async () => {
    const user = userEvent.setup();
    mockPut.mockRejectedValueOnce({ response: { data: { message: 'Invalid master password' } } });
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[1]);

    await user.type(screen.getByPlaceholderText(/enter your current master password/i), 'wrongmaster');
    await user.type(screen.getByPlaceholderText(/enter your new master password/i), 'newmaster12');
    await user.type(screen.getByPlaceholderText(/confirm your new master password/i), 'newmaster12');
    await user.click(screen.getByRole('button', { name: /^change master password$/i }));

    await waitFor(() => {
      expect(screen.getByText('Invalid master password')).toBeInTheDocument();
    });
  });

  it('shows generic error when response has no message', async () => {
    const user = userEvent.setup();
    mockPut.mockRejectedValueOnce(new Error('Network error'));
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[0]);

    await user.type(screen.getByPlaceholderText(/enter your current password/i), 'oldpass123');
    await user.type(screen.getByPlaceholderText(/enter your new password/i), 'newpass1234');
    await user.type(screen.getByPlaceholderText(/confirm your new password/i), 'newpass1234');
    await user.click(screen.getByRole('button', { name: /^change password$/i }));

    await waitFor(() => {
      // The component shows a generic fallback error with red text
      expect(screen.getByText(/failed to change password/i)).toBeInTheDocument();
    });
  });

  it('hides master password form when Cancel is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[1]);
    expect(screen.getByText('Current Master Password')).toBeInTheDocument();
    await user.click(screen.getByText('Cancel'));
    expect(screen.queryByText('Current Master Password')).not.toBeInTheDocument();
  });

  it('toggles master password visibility', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[1]);

    const masterPwInput = screen.getByPlaceholderText(/enter your current master password/i);
    expect(masterPwInput).toHaveAttribute('type', 'password');

    const showButtons = screen.getAllByLabelText('Show password');
    await user.click(showButtons[0]);
    expect(masterPwInput).toHaveAttribute('type', 'text');
  });

  it('shows password strength bar for new master password', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SecuritySettings />);
    const changeButtons = screen.getAllByText('Change');
    await user.click(changeButtons[1]);

    await user.type(screen.getByPlaceholderText(/enter your new master password/i), 'SuperStr0ng!Pass');
    await waitFor(() => {
      expect(screen.getByText(/strong|good/i)).toBeInTheDocument();
    });
  });

  it('shows 2FA section with coming soon label', () => {
    renderWithProviders(<SecuritySettings />);
    expect(screen.getByText('Two-Factor Authentication')).toBeInTheDocument();
    const comingSoonElements = screen.getAllByText(/coming soon/i);
    expect(comingSoonElements.length).toBeGreaterThanOrEqual(1);
  });
});
