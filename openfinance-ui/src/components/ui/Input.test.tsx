import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Input } from './Input';

describe('Input', () => {
  it('renders a text input by default', () => {
    render(<Input placeholder="Enter text" />);
    const input = screen.getByPlaceholderText('Enter text');
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute('type', 'text');
  });

  it('renders a label when provided', () => {
    render(<Input label="Email" />);
    expect(screen.getByText('Email')).toBeInTheDocument();
  });

  it('shows required indicator', () => {
    render(<Input label="Name" required />);
    expect(screen.getByLabelText(/required/)).toBeInTheDocument();
  });

  it('shows error message with alert role', () => {
    render(<Input label="Email" error="Invalid email" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid email');
  });

  it('sets aria-invalid when error present', () => {
    render(<Input label="Email" error="Bad" />);
    const input = screen.getByRole('textbox');
    expect(input).toHaveAttribute('aria-invalid', 'true');
  });

  it('sets aria-invalid=false when no error', () => {
    render(<Input label="Email" />);
    const input = screen.getByRole('textbox');
    expect(input).toHaveAttribute('aria-invalid', 'false');
  });

  it('shows helper text when no error', () => {
    render(<Input helperText="Enter your email address" />);
    expect(screen.getByText('Enter your email address')).toBeInTheDocument();
  });

  it('hides helper text when error is present', () => {
    render(<Input helperText="Helper" error="Error" />);
    expect(screen.queryByText('Helper')).not.toBeInTheDocument();
    expect(screen.getByText('Error')).toBeInTheDocument();
  });

  it('renders icon', () => {
    render(<Input icon={<span data-testid="icon">🔍</span>} />);
    expect(screen.getByTestId('icon')).toBeInTheDocument();
  });

  it('handles value changes', () => {
    const onChange = vi.fn();
    render(<Input onChange={onChange} />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'test' } });
    expect(onChange).toHaveBeenCalled();
  });

  it('forwards ref', () => {
    const ref = { current: null as HTMLInputElement | null };
    render(<Input ref={ref} />);
    expect(ref.current).toBeInstanceOf(HTMLInputElement);
  });

  it('applies custom className', () => {
    const { container } = render(<Input className="custom-input" />);
    const input = container.querySelector('input');
    expect(input?.className).toContain('custom-input');
  });
});
