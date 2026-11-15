import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { HistoryEntryDetails } from '@/components/history/HistoryEntryDetails';
import type { HistoryRecordChange, OperationHistoryResponse } from '@/types/history';
import i18n from '@/test/i18n-test';

const state = vi.hoisted(() => ({ visible: true, details: vi.fn() }));
vi.mock('@/services/historyService', () => ({
  historyService: { getDetails: (...args: unknown[]) => state.details(...args) },
}));
vi.mock('@/context/VisibilityContext', async original => ({
  ...(await original<typeof import('@/context/VisibilityContext')>()),
  useVisibility: () => ({ isAmountsVisible: state.visible }),
}));
const entry: OperationHistoryResponse = {
  id: 1,
  entityId: 2,
  entityType: 'ACCOUNT',
  entityLabel: 'Account edit',
  operationType: 'UPDATE',
  canUndo: true,
  canRedo: false,
  createdAt: '2024-01-01T00:00:00Z',
};
const record = (fields: HistoryRecordChange['fields'] = []): HistoryRecordChange => ({
  recordType: 'accounts',
  recordId: '2',
  operationType: 'UPDATE',
  context: {},
  fields,
});
const page = (content: HistoryRecordChange[], totalPages = 1) => ({
  content,
  totalPages,
  totalElements: content.length,
});
const tr = (key: string) => i18n.t(`history:${key}`);
beforeEach(() => {
  mockAuthentication();
  state.visible = true;
  state.details.mockReset().mockResolvedValue(page([]));
});
describe('Operation history details', () => {
  it('renders booleans, structured metadata, enums and dates as distinct values', async () => {
    state.details.mockResolvedValue(
      page([
        {
          ...record([
            { field: 'is_active', before: false, after: true },
            { field: 'metadata', before: { source: 'old' }, after: { source: 'new' } },
            { field: 'type', before: 'CHECKING', after: 'SAVINGS' },
            { field: 'valid_from', before: '', after: '2024-01-31' },
            { field: 'customField', before: null, after: 'Reviewed' },
          ]),
          context: { account_name: 'Main account' },
        },
      ])
    );
    renderWithProviders(<HistoryEntryDetails entry={entry} />);
    expect(await screen.findByText(tr('yes'))).toBeVisible();
    expect(screen.getByText(tr('no'))).toBeVisible();
    expect(screen.getByText('{"source":"new"}')).toBeVisible();
    expect(screen.getByText(tr('values.SAVINGS'))).toBeVisible();
    expect(screen.getByText('Main account')).toBeVisible();
    expect(screen.getByText('Reviewed')).toBeVisible();
    expect(screen.getAllByText('—')).toHaveLength(2);
  });
  it('hides financial values in both record context and changed fields', async () => {
    state.visible = false;
    state.details.mockResolvedValue(
      page([
        {
          ...record([{ field: 'amount', before: '1234.000000000000000001', after: '2000' }]),
          context: { balance: '999', name: 'Visible name' },
        },
      ])
    );
    renderWithProviders(<HistoryEntryDetails entry={entry} />);
    expect(await screen.findByText('Visible name')).toBeVisible();
    expect(screen.getAllByText('••••')).toHaveLength(3);
    expect(screen.queryByText(/1234/)).not.toBeInTheDocument();
  });
  it('renders a loading state, retries failed detail retrieval and clears the error', async () => {
    state.details
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(page([record()]));
    renderWithProviders(<HistoryEntryDetails entry={entry} />);
    expect(screen.getByRole('status')).toHaveTextContent(tr('loadingDetails'));
    expect(await screen.findByRole('alert')).toHaveTextContent(tr('detailsError'));
    fireEvent.click(screen.getByRole('button', { name: tr('retry') }));
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    expect(await screen.findByText(tr('noRecordedDetails'))).toBeVisible();
    expect(state.details).toHaveBeenCalledTimes(2);
  });
  it('keeps legacy before/after fields available when there are no record-level details', async () => {
    renderWithProviders(
      <HistoryEntryDetails
        entry={{
          ...entry,
          changedFieldsJson: JSON.stringify({
            name: { before: 'Old account', after: 'New account' },
          }),
        }}
      />
    );
    expect(screen.getByText('Old account')).toBeVisible();
    expect(screen.getByText('New account')).toBeVisible();
    await waitFor(() => expect(state.details).toHaveBeenCalledWith(1, 0));
    expect(screen.queryByText(tr('noRecordedDetails'))).not.toBeInTheDocument();
  });
  it('uses page-specific requests and disables navigation at each boundary', async () => {
    state.details.mockImplementation((_id: number, index: number) =>
      Promise.resolve(page([{ ...record(), entityLabel: `Page ${index + 1}` }], 2))
    );
    renderWithProviders(<HistoryEntryDetails entry={entry} />);
    expect(await screen.findByText('Page 1')).toBeVisible();
    expect(screen.getByRole('button', { name: tr('previous') })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: tr('next') }));
    expect(await screen.findByText('Page 2')).toBeVisible();
    expect(screen.getByRole('button', { name: tr('next') })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: tr('previous') }));
    expect(await screen.findByText('Page 1')).toBeVisible();
    expect(state.details).toHaveBeenCalledWith(1, 1);
  });
});
