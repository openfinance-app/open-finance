import { useState } from 'react';
import type { ComponentProps } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { ImportReview } from '@/components/import/ImportReview';
import type { ImportTransactionDTO } from '@/types/import';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';

const categories = vi.hoisted(() => ({
  data: [] as Array<{ id: number; name: string; parentId?: number }>,
}));
vi.mock('@/hooks/useTransactionTags', () => ({ usePopularTags: () => ({ data: [] }) }));
vi.mock('@/hooks/useCategories', () => ({ useCategories: () => categories }));
vi.mock('@/hooks/useUserSettings', async original => ({
  ...(await original<typeof import('@/hooks/useUserSettings')>()),
  useUserSettings: () => ({ data: { dateFormat: 'YYYY-MM-DD' } }),
  useBaseCurrency: () => ({ data: 'EUR' }),
}));
vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: ({
    value,
    onValueChange,
  }: ComponentProps<typeof import('@/components/ui/CategorySelect').CategorySelect>) => (
    <select
      aria-label="Category mapping"
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value ? Number(event.target.value) : undefined)}
    >
      <option value="">Unmapped</option>
      <option value="2">Home insurance</option>
      <option value="4">Car insurance</option>
    </select>
  ),
  CategoryCombobox: ({
    value,
    onValueChange,
  }: {
    value: string;
    onValueChange: (value: string, id?: number) => void;
  }) => (
    <input
      aria-label="Category name"
      value={value}
      onChange={event => onValueChange(event.target.value)}
    />
  ),
}));
vi.mock('@/components/ui/PayeeSelector', () => ({
  PayeeCombobox: ({
    value,
    onValueChange,
  }: {
    value: string;
    onValueChange: (value: string) => void;
  }) => (
    <input
      aria-label="Payee name"
      value={value}
      onChange={event => onValueChange(event.target.value)}
    />
  ),
}));
const row: ImportTransactionDTO = {
  transactionDate: '2025-01-01',
  payee: 'Merchant',
  amount: '-20',
  currency: 'EUR',
  memo: null,
  category: null,
  clearedStatus: '',
  referenceNumber: null,
  accountName: null,
  lineNumber: null,
  splits: [],
  validationErrors: [],
  potentialDuplicate: false,
  sourceFileName: 'statement.qif',
  rawData: null,
};
const changed = vi.fn();
const text = (key: string) => i18n.t(key, { ns: 'import' });

function Session({
  rows,
  mappings = {},
}: {
  rows: ImportTransactionDTO[];
  mappings?: Record<string, number>;
}) {
  const [transactions, setTransactions] = useState(rows);
  const [categoryMappings, setMappings] = useState(mappings);
  const [newCategoryNames, setNewCategories] = useState<string[]>([]);
  return (
    <>
      <ImportReview
        transactions={transactions}
        onTransactionsChange={next => {
          changed(next);
          setTransactions(next);
        }}
        categoryMappings={categoryMappings}
        onCategoryMappingsChange={setMappings}
        newCategoryNames={newCategoryNames}
        onNewCategoryNamesChange={setNewCategories}
      />
      <output aria-label="Reviewed transactions">{JSON.stringify(transactions)}</output>
      <output aria-label="Category assignments">{JSON.stringify(categoryMappings)}</output>
    </>
  );
}
function renderRows(rows: ImportTransactionDTO[], mappings?: Record<string, number>) {
  return renderWithProviders(<Session rows={rows} mappings={mappings} />);
}
function reviewed(): ImportTransactionDTO[] {
  return JSON.parse(screen.getByLabelText('Reviewed transactions').textContent!);
}

describe('Import review decisions and selected-row edits', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    categories.data = [
      { id: 1, name: 'Home' },
      { id: 2, name: 'Insurance', parentId: 1 },
      { id: 3, name: 'Car' },
      { id: 4, name: 'Insurance', parentId: 3 },
    ];
  });

  it('includes unresolved ambiguous categories in the uncategorized filter and resolves them after mapping', async () => {
    renderRows([
      { ...row, category: 'Insurance', payee: 'Unresolved merchant' },
      { ...row, category: 'Home > Insurance', payee: 'Resolved merchant' },
    ]);
    fireEvent.click(screen.getByRole('button', { name: /Uncategorized/i }));
    expect(screen.getByRole('row', { name: /Unresolved merchant/ })).toBeInTheDocument();
    expect(screen.queryByRole('row', { name: /Resolved merchant/ })).not.toBeInTheDocument();
    fireEvent.click(
      screen.getByText(i18n.t('review.categoryAssignments.title', { ns: 'import', count: 2 }))
    );
    const selects = screen.getAllByRole('combobox', { name: 'Category mapping' });
    fireEvent.change(selects[0], { target: { value: '2' } });
    await waitFor(() =>
      expect(screen.queryByRole('row', { name: /Unresolved merchant/ })).not.toBeInTheDocument()
    );
    expect(screen.getByText(text('review.table.noResults'))).toBeInTheDocument();
  });

  it.each([
    ['SPLIT_INVALID: totals differ', 'validation.splitInvalid'],
    ['RULE_ACTION_INVALID: amount', 'validation.ruleActionInvalid'],
    ['Transaction date is required', 'validation.dateRequired'],
    ['Transaction amount is required', 'validation.amountRequired'],
    ['Transaction amount cannot be zero', 'validation.amountZero'],
    ['Transaction date cannot be in the future', 'validation.dateFuture'],
    ['Bank rejected the row', null],
  ])('expands and translates the blocking validation message %s', (message, translation) => {
    renderRows([{ ...row, validationErrors: [message!] }]);
    expect(screen.getByText(translation ? text(translation) : message!)).toBeInTheDocument();
    fireEvent.click(screen.getByTitle(text('review.actions.hideErrors')));
    expect(screen.queryByText(translation ? text(translation) : message!)).not.toBeInTheDocument();
    fireEvent.click(screen.getByTitle(text('review.actions.showErrors')));
    expect(screen.getByText(translation ? text(translation) : message!)).toBeInTheDocument();
  });

  it('filters real errors while informational rule and AI annotations remain reviewable', () => {
    renderRows([
      { ...row, payee: 'Error row', validationErrors: ['Problem'] },
      { ...row, payee: 'Suggested row', validationErrors: ['AI_MATCH: Insurance'] },
      { ...row, payee: 'Unavailable row', validationErrors: ['AI_UNAVAILABLE: Timeout'] },
    ]);
    expect(screen.getByText(text('review.aiUnavailable'))).toHaveAttribute('role', 'status');
    fireEvent.click(screen.getByRole('button', { name: /Errors/i }));
    expect(screen.getByRole('row', { name: /Error row/ })).toBeInTheDocument();
    expect(screen.queryByRole('row', { name: /Suggested row/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('row', { name: /Unavailable row/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: text('review.filters.all') }));
    expect(screen.getByRole('row', { name: /Suggested row/ })).toBeInTheDocument();
  });

  it.each([
    { annotations: ['AI_MATCH: matched'], ai: true, rule: false, skipped: false },
    {
      annotations: ['RULE_MATCH: matched', 'AI_MATCH: previous'],
      ai: false,
      rule: true,
      skipped: false,
    },
    { annotations: ['RULE_SKIP: ignored'], ai: false, rule: false, skipped: true },
  ])(
    'shows the authoritative annotation for $annotations',
    ({ annotations, ai, rule: ruleMatch, skipped }) => {
      renderRows([{ ...row, validationErrors: annotations }]);
      const transaction = screen.getByRole('row', { name: /Merchant/ });
      expect(within(transaction).queryAllByText(text('review.labels.ai'))).toHaveLength(ai ? 1 : 0);
      expect(within(transaction).queryAllByText(text('review.labels.matched'))).toHaveLength(
        ruleMatch ? 1 : 0
      );
      expect(within(transaction).queryAllByText(text('review.labels.skipped'))).toHaveLength(
        skipped ? 1 : 0
      );
      expect(screen.queryByText(text('review.table.validationErrors'))).not.toBeInTheDocument();
    }
  );

  it('clears inline fields only on save, preserves other rows and supports cancellation', () => {
    const original = {
      ...row,
      category: 'New category',
      memo: 'Old memo',
      payee: 'Original payee',
    };
    renderRows([original, { ...row, payee: 'Untouched' }]);
    fireEvent.click(screen.getAllByTitle(text('review.actions.edit'))[0]);
    fireEvent.change(screen.getByRole('textbox', { name: 'Payee name' }), {
      target: { value: 'Discarded' },
    });
    fireEvent.click(screen.getByTitle(text('review.actions.cancel')));
    expect(reviewed()[0]).toEqual(original);
    fireEvent.click(screen.getAllByTitle(text('review.actions.edit'))[0]);
    fireEvent.change(screen.getByRole('textbox', { name: 'Category name' }), {
      target: { value: '  ' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: 'Payee name' }), {
      target: { value: '' },
    });
    fireEvent.change(screen.getByDisplayValue('Old memo'), { target: { value: '' } });
    fireEvent.click(screen.getByTitle(text('review.actions.save')));
    expect(reviewed()).toEqual([
      { ...original, category: null, payee: '', memo: null },
      { ...row, payee: 'Untouched' },
    ]);
  });

  it('edits and clears a fallback description independently of the payee and notes', () => {
    const original = { ...row, originalPayee: 'Bank text', memo: 'Receipt' };
    renderRows([original]);
    const edit = () => fireEvent.click(screen.getByTitle(text('review.actions.edit')));
    const description = () =>
      screen.getByRole('textbox', { name: text('review.table.description') });
    edit();
    expect(description()).toHaveValue('Bank text');
    fireEvent.change(description(), { target: { value: 'Discarded' } });
    fireEvent.click(screen.getByTitle(text('review.actions.cancel')));
    expect(reviewed()[0]).toEqual(original);
    edit();
    fireEvent.change(description(), { target: { value: '' } });
    fireEvent.click(screen.getByTitle(text('review.actions.save')));
    expect(reviewed()[0]).toEqual({ ...original, description: '' });
    edit();
    expect(description()).toHaveValue('');
  });

  it('saves a corrected date and exact signed amount and clears only their statement errors', () => {
    const annotation = 'RULE_MATCH: Bank rule';
    renderRows([
      {
        ...row,
        transactionDate: null,
        amount: null,
        validationErrors: [
          'Transaction date is required',
          'Le montant de la transaction est requis',
          annotation,
        ],
      },
    ]);
    fireEvent.click(screen.getByTitle(text('review.actions.edit')));
    const date = screen.getByRole('textbox', { name: text('review.table.date') });
    const amount = screen.getByRole('textbox', { name: text('review.table.amount') });
    fireEvent.change(date, { target: { value: '2025-02-31' } });
    fireEvent.change(amount, { target: { value: '-0.123456789012345678' } });
    fireEvent.click(screen.getByTitle(text('review.actions.save')));
    expect(date).toBeInvalid();
    expect(reviewed()[0].amount).toBeNull();
    fireEvent.change(date, { target: { value: '2025-02-28' } });
    fireEvent.click(screen.getByTitle(text('review.actions.save')));
    expect(reviewed()[0]).toMatchObject({
      transactionDate: '2025-02-28',
      amount: '-0.123456789012345678',
      validationErrors: [annotation],
    });
  });

  it('keeps date and amount drafts local and requires Enter to apply a calculation', () => {
    renderRows([row]);
    const edit = () => fireEvent.click(screen.getByTitle(text('review.actions.edit')));
    const amount = () => screen.getByRole('textbox', { name: text('review.table.amount') });
    edit();
    fireEvent.change(screen.getByRole('textbox', { name: text('review.table.date') }), {
      target: { value: '2025-02-28' },
    });
    fireEvent.change(amount(), { target: { value: '9+5' } });
    fireEvent.keyDown(amount(), { key: 'Enter' });
    expect(amount()).toHaveValue('14');
    fireEvent.click(screen.getByTitle(text('review.actions.cancel')));
    expect(reviewed()[0]).toEqual(row);
    edit();
    fireEvent.change(amount(), { target: { value: '-9-5' } });
    fireEvent.click(screen.getByTitle(text('review.actions.save')));
    expect(amount()).toBeInvalid();
    fireEvent.keyDown(amount(), { key: 'Enter' });
    fireEvent.click(screen.getByTitle(text('review.actions.save')));
    expect(reviewed()[0].amount).toBe('-14');
  });

  it('updates both signed amounts of a matched transfer within the same file and keeps posting dates independent', () => {
    const debit = {
      ...row,
      transfer: true,
      transferGroupKey: 'review:pair',
      sourceUploadId: 'first',
    };
    const credit = { ...debit, amount: '20', payee: 'Savings' };
    const unrelated = { ...credit, sourceUploadId: 'second' };
    renderRows([debit, credit, unrelated]);
    fireEvent.click(screen.getAllByTitle(text('review.actions.edit'))[1]);
    fireEvent.change(screen.getByRole('textbox', { name: text('review.table.amount') }), {
      target: { value: '25.75' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: text('review.table.date') }), {
      target: { value: '2025-01-06' },
    });
    fireEvent.click(screen.getByTitle(text('review.actions.save')));
    expect(reviewed()).toEqual([
      { ...debit, amount: '-25.75' },
      { ...credit, amount: '25.75', transactionDate: '2025-01-06' },
      unrelated,
    ]);
  });

  it('requires a nonzero transaction amount and allows a zero opening balance without a statement date', () => {
    renderRows([row, { ...row, openingBalance: true, transactionDate: null }]);
    const amount = () => screen.getByRole('textbox', { name: text('review.table.amount') });
    fireEvent.click(screen.getAllByTitle(text('review.actions.edit'))[0]);
    fireEvent.change(amount(), { target: { value: '0' } });
    expect(screen.getByTitle(text('review.actions.save'))).toBeDisabled();
    fireEvent.click(screen.getByTitle(text('review.actions.cancel')));
    fireEvent.click(screen.getAllByTitle(text('review.actions.edit'))[1]);
    fireEvent.change(amount(), { target: { value: '0' } });
    fireEvent.click(screen.getByTitle(text('review.actions.save')));
    expect(reviewed()[1]).toMatchObject({ amount: '0', transactionDate: null });
  });

  it('bulk edits the description, date and calculated amount on selected rows only', () => {
    const annotation = 'RULE_MATCH: bank';
    const missing = {
      ...row,
      transactionDate: null,
      amount: null,
      validationErrors: [
        'Transaction date is required',
        'Le montant de la transaction est requis',
        annotation,
      ],
    };
    renderRows([missing, row, { ...row, payee: 'Outside selection' }]);
    fireEvent.click(screen.getByRole('checkbox', { name: /Select transaction 1/i }));
    fireEvent.click(screen.getByRole('checkbox', { name: /Select transaction 2/i }));
    const date = screen.getByRole('textbox', { name: 'Date' });
    const amount = screen.getByRole('textbox', { name: 'Amount' });
    const apply = screen.getByRole('button', { name: 'Apply to 2' });
    fireEvent.change(screen.getByRole('textbox', { name: 'Description' }), {
      target: { value: 'Train ticket\nReturn' },
    });
    fireEvent.change(date, { target: { value: '2025-02-31' } });
    fireEvent.change(amount, { target: { value: '9+5' } });
    fireEvent.click(apply);
    expect(date).toBeInvalid();
    fireEvent.change(date, { target: { value: '2025-02-28' } });
    fireEvent.click(apply);
    expect(amount).toBeInvalid();
    expect(reviewed()[0]).toEqual(missing);
    fireEvent.keyDown(amount, { key: 'Enter' });
    expect(amount).toHaveValue('14');
    fireEvent.click(apply);
    const patch = {
      description: 'Train ticket\nReturn',
      transactionDate: '2025-02-28',
      amount: '14',
    };
    expect(reviewed()).toEqual([
      { ...missing, ...patch, validationErrors: [annotation] },
      { ...row, ...patch },
      { ...row, payee: 'Outside selection' },
    ]);
  });

  it('sets and clears only the selected transaction notes, independently of descriptions and split memos', () => {
    const first = {
      ...row,
      description: 'Rail fare',
      memo: 'From statement',
      splits: [
        { category: 'Travel', amount: '-10', memo: 'Split note' },
        { category: 'Fees', amount: '-10', memo: '' },
      ],
    };
    const second = { ...row, memo: 'Other note', description: 'Metro fare' };
    const opening = { ...row, openingBalance: true, memo: 'Source balance' };
    renderRows([first, second, opening]);
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select all' }));
    const notes = () => screen.getByRole('textbox', { name: 'Notes' });
    expect(notes()).toHaveAttribute('maxlength', '1000');
    const replacement = 'Business trip\nReceipt attached';
    fireEvent.change(notes(), { target: { value: replacement } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 3' }));
    expect(reviewed()).toEqual([
      { ...first, memo: replacement },
      { ...second, memo: replacement },
      opening,
    ]);
    fireEvent.click(screen.getByRole('checkbox', { name: /Select transaction 1/i }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Clear notes' }));
    expect(notes()).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 1' }));
    expect(reviewed()).toEqual([
      { ...first, memo: null },
      { ...second, memo: replacement },
      opening,
    ]);
  });

  it('keeps a missing amount on a date-only bulk edit and resets discarded drafts', () => {
    const missing = {
      ...row,
      amount: null,
      originalPayee: 'Statement payee',
      validationErrors: ['Transaction amount is required'],
    };
    renderRows([missing]);
    const select = screen.getByRole('checkbox', { name: /Select transaction 1/i });
    fireEvent.click(select);
    fireEvent.change(screen.getByRole('textbox', { name: 'Description' }), {
      target: { value: 'Discard me' },
    });
    fireEvent.click(select);
    fireEvent.click(select);
    expect(screen.getByRole('button', { name: 'Apply to 1' })).toBeDisabled();
    fireEvent.change(screen.getByRole('textbox', { name: 'Date' }), {
      target: { value: '2025-01-14' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 1' }));
    expect(reviewed()[0]).toEqual({ ...missing, transactionDate: '2025-01-14' });
    fireEvent.click(select);
    fireEvent.click(screen.getByRole('checkbox', { name: 'Clear description' }));
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 1' }));
    expect(reviewed()[0].description).toBe('');
  });

  it.each(['category', 'payee'])('applies only a changed %s in a bulk edit', field => {
    const first = { ...row, category: 'First', memo: 'Keep memo', payee: 'First merchant' };
    const second = { ...row, category: 'Second', payee: 'Second merchant' };
    renderRows([first, second]);
    fireEvent.click(screen.getByRole('checkbox', { name: /Select transaction 1/i }));
    expect(screen.getByRole('checkbox', { name: 'Select all' })).toBePartiallyChecked();
    const apply = screen.getByRole('button', { name: /Apply to 1/i });
    expect(apply).toBeDisabled();
    fireEvent.change(
      screen.getByRole('textbox', { name: field === 'category' ? 'Category name' : 'Payee name' }),
      { target: { value: '  Reviewed  ' } }
    );
    fireEvent.click(apply);
    expect(reviewed()).toEqual([{ ...first, [field]: 'Reviewed' }, second]);
    expect(screen.getByRole('checkbox', { name: 'Select all' })).not.toBeChecked();
  });

  it('bulk edits matched transfer amounts once per side and preserves the direction when both are selected', () => {
    const debit = { ...row, transfer: true, transferGroupKey: 'pair' };
    const credit = { ...debit, amount: '20' };
    const otherFile = { ...credit, sourceUploadId: 'other' };
    renderRows([credit, debit, otherFile]);
    const first = screen.getByRole('checkbox', { name: /Select transaction 1/i });
    const second = screen.getByRole('checkbox', { name: /Select transaction 2/i });
    const amount = () => screen.getByRole('textbox', { name: 'Amount' });
    fireEvent.click(second);
    fireEvent.change(amount(), { target: { value: '-17.000000000000000001' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 1' }));
    expect(reviewed().map(tx => tx.amount)).toEqual([
      '17.000000000000000001',
      '-17.000000000000000001',
      '20',
    ]);
    fireEvent.click(second);
    fireEvent.click(first);
    fireEvent.change(amount(), { target: { value: '0' } });
    expect(screen.getByRole('button', { name: 'Apply to 2' })).toBeDisabled();
    fireEvent.change(amount(), { target: { value: '15' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 2' }));
    expect(reviewed().map(tx => tx.amount)).toEqual(['15', '-15', '20']);
  });

  it('permits a zero opening balance and keeps split allocations visible for correction', () => {
    const opening = {
      ...row,
      openingBalance: true,
      transactionDate: null,
      description: 'Source balance',
    };
    const split = {
      ...row,
      splits: [
        { category: 'Food', amount: '-10', memo: '' },
        { category: 'Transport', amount: '-10', memo: '' },
      ],
    };
    renderRows([opening, split]);
    fireEvent.click(screen.getByRole('checkbox', { name: /Select transaction 1/i }));
    expect(screen.getByRole('textbox', { name: 'Description' })).toBeDisabled();
    fireEvent.change(screen.getByRole('textbox', { name: 'Amount' }), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 1' }));
    expect(reviewed()[0]).toEqual({ ...opening, amount: '0' });
    fireEvent.click(screen.getByRole('checkbox', { name: /Select transaction 2/i }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Amount' }), { target: { value: '-25' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply to 1' }));
    expect(reviewed()[1]).toMatchObject({
      amount: '-25',
      splits: split.splits,
      validationErrors: ['SPLIT_INVALID: totals differ'],
    });
  });

  it.each([
    'SPLIT_INVALID: amounts differ',
    'Split amounts (12) do not match transaction amount (20)',
    'Les montants ventilés (12) ne correspondent pas au montant de la transaction (20)',
  ])('saves tags and a balanced allocation and clears the resolved error "%s"', error => {
    renderRows([
      { ...row, tags: ['Existing'], validationErrors: ['RULE_MATCH: keep', error] },
      row,
    ]);
    fireEvent.click(screen.getAllByRole('button', { name: text('review.details.title') })[0]);
    const dialog = within(screen.getByRole('dialog'));
    const tag = dialog.getByRole('textbox', { name: text('review.details.tags') });
    fireEvent.change(tag, { target: { value: 'Work trip' } });
    fireEvent.blur(tag);
    fireEvent.click(dialog.getByRole('button', { name: 'Split transaction' }));
    const save = dialog.getByRole('button', { name: text('review.actions.save') });
    expect(save).toBeDisabled();
    fireEvent.change(dialog.getByRole('textbox', { name: 'Split 1 amount' }), {
      target: { value: '7.25' },
    });
    fireEvent.change(dialog.getByRole('textbox', { name: 'Split 2 amount' }), {
      target: { value: '12.74' },
    });
    expect(save).toBeDisabled();
    fireEvent.change(dialog.getByRole('textbox', { name: 'Split 2 amount' }), {
      target: { value: '12.75' },
    });
    fireEvent.change(dialog.getAllByRole('textbox', { name: 'Category name' })[0], {
      target: { value: 'Travel' },
    });
    const lineDetails = dialog.getByRole('button', { name: 'Notes and tags for split 1' });
    expect(lineDetails).toHaveAttribute('aria-expanded', 'false');
    fireEvent.click(lineDetails);
    fireEvent.change(dialog.getByRole('textbox', { name: 'Split 1 description' }), {
      target: { value: 'Fare' },
    });
    const splitTag = dialog.getByRole('textbox', { name: 'Split 1 tags' });
    fireEvent.change(splitTag, { target: { value: 'Reimbursable' } });
    fireEvent.keyDown(splitTag, { key: 'Enter' });
    fireEvent.click(save);
    expect(reviewed()).toEqual([
      {
        ...row,
        tags: ['Existing', 'Work trip'],
        validationErrors: ['RULE_MATCH: keep'],
        splits: [
          { category: 'Travel', memo: 'Fare', amount: '7.25', tags: ['Reimbursable'] },
          { category: '', memo: '', amount: '12.75', tags: [] },
        ],
      },
      row,
    ]);
  });

  it('keeps signed source amounts and category IDs through tag edits and permits removing a split', () => {
    const original = {
      ...row,
      tags: ['Receipt'],
      splits: [
        {
          category: 'Travel',
          sourceCategoryId: 19,
          amount: '-9.9900',
          memo: 'Fare',
          tags: ['Old'],
        },
        { category: 'Food', sourceCategoryId: 20, amount: '-10.00', memo: '', tags: [] },
      ],
    };
    renderRows([original]);
    const open = () => {
      fireEvent.click(screen.getByRole('button', { name: text('review.details.title') }));
      return within(screen.getByRole('dialog'));
    };
    let dialog = open();
    const details = dialog.getByRole('button', { name: 'Notes and tags for split 1' });
    expect(details).toHaveAttribute('aria-expanded', 'false');
    expect(details).toHaveTextContent('Fare · Old');
    fireEvent.click(details);
    fireEvent.click(dialog.getByRole('button', { name: 'Remove tag Old' }));
    fireEvent.click(dialog.getByRole('button', { name: 'Remove split', exact: true }));
    fireEvent.click(dialog.getByRole('button', { name: text('review.actions.cancel') }));
    expect(reviewed()[0]).toEqual(original);
    dialog = open();
    fireEvent.click(dialog.getByRole('button', { name: 'Notes and tags for split 1' }));
    fireEvent.click(dialog.getByRole('button', { name: 'Remove tag Old' }));
    fireEvent.change(dialog.getAllByRole('textbox', { name: 'Category name' })[1], {
      target: { value: 'Meal' },
    });
    fireEvent.click(dialog.getByRole('button', { name: text('review.actions.save') }));
    expect(reviewed()[0].splits).toEqual([
      { ...original.splits[0], tags: [] },
      { ...original.splits[1], category: 'Meal', sourceCategoryId: null },
    ]);
    dialog = open();
    fireEvent.click(dialog.getByRole('button', { name: 'Remove split', exact: true }));
    fireEvent.click(dialog.getByRole('button', { name: text('review.actions.save') }));
    expect(reviewed()[0]).toEqual({ ...original, splits: [] });
  });

  it('allows transfer tags and appends unique bulk tags only to selected transactions', () => {
    const first = { ...row, tags: ['Old'], transfer: true };
    renderRows([first, row]);
    fireEvent.click(screen.getAllByRole('button', { name: text('review.details.title') })[0]);
    const dialog = within(screen.getByRole('dialog'));
    expect(dialog.queryByRole('button', { name: 'Split transaction' })).not.toBeInTheDocument();
    expect(dialog.getByText('Old')).toBeInTheDocument();
    fireEvent.click(dialog.getByRole('button', { name: text('review.actions.cancel') }));
    fireEvent.click(screen.getByRole('checkbox', { name: /Select transaction 1/i }));
    const input = screen.getByRole('textbox', { name: text('review.bulkAction.addTags') });
    const apply = screen.getByRole('button', { name: /Apply to 1/i });
    fireEvent.change(input, { target: { value: 'x'.repeat(51) } });
    expect(apply).toBeDisabled();
    fireEvent.change(input, { target: { value: 'Old, New tag, New tag' } });
    fireEvent.click(apply);
    expect(reviewed()).toEqual([{ ...first, tags: ['Old', 'New tag'] }, row]);
  });

  it('deselects individual rows and the complete set without changing imported data', () => {
    renderRows([row, { ...row, payee: 'Second' }]);
    const first = screen.getByRole('checkbox', { name: /Select transaction 1/i });
    fireEvent.click(first);
    fireEvent.click(first);
    expect(first).not.toBeChecked();
    const all = screen.getByRole('checkbox', { name: 'Select all' });
    fireEvent.click(all);
    expect(first).toBeChecked();
    fireEvent.click(all);
    expect(first).not.toBeChecked();
    expect(changed).not.toHaveBeenCalled();
  });

  it('shows missing date and amount explicitly and uses base currency for unlabelled split amounts', () => {
    renderRows([
      {
        ...row,
        transactionDate: null,
        amount: null,
        currency: null,
        splits: [
          { category: '', amount: null, memo: '', tags: [] },
          { category: 'Home', amount: '2.01', memo: 'Split note', tags: ['Essential'] },
        ],
      },
    ]);
    expect(screen.getByText(text('review.table.missingDate'))).toBeInTheDocument();
    expect(screen.getByText(text('review.table.missingAmount'))).toBeInTheDocument();
    fireEvent.click(screen.getByTitle(text('review.actions.expandSplits')));
    expect(screen.getByText('€0.00')).toBeInTheDocument();
    expect(screen.getByText('€2.01')).toBeInTheDocument();
    expect(screen.getByText('Split note')).toBeInTheDocument();
    expect(screen.getByText('Essential')).toBeInTheDocument();
    fireEvent.click(screen.getByTitle(text('review.actions.collapse')));
    expect(screen.queryByText('Split note')).not.toBeInTheDocument();
  });

  it('renders an empty import without an accidental selection or category mapping', () => {
    renderRows([]);
    expect(screen.getByText(text('review.table.noResults'))).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Select all' })).not.toBeChecked();
    expect(screen.getByLabelText('Category assignments')).toHaveTextContent('{}');
    expect(changed).not.toHaveBeenCalled();
  });
});
