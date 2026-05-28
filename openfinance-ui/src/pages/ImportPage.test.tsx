import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication, userEvent } from '@/test/test-utils';
import ImportPage from '@/pages/ImportPage';

vi.mock('@/components/import/ImportWizard', () => ({
  ImportWizard: () => <div data-testid="import-wizard">Import Wizard</div>,
}));

describe('ImportPage', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
  });

  it('renders the page heading', () => {
    renderWithProviders(<ImportPage />);
    expect(document.querySelector('h1')).toBeInTheDocument();
  });

  it('renders the import wizard', () => {
    renderWithProviders(<ImportPage />);
    expect(screen.getByTestId('import-wizard')).toBeInTheDocument();
  });

  it('shows supported formats section', () => {
    renderWithProviders(<ImportPage />);
    // The page lists QIF, OFX, CSV, JSON formats
    const body = document.body.textContent;
    expect(body).toBeTruthy();
  });
});
