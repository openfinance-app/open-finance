import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { BulkEditDialog } from '@/components/bulk/BulkEditDialog';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import type { BulkItem, BulkResource } from '@/types/bulk';

vi.mock('@/components/bulk/BulkReferenceSelect', () => ({
  BulkReferenceSelect: ({
    kind: _kind,
    ...props
  }: React.SelectHTMLAttributes<HTMLSelectElement> & { kind: string }) => (
    <select {...props}>
      <option value="">Choose</option>
      <option value="42">Reference</option>
    </select>
  ),
}));

describe('bulk edit submission boundaries', () => {
  const submit = vi.fn().mockResolvedValue(undefined);
  const close = vi.fn();
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });
  const renderDialog = (
    resource: BulkResource = 'transactions',
    busy = false,
    items: BulkItem[] = [{ id: 1, label: 'One', physical: true }]
  ) =>
    renderWithProviders(
      <BulkEditDialog
        resource={resource}
        items={items}
        busy={busy}
        progress={1}
        onClose={close}
        onSubmit={submit}
      />
    );

  it.each([
    ['accounts', 'Account type', 'type', 'SAVINGS', 'SAVINGS'],
    ['transactions', 'Payment method', 'paymentMethod', 'DIRECT_DEBIT', 'DIRECT_DEBIT'],
    ['transactions', 'Reconciled', 'isReconciled', 'true', true],
    ['transactions', 'Reconciled', 'isReconciled', 'false', false],
    ['assets', 'Linked account', 'accountId', '42', 42],
    ['accounts', 'Institution', 'institutionId', '42', 42],
    ['payees', 'Category', 'categoryId', '42', 42],
  ] as const)(
    'submits typed %s / %s values without modifying untouched fields',
    async (resource, label, field, value, expected) => {
      const user = userEvent.setup();
      renderDialog(resource);
      await user.selectOptions(screen.getByLabelText(`Action for ${label}`), 'set');
      await user.selectOptions(screen.getByRole('combobox', { name: label, exact: true }), value);
      await user.click(screen.getByRole('button', { name: 'Apply to 1 record' }));
      await waitFor(() => expect(submit).toHaveBeenCalledExactlyOnceWith({ [field]: expected }));
    }
  );

  it.each([
    ['transactions', 'Notes', 'notes', ''],
    ['transactions', 'Payee', 'payee', ''],
    ['transactions', 'Payment method', 'paymentMethod', null],
    ['assets', 'Linked account', 'accountId', null],
  ] as const)(
    'clears %s / %s with the API representation for that field',
    async (resource, label, field, expected) => {
      const user = userEvent.setup();
      renderDialog(resource);
      await user.selectOptions(screen.getByLabelText(`Action for ${label}`), 'clear');
      await user.click(screen.getByRole('button', { name: 'Apply to 1 record' }));
      await waitFor(() => expect(submit).toHaveBeenCalledWith({ [field]: expected }));
    }
  );

  it.each(['Notes', 'Payee', 'Payment method', 'Category'])(
    'associates an empty %s value with an accessible validation error',
    async label => {
      const user = userEvent.setup();
      renderDialog();
      await user.selectOptions(screen.getByLabelText(`Action for ${label}`), 'set');
      await user.click(screen.getByRole('button', { name: 'Apply to 1 record' }));
      const error = await screen.findByRole('alert');
      const input = screen.getByRole(['Notes', 'Payee'].includes(label) ? 'textbox' : 'combobox', {
        name: label,
        exact: true,
      });
      expect(input).toHaveAttribute('aria-invalid', 'true');
      expect(input).toHaveAttribute('aria-describedby', error.id);
      expect(error).toHaveTextContent('Enter or select a value');
      expect(submit).not.toHaveBeenCalled();
    }
  );

  it.each([
    ['Payee', 100],
    ['Notes', 1000],
  ] as const)(
    'rejects overlong %s even if the browser length limit was bypassed',
    async (label, length) => {
      const user = userEvent.setup();
      renderDialog();
      await user.selectOptions(screen.getByLabelText(`Action for ${label}`), 'set');
      fireEvent.change(screen.getByRole('textbox', { name: label }), {
        target: { value: 'a'.repeat(length + 1) },
      });
      await user.click(screen.getByRole('button', { name: 'Apply to 1 record' }));
      expect(await screen.findByRole('alert')).toHaveTextContent(
        `Use no more than ${length} characters.`
      );
      expect(submit).not.toHaveBeenCalled();
    }
  );

  it('reverting a field to unchanged discards its draft and validation requirement', async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.selectOptions(screen.getByLabelText('Action for Payee'), 'set');
    await user.type(screen.getByRole('textbox', { name: 'Payee' }), 'Draft');
    await user.selectOptions(screen.getByLabelText('Action for Payee'), 'unchanged');
    expect(screen.getByRole('button', { name: 'Apply to 1 record' })).toBeDisabled();
    await user.selectOptions(screen.getByLabelText('Action for Notes'), 'clear');
    await user.click(screen.getByRole('button', { name: 'Apply to 1 record' }));
    await waitFor(() => expect(submit).toHaveBeenCalledWith({ notes: '' }));
  });

  it('physical metadata is hidden when any selected asset is an investment', () => {
    renderDialog('assets', false, [
      { id: 1, label: 'Car', physical: true },
      { id: 2, label: 'Stock', physical: false },
    ]);
    expect(screen.queryByLabelText('Action for Brand')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Action for Notes')).toBeVisible();
  });

  it('physical metadata and tag text retain the values the user entered', async () => {
    const user = userEvent.setup();
    renderDialog('assets');
    await user.selectOptions(screen.getByLabelText('Action for Brand'), 'set');
    await user.type(screen.getByRole('textbox', { name: 'Brand' }), 'Acme');
    await user.click(screen.getByRole('button', { name: 'Apply to 1 record' }));
    await waitFor(() => expect(submit).toHaveBeenCalledWith({ brand: 'Acme' }));
  });

  it('explains that tag input replaces existing tags and submits the comma-separated text', async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.selectOptions(screen.getByLabelText('Action for Tags'), 'set');
    expect(screen.getByText(/This replaces the existing tags/)).toBeVisible();
    await user.type(screen.getByRole('textbox', { name: 'Tags' }), 'holiday, family');
    await user.click(screen.getByRole('button', { name: 'Apply to 1 record' }));
    await waitFor(() => expect(submit).toHaveBeenCalledWith({ tags: 'holiday, family' }));
  });

  it.each([false, true])('Escape respects pending operation state: %s', async busy => {
    const user = userEvent.setup();
    renderDialog('transactions', busy);
    await user.keyboard('{Escape}');
    if (busy) {
      expect(close).not.toHaveBeenCalled();
      expect(screen.getByRole('status')).toHaveTextContent('Processed 1 of 1 records');
      expect(screen.getByLabelText('Action for Notes')).toBeDisabled();
    } else expect(close).toHaveBeenCalledOnce();
  });

  it.each(['empty', 'unsupported'] as const)('cannot submit an %s selection', async kind => {
    const user = userEvent.setup();
    renderDialog(
      kind === 'unsupported' ? 'categories' : 'transactions',
      false,
      kind === 'empty' ? [] : undefined
    );
    expect(screen.getByRole('button', { name: /Apply to/ })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(close).toHaveBeenCalledOnce();
    expect(submit).not.toHaveBeenCalled();
  });
});
