import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import type { ConversionPaymentCandidate } from '@/hooks/useLiabilityConversion';
import { ConvertLiabilityWizard } from '@/components/liabilities/ConvertLiabilityWizard';

const state = vi.hoisted(() => ({
  conversionEnabled: true,
  data: [
    {
      accountId: 8,
      name: 'Imported mortgage',
      currency: 'EUR',
      outstandingBalance: 800,
      startDate: '2024-01-01',
      transactionCount: 5,
      sourceRevision: 'a'.repeat(64),
      exactValues: { outstandingBalance: '800.00' },
    },
  ],
  payments: [] as ConversionPaymentCandidate[],
  paymentError: false,
  paymentPending: false,
  properties: [] as Array<{ id: number; name: string; isActive: boolean }>,
  isPending: false,
  isError: false,
  refetch: vi.fn(),
  mutateAsync: vi.fn(),
}));
vi.mock('@/hooks/useLiabilityConversion', () => ({
  useLiabilityConversionEnabled: () => state.conversionEnabled,
  useLiabilityConversionCandidates: () => state,
  useLiabilityConversionPayments: () => ({
    data: { items: state.payments, truncated: false },
    isError: state.paymentError,
    isPending: state.paymentPending,
    isFetching: state.paymentPending,
    refetch: state.refetch,
  }),
  useConvertLiability: () => ({ mutateAsync: state.mutateAsync, isPending: false }),
}));
vi.mock('@/hooks/useRealEstate', () => ({
  useProperties: () => ({ data: state.properties, isSuccess: true }),
}));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency }: { amount: number; currency: string }) => (
    <span>
      {amount} {currency}
    </span>
  ),
}));

beforeEach(() => {
  mockAuthentication();
  vi.stubEnv('VITE_IMPORT_MORTGAGE_PRESET_ACCOUNT_NAME', '00000000001');
  vi.stubEnv('VITE_IMPORT_MORTGAGE_PRESET_PROPERTY_NAME', 'Example residence');
  Element.prototype.scrollIntoView = vi.fn();
  Element.prototype.scrollTo = vi.fn();
  state.properties = [];
  state.data[0].name = 'Imported mortgage';
  state.data[0].outstandingBalance = 800;
  state.data[0].exactValues.outstandingBalance = '800.00';
  state.payments = [];
  state.paymentError = false;
  state.paymentPending = false;
  state.conversionEnabled = true;
  state.isPending = false;
  state.isError = false;
  state.mutateAsync.mockReset().mockResolvedValue({ id: 42 });
  state.refetch.mockReset();
  vi.spyOn(window, 'confirm').mockReturnValue(false);
});

afterEach(() => vi.unstubAllEnvs());

async function enterDetails() {
  const user = userEvent.setup();
  await user.click(await screen.findByRole('radio', { name: /Imported mortgage/ }));
  await user.click(screen.getByRole('button', { name: 'Next' }));
  return user;
}

describe('Imported liability conversion', () => {
  it('leaves terms unfilled when no mortgage preset is configured', async () => {
    vi.stubEnv('VITE_IMPORT_MORTGAGE_PRESET_ACCOUNT_NAME', '');
    state.data[0].name = '00000000001';
    renderWithProviders(<ConvertLiabilityWizard onClose={vi.fn()} onConverted={vi.fn()} />);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('radio', { name: /00000000001/ }));
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByLabelText('Agreed principal (EUR)')).toHaveValue('800');
    expect(screen.getByLabelText('Liability type')).toHaveValue('LOAN');
    expect(
      screen.getByLabelText('Monthly payment, excluding insurance (optional) (EUR)')
    ).toHaveValue('');
  });
  it('is unavailable when Skrooge import is disabled', () => {
    state.conversionEnabled = false;
    renderWithProviders(<ConvertLiabilityWizard onClose={vi.fn()} onConverted={vi.fn()} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
  it('reviews the cutover and submits confirmed terms with the exact source balance', async () => {
    const onConverted = vi.fn();
    state.properties = [{ id: 3, name: 'Imported home', isActive: true }];
    renderWithProviders(<ConvertLiabilityWizard onClose={vi.fn()} onConverted={onConverted} />);
    const user = await enterDetails();
    await user.clear(screen.getByLabelText('Agreed principal (EUR)'));
    await user.type(screen.getByLabelText('Agreed principal (EUR)'), '2000');
    await user.clear(screen.getByLabelText('Total already borrowed (EUR)'));
    await user.type(screen.getByLabelText('Total already borrowed (EUR)'), '1200');
    await user.selectOptions(screen.getByLabelText('Liability type'), 'MORTGAGE');
    await user.selectOptions(screen.getByLabelText(/Link to.*Property/i), '3');
    await user.type(screen.getByLabelText('First principal repayment'), '2025-07-15');
    await user.click(screen.getByRole('button', { name: 'Review existing payments' }));
    await user.click(await screen.findByRole('button', { name: 'Review conversion' }));
    expect(screen.getByText(/5 imported transactions remain available/)).toBeVisible();
    expect(screen.getAllByText('800 EUR')).toHaveLength(2);
    await user.click(screen.getByRole('button', { name: 'Convert to liability' }));
    await waitFor(() => expect(onConverted).toHaveBeenCalledWith(42));
    expect(state.mutateAsync.mock.calls[0][0]).toMatchObject({
      accountId: 8,
      sourceRevision: 'a'.repeat(64),
      fundedPrincipal: '1200',
      liability: {
        name: 'Imported mortgage',
        type: 'MORTGAGE',
        realEstateId: 3,
        currentBalance: '800',
        principal: '2000',
        currency: 'EUR',
        startDate: '2024-01-01',
        amortizationStartDate: '2025-07-15',
      },
    });
  });
  it('prevents inconsistent terms and protects an edited draft', async () => {
    const onClose = vi.fn();
    renderWithProviders(<ConvertLiabilityWizard onClose={onClose} onConverted={vi.fn()} />);
    const user = await enterDetails();
    await user.clear(screen.getByLabelText('Total already borrowed (EUR)'));
    await user.type(screen.getByLabelText('Total already borrowed (EUR)'), '700');
    await user.click(screen.getByRole('button', { name: 'Review existing payments' }));
    expect(
      await screen.findByText('Total borrowed must cover the outstanding debt.')
    ).toBeVisible();
    expect(state.mutateAsync).not.toHaveBeenCalled();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(window.confirm).toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });
  it('retries an uncertain result with the identical request', async () => {
    state.mutateAsync.mockRejectedValueOnce(new Error('Network interrupted'));
    const onConverted = vi.fn();
    renderWithProviders(<ConvertLiabilityWizard onClose={vi.fn()} onConverted={onConverted} />);
    const user = await enterDetails();
    await user.click(screen.getByRole('button', { name: 'Review existing payments' }));
    await user.click(await screen.findByRole('button', { name: 'Review conversion' }));
    await user.click(screen.getByRole('button', { name: 'Convert to liability' }));
    expect(await screen.findByRole('alert')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Back' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    await waitFor(() => expect(onConverted).toHaveBeenCalledWith(42));
    expect(state.mutateAsync.mock.calls[0][0]).toEqual(state.mutateAsync.mock.calls[1][0]);
  });
  it('does not permit cached source selection when loading fails', async () => {
    state.isError = true;
    renderWithProviders(
      <ConvertLiabilityWizard initialAccountId={8} onClose={vi.fn()} onConverted={vi.fn()} />
    );
    expect(await screen.findByRole('alert')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(state.refetch).toHaveBeenCalledOnce();
  });

  it('prefills the confirmed mortgage, links the configured property and submits reviewed charge splits atomically', async () => {
    state.data[0].name = '00000000001';
    state.data[0].outstandingBalance = 192500;
    state.data[0].exactValues.outstandingBalance = '192500.00';
    state.properties = [{ id: 3, name: 'Example residence', isActive: true }];
    state.payments = [
      {
        transactionId: 90,
        sourceRevision: 'b'.repeat(64),
        date: '2025-06-05',
        description: 'ECHEANCE PRET',
        accountName: '00000000002',
        currency: 'EUR',
        amount: 17.34,
        suggestedComponent: 'INSURANCE',
        categoryName: 'Assurance du Prêt',
        splits: [],
      },
      {
        transactionId: 91,
        sourceRevision: 'c'.repeat(64),
        date: '2025-07-07',
        description: 'ECHEANCE PRET',
        accountName: '00000000002',
        currency: 'EUR',
        amount: 1215.77,
        splits: [
          {
            splitId: 1,
            amount: 104.94,
            categoryName: 'Intérêts du Prêt',
            suggestedComponent: 'INTEREST',
          },
          {
            splitId: 2,
            amount: 35.83,
            categoryName: 'Assurance du Prêt',
            suggestedComponent: 'INSURANCE',
          },
          { splitId: 3, amount: 1075, categoryName: 'Frais de dossier', suggestedComponent: 'FEE' },
        ],
      },
    ];
    renderWithProviders(<ConvertLiabilityWizard onClose={vi.fn()} onConverted={vi.fn()} />);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('radio', { name: /00000000001/ }));
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByLabelText('Agreed principal (EUR)')).toHaveValue('215,000');
    expect(
      screen.getByLabelText('Monthly payment, excluding insurance (optional) (EUR)')
    ).toHaveValue('1,136.66');
    expect(screen.getByLabelText('Insurance Rate (% Annual)')).toHaveValue('0.20');
    await waitFor(() => expect(screen.getByLabelText(/Link to.*Property/i)).toHaveValue('3'));
    expect(screen.getByLabelText('First principal repayment')).toHaveValue('2027-01-05');
    await user.click(screen.getByRole('button', { name: 'Review existing payments' }));
    expect(await screen.findByText(/2 payments selected/)).toBeVisible();
    expect(screen.getAllByRole('checkbox', { checked: true })).toHaveLength(2);
    await user.click(screen.getByRole('button', { name: 'Review conversion' }));
    expect(screen.getByText('22500 EUR')).toBeVisible();
    expect(screen.getByText('Monthly insurance').parentElement).toHaveTextContent('35.83 EUR');
    expect(screen.getByText('1172.49 EUR')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Convert to liability' }));
    await waitFor(() => expect(state.mutateAsync).toHaveBeenCalledOnce());
    expect(state.mutateAsync.mock.calls[0][0]).toMatchObject({
      fundedPrincipal: '192500',
      liability: {
        principal: '215000',
        currentBalance: '192500',
        interestRate: 3.65,
        insurancePercentage: 0.2,
        minimumPayment: '1136.66',
        realEstateId: 3,
        startDate: '2025-06-17',
        amortizationStartDate: '2027-01-05',
        endDate: '2050-06-05',
      },
      payments: [
        { transactionId: 90, sourceRevision: 'b'.repeat(64), component: 'INSURANCE', splits: [] },
        {
          transactionId: 91,
          sourceRevision: 'c'.repeat(64),
          splits: [
            { splitId: 1, component: 'INTEREST' },
            { splitId: 2, component: 'INSURANCE' },
            { splitId: 3, component: 'FEE' },
          ],
        },
      ],
    });
  });

  it('requires unknown components to be reviewed and preserves exclusions when going back', async () => {
    state.payments = [
      {
        transactionId: 1,
        sourceRevision: 'c'.repeat(64),
        date: '2025-01-01',
        description: 'Unclear charge',
        accountName: 'Cash',
        currency: 'EUR',
        amount: 20,
        categoryName: 'Custom cost',
        splits: [],
      },
    ];
    renderWithProviders(<ConvertLiabilityWizard onClose={vi.fn()} onConverted={vi.fn()} />);
    const user = await enterDetails();
    await user.click(screen.getByRole('button', { name: 'Review existing payments' }));
    await user.click(screen.getByRole('button', { name: 'Review conversion' }));
    expect(await screen.findByText(/Choose a component for every selected/)).toBeVisible();
    expect(state.mutateAsync).not.toHaveBeenCalled();
    await user.selectOptions(screen.getByLabelText(/Custom cost/), 'INTEREST');
    await user.click(screen.getByRole('button', { name: 'Review conversion' }));
    await user.click(screen.getByRole('button', { name: 'Back' }));
    expect(screen.getByLabelText(/Custom cost/)).toHaveValue('INTEREST');
    state.refetch.mockResolvedValue({ data: { items: state.payments }, isError: false });
    await user.click(screen.getByRole('button', { name: 'Reload payments' }));
    expect(screen.getByLabelText(/Custom cost/)).toHaveValue('INTEREST');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: 'Reload payments' }));
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    await user.click(screen.getByRole('button', { name: 'Back' }));
    await user.click(screen.getByRole('button', { name: 'Review existing payments' }));
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    await user.click(screen.getByRole('button', { name: 'Review conversion' }));
    await user.click(screen.getByRole('button', { name: 'Convert to liability' }));
    await waitFor(() =>
      expect(state.mutateAsync).toHaveBeenCalledWith(expect.objectContaining({ payments: [] }))
    );
  });

  it('blocks review while payment discovery fails', async () => {
    state.paymentError = true;
    renderWithProviders(<ConvertLiabilityWizard onClose={vi.fn()} onConverted={vi.fn()} />);
    const user = await enterDetails();
    await user.click(screen.getByRole('button', { name: 'Review existing payments' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Payments could not be loaded');
    expect(screen.getByRole('button', { name: 'Review conversion' })).toBeDisabled();
    expect(state.mutateAsync).not.toHaveBeenCalled();
  });

  it('requires the configured property to be selected before leaving the prefilled mortgage terms', async () => {
    state.data[0].name = '00000000001';
    renderWithProviders(<ConvertLiabilityWizard onClose={vi.fn()} onConverted={vi.fn()} />);
    const user = userEvent.setup();
    await user.click(await screen.findByRole('radio', { name: /00000000001/ }));
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await user.click(screen.getByRole('button', { name: 'Review existing payments' }));
    expect(await screen.findByText('Select the property financed by this mortgage.')).toBeVisible();
    expect(state.mutateAsync).not.toHaveBeenCalled();
  });
});
