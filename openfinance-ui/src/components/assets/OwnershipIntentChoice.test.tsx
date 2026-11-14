import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { OwnershipIntentChoice } from './OwnershipIntentChoice';

describe('OwnershipIntentChoice', () => {
  beforeEach(() => {
    mockAuthentication();
  });

  it('offers all four ownership intents as non-submit buttons and reports the pick', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    renderWithProviders(<OwnershipIntentChoice onSelect={onSelect} onBack={vi.fn()} />);

    const buttons = screen.getAllByRole('button');
    // 4 intent choices + the back button; every one must be type=button inside the wizard form.
    expect(buttons).toHaveLength(5);
    for (const [index, intent] of ['PURCHASE', 'BUY', 'GIFT', 'PLANNED'].entries()) {
      expect(buttons[index]).toHaveAttribute('type', 'button');
      await user.click(buttons[index]);
      expect(onSelect).toHaveBeenLastCalledWith(intent);
    }
    expect(onSelect).toHaveBeenCalledTimes(4);
  });

  it('labels each intent with its title and hint so no choice renders blank', () => {
    renderWithProviders(<OwnershipIntentChoice onSelect={vi.fn()} onBack={vi.fn()} />);
    expect(screen.getByText('What are you recording?')).toBeInTheDocument();
    for (const label of [
      'Something I already own',
      'A purchase',
      'A gift or inheritance',
      'A purchase plan',
    ]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    const intentButtons = screen.getAllByRole('button').slice(0, 4);
    for (const button of intentButtons) {
      // Title span + hint span, both non-empty.
      const spans = button.querySelectorAll('span');
      expect(spans).toHaveLength(2);
      expect(spans[0].textContent?.trim()).not.toBe('');
      expect(spans[1].textContent?.trim()).not.toBe('');
    }
  });

  it('calls onBack from the previous-step button without selecting an intent', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    const onBack = vi.fn();
    renderWithProviders(<OwnershipIntentChoice onSelect={onSelect} onBack={onBack} />);

    const buttons = screen.getAllByRole('button');
    await user.click(buttons[buttons.length - 1]);

    expect(onBack).toHaveBeenCalledTimes(1);
    expect(onSelect).not.toHaveBeenCalled();
  });
});
