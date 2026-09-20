import { describe, it, expect, vi } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import { renderWithProviders as render } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import SuggestedPrompts from './SuggestedPrompts';

describe('SuggestedPrompts', () => {
  it('renders suggested prompt buttons', () => {
    const onSelect = vi.fn();
    render(<SuggestedPrompts onSelectPrompt={onSelect} />);
    expect(screen.getByText('Analyze My Spending')).toBeInTheDocument();
    expect(screen.getByText('Budget Recommendations')).toBeInTheDocument();
  });

  it('calls onSelectPrompt when a prompt is clicked', () => {
    const onSelect = vi.fn();
    render(<SuggestedPrompts onSelectPrompt={onSelect} />);
    fireEvent.click(screen.getByText('Analyze My Spending'));
    expect(onSelect).toHaveBeenCalledWith(
      'Can you analyze my spending patterns and tell me where most of my money is going?'
    );
  });

  it('inserts the French question when a French suggestion is selected', async () => {
    await i18n.changeLanguage('fr');
    try {
      const onSelect = vi.fn();
      render(<SuggestedPrompts onSelectPrompt={onSelect} />);
      fireEvent.click(
        screen.getByRole('button', { name: i18n.t('ai:prompts.analyzeSpending.label') })
      );
      expect(onSelect).toHaveBeenCalledWith(i18n.t('ai:prompts.analyzeSpending.question'));
      expect(onSelect.mock.calls[0][0]).not.toMatch(/^Can you/);
    } finally {
      await i18n.changeLanguage('en');
    }
  });

  it('disables buttons when disabled prop is true', () => {
    render(<SuggestedPrompts onSelectPrompt={vi.fn()} disabled />);
    const buttons = screen.getAllByRole('button');
    buttons.forEach(btn => expect(btn).toBeDisabled());
  });
});
