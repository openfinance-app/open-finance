import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { ImportReview } from '@/components/import/ImportReview';
import type { ImportTransactionDTO } from '@/types/import';

vi.mock('@/hooks/useCategories', () => ({
  useCategories: () => ({
    data: [
      { id: 7, name: 'Fast Food', type: 'EXPENSE' },
      {
        id: 308,
        name: 'Courses alimentaires',
        canonicalName: 'Groceries',
        type: 'EXPENSE',
      },
    ],
  }),
}));
vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: () => <select aria-label="Category" />,
  CategoryCombobox: ({ onValueChange }: { onValueChange: (name: string, id?: number) => void }) => (
    <button onClick={() => onValueChange('Courses alimentaires', 308)}>
      Choose existing groceries
    </button>
  ),
}));
vi.mock('@/components/ui/PayeeSelector', () => ({
  PayeeCombobox: () => <input aria-label="Payee" />,
}));

describe('ImportReview source categories', () => {
  beforeEach(() => {
    mockAuthentication();
  });

  it('maps a canonical source name to the existing localized category', async () => {
    const mappingsChanged = vi.fn();
    const newNamesChanged = vi.fn();
    renderWithProviders(
      <ImportReview
        transactions={[
          {
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
