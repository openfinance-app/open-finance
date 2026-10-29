import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import { CountrySelector } from '@/components/common/CountrySelector';
import { AccountSelector } from '@/components/ui/AccountSelector';
import { CategoryCombobox, CategorySelect } from '@/components/ui/CategorySelect';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/Dialog';
import { InstitutionSelector } from '@/components/ui/InstitutionSelector';
import { LiabilitySelector } from '@/components/ui/LiabilitySelector';
import { PayeeCombobox, PayeeSelector } from '@/components/ui/PayeeSelector';

const { createCategory, createPayee } = vi.hoisted(() => ({
  createCategory: vi.fn(),
  createPayee: vi.fn(),
}));

vi.mock('@/hooks/useTransactions', () => ({
  useCategoryTree: () => ({
    data: [
      { id: 1, name: 'Food', subcategories: [{ id: 2, name: 'Groceries', subcategories: [] }] },
      { id: 3, name: 'Travel', subcategories: [] },
    ],
    isLoading: false,
    isError: false,
  }),
  useCreateCategory: () => ({ mutateAsync: createCategory, isPending: false }),
}));

vi.mock('@/hooks/usePayees', () => ({
  useActivePayees: () => ({
    data: [
      { id: 1, name: 'Amazon', category: 'shopping' },
      { id: 2, name: 'Apple', category: 'shopping' },
      { id: 3, name: 'Netflix', category: 'entertainment' },
    ],
    isLoading: false,
    isError: false,
  }),
  useFindOrCreatePayee: () => ({ mutateAsync: createPayee }),
}));

vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: () => ({
    data: [
      { id: 1, name: 'Alpha', type: 'CHECKING', currency: 'EUR' },
      { id: 2, name: 'Beta', type: 'SAVINGS', currency: 'EUR' },
    ],
    isLoading: false,
    isError: false,
  }),
}));

vi.mock('@/hooks/useInstitutions', () => ({
  useInstitutions: () => ({
    data: [
      { id: 1, name: 'Alpha', country: 'FR' },
      { id: 2, name: 'Beta', country: 'US' },
    ],
    isLoading: false,
    isError: false,
  }),
}));

vi.mock('@/hooks/useLiabilities', () => ({
  useLiabilities: () => ({
    data: [
      { id: 1, name: 'Alpha', type: 'MORTGAGE', currency: 'EUR' },
      { id: 2, name: 'Beta', type: 'OTHER', currency: 'EUR' },
    ],
    isLoading: false,
    isError: false,
  }),
  getLiabilityTypeName: (type: string): string => type,
}));

vi.mock('@/components/ui/ConvertedAmount', () => ({ ConvertedAmount: () => null }));

beforeEach(() => {
  vi.clearAllMocks();
  mockAuthentication();
  Element.prototype.scrollIntoView = vi.fn();
  Element.prototype.hasPointerCapture = vi.fn(() => false);
});

describe('Popover selector keyboard navigation', () => {
  it('dismisses the selector with Escape without closing its parent dialog', async () => {
    const user = userEvent.setup();
    const onDialogOpenChange = vi.fn();
    const onValueChange = vi.fn();
    renderWithProviders(
      <Dialog defaultOpen onOpenChange={onDialogOpenChange}>
        <DialogContent>
          <DialogTitle>Edit country</DialogTitle>
          <CountrySelector value="" onValueChange={onValueChange} placeholder="Country" />
        </DialogContent>
      </Dialog>
    );
    const trigger = screen.getByRole('button', { name: 'Country' });
    await user.click(trigger);
    await waitFor(() => expect(screen.getByRole('combobox')).toHaveFocus());
    await user.keyboard('{ArrowDown}{Escape}');

    await waitFor(() => expect(trigger).toHaveFocus());
    expect(screen.getByRole('dialog', { name: 'Edit country' })).toBeInTheDocument();
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(onDialogOpenChange).not.toHaveBeenCalled();
    expect(onValueChange).not.toHaveBeenCalled();
  });

  it('filters countries, highlights a match, and selects without moving search focus', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    renderWithProviders(<CountrySelector value="" onValueChange={onValueChange} />);
    const trigger = screen.getByRole('button');
    await user.click(trigger);
    const search = screen.getByRole('combobox');

    await user.type(search, 'France');
    await user.keyboard('{ArrowDown}');

    const option = screen.getByRole('option', { name: 'France' });
    expect(search).toHaveFocus();
    expect(search).toHaveAttribute('aria-activedescendant', option.id);
    expect(option).toHaveAttribute('data-highlighted');
    expect(onValueChange).not.toHaveBeenCalled();
    expect(option.scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' });

    await user.keyboard('{Enter}');
    expect(onValueChange).toHaveBeenCalledExactlyOnceWith('FR');
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it('navigates category children in displayed order and supports clearing the selection', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    renderWithProviders(<CategorySelect value={1} onValueChange={onValueChange} />);
    const trigger = screen.getByRole('combobox');
    trigger.focus();
    await user.keyboard('{ArrowDown}{ArrowDown}{Enter}');
    expect(onValueChange).toHaveBeenCalledExactlyOnceWith(2);

    await waitFor(() => expect(trigger).toHaveFocus());
    await user.keyboard('{ArrowUp}{ArrowUp}{Enter}');
    expect(onValueChange).toHaveBeenLastCalledWith(undefined);
  });

  it('creates a category from the filtered list using Enter', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    createCategory.mockResolvedValueOnce({ id: 99, name: 'New category' });
    renderWithProviders(
      <CategorySelect onValueChange={onValueChange} allowNone={false} allowCreateInline />
    );
    await user.click(screen.getByRole('combobox'));
    await user.type(screen.getByRole('combobox', { name: 'Search categories...' }), 'New category');
    await user.keyboard('{ArrowDown}{Enter}');

    expect(createCategory).toHaveBeenCalledExactlyOnceWith({ name: 'New category' });
    await waitFor(() => expect(onValueChange).toHaveBeenCalledExactlyOnceWith(99));
  });

  it('navigates grouped payees in displayed order', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    renderWithProviders(<PayeeSelector onValueChange={onValueChange} allowNone={false} />);
    await user.click(screen.getByRole('combobox'));
    const search = screen.getByRole('combobox', { name: /search/i });
    await user.click(search);
    const options = screen.getAllByRole('option');
    await user.keyboard('{ArrowDown}{ArrowDown}');

    expect(search).toHaveAttribute('aria-activedescendant', options[1].id);
    expect(search).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(onValueChange).toHaveBeenCalledExactlyOnceWith(options[1].textContent);
  });

  it('ignores repeated Enter while creating a new payee', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    let finishCreation: (payee: { name: string }) => void = () => {};
    createPayee.mockReturnValueOnce(
      new Promise<{ name: string }>(resolve => {
        finishCreation = resolve;
      })
    );
    renderWithProviders(<PayeeSelector onValueChange={onValueChange} allowNone={false} />);
    await user.click(screen.getByRole('combobox'));
    await user.type(screen.getByRole('combobox', { name: /search/i }), 'New payee');
    await user.keyboard('{ArrowDown}{Enter}{Enter}');
    expect(createPayee).toHaveBeenCalledExactlyOnceWith('New payee');

    finishCreation({ name: 'New payee' });
    await waitFor(() => expect(onValueChange).toHaveBeenCalledExactlyOnceWith('New payee'));
  });
});

describe.each([
  { name: 'accounts', Component: AccountSelector, expected: 2 },
  { name: 'institutions', Component: InstitutionSelector, expected: '2' },
  { name: 'liabilities', Component: LiabilitySelector, expected: 2 },
])('Radix selector keyboard navigation: $name', ({ Component, expected }) => {
  it('navigates filtered results from the search input and selects with Enter', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    renderWithProviders(<Component onValueChange={onValueChange} allowNone={false} />);
    const trigger = screen.getByRole('combobox');
    trigger.focus();
    await user.keyboard('{ArrowDown}');

    const search = await screen.findByRole('textbox');
    await user.type(search, 'Beta');
    expect(search).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(screen.getByRole('option', { name: /Beta/ })).toHaveFocus());
    await user.keyboard('{Enter}');

    expect(onValueChange).toHaveBeenCalledExactlyOnceWith(expected);
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it('supports ArrowUp from search and Escape without changing the value', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    renderWithProviders(<Component onValueChange={onValueChange} allowNone={false} />);
    await user.click(screen.getByRole('combobox'));
    const search = await screen.findByRole('textbox');
    await user.click(search);
    await user.keyboard('{ArrowUp}');
    const options = screen.getAllByRole('option');
    await waitFor(() => expect(options.at(-1)).toHaveFocus());

    await user.click(search);
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeInTheDocument());
    expect(onValueChange).not.toHaveBeenCalled();
  });
});

describe('Import combobox keyboard navigation', () => {
  it('selects a category suggestion with its id while preserving search focus', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    renderWithProviders(<CategoryCombobox value="" onValueChange={onValueChange} />);
    const search = screen.getByRole('combobox');
    await user.type(search, 'Gro');
    await user.keyboard('{ArrowDown}{Enter}');

    expect(search).toHaveFocus();
    expect(search).toHaveValue('Groceries');
    expect(onValueChange).toHaveBeenCalledExactlyOnceWith('Groceries', 2);
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('reopens payee suggestions with arrows and still commits free text on blur', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    renderWithProviders(<PayeeCombobox value="" onValueChange={onValueChange} />);
    const search = screen.getByRole('combobox');
    await user.type(search, 'A');
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    await user.keyboard('{ArrowUp}{Enter}');
    expect(onValueChange).toHaveBeenCalledExactlyOnceWith('Apple');

    await user.clear(search);
    await user.type(search, 'Custom payee');
    expect(search).toHaveAttribute('aria-expanded', 'false');
    fireEvent.blur(search);
    expect(onValueChange).toHaveBeenLastCalledWith('Custom payee');
  });
});
