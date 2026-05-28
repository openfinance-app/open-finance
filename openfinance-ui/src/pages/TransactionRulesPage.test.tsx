import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication } from '@/test/test-utils';
import userEvent from '@testing-library/user-event';
import TransactionRulesPage from '@/pages/TransactionRulesPage';

let mockRules: any[] = [];
let mockIsLoading = false;
let mockError: Error | null = null;
const mockCreateMutateAsync = vi.fn();
const mockUpdateMutateAsync = vi.fn();
const mockDeleteMutateAsync = vi.fn();
const mockToggleMutateAsync = vi.fn();

vi.mock('@/hooks/useTransactionRules', () => ({
  useTransactionRules: () => ({ data: mockRules, isLoading: mockIsLoading, error: mockError }),
  useCreateRule: () => ({ mutate: vi.fn(), mutateAsync: mockCreateMutateAsync, isPending: false }),
  useUpdateRule: () => ({ mutate: vi.fn(), mutateAsync: mockUpdateMutateAsync, isPending: false }),
  useDeleteRule: () => ({ mutate: vi.fn(), mutateAsync: mockDeleteMutateAsync, isPending: false }),
  useToggleRule: () => ({ mutate: vi.fn(), mutateAsync: mockToggleMutateAsync, isPending: false }),
}));

vi.mock('@/components/transaction-rules/RuleList', () => ({
  RuleList: ({ rules, onEdit, onDelete, onToggle, onCreateFirst }: any) => (
    <div data-testid="rule-list">
      {rules.map((r: any) => (
        <div key={r.id}>
          <span>{r.name}</span>
          <button data-testid={`edit-${r.id}`} onClick={() => onEdit(r)}>Edit</button>
          <button data-testid={`delete-${r.id}`} onClick={() => onDelete(r)}>Delete</button>
          <button data-testid={`toggle-${r.id}`} onClick={() => onToggle(r)}>Toggle</button>
        </div>
      ))}
      {rules.length === 0 && <button data-testid="create-first" onClick={onCreateFirst}>First</button>}
    </div>
  ),
}));

vi.mock('@/components/transaction-rules/RuleForm', () => ({
  RuleForm: ({ open, onSubmit, onOpenChange, rule, submitError }: any) => (
    open ? (
      <div data-testid="rule-form">
        {rule && <span data-testid="editing-rule">{rule.name}</span>}
        {submitError && <span data-testid="submit-error">{submitError}</span>}
        <button data-testid="submit-form" onClick={() => onSubmit({ name: 'Test', matchField: 'DESCRIPTION', matchType: 'CONTAINS', matchValue: 'test' })}>Submit</button>
        <button data-testid="close-form" onClick={() => onOpenChange(false)}>Close</button>
      </div>
    ) : null
  ),
}));

vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: ({ open, onConfirm, onOpenChange }: any) => (
    open ? (
      <div data-testid="confirm-dialog">
        <button data-testid="confirm-delete" onClick={onConfirm}>Confirm</button>
        <button data-testid="cancel-delete" onClick={() => onOpenChange(false)}>Cancel</button>
      </div>
    ) : null
  ),
}));

describe('TransactionRulesPage', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    mockRules = [];
    mockIsLoading = false;
    mockError = null;
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders the page heading', () => {
    renderWithProviders(<TransactionRulesPage />);
    expect(screen.getByRole('heading')).toBeInTheDocument();
  });

  it('renders the rule list', () => {
    renderWithProviders(<TransactionRulesPage />);
    expect(screen.getByTestId('rule-list')).toBeInTheDocument();
  });

  it('has an add rule button', () => {
    mockRules = [{ id: 1, name: 'Rule A', isEnabled: true }];
    renderWithProviders(<TransactionRulesPage />);
    expect(screen.getByRole('button', { name: /add rule/i })).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockError = new Error('Failed to load');
    renderWithProviders(<TransactionRulesPage />);
    expect(screen.getByText(/error|failed/i)).toBeInTheDocument();
  });

  it('shows stats with rule counts', () => {
    mockRules = [
      { id: 1, name: 'Rule A', isEnabled: true },
      { id: 2, name: 'Rule B', isEnabled: false },
      { id: 3, name: 'Rule C', isEnabled: true },
    ];
    renderWithProviders(<TransactionRulesPage />);
    // Total rules = 3, active = 2, inactive = 1
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('renders rule names in the list', () => {
    mockRules = [
      { id: 1, name: 'Auto Categorize', isEnabled: true },
    ];
    renderWithProviders(<TransactionRulesPage />);
    expect(screen.getByText('Auto Categorize')).toBeInTheDocument();
  });

  it('opens form on add button click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByTestId('create-first'));
    expect(screen.getByTestId('rule-form')).toBeInTheDocument();
  });

  it('opens form with rule data on edit click', async () => {
    const user = userEvent.setup();
    mockRules = [{ id: 1, name: 'My Rule', isEnabled: true }];
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByTestId('edit-1'));
    expect(screen.getByTestId('rule-form')).toBeInTheDocument();
    expect(screen.getByTestId('editing-rule')).toHaveTextContent('My Rule');
  });

  it('opens delete confirmation on delete click', async () => {
    const user = userEvent.setup();
    mockRules = [{ id: 1, name: 'My Rule', isEnabled: true }];
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByTestId('delete-1'));
    expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
  });

  it('calls deleteRule on confirm', async () => {
    const user = userEvent.setup();
    mockDeleteMutateAsync.mockResolvedValue(undefined);
    mockRules = [{ id: 1, name: 'My Rule', isEnabled: true }];
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByTestId('delete-1'));
    await user.click(screen.getByTestId('confirm-delete'));
    expect(mockDeleteMutateAsync).toHaveBeenCalledWith(1);
  });

  it('calls toggleRule on toggle click', async () => {
    const user = userEvent.setup();
    mockToggleMutateAsync.mockResolvedValue(undefined);
    mockRules = [{ id: 1, name: 'My Rule', isEnabled: true }];
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByTestId('toggle-1'));
    expect(mockToggleMutateAsync).toHaveBeenCalledWith(1);
  });

  it('calls createRule on form submit for new rule', async () => {
    const user = userEvent.setup();
    mockCreateMutateAsync.mockResolvedValue(undefined);
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByRole('button', { name: /add rule/i }));
    await user.click(screen.getByTestId('submit-form'));
    expect(mockCreateMutateAsync).toHaveBeenCalled();
  });

  it('calls updateRule on form submit for existing rule', async () => {
    const user = userEvent.setup();
    mockUpdateMutateAsync.mockResolvedValue(undefined);
    mockRules = [{ id: 1, name: 'My Rule', isEnabled: true }];
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByTestId('edit-1'));
    await user.click(screen.getByTestId('submit-form'));
    expect(mockUpdateMutateAsync).toHaveBeenCalled();
  });

  it('closes form and clears editing state', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByRole('button', { name: /add rule/i }));
    expect(screen.getByTestId('rule-form')).toBeInTheDocument();
    await user.click(screen.getByTestId('close-form'));
    expect(screen.queryByTestId('rule-form')).not.toBeInTheDocument();
  });

  it('cancels delete dialog', async () => {
    const user = userEvent.setup();
    mockRules = [{ id: 1, name: 'My Rule', isEnabled: true }];
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByTestId('delete-1'));
    expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
    await user.click(screen.getByTestId('cancel-delete'));
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
  });

  it('shows loading state', () => {
    mockIsLoading = true;
    renderWithProviders(<TransactionRulesPage />);
    expect(screen.queryByTestId('rule-list')).not.toBeInTheDocument();
  });

  it('opens form via create-first button', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByTestId('create-first'));
    expect(screen.getByTestId('rule-form')).toBeInTheDocument();
  });

  it('handles delete failure gracefully', async () => {
    const user = userEvent.setup();
    mockDeleteMutateAsync.mockRejectedValue(new Error('fail'));
    mockRules = [{ id: 1, name: 'My Rule', isEnabled: true }];
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByTestId('delete-1'));
    await user.click(screen.getByTestId('confirm-delete'));
    // Should not throw
    expect(mockDeleteMutateAsync).toHaveBeenCalled();
  });

  it('handles toggle failure gracefully', async () => {
    const user = userEvent.setup();
    mockToggleMutateAsync.mockRejectedValue(new Error('fail'));
    mockRules = [{ id: 1, name: 'My Rule', isEnabled: true }];
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByTestId('toggle-1'));
    expect(mockToggleMutateAsync).toHaveBeenCalled();
  });

  it('shows submit error on form submit failure', async () => {
    const user = userEvent.setup();
    mockCreateMutateAsync.mockRejectedValue({ response: { data: { message: 'Duplicate rule' } } });
    renderWithProviders(<TransactionRulesPage />);
    await user.click(screen.getByRole('button', { name: /add rule/i }));
    await user.click(screen.getByTestId('submit-form'));
    await screen.findByTestId('submit-error');
    expect(screen.getByTestId('submit-error')).toHaveTextContent('Duplicate rule');
  });
});
