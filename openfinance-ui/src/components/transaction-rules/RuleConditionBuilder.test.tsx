/**
 * Tests for RuleConditionBuilder component
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderWithProviders, screen, fireEvent } from '@/test/test-utils';
import { act } from 'react';
import { RuleConditionBuilder, type ConditionDraft } from './RuleConditionBuilder';

// PayeeCombobox fetches payees via react-query; mock it with a plain input
// so DESCRIPTION-field tests don't depend on network/query state.
vi.mock('@/components/ui/PayeeSelector', () => ({
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
      aria-label={ariaLabel}
      value={value}
      placeholder={placeholder}
      onChange={(e) => onValueChange(e.target.value)}
    />
  ),
}));

describe('RuleConditionBuilder', () => {
  const mockOnChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render empty state when no conditions', () => {
    renderWithProviders(
      <RuleConditionBuilder
        conditions={[]}
        onChange={mockOnChange}
      />
    );

    expect(screen.getByText('No conditions added yet. Add at least one condition.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /add condition/i })).toBeInTheDocument();
  });

  it('should render conditions when provided', () => {
    const conditions: ConditionDraft[] = [
      {
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'test',
        sortOrder: 0,
      },
    ];

    renderWithProviders(
      <RuleConditionBuilder
        conditions={conditions}
        onChange={mockOnChange}
      />
    );

    expect(screen.getByDisplayValue('Description / Payee')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Contains')).toBeInTheDocument();
    expect(screen.getByDisplayValue('test')).toBeInTheDocument();
  });

  it('should add a new condition when add button is clicked', () => {
    renderWithProviders(
      <RuleConditionBuilder
        conditions={[]}
        onChange={mockOnChange}
      />
    );

    const addButton = screen.getByRole('button', { name: /add condition/i });
    fireEvent.click(addButton);

    expect(mockOnChange).toHaveBeenCalledWith([
      {
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: '',
        sortOrder: 0,
      },
    ]);
  });

  it('should remove a condition when remove button is clicked', () => {
    const conditions: ConditionDraft[] = [
      {
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'test1',
        sortOrder: 0,
      },
      {
        field: 'AMOUNT',
        operator: 'GREATER_THAN',
        value: '100',
        sortOrder: 1,
      },
    ];

    renderWithProviders(
      <RuleConditionBuilder
        conditions={conditions}
        onChange={mockOnChange}
      />
    );

    const removeButtons = screen.getAllByRole('button', { name: /remove condition/i });
    fireEvent.click(removeButtons[0]);

    expect(mockOnChange).toHaveBeenCalledWith([
      {
        field: 'AMOUNT',
        operator: 'GREATER_THAN',
        value: '100',
        sortOrder: 0,
      },
    ]);
  });

  it('should update field and reset operator/value when field changes', () => {
    const conditions: ConditionDraft[] = [
      {
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'test',
        sortOrder: 0,
      },
    ];

    renderWithProviders(
      <RuleConditionBuilder
        conditions={conditions}
        onChange={mockOnChange}
      />
    );

    const fieldSelect = screen.getByRole('combobox', { name: 'Condition field' });
    fireEvent.change(fieldSelect, { target: { value: 'AMOUNT' } });

    expect(mockOnChange).toHaveBeenCalledWith([
      {
        field: 'AMOUNT',
        operator: 'EQUALS', // Default operator for AMOUNT
        value: '',
        sortOrder: 0,
      },
    ]);
  });

  it('should update operator when operator changes', () => {
    const conditions: ConditionDraft[] = [
      {
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'test',
        sortOrder: 0,
      },
    ];

    renderWithProviders(
      <RuleConditionBuilder
        conditions={conditions}
        onChange={mockOnChange}
      />
    );

    const operatorSelect = screen.getByRole('combobox', { name: 'Condition operator' });
    fireEvent.change(operatorSelect, { target: { value: 'EQUALS' } });

    expect(mockOnChange).toHaveBeenCalledWith([
      {
        field: 'DESCRIPTION',
        operator: 'EQUALS',
        value: 'test',
        sortOrder: 0,
      },
    ]);
  });

  it('should update value when value changes', () => {
    const conditions: ConditionDraft[] = [
      {
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'test',
        sortOrder: 0,
      },
    ];

    renderWithProviders(
      <RuleConditionBuilder
        conditions={conditions}
        onChange={mockOnChange}
      />
    );

    const valueInput = screen.getByRole('textbox', { name: 'Condition value' });
    fireEvent.change(valueInput, { target: { value: 'new value' } });

    expect(mockOnChange).toHaveBeenCalledWith([
      {
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'new value',
        sortOrder: 0,
      },
    ]);
  });

  it('should render select for TRANSACTION_TYPE field', () => {
    const conditions: ConditionDraft[] = [
      {
        field: 'TRANSACTION_TYPE',
        operator: 'EQUALS',
        value: 'CREDIT',
        sortOrder: 0,
      },
    ];

    renderWithProviders(
      <RuleConditionBuilder
        conditions={conditions}
        onChange={mockOnChange}
      />
    );

    expect(screen.getByDisplayValue('Credit (Income)')).toBeInTheDocument();
  });

  it('should render number input for AMOUNT field', () => {
    const conditions: ConditionDraft[] = [
      {
        field: 'AMOUNT',
        operator: 'GREATER_THAN',
        value: '100.50',
        sortOrder: 0,
      },
    ];

    renderWithProviders(
      <RuleConditionBuilder
        conditions={conditions}
        onChange={mockOnChange}
      />
    );

    const valueInput = screen.getByRole('spinbutton', { name: 'Condition value' });
    expect(valueInput).toHaveAttribute('type', 'number');
    expect(valueInput).toHaveAttribute('step', 'any');
  });

  it('should filter operators based on field type', () => {
    const conditions: ConditionDraft[] = [
      {
        field: 'AMOUNT',
        operator: 'GREATER_THAN',
        value: '100',
        sortOrder: 0,
      },
    ];

    renderWithProviders(
      <RuleConditionBuilder
        conditions={conditions}
        onChange={mockOnChange}
      />
    );

    // Should show numeric operators
    expect(screen.getByDisplayValue('Greater than')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('Contains')).not.toBeInTheDocument();
  });
});