import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createTestQueryClient, mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import PeriodLabel from '@/components/dashboard/PeriodLabel';

describe('PeriodLabel', () => {
  beforeEach(() => {
    mockAuthentication();
  });

  afterEach(async () => {
    await i18n.changeLanguage('en');
  });

  it.each([
    ['en', 'MM/DD/YYYY', '31d', 'Selected period: 10/01/2026 → 10/31/2026'],
    ['fr', 'DD/MM/YYYY', '31j', 'Période sélectionnée : 01/10/2026 → 31/10/2026'],
  ])(
    'shows exact dates on keyboard focus in %s using the preferred date format',
    async (language, dateFormat, caption, expected) => {
      await i18n.changeLanguage(language);
      const queryClient = createTestQueryClient();
      queryClient.setQueryData(['user', 'settings', 1], { dateFormat, language });
      const user = userEvent.setup();
      renderWithProviders(
        <PeriodLabel range={{ from: '2026-10-01', to: '2026-10-31' }}>{caption}</PeriodLabel>,
        { queryClient }
      );

      expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
      await user.tab();
      const trigger = screen.getByRole('button', { name: caption, exact: true });
      expect(trigger).toHaveFocus();
      const tooltip = await screen.findByRole('tooltip', { name: expected });
      expect(trigger).toHaveAttribute('aria-describedby', tooltip.id);
      await user.keyboard('{Escape}');
      await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument());
    }
  );

  it('opens the exact range on hover and on tap without changing the caption', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <PeriodLabel range={{ from: '2026-10-01', to: '2026-10-31' }}>31d</PeriodLabel>
    );
    const trigger = screen.getByRole('button', { name: '31d', exact: true });
    await user.hover(trigger);
    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'Selected period: 10/01/2026 → 10/31/2026'
    );
    await user.keyboard('{Escape}');
    await user.unhover(trigger);
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument());

    await user.pointer([{ keys: '[TouchA>]', target: trigger }, { keys: '[/TouchA]' }]);
    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'Selected period: 10/01/2026 → 10/31/2026'
    );
    expect(trigger).toHaveTextContent('31d');
    await user.pointer([{ keys: '[TouchA>]', target: trigger }, { keys: '[/TouchA]' }]);
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument());
    fireEvent.focus(trigger);
    expect(await screen.findByRole('tooltip')).toBeInTheDocument();
    fireEvent.blur(trigger);
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument());
  });

  it('keeps all time as plain text without inventing date boundaries', () => {
    renderWithProviders(<PeriodLabel>all time</PeriodLabel>);
    expect(screen.getByText('all time')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });
});
