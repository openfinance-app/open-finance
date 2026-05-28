import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';

const mockNavigate = vi.fn();
const mockClearAuth = vi.fn();

vi.mock('@/context/AuthContext', () => ({
  useAuthContext: () => ({
    user: { username: 'John Doe', profileImage: null },
    clearAuth: mockClearAuth,
    baseCurrency: 'USD',
  }),
}));

vi.mock('react-router', async () => {
  const actual = await vi.importActual('react-router');
  return { ...actual, useNavigate: () => mockNavigate };
});

import { UserDropdownMenu } from './UserDropdownMenu';

function Wrapper({ children }: { children: React.ReactNode }) {
  return (
    <I18nextProvider i18n={i18n}>
      <BrowserRouter>{children}</BrowserRouter>
    </I18nextProvider>
  );
}

describe('UserDropdownMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders user initials button', () => {
    render(<UserDropdownMenu />, { wrapper: Wrapper });
    expect(screen.getByText('JD')).toBeInTheDocument();
  });

  it('opens dropdown on click', () => {
    render(<UserDropdownMenu />, { wrapper: Wrapper });
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Profile')).toBeInTheDocument();
    expect(screen.getByText('Settings')).toBeInTheDocument();
  });

  it('shows logout option in dropdown', () => {
    render(<UserDropdownMenu />, { wrapper: Wrapper });
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Logout')).toBeInTheDocument();
  });

  it('closes dropdown on Escape key', () => {
    render(<UserDropdownMenu />, { wrapper: Wrapper });
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Profile')).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByText('Profile')).not.toBeInTheDocument();
  });

  it('navigates to profile on Profile click', () => {
    render(<UserDropdownMenu />, { wrapper: Wrapper });
    fireEvent.click(screen.getByRole('button'));
    fireEvent.click(screen.getByText('Profile'));
    expect(mockNavigate).toHaveBeenCalledWith('/profile');
  });

  it('navigates to settings on Settings click', () => {
    render(<UserDropdownMenu />, { wrapper: Wrapper });
    fireEvent.click(screen.getByRole('button'));
    fireEvent.click(screen.getByText('Settings'));
    expect(mockNavigate).toHaveBeenCalledWith('/settings');
  });

  it('calls clearAuth and navigates on logout', () => {
    render(<UserDropdownMenu />, { wrapper: Wrapper });
    fireEvent.click(screen.getByRole('button'));
    fireEvent.click(screen.getByText('Logout'));
    expect(mockClearAuth).toHaveBeenCalled();
    expect(mockNavigate).toHaveBeenCalledWith('/login');
  });

  it('opens help link in new tab', () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    render(<UserDropdownMenu />, { wrapper: Wrapper });
    fireEvent.click(screen.getByRole('button'));
    fireEvent.click(screen.getByText('Help'));
    expect(openSpy).toHaveBeenCalledWith(expect.stringContaining('github.com'), '_blank');
  });

  it('closes dropdown on outside click', () => {
    render(<UserDropdownMenu />, { wrapper: Wrapper });
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Profile')).toBeInTheDocument();
    fireEvent.mouseDown(document.body);
    expect(screen.queryByText('Profile')).not.toBeInTheDocument();
  });
});
