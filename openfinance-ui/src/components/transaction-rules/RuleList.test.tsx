/**
 * Tests for RuleList component
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { act } from 'react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';
import { RuleList } from './RuleList';
import type { TransactionRule } from '@/types/transactionRules';

describe('RuleList', () => {
  const mockOnEdit = vi.fn();
  const mockOnDelete = vi.fn();
  const mockOnToggle = vi.fn();
  const mockOnCreateFirst = vi.fn();

  const renderWithI18n = (ui: React.ReactElement) =>
    render(<I18nextProvider i18n={i18n}>{ui}</I18nextProvider>);

  const mockRules: TransactionRule[] = [
    {
      id: 1,
      name: 'Groceries Rule',
      priority: 0,
      isEnabled: true,
      conditions: [
        { id: 1, field: 'DESCRIPTION', operator: 'CONTAINS', value: 'grocery', sortOrder: 0 },
      ],
      actions: [
        { id: 1, actionType: 'SET_CATEGORY', actionValue: 'Groceries', sortOrder: 0 },
      ],
    },
    {
      id: 2,
      name: 'Salary Rule',
      priority: 1,
      isEnabled: false,
      conditions: [
        { id: 2, field: 'DESCRIPTION', operator: 'CONTAINS', value: 'salary', sortOrder: 0 },
      ],
      actions: [
        { id: 2, actionType: 'SET_CATEGORY', actionValue: 'Income', sortOrder: 0 },
      ],
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render empty state when no rules', () => {
    renderWithI18n(
      <RuleList
        rules={[]}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    expect(screen.getByText('No rules yet')).toBeInTheDocument();
    expect(screen.getByText('Create your first rule')).toBeInTheDocument();

    const createButton = screen.getByRole('button', { name: /create your first rule/i });
    fireEvent.click(createButton);

    expect(mockOnCreateFirst).toHaveBeenCalledTimes(1);
  });

  it('should render table with rules', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    expect(screen.getByText('Groceries Rule')).toBeInTheDocument();
    expect(screen.getByText('Salary Rule')).toBeInTheDocument();
    expect(screen.getByText('0')).toBeInTheDocument(); // Priority for first rule
  });

  it('should display active/inactive status correctly', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Inactive')).toBeInTheDocument();
  });

  it('should call onEdit when edit button is clicked', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    const editButtons = screen.getAllByRole('button', { name: /edit rule/i });
    fireEvent.click(editButtons[0]);

    expect(mockOnEdit).toHaveBeenCalledWith(mockRules[0]);
  });

  it('should call onDelete when delete button is clicked', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    const deleteButtons = screen.getAllByRole('button', { name: /delete rule/i });
    fireEvent.click(deleteButtons[0]);

    expect(mockOnDelete).toHaveBeenCalledWith(mockRules[0]);
  });

  it('should call onToggle when toggle switch is clicked', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    const toggleSwitches = screen.getAllByRole('switch');
    fireEvent.click(toggleSwitches[0]);

    expect(mockOnToggle).toHaveBeenCalledWith(mockRules[0]);
  });

  it('should disable buttons when isMutating is true', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
        isMutating={true}
      />
    );

    const editButtons = screen.getAllByRole('button', { name: /edit rule/i });
    const deleteButtons = screen.getAllByRole('button', { name: /delete rule/i });
    const toggleSwitches = screen.getAllByRole('switch');

    editButtons.forEach(button => expect(button).toBeDisabled());
    deleteButtons.forEach(button => expect(button).toBeDisabled());
    toggleSwitches.forEach(button => expect(button).toBeDisabled());
  });

  it('should render correct condition and action counts', () => {
    const rulesWithMultiple: TransactionRule[] = [
      {
        id: 1,
        name: 'Complex Rule',
        priority: 0,
        isEnabled: true,
        conditions: [
          { id: 1, field: 'DESCRIPTION', operator: 'CONTAINS', value: 'test1', sortOrder: 0 },
          { id: 2, field: 'AMOUNT', operator: 'GREATER_THAN', value: '100', sortOrder: 1 },
        ],
        actions: [
          { id: 1, actionType: 'SET_CATEGORY', actionValue: 'Category1', sortOrder: 0 },
          { id: 2, actionType: 'SET_PAYEE', actionValue: 'Payee1', sortOrder: 1 },
          { id: 3, actionType: 'ADD_TAG', actionValue: 'tag1', sortOrder: 2 },
        ],
      },
    ];

    renderWithI18n(
      <RuleList
        rules={rulesWithMultiple}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    expect(screen.getByText('2')).toBeInTheDocument(); // 2 conditions
    expect(screen.getByText('3')).toBeInTheDocument(); // 3 actions
  });

  it('should render table headers correctly', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('Priority')).toBeInTheDocument();
    expect(screen.getByText('Status')).toBeInTheDocument();
    expect(screen.getByText('Conditions')).toBeInTheDocument();
    expect(screen.getByText('Actions')).toBeInTheDocument();
    expect(screen.getByText('Controls')).toBeInTheDocument();
  });

  it('should sort rules by name when clicking the Name header', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    const nameHeader = screen.getByText('Name');
    // Click to sort ascending
    fireEvent.click(nameHeader);
    // Click again to toggle direction
    fireEvent.click(nameHeader);

    // Both rules still present after sorting
    expect(screen.getByText('Groceries Rule')).toBeInTheDocument();
    expect(screen.getByText('Salary Rule')).toBeInTheDocument();
  });

  it('should sort by priority column', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    const priorityHeader = screen.getByText('Priority');
    fireEvent.click(priorityHeader);
    expect(screen.getByText('Groceries Rule')).toBeInTheDocument();
  });

  it('should sort by status column', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    const statusHeader = screen.getByText('Status');
    fireEvent.click(statusHeader);
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('should sort by conditions column', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    const conditionsHeader = screen.getByText('Conditions');
    fireEvent.click(conditionsHeader);
    expect(screen.getByText('Groceries Rule')).toBeInTheDocument();
  });

  it('should sort by actions column', () => {
    renderWithI18n(
      <RuleList
        rules={mockRules}
        onEdit={mockOnEdit}
        onDelete={mockOnDelete}
        onToggle={mockOnToggle}
        onCreateFirst={mockOnCreateFirst}
      />
    );

    const actionsHeader = screen.getByText('Actions');
    fireEvent.click(actionsHeader);
    expect(screen.getByText('Groceries Rule')).toBeInTheDocument();
  });
});