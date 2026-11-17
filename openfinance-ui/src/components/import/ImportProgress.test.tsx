import { screen, within, fireEvent } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import { ImportProgress } from '@/components/import/ImportProgress';
import type { ImportSessionResponse } from '@/types/import';

vi.mock('@/hooks/useUserSettings', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useUserSettings')>()),
  useUserSettings: () => ({
    data: { dateFormat: 'YYYY-MM-DD', timezone: 'America/New_York', useDeviceTimezone: false },
  }),
}));

const session: ImportSessionResponse = {
  id: 1,
  uploadId: 'upload',
  userId: 1,
  fileName: 'statement.json',
  fileFormat: 'JSON',
  accountId: null,
  suggestedAccountName: null,
  status: 'IMPORTING',
  totalTransactions: 3083,
  importedCount: 0,
  errorCount: 0,
  duplicateCount: 0,
  skippedCount: 0,
  errorMessage: null,
  metadata: '',
  createdAt: '',
  updatedAt: '',
  completedAt: null,
  terminal: false,
  cancellable: false,
  readyForReview: false,
  confirmable: false,
};

describe('ImportProgress', () => {
  beforeEach(() => mockAuthentication());

  it.each([
    ['PENDING', 'preparing'],
    ['PARSING', 'parsing'],
    ['FAILED', 'failed'],
    ['CANCELLED', 'cancelled'],
    ['REVIEWING', 'processing'],
  ] as const)(
    'shows the %s status without offering premature transaction navigation',
    (status, key) => {
      const close = vi.fn();
      const view = vi.fn();
      renderWithProviders(
        <ImportProgress
          session={{ ...session, status }}
          onClose={close}
          onViewTransactions={view}
        />
      );
      expect(screen.getByRole('status')).toHaveTextContent(i18n.t(`import:progress.${key}`));
      expect(
        screen.queryByRole('button', { name: i18n.t('import:progress.viewTransactions') })
      ).not.toBeInTheDocument();
      if (status === 'FAILED' || status === 'CANCELLED') {
        fireEvent.click(screen.getByRole('button', { name: 'Close' }));
        expect(close).toHaveBeenCalledOnce();
      } else expect(screen.queryByRole('button')).not.toBeInTheDocument();
    }
  );
  it('reports AI progress separately from rows being committed', () => {
    renderWithProviders(
      <ImportProgress
        session={session}
        liveProgress={{ phase: 'AI_CATEGORIZING', processed: 2, total: 5 }}
      />
    );
    expect(screen.getByRole('status')).toHaveTextContent(
      i18n.t('import:review.aiProcessing', { current: 2, total: 5 })
    );
    expect(screen.getByRole('progressbar')).not.toHaveAttribute('aria-valuenow');
  });
  it('retains a failed import explanation and the count of intentionally skipped rows', () => {
    renderWithProviders(
      <ImportProgress
        session={{
          ...session,
          status: 'FAILED',
          errorMessage: 'Invalid bank statement',
          skippedCount: 3,
        }}
      />
    );
    expect(screen.getByRole('status')).toHaveTextContent('Invalid bank statement');
    expect(screen.getByText(i18n.t('import:progress.skipped', { count: 3 }))).toBeVisible();
  });
  it('offers navigation after completion and uses the account timezone for the completion time', () => {
    const view = vi.fn();
    renderWithProviders(
      <ImportProgress
        session={{ ...session, status: 'COMPLETED', completedAt: '2024-01-01T12:00:00Z' }}
        onViewTransactions={view}
      />
    );
    fireEvent.click(
      screen.getByRole('button', { name: i18n.t('import:progress.viewTransactions') })
    );
    expect(view).toHaveBeenCalledOnce();
    expect(
      screen.getByText(
        i18n.t('import:progress.completedAt', {
          date: '2024-01-01 07:00 AM',
        })
      )
    ).toBeVisible();
  });

  it('renders live processed rows while committed session counts are still zero', () => {
    renderWithProviders(
      <ImportProgress
        session={session}
        liveProgress={{ phase: 'IMPORTING', processed: 500, total: 3083 }}
      />
    );
    expect(screen.getByRole('status')).toHaveTextContent('(500/3083)');
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '16');
    expect(screen.getByText('Changes are saved when the import completes.')).toBeInTheDocument();
  });

  it('shows finalization until the committed session completes', () => {
    const view = renderWithProviders(
      <ImportProgress
        session={session}
        liveProgress={{ phase: 'FINALIZING', processed: 3083, total: 3083 }}
      />
    );
    expect(screen.getByRole('status')).toHaveTextContent('Finalizing import');
    expect(screen.queryByText('Import completed successfully!')).not.toBeInTheDocument();
    view.rerender(
      <ImportProgress
        session={{ ...session, status: 'COMPLETED' }}
        liveProgress={{ phase: 'FINALIZING', processed: 3083, total: 3083 }}
      />
    );
    expect(screen.getByRole('status')).toHaveTextContent('Import completed successfully!');
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('uses an indeterminate bar before counters arrive', () => {
    renderWithProviders(<ImportProgress session={session} />);
    expect(screen.getByRole('progressbar')).not.toHaveAttribute('aria-valuenow');
    expect(screen.queryByText('50%')).not.toBeInTheDocument();
  });

  it('includes rule import counts and skipped-rule details in the completion card', () => {
    renderWithProviders(
      <ImportProgress
        session={{
          ...session,
          status: 'COMPLETED',
          metadata: JSON.stringify({
            skroogeMetadata: {
              sourceRuleCount: 31,
              rules: Array(29).fill({}),
              importedRuleCount: 27,
              existingRuleCount: 2,
              skippedRuleCount: 2,
              ruleWarnings: [{ sourceId: 5, code: 'unsupportedAction' }],
            },
          }),
        }}
      />
    );
    const rules = within(screen.getByRole('region', { name: 'Skrooge rules' }));
    for (const [label, count] of [
      ['Source rules', '31'],
      ['Imported rules', '27'],
      ['Already present', '2'],
      ['Skipped source rules', '2'],
    ]) {
      expect(rules.getByText(label).parentElement).toHaveTextContent(count);
    }
    expect(rules.getByText(/Rule 5: Skipped: unsupported action/)).toBeInTheDocument();
    expect(rules.queryByText(/Open Finance rules ready/)).not.toBeInTheDocument();
  });

  it.each(['', '{invalid', JSON.stringify({ skroogeMetadata: { sourceRuleCount: 0, rules: [] } })])(
    'does not show rule statistics for imports without rules (%s)',
    metadata => {
      renderWithProviders(
        <ImportProgress session={{ ...session, status: 'COMPLETED', metadata }} />
      );
      expect(screen.queryByRole('region', { name: 'Skrooge rules' })).not.toBeInTheDocument();
    }
  );
});
