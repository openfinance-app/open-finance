import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { InterestRateVariationsSection } from '@/components/accounts/InterestRateVariationsSection';

const state = vi.hoisted(() => ({
  rates: vi.fn(),
  estimate: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  createError: null as Error | null,
  updateError: null as Error | null,
  createPending: false,
  updatePending: false,
  deletePending: false,
}));
vi.mock('@/hooks/useAccounts', () => ({
  useInterestRateVariations: state.rates,
  useInterestEstimate: state.estimate,
  useCreateVariation: () => ({
    mutate: state.create,
    error: state.createError,
    isPending: state.createPending,
  }),
  useUpdateVariation: () => ({
    mutate: state.update,
    error: state.updateError,
    isPending: state.updatePending,
  }),
  useDeleteVariation: () => ({ mutate: state.remove, isPending: state.deletePending }),
}));
const rate = { id: 7, accountId: 12, rate: 3, validFrom: '2020-01-01', createdAt: '2020-01-01' };

beforeEach(() => {
  vi.resetAllMocks();
  mockAuthentication();
  Element.prototype.scrollIntoView = vi.fn();
  state.rates.mockReturnValue({ data: [rate], isLoading: false });
  state.estimate.mockReturnValue({ data: undefined, isLoading: false, isError: false });
  state.createError = null;
  state.updateError = null;
  state.createPending = false;
  state.updatePending = false;
  state.deletePending = false;
});

function renderSection(): void {
  renderWithProviders(<InterestRateVariationsSection accountId={12} />);
}
function add(): void {
  fireEvent.click(screen.getByRole('button', { name: 'Add Rate' }));
}
function save(): void {
  fireEvent.click(screen.getByRole('button', { name: 'Save Rate' }));
}

describe('Interest history boundaries', () => {
  it.each(['loading', 'error'])(
    'does not replace an unavailable %s estimate with misleading zero earnings',
    mode => {
      state.estimate.mockReturnValue({
        data: undefined,
        isLoading: mode === 'loading',
        isError: mode === 'error',
      });
      renderSection();
      expect(screen.queryByText('€0.00')).not.toBeInTheDocument();
      expect(screen.queryByRole('alert') !== null).toBe(mode === 'error');
      expect(screen.getAllByText('—')).toHaveLength(4);
    }
  );

  it('retains exact annual and lifetime earnings beyond floating-point precision', () => {
    state.estimate.mockReturnValue({
      data: {
        yearToDate: 9007199254740992,
        estimate: 9007199254740992,
        historicalAccumulated: 9007199254740992,
        exactValues: {
          yearToDate: '9007199254740993.01',
          estimate: '9007199254740993.02',
          historicalAccumulated: '9007199254740993.03',
        },
      },
      isLoading: false,
      isError: false,
    });
    renderSection();
    expect(screen.getByText('€9,007,199,254,740,993.01')).toBeVisible();
    expect(screen.getByText('€9,007,199,254,740,993.02')).toBeVisible();
    expect(screen.getByText('€9,007,199,254,740,993.03')).toBeVisible();
  });

  it('sorts rate changes chronologically without mutating the cached response', () => {
    const rates = [{ ...rate, id: 8, validFrom: '2099-01-01', rate: 9, taxRate: 25 }, rate];
    state.rates.mockReturnValue({ data: rates, isLoading: false });
    renderSection();
    const rows = screen.getAllByRole('row');
    expect(within(rows[1]).getByText('3.00%')).toBeVisible();
    expect(within(rows[2]).getByText('9.00%')).toBeVisible();
    expect(within(rows[2]).getByText('25.00%')).toBeVisible();
    expect(within(rows[2]).getByText('Scheduled')).toBeVisible();
    expect(rates.map(item => item.id)).toEqual([8, 7]);
  });

  it('waits for rate history before offering an editor', () => {
    state.rates.mockReturnValue({ data: undefined, isLoading: true });
    renderSection();
    expect(screen.queryByRole('button', { name: 'Add Rate' })).not.toBeInTheDocument();
  });

  it('can add the first rate when the server returns no history', () => {
    state.rates.mockReturnValue({ data: undefined, isLoading: false });
    renderSection();
    add();
    expect(screen.getByRole('textbox', { name: /Tax Rate/ })).toHaveValue('0');
    expect(screen.getByRole('combobox', { name: 'Calculation basis' })).toHaveValue('ACTUAL_365');
  });

  it.each([false, true])(
    'requires confirmation before deleting a saved rate: accepted=%s',
    accepted => {
      vi.spyOn(window, 'confirm').mockReturnValue(accepted);
      renderSection();
      fireEvent.click(screen.getByRole('button', { name: 'Delete rate' }));
      if (accepted) expect(state.remove).toHaveBeenCalledWith({ accountId: 12, variationId: 7 });
      else expect(state.remove).not.toHaveBeenCalled();
    }
  );

  it('disables deletion while its request is pending', () => {
    state.deletePending = true;
    renderSection();
    expect(screen.getByRole('button', { name: 'Delete rate' })).toBeDisabled();
  });

  it('fills legacy convention defaults and closes only after a successful update', async () => {
    state.update.mockImplementation((_payload: unknown, callbacks: { onSuccess: () => void }) =>
      callbacks.onSuccess()
    );
    renderSection();
    fireEvent.click(screen.getByRole('button', { name: 'Edit rate' }));
    expect(screen.getByRole('textbox', { name: /Tax Rate/ })).toHaveValue('0');
    expect(screen.getByRole('combobox', { name: 'Credit value date' })).toHaveValue(
      'TRANSACTION_DATE'
    );
    save();
    await waitFor(() =>
      expect(state.update).toHaveBeenCalledWith(
        {
          accountId: 12,
          variationId: 7,
          data: expect.objectContaining({
            rate: '3',
            taxRate: '0',
            debitValueDateMode: 'TRANSACTION_DATE',
          }),
        },
        expect.any(Object)
      )
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(state.create).not.toHaveBeenCalled();
  });

  it.each([false, true])('keeps the editor and values when saving fails: editing=%s', editing => {
    if (editing) state.updateError = new Error('Could not update this rate');
    else state.createError = new Error('Could not create this rate');
    renderSection();
    fireEvent.click(screen.getByRole('button', { name: editing ? 'Edit rate' : 'Add Rate' }));
    expect(screen.getByRole('alert')).toHaveTextContent(
      editing ? 'Could not update this rate' : 'Could not create this rate'
    );
    expect(screen.getByRole('textbox', { name: /Interest Rate/ })).toHaveValue(editing ? '3' : '0');
  });

  it.each([
    ['rate', '-1', /at least 0/i],
    ['tax', '-1', /at least 0/i],
    ['tax', '100.01', /no greater than 100/i],
    ['date', '', /effective date is required/i],
  ])('blocks an invalid %s value %s before saving', async (field, value, error) => {
    renderSection();
    add();
    const input =
      field === 'date'
        ? screen.getByLabelText(/Effective Date/)
        : screen.getByRole('textbox', { name: field === 'rate' ? /Interest Rate/ : /Tax Rate/ });
    fireEvent.change(input, { target: { value } });
    save();
    expect(await screen.findByText(error)).toBeVisible();
    expect(state.create).not.toHaveBeenCalled();
  });

  it('normalizes cleared rate fields to zero and allows a zero-rate period', async () => {
    renderSection();
    add();
    fireEvent.change(screen.getByRole('textbox', { name: /Interest Rate/ }), {
      target: { value: '' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: /Tax Rate/ }), { target: { value: '' } });
    save();
    await waitFor(() =>
      expect(state.create).toHaveBeenCalledWith(
        { accountId: 12, data: expect.objectContaining({ rate: '0', taxRate: '0' }) },
        expect.any(Object)
      )
    );
  });

  it('cancels an edit without changing the server record', () => {
    renderSection();
    fireEvent.click(screen.getByRole('button', { name: 'Edit rate' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(state.update).not.toHaveBeenCalled();
  });
});
