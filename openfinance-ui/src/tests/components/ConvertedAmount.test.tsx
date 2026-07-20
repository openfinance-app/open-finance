/**
 * Tests for ConvertedAmount component
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';

// Mock PrivateAmount to just render children
vi.mock('@/components/ui/PrivateAmount', () => ({
  PrivateAmount: ({ children, className, inline }: any) => (
    <span className={className} data-testid="private-amount" data-inline={inline}>{children}</span>
  )
}));

// Mock formatCurrency and formatExchangeRate
vi.mock('@/utils/currency', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/currency')>();
  return {
    ...actual,
    formatCurrency: vi.fn((amount: number, currency: string, options?: any) => {
      const compact = options?.compact ? 'K' : '';
      return `${currency} ${amount}${compact}`;
    }),
    formatExchangeRate: vi.fn((rate: number) => {
      if (!isFinite(rate) || rate === 0) return '0';
      return parseFloat(rate.toPrecision(6)).toString();
    })
  };
});

// Mock useCurrencyDisplay
const mockUseCurrencyDisplay = vi.fn();
vi.mock('@/context/CurrencyDisplayContext', () => ({
  useCurrencyDisplay: () => mockUseCurrencyDisplay(),
  CurrencyDisplayProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));


// Mock Tooltip
vi.mock('@/components/ui/Tooltip', () => ({
  TooltipProvider: ({ children }: any) => <>{children}</>,
  Tooltip: ({ children }: any) => <>{children}</>,
  TooltipTrigger: ({ children, asChild }: any) => {
    return asChild ? children : <span>{children}</span>;
  },
  TooltipContent: ({ children, id, className }: any) => (
    <div role="tooltip" id={id} className={className}>
      {children}
    </div>
  )
}));

describe('ConvertedAmount', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base' });
  });

  describe('Decision Matrix Scenarios', () => {
    describe('Mode: base', () => {
      beforeEach(() => {
        mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base', secondaryCurrency: 'EUR' });
      });

      it('base | native ≠ base | secondary set → base amount | native + secondary', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="XOF"
            convertedAmount={1.62}
            baseCurrency="USD"
            isConverted={true}
            secondaryAmount={1.45}
            secondaryCurrency="EUR"
          />
        );

        expect(screen.getByText('USD 1.62')).toBeInTheDocument();
        const element = screen.getByTestId('converted-amount');
        expect(element).toHaveAttribute('tabIndex', '0');
        expect(element).toHaveAttribute('aria-describedby');

        // Check tooltip on hover - tooltip is always rendered but CSS controls visibility
        const tooltip = screen.getByRole('tooltip');
        expect(tooltip).toBeInTheDocument();
        expect(tooltip).toHaveTextContent('XOF 1000');
        expect(tooltip).toHaveTextContent('EUR 1.45');
      });

      it('base | native ≠ base | no secondary → base amount | native only', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="XOF"
            convertedAmount={1.62}
            baseCurrency="USD"
            isConverted={true}
          />

        );

        expect(screen.getByText('USD 1.62')).toBeInTheDocument();
        const element = screen.getByTestId('converted-amount');
        expect(element).toHaveAttribute('tabIndex', '0');

        const tooltip = screen.getByRole('tooltip');
        expect(tooltip).toHaveTextContent('XOF 1000');
        expect(screen.queryByText('EUR')).not.toBeInTheDocument();
      });

      it('base | no conversion | secondary set → native (= base) | secondary only', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="USD"
            isConverted={false}
            secondaryAmount={1.45}
            secondaryCurrency="EUR"
          />

        );

        expect(screen.getByText('USD 1000')).toBeInTheDocument();
        const element = screen.getByTestId('converted-amount');
        expect(element).toHaveAttribute('tabIndex', '0');

        act(() => {
          fireEvent.mouseEnter(element);
        });
        expect(screen.getByRole('tooltip')).toHaveTextContent('EUR 1.45');
        expect(screen.queryByText('XOF')).not.toBeInTheDocument();
      });

      it('base | no conversion | no secondary → native (= base) | (none)', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="USD"
            isConverted={false}
          />

        );

        expect(screen.getByText('USD 1000')).toBeInTheDocument();
        expect(screen.getByTestId('private-amount')).not.toHaveAttribute('tabIndex');
        expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
      });
    });

    describe('Mode: native', () => {
      beforeEach(() => {
        mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'native', secondaryCurrency: 'EUR' });
      });

      it('native | native ≠ base | secondary set → native amount | base + secondary', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="XOF"
            convertedAmount={1.62}
            baseCurrency="USD"
            isConverted={true}
            secondaryAmount={1.45}
            secondaryCurrency="EUR"
          />

        );

        expect(screen.getByText('XOF 1000')).toBeInTheDocument();
        const element = screen.getByTestId('converted-amount');
        expect(element).toHaveAttribute('tabIndex', '0');

        act(() => {
          fireEvent.mouseEnter(element);
        });
        expect(screen.getByRole('tooltip')).toHaveTextContent('USD 1.62');
        expect(screen.getByRole('tooltip')).toHaveTextContent('EUR 1.45');
      });

      it('native | native ≠ base | no secondary → native amount | base only', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="XOF"
            convertedAmount={1.62}
            baseCurrency="USD"
            isConverted={true}
          />

        );

        expect(screen.getByText('XOF 1000')).toBeInTheDocument();
        const element = screen.getByTestId('converted-amount');
        expect(element).toHaveAttribute('tabIndex', '0');

        act(() => {
          fireEvent.mouseEnter(element);
        });
        expect(screen.getByRole('tooltip')).toHaveTextContent('USD 1.62');
        expect(screen.queryByText('EUR')).not.toBeInTheDocument();
      });

      it('native | no conversion | secondary set → native (= base) | secondary only', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="USD"
            isConverted={false}
            secondaryAmount={1.45}
            secondaryCurrency="EUR"
          />

        );

        expect(screen.getByText('USD 1000')).toBeInTheDocument();
        const element = screen.getByTestId('converted-amount');
        expect(element).toHaveAttribute('tabIndex', '0');

        act(() => {
          fireEvent.mouseEnter(element);
        });
        expect(screen.getByRole('tooltip')).toHaveTextContent('EUR 1.45');
        expect(screen.queryByText('XOF')).not.toBeInTheDocument();
      });

      it('native | no conversion | no secondary → native (= base) | (none)', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="USD"
            isConverted={false}
          />

        );

        expect(screen.getByText('USD 1000')).toBeInTheDocument();
        expect(screen.getByTestId('private-amount')).not.toHaveAttribute('tabIndex');
        expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
      });
    });

    describe('Mode: both', () => {
      beforeEach(() => {
        mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'both', secondaryCurrency: 'EUR' });
      });

      it('both | native ≠ base | secondary set → base · native inline | secondary only', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="XOF"
            convertedAmount={1.62}
            baseCurrency="USD"
            isConverted={true}
            secondaryAmount={1.45}
            secondaryCurrency="EUR"
          />

        );

        expect(screen.getByText('USD 1.62')).toBeInTheDocument();
        expect(screen.getByText('·')).toBeInTheDocument();
        expect(screen.getByText('XOF 1000')).toBeInTheDocument();
        const element = screen.getByTestId('converted-amount');
        expect(element).toHaveAttribute('tabIndex', '0');

        act(() => {
          fireEvent.mouseEnter(element);
        });
        expect(screen.getByRole('tooltip')).toHaveTextContent('EUR 1.45');
        expect(screen.queryByText('XOF')).not.toBeInTheDocument();
        expect(screen.queryByText('USD')).not.toBeInTheDocument();
      });

      it('both | native ≠ base | no secondary → base · native inline | (none)', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="XOF"
            convertedAmount={1.62}
            baseCurrency="USD"
            isConverted={true}
          />

        );

        expect(screen.getByText('USD 1.62')).toBeInTheDocument();
        expect(screen.getByText('·')).toBeInTheDocument();
        expect(screen.getByText('XOF 1000')).toBeInTheDocument();
        const element = screen.getByTestId('converted-amount');
        expect(element).not.toHaveAttribute('tabIndex');
        expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
      });

      it('both | no conversion | secondary set → native (= base) once | secondary only', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="USD"
            isConverted={false}
            secondaryAmount={1.45}
            secondaryCurrency="EUR"
          />

        );

        expect(screen.getByText('USD 1000')).toBeInTheDocument();
        expect(screen.queryByText('·')).not.toBeInTheDocument();
        const element = screen.getByTestId('converted-amount');
        expect(element).toHaveAttribute('tabIndex', '0');

        act(() => {
          fireEvent.mouseEnter(element);
        });
        expect(screen.getByRole('tooltip')).toHaveTextContent('EUR 1.45');
      });

      it('both | no conversion | no secondary → native (= base) once | (none)', () => {
        renderWithProviders(
          <ConvertedAmount
            amount={1000}
            currency="USD"
            isConverted={false}
          />

        );

        expect(screen.getByText('USD 1000')).toBeInTheDocument();
        expect(screen.queryByText('·')).not.toBeInTheDocument();
        const element = screen.getByTestId('converted-amount');
        expect(element).not.toHaveAttribute('tabIndex');
        expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
      });
    });
  });

  describe('isConverted=false fallback', () => {
    beforeEach(() => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base', secondaryCurrency: 'EUR' });
    });

    it('should always show native, tooltip secondary if available', () => {
      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          isConverted={false}
          secondaryAmount={1.45}
          secondaryCurrency="EUR"
        />

      );

      expect(screen.getByText('XOF 1000')).toBeInTheDocument();
      const element = screen.getByTestId('converted-amount');
      expect(element).toHaveAttribute('tabIndex', '0');

      act(() => {
        fireEvent.mouseEnter(element);
      });
      expect(screen.getByRole('tooltip')).toHaveTextContent('EUR 1.45');
    });
  });

  describe('No badge/icon element rendered', () => {
    it('should not render CurrencyBadge component', () => {
      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          convertedAmount={1.62}
          baseCurrency="USD"
          isConverted={true}
        />

      );

      expect(screen.queryByRole('img')).not.toBeInTheDocument();
    });
  });

  describe('Tooltip attributes', () => {
    it('should have role="tooltip" when present', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base', secondaryCurrency: 'EUR' });
      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          isConverted={false}
          secondaryAmount={1.45}
          secondaryCurrency="EUR"
        />

      );

      expect(screen.getByRole('tooltip')).toBeInTheDocument();
    });

    it('should have tabIndex={0} only when tooltip is present', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base', secondaryCurrency: null });
      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="USD"
          isConverted={false}
        />

      );

      expect(screen.getByTestId('private-amount')).not.toHaveAttribute('tabIndex');
      expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
    });
  });

  describe('Edge cases', () => {
    it('should handle missing conversion data gracefully', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base' });

      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          convertedAmount={undefined}
          baseCurrency={undefined}
          isConverted={undefined}
        />

      );

      expect(screen.getByText('XOF 1000')).toBeInTheDocument();
    });

    it('should apply compact formatting', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base' });

      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          compact={true}
        />

      );

      expect(screen.getByText('XOF 1000K')).toBeInTheDocument();
    });

    it('should apply custom className', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base' });

      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          className="custom-class"
        />

      );

      expect(screen.getByTestId('converted-amount')).toHaveClass('custom-class');
    });

    it('should handle inline prop', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base' });

      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          inline={true}
        />

      );

      expect(screen.getByTestId('private-amount')).toHaveAttribute('data-inline', 'true');
    });
  });

  describe('Exchange rate strings in tooltips', () => {
    it('native mode: base tooltip line includes exchange rate string', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'native', secondaryCurrency: null });

      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          convertedAmount={1.62}
          baseCurrency="USD"
          exchangeRate={0.00162}
          isConverted={true}
        />

      );

      const tooltip = screen.getByRole('tooltip');
      // Amount is present
      expect(tooltip).toHaveTextContent('USD 1.62');
      // Exchange rate string appended
      expect(tooltip).toHaveTextContent('1 XOF = 0.00162 USD');
    });

    it('native mode: secondary tooltip line includes secondary exchange rate string', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'native', secondaryCurrency: 'EUR' });

      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          convertedAmount={1.62}
          baseCurrency="USD"
          exchangeRate={0.00162}
          isConverted={true}
          secondaryAmount={1.45}
          secondaryCurrency="EUR"
          secondaryExchangeRate={0.00145}
        />

      );

      const tooltip = screen.getByRole('tooltip');
      // Both lines with rate strings
      expect(tooltip).toHaveTextContent('USD 1.62');
      expect(tooltip).toHaveTextContent('1 XOF = 0.00162 USD');
      expect(tooltip).toHaveTextContent('EUR 1.45');
      expect(tooltip).toHaveTextContent('1 XOF = 0.00145 EUR');
    });

    it('base mode: native tooltip line includes exchange rate string when available', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base', secondaryCurrency: null });

      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          convertedAmount={1.62}
          baseCurrency="USD"
          exchangeRate={0.00162}
          isConverted={true}
        />

      );

      const tooltip = screen.getByRole('tooltip');
      // Native amount shown with rate annotation (1 XOF = 0.00162 USD)
      expect(tooltip).toHaveTextContent('XOF 1000');
      expect(tooltip).toHaveTextContent('1 XOF = 0.00162 USD');
    });

    it('base mode: native tooltip line has NO rate string when exchangeRate is not provided', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base', secondaryCurrency: null });

      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          convertedAmount={1.62}
          baseCurrency="USD"
          // no exchangeRate prop
          isConverted={true}
        />

      );

      const tooltip = screen.getByRole('tooltip');
      // Native amount shown, but no rate annotation since rate was not provided
      expect(tooltip).toHaveTextContent('XOF 1000');
      expect(tooltip).not.toHaveTextContent('1 XOF =');
    });

    it('base mode: secondary tooltip line includes secondary exchange rate string', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'base', secondaryCurrency: 'EUR' });

      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          convertedAmount={1.62}
          baseCurrency="USD"
          isConverted={true}
          secondaryAmount={1.45}
          secondaryCurrency="EUR"
          secondaryExchangeRate={0.00145}
        />

      );

      const tooltip = screen.getByRole('tooltip');
      expect(tooltip).toHaveTextContent('EUR 1.45');
      expect(tooltip).toHaveTextContent('1 XOF = 0.00145 EUR');
    });

    it('both mode: secondary tooltip line includes secondary exchange rate string', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'both', secondaryCurrency: 'EUR' });

      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          convertedAmount={1.62}
          baseCurrency="USD"
          isConverted={true}
          secondaryAmount={1.45}
          secondaryCurrency="EUR"
          secondaryExchangeRate={0.00145}
        />

      );

      const tooltip = screen.getByRole('tooltip');
      expect(tooltip).toHaveTextContent('EUR 1.45');
      expect(tooltip).toHaveTextContent('1 XOF = 0.00145 EUR');
    });

    it('should omit rate string when exchange rate is not provided', () => {
      mockUseCurrencyDisplay.mockReturnValue({ displayMode: 'native', secondaryCurrency: null });

      renderWithProviders(
        <ConvertedAmount
          amount={1000}
          currency="XOF"
          convertedAmount={1.62}
          baseCurrency="USD"
          // no exchangeRate prop
          isConverted={true}
        />

      );

      const tooltip = screen.getByRole('tooltip');
      expect(tooltip).toHaveTextContent('USD 1.62');
      // No rate string since it wasn't provided
      expect(tooltip).not.toHaveTextContent('1 XOF =');
    });
  });
});