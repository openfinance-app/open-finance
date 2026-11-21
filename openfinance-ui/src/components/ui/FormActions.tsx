import type { ReactNode } from 'react';

export function FormActions({ children, error }: { children: ReactNode; error?: string | null }) {
  return (
    <div className="form-actions">
      {error && (
        <p
          role="alert"
          className="w-full min-w-0 max-h-[25dvh] overflow-y-auto rounded-md border border-error/30 bg-error/10 px-3 py-2 text-sm text-error [overflow-wrap:anywhere]"
        >
          {error}
        </p>
      )}
      {children}
    </div>
  );
}
