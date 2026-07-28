/**
 * ProtectedRoute - Route guard for authenticated routes
 *
 * Implements TASK-1.3.13:
 * - Check authentication before rendering protected content
 * - Redirect to login if not authenticated
 * - Show loading state while checking auth
 * - Wraps content in AppLayout with sidebar and top bar (TASK-4.3.14, 4.4)
 *
 * Requirements: REQ-2.1.3, REQ-2.1.4 (User Authentication)
 */
import React from 'react';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { ROUTES } from '@/constants/routes';
import { useAuthContext } from '@/context/AuthContext';
import { LoadingSpinner } from '@/components/LoadingComponents';
import { AppLayout } from '@/components/layout/AppLayout';

interface ProtectedRouteProps {
  children: ReactNode;
}

/**
 * ProtectedRoute component
 * Wraps routes that require authentication.
 * Memoised to avoid unnecessary re-renders when parent context values change
 * but auth state is stable.
 *
 * Usage:
 * ```tsx
 * <Route path="/dashboard" element={
 *   <ProtectedRoute>
 *     <DashboardPage />
 *   </ProtectedRoute>
 * } />
 * ```
 */
export const ProtectedRoute = React.memo(function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated, isLoading } = useAuthContext();
  const location = useLocation();

  // Show loading spinner while checking auth state
  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  // Redirect to login if not authenticated
  // Preserve the attempted location so we can redirect back after login
  if (!isAuthenticated) {
    return <Navigate to={ROUTES.LOGIN} state={{ from: location }} replace />;
  }

  // Render protected content wrapped in app layout
  return (
    <AppLayout>
      {children}
    </AppLayout>
  );
});
