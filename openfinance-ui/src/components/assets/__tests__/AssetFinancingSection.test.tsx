import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { AssetFinancingSection } from '@/components/assets/AssetFinancingSection';

vi.mock('@/hooks/useAssets', () => ({
  useAssets: () => ({
    data: [
      { id: 1, name: 'Car', type: 'VEHICLE', totalValue: 5000, currency: 'USD' },
      { id: 2, name: 'Other asset', type: 'JEWELRY', totalValue: 1000, currency: 'USD' },
    ],
  }),
}));
vi.mock('@/hooks/useLiabilities', () => ({
  useLiabilities: () => ({ data: [{ id: 9, name: 'Shared loan', currency: 'USD' }] }),
  useCreateLiability: () => ({ reset: vi.fn() }),
}));
const save = vi.fn();
let rows: { assetId: number; relationship: string; allocationPercentage: number }[];

beforeEach(() => {
  mockAuthentication();
  save.mockReset();
  rows = [{ assetId: 2, relationship: 'FINANCING', allocationPercentage: 40 }];
  server.use(
    http.get('*/assets/1/liabilities', () => HttpResponse.json([])),
    http.get('*/liabilities/9/asset-links', () => HttpResponse.json(rows)),
    http.put('*/liabilities/9/asset-links', async ({ request }) => {
      save(await request.json());
      return HttpResponse.json([]);
    })
  );
});

async function openLink(): Promise<void> {
  await userEvent.click(screen.getByRole('button', { name: 'Link a loan' }));
  await userEvent.selectOptions(screen.getByLabelText('Loan'), '9');
  await screen.findByRole('button', { name: 'Remove link to Car' });
}

describe('Loan relationships', () => {
  it('preselects the current item, allocates the remaining share and preserves other assets', async () => {
    renderWithProviders(<AssetFinancingSection assetId={1} />);
    await openLink();
    expect(screen.getByText('100% of the loan allocated (maximum 100%).')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Save links' }));
    await waitFor(() =>
      expect(save).toHaveBeenCalledWith([
        { assetId: 2, relationship: 'FINANCING', allocationPercentage: 40 },
        { assetId: 1, relationship: 'FINANCING', allocationPercentage: 60 },
      ])
    );
    expect(screen.getByRole('button', { name: 'Link a loan' })).toBeVisible();
  });

  it('prevents over-allocation and duplicate relationships', async () => {
    renderWithProviders(<AssetFinancingSection assetId={1} />);
    await openLink();
    fireEvent.change(screen.getAllByLabelText('Share of loan (%)')[1], { target: { value: '90' } });
    expect(screen.getByRole('button', { name: 'Save links' })).toBeDisabled();
    fireEvent.change(screen.getAllByLabelText('Share of loan (%)')[1], { target: { value: '60' } });
    await userEvent.selectOptions(screen.getAllByLabelText('Asset / property')[1], '2');
    expect(screen.getByText(/This item already has this relationship/)).toBeVisible();
    expect(screen.getByRole('button', { name: 'Save links' })).toBeDisabled();
    expect(save).not.toHaveBeenCalled();
  });

  it('allows removing this item while retaining the other allocation', async () => {
    renderWithProviders(<AssetFinancingSection assetId={1} />);
    await openLink();
    await userEvent.click(screen.getByRole('button', { name: 'Remove link to Car' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save links' }));
    await waitFor(() => expect(save).toHaveBeenCalledWith(rows));
  });
});
