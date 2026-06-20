import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('@/context/CurrencyDisplayContext', () => ({
  useCurrencyDisplay: vi.fn(),
}));

vi.mock('@/context/NumberFormatContext', () => ({
  useNumberFormat: vi.fn(),
}));

vi.mock('@/components/ui/PrivateAmount', () => ({
  PrivateAmount: ({ children }: { children: React.ReactNode }) => <span data-testid="private-amount">{children}</span>,
}));

import { ConvertedAmount } from './ConvertedAmount';
import { useCurrencyDisplay } from '@/context/CurrencyDisplayContext';
import { useNumberFormat } from '@/context/NumberFormatContext';

const mockCurrencyDisplay = useCurrencyDisplay as ReturnType<typeof vi.fn>;
const mockNumberFormat = useNumberFormat as ReturnType<typeof vi.fn>;


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
  beforeEach(() => {
    vi.clearAllMocks();
    mockCurrencyDisplay.mockReturnValue({
      displayMode: 'base',
      secondaryCurrency: null,
    });
    mockNumberFormat.mockReturnValue({
      numberFormat: 'COMMA_DOT',
    });
  });

  it('renders native amount when not converted in base mode', () => {
    render(<ConvertedAmount amount={100} currency="USD" />);
    expect(screen.getByTestId('private-amount')).toHaveTextContent('$100');
  });

  it('renders converted base amount in base mode when isConverted', () => {
    render(
      <ConvertedAmount
        amount={65000}
        currency="XOF"
        convertedAmount={100}
        baseCurrency="USD"
        exchangeRate={0.00162}
        isConverted={true}
      />
    );
    // Should show base amount ($100), not native
    const amounts = screen.getAllByTestId('private-amount');
    expect(amounts[0]).toHaveTextContent('$100');
  });

  it('renders native amount in native mode', () => {
    mockCurrencyDisplay.mockReturnValue({
      displayMode: 'native',
      secondaryCurrency: null,
    });
    render(
      <ConvertedAmount
        amount={65000}
        currency="XOF"
        convertedAmount={100}
        baseCurrency="USD"
        isConverted={true}
      />
    );
    const amounts = screen.getAllByTestId('private-amount');
    expect(amounts[0]).toHaveTextContent('XOF');
  });

  it('renders both amounts in both mode when currencies differ', () => {
    mockCurrencyDisplay.mockReturnValue({
      displayMode: 'both',
      secondaryCurrency: null,
    });
    render(
      <ConvertedAmount
        amount={65000}
        currency="XOF"
        convertedAmount={100}
        baseCurrency="USD"
        exchangeRate={0.00162}
        isConverted={true}
      />
    );
    const amounts = screen.getAllByTestId('private-amount');
    // Should show both base and native
    expect(amounts.length).toBe(2);
  });

  it('renders single amount in both mode when same currency', () => {
    mockCurrencyDisplay.mockReturnValue({
      displayMode: 'both',
      secondaryCurrency: null,
    });
    render(
      <ConvertedAmount
        amount={100}
        currency="USD"
        convertedAmount={100}
        baseCurrency="USD"
        isConverted={false}
      />
    );
    const amounts = screen.getAllByTestId('private-amount');
    expect(amounts.length).toBe(1);
  });

  it('shows tooltip with native amount in base mode when converted', () => {
    render(
      <ConvertedAmount
        amount={65000}
        currency="XOF"
        convertedAmount={100}
        baseCurrency="USD"
        exchangeRate={0.00162}
        isConverted={true}
      />
    );
    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent('XOF');
  });

  it('shows tooltip with base amount in native mode when converted', () => {
    mockCurrencyDisplay.mockReturnValue({
      displayMode: 'native',
      secondaryCurrency: null,
    });
    render(
      <ConvertedAmount
        amount={65000}
        currency="XOF"
        convertedAmount={100}
        baseCurrency="USD"
        exchangeRate={0.00162}
        isConverted={true}
      />
    );
    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent('USD');
  });

  it('shows no tooltip when native equals base and no secondary', () => {
    render(<ConvertedAmount amount={100} currency="USD" />);
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });

  it('shows secondary currency in tooltip when configured', () => {
    mockCurrencyDisplay.mockReturnValue({
      displayMode: 'base',
      secondaryCurrency: 'EUR',
    });
    render(
      <ConvertedAmount
        amount={100}
        currency="USD"
        secondaryAmount={92}
        secondaryCurrency="EUR"
        secondaryExchangeRate={0.92}
      />
    );
    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent('EUR');
  });

  it('uses context secondary currency when prop not provided', () => {
    mockCurrencyDisplay.mockReturnValue({
      displayMode: 'base',
      secondaryCurrency: 'GBP',
    });
    render(
      <ConvertedAmount
        amount={100}
        currency="USD"
        secondaryAmount={78}
        secondaryExchangeRate={0.78}
      />
    );
    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent('GBP');
  });

  it('shows exchange rate in tooltip', () => {
    render(
      <ConvertedAmount
        amount={65000}
        currency="XOF"
        convertedAmount={100}
        baseCurrency="USD"
        exchangeRate={0.00162}
        isConverted={true}
      />
    );
    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent('1 XOF');
    expect(tooltip).toHaveTextContent('USD');
  });

  it('applies custom className', () => {
    const { container } = render(
      <ConvertedAmount amount={100} currency="USD" className="custom-class" />
    );
    expect(container.firstChild).toHaveClass('custom-class');
  });

  it('shows secondary in tooltip for both mode when converted', () => {
    mockCurrencyDisplay.mockReturnValue({
      displayMode: 'both',
      secondaryCurrency: 'EUR',
    });
    render(
      <ConvertedAmount
        amount={65000}
        currency="XOF"
        convertedAmount={100}
        baseCurrency="USD"
        exchangeRate={0.00162}
        isConverted={true}
        secondaryAmount={92}
        secondaryCurrency="EUR"
        secondaryExchangeRate={0.92}
      />
    );
    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent('EUR');
  });

  it('no tooltip in both mode when not converted and no secondary', () => {
    mockCurrencyDisplay.mockReturnValue({
      displayMode: 'both',
      secondaryCurrency: null,
    });
    render(
      <ConvertedAmount amount={100} currency="USD" isConverted={false} />
    );
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });

  it('shows secondary tooltip for native mode', () => {
    mockCurrencyDisplay.mockReturnValue({
      displayMode: 'native',
      secondaryCurrency: 'EUR',
    });
    render(
      <ConvertedAmount
        amount={100}
        currency="USD"
        secondaryAmount={92}
        secondaryCurrency="EUR"
        secondaryExchangeRate={0.92}
      />
    );
    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent('EUR');
  });
});
