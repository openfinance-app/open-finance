import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';

let mockSettingsData: any = { theme: 'dark' };
let mockIsLoading = false;
const mockMutate = vi.fn();
const mockMutateAsync = vi.fn().mockResolvedValue({});

vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({
    data: mockSettingsData,
    isLoading: mockIsLoading,
  }),
  useUpdateUserSettings: () => ({
    mutate: mockMutate,
    mutateAsync: mockMutateAsync,
  }),
}));

import { ThemeProvider, useTheme } from './ThemeContext';

function wrapper({ children }: { children: ReactNode }) {
  return <ThemeProvider>{children}</ThemeProvider>;
}

describe('ThemeContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSettingsData = { theme: 'dark' };
    mockIsLoading = false;
    document.documentElement.classList.remove('light', 'dark');
  });

  it('provides dark theme by default', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(result.current.theme).toBe('dark');
  });

  it('provides setTheme function', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(typeof result.current.setTheme).toBe('function');
  });

  it('provides isLoading', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(result.current.isLoading).toBe(false);
  });

  it('throws when used outside provider', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => {
      renderHook(() => useTheme());
    }).toThrow('useTheme must be used within a ThemeProvider');
    spy.mockRestore();
  });

  it('applies light class to html when settings say light', () => {
    mockSettingsData = { theme: 'light' };
    renderHook(() => useTheme(), { wrapper });
    expect(document.documentElement.classList.contains('light')).toBe(true);
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });

  it('applies dark class to html when settings say dark', () => {
    mockSettingsData = { theme: 'dark' };
    renderHook(() => useTheme(), { wrapper });
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.classList.contains('light')).toBe(false);
  });

  it('setTheme updates theme optimistically and calls mutate', () => {
    const { result } = renderHook(() => useTheme(), { wrapper });

    act(() => {
      result.current.setTheme('light');
    });

    expect(result.current.theme).toBe('light');
    expect(document.documentElement.classList.contains('light')).toBe(true);
    expect(mockMutate).toHaveBeenCalledWith(
      { theme: 'light' },
      expect.objectContaining({ onError: expect.any(Function) })
    );
  });

  it('reverts theme on mutation error', () => {
    mockSettingsData = { theme: 'dark' };
    const { result } = renderHook(() => useTheme(), { wrapper });

    act(() => {
      result.current.setTheme('light');
    });

    expect(result.current.theme).toBe('light');

    // Simulate error callback
    const onError = mockMutate.mock.calls[0][1].onError;
    act(() => {
      onError();
    });

    expect(result.current.theme).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('reports isLoading true when settings are loading', () => {
    mockIsLoading = true;
    const { result } = renderHook(() => useTheme(), { wrapper });
    expect(result.current.isLoading).toBe(true);
  });
});
