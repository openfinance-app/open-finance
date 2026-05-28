import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, userEvent } from '@/test/test-utils';

let mockBackups: any[] = [];
let mockIsLoading = false;

vi.mock('@/hooks/useBackup', () => ({
  useListBackups: () => ({ data: mockBackups, isLoading: mockIsLoading }),
}));
vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({ data: { dateFormat: 'YYYY-MM-DD' } }),
  useUpdateUserSettings: () => ({ mutateAsync: vi.fn() }),
}));

const mockNavigate = vi.fn();
vi.mock('react-router', async () => {
  const actual = await vi.importActual('react-router');
  return { ...actual, useNavigate: () => mockNavigate };
});

import { BackupSettings } from './BackupSettings';

describe('BackupSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockBackups = [];
    mockIsLoading = false;
  });

  it('renders the manage backups button', () => {
    renderWithProviders(<BackupSettings />);
    expect(screen.getByRole('button', { name: /manage backups/i })).toBeInTheDocument();
  });

  it('shows loading skeleton when loading', () => {
    mockIsLoading = true;
    renderWithProviders(<BackupSettings />);
    expect(document.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('displays backup statistics with completed backups', () => {
    mockBackups = [
      { id: 1, status: 'COMPLETED', backupType: 'MANUAL', createdAt: new Date().toISOString(), fileSize: 1024 * 1024 },
      { id: 2, status: 'COMPLETED', backupType: 'AUTOMATIC', createdAt: new Date(Date.now() - 86400000).toISOString(), fileSize: 2048 * 1024 },
      { id: 3, status: 'FAILED', backupType: 'MANUAL', createdAt: new Date().toISOString(), fileSize: 0 },
    ];
    renderWithProviders(<BackupSettings />);
    // Should show count of completed backups (2, not 3)
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('shows never when no backups exist', () => {
    mockBackups = [];
    renderWithProviders(<BackupSettings />);
    expect(screen.getByText(/never/i)).toBeInTheDocument();
  });

  it('shows latest backup time', () => {
    const now = new Date();
    mockBackups = [
      { id: 1, status: 'COMPLETED', backupType: 'MANUAL', createdAt: now.toISOString(), fileSize: 512 },
    ];
    renderWithProviders(<BackupSettings />);
    // Should show a relative time like "just now" or "X minutes ago"
    expect(screen.queryByText(/never/i)).not.toBeInTheDocument();
  });

  it('formats storage size correctly', () => {
    mockBackups = [
      { id: 1, status: 'COMPLETED', backupType: 'MANUAL', createdAt: new Date().toISOString(), fileSize: 1048576 }, // 1 MB
    ];
    renderWithProviders(<BackupSettings />);
    expect(screen.getByText('1.00 MB')).toBeInTheDocument();
  });

  it('shows automatic backup count', () => {
    mockBackups = [
      { id: 1, status: 'COMPLETED', backupType: 'AUTOMATIC', createdAt: new Date().toISOString(), fileSize: 100 },
      { id: 2, status: 'COMPLETED', backupType: 'AUTOMATIC', createdAt: new Date().toISOString(), fileSize: 200 },
    ];
    renderWithProviders(<BackupSettings />);
    // Should show automatic count text
    const autoTexts = screen.getAllByText(/automatic/i);
    expect(autoTexts.length).toBeGreaterThanOrEqual(1);
  });

  it('navigates to backup page on manage button click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BackupSettings />);
    await user.click(screen.getByRole('button', { name: /manage backups/i }));
    expect(mockNavigate).toHaveBeenCalledWith('/backup');
  });

  it('shows backup type label for latest backup', () => {
    mockBackups = [
      { id: 1, status: 'COMPLETED', backupType: 'AUTOMATIC', createdAt: new Date().toISOString(), fileSize: 500 },
    ];
    renderWithProviders(<BackupSettings />);
    // Should show automatic type label
    const texts = screen.getAllByText(/automatic/i);
    expect(texts.length).toBeGreaterThanOrEqual(1);
  });

  it('shows 0 B when no storage used', () => {
    mockBackups = [];
    renderWithProviders(<BackupSettings />);
    expect(screen.getByText('0 B')).toBeInTheDocument();
  });
});
