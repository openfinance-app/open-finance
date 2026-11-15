import { useContext, useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within, fireEvent, act } from '@testing-library/react';
import { BulkSelection, BulkToolbar, SelectionCheckbox } from '@/components/bulk/BulkSelection';
import {
  createTestQueryClient,
  mockAuthentication,
  renderWithProviders,
  userEvent,
} from '@/test/test-utils';
import apiClient from '@/services/apiClient';
import { BulkContext } from '@/components/bulk/bulk-context';
import type { BulkResource } from '@/types/bulk';

vi.mock('@/services/apiClient');

function Fixture({ resource = 'transactions' }: { resource?: BulkResource }) {
  const [filtered, setFiltered] = useState(false);
  const [scope, setScope] = useState('page1');
  return (
    <>
      <button onClick={() => setScope(current => (current === 'page1' ? 'page2' : 'page1'))}>
        Change page
      </button>
      <button onClick={() => setFiltered(value => !value)}>Filter first row</button>
      <BulkSelection
        resource={resource}
        scopeKey={scope}
        items={
          filtered
            ? [{ id: 2, label: 'Two' }]
            : [
                { id: 1, label: 'One' },
                { id: 2, label: 'Two' },
              ]
        }
      >
        <BulkToolbar />
        <SelectionCheckbox id={1} />
        <SelectionCheckbox id={2} />
        <SelectionCheckbox id={99} />
      </BulkSelection>
    </>
  );
}

describe('bulk selection and edits', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} });
    vi.mocked(apiClient.delete).mockResolvedValue({ data: null });
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('reveals the toolbar after selecting an item and hides it when the page changes', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Fixture />);
    expect(screen.getAllByRole('checkbox')).toHaveLength(2);
    expect(screen.queryByRole('checkbox', { name: 'Select visible' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: 'Select One' }));
    expect(screen.getByRole('checkbox', { name: 'Select visible' })).toBePartiallyChecked();
    await user.click(screen.getByRole('checkbox', { name: 'Select visible' }));
    expect(screen.getByText('2 selected')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Change page' }));
    await user.click(screen.getByRole('button', { name: 'Change page' }));
    expect(screen.queryByRole('checkbox', { name: 'Select visible' })).not.toBeInTheDocument();
    expect(screen.queryByText('0 selected')).not.toBeInTheDocument();
  });

  it('hides the toolbar after deselecting the last item or clearing selection', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Fixture />);
    const first = screen.getByRole('checkbox', { name: 'Select One' });
    await user.click(first);
    expect(screen.getByText('1 selected')).toBeInTheDocument();
    await user.click(first);
    expect(screen.queryByRole('checkbox', { name: 'Select visible' })).not.toBeInTheDocument();
    await user.click(first);
    await user.click(screen.getByRole('button', { name: 'Clear selection' }));
    expect(first).not.toBeChecked();
    expect(screen.queryByRole('checkbox', { name: 'Select visible' })).not.toBeInTheDocument();
  });

  it('requires delete confirmation and keeps only failures selected', async () => {
    const user = userEvent.setup();
    const queryClient = createTestQueryClient();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    vi.mocked(apiClient.delete).mockRejectedValueOnce({
      isAxiosError: true,
      response: { data: { message: 'Protected link' } },
    });
    renderWithProviders(<Fixture resource="assets" />, { queryClient });
    await user.click(screen.getByRole('checkbox', { name: 'Select One' }));
    await user.click(screen.getByRole('checkbox', { name: 'Select visible' }));
    await user.click(screen.getByRole('button', { name: 'Delete selected (2)' }));
    expect(apiClient.delete).not.toHaveBeenCalled();
    let dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(apiClient.delete).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Delete selected (2)' }));
    dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Delete selected (2)' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('checkbox', { name: 'Select One' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Select Two' })).not.toBeChecked();
    expect(screen.getByRole('alert')).toHaveTextContent('Protected link');
    expect(invalidate).toHaveBeenCalledTimes(1);
    expect(apiClient.delete).toHaveBeenCalledTimes(2);
    await user.click(screen.getByRole('button', { name: 'Change page' }));
    expect(screen.getByRole('checkbox', { name: 'Select One' })).toBeChecked();
    expect(screen.getByRole('alert')).toHaveTextContent('Protected link');
  });

  it('sends only chosen fields, with distinct clear and false values', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Fixture />);
    await user.click(screen.getByRole('checkbox', { name: 'Select One' }));
    await user.click(screen.getByRole('checkbox', { name: 'Select visible' }));
    await user.click(screen.getByRole('button', { name: 'Edit selected' }));
    expect(screen.getByRole('button', { name: 'Apply to 2 records' })).toBeDisabled();
    await user.selectOptions(screen.getByRole('combobox', { name: 'Action for Notes' }), 'clear');
    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Action for Category' }),
      'clear'
    );
    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Action for Reconciled' }),
      'set'
    );
    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Reconciled', exact: true }),
      'false'
    );
    await user.click(screen.getByRole('button', { name: 'Apply to 2 records' }));
    await waitFor(() => expect(apiClient.patch).toHaveBeenCalledTimes(2));
    expect(apiClient.patch).toHaveBeenCalledWith(
      '/transactions/1/metadata',
      { notes: '', categoryId: null, isReconciled: false },
      expect.anything()
    );
    expect(apiClient.put).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(screen.queryByRole('checkbox', { name: 'Select visible' })).not.toBeInTheDocument()
    );
    expect(screen.getByRole('status')).toHaveTextContent('2 completed · 0 failed');
    expect(screen.getByRole('checkbox', { name: 'Select One' })).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Select Two' })).not.toBeChecked();
  });

  it('validates a chosen empty field without modifying records', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Fixture resource="assets" />);
    await user.click(screen.getByRole('checkbox', { name: 'Select One' }));
    await user.click(screen.getByRole('button', { name: 'Edit selected' }));
    await user.selectOptions(screen.getByRole('combobox', { name: 'Action for Notes' }), 'set');
    await user.click(screen.getByRole('button', { name: 'Apply to 1 record' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter or select a value');
    expect(apiClient.patch).not.toHaveBeenCalled();
    await user.type(screen.getByRole('textbox', { name: 'Notes' }), 'Shared note');
    await user.click(screen.getByRole('button', { name: 'Apply to 1 record' }));
    await waitFor(() =>
      expect(apiClient.patch).toHaveBeenCalledWith(
        '/assets/1/metadata',
        { notes: 'Shared note' },
        expect.anything()
      )
    );
  });
  it('drops filtered-out rows from selection and never submits them', async () => {
    renderWithProviders(<Fixture />);
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select One' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select visible' }));
    fireEvent.click(screen.getByRole('button', { name: 'Filter first row' }));
    expect(screen.getByText('1 selected')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Delete selected (1)' }));
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete selected (1)' })
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(apiClient.delete).toHaveBeenCalledExactlyOnceWith('/transactions/2', expect.anything());
    fireEvent.click(screen.getByRole('button', { name: 'Filter first row' }));
    expect(screen.getByRole('checkbox', { name: 'Select One' })).not.toBeChecked();
  });

  it('select-all toggles back to an empty selection without modifying any record', () => {
    renderWithProviders(<Fixture />);
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select One' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select visible' }));
    expect(screen.getByRole('checkbox', { name: 'Select visible' })).toBeChecked();
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select visible' }));
    expect(screen.queryByRole('button', { name: /Delete selected/ })).not.toBeInTheDocument();
    expect(apiClient.delete).not.toHaveBeenCalled();
  });

  it('does not expose controls outside a selection context', () => {
    renderWithProviders(
      <>
        <BulkToolbar />
        <SelectionCheckbox id={1} />
      </>
    );
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('checkbox keyboard and pointer events do not activate the enclosing record', () => {
    const open = vi.fn();
    renderWithProviders(
      <BulkSelection resource="assets" items={[{ id: 1, label: 'One' }]}>
        <div onClick={open} onKeyDown={open}>
          <SelectionCheckbox id={1} />
        </div>
        <BulkToolbar embedded />
      </BulkSelection>
    );
    const checkbox = screen.getByRole('checkbox', { name: 'Select One' });
    fireEvent.keyDown(checkbox, { key: ' ' });
    fireEvent.click(checkbox);
    expect(open).not.toHaveBeenCalled();
    expect(checkbox).toBeChecked();
  });

  it('locks context actions and cancellation while a delete is pending, then reports success', async () => {
    let complete!: (value: { data: null }) => void;
    vi.mocked(apiClient.delete).mockImplementation(
      () =>
        new Promise(resolve => {
          complete = resolve;
        })
    );
    function ContextActions() {
      const context = useContext(BulkContext)!;
      return (
        <>
          <button onClick={() => context.toggle(1)}>Toggle from consumer</button>
          <button onClick={context.toggleAll}>Toggle all from consumer</button>
          <button onClick={context.clear}>Clear from consumer</button>
        </>
      );
    }
    renderWithProviders(
      <BulkSelection resource="assets" items={[{ id: 1, label: 'One' }]}>
        <BulkToolbar embedded />
        <SelectionCheckbox id={1} />
        <ContextActions />
      </BulkSelection>
    );
    fireEvent.click(screen.getByRole('checkbox', { name: 'Select One' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete selected (1)' }));
    const dialog = screen.getByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Delete selected (1)' }));
    await waitFor(() => expect(apiClient.delete).toHaveBeenCalledTimes(1));
    for (const label of [
      'Toggle from consumer',
      'Toggle all from consumer',
      'Clear from consumer',
    ]) {
      fireEvent.click(screen.getByText(label));
    }
    fireEvent.keyDown(dialog, { key: 'Escape' });
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Select One', hidden: true })).toBeChecked();
    await act(async () => complete({ data: null }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('status')).toHaveTextContent('1 completed · 0 failed');
  });
});
