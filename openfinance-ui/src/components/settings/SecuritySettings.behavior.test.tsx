import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { SecuritySettings } from '@/components/settings/SecuritySettings';
import apiClient from '@/services/apiClient';
import { STORAGE_KEYS } from '@/constants/storage';

vi.mock('@/services/apiClient', () => ({ default: { put: vi.fn() } }));
const put = vi.mocked(apiClient.put);

function openForm(master: boolean): void {
  renderWithProviders(<SecuritySettings />);
  fireEvent.click(screen.getAllByRole('button', { name: 'Change' })[master ? 1 : 0]);
}

function fillForm(
  master: boolean,
  current = 'old-password',
  next = 'Next-password123!',
  confirm = next
): void {
  const noun = master ? 'master password' : 'password';
  fireEvent.change(screen.getByPlaceholderText(new RegExp(`Enter your current ${noun}`, 'i')), {
    target: { value: current },
  });
  fireEvent.change(screen.getByPlaceholderText(new RegExp(`Enter your new ${noun}`, 'i')), {
    target: { value: next },
  });
  fireEvent.change(screen.getByPlaceholderText(new RegExp(`Confirm your new ${noun}`, 'i')), {
    target: { value: confirm },
  });
}

function submit(master: boolean): void {
  fireEvent.click(
    screen.getByRole('button', { name: master ? /^Change Master Password$/ : /^Change Password$/ })
  );
}

beforeEach(() => {
  vi.resetAllMocks();
  mockAuthentication();
  Element.prototype.scrollIntoView = vi.fn();
});
afterEach(() => vi.useRealTimers());

describe('Security settings form boundaries', () => {
  it.each([
    [false, 'current'],
    [false, 'new'],
    [false, 'confirm'],
    [true, 'current'],
    [true, 'new'],
    [true, 'confirm'],
  ] as const)(
    'reveals and conceals only the selected field: master=%s, field=%s',
    (master, field) => {
      openForm(master);
      fillForm(master);
      const noun = master ? 'master password' : 'password';
      const placeholder =
        field === 'confirm' ? `Confirm your new ${noun}` : `Enter your ${field} ${noun}`;
      const input = screen.getByPlaceholderText(new RegExp(placeholder, 'i'));
      const wrapper = input.parentElement!;
      fireEvent.click(within(wrapper).getByRole('button', { name: 'Show password' }));
      expect(input).toHaveAttribute('type', 'text');
      expect(screen.getAllByRole('button', { name: 'Show password' })).toHaveLength(2);
      fireEvent.click(within(wrapper).getByRole('button', { name: 'Hide password' }));
      expect(input).toHaveAttribute('type', 'password');
      expect(input).toHaveValue(field === 'current' ? 'old-password' : 'Next-password123!');
      expect(put).not.toHaveBeenCalled();
    }
  );

  it.each([
    ['abcdefgh', 'Weak'],
    ['abcdefghijkl', 'Fair'],
    ['Abcdefgh1234', 'Strong'],
    ['Abcdefgh1', 'Good'],
    ['ABCDEFGH', 'Weak'],
    ['abc', ''],
    ['!Aa1', 'Good'],
  ])('reports password strength for %s without submitting', (password, strength) => {
    openForm(false);
    fireEvent.change(screen.getByPlaceholderText(/Enter your new password/i), {
      target: { value: password },
    });
    for (const label of ['Weak', 'Fair', 'Good', 'Strong']) {
      expect(screen.queryByText(label) !== null).toBe(label === strength);
    }
    expect(put).not.toHaveBeenCalled();
  });

  it.each([
    [false, '', 'Next-password123!', 'Next-password123!', /current password is required/i],
    [true, '', 'Next-password123!', 'Next-password123!', /master password is required/i],
    [true, 'old-password', 'short', 'short', /master password must be at least 8 characters/i],
    [true, 'old-password', 'Next-password123!', 'mismatched', /master passwords do not match/i],
  ] as const)(
    'rejects invalid credentials before calling the server: master=%s, current=%s, next=%s',
    async (master, current, next, confirm, error) => {
      openForm(master);
      fillForm(master, current, next, confirm);
      submit(master);
      expect(await screen.findByText(error)).toBeVisible();
      expect(put).not.toHaveBeenCalled();
    }
  );

  it.each([
    new Error('offline'),
    { response: {} },
    { response: { data: {} } },
    { response: { data: { message: '' } } },
  ])(
    'keeps credentials editable when a master-password request fails without a usable message: %j',
    async error => {
      put.mockRejectedValueOnce(error);
      openForm(true);
      fillForm(true);
      submit(true);
      expect(await screen.findByText(/failed to change master password/i)).toBeVisible();
      expect(screen.getByPlaceholderText(/Enter your new master password/i)).toHaveValue(
        'Next-password123!'
      );
      expect(screen.getByRole('button', { name: /^Change Master Password$/ })).toBeEnabled();
    }
  );

  const responses: Array<{ data?: { encryptionKey?: string } } | undefined> = [
    undefined,
    {},
    { data: {} },
    { data: { encryptionKey: '' } },
    { data: { encryptionKey: 'replacement-key' } },
  ];
  it.each(responses)(
    'replaces the encryption session only when a new key is returned: %j',
    async response => {
      sessionStorage.setItem(STORAGE_KEYS.ENCRYPTION_SESSION, 'original-key');
      put.mockResolvedValueOnce(response);
      openForm(true);
      fillForm(true);
      submit(true);
      expect(await screen.findByText(/master password changed successfully/i)).toBeVisible();
      expect(sessionStorage.getItem(STORAGE_KEYS.ENCRYPTION_SESSION)).toBe(
        response?.data?.encryptionKey || 'original-key'
      );
      expect(
        screen.queryByPlaceholderText(/Enter your new master password/i)
      ).not.toBeInTheDocument();
      expect(localStorage.getItem('auth_token')).not.toBeNull();
    }
  );

  it('expires the master-password success notice after eight seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    put.mockResolvedValueOnce({ data: { encryptionKey: 'replacement-key' } });
    openForm(true);
    fillForm(true);
    submit(true);
    await waitFor(() =>
      expect(screen.getByText(/master password changed successfully/i)).toBeVisible()
    );
    await act(async () => {
      vi.advanceTimersByTime(8000);
    });
    expect(screen.queryByText(/master password changed successfully/i)).not.toBeInTheDocument();
  });
});
