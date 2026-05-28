import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { ReactNode } from 'react';
import { SidebarProvider, useSidebar } from './SidebarContext';

function wrapper({ children }: { children: ReactNode }) {
  return <SidebarProvider>{children}</SidebarProvider>;
}

describe('SidebarContext', () => {
  it('provides default collapsed state as false', () => {
    const { result } = renderHook(() => useSidebar(), { wrapper });
    expect(result.current.isCollapsed).toBe(false);
  });

  it('toggleCollapsed flips the state', () => {
    const { result } = renderHook(() => useSidebar(), { wrapper });

    act(() => {
      result.current.toggleCollapsed();
    });
    expect(result.current.isCollapsed).toBe(true);

    act(() => {
      result.current.toggleCollapsed();
    });
    expect(result.current.isCollapsed).toBe(false);
  });

  it('setIsCollapsed with a boolean value', () => {
    const { result } = renderHook(() => useSidebar(), { wrapper });

    act(() => {
      result.current.setIsCollapsed(true);
    });
    expect(result.current.isCollapsed).toBe(true);

    act(() => {
      result.current.setIsCollapsed(false);
    });
    expect(result.current.isCollapsed).toBe(false);
  });

  it('setIsCollapsed with an updater function', () => {
    const { result } = renderHook(() => useSidebar(), { wrapper });

    act(() => {
      result.current.setIsCollapsed((prev) => !prev);
    });
    expect(result.current.isCollapsed).toBe(true);
  });

  it('throws when used outside SidebarProvider', () => {
    // Suppress React error boundary console output
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => {
      renderHook(() => useSidebar());
    }).toThrow('useSidebar must be used within a SidebarProvider');
    spy.mockRestore();
  });
});
