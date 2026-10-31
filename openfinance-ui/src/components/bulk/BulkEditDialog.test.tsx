import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { BulkEditDialog } from '@/components/bulk/BulkEditDialog';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import apiClient from '@/services/apiClient';
import type { BulkResource } from '@/types/bulk';

vi.mock('@/services/apiClient');

describe('bulk reference fields', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
  });

  it.each<{ resource: BulkResource; label: string; field: string; endpoint: string }>([
    { resource: 'assets', label: 'Linked account', field: 'accountId', endpoint: '/accounts' },
    {
      resource: 'accounts',
      label: 'Institution',
      field: 'institutionId',
      endpoint: '/institutions',
    },
    { resource: 'transactions', label: 'Category', field: 'categoryId', endpoint: '/categories' },
  ])('explains a failed $label lookup and retries without losing other changes', async scenario => {
    const user = userEvent.setup();
    let unavailable = true;
    vi.mocked(apiClient.get).mockImplementation(async url => {
      if (url === scenario.endpoint) {
        if (unavailable) throw new Error('Lookup unavailable');
        return { data: [{ id: 42, name: 'Recovered choice', currency: 'EUR' }] };
      }
      return { data: {} };
    });
    const submit = vi.fn().mockResolvedValue(undefined);
    renderWithProviders(
      <BulkEditDialog
        resource={scenario.resource}
        items={[{ id: 1, label: 'One' }]}
        busy={false}
        progress={0}
        onClose={vi.fn()}
        onSubmit={submit}
      />
    );
    const textField = scenario.resource === 'accounts' ? 'Description' : 'Notes';
    await user.selectOptions(screen.getByLabelText(`Action for ${textField}`), 'set');
    await user.type(screen.getByRole('textbox', { name: textField }), 'Keep my draft');
    await user.selectOptions(screen.getByLabelText(`Action for ${scenario.label}`), 'set');
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load choices');
    const select = screen.getByRole('combobox', { name: scenario.label, exact: true });
    expect(select).toBeDisabled();
    expect(submit).not.toHaveBeenCalled();

    unavailable = false;
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(select).toBeEnabled());
    await user.selectOptions(select, '42');
    await user.click(screen.getByRole('button', { name: 'Apply to 1 record' }));
    await waitFor(() =>
      expect(submit).toHaveBeenCalledWith({
        [scenario.field]: 42,
        [textField.toLowerCase()]: 'Keep my draft',
      })
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
