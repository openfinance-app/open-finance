import { useState, useEffect } from 'react';

/**
 * Breakpoint types matching Tailwind CSS breakpoints
 */
export type Breakpoint = 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl';

/**
 * Breakpoint pixel values
 */
const breakpoints = {
  xs: 0,
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
  '2xl': 1536,
} as const;

/**
 * Hook to detect current responsive breakpoint
 * Returns the current breakpoint based on window width
 *
 * @example
 * const breakpoint = useBreakpoint();
 * if (breakpoint === 'xs' || breakpoint === 'sm') {
 *   // Mobile layout
 * }
 */
export function useBreakpoint(): Breakpoint {
  const getBreakpoint = (width: number): Breakpoint => {
    if (width >= breakpoints['2xl']) return '2xl';
    if (width >= breakpoints.xl) return 'xl';
    if (width >= breakpoints.lg) return 'lg';
    if (width >= breakpoints.md) return 'md';
    if (width >= breakpoints.sm) return 'sm';
    return 'xs';
  };

  const [breakpoint, setBreakpoint] = useState<Breakpoint>(() => getBreakpoint(window.innerWidth));

  useEffect(() => {
    const handleResize = () => {
      setBreakpoint(getBreakpoint(window.innerWidth));
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  return breakpoint;
}

/**
 * Hook to check if current breakpoint matches a condition
 *
 * @example
 * const isMobile = useBreakpointValue('xs', 'sm');
 * const isDesktop = useBreakpointValue('lg', 'xl', '2xl');
 */
export function useBreakpointValue(...breakpointsToMatch: Breakpoint[]): boolean {
  const currentBreakpoint = useBreakpoint();
  return breakpointsToMatch.includes(currentBreakpoint);
}

/**
 * Hook to check if viewport is mobile (< 768px)
 */
export function useIsMobile(): boolean {
  return useBreakpointValue('xs', 'sm');
}

/**
 * Hook to check if viewport is tablet (768px - 1024px)
 */
export function useIsTablet(): boolean {
  return useBreakpointValue('md');
}

/**
 * Hook to check if viewport is desktop (> 1024px)
 */
export function useIsDesktop(): boolean {
  return useBreakpointValue('lg', 'xl', '2xl');
}
