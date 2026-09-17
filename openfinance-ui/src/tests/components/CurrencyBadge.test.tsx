/**
 * Tests for CurrencyBadge component
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CurrencyBadge } from '@/components/ui/CurrencyBadge';

describe('CurrencyBadge', () => {
  it('should render nothing (null) — REQ-9.1', () => {
    const { container } = render(<CurrencyBadge fromCurrency="XOF" toCurrency="USD" />);

    expect(container.firstChild).toBeNull();
  });
});
