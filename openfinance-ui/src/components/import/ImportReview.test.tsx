import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { ImportReview } from '@/components/import/ImportReview';
import type { ImportTransactionDTO } from '@/types/import';
import i18n from '@/test/i18n-test';
import type { ComponentProps } from 'react';

const transactionDefaults: ImportTransactionDTO = {
  transactionDate: null,
  payee: '',
  amount: null,
  currency: 'EUR',
  memo: null,
  category: null,
  clearedStatus: '',
  referenceNumber: null,
  accountName: null,
  lineNumber: null,
  splits: [],
  validationErrors: [],
  potentialDuplicate: false,
  sourceFileName: 'statement.qif',
  rawData: null,
};

vi.mock('@/hooks/useCategories', () => ({
  useCategories: () => ({
    data: [
      { id: 7, name: 'Fast Food' },
      { id: 8, name: 'Shopping' },
      { id: 401, name: 'Home' },
      { id: 402, name: 'Insurance', parentId: 401 },
      { id: 403, name: 'Vehicles' },
      { id: 404, name: 'Insurance', parentId: 403 },
      {
        id: 308,
        name: 'Courses alimentaires',
        canonicalName: 'Groceries',
      },
    ],
  }),
}));
vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: ({
    value,
    onValueChange,
  }: ComponentProps<typeof import('@/components/ui/CategorySelect').CategorySelect>) => (
    <select
      aria-label="Category"
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value ? Number(event.target.value) : undefined)}
    >
      <option value="">Create new</option>
      <option value="7">Fast Food</option>
      <option value="8">Shopping</option>
    </select>
  ),
  CategoryCombobox: ({ onValueChange }: { onValueChange: (name: string, id?: number) => void }) => (
    <button onClick={() => onValueChange('Courses alimentaires', 308)}>
      Choose existing groceries
    </button>
  ),
}));
vi.mock('@/components/ui/PayeeSelector', () => ({
  PayeeCombobox: ({
    value,
    onValueChange,
  }: {
    value: string;
    onValueChange: (value: string) => void;
  }) => (
    <input aria-label="Payee" value={value} onChange={event => onValueChange(event.target.value)} />
  ),
}));

describe('ImportReview source categories', () => {
  beforeEach(() => {
    mockAuthentication();
  });

  it('can map a new source category to an existing category or keep it as a new category', () => {
    function ReviewSession() {
      const [mappings, setMappings] = useState<Record<string, number>>({});
      const [names, setNames] = useState<string[]>(['Imported shopping']);
      const rows = [
        {
          ...transactionDefaults,
          transactionDate: '2026-01-01',
          amount: '-25.50',
          currency: 'EUR',
          payee: 'Vendor',
          category: 'Imported shopping',
          validationErrors: [],
          splits: [],
        },
      ] as ImportTransactionDTO[];
      return (
        <>
          <ImportReview
            transactions={rows}
            onTransactionsChange={vi.fn()}
            categoryMappings={mappings}
            onCategoryMappingsChange={setMappings}
            newCategoryNames={names}
            onNewCategoryNamesChange={setNames}
          />
          <output aria-label="Mappings">{JSON.stringify(mappings)}</output>
          <output aria-label="Categories to create">{names.join(',')}</output>
        </>
      );
    }
    renderWithProviders(<ReviewSession />);
    fireEvent.click(
      screen.getByText(i18n.t('import:review.categoryAssignments.title', { count: 1 }))
    );
    fireEvent.click(screen.getByTitle(i18n.t('import:review.actions.mapExisting')));
    fireEvent.click(screen.getByTitle(i18n.t('import:review.actions.keepNew')));
    expect(screen.getByLabelText('Categories to create')).toHaveTextContent('Imported shopping');
    fireEvent.click(screen.getByTitle(i18n.t('import:review.actions.mapExisting')));
    fireEvent.change(screen.getByRole('combobox', { name: 'Category' }), { target: { value: '' } });
    expect(screen.getByLabelText('Mappings')).toHaveTextContent('{}');
    fireEvent.click(screen.getByTitle(i18n.t('import:review.actions.mapExisting')));
    fireEvent.change(screen.getByRole('combobox', { name: 'Category' }), {
      target: { value: '8' },
    });
    expect(JSON.parse(screen.getByLabelText('Mappings').textContent!)).toEqual({
      'Imported shopping': 8,
    });
    expect(screen.getByLabelText('Categories to create')).toBeEmptyDOMElement();
    fireEvent.change(screen.getByRole('combobox', { name: 'Category' }), { target: { value: '' } });
    expect(screen.getByLabelText('Mappings')).toHaveTextContent('{}');
    expect(screen.getByLabelText('Categories to create')).toHaveTextContent('Imported shopping');
  });

  it('bulk edits only selected filtered rows and preserves exact imported amounts', () => {
    const original = [
      {
        ...transactionDefaults,
        transactionDate: '2026-10-01',
        amount: '-0.123456789012345678',
        currency: 'BTC',
        payee: 'Keep me',
        memo: '',
        category: null,
        validationErrors: [],
        splits: [],
      },
      {
        ...transactionDefaults,
        transactionDate: '2026-10-02',
        amount: '-2.000000000000000001',
        currency: 'BTC',
        payee: 'Duplicate',
        memo: '',
        category: null,
        validationErrors: [],
        splits: [],
        potentialDuplicate: true,
      },
    ] as ImportTransactionDTO[];
    const changed = vi.fn();
    renderWithProviders(
      <ImportReview
        transactions={original}
        onTransactionsChange={changed}
        categoryMappings={{}}
        onCategoryMappingsChange={vi.fn()}
        newCategoryNames={[]}
        onNewCategoryNamesChange={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: /Duplicates/i }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select all' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Payee' }), {
      target: { value: '  Reviewed merchant  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Choose existing groceries' }));
    fireEvent.click(screen.getByRole('button', { name: /Apply to 1/i }));
    expect(changed).toHaveBeenCalledWith([
      original[0],
      expect.objectContaining({
        payee: 'Reviewed merchant',
        category: 'Courses alimentaires',
        amount: '-2.000000000000000001',
      }),
    ]);
    expect(original[1].payee).toBe('Duplicate');
    expect(screen.getByRole('checkbox', { name: 'Select all' })).not.toBeChecked();
  });

  it('removes selected rows without deleting hidden rows and can clear a selection', () => {
    const rows = [
      {
        ...transactionDefaults,
        transactionDate: '2026-10-01',
        amount: '-1',
        currency: 'EUR',
        payee: 'Keep',
        memo: '',
        category: null,
        validationErrors: [],
        splits: [],
      },
      {
        ...transactionDefaults,
        transactionDate: '2026-10-02',
        amount: '-2',
        currency: 'EUR',
        payee: 'Remove',
        memo: '',
        category: null,
        validationErrors: [],
        splits: [],
        potentialDuplicate: true,
      },
    ] as ImportTransactionDTO[];
    const changed = vi.fn();
    renderWithProviders(
      <ImportReview
        transactions={rows}
        onTransactionsChange={changed}
        categoryMappings={{}}
        onCategoryMappingsChange={vi.fn()}
        newCategoryNames={[]}
        onNewCategoryNamesChange={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select transaction 1' }));
    fireEvent.click(screen.getByTitle('Clear selection'));
    expect(screen.getByRole('checkbox', { name: 'Select transaction 1' })).not.toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: /Duplicates/i }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select all' }));
    fireEvent.click(screen.getByTitle(i18n.t('review.actions.removeSelected', { ns: 'import' })));
    expect(changed).toHaveBeenCalledWith([rows[0]]);
  });

  it('maps full paths without creating duplicates or guessing between identical leaf names', async () => {
    const mappings = vi.fn();
    const create = vi.fn();
    renderWithProviders(
      <ImportReview
        transactions={['Home:Insurance', 'Insurance', 'Vehicles:Insurance'].map(
          (category, i) =>
            ({
              ...transactionDefaults,
              transactionDate: '2026-10-07',
              amount: i === 0 ? '-20' : '10',
              currency: 'EUR',
              payee: 'Insurance provider',
              category,
              memo: '',
              validationErrors: [],
              splits: [],
            }) as ImportTransactionDTO
        )}
        onTransactionsChange={vi.fn()}
        categoryMappings={{}}
        onCategoryMappingsChange={mappings}
        newCategoryNames={[]}
        onNewCategoryNamesChange={create}
      />
    );

    await waitFor(() =>
      expect(mappings).toHaveBeenCalledWith({
        'Home:Insurance': 402,
        'Vehicles:Insurance': 404,
      })
    );
    expect(create).not.toHaveBeenCalled();
  });

  it('identifies a legacy rule with an incompatible condition in the review error', () => {
    renderWithProviders(
      <ImportReview
        transactions={[
          {
            ...transactionDefaults,
            transactionDate: '2026-10-03',
            amount: '-20',
            currency: 'EUR',
            payee: 'Coffee',
            category: null,
            memo: '',
            validationErrors: ['RULE_CONDITION_INVALID: Old coffee rule'],
            splits: [],
          } as ImportTransactionDTO,
        ]}
        onTransactionsChange={vi.fn()}
        categoryMappings={{}}
        onCategoryMappingsChange={vi.fn()}
        newCategoryNames={[]}
        onNewCategoryNamesChange={vi.fn()}
      />
    );
    expect(screen.getByText(/Rule "Old coffee rule" uses an operator/)).toBeInTheDocument();
    expect(screen.queryByText(/RULE_CONDITION_INVALID:/)).not.toBeInTheDocument();
  });

  it('uses the resolved account currency for rows and splits without a file currency', () => {
    renderWithProviders(
      <ImportReview
        transactions={[
          {
            ...transactionDefaults,
            transactionDate: '2026-10-03',
            amount: '-12.34',
            currency: '',
            reviewCurrency: 'USD',
            payee: 'Dollar coffee',
            category: null,
            memo: '',
            validationErrors: [],
            splits: [
              { amount: '6.17', category: 'Food', memo: '' },
              { amount: '6.17', category: 'Shopping', memo: '' },
            ],
          } as ImportTransactionDTO,
        ]}
        onTransactionsChange={vi.fn()}
        categoryMappings={{}}
        onCategoryMappingsChange={vi.fn()}
        newCategoryNames={[]}
        onNewCategoryNamesChange={vi.fn()}
      />
    );
    expect(screen.getByText(/-\$12\.34/)).toBeInTheDocument();
    fireEvent.click(screen.getByTitle('Expand splits'));
    expect(screen.getAllByText(/\$6\.17/)).toHaveLength(2);
  });

  it('leaves an AI abstention unassigned instead of guessing from a memo substring', () => {
    const changed = vi.fn();
    renderWithProviders(
      <ImportReview
        transactions={[
          {
            ...transactionDefaults,
            transactionDate: '2026-10-03',
            amount: '-37.82',
            currency: 'EUR',
            payee: 'Green Basket Supermarket',
            memo: 'Weekly grocery shopping',
            category: null,
            validationErrors: [],
            splits: [],
          } as ImportTransactionDTO,
        ]}
        onTransactionsChange={changed}
        categoryMappings={{}}
        onCategoryMappingsChange={vi.fn()}
        newCategoryNames={[]}
        onNewCategoryNamesChange={vi.fn()}
      />
    );
    expect(screen.getByText('+ Assign')).toBeInTheDocument();
    expect(changed).not.toHaveBeenCalled();
  });

  it('keeps validation and collapsible split details inside table rows', async () => {
    const { container } = renderWithProviders(
      <ImportReview
        transactions={[
          {
            ...transactionDefaults,
            transactionDate: '2026-09-29',
            amount: '-30',
            currency: 'EUR',
            payee: 'Split shop',
            category: null,
            memo: '',
            validationErrors: ['SPLIT_INVALID: Bad total'],
            splits: [
              { amount: '20', category: 'Groceries', memo: '' },
              { amount: '20', category: 'Utilities', memo: '' },
            ],
          } as ImportTransactionDTO,
        ]}
        onTransactionsChange={vi.fn()}
        categoryMappings={{}}
        onCategoryMappingsChange={vi.fn()}
        newCategoryNames={[]}
        onNewCategoryNamesChange={vi.fn()}
      />
    );
    const body = container.querySelector('tbody')!;
    expect([...body.children].every(node => node.tagName === 'TR')).toBe(true);
    expect(screen.getByText('Split Lines')).toBeInTheDocument();
    fireEvent.click(screen.getByTitle('Hide errors'));
    expect(screen.queryByText('Split Lines')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTitle('Show errors'));
    expect(await screen.findByText('Split Lines')).toBeInTheDocument();
    expect([...body.children].every(node => node.tagName === 'TR')).toBe(true);
    fireEvent.click(screen.getByTitle('Hide errors'));
    expect(screen.queryByText('Split Lines')).not.toBeInTheDocument();
  });

  it('preserves exact decimal strings when saving a category edit', () => {
    const onTransactionsChange = vi.fn();
    const transaction = {
      ...transactionDefaults,
      transactionDate: '2026-09-15',
      amount: '-0.123456789012345679',
      currency: 'ETH',
      payee: 'Crypto purchase',
      category: null,
      memo: '',
      validationErrors: [],
      splits: [
        { amount: '-0.123456789012345678', category: 'First', memo: '' },
        { amount: '-0.000000000000000001', category: 'Second', memo: '' },
      ],
    } as ImportTransactionDTO;
    renderWithProviders(
      <ImportReview
        transactions={[transaction]}
        onTransactionsChange={onTransactionsChange}
        categoryMappings={{}}
        onCategoryMappingsChange={vi.fn()}
        newCategoryNames={[]}
        onNewCategoryNamesChange={vi.fn()}
      />
    );

    fireEvent.click(screen.getByTitle('Edit transaction'));
    fireEvent.click(screen.getByRole('button', { name: 'Choose existing groceries' }));
    fireEvent.click(screen.getByTitle('Save'));

    const updated = onTransactionsChange.mock.lastCall?.[0][0] as ImportTransactionDTO;
    expect(updated.amount).toBe('-0.123456789012345679');
    expect(updated.splits.map(split => split.amount)).toEqual([
      '-0.123456789012345678',
      '-0.000000000000000001',
    ]);
  });

  it('maps a canonical source name to the existing localized category', async () => {
    const mappingsChanged = vi.fn();
    const newNamesChanged = vi.fn();
    renderWithProviders(
      <ImportReview
        transactions={[
          {
            ...transactionDefaults,
            transactionDate: '2026-08-20',
            amount: -54.8,
            currency: 'EUR',
            payee: 'Leclerc',
            category: 'Groceries',
            validationErrors: [],
            splits: [],
          } as unknown as ImportTransactionDTO,
        ]}
        onTransactionsChange={vi.fn()}
        categoryMappings={{}}
        onCategoryMappingsChange={mappingsChanged}
        newCategoryNames={[]}
        onNewCategoryNamesChange={newNamesChanged}
      />
    );
    await waitFor(() => expect(mappingsChanged).toHaveBeenCalledWith({ Groceries: 308 }));
    expect(newNamesChanged).not.toHaveBeenCalled();
  });

  it('preserves the ID selected in a localized combobox and removes stale new category names', async () => {
    const mappingsChanged = vi.fn();
    function ReviewSession() {
      const [transactions, setTransactions] = useState([
        {
          ...transactionDefaults,
          transactionDate: '2026-08-20',
          amount: -54.8,
          currency: 'EUR',
          payee: 'Leclerc',
          category: 'Old imported category',
          validationErrors: [],
          splits: [],
        } as unknown as ImportTransactionDTO,
      ]);
      const [mappings, setMappings] = useState<Record<string, number>>({});
      const [newNames, setNewNames] = useState<string[]>([]);
      return (
        <>
          <ImportReview
            transactions={transactions}
            onTransactionsChange={setTransactions}
            categoryMappings={mappings}
            onCategoryMappingsChange={next => {
              setMappings(next);
              mappingsChanged(next);
            }}
            newCategoryNames={newNames}
            onNewCategoryNamesChange={setNewNames}
          />
          <output aria-label="New categories">{newNames.join(',')}</output>
        </>
      );
    }
    renderWithProviders(<ReviewSession />);
    expect(screen.getByLabelText('New categories')).toHaveTextContent('Old imported category');
    fireEvent.click(screen.getByTitle('Edit transaction'));
    fireEvent.click(screen.getByRole('button', { name: 'Choose existing groceries' }));
    fireEvent.click(screen.getByTitle('Save'));
    await waitFor(() =>
      expect(mappingsChanged).toHaveBeenLastCalledWith(
        expect.objectContaining({ 'Courses alimentaires': 308 })
      )
    );
    expect(screen.getByLabelText('New categories')).toBeEmptyDOMElement();
  });

  it('shows the effective reviewed merchant after a rule or manual edit', () => {
    renderWithProviders(
      <ImportReview
        transactions={[
          {
            ...transactionDefaults,
            transactionDate: '2026-08-20',
            amount: -54.8,
            currency: 'EUR',
            originalPayee: 'Original shop',
            payee: 'Reviewed shop',
            category: null,
            validationErrors: [],
            splits: [],
          } as unknown as ImportTransactionDTO,
        ]}
        onTransactionsChange={vi.fn()}
        categoryMappings={{}}
        onCategoryMappingsChange={vi.fn()}
        newCategoryNames={[]}
        onNewCategoryNamesChange={vi.fn()}
      />
    );
    expect(screen.getByText('Reviewed shop')).toBeVisible();
    expect(screen.queryByText('Original shop')).not.toBeInTheDocument();
  });

  it.each(['Food', 'Food:Dining'])(
    'preserves the explicit QIF category %s instead of guessing Fast Food',
    category => {
      const onTransactionsChange = vi.fn();
      const transaction = {
        ...transactionDefaults,
        transactionDate: '2026-09-10',
        amount: -25.5,
        currency: 'EUR',
        payee: 'Picard',
        category,
        memo: '',
        valid: true,
        duplicate: false,
        validationErrors: [],
      } as ImportTransactionDTO;
      renderWithProviders(
        <ImportReview
          transactions={[transaction]}
          onTransactionsChange={onTransactionsChange}
          categoryMappings={{}}
          onCategoryMappingsChange={vi.fn()}
          newCategoryNames={[]}
          onNewCategoryNamesChange={vi.fn()}
        />
      );
      expect(screen.getAllByText(category).length).toBeGreaterThan(0);
      expect(onTransactionsChange).not.toHaveBeenCalled();
    }
  );
});
