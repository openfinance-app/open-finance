import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
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

  it('disables buttons when disabled prop is true', () => {
    render(<SuggestedPrompts onSelectPrompt={vi.fn()} disabled />);
    const buttons = screen.getAllByRole('button');
    buttons.forEach((btn) => expect(btn).toBeDisabled());
  });
});
