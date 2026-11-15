import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OptionalDetails } from '@/components/ui/OptionalDetails';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';

describe('OptionalDetails', () => {
  beforeEach(() => mockAuthentication());

  it('preserves an entered value when the section is collapsed and reopened', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OptionalDetails title="Additional details">
        <label htmlFor="note">Note</label>
        <input id="note" />
      </OptionalDetails>
    );
    const summary = screen.getByText('Additional details');
    const input = screen.getByLabelText('Note');
    expect(input).not.toBeVisible();
    await user.click(summary);
    await user.type(input, 'Annual renewal');
    await user.click(summary);
    expect(input).not.toBeVisible();
    await user.click(summary);
    expect(input).toBeVisible();
    expect(input).toHaveValue('Annual renewal');
  });

  it('reveals errors, including a repeated submission after the user collapses them', async () => {
    const user = userEvent.setup();
    const content = (hasErrors: boolean) => (
      <form onSubmit={event => event.preventDefault()}>
        <OptionalDetails title="Additional details" hasErrors={hasErrors}>
          <label htmlFor="note">Note</label>
          <input id="note" aria-invalid={hasErrors} />
        </OptionalDetails>
        <button type="submit">Save</button>
      </form>
    );
    const { rerender } = renderWithProviders(content(false));
    expect(screen.getByLabelText('Note')).not.toBeVisible();
    rerender(content(true));
    expect(screen.getByLabelText('Note')).toBeVisible();
    await user.click(screen.getByText('Additional details'));
    expect(screen.getByLabelText('Note')).not.toBeVisible();
    fireEvent.submit(screen.getByRole('button', { name: 'Save' }).closest('form')!);
    expect(screen.getByLabelText('Note')).toBeVisible();
  });

  it('offers keyboard-accessible help without changing the disclosure state', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <OptionalDetails title="Additional details" description="Notes and references">
        <label htmlFor="note">Note</label>
        <input id="note" />
      </OptionalDetails>
    );
    const input = screen.getByLabelText('Note');
    const help = screen.getByRole('button', { name: 'Notes and references' });
    await user.tab();
    await user.tab();
    expect(help).toHaveFocus();
    await waitFor(() => {
      expect(screen.getByRole('tooltip', { name: 'Notes and references' })).toBeVisible();
    });
    expect(input).not.toBeVisible();
    await user.click(help);
    expect(input).not.toBeVisible();
    await user.click(screen.getByText('Additional details'));
    await user.click(help);
    expect(input).toBeVisible();
  });
});
