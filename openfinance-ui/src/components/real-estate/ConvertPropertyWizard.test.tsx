import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { ConvertPropertyWizard } from '@/components/real-estate/ConvertPropertyWizard';

const state = vi.hoisted(() => ({
  conversionEnabled: true,
  data: [
    {
      assetId: 8,
      name: 'Apartment unit',
      accountName: 'Imported property account',
      closesSourceAccount: true,
      currency: 'EUR',
      currentValue: 1800,
      purchasePrice: 1500,
      purchaseDate: '2024-01-01',
      transactionCount: 5,
      valuationCount: 9,
      sourceRevision: 'a'.repeat(64),
    },
  ],
  isPending: false,
  isError: false,
  refetch: vi.fn(),
  mutateAsync: vi.fn(),
}));
vi.mock('@/hooks/usePropertyConversion', () => ({
  usePropertyConversionEnabled: () => state.conversionEnabled,
  usePropertyConversionCandidates: () => state,
  useConvertProperty: () => ({ mutateAsync: state.mutateAsync, isPending: false }),
}));
vi.mock('@/hooks/useLiabilities', () => ({
  useLiabilities: () => ({ data: [{ id: 3, name: 'Existing mortgage', type: 'MORTGAGE' }] }),
}));
vi.mock('@/components/ui/LiabilitySelector', () => ({
  LiabilitySelector: ({
    id,
    value,
    onValueChange,
  }: {
    id: string;
    value?: number;
    onValueChange: (value?: number) => void;
  }) => (
    <select
      id={id}
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value ? Number(event.target.value) : undefined)}
    >
      <option value="">None</option>
      <option value="3">Existing mortgage</option>
    </select>
  ),
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
  Element.prototype.scrollIntoView = vi.fn();
  state.isPending = false;
  state.conversionEnabled = true;
  state.data[0].closesSourceAccount = true;
  state.isError = false;
  state.mutateAsync.mockReset().mockResolvedValue({ propertyId: 42 });
  state.refetch.mockReset();
  vi.spyOn(window, 'confirm').mockReturnValue(false);
});

async function enterDetails() {
  const user = userEvent.setup();
  await user.click(await screen.findByRole('radio', { name: /Apartment unit/ }));
  await user.click(screen.getByRole('button', { name: 'Next' }));
  await user.type(screen.getByLabelText(/Address/), '10 Test Street');
  return user;
}

describe('Imported property conversion', () => {
  it('cannot open the wizard when Skrooge import is disabled', () => {
    state.conversionEnabled = false;
    renderWithProviders(
      <ConvertPropertyWizard initialAssetId={8} onClose={vi.fn()} onConverted={vi.fn()} />
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(state.mutateAsync).not.toHaveBeenCalled();
  });
  it('reviews recorded values and sends only property details with a stable source revision', async () => {
    const onConverted = vi.fn();
    renderWithProviders(<ConvertPropertyWizard onClose={vi.fn()} onConverted={onConverted} />);
    const user = await enterDetails();
    await user.click(screen.getByText('Mortgage & rental income (optional)'));
    await user.selectOptions(screen.getByLabelText('Linked Mortgage (Optional)'), '3');
    await user.type(screen.getByLabelText('Expected monthly rent (EUR)'), '123.45');
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByText('1800 EUR')).toBeVisible();
    expect(screen.getByText('1500 EUR')).toBeVisible();
    expect(screen.getByText('Existing mortgage', { selector: 'dd' })).toBeVisible();
    expect(screen.getByText(/Keep 5 transactions and 9 dated valuations/)).toBeVisible();
    expect(screen.getByText(/Imported property account.*will be closed/)).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Convert to property' }));
    await waitFor(() => expect(onConverted).toHaveBeenCalledWith(42));
    const request = state.mutateAsync.mock.calls[0][0];
    expect(request).toMatchObject({
      assetId: 8,
      sourceRevision: 'a'.repeat(64),
      name: 'Apartment unit',
      address: '10 Test Street',
      propertyType: 'RESIDENTIAL',
      mortgageId: 3,
      rentalIncome: '123.45',
    });
    expect(request).not.toHaveProperty('currentValue');
    expect(request).not.toHaveProperty('purchasePrice');
  });

  it('explains when the source account still has other financial activity', async () => {
    state.data[0].closesSourceAccount = false;
    renderWithProviders(<ConvertPropertyWizard onClose={vi.fn()} onConverted={vi.fn()} />);
    const user = await enterDetails();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByText(/Imported property account.*stays open/)).toBeVisible();
    expect(screen.queryByText(/will be closed/)).not.toBeInTheDocument();
  });

  it('requires missing property information and protects an edited draft on Escape', async () => {
    const onClose = vi.fn();
    renderWithProviders(
      <ConvertPropertyWizard initialAssetId={8} onClose={onClose} onConverted={vi.fn()} />
    );
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Next' }));
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Address is required')).toBeVisible();
    expect(state.mutateAsync).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText(/Address/), 'Draft address');
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(window.confirm).toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByLabelText(/Address/)).toHaveValue('Draft address');
  });

  it('retries an uncertain result with the identical request and retains the review', async () => {
    state.mutateAsync
      .mockRejectedValueOnce(new Error('Connection lost'))
      .mockResolvedValueOnce({ propertyId: 42 });
    renderWithProviders(<ConvertPropertyWizard onClose={vi.fn()} onConverted={vi.fn()} />);
    const user = await enterDetails();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await user.click(screen.getByRole('button', { name: 'Convert to property' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not confirm the conversion');
    expect(screen.getByRole('button', { name: 'Back' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Convert to property' }));
    expect(state.mutateAsync.mock.calls[1][0]).toEqual(state.mutateAsync.mock.calls[0][0]);
  });

  it('allows returning to refresh the source after a rejected stale preview', async () => {
    state.mutateAsync.mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 400, data: { message: 'The holding changed. Select it again.' } },
    });
    renderWithProviders(<ConvertPropertyWizard onClose={vi.fn()} onConverted={vi.fn()} />);
    const user = await enterDetails();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await user.click(screen.getByRole('button', { name: 'Convert to property' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The holding changed');
    expect(screen.getByRole('button', { name: 'Back' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Back' }));
    expect(screen.getByLabelText(/Address/)).toHaveValue('10 Test Street');
  });

  it('searches holdings and offers recovery when loading fails', async () => {
    const { unmount } = renderWithProviders(
      <ConvertPropertyWizard onClose={vi.fn()} onConverted={vi.fn()} />
    );
    fireEvent.change(await screen.findByLabelText('Search by asset or account'), {
      target: { value: 'absent' },
    });
    expect(screen.getByRole('status')).toHaveTextContent('No matching holdings');
    unmount();
    state.isError = true;
    renderWithProviders(<ConvertPropertyWizard onClose={vi.fn()} onConverted={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Try again' }));
    expect(state.refetch).toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  });
});
