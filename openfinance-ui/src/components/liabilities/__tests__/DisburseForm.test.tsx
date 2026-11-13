import { fireEvent, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DisburseForm } from '@/components/liabilities/DisburseForm';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { server } from '@/test/mocks/server';
import type { Liability } from '@/types/liability';

const state = vi.hoisted(() => ({ save: vi.fn() }));
vi.mock('@/hooks/useLiabilities', () => ({
  useDisburseLiability: () => ({ mutateAsync: state.save }),
}));
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => ({ data: [] }) }));
vi.mock('@/hooks/useAssets', () => ({
  useAssets: () => ({
    data: [
      { id: 10, name: 'Imported home', type: 'REAL_ESTATE', realEstateId: 7, currency: 'EUR' },
    ],
  }),
}));

const loan: Liability = {
  id: 5,
  name: 'Mortgage',
  type: 'MORTGAGE',
  principal: 150000,
  currentBalance: 90000,
  fundedAmount: 90000,
  currency: 'EUR',
  startDate: '2024-01-01',
  balanceStartDate: '2025-01-01',
  linkedPropertyId: 7,
  linkedPropertyName: 'Imported home',
  createdAt: '2025-01-01',
  updatedAt: '2025-01-01',
};

describe('DisburseForm', () => {
  beforeEach(() => {
    mockAuthentication();
    state.save.mockReset().mockResolvedValue({});
    server.use(
      http.get('*/liabilities/5/asset-links', () =>
        HttpResponse.json([{ assetId: 10, relationship: 'FINANCING' }])
      )
    );
  });

  it('shows the linked property once even when its backing asset is also returned', async () => {
    renderWithProviders(<DisburseForm liability={loan} onDone={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('Route'), { target: { value: 'property' } });
    await waitFor(() =>
      expect(screen.getByLabelText('Financed asset or property')).toHaveValue('10')
    );
    expect(screen.getAllByRole('option', { name: 'Imported home' })).toHaveLength(1);
    fireEvent.click(screen.getByRole('button', { name: 'Confirm disbursement' }));
    await waitFor(() =>
      expect(state.save).toHaveBeenCalledWith({
        liabilityId: 5,
        request: expect.objectContaining({
          amount: '60000',
          directRealEstateId: 7,
          capitalizePropertyCost: false,
          increasePropertyValue: false,
        }),
      })
    );
  });

  it.each([false, true])(
    'lets the user add property cost with value increase = %s',
    async increaseValue => {
      renderWithProviders(<DisburseForm liability={loan} onDone={vi.fn()} />);
      fireEvent.change(screen.getByLabelText('Route'), { target: { value: 'property' } });
      await screen.findByLabelText('Financed asset or property');
      fireEvent.click(screen.getByRole('checkbox', { name: 'Add to property cost' }));
      const valueChoice = screen.getByRole('checkbox', {
        name: 'Also increase the property value by this amount',
      });
      expect(valueChoice).not.toBeChecked();
      if (increaseValue) fireEvent.click(valueChoice);
      fireEvent.click(screen.getByRole('button', { name: 'Confirm disbursement' }));
      await waitFor(() =>
        expect(state.save).toHaveBeenCalledWith({
          liabilityId: 5,
          request: expect.objectContaining({
            directRealEstateId: 7,
            capitalizePropertyCost: true,
            increasePropertyValue: increaseValue,
          }),
        })
      );
    }
  );

  it('clears the value increase when capitalization is disabled or the destination changes', async () => {
    renderWithProviders(<DisburseForm liability={loan} onDone={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('Route'), { target: { value: 'property' } });
    await screen.findByLabelText('Financed asset or property');
    const cost = screen.getByRole('checkbox', { name: 'Add to property cost' });
    fireEvent.click(cost);
    fireEvent.click(
      screen.getByRole('checkbox', { name: 'Also increase the property value by this amount' })
    );
    fireEvent.click(cost);
    expect(
      screen.queryByRole('checkbox', { name: 'Also increase the property value by this amount' })
    ).not.toBeInTheDocument();
    fireEvent.click(cost);
    expect(
      screen.getByRole('checkbox', { name: 'Also increase the property value by this amount' })
    ).not.toBeChecked();
    fireEvent.change(screen.getByLabelText('Route'), { target: { value: 'account' } });
    expect(
      screen.queryByRole('checkbox', { name: 'Add to property cost' })
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Route'), { target: { value: 'property' } });
    expect(screen.getByRole('checkbox', { name: 'Add to property cost' })).not.toBeChecked();
  });

  it('blocks dates before the managed balance and amounts exceeding the available approval', async () => {
    renderWithProviders(<DisburseForm liability={loan} onDone={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('Route'), { target: { value: 'property' } });
    await screen.findByLabelText('Financed asset or property');
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '60001' } });
    expect(screen.getByRole('button', { name: 'Confirm disbursement' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '1000' } });
    fireEvent.change(screen.getByLabelText('Date'), { target: { value: '2024-12-31' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirm disbursement' }));
    expect(screen.getByLabelText('Date')).toHaveAttribute('aria-invalid', 'true');
    expect(state.save).not.toHaveBeenCalled();
  });
});
