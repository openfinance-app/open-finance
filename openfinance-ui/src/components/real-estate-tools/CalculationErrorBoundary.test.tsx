import { beforeEach, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { CalculationErrorBoundary } from '@/components/real-estate-tools/CalculationErrorBoundary';
import i18n from '@/test/i18n-test';

beforeEach(async () => {
  mockAuthentication();
  await i18n.changeLanguage('en');
});

it('contains a calculation failure and lets the user retry successfully', () => {
  let fails = true;
  function Calculation() {
    if (fails) throw new Error('Invalid financial projection');
    return <p>Projection ready</p>;
  }
  const reset = vi.fn(() => {
    fails = false;
  });
  const log = vi.spyOn(console, 'error').mockImplementation(() => {});
  renderWithProviders(
    <CalculationErrorBoundary onReset={reset}>
      <Calculation />
    </CalculationErrorBoundary>
  );
  expect(
    screen.getByText(i18n.t('calculationError.title', { ns: 'realEstate' }))
  ).toBeInTheDocument();
  expect(screen.queryByText('Projection ready')).not.toBeInTheDocument();
  fireEvent.click(
    screen.getByRole('button', { name: i18n.t('calculationError.retry', { ns: 'realEstate' }) })
  );
  expect(reset).toHaveBeenCalledOnce();
  expect(screen.getByText('Projection ready')).toBeInTheDocument();
  log.mockRestore();
});

it('renders the caller-provided fallback without exposing the thrown error', () => {
  function Broken(): never {
    throw new Error('Sensitive calculation details');
  }
  const log = vi.spyOn(console, 'error').mockImplementation(() => {});
  renderWithProviders(
    <CalculationErrorBoundary fallback={<p>Try again later</p>}>
      <Broken />
    </CalculationErrorBoundary>
  );
  expect(screen.getByText('Try again later')).toBeInTheDocument();
  expect(screen.queryByText('Sensitive calculation details')).not.toBeInTheDocument();
  log.mockRestore();
});
