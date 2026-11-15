import { useCallback, useState } from 'react';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LiabilityConversionPayments } from '@/components/liabilities/LiabilityConversionPayments';
import type { SelectedConversionPayment } from '@/components/liabilities/liability-conversion-payments';
import type {
  ConversionPaymentCandidate,
  LiabilityConversionCandidate,
} from '@/hooks/useLiabilityConversion';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';

const state = vi.hoisted(() => ({
  data: undefined as { items: ConversionPaymentCandidate[]; truncated: boolean } | undefined,
  pending: false,
  fetching: false,
  error: false,
  refetch: vi.fn(),
  query: vi.fn(),
  changed: vi.fn(),
  ready: vi.fn(),
}));
vi.mock('@/hooks/useLiabilityConversion', () => ({
  useLiabilityConversionPayments: (...args: unknown[]) => {
    state.query(...args);
    return {
      data: state.data,
      isPending: state.pending,
      isFetching: state.fetching,
      isError: state.error,
      refetch: state.refetch,
    };
  },
}));
const source: LiabilityConversionCandidate = {
  accountId: 1,
  name: 'Car loan',
  currency: 'EUR',
  outstandingBalance: 500,
  startDate: '2025-01-01',
  transactionCount: 2,
  sourceRevision: 'account-v1',
};
const row = (
  transactionId: number,
  changes: Partial<ConversionPaymentCandidate> = {}
): ConversionPaymentCandidate => ({
  transactionId,
  sourceRevision: `row-${transactionId}-v1`,
  date: '2025-01-01',
  description: `Payment ${transactionId}`,
  accountName: 'Checking',
  currency: 'EUR',
  amount: 100,
  splits: [],
  suggestedComponent: 'PRINCIPAL',
  ...changes,
});
const text = (key: string) => i18n.t(`conversion.payments.${key}`, { ns: 'liabilities' });
function Harness({
  active = true,
  initial = [],
}: {
  active?: boolean;
  initial?: SelectedConversionPayment[];
}) {
  const [selected, setSelected] = useState(initial);
  const change = useCallback((payments: SelectedConversionPayment[]) => {
    state.changed(payments);
    setSelected(payments);
  }, []);
  return (
    <LiabilityConversionPayments
      source={source}
      selected={selected}
      onChange={change}
      active={active}
      onReadyChange={state.ready}
    />
  );
}
function lastSelection(): SelectedConversionPayment[] {
  return state.changed.mock.lastCall?.[0] ?? [];
}

describe('Converting historical loan payments', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    state.data = { items: [row(1)], truncated: false };
    state.pending = false;
    state.fetching = false;
    state.error = false;
  });

  it('only initializes selection when activated and preserves later user choices', () => {
    const view = renderWithProviders(<Harness active={false} />);
    expect(state.changed).not.toHaveBeenCalled();
    view.rerender(<Harness active />);
    expect(lastSelection()).toHaveLength(1);
    fireEvent.click(screen.getByRole('button', { name: text('clear') }));
    view.rerender(<Harness active={false} />);
    view.rerender(<Harness active />);
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(lastSelection()).toEqual([]);
  });

  it.each(['pending', 'fetching', 'error', 'ready'])('reports readiness during %s', mode => {
    state.pending = mode === 'pending';
    state.fetching = mode === 'fetching';
    state.error = mode === 'error';
    if (mode === 'pending' || mode === 'error') state.data = undefined;
    renderWithProviders(<Harness />);
    expect(state.ready).toHaveBeenLastCalledWith(mode === 'ready');
    if (mode === 'error') expect(screen.getByRole('alert')).toHaveTextContent(text('error'));
    if (mode === 'fetching')
      expect(screen.getByRole('button', { name: text('reload') })).toBeDisabled();
  });

  it('shows an empty result and a truncated warning without inventing payments', () => {
    state.data = { items: [], truncated: true };
    renderWithProviders(<Harness />);
    expect(screen.getByText(text('empty'))).toBeInTheDocument();
    expect(screen.getByText(text('truncated'))).toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('only searches on Enter or Find, preserving selection outside the new results', () => {
    const view = renderWithProviders(<Harness />);
    const input = screen.getByLabelText(text('search'));
    fireEvent.change(input, { target: { value: 'insurance' } });
    fireEvent.keyDown(input, { key: 'a' });
    expect(state.query).toHaveBeenLastCalledWith(1, 'Car loan', true);
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(state.query).toHaveBeenLastCalledWith(1, 'insurance', true);
    state.data = { items: [row(2)], truncated: false };
    view.rerender(<Harness />);
    expect(screen.getAllByRole('checkbox')).toHaveLength(2);
    expect(screen.getByRole('checkbox', { name: /Payment 1/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /Payment 2/ })).not.toBeChecked();
    fireEvent.change(input, { target: { value: 'fees' } });
    fireEvent.click(screen.getByRole('button', { name: text('find') }));
    expect(state.query).toHaveBeenLastCalledWith(1, 'fees', true);
    fireEvent.click(screen.getByRole('button', { name: text('selectAll') }));
    expect(lastSelection().map(payment => payment.candidate.transactionId)).toEqual([1, 2]);
  });

  it('toggles payments individually without discarding their neighbors', () => {
    state.data = { items: [row(1), row(2)], truncated: false };
    renderWithProviders(<Harness />);
    fireEvent.click(screen.getByRole('checkbox', { name: /Payment 1/ }));
    expect(lastSelection().map(payment => payment.candidate.transactionId)).toEqual([2]);
    fireEvent.click(screen.getByRole('checkbox', { name: /Payment 1/ }));
    expect(lastSelection().map(payment => payment.candidate.transactionId)).toEqual([2, 1]);
  });

  it('select all preserves manually classified selections instead of restoring suggestions', () => {
    state.data = { items: [row(1, { categoryName: 'Loan' }), row(2)], truncated: false };
    renderWithProviders(<Harness />);
    fireEvent.change(screen.getByRole('combobox', { name: /Loan/ }), { target: { value: 'FEE' } });
    fireEvent.click(screen.getByRole('checkbox', { name: /Payment 2/ }));
    fireEvent.click(screen.getByRole('button', { name: text('selectAll') }));
    expect(lastSelection().map(payment => payment.component)).toEqual(['FEE', 'PRINCIPAL']);
  });

  it.each(['unchanged', 'changed', 'removed', 'outside-search', 'failed'])(
    'reload handles %s source rows while preserving valid choices',
    async mode => {
      const original = row(1, { categoryName: 'Loan' });
      state.data = { items: [original], truncated: false };
      const view = renderWithProviders(<Harness />);
      fireEvent.change(screen.getByRole('combobox', { name: /Loan/ }), {
        target: { value: 'INTEREST' },
      });
      if (mode === 'outside-search') {
        state.data = { items: [row(2)], truncated: false };
        view.rerender(<Harness />);
      }
      state.refetch.mockResolvedValue({
        isError: mode === 'failed',
        data:
          mode === 'failed'
            ? undefined
            : {
                items:
                  mode === 'removed' || mode === 'outside-search'
                    ? []
                    : [
                        {
                          ...original,
                          sourceRevision: mode === 'changed' ? 'row-1-v2' : original.sourceRevision,
                        },
                      ],
                truncated: false,
              },
      });
      fireEvent.click(screen.getByRole('button', { name: text('reload') }));
      await waitFor(() => expect(state.refetch).toHaveBeenCalledTimes(1));
      await waitFor(() => {
        if (mode === 'removed') expect(lastSelection()).toEqual([]);
        else
          expect(lastSelection()[0].component).toBe(mode === 'changed' ? 'PRINCIPAL' : 'INTEREST');
      });
    }
  );

  it('shows selected rows even if searching for other payments fails', () => {
    const view = renderWithProviders(<Harness />);
    state.error = true;
    state.data = undefined;
    view.rerender(<Harness />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /Payment 1/ })).toBeChecked();
  });

  it('requires each split to be classified and retains the other split when editing one', () => {
    state.data = {
      items: [
        row(1, {
          description: '',
          notes: 'Bank supplied split',
          splits: [
            {
              splitId: 11,
              amount: 90,
              categoryName: 'Principal',
              categoryNameKey: 'missing.translation',
            },
            { splitId: 12, amount: 10 },
          ],
        }),
      ],
      truncated: false,
    };
    renderWithProviders(<Harness />);
    expect(screen.getByText(text('unnamed'))).toBeInTheDocument();
    expect(screen.getByText('Bank supplied split')).toBeInTheDocument();
    expect(document.querySelector('details')).toHaveAttribute('open');
    fireEvent.change(screen.getByRole('combobox', { name: /Principal/ }), {
      target: { value: 'PRINCIPAL' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: new RegExp(text('uncategorized')) }), {
      target: { value: 'OTHER' },
    });
    expect(lastSelection()[0].splits).toEqual([
      { splitId: 11, component: 'PRINCIPAL' },
      { splitId: 12, component: 'OTHER' },
    ]);
    expect(screen.queryByText(text('classify'))).not.toBeInTheDocument();
    const other = screen
      .getAllByText(i18n.t('splitForm.loanComponents.OTHER', { ns: 'transactions' }))
      .find(element => element.tagName === 'DT')!;
    expect(other.parentElement).toHaveTextContent('€10.00');
  });

  it.each([false, true])('keeps exact amounts in classification labels for split=%s', split => {
    const candidate = row(1, {
      amount: 9007199254740992,
      exactValues: { amount: '9007199254740993.25' },
      categoryName: 'Exact amount',
      splits: split
        ? [
            {
              splitId: 11,
              amount: 9007199254740992,
              exactValues: { amount: '9007199254740993.25' },
              categoryName: 'Exact amount',
            },
          ]
        : [],
    });
    state.data = { items: [candidate], truncated: false };
    renderWithProviders(<Harness />);
    expect(
      screen.getByRole('combobox', { name: /Exact amount.*9,007,199,254,740,993\.25/ })
    ).toBeInTheDocument();
  });
});
