import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { CategoryMapping } from '@/components/import/CategoryMapping';
import type { ImportTransactionDTO } from '@/types/import';
import i18n from '@/test/i18n-test';

const create = vi.hoisted(() => vi.fn());
vi.mock('@/hooks/useTransactions', () => ({
  useCategories: () => ({
    data: [
      { id: 1, name: 'Food', parentId: null },
      { id: 2, name: 'Travel', parentId: null },
    ],
  }),
  useCreateCategory: () => ({ mutateAsync: create, isPending: false }),
}));
const tr = (key: string) => i18n.t(key, { ns: 'import' });
const transaction = (category: string | null): ImportTransactionDTO => ({
  transactionDate: '2026-01-01',
  payee: 'Shop',
  amount: '12.34',
  currency: 'EUR',
  memo: null,
  category,
  clearedStatus: '',
  referenceNumber: null,
  accountName: null,
  lineNumber: 1,
  splits: [],
  validationErrors: [],
  potentialDuplicate: false,
  sourceFileName: 'bank.csv',
  rawData: null,
});
beforeEach(async () => {
  mockAuthentication();
  vi.clearAllMocks();
  await i18n.changeLanguage('en');
});

describe('import category mapping', () => {
  it('includes split categories, counts duplicates, and maps or skips the chosen source', () => {
    const onMappingChange = vi.fn();
    const split = {
      ...transaction('Groceries'),
      splitTransaction: true,
      splits: [{ category: 'Train', amount: '2', memo: '' }],
    };
    renderWithProviders(
      <CategoryMapping
        transactions={[transaction('Groceries'), split, transaction(null)]}
        categoryMappings={{}}
        onMappingChange={onMappingChange}
      />
    );
    expect(screen.getByText('2 transactions')).toBeInTheDocument();
    expect(screen.getByText('Train')).toBeInTheDocument();
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: '1' } });
    expect(onMappingChange).toHaveBeenCalledWith('Groceries', 1);
    fireEvent.change(screen.getAllByRole('combobox')[1], { target: { value: 'skip' } });
    expect(onMappingChange).toHaveBeenCalledWith('Train', null);
  });
  it('trims a new category name and maps it only after creation succeeds', async () => {
    create.mockResolvedValue({ id: 99, name: 'New category' });
    const onMappingChange = vi.fn();
    renderWithProviders(
      <CategoryMapping
        transactions={[transaction('Imported')]}
        categoryMappings={{}}
        onMappingChange={onMappingChange}
      />
    );
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'create' } });
    const dialog = screen.getByRole('dialog');
    const input = within(dialog).getByRole('textbox');
    expect(input).toHaveValue('Imported');
    fireEvent.change(input, { target: { value: '  New category  ' } });
    await userEvent.click(
      within(dialog).getByRole('button', { name: tr('categoryMapping.createCategory') })
    );
    await waitFor(() => expect(onMappingChange).toHaveBeenCalledWith('Imported', 99));
    expect(create).toHaveBeenCalledWith({ name: 'New category' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
  it('keeps the dialog and mapping unchanged when creation fails, then allows cancellation', async () => {
    create.mockRejectedValue(new Error('Server unavailable'));
    const log = vi.spyOn(console, 'error').mockImplementation(() => {});
    const onMappingChange = vi.fn();
    renderWithProviders(
      <CategoryMapping
        transactions={[transaction('Imported')]}
        categoryMappings={{ Imported: 999 }}
        onMappingChange={onMappingChange}
      />
    );
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'create' } });
    const dialog = screen.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('textbox'), { target: { value: ' ' } });
    expect(
      within(dialog).getByRole('button', { name: tr('categoryMapping.createCategory') })
    ).toBeDisabled();
    fireEvent.change(within(dialog).getByRole('textbox'), { target: { value: 'Retry' } });
    await userEvent.click(
      within(dialog).getByRole('button', { name: tr('categoryMapping.createCategory') })
    );
    await waitFor(() => expect(create).toHaveBeenCalled());
    expect(onMappingChange).not.toHaveBeenCalled();
    await userEvent.click(
      within(dialog).getByRole('button', { name: tr('categoryMapping.cancel') })
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    log.mockRestore();
  });
});
