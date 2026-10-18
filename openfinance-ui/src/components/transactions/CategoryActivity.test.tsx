import { beforeEach, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import {
  CategoryAmount,
  CategoryTransactionCount,
} from '@/components/transactions/CategoryActivity';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import type { CategoryTreeNode } from '@/types/transaction';

beforeEach(mockAuthentication);

it('shows direct and cumulative amounts and links each count to its matching scope', () => {
  const node: CategoryTreeNode = {
    id: 1,
    name: 'Parent',
    directAmount: 100,
    totalAmount: 1100,
    currency: 'USD',
    directTransactionCount: 1,
    transactionCount: 3,
    subcategories: [],
    hasSubcategories: true,
  };
  renderWithProviders(
    <>
      <CategoryAmount node={node} />
      <CategoryTransactionCount node={node} />
      <CategoryAmount node={node} scope="direct" />
      <CategoryTransactionCount node={node} scope="direct" />
    </>
  );
  expect(screen.getByTitle('Direct amount')).toHaveTextContent('100');
  expect(screen.getAllByTitle('Including subcategories')[0]).toHaveTextContent(/1[,. ]100/);
  expect(screen.getByRole('link', { name: 'View direct transactions for Parent' })).toHaveAttribute(
    'href',
    '/transactions?categoryId=1&excludeTransfers=true'
  );
  expect(
    screen.getByRole('link', { name: 'View transactions including subcategories for Parent' })
  ).toHaveAttribute(
    'href',
    '/transactions?categoryId=1&includeSubcategories=true&excludeTransfers=true'
  );
});
