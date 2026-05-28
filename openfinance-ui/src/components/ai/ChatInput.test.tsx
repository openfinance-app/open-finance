import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ChatInput } from './ChatInput';

describe('ChatInput', () => {
  const defaultProps = {
    value: '',
    onChange: vi.fn(),
    onSubmit: vi.fn(),
  };

  it('renders a textarea', () => {
    render(<ChatInput {...defaultProps} />);
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('displays the current value', () => {
    render(<ChatInput {...defaultProps} value="Hello" />);
    expect(screen.getByRole('textbox')).toHaveValue('Hello');
  });

  it('calls onChange when typing', () => {
    render(<ChatInput {...defaultProps} />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'test' } });
    expect(defaultProps.onChange).toHaveBeenCalledWith('test');
  });

  it('shows send button', () => {
    render(<ChatInput {...defaultProps} value="test" />);
    const sendBtn = screen.getByRole('button');
    expect(sendBtn).toBeInTheDocument();
  });

  it('disables textarea when disabled', () => {
    render(<ChatInput {...defaultProps} disabled />);
    expect(screen.getByRole('textbox')).toBeDisabled();
  });

  it('shows custom placeholder', () => {
    render(<ChatInput {...defaultProps} placeholder="Type here..." />);
    expect(screen.getByPlaceholderText('Type here...')).toBeInTheDocument();
  });

  it('shows character count near limit', () => {
    // maxLength=100, value.length=95 > 90% of 100 → shows count
    const longValue = 'a'.repeat(95);
    render(<ChatInput {...defaultProps} value={longValue} maxLength={100} />);
    expect(screen.getByText('95/100')).toBeInTheDocument();
  });
});
