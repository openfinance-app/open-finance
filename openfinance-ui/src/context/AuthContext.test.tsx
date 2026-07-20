import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, beforeEach, expect } from 'vitest';

import { AuthProvider, useAuthContext } from './AuthContext';
import { VisibilityProvider } from './VisibilityContext';

function Consumer() {
  const { user, isAuthenticated, isLoading, token, sessionStartTime, baseCurrency, setAuth, clearAuth } = useAuthContext();
  return (
    <div>
      <div>loading:{String(isLoading)}</div>
      <div>auth:{String(isAuthenticated)}</div>
      <div>user:{user ? user.username : 'null'}</div>
      <div>token:{token ?? 'null'}</div>
      <div>baseCurrency:{baseCurrency}</div>
      <div>sessionStartTime:{sessionStartTime ?? 'null'}</div>
      <button onClick={() => setAuth({ id: 2, username: 'bob', email: '', createdAt: new Date().toISOString() } as any, 'tkn', true)}>set</button>
      <button onClick={() => clearAuth()}>clear</button>
    </div>
  );
}

describe('AuthProvider', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('should initialize empty auth state when storage empty', async () => {
    render(
      <VisibilityProvider>
        <AuthProvider>
          <Consumer />
        </AuthProvider>
      </VisibilityProvider>
    );

    await waitFor(() => expect(screen.getByText(/loading:false/)).toBeTruthy());
    expect(screen.getByText(/auth:false/)).toBeTruthy();
    expect(screen.getByText(/user:null/)).toBeTruthy();
    expect(screen.getByText(/token:null/)).toBeTruthy();
  });

  it('should default baseCurrency to EUR when unauthenticated', async () => {
    render(
      <VisibilityProvider>
        <AuthProvider>
          <Consumer />
        </AuthProvider>
      </VisibilityProvider>
    );

    await waitFor(() => expect(screen.getByText(/loading:false/)).toBeTruthy());
    expect(screen.getByText(/baseCurrency:EUR/)).toBeTruthy();
  });

  it('setAuth should update context and persist to localStorage', async () => {
    render(
      <VisibilityProvider>
        <AuthProvider>
          <Consumer />
        </AuthProvider>
      </VisibilityProvider>
    );

    fireEvent.click(screen.getByText('set'));

    await waitFor(() => expect(screen.getByText(/auth:true/)).toBeTruthy());
    expect(screen.getByText(/user:bob/)).toBeTruthy();
    expect(screen.getByText(/token:tkn/)).toBeTruthy();

    // persisted
    expect(localStorage.getItem('auth_token')).toBe('tkn');
    const storedUser = JSON.parse(localStorage.getItem('auth_user') || '{}');
    expect(storedUser.username).toBe('bob');
  });

  it('clearAuth should reset context and clear storages', async () => {
    render(
      <VisibilityProvider>
        <AuthProvider>
          <Consumer />
        </AuthProvider>
      </VisibilityProvider>
    );

    // set then clear
    fireEvent.click(screen.getByText('set'));
    await waitFor(() => expect(screen.getByText(/auth:true/)).toBeTruthy());

    fireEvent.click(screen.getByText('clear'));
    await waitFor(() => expect(screen.getByText(/auth:false/)).toBeTruthy());

    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(localStorage.getItem('auth_user')).toBeNull();
    expect(sessionStorage.getItem('encryption_session')).toBeNull();
  });

  it('should handle invalid stored JSON by clearing storage', async () => {
    // Put invalid JSON into storage
    localStorage.setItem('auth_token', 'x');
    localStorage.setItem('auth_user', '{not:valid');

    render(
      <VisibilityProvider>
        <AuthProvider>
          <Consumer />
        </AuthProvider>
      </VisibilityProvider>
    );

    // initialization should catch error and clear invalid items
    await waitFor(() => expect(localStorage.getItem('auth_token')).toBeNull());
    expect(localStorage.getItem('auth_user')).toBeNull();
  });

  it('sessionStartTime should be null when storage is empty', async () => {
    render(
      <VisibilityProvider>
        <AuthProvider>
          <Consumer />
        </AuthProvider>
      </VisibilityProvider>
    );

    await waitFor(() => expect(screen.getByText(/loading:false/)).toBeTruthy());
    expect(screen.getByText(/sessionStartTime:null/)).toBeTruthy();
    expect(sessionStorage.getItem('session_start_time')).toBeNull();
  });

  it('setAuth should write session_start_time to sessionStorage and expose in context', async () => {
    render(
      <VisibilityProvider>
        <AuthProvider>
          <Consumer />
        </AuthProvider>
      </VisibilityProvider>
    );

    fireEvent.click(screen.getByText('set'));

    await waitFor(() => expect(screen.getByText(/auth:true/)).toBeTruthy());

    const stored = sessionStorage.getItem('session_start_time');
    expect(stored).toBeTruthy();
    // Verify the component renders the stored timestamp (use textContent to avoid regex escaping issues)
    const el = screen.getByText(/sessionStartTime:/);
    expect(el.textContent).toContain(stored);
  });

  it('clearAuth should remove session_start_time from sessionStorage and set to null in context', async () => {
    render(
      <VisibilityProvider>
        <AuthProvider>
          <Consumer />
        </AuthProvider>
      </VisibilityProvider>
    );

    fireEvent.click(screen.getByText('set'));
    await waitFor(() => expect(screen.getByText(/auth:true/)).toBeTruthy());

    fireEvent.click(screen.getByText('clear'));
    await waitFor(() => expect(screen.getByText(/auth:false/)).toBeTruthy());

    expect(sessionStorage.getItem('session_start_time')).toBeNull();
    expect(screen.getByText(/sessionStartTime:null/)).toBeTruthy();
  });
});
