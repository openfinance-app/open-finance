import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import apiClient from '@/services/apiClient';

import {
  resolveEncryptionEnabled,
  useSecurityConfig,
  type SecurityConfig,
} from './useSecurityConfig';

vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn(),
  },
}));

function createWrapper(): ({ children }: { children: ReactNode }) => JSX.Element {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe('useSecurityConfig', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches security config and returns disabled encryption mode from the API', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { encryptionEnabled: false } });

    const { result } = renderHook(() => useSecurityConfig(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.data?.encryptionEnabled).toBe(false));

    expect(apiClient.get).toHaveBeenCalledWith('/config/security');
  });

  it('resolves encryption mode with fail-closed defaults', () => {
    expect(resolveEncryptionEnabled(undefined, false)).toBe(true);
    expect(resolveEncryptionEnabled(null, false)).toBe(true);
    expect(resolveEncryptionEnabled({} as SecurityConfig, false)).toBe(true);
    expect(
      resolveEncryptionEnabled({ encryptionEnabled: null } as unknown as SecurityConfig, false)
    ).toBe(true);
    expect(resolveEncryptionEnabled({ encryptionEnabled: false }, true)).toBe(true);
    expect(resolveEncryptionEnabled(undefined, true)).toBe(true);
    expect(resolveEncryptionEnabled({ encryptionEnabled: false }, false)).toBe(false);
  });
});
