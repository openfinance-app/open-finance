import { StrictMode } from 'react';
import { act, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { AnimatedNumber } from '@/components/ui/AnimatedNumber';

describe('AnimatedNumber', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.useFakeTimers();
  });
  afterEach(() => vi.useRealTimers());

  it('settles to the exact total in Strict Mode and reformats an unchanged value', () => {
    const format = (value: number) => value.toFixed(2);
    const { rerender } = renderWithProviders(
      <StrictMode>
        <AnimatedNumber value={12199.23} format={format} />
      </StrictMode>
    );
    rerender(
      <StrictMode>
        <AnimatedNumber value={12149.25} format={format} />
      </StrictMode>
    );
    act(() => vi.advanceTimersByTime(1200));
    expect(screen.getByText('12149.25')).toBeInTheDocument();
    rerender(
      <StrictMode>
        <AnimatedNumber value={12149.25} format={value => value.toFixed(2).replace('.', ',')} />
      </StrictMode>
    );
    expect(screen.getByText('12149,25')).toBeInTheDocument();
  });
});
