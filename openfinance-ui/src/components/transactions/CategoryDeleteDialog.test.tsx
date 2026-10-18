import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, userEvent } from '@/test/test-utils';
import { CategoryDeleteDialog } from '@/components/transactions/CategoryDeleteDialog';

const remove = vi.fn();
const refetch = vi.fn();
let impact = {
  system: false,
  hasChildren: false,
  transactionCount: 0,
  splitCount: 0,
  recurringCount: 0,
  payeeCount: 0,
  budgetCount: 0,
  deletable: true,
};
let isFetching = false;
let isError = false;
vi.mock('@/hooks/useTransactions', () => ({
  useCategoryDeletionImpact: () => ({ data: impact, isFetching, isError, refetch }),
  useDeleteCategory: () => ({ mutateAsync: remove, isPending: false }),
}));

describe('category deletion warning', () => {
  beforeEach(() => {
    mockAuthentication();
    remove.mockReset().mockResolvedValue(undefined);
    refetch.mockReset();
    impact = {
      system: false,
      hasChildren: false,
      transactionCount: 0,
      splitCount: 0,
      recurringCount: 0,
      payeeCount: 0,
      budgetCount: 0,
      deletable: true,
    };
    isFetching = false;
    isError = false;
  });

  it('warns about every linked budget and allows the confirmed cascade', async () => {
    impact.budgetCount = 3;
    const close = vi.fn();
    renderWithProviders(
      <CategoryDeleteDialog category={{ id: 12, name: 'Holiday' }} onClose={close} />
    );
    expect(screen.getByText(/3 linked budgets and their alerts/i)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole('button', { name: /^delete/i }));
    await waitFor(() => expect(remove).toHaveBeenCalledWith(12));
    expect(close).toHaveBeenCalled();
  });

  it.each(['splitCount', 'recurringCount', 'payeeCount'] as const)(
    'blocks deletion when %s is nonzero',
    async field => {
      impact[field] = 2;
      impact.deletable = false;
      renderWithProviders(
        <CategoryDeleteDialog category={{ id: 12, name: 'Holiday' }} onClose={vi.fn()} />
      );
      expect(screen.getByRole('alert')).toHaveTextContent(/still in use/i);
      expect(screen.getByRole('button', { name: /^delete/i })).toBeDisabled();
      expect(remove).not.toHaveBeenCalled();
    }
  );

  it('does not allow deletion before the dependency check finishes or when it fails', () => {
    isFetching = true;
    const view = renderWithProviders(
      <CategoryDeleteDialog category={{ id: 12, name: 'Holiday' }} onClose={vi.fn()} />
    );
    expect(screen.getByRole('button', { name: /^delete/i })).toBeDisabled();
    isFetching = false;
    isError = true;
    view.rerender(
      <CategoryDeleteDialog category={{ id: 12, name: 'Holiday' }} onClose={vi.fn()} />
    );
    expect(screen.getByRole('button', { name: /^delete/i })).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent(/Could not check linked records/i);
  });

  it('keeps the dialog open and displays a server-side dependency rejection', async () => {
    remove.mockRejectedValue({
      response: { data: { message: 'Category now has a recurring template.' } },
    });
    const close = vi.fn();
    renderWithProviders(
      <CategoryDeleteDialog category={{ id: 12, name: 'Holiday' }} onClose={close} />
    );
    await userEvent.setup().click(screen.getByRole('button', { name: /^delete/i }));
    expect(await screen.findByText('Category now has a recurring template.')).toBeInTheDocument();
    expect(close).not.toHaveBeenCalled();
    expect(refetch).toHaveBeenCalled();
  });
});
