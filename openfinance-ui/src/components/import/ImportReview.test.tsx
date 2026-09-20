import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { ImportReview } from '@/components/import/ImportReview';
import type { ImportTransactionDTO } from '@/types/import';

vi.mock('@/hooks/useCategories', () => ({
  useCategories: () => ({ data: [{ id: 7, name: 'Fast Food', type: 'EXPENSE' }] }),
}));
vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: () => <select aria-label="Category" />,
  CategoryCombobox: () => <input aria-label="Category" />,
}));
vi.mock('@/components/ui/PayeeSelector', () => ({
  PayeeCombobox: () => <input aria-label="Payee" />,
}));

describe('ImportReview source categories', () => {
  beforeEach(() => {
    mockAuthentication();
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
