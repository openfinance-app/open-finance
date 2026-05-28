import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

const mockCreateMutateAsync = vi.fn().mockResolvedValue({});
const mockDeleteMutateAsync = vi.fn().mockResolvedValue({});

vi.mock('@/hooks/useTransactions', () => ({
  useCategories: vi.fn(() => ({
    data: [
      { id: 1, name: 'Salary', type: 'INCOME', icon: '💰', color: '#00ff00' },
      { id: 2, name: 'Food', type: 'EXPENSE', icon: '🍕', color: '#ff0000', parentId: null },
      { id: 3, name: 'Transport', type: 'EXPENSE', icon: '🚗', color: '#0000ff' },
      { id: 4, name: 'Fast Food', type: 'EXPENSE', icon: '🍔', color: '#ff0000', parentId: 2 },
    ],
  })),
  useCreateCategory: () => ({ mutateAsync: mockCreateMutateAsync, isPending: false }),
  useDeleteCategory: () => ({ mutateAsync: mockDeleteMutateAsync, isPending: false }),
}));

vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: ({ open, onConfirm, title, description }: any) =>
    open ? (
      <div data-testid="confirm-dialog">
        <span>{description}</span>
        <button onClick={onConfirm}>Confirm</button>
      </div>
    ) : null,
}));

import { CategoryManager } from './CategoryManager';

describe('CategoryManager', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
  });

  it('renders expense categories by default when open', () => {
    renderWithProviders(<CategoryManager open={true} onOpenChange={vi.fn()} />);
    expect(screen.getByText('Food')).toBeInTheDocument();
    expect(screen.getByText('Transport')).toBeInTheDocument();
    expect(screen.queryByText('Salary')).not.toBeInTheDocument();
  });

  it('does not render content when closed', () => {
    renderWithProviders(<CategoryManager open={false} onOpenChange={vi.fn()} />);
    expect(screen.queryByText('Food')).not.toBeInTheDocument();
  });

  it('switches to INCOME tab and shows income categories', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoryManager open={true} onOpenChange={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: /income/i }));
    expect(screen.getByText('Salary')).toBeInTheDocument();
    expect(screen.queryByText('Food')).not.toBeInTheDocument();
  });

  it('shows subcategory badge for categories with parentId', () => {
    renderWithProviders(<CategoryManager open={true} onOpenChange={vi.fn()} />);
    expect(screen.getByText('Subcategory')).toBeInTheDocument();
  });

  it('creates a category when typing name and clicking add button', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoryManager open={true} onOpenChange={vi.fn()} />);
    const input = screen.getByRole('textbox');
    await user.type(input, 'Groceries');
    const addButton = screen.getAllByRole('button').find(
      (btn) => !btn.textContent?.includes('Expenses') && !btn.textContent?.includes('Income') && !btn.textContent?.includes('Close') && !btn.textContent?.includes('Delete')
    );
    await user.click(addButton!);
    expect(mockCreateMutateAsync).toHaveBeenCalledWith({ name: 'Groceries', type: 'EXPENSE' });
  });

  it('creates a category on Enter key press', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoryManager open={true} onOpenChange={vi.fn()} />);
    const input = screen.getByRole('textbox');
    await user.type(input, 'Rent{Enter}');
    expect(mockCreateMutateAsync).toHaveBeenCalledWith({ name: 'Rent', type: 'EXPENSE' });
  });

  it('does not create category with empty name', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoryManager open={true} onOpenChange={vi.fn()} />);
    const input = screen.getByRole('textbox');
    await user.type(input, '   {Enter}');
    expect(mockCreateMutateAsync).not.toHaveBeenCalled();
  });

  it('opens delete confirmation when clicking delete button', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoryManager open={true} onOpenChange={vi.fn()} />);
    const deleteButtons = screen.getAllByLabelText('Delete category');
    await user.click(deleteButtons[0]);
    expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
  });

  it('deletes category when confirming deletion', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoryManager open={true} onOpenChange={vi.fn()} />);
    const deleteButtons = screen.getAllByLabelText('Delete category');
    await user.click(deleteButtons[0]);
    const confirmBtn = screen.getByText('Confirm');
    fireEvent.click(confirmBtn);
    expect(mockDeleteMutateAsync).toHaveBeenCalledWith(2);
  });

  it('calls onOpenChange(false) when Close button is clicked', async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    renderWithProviders(<CategoryManager open={true} onOpenChange={onOpenChange} />);
    const closeButtons = screen.getAllByRole('button', { name: /close/i });
    // Use the last one (the explicit Close button, not dialog's X)
    await user.click(closeButtons[closeButtons.length - 1]);
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('shows empty state when no categories for selected tab', async () => {
    const { useCategories } = await import('@/hooks/useTransactions');
    vi.mocked(useCategories).mockReturnValueOnce({ data: [], isLoading: false } as any);
    renderWithProviders(<CategoryManager open={true} onOpenChange={vi.fn()} />);
    expect(screen.getByText('No categories yet')).toBeInTheDocument();
  });

  it('clears input after successful category creation', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoryManager open={true} onOpenChange={vi.fn()} />);
    const input = screen.getByRole('textbox');
    await user.type(input, 'NewCat{Enter}');
    expect(input).toHaveValue('');
  });
});
