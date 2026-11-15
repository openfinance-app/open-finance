import { act, fireEvent, screen } from '@testing-library/react';
import { useState, type ComponentProps } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { AccountFilters } from '@/components/accounts/AccountFilters';
import type { AccountFilters as Filters } from '@/types/account';
import i18n from '@/test/i18n-test';

vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({
    value,
    onValueChange,
  }: ComponentProps<typeof import('@/components/ui/CurrencySelector').CurrencySelector>) => (
    <select
      aria-label="Choose currency"
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value || undefined)}
    >
      <option value="">All</option>
      <option value="EUR">EUR</option>
    </select>
  ),
}));
beforeEach(() => {
  mockAuthentication();
  vi.useFakeTimers();
});
afterEach(() => {
  vi.useRealTimers();
});
function mount(initial: Filters = {}) {
  const changed = vi.fn();
  function Harness() {
    const [filters, setFilters] = useState(initial);
    return (
      <AccountFilters
        filters={filters}
        onFiltersChange={next => {
          changed(next);
          setFilters(next);
        }}
      />
    );
  }
  return { ...renderWithProviders(<Harness />), changed };
}
function search(): HTMLElement {
  return screen.getByRole('textbox', { name: i18n.t('accounts:filters.search') });
}

describe('Account filter boundaries', () => {
  it.each([
    ['active', true],
    ['closed', false],
    ['', undefined],
  ] as const)('preserves the selected %s status including false', (value, expected) => {
    const { changed } = mount({ isActive: value === 'active' ? false : true });
    fireEvent.change(screen.getByRole('combobox', { name: i18n.t('accounts:filters.status') }), {
      target: { value },
    });
    expect(changed).toHaveBeenLastCalledWith(expect.objectContaining({ isActive: expected }));
    expect(screen.getByRole('combobox', { name: i18n.t('accounts:filters.status') })).toHaveValue(
      value
    );
  });

  it.each(['SAVINGS', ''] as const)('selects or removes the account type: %s', value => {
    const { changed } = mount({ type: 'CHECKING' });
    fireEvent.change(
      screen.getByRole('combobox', { name: i18n.t('accounts:filters.accountType') }),
      { target: { value } }
    );
    expect(changed).toHaveBeenLastCalledWith(expect.objectContaining({ type: value || undefined }));
  });

  it('changes sort order while retaining the other filters', () => {
    const { changed } = mount({ currency: 'EUR', isActive: false });
    fireEvent.change(screen.getByRole('combobox', { name: i18n.t('accounts:filters.sortBy') }), {
      target: { value: 'balance,desc' },
    });
    expect(changed).toHaveBeenLastCalledWith({
      currency: 'EUR',
      isActive: false,
      sort: 'balance,desc',
    });
  });

  it('debounces repeated text edits and resets the page once', () => {
    const { changed } = mount({ page: 4, type: 'SAVINGS' });
    fireEvent.change(search(), { target: { value: 'Sa' } });
    act(() => {
      vi.advanceTimersByTime(250);
    });
    fireEvent.change(search(), { target: { value: 'Savings' } });
    act(() => {
      vi.advanceTimersByTime(399);
    });
    expect(changed).not.toHaveBeenCalled();
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(changed).toHaveBeenCalledExactlyOnceWith({
      page: 0,
      type: 'SAVINGS',
      keyword: 'Savings',
    });
  });

  it('does not reapply a pending search after the user clears all filters', () => {
    const { changed } = mount({ keyword: 'Old search', type: 'SAVINGS' });
    fireEvent.change(search(), { target: { value: 'Pending search' } });
    fireEvent.click(screen.getByRole('button', { name: /clear/i }));
    expect(search()).toHaveValue('');
    act(() => {
      vi.advanceTimersByTime(400);
    });
    expect(changed).toHaveBeenCalledExactlyOnceWith({ page: 0, sort: 'name,asc' });
  });

  it('cancels a pending search when the filter panel unmounts', () => {
    const { unmount, changed } = mount();
    fireEvent.change(search(), { target: { value: 'Pending search' } });
    unmount();
    act(() => {
      vi.advanceTimersByTime(400);
    });
    expect(changed).not.toHaveBeenCalled();
  });

  it('clears the keyword without removing other active filters', () => {
    const { changed } = mount({ keyword: 'Old', currency: 'EUR' });
    fireEvent.change(search(), { target: { value: '' } });
    act(() => {
      vi.advanceTimersByTime(400);
    });
    expect(changed).toHaveBeenLastCalledWith({ keyword: undefined, currency: 'EUR', page: 0 });
  });

  it('renders and clears a low-balance constraint', () => {
    const { changed } = mount({ lowBalance: true });
    expect(screen.getByText(i18n.t('accounts:filters.lowBalance'))).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: /clear/i }));
    expect(changed).toHaveBeenLastCalledWith({ page: 0, sort: 'name,asc' });
  });
  it('retains a status change made while keyword debounce is pending', () => {
    const { changed } = mount({ keyword: 'Previous' });
    fireEvent.change(search(), { target: { value: 'Savings' } });
    fireEvent.change(screen.getByRole('combobox', { name: i18n.t('accounts:filters.status') }), {
      target: { value: 'closed' },
    });
    act(() => {
      vi.advanceTimersByTime(400);
    });
    expect(changed).toHaveBeenLastCalledWith({ keyword: 'Savings', isActive: false, page: 0 });
  });
});
