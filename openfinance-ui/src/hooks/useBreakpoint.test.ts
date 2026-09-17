import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  useBreakpoint,
  useBreakpointValue,
  useIsMobile,
  useIsTablet,
  useIsDesktop,
} from './useBreakpoint';

// Helper to set window.innerWidth and trigger resize
function setWindowWidth(width: number) {
  Object.defineProperty(window, 'innerWidth', {
    writable: true,
    configurable: true,
    value: width,
  });
  window.dispatchEvent(new Event('resize'));
}

describe('useBreakpoint', () => {
  const originalInnerWidth = window.innerWidth;

  afterEach(() => {
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: originalInnerWidth,
    });
  });

  it('should return "xs" for widths below 640px', () => {
    setWindowWidth(320);
    const { result } = renderHook(() => useBreakpoint());
    expect(result.current).toBe('xs');
  });

  it('should return "sm" for widths between 640px and 767px', () => {
    setWindowWidth(640);
    const { result } = renderHook(() => useBreakpoint());
    expect(result.current).toBe('sm');
  });

  it('should return "md" for widths between 768px and 1023px', () => {
    setWindowWidth(768);
    const { result } = renderHook(() => useBreakpoint());
    expect(result.current).toBe('md');
  });

  it('should return "lg" for widths between 1024px and 1279px', () => {
    setWindowWidth(1024);
    const { result } = renderHook(() => useBreakpoint());
    expect(result.current).toBe('lg');
  });

  it('should return "xl" for widths between 1280px and 1535px', () => {
    setWindowWidth(1280);
    const { result } = renderHook(() => useBreakpoint());
    expect(result.current).toBe('xl');
  });

  it('should return "2xl" for widths 1536px and above', () => {
    setWindowWidth(1536);
    const { result } = renderHook(() => useBreakpoint());
    expect(result.current).toBe('2xl');
  });

  it('should update breakpoint when window is resized', () => {
    setWindowWidth(1024);
    const { result } = renderHook(() => useBreakpoint());
    expect(result.current).toBe('lg');

    act(() => {
      setWindowWidth(400);
    });
    expect(result.current).toBe('xs');
  });

  it('should remove resize event listener on unmount', () => {
    const removeEventListenerSpy = vi.spyOn(window, 'removeEventListener');
    setWindowWidth(1024);

    const { unmount } = renderHook(() => useBreakpoint());
    unmount();

    expect(removeEventListenerSpy).toHaveBeenCalledWith('resize', expect.any(Function));
    removeEventListenerSpy.mockRestore();
  });
});

describe('useBreakpointValue', () => {
  afterEach(() => {
    Object.defineProperty(window, 'innerWidth', {
      writable: true,
      configurable: true,
      value: 1024,
    });
  });

  it('should return true when current breakpoint matches one of the provided breakpoints', () => {
    setWindowWidth(400); // xs
    const { result } = renderHook(() => useBreakpointValue('xs', 'sm'));
    expect(result.current).toBe(true);
  });

  it('should return false when current breakpoint does not match', () => {
    setWindowWidth(1200); // xl
    const { result } = renderHook(() => useBreakpointValue('xs', 'sm'));
    expect(result.current).toBe(false);
  });
});

describe('useIsMobile', () => {
  it('should return true for mobile widths (< 768px)', () => {
    setWindowWidth(375);
    const { result } = renderHook(() => useIsMobile());
    expect(result.current).toBe(true);
  });

  it('should return false for non-mobile widths (>= 768px)', () => {
    setWindowWidth(1024);
    const { result } = renderHook(() => useIsMobile());
    expect(result.current).toBe(false);
  });
});

describe('useIsTablet', () => {
  it('should return true for tablet widths (768px - 1023px)', () => {
    setWindowWidth(800);
    const { result } = renderHook(() => useIsTablet());
    expect(result.current).toBe(true);
  });

  it('should return false for non-tablet widths', () => {
    setWindowWidth(1200);
    const { result } = renderHook(() => useIsTablet());
    expect(result.current).toBe(false);
  });
});

describe('useIsDesktop', () => {
  it('should return true for desktop widths (>= 1024px)', () => {
    setWindowWidth(1440);
    const { result } = renderHook(() => useIsDesktop());
    expect(result.current).toBe(true);
  });

  it('should return false for non-desktop widths (< 1024px)', () => {
    setWindowWidth(768);
    const { result } = renderHook(() => useIsDesktop());
    expect(result.current).toBe(false);
  });
});
