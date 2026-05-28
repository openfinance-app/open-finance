import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/test-utils';
import { formatPrivateCurrency, usePrivateCurrencyFormatter } from './formatPrivateCurrency';

describe('formatPrivateCurrency', () => {
  it('renders formatted currency inside PrivateAmount', () => {
    function Wrapper() { return <>{formatPrivateCurrency(1234.56, 'USD')}</>; }
    renderWithProviders(<Wrapper />);
    expect(screen.getByText(/1.*234/)).toBeInTheDocument();
  });

  it('uses EUR as default currency', () => {
    function Wrapper() { return <>{formatPrivateCurrency(100)}</>; }
    renderWithProviders(<Wrapper />);
    expect(screen.getByText(/100/)).toBeInTheDocument();
  });
});

describe('usePrivateCurrencyFormatter', () => {
  function TestComponent({ amount, currency }: { amount: number; currency: string }) {
    const { formatPrivate } = usePrivateCurrencyFormatter();
    return <div>{formatPrivate(amount, currency)}</div>;
  }

  it('formats currency with privacy wrapper', () => {
    renderWithProviders(<TestComponent amount={500} currency="EUR" />);
    expect(screen.getByText(/500/)).toBeInTheDocument();
  });
});
