import { fireEvent, screen } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { LinkedText } from '@/components/ui/LinkedText';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';

beforeEach(() => {
  mockAuthentication();
});

it('preserves the note and opens only its web addresses in new tabs', () => {
  const text =
    'See (https://example.org/a_(b)).\nAlso www.example.fr, or example.com/receipt?x=1&y=2.';
  const { container } = renderWithProviders(
    <p>
      <LinkedText text={text} />
    </p>
  );
  expect(container.querySelector('p')?.textContent).toBe(text);
  const links = screen.getAllByRole('link');
  expect(links.map(link => link.getAttribute('href'))).toEqual([
    'https://example.org/a_(b)',
    'https://www.example.fr',
    'https://example.com/receipt?x=1&y=2',
  ]);
  for (const link of links) {
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  }
});

it('keeps markup, non-web protocols and financial values as text', () => {
  const text =
    '<b>Invoice</b> €12.50 · javascript:alert(1) · ftp://example.com/file · http://example.com:99999 · user@example.com';
  const { container } = renderWithProviders(
    <p>
      <LinkedText text={text} />
    </p>
  );
  expect(screen.queryByRole('link')).toBeNull();
  expect(container.querySelector('b')).toBeNull();
  expect(container.querySelector('p')?.textContent).toBe(text);
});

it('offers only the detected links beneath an editor and isolates them from row actions', () => {
  const activateRow = vi.fn();
  renderWithProviders(
    <div onKeyDown={activateRow} onClick={activateRow}>
      <LinkedText text="Invoice at http://example.com" linksOnly />
    </div>
  );
  const link = screen.getByRole('link', { name: 'http://example.com' });
  expect(screen.queryByText('Invoice at')).toBeNull();
  fireEvent.keyDown(link, { key: 'Enter' });
  fireEvent.click(link);
  expect(activateRow).not.toHaveBeenCalled();
});
