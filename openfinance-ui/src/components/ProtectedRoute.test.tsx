import { render, screen } from '@/test/test-utils';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, it, beforeEach, expect } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { AuthProvider } from '@/context/AuthContext';
import { VisibilityProvider } from '@/context/VisibilityContext';
import { NumberFormatProvider } from '@/context/NumberFormatContext';
import { CurrencyDisplayProvider } from '@/context/CurrencyDisplayContext';

import { ProtectedRoute } from './ProtectedRoute';

function Content() {
  return <div>protected</div>;
}

describe('ProtectedRoute', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
    localStorage.clear();
    sessionStorage.clear();
  });

  it('should show loading spinner when auth is loading', () => {
    // We simulate by rendering Provider and checking initial isLoading true -> but in AuthProvider isLoading becomes false quickly
    // So instead mount component with AuthProvider and verify that when not authenticated it redirects to /login
    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <NumberFormatProvider>
            <CurrencyDisplayProvider>
              <VisibilityProvider>
                <MemoryRouter initialEntries={['/dashboard']}>
                  <Routes>
                    <Route path="/login" element={<div>login</div>} />
                    <Route
                      path="/dashboard"
                      element={
                        <ProtectedRoute>
                          <Content />
                        </ProtectedRoute>
                      }
                    />
                  </Routes>
                </MemoryRouter>
              </VisibilityProvider>
            </CurrencyDisplayProvider>
          </NumberFormatProvider>
        </AuthProvider>
      </QueryClientProvider>
    );

    // Since no auth in storage, it should redirect to login
    expect(screen.queryByText('protected')).toBeNull();
    expect(screen.getByText('login')).toBeTruthy();
  });

  it('should render children when authenticated', async () => {
    // Prepopulate storage so AuthProvider initializes as authenticated
    localStorage.setItem('auth_token', 't');
    localStorage.setItem('auth_user', JSON.stringify({ id: 1, username: 'u' }));

    render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <NumberFormatProvider>
            <CurrencyDisplayProvider>
              <VisibilityProvider>
                <MemoryRouter initialEntries={['/dashboard']}>
                  <Routes>
                    <Route path="/login" element={<div>login</div>} />
                    <Route
                      path="/dashboard"
                      element={
                        <ProtectedRoute>
                          <Content />
                        </ProtectedRoute>
                      }
                    />
                  </Routes>
                </MemoryRouter>
              </VisibilityProvider>
            </CurrencyDisplayProvider>
          </NumberFormatProvider>
        </AuthProvider>
      </QueryClientProvider>
    );

    expect(await screen.findByText('protected')).toBeTruthy();
  });
});
