import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { ImportProgress } from '@/components/import/ImportProgress';
import type { ImportSessionResponse } from '@/types/import';

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
});
