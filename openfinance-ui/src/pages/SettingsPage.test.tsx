import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, act } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication, userEvent } from '@/test/test-utils';
import SettingsPage from '@/pages/SettingsPage';

let capturedOnDirtyChange: ((dirty: boolean) => void) | undefined;

vi.mock('@/components/settings/GeneralSettings', () => ({
  GeneralSettings: ({ onHasChanges }: { onHasChanges?: (v: boolean) => void }) => {
    capturedOnDirtyChange = onHasChanges;
    return <div data-testid="general-settings">General Settings</div>;
  },
}));
vi.mock('@/components/settings/DisplaySettings', () => ({
  DisplaySettings: () => <div data-testid="display-settings">Display Settings</div>,
}));
vi.mock('@/components/settings/SecuritySettings', () => ({
  SecuritySettings: () => <div data-testid="security-settings">Security Settings</div>,
}));
vi.mock('@/components/settings/BackupSettings', () => ({
  BackupSettings: () => <div data-testid="backup-settings">Backup Settings</div>,
}));

describe('SettingsPage', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    capturedOnDirtyChange = undefined;
  });

  it('renders the page heading', () => {
    renderWithProviders(<SettingsPage />);
    expect(document.querySelector('h1')).toBeInTheDocument();
  });

  it('shows general settings by default', () => {
    renderWithProviders(<SettingsPage />);
    expect(screen.getByTestId('general-settings')).toBeInTheDocument();
  });

  it('switches to security tab', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SettingsPage />);

    const securityTab = screen.getByRole('button', { name: /security/i });
    await user.click(securityTab);
    expect(screen.getByTestId('security-settings')).toBeInTheDocument();
  });

  it('switches to display tab', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SettingsPage />);

    const displayTab = screen.getByRole('button', { name: /display/i });
    await user.click(displayTab);
    expect(screen.getByTestId('display-settings')).toBeInTheDocument();
  });

  it('switches to backup tab', async () => {
    const user = userEvent.setup();
    renderWithProviders(<SettingsPage />);

    const backupTab = screen.getByRole('button', { name: /backup/i });
    await user.click(backupTab);
    expect(screen.getByTestId('backup-settings')).toBeInTheDocument();
  });

  it('blocks tab switch when general has unsaved changes and user cancels confirm', async () => {
    const user = userEvent.setup();
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderWithProviders(<SettingsPage />);

    // Mark general as dirty via the captured callback
    act(() => {
      capturedOnDirtyChange?.(true);
    });

    const securityTab = screen.getByRole('button', { name: /security/i });
    await user.click(securityTab);

    // Should still show general settings because user cancelled
    expect(screen.getByTestId('general-settings')).toBeInTheDocument();
    expect(window.confirm).toHaveBeenCalled();
  });

  it('allows tab switch when general has unsaved changes and user confirms', async () => {
    const user = userEvent.setup();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderWithProviders(<SettingsPage />);

    act(() => {
      capturedOnDirtyChange?.(true);
    });

    const securityTab = screen.getByRole('button', { name: /security/i });
    await user.click(securityTab);

    expect(screen.getByTestId('security-settings')).toBeInTheDocument();
    expect(window.confirm).toHaveBeenCalled();
  });

  it('registers beforeunload handler when general has unsaved changes', () => {
    const addSpy = vi.spyOn(window, 'addEventListener');
    renderWithProviders(<SettingsPage />);

    act(() => {
      capturedOnDirtyChange?.(true);
    });

    expect(addSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function));
  });
});
