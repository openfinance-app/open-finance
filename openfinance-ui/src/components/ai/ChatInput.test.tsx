import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import { mockAuthentication, renderWithProviders as render } from '@/test/test-utils';
import { ChatInput } from './ChatInput';

describe('ChatInput', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });
  const defaultProps = {
    value: '',
    onChange: vi.fn(),
    onSubmit: vi.fn(),
  };

  it.each([
    ['Enter', false, 'hello', false, false, 1],
    ['Enter', true, 'hello', false, false, 0],
    ['ArrowDown', false, 'hello', false, false, 0],
    ['Enter', false, '   ', false, false, 0],
    ['Enter', false, 'hello', true, false, 0],
    ['Enter', false, 'hello', false, true, 0],
    ['Enter', false, '12345678901', false, false, 0],
  ] as const)(
    'respects keyboard submission eligibility: %s/%s/%s/%s/%s',
    (key, shiftKey, value, isLoading, disabled, calls) => {
      render(
        <ChatInput
          {...defaultProps}
          value={value}
          isLoading={isLoading}
          disabled={disabled}
          maxLength={10}
        />
      );
      fireEvent.keyDown(screen.getByRole('textbox'), { key, shiftKey });
      expect(defaultProps.onSubmit).toHaveBeenCalledTimes(calls);
    }
  );
  it('sends by button and relinquishes focus without changing the draft', () => {
    render(<ChatInput {...defaultProps} value="hello" />);
    expect(screen.getByRole('textbox')).toHaveFocus();
    fireEvent.blur(screen.getByRole('textbox'));
    fireEvent.click(screen.getByRole('button'));
    expect(defaultProps.onSubmit).toHaveBeenCalledOnce();
    expect(screen.getByRole('textbox')).toHaveValue('hello');
  });
  it('keeps over-limit drafts visible but cannot send them', () => {
    render(<ChatInput {...defaultProps} value="12345678901" maxLength={10} />);
    expect(screen.getByText('11/10')).toBeVisible();
    expect(screen.getByRole('button')).toBeDisabled();
  });

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
