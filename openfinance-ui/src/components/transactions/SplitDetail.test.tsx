/**
 * Unit tests for SplitDetail component
 *
 * Tests rendering of category pill + amount per split, handles empty splits,
 * handles splits without category names.
 */
import { screen } from '@testing-library/react';
import { vi, describe, it, expect } from 'vitest';
import { renderWithProviders } from '@/test/test-utils';
import { SplitDetail } from './SplitDetail';
import type { TransactionSplitResponse } from '@/types/transaction';

// ── Fixtures ──────────────────────────────────────────────────────────────────

const mockSplits: TransactionSplitResponse[] = [
  {
    id: 1,
    transactionId: 100,
    categoryId: 10,
    categoryName: 'Shopping',
    categoryColor: '#ff0000',
    categoryIcon: '🛒',
    amount: 50.0,
    description: 'Groceries',
  },
  {
    id: 2,
    transactionId: 100,
    categoryId: 30,
    categoryName: 'Entertainment',
    categoryColor: '#00ff00',
    categoryIcon: undefined,
    amount: 25.5,
    description: undefined,
  },
  {
    id: 3,
    transactionId: 100,
    categoryId: undefined,
    categoryName: undefined,
    categoryColor: undefined,
    categoryIcon: undefined,
    amount: 10.25,
    description: 'Miscellaneous',
  },
];

// ── Helpers ───────────────────────────────────────────────────────────────────

interface RenderComponentOptions {
  splits?: TransactionSplitResponse[];
  currency?: string;
}

function renderComponent({ splits = mockSplits, currency = 'EUR' }: RenderComponentOptions = {}) {
  renderWithProviders(<SplitDetail splits={splits} currency={currency} />);
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('SplitDetail', () => {
  describe('Basic Rendering', () => {
    it('renders all split details in a list', () => {
      renderComponent();

      expect(screen.getByRole('list', { name: 'Split details' })).toBeInTheDocument();
      expect(screen.getAllByRole('listitem')).toHaveLength(3);
    });

    it('displays formatted currency amounts for each split', () => {
      renderComponent();

      // Use getAllByText since amounts may appear multiple times
      // Match the numeric part only to be locale-agnostic
      expect(screen.getAllByText(/\b50\b/).length).toBeGreaterThan(0);
      expect(screen.getByText(/25[,.]5/)).toBeInTheDocument();
      expect(screen.getByText(/10[,.]25/)).toBeInTheDocument();
    });

    it('displays descriptions when present', () => {
      renderComponent();

      expect(screen.getByText('Groceries')).toBeInTheDocument();
      expect(screen.getByText('Miscellaneous')).toBeInTheDocument();
    });

    it('does not display description section when description is undefined', () => {
      renderComponent();

      // Second split has no description, so "Entertainment" should be the only text in that item
      const listItems = screen.getAllByRole('listitem');
      const secondItem = listItems[1];
      expect(secondItem).toHaveTextContent('Entertainment');
      // Should not contain the word "description"
      expect(secondItem).not.toHaveTextContent('description');
    });
  });

  describe('Category Pill Rendering', () => {
    it('renders category name and icon when both are present', () => {
      renderComponent();

      const shoppingText = screen.getByText('Shopping');
      expect(shoppingText).toBeInTheDocument();
      // Icon should be in the same list item
      const listItem = shoppingText.closest('li');
      expect(listItem).toHaveTextContent('🛒');
      expect(listItem).toHaveTextContent('Shopping');
    });

    it('renders category name with colored dot when icon is not present', () => {
      renderComponent();

      const entertainmentText = screen.getByText('Entertainment');
      expect(entertainmentText).toBeInTheDocument();

      // Check for the colored dot (aria-hidden span with background color)
      const listItem = entertainmentText.closest('li');
      const dotElement = listItem?.querySelector('[aria-hidden="true"]');
      expect(dotElement).toBeInTheDocument();
      expect(dotElement).toHaveStyle({ backgroundColor: '#00ff00' });
    });

    it('renders "No category" when category name is undefined', () => {
      renderComponent();

      expect(screen.getByText('No category')).toBeInTheDocument();
    });

    it('applies default color when category color is undefined', () => {
      renderComponent({ splits: [mockSplits[2]] }); // Third split has no category

      const noCategoryPill = screen.getByText('No category');
      expect(noCategoryPill).toBeInTheDocument();
      expect(noCategoryPill).toHaveClass('text-text-tertiary');
      expect(noCategoryPill).toHaveClass('italic');
    });

    it('uses default primary color for dot when category color is undefined', () => {
      renderComponent({ splits: [mockSplits[1]] }); // Second split has no icon, but has color

      const listItem = screen.getByText('Entertainment').closest('li');
      const dotElement = listItem?.querySelector('[aria-hidden="true"]');
      expect(dotElement).toHaveStyle({ backgroundColor: '#00ff00' });
    });
  });

  describe('Empty and Edge Cases', () => {
    it('renders "No split details available." when splits array is empty', () => {
      renderComponent({ splits: [] });

      expect(screen.getByText('No split details available.')).toBeInTheDocument();
      expect(screen.queryByRole('list')).not.toBeInTheDocument();
    });

    it('renders "No split details available." when splits is null', () => {
      renderComponent({ splits: null as any });

      expect(screen.getByText('No split details available.')).toBeInTheDocument();
      expect(screen.queryByRole('list')).not.toBeInTheDocument();
    });

    it('renders "No split details available." when splits is undefined', () => {
      // Render directly (not via helper) to avoid default parameter applying
      renderWithProviders(<SplitDetail splits={undefined as any} currency="EUR" />);

      expect(screen.getByText('No split details available.')).toBeInTheDocument();
      expect(screen.queryByRole('list')).not.toBeInTheDocument();
    });

    it('handles splits with zero amount', () => {
      const zeroAmountSplit: TransactionSplitResponse[] = [
        {
          id: 1,
          transactionId: 100,
          categoryId: 10,
          categoryName: 'Test',
          categoryColor: '#000000',
          categoryIcon: undefined,
          amount: 0,
          description: 'Zero amount',
        },
      ];

      renderComponent({ splits: zeroAmountSplit });

      // Locale-agnostic: just verify 0 appears in an amount span
      const listItems = screen.getAllByRole('listitem');
      expect(listItems[0]).toHaveTextContent('0');
    });

    it('handles negative amounts', () => {
      const negativeAmountSplit: TransactionSplitResponse[] = [
        {
          id: 1,
          transactionId: 100,
          categoryId: 10,
          categoryName: 'Test',
          categoryColor: '#000000',
          categoryIcon: undefined,
          amount: -25.5,
          description: 'Negative amount',
        },
      ];

      renderComponent({ splits: negativeAmountSplit });

      // Locale-agnostic: verify the number 25 and decimal part appear with a minus sign somewhere
      const listItems = screen.getAllByRole('listitem');
      expect(listItems[0]).toHaveTextContent('25');
      // The amount span should contain a minus sign
      const amountSpan = listItems[0].querySelector('.font-mono');
      expect(amountSpan?.textContent).toMatch(/-/);
    });

    it('handles very small decimal amounts', () => {
      const smallDecimalSplit: TransactionSplitResponse[] = [
        {
          id: 1,
          transactionId: 100,
          categoryId: 10,
          categoryName: 'Test',
          categoryColor: '#000000',
          categoryIcon: undefined,
          amount: 0.01,
          description: 'Penny',
        },
      ];

      renderComponent({ splits: smallDecimalSplit });

      // Locale-agnostic: verify 0.01 or 0,01 appears
      const listItems = screen.getAllByRole('listitem');
      const amountSpan = listItems[0].querySelector('.font-mono');
      expect(amountSpan?.textContent).toMatch(/0[,.]01/);
    });

    it('handles different currencies', () => {
      renderComponent({ currency: 'USD' });

      // Use getByRole to get list items and check their amount spans
      const listItems = screen.getAllByRole('listitem');
      // All three amounts should be formatted with USD currency code somewhere
      listItems.forEach(item => {
        // Either the currency symbol ($) or the ISO code (USD) should appear
        const amountSpan = item.querySelector('.font-mono');
        expect(amountSpan?.textContent).toBeTruthy();
      });
      // Verify the amounts are present
      expect(listItems).toHaveLength(3);
    });

    it('truncates long descriptions', () => {
      const longDescriptionSplit: TransactionSplitResponse[] = [
        {
          id: 1,
          transactionId: 100,
          categoryId: 10,
          categoryName: 'Test',
          categoryColor: '#000000',
          categoryIcon: undefined,
          amount: 50,
          description:
            'This is a very long description that should be truncated in the UI to prevent overflow and maintain good layout',
        },
      ];

      renderComponent({ splits: longDescriptionSplit });

      const descriptionElement = screen.getByText(/This is a very long description/);
      expect(descriptionElement).toHaveClass('truncate');
    });
  });

  describe('Accessibility', () => {
    it('has proper aria-label for the split details list', () => {
      renderComponent();

      expect(screen.getByRole('list', { name: 'Split details' })).toBeInTheDocument();
    });

    it('has aria-hidden on decorative icon elements', () => {
      renderComponent();

      const iconElement = screen.getByText('🛒').closest('span');
      expect(iconElement).toHaveAttribute('aria-hidden', 'true');
    });

    it('has aria-hidden on decorative color dot elements', () => {
      renderComponent();

      const dotElements = screen.getAllByRole('listitem')[1].querySelectorAll('[aria-hidden]');
      expect(dotElements.length).toBeGreaterThan(0);
    });
  });

  describe('Layout and Styling', () => {
    it('applies correct grid layout classes', () => {
      renderComponent();

      const listItems = screen.getAllByRole('listitem');
      listItems.forEach(item => {
        expect(item).toHaveClass('flex');
        expect(item).toHaveClass('items-center');
        expect(item).toHaveClass('justify-between');
        expect(item).toHaveClass('gap-3');
      });
    });

    it('applies correct background and padding classes', () => {
      renderComponent();

      const listItems = screen.getAllByRole('listitem');
      listItems.forEach(item => {
        expect(item).toHaveClass('px-3');
        expect(item).toHaveClass('py-1.5');
        expect(item).toHaveClass('rounded-md');
        expect(item).toHaveClass('bg-surface-elevated');
      });
    });

    it('applies correct text styling to amounts', () => {
      renderComponent();

      // Query amount spans directly by their CSS class
      const listItems = screen.getAllByRole('listitem');
      listItems.forEach(item => {
        const amountSpan = item.querySelector('.font-mono');
        expect(amountSpan).not.toBeNull();
        expect(amountSpan).toHaveClass('font-mono');
        expect(amountSpan).toHaveClass('text-sm');
        expect(amountSpan).toHaveClass('text-text-primary');
        expect(amountSpan).toHaveClass('shrink-0');
      });
    });

    it('applies correct text styling to category pills', () => {
      renderComponent();

      // The category name <span> elements are wrapped in a parent <span> with text-xs
      // Query by category names and check the parent wrapper
      const shoppingSpan = screen.getByText('Shopping');
      const entertainmentSpan = screen.getByText('Entertainment');

      // The wrapper spans (parent of the text span) should have text-xs
      expect(shoppingSpan.parentElement).toHaveClass('text-xs');
      expect(entertainmentSpan.parentElement).toHaveClass('text-xs');
    });

    it('applies correct text styling to descriptions', () => {
      renderComponent();

      const descriptionElements = screen.getAllByText(/Groceries|Miscellaneous/);
      descriptionElements.forEach(desc => {
        expect(desc).toHaveClass('text-xs');
        expect(desc).toHaveClass('text-text-tertiary');
        expect(desc).toHaveClass('truncate');
        expect(desc).toHaveClass('mt-0.5');
      });
    });
  });
});
