import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import i18n from '@/test/i18n-test';

let fail: boolean;
function FailingPanel() {
  if (fail) throw new Error('Unable to render balances');
  return <p>Balances restored</p>;
}
beforeEach(() => {
  mockAuthentication();
  fail = true;
  vi.spyOn(console, 'error').mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

it('isolates a failed panel and retries its children after the error is resolved', async () => {
  renderWithProviders(
    <ErrorBoundary>
      <FailingPanel />
    </ErrorBoundary>
  );
  expect(screen.getByText('Error: Unable to render balances')).toBeInTheDocument();
  expect(screen.queryByText('Balances restored')).not.toBeInTheDocument();
  fail = false;
  await userEvent.click(screen.getByRole('button', { name: i18n.t('errorBoundary.tryAgain') }));
  expect(screen.getByText('Balances restored')).toBeInTheDocument();
  expect(screen.queryByText('Error: Unable to render balances')).not.toBeInTheDocument();
});
it('uses the supplied recovery UI when a feature provides one', () => {
  renderWithProviders(
    <ErrorBoundary fallback={<p>Account temporarily unavailable</p>}>
      <FailingPanel />
    </ErrorBoundary>
  );
  expect(screen.getByText('Account temporarily unavailable')).toBeInTheDocument();
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
});
