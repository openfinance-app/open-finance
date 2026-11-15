import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ComponentProps } from 'react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { AssetFinancingSection } from '@/components/assets/AssetFinancingSection';
import type { Asset } from '@/types/asset';
import type { Liability, LiabilityRequest } from '@/types/liability';

const state = vi.hoisted(() => ({
  assets: vi.fn(),
  loans: vi.fn(),
  create: vi.fn(),
  reset: vi.fn(),
  error: null as unknown,
}));
vi.mock('@/hooks/useAssets', () => ({ useAssets: () => state.assets() }));
vi.mock('@/hooks/useLiabilities', () => ({
  useLiabilities: () => state.loans(),
  useCreateLiability: () => ({ mutateAsync: state.create, reset: state.reset, error: state.error }),
}));
vi.mock('@/components/ui/CurrencyAmount', () => ({
  CurrencyAmount: ({ amount, currency }: { amount: string | number; currency: string }) => (
    <span>
      {currency}:{String(amount)}
    </span>
  ),
}));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency }: { amount: string | number; currency: string }) => (
    <span>
      {currency}:{String(amount)}
    </span>
  ),
}));
vi.mock('@/components/liabilities/RecordLoanPayment', () => ({
  RecordLoanPayment: ({ liability }: { liability: Liability }) => (
    <button>Pay {liability.name}</button>
  ),
}));
const request: LiabilityRequest = {
  name: 'New loan',
  type: 'LOAN',
  principal: '100',
  currentBalance: '100',
  currency: 'EUR',
  startDate: '2024-01-01',
};
vi.mock('@/components/liabilities/LiabilityForm', () => ({
  LiabilityForm: ({
    onSubmit,
    onCancel,
    initialType,
    initialCurrency,
    showPropertyLink,
  }: ComponentProps<typeof import('@/components/liabilities/LiabilityForm').LiabilityForm>) => (
    <div>
      <p>
        Loan defaults: {initialType} / {initialCurrency} / property link {String(showPropertyLink)}
      </p>
      <button onClick={() => onSubmit(request)}>Submit new loan</button>
      <button onClick={onCancel}>Cancel new loan</button>
    </div>
  ),
}));

type Link = {
  assetId: number;
  relationship: 'FINANCING' | 'COLLATERAL';
  allocationPercentage: number;
  liabilityId?: number;
  allocatedBalance?: number;
  assetName?: string;
  liabilityName?: string;
  primaryMortgage?: boolean;
  assetCurrency?: string;
  exactValues?: Record<string, string>;
};
const asset = (id: number, overrides: Partial<Asset> = {}): Asset => ({
  id,
  userId: 1,
  name: `Item ${id}`,
  type: 'VEHICLE',
  isPhysical: true,
  quantity: 1,
  purchasePrice: 5000,
  currentPrice: 5000,
  totalValue: 5000,
  totalCost: 5000,
  unrealizedGain: 0,
  gainPercentage: 0,
  holdingDays: 1,
  currency: 'EUR',
  purchaseDate: '2024-01-01',
  createdAt: '2024-01-01',
  lastUpdated: '2024-01-01',
  updatedAt: '2024-01-01',
  ...overrides,
});
const loan: Liability = {
  id: 9,
  name: 'Shared loan',
  type: 'LOAN',
  principal: 1000,
  currentBalance: 1000,
  currency: 'EUR',
  startDate: '2024-01-01',
  createdAt: '2024-01-01',
  updatedAt: '2024-01-01',
};
let links: Link[];
let loanLinks: Link[];
const saved = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  mockAuthentication();
  state.error = null;
  state.assets.mockReturnValue({ data: [asset(1), asset(2)] });
  state.loans.mockReturnValue({ data: [loan] });
  state.create.mockResolvedValue({ ...loan, id: 10 });
  links = [];
  loanLinks = [];
  server.use(
    http.get('*/assets/:id/liabilities', () => HttpResponse.json(links)),
    http.get('*/liabilities/:id/asset-links', () => HttpResponse.json(loanLinks)),
    http.put('*/liabilities/:id/asset-links', async ({ params, request }) => {
      saved(params.id, await request.json());
      return HttpResponse.json([]);
    })
  );
});

async function edit(): Promise<void> {
  await userEvent.click(screen.getByRole('button', { name: 'Link a loan' }));
  await userEvent.selectOptions(screen.getByLabelText('Loan'), '9');
  await screen.findByRole('button', { name: 'Remove link to Item 1' });
}

describe('financing review and validation', () => {
  it('includes legacy linked items but excludes sold, planned, inactive and financial assets from new choices', async () => {
    state.assets.mockReturnValue({
      data: [
        asset(1),
        asset(2, { type: 'REAL_ESTATE', isPhysical: false }),
        asset(3, { soldDate: '2025-01-01' }),
        asset(4, { acquisitionType: 'PLANNED' }),
        asset(5, { isActive: false }),
        asset(6, { type: 'STOCK', isPhysical: false }),
        asset(7, { isActive: false }),
      ],
    });
    loanLinks = [{ assetId: 7, relationship: 'COLLATERAL', allocationPercentage: 0 }];
    renderWithProviders(<AssetFinancingSection assetId={1} />);
    await edit();
    expect(
      within(screen.getAllByLabelText('Asset / property')[0])
        .getAllByRole('option')
        .map(o => o.textContent)
    ).toEqual(['Item 1', 'Item 2', 'Item 7']);
  });

  it.each(['0', '-1', '101', ''])(
    'rejects a financing share of %s without sending a request',
    async share => {
      renderWithProviders(<AssetFinancingSection assetId={1} />);
      await edit();
      fireEvent.change(screen.getByLabelText('Share of loan (%)'), { target: { value: share } });
      expect(screen.getByRole('button', { name: 'Save links' })).toBeDisabled();
      expect(saved).not.toHaveBeenCalled();
    }
  );

  it('allocates only financing shares, permits collateral, and preserves saved relationships when cancelling', async () => {
    loanLinks = [{ assetId: 2, relationship: 'FINANCING', allocationPercentage: 40 }];
    renderWithProviders(<AssetFinancingSection assetId={1} />);
    await edit();
    await userEvent.selectOptions(screen.getAllByLabelText('Relationship')[1], 'COLLATERAL');
    expect(screen.getByText('40% of the loan allocated (maximum 100%).')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getAllByLabelText('Relationship')[1], 'FINANCING');
    expect(screen.getAllByLabelText('Share of loan (%)')[1]).toHaveValue(60);
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(saved).not.toHaveBeenCalled();
    expect(loanLinks).toEqual([
      { assetId: 2, relationship: 'FINANCING', allocationPercentage: 40 },
    ]);
    await edit();
    expect(screen.getAllByLabelText('Share of loan (%)')[1]).toHaveValue(60);
  });

  it('adds a second asset only after its allocation is made positive and can remove every link', async () => {
    renderWithProviders(<AssetFinancingSection assetId={1} />);
    await edit();
    await userEvent.click(screen.getByRole('button', { name: 'Add another asset' }));
    expect(screen.getByRole('button', { name: 'Add another asset' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Save links' })).toBeDisabled();
    fireEvent.change(screen.getAllByLabelText('Share of loan (%)')[0], { target: { value: '70' } });
    fireEvent.change(screen.getAllByLabelText('Share of loan (%)')[1], { target: { value: '30' } });
    expect(screen.getByRole('button', { name: 'Save links' })).toBeEnabled();
    await userEvent.click(screen.getByRole('button', { name: 'Remove link to Item 1' }));
    await userEvent.click(screen.getByRole('button', { name: 'Remove link to Item 2' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save links' }));
    await waitFor(() => expect(saved).toHaveBeenCalledWith('9', []));
  });

  it.each([true, false])(
    'keeps the allocation draft after a failed save (server message=%s)',
    async message => {
      server.use(
        http.put('*/liabilities/9/asset-links', () =>
          HttpResponse.json(message ? { message: 'Allocation changed; reload first' } : {}, {
            status: 409,
          })
        )
      );
      renderWithProviders(<AssetFinancingSection assetId={1} />);
      await edit();
      fireEvent.change(screen.getByLabelText('Share of loan (%)'), { target: { value: '75' } });
      await userEvent.click(screen.getByRole('button', { name: 'Save links' }));
      expect(await screen.findByRole('alert')).toHaveTextContent(
        message ? 'Allocation changed; reload first' : 'Could not save or load relationships'
      );
      expect(screen.getByLabelText('Share of loan (%)')).toHaveValue(75);
      expect(screen.getByRole('button', { name: 'Save links' })).toBeEnabled();
    }
  );

  it('blocks saving when allocations cannot be loaded', async () => {
    server.use(
      http.get('*/liabilities/9/asset-links', () => HttpResponse.json({}, { status: 500 }))
    );
    renderWithProviders(<AssetFinancingSection assetId={1} />);
    await userEvent.click(screen.getByRole('button', { name: 'Link a loan' }));
    await userEvent.selectOptions(screen.getByLabelText('Loan'), '9');
    await screen.findByRole('alert');
    expect(screen.getByRole('button', { name: 'Save links' })).toBeDisabled();
    expect(screen.queryByLabelText('Share of loan (%)')).not.toBeInTheDocument();
  });

  it('shows the loan-side links, distinguishes primary mortgage from collateral, and edits that loan', async () => {
    loanLinks = [
      {
        assetId: 1,
        liabilityId: 9,
        assetName: 'Home',
        relationship: 'FINANCING',
        allocationPercentage: 75,
        allocatedBalance: 750,
        primaryMortgage: true,
      },
      {
        assetId: 2,
        liabilityId: 9,
        assetName: 'Guarantee',
        relationship: 'COLLATERAL',
        allocationPercentage: 0,
      },
    ];
    renderWithProviders(<AssetFinancingSection liabilityId={9} />);
    expect(await screen.findByRole('link', { name: 'Home' })).toHaveAttribute(
      'href',
      '/assets?highlight=1'
    );
    expect(screen.getByText('Primary mortgage · 75%')).toBeInTheDocument();
    expect(screen.getByText('Security only')).toBeInTheDocument();
    expect(screen.getByText('EUR:750')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'Manage links' })[1]);
    expect(screen.queryByLabelText('Loan')).not.toBeInTheDocument();
    expect(await screen.findByLabelText('Share of loan (%)')).toHaveValue(75);
  });

  it.each([
    ['VEHICLE', 'AUTO_LOAN'],
    ['REAL_ESTATE', 'MORTGAGE'],
    ['COLLECTIBLE', 'LOAN'],
  ] as const)(
    'creates a %s loan with the asset association and appropriate defaults',
    async (type, expected) => {
      state.assets.mockReturnValue({ data: [asset(1, { type })] });
      renderWithProviders(<AssetFinancingSection assetId={1} />);
      await userEvent.click(screen.getByRole('button', { name: 'Link a loan' }));
      await userEvent.click(screen.getByRole('button', { name: 'Create a new loan' }));
      expect(
        screen.getByText(`Loan defaults: ${expected} / EUR / property link false`)
      ).toBeInTheDocument();
      await userEvent.click(screen.getByRole('button', { name: 'Submit new loan' }));
      await waitFor(() =>
        expect(state.create).toHaveBeenCalledWith({ ...request, financedAssetId: 1 })
      );
      await screen.findByRole('button', { name: 'Link a loan' });
    }
  );

  it.each([new Error('Unavailable'), { isAxiosError: true, response: { status: 503 } }])(
    'retains a failed loan form and supports cancellation: %j',
    async error => {
      state.error = error;
      state.create.mockRejectedValue(state.error);
      renderWithProviders(<AssetFinancingSection assetId={1} />);
      await userEvent.click(screen.getByRole('button', { name: 'Link a loan' }));
      await userEvent.click(screen.getByRole('button', { name: 'Create a new loan' }));
      await userEvent.click(screen.getByRole('button', { name: 'Submit new loan' }));
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Could not save or load relationships'
      );
      expect(screen.getByRole('button', { name: 'Submit new loan' })).toBeEnabled();
      await userEvent.click(screen.getByRole('button', { name: 'Cancel new loan' }));
      expect(screen.getByRole('button', { name: 'Link a loan' })).toBeInTheDocument();
    }
  );
});
