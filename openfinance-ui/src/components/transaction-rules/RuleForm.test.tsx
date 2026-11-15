import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderWithProviders, screen, fireEvent, waitFor } from '@/test/test-utils';
import { act } from 'react';
import i18n from '@/test/i18n-test';
import { RuleForm } from './RuleForm';
import type { TransactionRule, TransactionRuleRequest } from '@/types/transactionRules';

// ---------------------------------------------------------------------------
// Mocks — Radix UI Select and custom components don't render in jsdom
// (see LESSONS_LEARNED.md entry #2 and #6)
// ---------------------------------------------------------------------------

vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: ({
    value,
    onValueChange,
    placeholder,
  }: {
    value?: number;
    onValueChange: (id: number | undefined) => void;
    placeholder?: string;
  }) => (
    <select
      aria-label="Category name"
      value={value ?? ''}
      onChange={e => onValueChange(e.target.value ? Number(e.target.value) : undefined)}
    >
      <option value="">{placeholder ?? 'Select category'}</option>
      <option value="1">Groceries</option>
      <option value="2">Transport</option>
    </select>
  ),
}));

vi.mock('@/components/ui/PayeeSelector', () => ({
  PayeeSelector: ({
    value,
    onValueChange,
    placeholder,
  }: {
    value?: string;
    onValueChange: (val: string | undefined) => void;
    placeholder?: string;
  }) => (
    <select
      aria-label="Payee name"
      value={value ?? ''}
      onChange={e => onValueChange(e.target.value || undefined)}
    >
      <option value="">{placeholder ?? 'Select payee'}</option>
      <option value="Store">Store</option>
    </select>
  ),
  PayeeCombobox: ({
    value,
    onValueChange,
    placeholder,
    ariaLabel,
  }: {
    value: string;
    onValueChange: (val: string) => void;
    placeholder?: string;
    ariaLabel?: string;
  }) => (
    <input
      aria-label={ariaLabel || placeholder || 'Type payee...'}
      value={value}
      onChange={e => onValueChange(e.target.value)}
      placeholder={placeholder}
    />
  ),
}));

vi.mock('@/components/transactions/TagInput', () => ({
  TagInput: ({
    value,
    onChange,
    placeholder,
  }: {
    value: string[];
    onChange: (tags: string[]) => void;
    placeholder?: string;
  }) => (
    <input
      aria-label="Tag name"
      value={value.join(',')}
      placeholder={placeholder}
      onChange={e => onChange(e.target.value ? e.target.value.split(',') : [])}
    />
  ),
}));

vi.mock('@/hooks/useCategories', () => ({
  useCategories: () => ({
    data: [
      { id: 1, name: 'Groceries' },
      { id: 2, name: 'Transport' },
    ],
  }),
}));

vi.mock('@/hooks/useTransactionTags', () => ({
  usePopularTags: () => ({ data: ['food', 'travel'] }),
}));

describe('RuleForm', () => {
  const mockOnSubmit = vi.fn();
  const mockOnOpenChange = vi.fn();

  const mockRule: TransactionRule = {
    id: 1,
    name: 'Test Rule',
    priority: 5,
    isEnabled: false,
    conditions: [
      {
        id: 1,
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'test',
        sortOrder: 0,
      },
    ],
    actions: [
      {
        id: 1,
        actionType: 'SET_CATEGORY',
        actionValue: 'Groceries',
        sortOrder: 0,
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it.each([
    ['priorityNegative', { priority: -1 }],
    [
      'conditionValueRequired',
      { conditions: [{ field: 'DESCRIPTION', operator: 'CONTAINS', value: ' ', sortOrder: 0 }] },
    ],
    [
      'actionValueRequired',
      { actions: [{ actionType: 'SET_CATEGORY', actionValue: ' ', sortOrder: 0 }] },
    ],
  ] as const)('rejects invalid saved-rule fields: %s', (error, overrides) => {
    renderWithProviders(
      <RuleForm
        open
        onOpenChange={mockOnOpenChange}
        onSubmit={mockOnSubmit}
        rule={{ ...mockRule, ...overrides } as TransactionRule}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'Update Rule' }));
    expect(mockOnSubmit).not.toHaveBeenCalled();
    if (error === 'priorityNegative')
      expect(screen.getByText('Enter a value of at least 0.')).toBeVisible();
    else expect(screen.getByText(i18n.t(`rules:form.errors.${error}`))).toBeVisible();
  });
  it.each([1, 2])('duplicate-name validation excludes only the current rule (%s)', id => {
    renderWithProviders(
      <RuleForm
        open
        onOpenChange={mockOnOpenChange}
        onSubmit={mockOnSubmit}
        rule={mockRule}
        existingRules={[{ ...mockRule, id, name: 'TEST RULE' }]}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'Update Rule' }));
    if (id === 1) expect(mockOnSubmit).toHaveBeenCalledOnce();
    else {
      expect(mockOnSubmit).not.toHaveBeenCalled();
      expect(screen.getByText(i18n.t('rules:form.errors.nameDuplicate'))).toBeVisible();
    }
  });
  it.each([
    'NaN',
    'Infinity',
    'not-a-number',
    '0.0000000000000000001',
    '100000000000000000000000000',
  ])('rejects unbookable split amount %s', amount => {
    renderWithProviders(
      <RuleForm
        open
        onOpenChange={mockOnOpenChange}
        onSubmit={mockOnSubmit}
        rule={{
          ...mockRule,
          actions: [
            {
              actionType: 'ADD_SPLIT',
              actionValue: 'Groceries',
              actionValue2: amount,
              sortOrder: 0,
            },
          ],
        }}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'Update Rule' }));
    expect(mockOnSubmit).not.toHaveBeenCalled();
    if (['NaN', 'Infinity', 'not-a-number'].includes(amount))
      expect(
        screen.getAllByRole('textbox').some(input => input.getAttribute('aria-invalid') === 'true')
      ).toBe(true);
    else expect(screen.getByText(i18n.t('rules:form.errors.splitAmountRequired'))).toBeVisible();
  });
  it('preserves OR matching, exposes server errors and prevents cancelling a pending save', () => {
    renderWithProviders(
      <RuleForm
        open
        onOpenChange={mockOnOpenChange}
        onSubmit={mockOnSubmit}
        isLoading
        submitError="Rule could not be saved"
        rule={{ ...mockRule, conditionMatch: 'OR' }}
      />
    );
    expect(screen.getByText('Rule could not be saved')).toBeVisible();
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    expect(mockOnOpenChange).not.toHaveBeenCalled();
  });

  it.each(['', '0', '-1'])('rejects an Add Split amount of %s', async amount => {
    renderWithProviders(
      <RuleForm
        open
        onOpenChange={mockOnOpenChange}
        onSubmit={mockOnSubmit}
        rule={{
          ...mockRule,
          actions: [
            {
              actionType: 'ADD_SPLIT',
              actionValue: 'Groceries',
              actionValue2: amount,
              sortOrder: 0,
            },
          ],
        }}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: 'Update Rule' }));
    expect(mockOnSubmit).not.toHaveBeenCalled();
    expect(await screen.findByText('Every split must have a positive amount.')).toBeInTheDocument();
  });

  it('should render create form when no rule provided', () => {
    renderWithProviders(
      <RuleForm open={true} onOpenChange={mockOnOpenChange} rule={null} onSubmit={mockOnSubmit} />
    );

    expect(screen.getByRole('heading', { name: 'Create Rule' })).toBeInTheDocument();
    expect(screen.getByLabelText(/rule name/i)).toHaveValue('');
    expect(screen.getByRole('textbox', { name: /priority/i })).toHaveValue('0');
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('should render edit form when rule provided', () => {
    renderWithProviders(
      <RuleForm
        open={true}
        onOpenChange={mockOnOpenChange}
        rule={mockRule}
        onSubmit={mockOnSubmit}
      />
    );

    expect(screen.getByText('Edit Rule')).toBeInTheDocument();
    expect(screen.getByLabelText(/rule name/i)).toHaveValue('Test Rule');
    expect(screen.getByRole('textbox', { name: /priority/i })).toHaveValue('5');
    expect(screen.getByText('Inactive')).toBeInTheDocument();
  });

  it('should validate empty name', async () => {
    renderWithProviders(
      <RuleForm open={true} onOpenChange={mockOnOpenChange} rule={null} onSubmit={mockOnSubmit} />
    );

    const submitButton = screen.getByRole('button', { name: /create rule/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('Rule name is required.')).toBeInTheDocument();
    });
  });

  it('should validate name length', async () => {
    renderWithProviders(
      <RuleForm open={true} onOpenChange={mockOnOpenChange} rule={null} onSubmit={mockOnSubmit} />
    );

    const nameInput = screen.getByLabelText(/rule name/i);
    fireEvent.change(nameInput, { target: { value: 'a'.repeat(101) } });

    const submitButton = screen.getByRole('button', { name: /create rule/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('Rule name must not exceed 100 characters.')).toBeInTheDocument();
    });
  });

  it('should validate no conditions', async () => {
    renderWithProviders(
      <RuleForm open={true} onOpenChange={mockOnOpenChange} rule={null} onSubmit={mockOnSubmit} />
    );

    const nameInput = screen.getByLabelText(/rule name/i);
    fireEvent.change(nameInput, { target: { value: 'Test Rule' } });

    const submitButton = screen.getByRole('button', { name: /create rule/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('At least one condition is required.')).toBeInTheDocument();
    });
  });

  it('should validate no actions', async () => {
    renderWithProviders(
      <RuleForm open={true} onOpenChange={mockOnOpenChange} rule={null} onSubmit={mockOnSubmit} />
    );

    const nameInput = screen.getByLabelText(/rule name/i);
    fireEvent.change(nameInput, { target: { value: 'Test Rule' } });

    // Add a condition
    const addConditionButton = screen.getByRole('button', { name: /add condition/i });
    fireEvent.click(addConditionButton);

    const conditionValueInput = screen.getByRole('textbox', { name: 'Condition value' });
    fireEvent.change(conditionValueInput, { target: { value: 'test' } });

    const submitButton = screen.getByRole('button', { name: /create rule/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('At least one action is required.')).toBeInTheDocument();
    });
  });

  it('should submit valid form data', async () => {
    renderWithProviders(
      <RuleForm open={true} onOpenChange={mockOnOpenChange} rule={null} onSubmit={mockOnSubmit} />
    );

    // Fill name
    const nameInput = screen.getByLabelText(/rule name/i);
    fireEvent.change(nameInput, { target: { value: 'Test Rule' } });

    // Set priority
    const priorityInput = screen.getByRole('textbox', { name: /priority/i });
    fireEvent.change(priorityInput, { target: { value: '5' } });

    // Toggle enabled
    const toggleButton = screen.getByRole('switch');
    fireEvent.click(toggleButton);

    // Add condition
    const addConditionButton = screen.getByRole('button', { name: /add condition/i });
    fireEvent.click(addConditionButton);

    const conditionValueInput = screen.getByRole('textbox', { name: 'Condition value' });
    fireEvent.change(conditionValueInput, { target: { value: 'test' } });

    // Add action
    const addActionButton = screen.getByRole('button', { name: /add action/i });
    fireEvent.click(addActionButton);

    // CategorySelect is mocked as a <select>; choose option with value '1' → 'Groceries'
    const categorySelect = screen.getByRole('combobox', { name: 'Category name' });
    fireEvent.change(categorySelect, { target: { value: '1' } });

    // Submit
    const submitButton = screen.getByRole('button', { name: /create rule/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledWith({
        name: 'Test Rule',
        priority: 5,
        isEnabled: false,
        conditions: [
          {
            field: 'DESCRIPTION',
            operator: 'CONTAINS',
            value: 'test',
            sortOrder: 0,
          },
        ],
        actions: [
          {
            actionType: 'SET_CATEGORY',
            actionValue: 'Groceries',
            actionValue2: undefined,
            actionValue3: undefined,
            sortOrder: 0,
          },
        ],
      });
    });
  });

  it('should pre-populate form when editing', () => {
    renderWithProviders(
      <RuleForm
        open={true}
        onOpenChange={mockOnOpenChange}
        rule={mockRule}
        onSubmit={mockOnSubmit}
      />
    );

    expect(screen.getByLabelText(/rule name/i)).toHaveValue('Test Rule');
    expect(screen.getByRole('textbox', { name: /priority/i })).toHaveValue('5');
    expect(screen.getByText('Inactive')).toBeInTheDocument();
    expect(screen.getByDisplayValue('test')).toBeInTheDocument();
    // CategorySelect is mocked as a <select>; the selected option matches the name 'Groceries'
    expect(screen.getByRole('combobox', { name: 'Category name' })).toBeInTheDocument();
  });

  it('should call onOpenChange when cancel is clicked', () => {
    renderWithProviders(
      <RuleForm open={true} onOpenChange={mockOnOpenChange} rule={null} onSubmit={mockOnSubmit} />
    );

    const cancelButton = screen.getByRole('button', { name: /cancel/i });
    fireEvent.click(cancelButton);

    expect(mockOnOpenChange).toHaveBeenCalledWith(false);
  });

  it('should disable buttons when loading', () => {
    renderWithProviders(
      <RuleForm
        open={true}
        onOpenChange={mockOnOpenChange}
        rule={null}
        onSubmit={mockOnSubmit}
        isLoading={true}
      />
    );

    expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /create rule/i })).toBeDisabled();
  });

  it('should show update button when editing', () => {
    renderWithProviders(
      <RuleForm
        open={true}
        onOpenChange={mockOnOpenChange}
        rule={mockRule}
        onSubmit={mockOnSubmit}
      />
    );

    expect(screen.getByRole('button', { name: /update rule/i })).toBeInTheDocument();
  });
});
